"""Extract the Study & Training tables (Ch. 3, p. 34, global 2279-2331).

Two files. `study_costs` is the eight-cell time-and-money table, keyed on the
closed set of four difficulty names -- the same anchor the maneuver table uses,
and reliable here because a cost cell never looks like a difficulty.

`study_rules` is the prose the GM reads: the tutor's and self-study's
requirements, and the three Limits bullets. Only the first of those three is a
number the app can act on; the other two are shown, because this app holds no
downtime period and no tutor.

WHAT IS DELIBERATELY NOT EXTRACTED, and why this file exists at all:

The "Cumulative CP" grid at 2300-2324 is a TRUNCATED SECOND PRINTING of the
skill cost grid `skill_ladder.json` has carried from p. 25-26 since 6ac -- four
rungs against nine, every cell agreeing. Storing it would be the silent merge
4.1 forbids, and `GoldenStudyPointsTest` already asserts it as a PREFIX. Its
lines stay unclaimed on purpose, so the two spans below have a hole in them.

The whole slice rests on one sentence at 2279: a study session earns "1
character point locked to that skill". Study points ARE character points, so
`SkillLadder` already prices a rung and this table only prices the point --
which is what makes a stateless planner possible in an app with no clock.
Tomas's worked example proves the cost is linear per point ("another 24 hours
(16s) for 2 more CP" against a printed 12 hrs + 8s).

Two parsing notes. The money column prints TWO currencies -- soli as a bare
"4s" and pounds as "£1" -- so a regex that knows one silently drops half the
column, the trap the wealth table's two minus signs already set (6u).
`verify()` therefore requires both to survive. And the Very Hard self-study
cell reads "Not possible", which is a refusal, not a price: it is stored with
no hours and no money at all, because printing no cost is not printing zero --
the distinction `RitualCritical.corGained` draws for corruption.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "3"
# The CP grid (2300-2324) is deliberately outside both spans; see the docstring.
TABLE_RANGE = (2279, 2299)
LIMITS_RANGE = (2325, 2331)

# This PDF prints list bullets as U+007F. See extract_defenses.py.
BULLET = "\x7f"
FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADER = {"Difficulty", "With a Tutor", "Self-Study", "Spending Study Points", "Limits"}

DIFFICULTIES = ["Easy", "Average", "Hard", "Very Hard"]
# "6 hrs + 4s" and "24 hrs + £1" -- one column, two currencies.
COST = re.compile(r"^(\d+)\s*hrs\s*\+\s*(?:£(\d+)|(\d+)s)$")
IMPOSSIBLE = "Not possible"
# The two requirements label themselves; the intro and the ladder sentence
# around them do not, which is what makes a label the anchor rather than a
# position.
REQUIREMENT = re.compile(r"^(Tutor|Self-study):")
# 1 pound = 20 soli (Ch. 8). Used ONLY to check the column climbs -- nothing in
# this repo converts currency, and the money is committed exactly as printed.
SOLI_PER_POUND = 20

PINNED = [
    # The sentence the whole slice rests on.
    "1 character point locked to that skill",
    "Study points follow the same point ladder as normal character points",
    # All three Limits bullets. Only the first is applied.
    "A skill cannot exceed Attribute+1 through study alone.",
    "cannot be studied",
    "No more than two skills may be studied per downtime period.",
    # The two requirements, shown and never enforced.
    "Must have the skill at Attribute+2 or higher",
    "Requires a book, manual, or practice space",
]


def load_lines():
    return json.loads(LINES_PATH.read_text(encoding="utf-8"))


def stream(records, lo, hi):
    """Lines lo..hi, furniture dropped, each marked as bulleted or not.

    The marker is the whole reason the Limits are separable. Tomas's worked
    example sits directly above them and WRAPS across three lines, so a
    `startswith` blacklist takes the first continuation ("8s earns 1 study
    CP...") as a fourth Limit -- the trailing-paragraph trap `extract_core.py`
    and `extract_rituals.py` each had to be rescued from. The three Limits are
    bulleted and the example is not, so the book separates them itself.
    """
    out = []
    for record in records:
        if not lo <= record["n"] <= hi:
            continue
        raw = record["text"]
        text = raw.replace(BULLET, "").strip()
        if not text or FURNITURE.match(text):
            continue
        out.append((BULLET in raw, text))
    return out


def parse_cost(cell):
    """One cost cell, or a refusal carrying no numbers at all."""
    if cell == IMPOSSIBLE:
        return {"printed": cell}
    m = COST.match(cell)
    if not m:
        raise ValueError(f"unparsed study cost cell: {cell!r}")
    pounds, soli = m.group(2), m.group(3)
    return {
        "printed": cell,
        "hours": int(m.group(1)),
        "amount": int(pounds or soli),
        "unit": "£" if pounds else "s",
    }


def parse_costs(lines):
    """Difficulty / With a Tutor / Self-Study, cut on the closed name column."""
    rows = []
    for name in DIFFICULTIES:
        if name not in lines:
            raise ValueError(f"the difficulty column lost {name!r}")
        i = lines.index(name)
        if i + 2 >= len(lines):
            raise ValueError(f"{name!r} has no pair of cost cells")
        rows.append({
            "difficulty": name,
            "tutor": parse_cost(lines[i + 1]),
            "selfStudy": parse_cost(lines[i + 2]),
        })
    return rows


def as_soli(cost):
    return cost["amount"] * (SOLI_PER_POUND if cost["unit"] == "£" else 1)


def verify(costs, rules, body):
    if len(costs) != len(DIFFICULTIES):
        raise ValueError(f"expected {len(DIFFICULTIES)} rows, got {len(costs)}")

    refusals = [(row["difficulty"], side)
                for row in costs for side in ("tutor", "selfStudy")
                if "hours" not in row[side]]
    if refusals != [("Very Hard", "selfStudy")]:
        raise ValueError(f"exactly one cell reads '{IMPOSSIBLE}'; got {refusals}")
    refused = next(row["selfStudy"] for row in costs if row["difficulty"] == "Very Hard")
    if "amount" in refused or "unit" in refused:
        raise ValueError("the refused cell must carry no cost: no cost is not zero cost")

    # Both currencies survived the parse. Dropping one still parses and still
    # reads plausibly, which is exactly why this is checked.
    priced = [c for row in costs for c in (row["tutor"], row["selfStudy"]) if "hours" in c]
    units = {c["unit"] for c in priced}
    if units != {"£", "s"}:
        raise ValueError(f"the money column prints two currencies; parsed {units}")

    # Self-study is always the slower road where the book prices both.
    for row in costs:
        if "hours" not in row["selfStudy"]:
            continue
        if row["selfStudy"]["hours"] <= row["tutor"]["hours"]:
            raise ValueError(f"{row['difficulty']}: self-study stopped costing more time")

    # Hours double down the difficulty column; money climbs but does NOT double
    # (4s, 8s, then 20s and 40s once it turns into pounds), so only the climb is
    # asserted -- and the conversion is used for nothing else.
    tutor_hours = [row["tutor"]["hours"] for row in costs]
    if tutor_hours != [6, 12, 24, 48]:
        raise ValueError(f"the tutor hours ladder changed: {tutor_hours}")
    money = [as_soli(row["tutor"]) for row in costs]
    if money != sorted(money) or len(set(money)) != len(money):
        raise ValueError(f"the tutor money column stopped climbing: {money}")

    if len(rules) < 5:
        raise ValueError(f"expected the two requirements and three Limits, got {len(rules)}")
    for phrase in PINNED:
        if phrase not in body:
            raise ValueError(f"the sentence the app was read from is gone: {phrase!r}")


def write(name, entries, spans):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: spans}, "entries": entries}
    (OUT / f"{name}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def main():
    records = load_lines()
    table = stream(records, *TABLE_RANGE)
    limits = stream(records, *LIMITS_RANGE)

    cells = [text for _, text in table if text not in HEADER]
    costs = parse_costs(cells)
    # The two requirements print as "Tutor:" / "Self-study:" labelled prose,
    # which is what separates them from the intro and the ladder sentence.
    requirements = [text for text in cells if REQUIREMENT.match(text)]
    # The Limits are the bulleted lines; the worked example above them is not.
    bulleted = [text for is_bullet, text in limits if is_bullet]
    rules = [{"rule": text} for text in requirements + bulleted]

    verify(costs, rules, " ".join(text for _, text in table + limits))

    write("study_costs", costs, [list(TABLE_RANGE)])
    write("study_rules", rules, [list(TABLE_RANGE), list(LIMITS_RANGE)])

    print(f"study_costs: {len(costs)} rows")
    for row in costs:
        print(f"  {row['difficulty']:10} tutor {row['tutor']['printed']:14}"
              f" self {row['selfStudy']['printed']}")
    print(f"study_rules: {len(rules)} sentences")
    for rule in rules:
        print(f"  {rule['rule'][:80]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
