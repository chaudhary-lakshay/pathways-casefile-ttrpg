"""Extract the rulebook PDF into page text and chapter spans.

Output goes to build/extract/ and is gitignored — it is an intermediate,
not content. Chapter boundaries come from the table of contents on pages
2-3, which lists each chapter's start page; a chapter ends on the page
before the next one starts.
"""
import json, os, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
PDF = pathlib.Path(os.path.expanduser("~/Downloads/Mr.Worms LOM TTRPG Rulebook v6.9d.pdf"))
OUT = ROOT / "build" / "extract"

# (id, title, startPage) read from the table of contents, pages 2-3.
CHAPTERS = [
    ("1",   "Introduction",                  5),
    ("2",   "Core Rules",                    7),
    ("3",   "Character Creation",           10),
    ("4",   "Spirituality",                 33),
    ("5",   "Combat",                       35),
    ("6",   "The Beyonder System",          44),
    ("6.5", "Divination Arts",              53),
    ("7",   "Ritualistic Magic",            60),
    ("7.5", "Summoning Spiritual Creatures", 75),
    ("7.6", "Spirit Vision Guide",          77),
    ("8",   "Equipment & Starting Wealth",  79),
    ("9",   "Sequence 9 Potion Effects",    85),
    ("10",  "Sequence 8 Potion Effects",    97),
    ("11",  "Sequence 7 Potion Effects",   111),
    ("12",  "Sequence 6 Potion Effects",   129),
    ("13",  "Boon Granting",               144),
    ("14",  "Non-Standard Pathways",       146),
    ("15",  "Non-Standard Sequence 8",     153),
    ("A",   "Quick Reference Tables",      159),
    ("B",   "The Political World",         160),
    ("C",   "Orthodox Churches",           162),
    ("G",   "Glossary of Terms",           167),
]


def main() -> None:
    from pypdf import PdfReader

    if not PDF.exists():
        raise SystemExit(f"rulebook not found at {PDF}")

    reader = PdfReader(str(PDF))
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
