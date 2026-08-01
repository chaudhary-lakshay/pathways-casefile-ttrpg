"""Extract the two Optional tables that multiply a hit (Ch. 5, p. 40-41).

HIT LOCATIONS (global lines 2742-2783) is four columns -- Target, Penalty,
multiplier, Special Effect -- and WOUNDING MULTIPLIERS (2789-2815) is three.
Both flatten into one line per cell, so the whole job is knowing where a row
starts.

The anchor is the PENALTY column: a line is a penalty exactly when it is a
bare signed integer ("+0", "-7"). Nothing else in either table is, and every
effect cell that contains a signed number wraps it in prose ("Knockdown at -5;
risk of blindness"). The line before a penalty is the target, and everything
after it belongs to that row. Pairing by position would shift the entire table
below the first cell that wrapped -- and two of them do:

  * Neck prints "Cr x1.5 / Cut x2 / Imp x2" across two lines. Its multiplier
    is not one number: it DEPENDS ON THE DAMAGE TYPE, and reading the first
    would call a cutting hit x1.5.
  * Vitals prints "x3 (imp/pi only)" across two lines, and means it -- a punch
    to the vitals is x1. Dropping "only)" as noise would triple every crushing
    hit there.

So the multiplier cell is consumed while a line still contains a "x", or while
the text taken so far has an unclosed bracket. Both wraps are then handled for
a stated reason rather than by counting lines, and a wrapped EFFECT cell (none
today) stays with its own row instead of being read as a multiplier.

Abbreviations are only emitted where the book prints one. "Cr", "Cut" and
"Imp" appear in the Neck cell and "imp/pi" in the Vitals cell, so those four
are the book's own. Piercing (small) and Large Piercing get none: GURPS writes
them "pi-" and "pi+", but THIS book never does, and inventing a key is how a
lookup starts answering for a row the rulebook never wrote.

The stacking sentence (2816-2817) carries the worked example the Kotlin side
implements, so verify() pins it: if the book stops saying the two multipliers
compose, com.pathways.rules.Wounds is wrong and should fail loudly here.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "5"
LOCATION_RANGE = (2743, 2784)
MULTIPLIER_RANGE = (2790, 2816)
STACKING_RANGE = (2817, 2818)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADER = {
    "Target", "Penalty", "×", "Special Effect on Hit",
    "Damage Type", "Examples",
}

PENALTY = re.compile(r"^[+-]\d+$")
# "x1", "x1.5", "x3 (imp/pi only)" -- the cell opens with the sign.
PLAIN = re.compile(r"^×(\d+(?:\.\d+)?)")
# "Cr x1.5 / Cut x2 / Imp x2" -- a type name in front of each number.
TYPED = re.compile(r"([A-Za-z]+)\s*×(\d+(?:\.\d+)?)")
# "(imp/pi only)" -- the row applies to those types and no others.
ONLY = re.compile(r"\(([a-z/]+)\s+only\)")

# Printed in the Neck and Vitals cells; not invented here. See the docstring
# for why Piercing (small) and Large Piercing are deliberately unabbreviated.
ABBREVIATIONS = {
    "Crushing": "cr",
    "Cutting": "cut",
    "Impaling": "imp",
    "Piercing": "pi",
}

EXPECTED_LOCATIONS = [
    "Torso", "Arm", "Leg", "Hand", "Neck", "Face", "Skull", "Vitals", "Eye",
]
EXPECTED_TYPES = [
    "Crushing", "Cutting", "Impaling", "Piercing (small)", "Piercing",
    "Large Piercing", "Burning", "Toxic",
]
STACKING = "stack with damage-type multipliers"


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


def rows(lines):
    """(target, penalty, cells) per row, cut on the bare signed integer."""
    starts = [i for i, line in enumerate(lines) if PENALTY.fullmatch(line)]
    if not starts:
        raise ValueError("no penalty cell found; the table's anchor column is gone")
    out = []
    for pos, i in enumerate(starts):
        if i == 0:
            raise ValueError(f"penalty {lines[i]!r} has no target before it")
        # The row runs to the line before the NEXT row's target.
        end = starts[pos + 1] - 1 if pos + 1 < len(starts) else len(lines)
        out.append((lines[i - 1], int(lines[i]), lines[i + 1:end]))
    return out


def split_cells(cells):
    """(multiplier text, effect text): the multiplier runs while a 'x' or an
    unclosed bracket says the cell has not finished."""
    taken = []
    for cell in cells:
        joined = " ".join(taken)
        unclosed = joined.count("(") > joined.count(")")
        if "×" not in cell and not unclosed:
            break
        taken.append(cell)
    if not taken:
        raise ValueError(f"row has no multiplier cell: {cells!r}")
    return " ".join(taken), " ".join(cells[len(taken):])


def parse_multiplier(text):
    """(default, by-type) for one multiplier cell."""
    typed = {t.lower(): float(v) for t, v in TYPED.findall(text)}
    plain = PLAIN.match(text)
    if plain:
        only = ONLY.search(text)
        if only:
            # "x3 (imp/pi only)": the number is real but reaches two types.
            # Everything else takes the plain x1 the book leaves it at.
            return 1.0, {t: float(plain.group(1)) for t in only.group(1).split("/")}
        if typed:
            raise ValueError(f"cell is both plain and typed: {text!r}")
        return float(plain.group(1)), {}
    if not typed:
        raise ValueError(f"unreadable multiplier cell: {text!r}")
    # A typed cell lists the types the book thought worth printing. An unlisted
    # one takes x1: guessing which listed type it is nearest would be inventing.
    return 1.0, typed


def parse_locations(lines):
    out = []
    for target, penalty, cells in rows(lines):
        printed, effect = split_cells(cells)
        if not effect:
            raise ValueError(f"{target} prints no special effect")
        default, by_type = parse_multiplier(printed)
        entry = {
            "target": target,
            "penalty": penalty,
            "printedMultiplier": printed,
            "multiplier": default,
            "effect": effect,
        }
        if by_type:
            entry["byType"] = by_type
        out.append(entry)
    return out


def parse_types(lines):
    out = []
    for i, line in enumerate(lines):
        if not PLAIN.fullmatch(line):
            continue
        if i == 0 or i + 1 >= len(lines):
            raise ValueError(f"multiplier {line!r} is missing a neighbouring cell")
        name = lines[i - 1]
        entry = {
            "type": name,
            "multiplier": float(PLAIN.match(line).group(1)),
            "examples": lines[i + 1],
        }
        if name in ABBREVIATIONS:
            entry["abbreviation"] = ABBREVIATIONS[name]
        out.append(entry)
    return out


def verify(locations, types, stacking):
    got = [l["target"] for l in locations]
    if got != EXPECTED_LOCATIONS:
        raise ValueError(f"hit locations changed: {got} != {EXPECTED_LOCATIONS}")
    got = [t["type"] for t in types]
    if got != EXPECTED_TYPES:
        raise ValueError(f"damage types changed: {got} != {EXPECTED_TYPES}")

    # Two rows carry a multiplier that is not one number. If either becomes
    # plain, the DATA changed and Wounds.multiplierFor is answering for a
    # reading the book no longer prints.
    neck = next(l for l in locations if l["target"] == "Neck")
    if set(neck.get("byType", {})) != {"cr", "cut", "imp"}:
        raise ValueError(f"the Neck row no longer varies by damage type: {neck}")
    vitals = next(l for l in locations if l["target"] == "Vitals")
    if set(vitals.get("byType", {})) != {"imp", "pi"} or vitals["multiplier"] != 1.0:
        raise ValueError(f"the Vitals row no longer reads 'imp/pi only': {vitals}")

    # Every abbreviation must resolve to exactly one row, or a lookup by "pi"
    # would silently answer with whichever piercing row was parsed last.
    keys = [t["abbreviation"] for t in types if "abbreviation" in t]
    if len(keys) != len(set(keys)):
        raise ValueError(f"damage-type abbreviations are not unique: {keys}")
    for location in locations:
        for key in location.get("byType", {}):
            if key not in keys:
                raise ValueError(f"{location['target']} names an unknown type {key!r}")

    if STACKING not in " ".join(stacking):
        raise ValueError(f"the stacking sentence is gone: {stacking}")


def write(name, entries, spans):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: spans}, "entries": entries}
    (OUT / f"{name}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def main():
    byn = load_lines()
    locations = parse_locations(stream(byn, *LOCATION_RANGE))
    types = parse_types(stream(byn, *MULTIPLIER_RANGE))
    stacking = stream(byn, *STACKING_RANGE)
    verify(locations, types, stacking)

    write("hit_locations", locations, [list(LOCATION_RANGE)])
    write("wounding_multipliers", types, [list(MULTIPLIER_RANGE), list(STACKING_RANGE)])

    print(f"hit_locations: {len(locations)} rows")
    for row in locations:
        print(f"  {row['target']:8} {row['penalty']:+d}  {row['printedMultiplier']}")
    print(f"wounding_multipliers: {len(types)} rows")
    for row in types:
        print(f"  {row['type']:18} x{row['multiplier']:<5} {row.get('abbreviation', '-')}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
