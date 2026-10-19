"""Extract Ch. 3's skill cost ladder and its two worked examples (p. 27-28).

WHY THIS IS WORTH EXTRACTING AT ALL
------------------------------------
`SkillLadder` has been production code since M0 and every skill level the app
shows goes through it -- 232 of them across the 23 published sheets. It was
implemented from this table and then never checked against it. This extract
turns the table into content and the book's two worked examples into an
oracle, the way Ch. 7's worked rituals became one (CLAUDE.md 6ab).

v6.9d's ladder doubled at every rung (1, 2, 4 ... 256). v6.9i caps it:
"after the first five rungs the cost per level stops climbing" -- the fifth
rung costs +8 and every rung after it a flat +16, so the cumulative column
reads 1, 2, 4, 8, 16, 32, 48, 64, 80. It is still not vanilla GURPS (core 4e
costs 1/2/4/8/12/16 with +4 per level past Attr+2).

THE TABLE IS TWO TABLES STACKED
---------------------------------
p. 27 prints the cumulative ladder as a single column -- 1, 2, 4, 8, 16, 32,
48, 64, 80 -- beside an incremental one (+1, +2, +4, +8, +16, +16 ...). It then
prints a 4x9 grid of Attr offsets keyed on those same costs:

    Easy       +0 +1 +2 +3 +4 +5 +6 +7 +8
    Average    -1 +0 +1 +2 +3 +4 +5 +6 +7
    Hard       -2 -1 +0 +1 +2 +3 +4 +5 +6
    Very Hard  -3 -2 -1 +0 +1 +2 +3 +4 +5

Every row is its predecessor shifted by one, and every column is the one to
its left plus one. verify() asserts both, because that regularity is the whole
reason `Difficulty.firstRungOffset` can be one number per difficulty instead
of a stored row.

EVERY RUNG HAS AN OFFSET NOW
----------------------------
v6.9d printed nine costs and only eight offset columns, so its 256-point rung
was an extrapolation. v6.9i prints nine of each. Past 80 points the prose
still answers -- "every level after that costs a flat 16 pts" -- so a tenth
rung is 96 pts and one more level, which is printed and not a reading.

THE EXAMPLES ARE THE ORACLE
-----------------------------
"Research (IQ/Hard), IQ 9" walks 1/2/4/8/16 points and prints the level each
time, with the arithmetic spelled out ("Research 7 (IQ 9 - 2)"). "Brawling
(DX/Easy), DX 9" walks 1/2/4. Between them they exercise two difficulties,
both a negative and a zero first rung, and five rungs of the doubling -- which
is more of `SkillLadder.levelFor` than any unit test had covered.

WHAT IS DELIBERATELY NOT EXTRACTED
----------------------------------
The "Skill Buying Strategy" advice below the examples is guidance, not
mechanics. The study/training system on p. 32 -- tutors, study points, and a
cap of Attribute+1 through self-study alone -- is a real unmodelled subsystem
with no caller in the app, and is its own slice.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "3"
LADDER_RANGE = (1674, 1694)
GRID_RANGE = (1695, 1745)
EXAMPLE_RANGE = (1746, 1757)
PROSE_RANGE = (1666, 1673)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADERS = {"Total Pts Spent", "Incremental cost", "Difficulty"}

MINUS = "-−"
COST = re.compile(r"^(\d+)$")
INCREMENTAL = re.compile(r"^\+?(\d+) pts?$")
OFFSET = re.compile(rf"^Attr([+{MINUS}])(\d+)$")
DIFFICULTIES = ("Easy", "Average", "Hard", "Very Hard")
EXAMPLE_TITLE = re.compile(r"^Example — (.+) \((\w+)/(\w[\w ]*)\), (\w+) (\d+):$")
STEP = re.compile(
    rf"^Spend (\d+) (?:more )?pts?(?: \((\d+) total\))?: (.+?) (\d+)"
    rf"(?: \(\w+ \d+ ?[{MINUS}+] ?\d+\))?(?: —.*)?$"
)

EXPECTED_CUMULATIVE = [1, 2, 4, 8, 16, 32, 48, 64, 80]
EXPECTED_OFFSETS = {
    "Easy": [0, 1, 2, 3, 4, 5, 6, 7, 8],
    "Average": [-1, 0, 1, 2, 3, 4, 5, 6, 7],
    "Hard": [-2, -1, 0, 1, 2, 3, 4, 5, 6],
    "Very Hard": [-3, -2, -1, 0, 1, 2, 3, 4, 5],
}
# The sentence the flat tail rests on, and the one saying the ladder is
# difficulty-independent -- which is what lets one offset per difficulty stand
# in for a whole stored row.
DOUBLING = "every level after that costs a flat 16 pts"
SAME_LADDER = "The ladder is the same for every skill regardless of difficulty"


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    out = []
    for n in range(start, end + 1):
        # This PDF prints list bullets as U+007F, and the worked examples are
        # a bulleted list -- so an unstripped cell starts with a control
        # character and every step regex silently fails to match.
        text = (byn.get(n) or "").replace("", " ").strip()
        if not text or FURNITURE.match(text) or text in HEADERS:
            continue
        out.append(text)
    return out


def signed(sign, digits):
    return int(digits) * (-1 if sign in MINUS else 1)


def parse_ladder(cells):
    """The cumulative column, then the incremental one beside it."""
    cumulative = [int(c) for c in cells if COST.fullmatch(c)]
    incremental = []
    for cell in cells:
        found = INCREMENTAL.fullmatch(cell)
        if found:
            incremental.append(int(found.group(1)))
    if not cumulative:
        raise ValueError("the cumulative point ladder is gone")
    # Each incremental cost is the gap between two cumulative totals, and the
    # first is the first total. If that stops holding, one of the two columns
    # has been mis-cut.
    gaps = [cumulative[0]] + [
        cumulative[i] - cumulative[i - 1] for i in range(1, len(cumulative))
    ]
    if incremental != gaps[:len(incremental)]:
        raise ValueError(
            f"the incremental column {incremental} is not the gaps between the "
            f"cumulative one {gaps[:len(incremental)]}")
    return cumulative, incremental


def parse_grid(cells):
    """Four difficulty rows of eight Attr offsets each.

    The difficulty NAMES are the anchor -- a closed set of four -- because the
    cost headers above them ("1 pt", "2 pts", ...) look exactly like the
    incremental column on the page before.
    """
    rows, current = {}, None
    for cell in cells:
        if cell in DIFFICULTIES:
            current = cell
            rows[current] = []
            continue
        offset = OFFSET.fullmatch(cell)
        if offset and current is not None:
            rows[current].append(signed(offset.group(1), offset.group(2)))
    if set(rows) != set(DIFFICULTIES):
        raise ValueError(f"the difficulty rows changed: {sorted(rows)}")
    return rows


def parse_examples(cells):
    """The two worked walk-ups, each a title then its steps."""
    examples, current = [], None
    for cell in cells:
        title = EXAMPLE_TITLE.match(cell)
        if title:
            skill, attribute, difficulty, again, level = title.groups()
            if again != attribute:
                raise ValueError(
                    f"{skill}'s title names {attribute} then {again}; they were the same")
            current = {
                "skill": skill,
                "attribute": attribute,
                "difficulty": difficulty,
                "attributeLevel": int(level),
                "steps": [],
            }
            examples.append(current)
            continue
        step = STEP.match(cell)
        if step and current is not None:
            spent, total, named, result = step.groups()
            if named != current["skill"]:
                raise ValueError(f"a step under {current['skill']} names {named!r}")
            current["steps"].append({
                # The first step prints no running total, because it IS one.
                "points": int(total) if total else int(spent),
                "level": int(result),
            })
    if not examples:
        raise ValueError("the worked skill examples are gone")
    return examples


def verify(cumulative, offsets, examples, prose):
    if cumulative != EXPECTED_CUMULATIVE:
        raise ValueError(f"the cumulative ladder changed: {cumulative}")
    for difficulty, want in EXPECTED_OFFSETS.items():
        if offsets[difficulty] != want:
            raise ValueError(
                f"{difficulty}'s offsets changed: {offsets[difficulty]} != {want}")

    # Each row is its predecessor shifted down by one, and each column is the
    # one left of it plus one. That double regularity is the entire reason
    # Difficulty can carry ONE firstRungOffset instead of a stored row.
    ordered = [offsets[d] for d in DIFFICULTIES]
    for above, below in zip(ordered, ordered[1:]):
        if [x - 1 for x in above] != below:
            raise ValueError(
                f"the difficulty rows are no longer one apart: {above} then {below}")
    for row in ordered:
        if row != list(range(row[0], row[0] + len(row))):
            raise ValueError(f"a difficulty row no longer climbs by one: {row}")

    # v6.9i prints an offset for every rung.
    width = len(ordered[0])
    if width != len(cumulative):
        raise ValueError(
            f"the grid is {width} columns against {len(cumulative)} ladder rungs")

    # THE ORACLE. Every printed step must be reproducible from the offsets.
    for example in examples:
        offset_row = offsets[example["difficulty"]]
        for step in example["steps"]:
            rung = max(i for i, c in enumerate(cumulative) if c <= step["points"])
            expected = example["attributeLevel"] + offset_row[0] + rung
            if expected != step["level"]:
                raise ValueError(
                    f"{example['skill']} at {step['points']} pts should be "
                    f"{expected} by the table, and the book prints {step['level']}")

    for phrase in (DOUBLING, SAME_LADDER):
        if phrase not in prose:
            raise ValueError(f"the sentence {phrase!r} is gone")


def extract():
    byn = load_lines()
    cumulative, incremental = parse_ladder(stream(byn, *LADDER_RANGE))
    offsets = parse_grid(stream(byn, *GRID_RANGE))
    examples = parse_examples(stream(byn, *EXAMPLE_RANGE))
    prose = " ".join(stream(byn, *PROSE_RANGE))
    verify(cumulative, offsets, examples, prose)
    return cumulative, incremental, offsets, examples


def main():
    cumulative, incremental, offsets, examples = extract()

    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "skill_ladder.json").write_text(
        json.dumps(
            {
                "sourceLines": {CHAPTER: [
                    list(LADDER_RANGE), list(GRID_RANGE), list(EXAMPLE_RANGE),
                ]},
                # One entry, because this is one table rather than a row set --
                # the shape boon_granting.json already uses, so ContentLoader
                # needs no second path to read it.
                "entries": [{
                    "cumulative": cumulative,
                    "incremental": incremental,
                    "rungs": [
                        {"difficulty": d, "offsets": offsets[d]} for d in DIFFICULTIES
                    ],
                    "examples": examples,
                }],
            },
            ensure_ascii=False, indent=1) + "\n", encoding="utf-8")

    print(f"skill ladder: {cumulative}")
    for difficulty in DIFFICULTIES:
        print(f"  {difficulty:10} " + " ".join(f"{o:+d}" for o in offsets[difficulty]))
    print(f"ladder and grid: "
          f"{len(cumulative)} rungs, {len(offsets['Easy'])} offsets")
    for example in examples:
        walk = ", ".join(f"{s['points']}pt->{s['level']}" for s in example["steps"])
        print(f"  {example['skill']} ({example['attribute']}/{example['difficulty']} "
              f"{example['attributeLevel']}): {walk}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
