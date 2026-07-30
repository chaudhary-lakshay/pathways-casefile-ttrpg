"""Extract the Fright Check tables (Ch. 5, p. 43-44) into committed JSON.

Two tables, two shapes.

TRIGGERS (global lines 2904-2915) is the familiar label/value pair table:

    Minor supernatural event (strange sounds, moved objects)
     +2
    Encountering a monster or undead creature
     -2 to -6

The modifier column is not always a number: two rows print a RANGE and one
prints "-8 or worse". The gentler end is the one every shape has, so
`modifier` carries it and `worstCase`/`openEnded` say what else the book gave.
Collapsing "-2 to -6" to a single figure would invent a severity the GM is
supposed to choose.

EFFECTS (global lines 2924-2994) is a THREE-column table -- Fright, Awe,
Confusion -- flattened into a single stream of wrapped lines:

     4-9
    Stunned 1 sec; roll vs. Will each sec to
    recover.
    Stunned 1 sec; roll vs. Will each sec to
    recover.
    Stunned 1 sec; roll vs. IQ each sec to
    recover.

Cells are NOT a fixed number of lines (rows 22-23 and 24-29 run to seven),
so lines cannot be split evenly three ways -- that mis-cuts exactly those two
rows while looking right everywhere else. Instead a cell ends when its text
ends a SENTENCE, with "vs." excluded as the one abbreviation the table uses
("roll vs." would otherwise end the cell mid-clause on the 12 row). verify()
then requires exactly three cells in every row, so a parse that drifts fails
loudly rather than shipping a shifted table.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "5"
TRIGGER_RANGE = (2904, 2915)
EFFECT_RANGE = (2924, 2994)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADER = {"Trigger", "Will Modifier", "Roll", "Fright", "Awe", "Confusion"}
COLUMNS = ("fright", "awe", "confusion")

# "+2", "-4", "-2 to -6", "-8 or worse". Minus signs print as U+2212.
MINUS = "-−"
MODIFIER = re.compile(
    rf"^([+{MINUS}]?\d+)(?:\s*to\s*([+{MINUS}]?\d+)|\s*(or worse))?$"
)
# "4-9", "12", "35+" -- an en dash separates the ends of a band.
BAND = re.compile(rf"^(\d+)(?:[{MINUS}–—](\d+)|(\+))?$")
# The only abbreviation in the effects table that ends in a period.
ABBREVIATIONS = ("vs.",)

EXPECTED_BANDS = [(4, 9), (10, 11), (12, 12), (13, 15), (16, 21), (22, 23),
                  (24, 29), (30, 34), (35, None)]


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    out = []
    for n in range(start, end + 1):
        text = byn.get(n)
        if text is None:
            continue
        stripped = text.strip()
        if not stripped or FURNITURE.match(stripped) or stripped in HEADER:
            continue
        out.append(stripped)
    return out


def normalise(value):
    """The PDF's U+2212 minus is not the ASCII one int() wants."""
    return value.replace("−", "-")


def parse_triggers(lines):
    if len(lines) % 2:
        # Pairing would silently drop the odd one out and still ship five
        # rows, which is exactly the drift this extractor exists to catch.
        raise ValueError(f"trigger stream has {len(lines)} lines; label/value pairs must be even")
    rows = []
    for i in range(0, len(lines) - 1, 2):
        label, value = lines[i], lines[i + 1]
        match = MODIFIER.match(value)
        if not match:
            raise ValueError(f"unparsed fright modifier for {label!r}: {value!r}")
        first, second, worse = match.groups()
        row = {"trigger": label, "printed": value, "modifier": int(normalise(first))}
        if second:
            # "-2 to -6": the book hands the GM a range, so both ends ship and
            # `modifier` is the gentler end the row prints first.
            row["worstCase"] = int(normalise(second))
        if worse:
            row["openEnded"] = True
        rows.append(row)
    return rows


def ends_cell(text):
    if not text.endswith((".", "!", "?")):
        return False
    return not text.endswith(ABBREVIATIONS)


def parse_effects(lines):
    """Rows of the Fright / Awe / Confusion table, cells rejoined."""
    rows = []
    cells, buffer = [], ""
    for line in lines:
        band = BAND.match(line)
        if band and not buffer:
            if cells:
                rows[-1]["cells"] = cells
            low, high, plus = band.groups()
            rows.append({
                "low": int(low),
                # "35+" has no ceiling at all; a single-value band such as
                # "12" is its own ceiling.
                "high": None if plus else int(high) if high else int(low),
                "printed": line,
            })
            cells = []
            continue
        if not rows:
            raise ValueError(f"effect text before any roll band: {line!r}")
        buffer = f"{buffer} {line}".strip()
        if ends_cell(buffer):
            cells.append(buffer)
            buffer = ""
    if buffer:
        raise ValueError(f"unterminated effect cell: {buffer!r}")
    if cells:
        rows[-1]["cells"] = cells

    out = []
    for row in rows:
        cells = row.pop("cells", [])
        if len(cells) != len(COLUMNS):
            raise ValueError(
                f"band {row['printed']!r} parsed {len(cells)} cells, expected "
                f"{len(COLUMNS)}: {cells}"
            )
        out.append({**row, **dict(zip(COLUMNS, cells))})
    return out


def verify(triggers, effects):
    if len(triggers) != 5:
        raise ValueError(f"expected 5 trigger rows, got {len(triggers)}")
    bands = [(r["low"], r["high"]) for r in effects]
    if bands != EXPECTED_BANDS:
        raise ValueError(f"fright bands changed: {bands} != {EXPECTED_BANDS}")
    # The bands must tile 4 upward with no gap and no overlap, or a roll would
    # land on nothing (or on two rows) at the table.
    for lower, upper in zip(effects, effects[1:]):
        if lower["high"] is None or upper["low"] != lower["high"] + 1:
            raise ValueError(
                f"bands do not tile: {lower['printed']} then {upper['printed']}"
            )


def write(name, entries, spans):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: spans}, "entries": entries}
    (OUT / f"{name}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def main():
    byn = load_lines()
    triggers = parse_triggers(stream(byn, *TRIGGER_RANGE))
    effects = parse_effects(stream(byn, *EFFECT_RANGE))
    verify(triggers, effects)

    write("fright_triggers", triggers, [list(TRIGGER_RANGE)])
    write("fright_effects", effects, [list(EFFECT_RANGE)])

    print(f"fright_triggers: {len(triggers)} rows")
    print(f"fright_effects: {len(effects)} bands, {[r['printed'] for r in effects]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
