"""Render the rulebook PDF to Markdown with markitdown (pdfminer + pdfplumber).

Output goes to build/extract/book.md and is gitignored, exactly like
`coverage.py`'s book.txt. It is a SECOND rendering, never a replacement:
`book.txt`'s global line numbers are this repo's citation anchor (coverage.py
compares line sets; the test_tools_*.py suite pins verbatim book lines by number), and
this file's line numbering is different and must never be cited as those.

Why it exists: pdfplumber recovers ruled tables as pipe rows that pypdf
flattens into prose. Measured on v6.9d: 1177 table rows here, 0 in book.txt.

Why the repair table exists: pdfminer's glyph mapping for this PDF's symbol
font is worse than pypdf's. It loses every "->" arrow (emitting a bare "fi"
ligature) and every bullet (emitting "(cid:127)"). Both are silent
corruptions -- "Status 1 fi +1" reads as text -- so they are repaired here
and the counts are asserted, not assumed.

Source PDF path resolves the same way as extract_pdf.py:
  - `--pdf <path>`, or `PATHWAYS_RULEBOOK_PDF`, else the workstation default.
"""
import argparse, pathlib, re, sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from extract_pdf import resolve_pdf_path, OUT

# (pattern, replacement, what the glyph actually is in the PDF)
REPAIRS = [
    (re.compile(r"\(cid:127\)"), "\u2022"),          # Wingdings bullet
    # The arrow can also sit at end of line, where a bare [ )] lookahead finds nothing.
    (re.compile(r"(?<=[ (])fi(?=[ )]|$)", re.M), "\u2192"),  # arrow lost to the fi ligature
]


def repair(text: str) -> str:
    for pattern, replacement in REPAIRS:
        text = pattern.sub(replacement, text)
    return text


def cross_check_arrows(md: str) -> None:
    """Verify the `fi` repair against pypdf's independent rendering of the PDF.

    Nothing inside this file can catch a REAL standalone "fi" ("a sci fi
    campaign") being turned into an arrow: the repair regex and any guard built
    from it share the same blind spots, so a guard reusing the pattern is
    vacuous against exactly the case that matters. The check therefore reads an
    artifact produced by a DIFFERENT parser. pypdf maps this glyph correctly,
    so book.txt's arrow count is the expected answer. Measured on v6.9d: 18
    arrows in book.txt, and zero standalone "fi" that is not one.
    """
    book_txt = OUT / "book.txt"
    if not book_txt.exists():
        print("book.txt absent; arrow count unverified (run extract_pdf.py, then coverage.py)")
        return

    expected = book_txt.read_text(encoding="utf-8").count("→")
    actual = md.count("→")
    if actual != expected:
        raise SystemExit(
            f"repaired {actual} arrows but book.txt has {expected}; "
            "a real standalone 'fi' may have been substituted"
        )


def convert(pdf: pathlib.Path) -> str:
    from markitdown import MarkItDown

    return repair(MarkItDown().convert(str(pdf)).text_content)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pdf", default=None, help="path to the rulebook PDF")
    args = parser.parse_args()

    pdf = resolve_pdf_path(args.pdf)
    if not pdf.exists():
        raise SystemExit(f"rulebook not found at {pdf}")

    md = convert(pdf)
    if "(cid:" in md:
        raise SystemExit("unmapped glyphs remain; add them to REPAIRS")

    stray = re.findall(r"(?<=[ (])fi(?=[ )]|$)", md, re.M)
    if stray:
        raise SystemExit(f"{len(stray)} unrepaired arrow ligatures remain")

    cross_check_arrows(md)

    OUT.mkdir(parents=True, exist_ok=True)
    out = OUT / "book.md"
    out.write_text(md, encoding="utf-8")

    rows = sum(1 for line in md.splitlines() if line.startswith("|"))
    print(f"wrote {out} ({out.stat().st_size} bytes, {rows} table rows)")


if __name__ == "__main__":
    main()
