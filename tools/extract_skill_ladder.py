"""Extract Ch. 3's skill cost ladder and its two worked examples (p. 25-26).

WHY THIS IS WORTH EXTRACTING AT ALL
------------------------------------
`SkillLadder` has been production code since M0 and every skill level the app
shows goes through it -- 232 of them across the 23 published sheets. It was
implemented from this table and then never checked against it. This extract
turns the table into content and the book's two worked examples into an
oracle, the way Ch. 7's worked rituals became one (CLAUDE.md 6ab).

It confirms the existing code rather than changing it. That is the expected
result of marking homework, and it is worth having written down: the ladder is
NOT vanilla GURPS (core 4e costs 1/2/4/8/12/16 with +4 per level past Attr+2),
and using core costs would make 14 of those 232 levels wrong. Now something
fails if either the table or the constant drifts.

THE TABLE IS TWO TABLES STACKED
---------------------------------
p. 25 prints the cumulative ladder as a single column -- 1, 2, 4, 8, 16, 32,
64, 128, 256 -- beside an incremental one (+1, +2, +4 ... +128). p. 26 then
prints a 4x8 grid of Attr offsets keyed on those same costs:

    Easy       +0 +1 +2 +3 +4 +5 +6 +7
    Average    -1 +0 +1 +2 +3 +4 +5 +6
    Hard       -2 -1 +0 +1 +2 +3 +4 +5
    Very Hard  -3 -2 -1 +0 +1 +2 +3 +4

Every row is its predecessor shifted by one, and every column is the one to
its left plus one. verify() asserts both, because that regularity is the whole
reason `Difficulty.firstRungOffset` can be one number per difficulty instead
of a stored row.

THE NINTH RUNG HAS NO OFFSET COLUMN
-------------------------------------
The cumulative ladder prints nine costs and the difficulty grid only eight:
256 pts has no printed offset. `SkillLadder`'s rungs carry it and extrapolate
+1, which the prose supports ("each level after the third costs twice as much
as the level before it") but the grid does not show. Recorded as a reading
here and pinned by a named test, rather than left to be rediscovered by
whoever first buys a skill to 256 points.

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
LADDER_RANGE = (1580, 1602)
GRID_RANGE = (1603, 1648)
EXAMPLE_RANGE = (1649, 1658)
PROSE_RANGE = (1571, 1579)

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

EXPECTED_CUMULATIVE = [1, 2, 4, 8, 16, 32, 64, 128, 256]
EXPECTED_OFFSETS = {
    "Easy": [0, 1, 2, 3, 4, 5, 6, 7],
    "Average": [-1, 0, 1, 2, 3, 4, 5, 6],
    "Hard": [-2, -1, 0, 1, 2, 3, 4, 5],
    "Very Hard": [-3, -2, -1, 0, 1, 2, 3, 4],
}
# The sentence the doubling rests on, and the one saying the ladder is
# difficulty-independent -- which is what lets one offset per difficulty stand
# in for a whole stored row.
DOUBLING = "each level after the third costs twice as much as the level before it"
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

    # The grid is one column SHORT of the ladder. The 256-point rung is
    # printed as a cost and given no offset, so continuing it is a reading.
    width = len(ordered[0])
    if width != len(cumulative) - 1:
        raise ValueError(
            f"the grid is {width} columns against {len(cumulative)} ladder rungs. "
            "It was exactly one short -- 256 pts has a cost and no offset -- and "
            "SkillLadder extrapolates that rung by +1")

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
    print(f"the grid is one column short of the ladder: "
          f"{len(cumulative)} rungs, {len(offsets['Easy'])} offsets")
    for example in examples:
        walk = ", ".join(f"{s['points']}pt->{s['level']}" for s in example["steps"])
        print(f"  {example['skill']} ({example['attribute']}/{example['difficulty']} "
              f"{example['attributeLevel']}): {walk}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
