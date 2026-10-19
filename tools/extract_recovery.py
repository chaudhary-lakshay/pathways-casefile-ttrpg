"""Extract the Natural Recovery rows of the HP recovery table (Ch. 5, p. 42).

Source shape (verified against build/extract/lines.json, not assumed). The
table prints "Method / HP Recovered / Time Required / Notes" flattened into
three lines a row, and none of these three rows wraps:

    Natural Recovery (HT roll success)
    1 HP (2 HP w/ Very Rapid Healing)
    Per day; rest & food required

So rows ARE paired positionally here, and parse() refuses a count that is not
a multiple of three rather than re-cutting a wrapped cell into the next row.

Only the three Natural Recovery rows are read, and verify() pins them by
name. The first is a ROLL -- the daily HT roll line 2897 names -- and the app
offers it; the two after it are what that roll restores for a larger body.
The table's other three methods stay out, each for its own reason, and so
does the separate Regeneration table printed under it (2882-2895):

    First Aid            "once per wound": the app tracks no wounds
    Physician            a skill roll the sheet already offers
    Healing advantage    an advantage in no catalogue
    Regeneration         rates, per 12 hours down to per second: clocks

Every cell is kept VERBATIM and no arithmetic is encoded. "2x base rate" for
"20-29 HP" does not say whether the band is read on maximum or current HP,
and "scales proportionally" prints no band past 39; both are the GM's, so the
app prints the rows beside the roll and applies nothing.
"""
import json, pathlib, sys

import extract_pathways as pathways
import extract_pools as pools

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

RANGE = (2927, 2935)
CELLS = ("method", "recovered", "notes")

EXPECTED = [
    "Natural Recovery (HT roll success)",
    "Natural Recovery (20–29 HP)",
    "Natural Recovery (30–39 HP)",
]


def parse(lines):
    """Rows of the table, three printed cells each."""
    if len(lines) % len(CELLS):
        raise ValueError(f"{len(lines)} cells is not a multiple of {len(CELLS)}: {lines!r}")
    return [dict(zip(CELLS, lines[i:i + len(CELLS)])) for i in range(0, len(lines), len(CELLS))]


def verify(rows):
    methods = [r["method"] for r in rows]
    if methods != EXPECTED:
        raise ValueError(f"recovery methods changed: {methods} != {EXPECTED}")


def main():
    byn = pools.load_lines()
    rows = parse(pools.stream(byn, *RANGE))
    verify(rows)

    chapter = pathways.chapter_at(RANGE[0], pathways.heading_of_chapters(pathways.load_lines()))
    if chapter is None:
        raise ValueError(f"no chapter heading precedes line {RANGE[0]}")
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {chapter: [list(RANGE)]}, "entries": rows}
    (OUT / "hp_recovery.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )
    print(f"hp_recovery: {len(rows)} rows")
    return 0


if __name__ == "__main__":
    sys.exit(main())
