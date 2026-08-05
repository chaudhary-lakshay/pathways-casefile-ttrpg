"""Extract Ch. 7's Section IV power sources A-E and the Time & Complexity
table above it (p. 65-66).

THE BOOK PRINTS SPI COST TWICE, ON TWO DIFFERENT AXES
------------------------------------------------------
Section V (p. 67) says every ritual "falls into one of three weights that
determine its base difficulty AND SPI cost", and prints:

    Light  +0   2 SPI      Moderate  -2   4-6 SPI     Heavy  -4   8+ SPI

Section IV.A (p. 65-66) prints a DIFFERENT table, four rows deep, under the
sentence "The caster pays SPI equal to the ritual's complexity cost":

    Simple effect, short duration, single target                2-3 SPI
    Moderate effect, hours to days, single target               4-6 SPI
    Powerful effect, long duration or wider scope               7-10 SPI
    Major effect (territory-scale, permanent, or very powerful) 11-15+ SPI

They do not line up. Only Moderate agrees. `ritual_weights.json` is the first
of those and is what the app has charged fuel from since M4's ritual slice.

WHICH ONE THE BOOK ITSELF USES: ALL SIX WORKED RITUALS
-------------------------------------------------------
Section VIII prints six worked rituals, each naming its category (hence its
weight) and its SPI cost. Three of the six fall OUTSIDE the weight column:

  Ritual 2  Divination   = Light    weight says 2       charges 3 SPI
  Ritual 3  Summoning    = Heavy    weight says 8+      charges 7 SPI
  Ritual 4  Curse        = Moderate weight says 4-6     charges 7 SPI

All six fall inside the complexity ladder. And every parenthetical the book
gives to justify a cost is written in the LADDER's vocabulary, never a weight
name -- "3 SPI (simple category, single fact)", "4 SPI (moderate duration,
single target)".

So these are two axes, not two printings of one column. The WEIGHT is the
ritual's category and gives its base difficulty modifier; the COMPLEXITY is
its scope -- duration, target count, territory -- and gives its SPI cost, its
base time, and its Rushed/Extended modifier. Section V's claim that the weight
determines the SPI cost loses its second half to the book's own examples, the
same way the Major Wound sentence lost to its worked example (CLAUDE.md 6n).

Nothing is merged and nothing is straightened. `ritual_weights.json` keeps its
printed `spi` column exactly as Section V prints it, and verify() FAILS if
that column ever stops disagreeing -- a silent reconciliation is the one
outcome this slice exists to prevent.

THE TIME TABLE IS THE EVIDENCE THAT THE LADDER IS AN AXIS AND NOT A LIST
-------------------------------------------------------------------------
"Time & Complexity Reference" (p. 65) keys Base Time, Rushed and Extended on
four complexity names: Light, Moderate, Heavy, MAJOR. Section V prints only
three weights and no Major at all. That fourth row is what makes the ladder a
real axis with a tier the weight table cannot express -- and it is why Major
needs no base difficulty modifier here: it is a complexity, not a weight.

The two tables are joined by POSITION, which is only defensible because two of
the four rows also agree by name (Moderate, Major) at the same index. verify()
pins that agreement; if it went, the join would need rereading rather than
reusing -- the same standard the appendix's matching "Pi" had to meet before
its siblings could be read as labels (CLAUDE.md 6v).

B THROUGH E ARE SENTENCES, SO THEIR NUMBERS STAY IN KOTLIN
------------------------------------------------------------
Only two real tables live in this range. The rest are numbers inside prose:

  B  "Every 2 HP spent counts as 1 SPI toward the ritual cost." And then, in
     the same breath, "HP sacrifice does not grant a bonus to the roll -- it
     only provides spiritual fuel." That second sentence is the one worth
     pinning: a fuel source sitting in a list of roll modifiers is exactly
     the shape that gets quietly turned into a bonus.
  C  "Each assistant beyond the first also grants +1 to the ritual roll
     (maximum +3)." A count, not a row set -- and "beyond the first" means
     beyond the first PARTICIPANT, who is the caster. The book's own Ritual 3
     settles it: "Two trained assistants ... +2". Read as "beyond the first
     assistant" that pair would be +1, and every ritual with helpers would
     roll a point low. verify() pins the worked example, because it is the
     only thing in the chapter that disambiguates the sentence.
  D  Three named tiers with examples -- committed, because a GM choosing one
     needs to know what counts as Precious.
  E  "+0 to +2, at the GM's discretion." A range, like the darkness row.

They live in PowerSources.kt with verify() pinning the sentences they were
read from, the split the maneuver table's +4/-4, the +3 support cap and the
orthodox -2 all take.

WHAT IS DELIBERATELY NOT EXTRACTED
----------------------------------
Section IV.F, the Entity Response Table -- already `entity_response.json`.
The 12-step canon procedure above the suspension rule is reference prose the
book itself labels as texture.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "7"
TIME_RANGE = (4223, 4247)
COMPLEXITY_RANGE = (4255, 4264)
SACRIFICE_RANGE = (4281, 4286)
# The prose whose numbers live in Kotlin. Read only so verify() can pin the
# sentences they came out of. One span, because B through E run together.
PROSE_RANGE = (4218, 4291)
# Section VIII's Ritual 3, whose modifier table is the only place the book
# says what "each assistant beyond the first" actually counts from.
WORKED_PARTICIPANTS_RANGE = (4500, 4501)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADERS = {
    "Time & Complexity Reference", "Ritual Complexity", "Base Time",
    "Rushed", "Extended", "Complexity", "SPI Cost",
}

# The four names the Time table keys on. A closed set, so a row starts exactly
# where one of them does -- the same anchor the divination method names and
# the maneuver table's middle column give.
TIME_NAMES = ("Light", "Moderate", "Heavy", "Major")

# This book prints point costs with an ASCII hyphen and modifiers with U+2212
# in places, so every signed number here accepts both. Reading only one
# silently drops half the negatives -- and the half it drops still parses, as
# positives (CLAUDE.md 6u).
MINUS = "-−"
ROLL_CELL = re.compile(rf"^([+{MINUS}]\d+) to roll$")
# "2-3 SPI", "8+ SPI", "11-15+ SPI".
SPI_CELL = re.compile(rf"^(\d+)(?:[{MINUS}](\d+))?(\+?) SPI$")

SACRIFICE_TIER = re.compile(r"(\w+) \(([^)]+)\) grants \+(\d+)")
SENTIENT = re.compile(r"adds \+(\d+) per sentient being")
PARTICIPANT = re.compile(r"grants \+(\d+) to the ritual roll \(maximum \+(\d+)\)")
CATALYST = re.compile(r"provides \+(\d+) to \+(\d+) to the ritual roll")
HP_PER_SPI = re.compile(r"Every (\d+) HP spent counts as (\d+) SPI")
SUSPENSION = re.compile(rf"costs ([{MINUS}]\d+) to the effective skill")
BURN = re.compile(r"\(\+(\d+) per SPI, no cap\)")

# The sentence that keeps HP sacrifice out of the modifier list. It sits one
# line under "Every 2 HP spent counts as 1 SPI", which is exactly the position
# from which a fuel rule gets read as a bonus.
HP_NO_BONUS = "HP sacrifice does not grant a bonus to the roll"
# The sentence that makes the ladder the operative SPI table.
COMPLEXITY_COST = "The caster pays SPI equal to the ritual's complexity cost"
# The declaration that makes the power source a choice at all.
DECLARATION = "This choice cannot be changed once the ritual has begun"
# Burning is a SECOND use of SPI, on top of the fuel.
CUMULATIVE = "The two uses of SPI are cumulative"

EXPECTED_NAMES = ["Light", "Moderate", "Heavy", "Major"]
EXPECTED_SPI = [(2, 3, False), (4, 6, False), (7, 10, False), (11, 15, True)]
EXPECTED_RUSHED = [-3, -3, -4, -5]
EXPECTED_EXTENDED = [1, 1, 2, 2]
EXPECTED_TIERS = [("Ordinary", 0), ("Meaningful", 2), ("Precious", 4)]
EXPECTED_NUMBERS = {
    "hpPerSpi": 2, "spiPerHp": 1, "participantBonus": 1, "participantCap": 3,
    "sentientBonus": 2, "catalystLow": 0, "catalystHigh": 2,
    "suspension": -2, "burnPerSpi": 1,
}


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    out = []
    for n in range(start, end + 1):
        text = (byn.get(n) or "").strip()
        if not text or FURNITURE.match(text) or text in HEADERS:
            continue
        out.append(text)
    return out


def signed(text):
    """int() for a cell that may use U+2212 as its minus."""
    return int(text.replace("−", "-"))


def parse_time(cells):
    """Name / Base Time / Rushed / Extended, four rows.

    The name cell always wraps -- "Light (divination, minor" / "enhancement)"
    -- so it is consumed while its bracket is still open, the rule the Vitals
    multiplier and the shield names already needed. Counting two lines per
    name works today and breaks on the first name that fits on one.
    """
    rows, i = [], 0
    while i < len(cells):
        name = cells[i]
        if not name.startswith(TIME_NAMES):
            raise ValueError(f"{name!r} does not start a Time & Complexity row")
        while name.count("(") > name.count(")"):
            i += 1
            if i >= len(cells):
                raise ValueError(f"the name cell {name!r} never closes its bracket")
            name = f"{name} {cells[i]}"
        i += 1
        if i + 2 > len(cells):
            raise ValueError(f"the {name!r} row is missing cells")
        rushed, extended = ROLL_CELL.match(cells[i + 1]), ROLL_CELL.match(cells[i + 2])
        if not rushed or not extended:
            raise ValueError(
                f"the {name!r} row has no Rushed/Extended pair: {cells[i + 1:i + 3]}")
        rows.append({
            "complexity": name.split(" (")[0],
            "looksLike": name,
            "baseTime": cells[i],
            "rushed": signed(rushed.group(1)),
            "extended": signed(extended.group(1)),
        })
        i += 3
    return rows


def parse_ladder(cells):
    """Section IV.A: a description and its SPI cell, four times over."""
    rows, description = [], []
    for cell in cells:
        found = SPI_CELL.match(cell)
        if not found:
            description.append(cell)
            continue
        if not description:
            raise ValueError(f"the SPI cell {cell!r} has no description above it")
        rows.append({
            "description": " ".join(description),
            "spi": int(found.group(1)),
            "spiMax": int(found.group(2)) if found.group(2) else None,
            # "11-15+" is a range with an open TOP: the floor and the ceiling
            # are both printed and the "+" says the ceiling is not one. Not
            # the same as the weight table's "8+", which prints no ceiling.
            "spiOpen": bool(found.group(3)),
        })
        description = []
    if description:
        raise ValueError(f"a description with no SPI cell under it: {description}")
    return rows


def parse_sacrifice(text):
    tiers = [
        {"tier": name, "modifier": int(mod), "looksLike": examples}
        for name, examples, mod in SACRIFICE_TIER.findall(text)
    ]
    if not tiers:
        raise ValueError(
            "the External Sacrifice tiers no longer read '<Tier> (examples) grants +N'")
    return tiers


def join(time_rows, ladder_rows):
    """One complexity per row, by POSITION.

    Defensible only because two of the four rows agree by NAME at the same
    index; verify() pins that. The ladder's descriptions and the time table's
    names are two halves of one axis -- not two printings of one column, which
    is `ritual_weights.json`'s relationship to the ladder and stays a recorded
    disagreement.
    """
    if len(time_rows) != len(ladder_rows):
        raise ValueError(
            f"{len(time_rows)} time rows against {len(ladder_rows)} SPI rows; "
            "the positional join no longer holds"
        )
    return [{**time, **ladder} for time, ladder in zip(time_rows, ladder_rows)]


def verify(rows, tiers, numbers, prose, weights, worked):
    # "Each assistant beyond the first also grants +1" reads two ways, and only
    # Section VIII's Ritual 3 says which: TWO assistants are worth +2, so the
    # "first" is the caster and every assistant counts. The other reading --
    # beyond the first ASSISTANT -- makes that pair +1, and every ritual with
    # helpers rolls a point low. Pinned here because the sentence alone cannot
    # carry it.
    label, value = worked
    if "assistants" not in label.lower():
        raise ValueError(f"Ritual 3's participant row is gone; found {label!r}")
    expected = 2 * numbers["participantBonus"]
    if value != expected:
        raise ValueError(
            f"Ritual 3 gives {label!r} a value of {value:+d}, not {expected:+d}. "
            "That row is the only evidence that an assistant beyond 'the first' "
            "means beyond the CASTER rather than beyond the first assistant"
        )

    names = [r["complexity"] for r in rows]
    if names != EXPECTED_NAMES:
        raise ValueError(f"the complexity names changed: {names}")
    spi = [(r["spi"], r["spiMax"], r["spiOpen"]) for r in rows]
    if spi != EXPECTED_SPI:
        raise ValueError(f"the SPI ladder changed: {spi} != {EXPECTED_SPI}")
    if [r["rushed"] for r in rows] != EXPECTED_RUSHED:
        raise ValueError(f"the Rushed column changed: {[r['rushed'] for r in rows]}")
    if [r["extended"] for r in rows] != EXPECTED_EXTENDED:
        raise ValueError(f"the Extended column changed: {[r['extended'] for r in rows]}")

    # Rushing always costs and taking longer always pays. If either sign
    # flipped, the pacing row would need rereading rather than reusing -- the
    # standard the orthodox shift's direction is already held to.
    for row in rows:
        if row["rushed"] >= 0 or row["extended"] <= 0:
            raise ValueError(
                f"{row['complexity']} is rushed {row['rushed']} and extended "
                f"{row['extended']}; rushing is a penalty and taking longer is a bonus"
            )

    # The positional join's entire evidence: two rows agree by name at the
    # same index. "Moderate effect..." under "Moderate", "Major effect..."
    # under "Major"; Simple/Light and Powerful/Heavy do not.
    agreed = [
        r for r in rows
        if r["description"].split()[0].lower() == r["complexity"].lower()
    ]
    if len(agreed) < 2:
        raise ValueError(
            "the Time table and the SPI ladder no longer share two row names "
            f"at the same index (matched {[r['complexity'] for r in agreed]}); "
            "joining them by position is no longer justified"
        )

    # THE DISAGREEMENT THIS SLICE EXISTS TO KEEP. Section V's weight column and
    # Section IV.A's ladder price the same rituals differently at both ends and
    # identically in the middle. Straightening either is the failure mode.
    by_name = {w["weight"]: w for w in weights}
    light, moderate, heavy = by_name["Light"], by_name["Moderate"], by_name["Heavy"]
    simple, mid, powerful = rows[0], rows[1], rows[2]
    if (light["spi"], light.get("spiMax")) == (simple["spi"], simple["spiMax"]):
        raise ValueError(
            "Section V's Light cell and the ladder's Simple row now agree. They "
            "printed 2 against 2-3, and the book's own Ritual 2 charges 3 SPI "
            "for a Light divination -- outside the weight column, inside the ladder"
        )
    if heavy["spi"] == powerful["spi"]:
        raise ValueError(
            "Section V's Heavy floor and the ladder's Powerful floor now agree. "
            "They printed 8+ against 7-10, and the book's own Ritual 3 charges "
            "7 SPI for a Heavy summoning -- below the weight column's floor"
        )
    if (moderate["spi"], moderate.get("spiMax")) != (mid["spi"], mid["spiMax"]):
        raise ValueError(
            f"the two tables no longer agree on Moderate ({moderate['spi']}-"
            f"{moderate.get('spiMax')} against {mid['spi']}-{mid['spiMax']}). "
            "That one agreement is what makes the other two a real difference "
            "rather than two unrelated ladders"
        )
    # Section V prints no Major at all, which is why Major carries no base
    # difficulty modifier and is offered as a complexity rather than a weight.
    if any(w["weight"] == "Major" for w in weights):
        raise ValueError(
            "the weight table prints a Major row now. Major had no base "
            "difficulty modifier, which is the whole reason it lives on the "
            "complexity axis instead"
        )

    got = [(t["tier"], t["modifier"]) for t in tiers]
    if got != EXPECTED_TIERS:
        raise ValueError(f"the sacrifice tiers changed: {got} != {EXPECTED_TIERS}")

    for key, want in EXPECTED_NUMBERS.items():
        if numbers.get(key) != want:
            raise ValueError(f"{key} reads {numbers.get(key)}, not {want}")

    if HP_NO_BONUS not in prose:
        raise ValueError(
            f"the sentence {HP_NO_BONUS!r} is gone. HP sacrifice is FUEL; "
            "without that clause nothing stops it being applied to the roll"
        )
    for phrase in (COMPLEXITY_COST, DECLARATION, CUMULATIVE):
        if phrase not in prose:
            raise ValueError(f"the sentence {phrase!r} is gone")


def read_numbers(prose):
    found = {
        "hp": HP_PER_SPI.search(prose),
        "participants": PARTICIPANT.search(prose),
        "catalyst": CATALYST.search(prose),
        "suspension": SUSPENSION.search(prose),
        "burn": BURN.search(prose),
        "sentient": SENTIENT.search(prose),
    }
    for name, match in found.items():
        if not match:
            raise ValueError(f"the {name} sentence no longer prints its number")
    return {
        "hpPerSpi": int(found["hp"].group(1)),
        "spiPerHp": int(found["hp"].group(2)),
        "participantBonus": int(found["participants"].group(1)),
        "participantCap": int(found["participants"].group(2)),
        "sentientBonus": int(found["sentient"].group(1)),
        "catalystLow": int(found["catalyst"].group(1)),
        "catalystHigh": int(found["catalyst"].group(2)),
        "suspension": signed(found["suspension"].group(1)),
        "burnPerSpi": int(found["burn"].group(1)),
    }


def extract():
    byn = load_lines()
    rows = join(
        parse_time(stream(byn, *TIME_RANGE)),
        parse_ladder(stream(byn, *COMPLEXITY_RANGE)),
    )
    tiers = parse_sacrifice(" ".join(stream(byn, *SACRIFICE_RANGE)))
    prose = " ".join(stream(byn, *PROSE_RANGE))
    numbers = read_numbers(prose)
    worked_cells = stream(byn, *WORKED_PARTICIPANTS_RANGE)
    if len(worked_cells) != 2:
        raise ValueError(f"Ritual 3's participant row is not a label/value pair: {worked_cells}")
    worked = (worked_cells[0], signed(worked_cells[1]))
    weights = json.loads(
        (OUT / "ritual_weights.json").read_text(encoding="utf-8"))["entries"]
    verify(rows, tiers, numbers, prose, weights, worked)
    return rows, tiers, numbers


def main():
    rows, tiers, numbers = extract()

    OUT.mkdir(parents=True, exist_ok=True)
    # Every number in `numbers` lives in PowerSources.kt, not here: each is a
    # value inside a sentence, and a committed column would make the book look
    # like it printed a table it never printed.
    (OUT / "ritual_complexity.json").write_text(
        json.dumps(
            {"sourceLines": {CHAPTER: [list(TIME_RANGE), list(COMPLEXITY_RANGE)]},
             "entries": rows},
            ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    (OUT / "ritual_sacrifice.json").write_text(
        json.dumps(
            {"sourceLines": {CHAPTER: [list(SACRIFICE_RANGE)]}, "entries": tiers},
            ensure_ascii=False, indent=1) + "\n", encoding="utf-8")

    print(f"ritual complexity: {len(rows)} rows (the weight table prints 3 and no Major)")
    for row in rows:
        cost = f"{row['spi']}-{row['spiMax']}" if row["spiMax"] else str(row["spi"])
        cost += "+" if row["spiOpen"] else ""
        print(f"  {row['complexity']:9} {cost:8} SPI  {row['baseTime']:9} "
              f"rushed {row['rushed']:+d}  extended {row['extended']:+d}")
    print("sacrifice: " + ", ".join(f"{t['tier']} {t['modifier']:+d}" for t in tiers)
          + f", +{numbers['sentientBonus']} per sentient being")
    print(f"in Kotlin: {numbers['hpPerSpi']} HP = {numbers['spiPerHp']} SPI (no roll bonus), "
          f"participants +{numbers['participantBonus']} each to +{numbers['participantCap']}, "
          f"catalyst +{numbers['catalystLow']} to +{numbers['catalystHigh']}, "
          f"suspension {numbers['suspension']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
