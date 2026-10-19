"""Extract the rulebook PDF into page text and chapter spans.

Output goes to build/extract/ and is gitignored — it is an intermediate,
not content. Chapter boundaries come from the table of contents on pages
2-3, which lists each chapter's start page; a chapter ends on the page
before the next one starts.

The source PDF path is overridable (KNOWN_ISSUES: "Python tooling is not
reproducible off this machine") so this runs on any machine or CI without
the original file, hardcoded only as the default for this workstation:
  - `PATHWAYS_RULEBOOK_PDF` environment variable, or
  - `--pdf <path>` on the command line (takes precedence over the env var).
"""
import argparse, json, os, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
DEFAULT_PDF = pathlib.Path(os.path.expanduser("~/Downloads/Mobile Devices/Mr.Worms LOM TTRPG Rulebook v6.9i.pdf"))
OUT = ROOT / "build" / "extract"


def resolve_pdf_path(cli_arg: str | None = None) -> pathlib.Path:
    if cli_arg:
        return pathlib.Path(cli_arg)
    env = os.environ.get("PATHWAYS_RULEBOOK_PDF")
    if env:
        return pathlib.Path(env)
    return DEFAULT_PDF

# (id, title, startPage) read from the table of contents, pages 2-3, of
# v6.9i. v6.9d numbered Divination 6.5, Ritualistic Magic 7, Summoning 7.5 and
# the Spirit Vision guide 7.6; v6.9i adds Rest as Chapter 7, folds Summoning
# into Ritualistic Magic (Ch. 9, Section VI) and the guide into Chapter 5's
# end, and renumbers everything after.
CHAPTERS = [
    ("1",  "Introduction",                  5),
    ("2",  "Core Rules",                    7),
    ("3",  "Character Creation",           11),
    ("4",  "Spirituality",                 34),
    ("5",  "Combat",                       37),
    ("6",  "The Beyonder System",          47),
    ("7",  "Rest",                         52),
    ("8",  "Divination Arts",              54),
    ("9",  "Ritualistic Magic",            59),
    ("10", "Equipment & Starting Wealth",  65),
    ("11", "Sequence 9 Potion Effects",    71),
    ("12", "Sequence 8 Potion Effects",    83),
    ("13", "Sequence 7 Potion Effects",    97),
    ("14", "Sequence 6 Potion Effects",   115),
    ("15", "Boon Granting",               130),
    ("16", "Non-Standard Pathways",       132),
    ("17", "Non-Standard Potion Pathways", 139),
    ("A",  "Quick Reference Tables",      146),
    ("B",  "The Political World",         147),
    ("C",  "Orthodox Churches",           149),
    ("G",  "Glossary of Terms",           154),
]


def main() -> None:
    from pypdf import PdfReader

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pdf", default=None, help="path to the rulebook PDF (overrides PATHWAYS_RULEBOOK_PDF)")
    args = parser.parse_args()
    pdf = resolve_pdf_path(args.pdf)

    if not pdf.exists():
        raise SystemExit(f"rulebook not found at {pdf}")

    reader = PdfReader(str(pdf))
    pages = [
        {"page": i + 1, "text": p.extract_text() or ""}
        for i, p in enumerate(reader.pages)
    ]

    last = len(pages)
    chapters = []
    for idx, (cid, title, start) in enumerate(CHAPTERS):
        end = CHAPTERS[idx + 1][2] - 1 if idx + 1 < len(CHAPTERS) else last
        chapters.append({"id": cid, "title": title, "startPage": start, "endPage": end})

    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "pages.json").write_text(json.dumps(pages, ensure_ascii=False, indent=1), encoding="utf-8")
    (OUT / "chapters.json").write_text(json.dumps(chapters, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{len(pages)} pages, {len(chapters)} chapters -> {OUT}")


if __name__ == "__main__":
    main()
