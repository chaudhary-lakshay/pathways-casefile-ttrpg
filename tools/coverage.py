"""Report how much of each chapter has been claimed by extracted content.

A content file may declare which source lines it consumed:

    { "sourceLines": { "3": [[1717, 2100]] }, "entries": [...] }

Everything not claimed is counted as unclaimed. A line is "mechanical" if
it contains a die expression, a signed modifier, a cost, or an attribute
offset — prose without those is flavour and may stay unclaimed.

A COST IS NOT ALWAYS SIGNED, AND THAT USED TO BE INVISIBLE
----------------------------------------------------------
The filter admitted "5 pts" from the start: a bare integer followed by the
unit a rule reads. It did not admit the other units this book prices in,
so "Ability - Book Reading (1 SPI)", "Attributes: 4 HP" and "Activate by
expending 1 SPI" were all read as flavour. The widening is that same
condition applied to the rest of the pools rather than a list of the lines
that were missed: SPI, HP, FP, CoR and DR are quantities the rules read,
exactly as pts is. The unit must follow the number, which is what keeps
ordinary prose out -- "around 5 cm in length" names no pool.

It stays case-SENSITIVE, like every other alternative here. That is a
measured choice rather than an oversight: the book writes SPI, HP, FP, CoR
and DR and never "5 cor", "5 hp" or "5 COR" -- zero occurrences of any
lowercase or all-caps rendering. If one ever appears it will be invisible,
so a future unclaimed line priced that way is the signal to revisit.

That blind spot moved the DENOMINATOR, not just the miss count. A line the
filter cannot see is excluded from both sides of the ratio, and the branch
below reads an empty denominator as "no mechanical lines to claim -- chapter
is complete". So invisibility was reported as completion. Chapter 7.6,
Spirit Vision Guide, is claimed by NO content file at all and reported
100.0%; under the widened filter it reads 0.0%, which is what it always
was. Its "Activate by expending 1 SPI (1 SPI per minute to maintain)" was
the kind of line that could not be seen.

Census at the time of the change: 286 lines repo-wide became visible, 33 of
them unclaimed. Six chapters moved: 7.6 100.0% -> 0.0%, 4 100.0% -> 66.7%,
5 80.8% -> 73.9%, 6.5 89.3% -> 81.2%, 7 84.2% -> 84.0%, and 6 and 9 rose
(85.4% -> 89.7%, 94.8% -> 95.1%) because the lines that became visible there
were already claimed.

Percentages recorded in docs before this change were measured under the
narrower filter and are not comparable with the ones after it.

Note this does NOT touch the numbering scheme or the set-comparison that
CLAUDE.md 4.6 pins -- sourceLines are still global line numbers and the
report still compares line sets, never counts.

sourceLines are global line numbers as emitted in build/extract/lines.json;
line 1 is the first line of page 1; pages are concatenated in order with no
separators.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "build" / "extract"
CONTENT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

MECHANICAL = re.compile(
    r"\d+d[-+]?\d*|[+-]\s*\d+\b|\d+\s*pts?\b|\bAttr\s*[+-]\s*\d+"
    r"|\b\d+\s*(?:SPI|HP|FP|CoR|DR)\b"
)


def main() -> int:
    pages = json.loads((OUT / "pages.json").read_text(encoding="utf-8"))
    chapters = json.loads((OUT / "chapters.json").read_text(encoding="utf-8"))

    # Build global line numbering: walk pages in order, number every line sequentially
    global_lines = []
    line_number = 1
    for page in pages:
        for text in page["text"].splitlines():
            global_lines.append({"n": line_number, "page": page["page"], "text": text})
            line_number += 1

    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "lines.json").write_text(json.dumps(global_lines, indent=2) + "\n", encoding="utf-8")

    # Build claimed line numbers as sets, deduplicating overlaps
    claimed: dict[str, set[int]] = {}
    if CONTENT.exists():
        for f in CONTENT.rglob("*.json"):
            data = json.loads(f.read_text(encoding="utf-8"))
            for cid, spans in (data.get("sourceLines") or {}).items():
                for span in spans:
                    claimed.setdefault(cid, set()).update(range(span[0], span[1] + 1))

    report_lines = ["# Extraction coverage", "",
                    "| Ch | Title | Pages | Claimed | Unclaimed mechanical lines |",
                    "|---|---|---|---|---|"]
    failed = False

    for ch in chapters:
        # Get lines for this chapter
        ch_start = ch["startPage"]
        ch_end = ch["endPage"]
        ch_lines = [
            (gl["n"], gl["text"])
            for gl in global_lines
            if ch_start <= gl["page"] <= ch_end
        ]

        # Find mechanical line numbers in this chapter
        mechanical_numbers = {n for n, text in ch_lines if MECHANICAL.search(text)}

        # Get claimed line numbers for this chapter
        claimed_numbers = claimed.get(ch["id"], set())

        # Calculate coverage
        claimed_mechanical = mechanical_numbers & claimed_numbers
        unclaimed_mechanical = mechanical_numbers - claimed_numbers

        if len(mechanical_numbers) == 0:
            pct = 100.0  # No mechanical lines to claim — chapter is "complete"
        else:
            pct = (len(claimed_mechanical) / len(mechanical_numbers)) * 100

        unclaimed = len(unclaimed_mechanical)
        # Failed when chapter has claims but still has unclaimed mechanical lines
        if len(claimed_numbers) > 0 and unclaimed > 0:
            failed = True

        report_lines.append(f"| {ch['id']} | {ch['title']} | "
                            f"{ch_start}-{ch_end} | {pct:.1f}% | {unclaimed} |")

    (OUT / "coverage.md").write_text("\n".join(report_lines) + "\n", encoding="utf-8")
    print("\n".join(report_lines))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
