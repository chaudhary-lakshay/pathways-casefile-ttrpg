"""Report how much of each chapter has been claimed by extracted content.

A content file may declare which source lines it consumed:

    { "sourceLines": { "3": [[1717, 2100]] }, "entries": [...] }

Everything not claimed is counted as unclaimed. A line is "mechanical" if
it contains a die expression, a signed modifier, a point cost, or an
attribute offset — prose without those is flavour and may stay unclaimed.

sourceLines are global line numbers as emitted in build/extract/lines.json;
line 1 is the first line of page 1; pages are concatenated in order with no
separators.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "build" / "extract"
CONTENT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

MECHANICAL = re.compile(r"\d+d[-+]?\d*|[+-]\d+\b|\d+\s*pts?\b|\bAttr[+-]\d+")


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
        for f in CONTENT.glob("*.json"):
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
