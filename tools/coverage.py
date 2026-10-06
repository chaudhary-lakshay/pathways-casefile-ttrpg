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

The filter was not the only thing hiding 7.6, and the other half is still
here on purpose. main() sets `failed` only when a chapter HAS claims and
still has unclaimed mechanical lines, so a wholly unextracted chapter
contributes nothing to the exit code no matter how low it reads -- 7.6 at
0.0% does not fail the run any more than it did at 100.0%. That is the
intended behaviour (a chapter nobody has started is not a regression), but
it is why nothing ever went red for this one. Read the table, not just the
exit status.

Percentages recorded in docs before this change were measured under the
narrower filter and are not comparable with the ones after it.

Note this does NOT touch the numbering scheme or the set-comparison that
CLAUDE.md 4.6 pins -- sourceLines are still global line numbers and the
report still compares line sets, never counts.

A CHAPTER STARTS AT ITS HEADING, NOT AT THE TOP OF ITS PAGE
-----------------------------------------------------------
`chapters.json` carries a startPage per chapter, and this report used to give
every line on that page to the chapter -- including the lines ABOVE the
heading, which belong to the chapter before it. Where a page break happens to
fall between two chapters that is harmless. Where the book starts a chapter
mid-page it puts real rules in the wrong bucket, and 57 mechanical lines were
sitting in the wrong chapter because of it.

Appendix A is the case that proves it. Its heading is on p. 159 and Appendix
B's is on p. 160, so the page partition handed the WHOLE of p. 160 to B --
including lines 9707-9747, which are Appendix A's Spiritual Skills table, CoR
reference, SPI recovery row and the entire Common Modifiers table.
`common_modifiers.json` claims 9725-9745 under "A", which is correct; the
claim landed in a bucket holding none of those lines, while B was charged for
twelve mechanical lines nobody had ever promised to extract. B read 0.0% and
A read 8.6%, and BOTH numbers were artefacts.

That is worth stating plainly because the coverage table invites the opposite
diagnosis. Six content files appear to declare a chapter id that disagrees
with their lines' pages, which reads as six misfiled extractions. Measured
against the headings instead, **all six declarations are right** -- the
partition was the only thing wrong. A fix that "corrected" the declarations
would have moved Appendix A's own quick-reference tables into the political
appendix and called it an improvement.

So the boundaries are derived here, from the book's own heading lines, rather
than from the page numbers. `chapters.json` is left alone: five other tools
read its startPage/endPage and none of them wants a line number.

A false LOW is as much an instrument defect as a false high, and it is the one
nobody investigates, because it looks like honest work remaining.

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


def heading_lines(global_lines: list[dict], chapters: list[dict]) -> dict[str, int]:
    """Where each chapter's heading actually sits, as a global line number.

    Matched on the chapter's id as the book prints it -- "Chapter 7.5:",
    "Appendix A:" -- rather than on its title, for two reasons. p. 170's index
    reprints every chapter TITLE, so a title match needs the page confinement
    to stay unambiguous; and `chapters.json`'s titles are editorial shorthand
    that the printed headings do not always match. 7.6 is titled "Spirit Vision
    Guide" there and prints as "Chapter 7.6: Spirit Vision - A Complete Guide",
    and 8 is "Equipment & Starting Wealth" against a heading that renders the
    ampersand differently. The id and its colon are the stable part.

    A chapter whose heading cannot be found is an ERROR, not a fallback. The
    quiet alternative -- defaulting to the top of the page -- restores exactly
    the defect this function exists to remove, on whichever chapter the
    extraction happened to mangle, and reports nothing.
    """
    by_page: dict[int, list[dict]] = {}
    for gl in global_lines:
        by_page.setdefault(gl["page"], []).append(gl)

    found: dict[str, int] = {}
    missing: list[str] = []
    for ch in chapters:
        # An id that is a letter is an appendix; a number is a chapter. The
        # colon is required, so "Chapter 1" in a cross-reference cannot match.
        #
        # The glossary is the one section the book heads with neither form --
        # it prints "Glossary of Terms" bare, and "G" is this project's id for
        # it rather than the book's. So it matches on its title, and it is
        # named here rather than handled by a general title fallback: a
        # fallback would silently absorb the next heading the extractor
        # mangles, which is what the SystemExit below exists to prevent.
        kind = "Appendix" if ch["id"].isalpha() else "Chapter"
        prefixes = (f"{kind} {ch['id']}:",) if ch["id"] != "G" else ("Glossary of Terms",)
        hit = next(
            (gl["n"] for gl in by_page.get(ch["startPage"], [])
             if gl["text"].strip().startswith(prefixes)),
            None,
        )
        if hit is None:
            missing.append(f"{ch['id']} ({ch['title']!r} on p.{ch['startPage']})")
        else:
            found[ch["id"]] = hit

    if missing:
        raise SystemExit(
            "coverage.py: no heading line found for chapter(s): "
            + "; ".join(missing)
            + ". The page-range fallback is deliberately not used -- it is the "
              "defect this partition exists to remove."
        )
    return found


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
    # The same numbering as text, which is what a person greps and every
    # `sourceLines` citation is read against. Written HERE so the two cannot
    # be numbered by different walks; until 6ed it had no writer at all.
    (OUT / "book.txt").write_text(
        "\n".join(f"{line['n']}: {line['text']}" for line in global_lines), encoding="utf-8"
    )

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

    # Boundaries by heading line, not by page: see the module docstring.
    heads = heading_lines(global_lines, chapters)
    ordered = sorted(chapters, key=lambda c: heads[c["id"]])
    starts = {c["id"]: heads[c["id"]] for c in ordered}
    ends = {
        c["id"]: (heads[ordered[i + 1]["id"]] - 1 if i + 1 < len(ordered)
                  else global_lines[-1]["n"])
        for i, c in enumerate(ordered)
    }

    page_of = {gl["n"]: gl["page"] for gl in global_lines}

    for ch in chapters:
        # Get lines for this chapter
        ch_lines = [
            (gl["n"], gl["text"])
            for gl in global_lines
            if starts[ch["id"]] <= gl["n"] <= ends[ch["id"]]
        ]
        # The pages the measurement ACTUALLY covered, derived from the same
        # boundaries as the percentage beside them. `chapters.json`'s
        # startPage/endPage would disagree here for exactly the chapters this
        # partition fixed -- Appendix A is declared 159-159 and its content
        # runs onto 160 -- so printing the declared range would put a correct
        # number next to a page range it was not measured over.
        ch_start = page_of[starts[ch["id"]]]
        ch_end = page_of[ends[ch["id"]]]

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
