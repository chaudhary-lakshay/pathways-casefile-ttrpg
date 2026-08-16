#!/usr/bin/env bash
# Round 1: each batch of test files reviewed against the rulebook text.
# Usage: bash qa-findings/run-round1.sh
set -u
cd "$(dirname "$0")/.."
OUT=qa-findings/round1
mkdir -p "$OUT"

RULES='NOT findings — this project records these deliberately, do not report them:
- GoldenSheetTest exclusion sets (Docks Bouncer HP; Docks Bouncer / Factory Foreman / Street Acrobat FP; Monastery Scholar advantages; University Librarian disadvantages) — the published sheets really do disagree with the book, and the tests assert the disagreement on purpose.
- any test whose name says it guards against silent repair, or asserts the source data is WRONG (e.g. "most sheets floor Basic Speed and the book does not").
- the five potion-table difficulty disagreements (Herb Lore, Herbal Medicine, Jeweler, Knife, Leatherworking) and the three grants naming no real skill.
Where a character sheet disagrees with the book, the book wins and the disagreement is RECORDED, never repaired.'

while IFS='|' read -r NAME FILES; do
  [ -z "${NAME:-}" ] && continue
  [ -s "$OUT/$NAME.md" ] && { echo "skip $NAME (exists)"; continue; }
  PROMPT="Check these Kotlin test files against the rulebook.

Test files: $FILES

The rulebook's extracted text is at build/extract/book.txt, one line per book line, format: '<globalLineNumber>: <text>'. grep it.

Answer exactly these two questions, nothing else:
1. Does any number, threshold, table row or rule asserted in these tests contradict the book?
2. Does any test assert a rule the book does not print at all (invented)? Show the grep over book.txt that returned nothing.

Do NOT report coverage gaps, untested rules, or anything 'never asserted'. You cannot see the rest of the suite, and every such claim in the previous run was false. Report only a contradiction you can show with two quoted lines.

MANDATORY citation format, one per finding:
FINDING <n>: TEST <path:line> | BOOK <globalLineNumber>: \"<the book line quoted verbatim from book.txt>\" | <one line: what contradicts what>
A finding without a verbatim book line and its global line number is invalid — drop it yourself.

$RULES

Do not explore broadly: read only the named test files, their production source if a number needs tracing, and book.txt. If nothing survives, print exactly: NO FINDINGS"

  echo "=== $NAME @ $(date +%H:%M:%S) ==="
  # the gateway rate-limits hard: retry with backoff, and never keep a 429-only file
  for WAIT in 0 150 300 600 900; do
    [ "$WAIT" -gt 0 ] && { echo "  refused, sleeping ${WAIT}s"; sleep "$WAIT"; }
    node ~/.claude/qa/qa.mjs ask --timeout 900 "$PROMPT" > "$OUT/$NAME.md" 2>&1
    grep -qE 'FINDING|NO FINDINGS|VERDICT' "$OUT/$NAME.md" && break
    grep -q 'refused' "$OUT/$NAME.md" || break
  done
  if grep -q 'every model in the chain refused' "$OUT/$NAME.md"; then
    echo REFUSED > "$OUT/$NAME.status"
    echo "  -> REFUSED (kept for retry)"; rm -f "$OUT/$NAME.md"; sleep 120; continue
  fi
  sleep 90
  if [ ! -s "$OUT/$NAME.md" ]; then
    echo EMPTY > "$OUT/$NAME.status"
  elif grep -qE 'BOOK [0-9]+:' "$OUT/$NAME.md" || grep -q 'NO FINDINGS' "$OUT/$NAME.md"; then
    echo OK > "$OUT/$NAME.status"
  else
    echo NOCITE > "$OUT/$NAME.status"
  fi
  echo "  -> $(cat "$OUT/$NAME.status")"
done < qa-findings/batches.txt
echo "round1 done"
