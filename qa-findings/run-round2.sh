#!/usr/bin/env bash
# Round 2: cross-verification. A fresh session verifies findings it did not author.
# Usage: bash qa-findings/run-round2.sh [preferred-model-id]
set -u
cd "$(dirname "$0")/.."
IN=qa-findings/round1
OUT=qa-findings/round2
mkdir -p "$OUT"
MODEL_ARG=""
[ $# -ge 1 ] && MODEL_ARG="--model $1"

for F in "$IN"/*.md; do
  NAME=$(basename "$F" .md)
  [ -s "$OUT/$NAME-verify.md" ] && { echo "skip $NAME (exists)"; continue; }
  grep -qE 'FINDING [0-9]' "$F" || { echo "skip $NAME (nothing to verify)"; continue; }

  PROMPT="Another reviewer produced the findings in $F. Verify each one. You did not write them; assume nothing in them is true.

For EVERY line starting 'FINDING':
1. Open the cited TEST path:line. Quote what is actually there.
2. Open build/extract/book.txt (format '<globalLineNumber>: <text>') at the cited BOOK line. Quote what is actually there.
3. Decide.

Output one block per finding, nothing else:
VERDICT <n>: CONFIRMED | REFUTED | UNVERIFIABLE
  TEST SAYS: <verbatim line at the cited path:line, or 'line does not exist / does not say that'>
  BOOK SAYS: <verbatim line at the cited book line number, or 'line does not exist / does not say that'>
  WHY: <one line>

REFUTED means the citation is wrong, the quoted book text is not at that line, or the book does not in fact contradict the test.
UNVERIFIABLE means the finding cites no checkable line.
CONFIRMED requires BOTH quotes to be real AND to contradict each other.

Also: this project deliberately records disagreements between the published character sheets and the book. A test asserting such a disagreement is correct behaviour, not a defect — REFUTE any finding that reports one.

Do not explore broadly. Read only the cited files, book.txt, and the findings file."

  echo "=== verify $NAME @ $(date +%H:%M:%S) ==="
  node ~/.claude/qa/qa.mjs ask --timeout 900 $MODEL_ARG "$PROMPT" > "$OUT/$NAME-verify.md" 2>&1
  if [ ! -s "$OUT/$NAME-verify.md" ]; then
    echo EMPTY > "$OUT/$NAME-verify.status"
  elif grep -qE 'VERDICT [0-9]+: (CONFIRMED|REFUTED|UNVERIFIABLE)' "$OUT/$NAME-verify.md"; then
    echo OK > "$OUT/$NAME-verify.status"
  else
    echo NOVERDICT > "$OUT/$NAME-verify.status"
  fi
  echo "  -> $(cat "$OUT/$NAME-verify.status")"
done
echo "round2 done"
