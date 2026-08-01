"""Extract the two ranged-combat tables (Ch. 5, p. 39-40).

RANGE PENALTIES (global lines 2648-2664) and OTHER RANGED MODIFIERS
(2665-2685) are both two-column tables flattened into alternating lines. The
anchor is that every MODIFIER cell opens with a sign -- "+0", "-1", "-6 or
worse (GM discretion)", "+ weapon's Acc ..." -- and no condition cell does
("100+ meters" opens with a digit). So a line is a modifier exactly when it
starts with + or -, which makes the line before it the condition. Pairing by
position instead would shift the whole table the first time a cell wrapped.

Three cells carry more than one number, and getting them wrong is the point
of this file:

  * "+ weapon's Acc +2 (max Acc +2 total)" -- reading the first signed integer
    would call this +2. It is not a number at all: it is the weapon's Acc, and
    THIS BOOK PRINTS NO WEAPON TABLE (see the defenses slice). So both Aim
    rows are flagged and carry no modifier.
  * "-2 ranged (but -4 in melee)" -- one row, two scopes. The melee value is
    kept separately rather than dropped, because a prone target is easier to
    hit in reach and harder at distance, and an app applying -2 to a kick
    would have the sign right and the rule wrong.
  * "-3 to -9 depending on severity" -- a range the GM picks inside, like the
    fright triggers. The gentler end is the modifier and the harsher end is
    the worst case; picking one silently would make the GM's call for them.

The bands OVERLAP at exactly 100 metres: "51-100 meters" is -5 and "100+
meters" is -6. That is the book's own arithmetic, so it is recorded, not
repaired -- verify() asserts the overlap still exists, and
com.pathways.rules.Ranged documents which row wins.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "5"
RANGE_RANGE = (2648, 2664)
MODIFIER_RANGE = (2665, 2685)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADER = {
    "Range Penalties", "Distance to Target", "Penalty",
    "Other Ranged Modifiers", "Condition", "Modifier",
}
# A modifier cell always opens with a sign; a condition cell never does.
MODIFIER_CELL = re.compile(r"^[+-]")
# Any dash the typesetter used: hyphen, en dash, em dash.
DASH = r"[-‐-―]"
BAND = re.compile(
    rf"^(?:Up to (?P<upto>\d+)|(?P<from>\d+)\s*(?:{DASH}\s*(?P<to>\d+)|(?P<open>\+)))\s*meters"
)
SIGNED = re.compile(r"[+-]\d+")
WORST = re.compile(r"to\s+(-\d+)")
IN_MELEE = re.compile(r"\((?:but\s+)?(-\d+)\s+in melee\)")
WEAPON_ACC = "weapon's Acc"
GM_DISCRETION = "GM discretion"

EXPECTED_BANDS = 7
EXPECTED_MODIFIERS = [
    "Target is stationary", "Target walking (up to 3 meters/sec)",
    "Target running (4+ meters/sec)", "Shooter moved this turn",
    "Shooter used Aim last turn", "Shooter used Aim for 3+ turns",
    "Target is prone", "Darkness or heavy fog", "Target has cover (half body)",
]


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


def pair(lines):
    """(condition, modifier) for each row, cut on the modifier's leading sign."""
    rows = []
    for i, line in enumerate(lines):
        if not MODIFIER_CELL.match(line):
            continue
        if i == 0:
            raise ValueError(f"modifier {line!r} has no condition before it")
        rows.append((lines[i - 1], line))
    return rows


def parse_bands(lines):
    out = []
    for distance, cell in pair(lines):
        band = BAND.match(distance)
        if not band:
            raise ValueError(f"unreadable distance band: {distance!r}")
        modifier = SIGNED.search(cell)
        if not modifier:
            raise ValueError(f"band {distance!r} prints no penalty: {cell!r}")
        entry = {
            "printed": distance,
            "penalty": int(modifier.group()),
            "printedPenalty": cell,
        }
        if band.group("upto"):
            entry["from"], entry["to"] = 0, int(band.group("upto"))
        else:
            entry["from"] = int(band.group("from"))
            if band.group("to"):
                entry["to"] = int(band.group("to"))
            # An open-ended band carries no "to" at all.
        if GM_DISCRETION in cell:
            entry["gmDiscretion"] = True
        out.append(entry)
    return out


def parse_modifiers(lines):
    out = []
    for condition, cell in pair(lines):
        entry = {"condition": condition, "printed": cell}
        if WEAPON_ACC in cell:
            # Not a number this app can know: no weapon table is printed.
            entry["weaponAcc"] = True
            out.append(entry)
            continue
        modifier = SIGNED.search(cell)
        if not modifier:
            raise ValueError(f"condition {condition!r} prints no modifier: {cell!r}")
        entry["modifier"] = int(modifier.group())
        worst = WORST.search(cell)
        if worst:
            entry["worstCase"] = int(worst.group(1))
        melee = IN_MELEE.search(cell)
        if melee:
            entry["melee"] = int(melee.group(1))
        out.append(entry)
    return out


def verify(bands, modifiers):
    if len(bands) != EXPECTED_BANDS:
        raise ValueError(f"expected {EXPECTED_BANDS} range bands, got {len(bands)}")
    if [b["from"] for b in bands] != sorted(b["from"] for b in bands):
        raise ValueError("range bands are no longer in ascending order")
    open_ended = [b for b in bands if "to" not in b]
    if len(open_ended) != 1:
        raise ValueError(f"expected exactly one open-ended band, got {len(open_ended)}")

    # The book's own bands overlap at 100 metres. Recorded, never repaired: if
    # this stops being true the DATA changed, and that is the finding.
    banded = [b for b in bands if "to" in b]
    overlaps = [
        (a["printed"], b["printed"])
        for a in banded for b in bands
        if a is not b and b["from"] <= a["to"] and a["from"] <= b.get("to", a["to"])
    ]
    if not overlaps:
        raise ValueError("the 51-100 / 100+ overlap is gone; Ranged documents it")

    got = [m["condition"] for m in modifiers]
    if got != EXPECTED_MODIFIERS:
        raise ValueError(f"ranged modifiers changed: {got} != {EXPECTED_MODIFIERS}")
    acc = [m["condition"] for m in modifiers if m.get("weaponAcc")]
    if len(acc) != 2:
        raise ValueError(f"expected the two Aim rows to need a weapon's Acc, got {acc}")
    if not any("melee" in m for m in modifiers):
        raise ValueError("no row carries a separate melee value; prone used to")


def write(name, entries, spans):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: spans}, "entries": entries}
    (OUT / f"{name}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def main():
    byn = load_lines()
    bands = parse_bands(stream(byn, *RANGE_RANGE))
    modifiers = parse_modifiers(stream(byn, *MODIFIER_RANGE))
    verify(bands, modifiers)

    write("range_penalties", bands, [list(RANGE_RANGE)])
    write("ranged_modifiers", modifiers, [list(MODIFIER_RANGE)])

    print(f"range_penalties: {len(bands)} bands")
    for band in bands:
        print(f"  {band['printed']:38} {band['penalty']:+d}")
    print(f"ranged_modifiers: {len(modifiers)} rows")
    for row in modifiers:
        value = "weapon's Acc" if row.get("weaponAcc") else f"{row['modifier']:+d}"
        print(f"  {row['condition']:38} {value}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
