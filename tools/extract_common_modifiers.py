"""Extract Appendix A's Common Modifiers table (p. 159).

WHAT THIS IS
-------------
Nine rows of general circumstance -- an easy task, poor tools, a helper --
that modify any skill roll. It is the fourth modifier source, beside
`Conditions` (what the character is carrying), `Attacks` (what the player
chose this turn) and `Ranged` (the distance and the scene). This one is what
the GM says the situation is.

Adopted on the same warrant as the degree-of-success ladder (CLAUDE.md 6ad):
the table is Appendix A's alone -- no chapter prints a general modifier list --
and the one row that CAN be checked against the engine agrees with it exactly.
See the untrained row below, which is that check.

ONE ROW IS NOT A MODIFIER
--------------------------
"Using untrained skill (default) / Attribute -4 to -6" does not modify a
skill; it REPLACES the roll with an attribute-based default, and this app has
implemented that since M0 -- `Difficulty.defaultPenalty` is -4 Easy, -5
Average, -6 Hard, and Very Hard has no default at all. So the printed range is
that column's two ends, and verify() checks it, which is simultaneously the
warrant for the rest of the table and the proof this row must be refused.

Applied as a modifier it would be wrong twice over: it would stack on top of
`SkillLadder.defaultFor` for a character who is genuinely untrained, and it
subtracts from the ATTRIBUTE where a modifier subtracts from the SKILL. Read
as +0 instead it silently drops the whole rule. The same shape as divination's
"Question difficulty / See Step 1 table" row (6p), which points back at a step
already applied -- refused rather than ignored, so a caller cannot believe it
was counted.

RANGES, FLOORS AND A CONDITION
-------------------------------
Only four of the nine rows print a single number. Two print a range ("+1 to
+2", "-2 to -4"), two print an open end ("+4 or more", "-6 or worse"), and one
carries a condition in the cell ("+2 (if they succeed first)"). The end nearer
zero is the value offered, the same gentle reading the darkness row and the
ritual preparation ranges already take; the other end is kept beside it so a
GM choosing inside the range is validated against what the book actually
prints rather than against nothing.

"Routine conditions / +0" is a real printed row that adds nothing, which is
NOT the same as a row that prints no modifier. The untrained row is the
latter and carries no value at all -- the distinction
`RitualCritical.corGained` already draws between charging no corruption and
charging zero.

WHY IT IS NOT LAYERED ONTO THE RANGED DIALOG
----------------------------------------------
"Adverse conditions (dark, noisy, dangerous) -4" and `ranged_modifiers.json`'s
"Darkness or heavy fog -3 to -9" are the same circumstance priced twice, in a
general table and a specific one. Offering both on one roll charges darkness
twice. Specific beats general, so this table belongs to the plain skill roll
and the ranged dialog keeps chapter 5's -- the same scoping the ritual CoR
ladder needed (6h) and that keeps chapter 13's boons off Phase I preparation
(6r). verify() pins that the overlap is still really there, because the day it
stops being there is the day the scoping stops being needed.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "A"
TABLE_RANGE = (8980, 9000)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+|Common Modifiers)$")
HEADERS = {"Situation", "Modifier"}

MINUS = "-−"
NUM = rf"[+{MINUS}]\d+"
EXACT = re.compile(rf"^({NUM})$")
OPEN_ENDED = re.compile(rf"^({NUM}) or (?:more|worse)$")
RANGE = re.compile(rf"^({NUM}) to ({NUM})$")
CONDITIONAL = re.compile(rf"^({NUM}) \(.+\)$")
UNTRAINED = re.compile(rf"^Attribute ({NUM}) to ({NUM})$")

# The untrained row restates Difficulty.defaultPenalty's two ends. This is
# both the table's warrant and the reason that row is refused as a modifier.
EXPECTED_DEFAULT_ENDS = (-4, -6)
UNTRAINED_SITUATION = "Using untrained skill (default)"
ROUTINE_SITUATION = "Routine conditions"

EXPECTED_SITUATIONS = [
    "Easy task (well-lit, calm, plenty of time)",
    ROUTINE_SITUATION,
    "Distracted or hurried",
    "Adverse conditions (dark, noisy, dangerous)",
    "Very adverse conditions (combat, extreme weather)",
    UNTRAINED_SITUATION,
    "Good tools / equipment",
    "Improvised / poor tools",
    "Aided by someone with the same skill",
]

# Chapter 5 prices darkness itself, which is why this general table is not
# offered on a ranged attack.
OVERLAPPING_ROW = "Adverse conditions (dark, noisy, dangerous)"
RANGED_FILE = "ranged_modifiers.json"
RANGED_OVERLAP = "Darkness"


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    out = []
    for n in range(start, end + 1):
        text = (byn.get(n) or "").replace("\x7f", " ").strip()
        if not text or FURNITURE.match(text) or text in HEADERS:
            continue
        out.append(text)
    return out


def signed(text):
    return int(text[1:]) * (-1 if text[0] in MINUS else 1)


def parse_value(cell):
    """The Modifier cell, in the five shapes this table prints.

    Returns None when the cell is not a modifier at all, which happens for
    exactly one row and is the point of the function.
    """
    found = UNTRAINED.fullmatch(cell)
    if found:
        return {"isDefault": True}
    found = EXACT.fullmatch(cell)
    if found:
        return {"modifier": signed(found.group(1))}
    found = OPEN_ENDED.fullmatch(cell)
    if found:
        return {"modifier": signed(found.group(1)), "isOpenEnded": True}
    found = RANGE.fullmatch(cell)
    if found:
        return {"modifier": signed(found.group(1)), "otherEnd": signed(found.group(2))}
    found = CONDITIONAL.fullmatch(cell)
    if found:
        return {"modifier": signed(found.group(1))}
    return None


def parse_rows(cells):
    """Situation cell then Modifier cell, nine times.

    The MODIFIER cell is the anchor: every one of them starts with a sign or
    the word Attribute, and no situation cell does. Pairing the stream off
    blindly would let one dropped cell shift every row below it and still
    produce a table that sums.
    """
    rows, situation = [], None
    for cell in cells:
        value = parse_value(cell)
        if value is None:
            if situation is not None:
                raise ValueError(f"two situations in a row: {situation!r} then {cell!r}")
            situation = cell
            continue
        if situation is None:
            raise ValueError(f"a modifier with no situation above it: {cell!r}")
        rows.append({"situation": situation, "printed": cell, **value})
        situation = None
    if situation is not None:
        raise ValueError(f"the last situation has no modifier: {situation!r}")
    if not rows:
        raise ValueError("the Common Modifiers table is gone")
    return rows


def find(rows, situation):
    for row in rows:
        if row["situation"] == situation:
            return row
    raise ValueError(f"the {situation!r} row is gone")


def ranged_conditions():
    path = OUT / RANGED_FILE
    if not path.exists():
        return []
    return [e["condition"] for e in json.loads(path.read_text(encoding="utf-8"))["entries"]]


def verify(rows):
    if [r["situation"] for r in rows] != EXPECTED_SITUATIONS:
        raise ValueError(f"the table's rows changed: {[r['situation'] for r in rows]}")

    # THE WARRANT, and the reason this row is refused. Its printed range is
    # Difficulty.defaultPenalty's two ends -- the only row here the engine can
    # be checked against, and it agrees exactly.
    untrained = find(rows, UNTRAINED_SITUATION)
    if not untrained.get("isDefault"):
        raise ValueError(f"the untrained row is no longer a default: {untrained}")
    ends = UNTRAINED.fullmatch(untrained["printed"])
    if (signed(ends.group(1)), signed(ends.group(2))) != EXPECTED_DEFAULT_ENDS:
        raise ValueError(
            f"the untrained default range changed: {untrained['printed']}. It was "
            f"Difficulty.defaultPenalty's two ends {EXPECTED_DEFAULT_ENDS}, which is "
            "what makes this row a restatement rather than a modifier")
    if "modifier" in untrained:
        raise ValueError(
            "the untrained row carries a modifier. It replaces the roll with an "
            "attribute default; applied to a skill it would stack on "
            "SkillLadder.defaultFor and subtract from the wrong number")

    # A row that prints +0 is not the same as a row that prints no modifier.
    routine = find(rows, ROUTINE_SITUATION)
    if routine.get("modifier") != 0:
        raise ValueError(f"the routine row no longer prints +0: {routine}")

    # Every other row must carry a value, or the parse dropped one silently.
    for row in rows:
        if row["situation"] == UNTRAINED_SITUATION:
            continue
        if "modifier" not in row:
            raise ValueError(f"a row lost its modifier: {row}")

    # The ranges and floors are what stop a GM's choice being validated
    # against nothing.
    ranged = [r for r in rows if r.get("otherEnd") is not None]
    open_ended = [r for r in rows if r.get("isOpenEnded")]
    if len(ranged) != 2 or len(open_ended) != 2:
        raise ValueError(
            f"the table printed {len(ranged)} ranges and {len(open_ended)} open ends; "
            "it printed two of each, and a row that lost its second end would "
            "silently become exact")
    for row in ranged:
        if abs(row["otherEnd"]) <= abs(row["modifier"]):
            raise ValueError(
                f"a range no longer runs away from zero: {row}. The gentle end is "
                "the value offered, so their order is what makes it gentle")

    # The overlap this table is scoped out of the ranged dialog for.
    if OVERLAPPING_ROW not in [r["situation"] for r in rows]:
        raise ValueError("the row that overlaps chapter 5's darkness rule is gone")
    conditions = ranged_conditions()
    if conditions and not any(RANGED_OVERLAP in c for c in conditions):
        raise ValueError(
            "chapter 5 no longer prices darkness, so the reason this general "
            "table is kept off the ranged dialog needs rereading")


def extract():
    byn = load_lines()
    rows = parse_rows(stream(byn, *TABLE_RANGE))
    verify(rows)
    return rows


def main():
    rows = extract()

    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "common_modifiers.json").write_text(
        json.dumps(
            {"sourceLines": {CHAPTER: [list(TABLE_RANGE)]}, "entries": rows},
            ensure_ascii=False, indent=1) + "\n", encoding="utf-8")

    for row in rows:
        value = row["printed"] if not row.get("isDefault") else f"{row['printed']} (not a modifier)"
        print(f"  {row['situation']:52} {value}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
