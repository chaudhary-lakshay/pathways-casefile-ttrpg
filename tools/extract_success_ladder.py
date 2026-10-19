"""Extract Appendix A's degree-of-success ladder (p. 159).

WHY AN APPENDIX GETS TO DEFINE A MECHANIC
------------------------------------------
CLAUDE.md 6v set the rule this slice has to answer to: "a summary does not
overrule the chapter it summarises". Appendix A is a quick-reference summary
and it demonstrably gets things wrong -- its hit-location line drops the
Vitals row's "(imp/pi only)" scope and invents a "Hand/Foot" location chapter
5 never prints.

This table is not a summary. "Good Success" and "Excellent Success" appear at
global lines 9680 and 9682 and NOWHERE else in the book. Chapter 2 points at
the idea and tabulates nothing -- "Margins matter -- the degree of success or
failure often determines quality of outcome" (line 278) -- and five Sequence
abilities then say an effect scales with "the degree of success" (4679, 9419,
9556, 9595, 9625). So the pointer is in the chapters and the only table is
here. Same shape as chapter 3's "See the Languages section for cost tables"
(6x): a cross-reference is what makes another page's numbers reachable.

THE EVIDENCE IS PER-TABLE, NOT PER-APPENDIX
--------------------------------------------
"The appendix is reliable" is exactly the generalisation 6v disproves, so the
warrant has to come from this table alone. Five of its seven rows are rules
`SuccessRoll` has implemented since M0 -- 3-4 always succeeds, 17-18 always
fails, roll <= target succeeds, roll > target fails, roll >= target+10 is a
critical failure. verify() checks all five against the values the Kotlin
holds. Where a table repeats what the chapters price and agrees exactly, its
unrepeated rows are the same kind of thing; that is the standard the
appendix's "Pi" abbreviation had to meet in 6v, and it is the only reason the
two new rows are adopted.

THE BANDS OVERLAP AND THE ORDER IS THE ANSWER
----------------------------------------------
A roll of Target-5 satisfies "<= Target", "<= Target -3" and "<= Target -5".
The table prints them in that order and the most demanding one is the result,
so a reader takes the LAST matching row rather than the first. verify() pins
that the two degrees stay strictly ordered and both stay below plain success,
because a parse that swapped them would still produce a table that looks
right.

WHAT IS DELIBERATELY NOT EXTRACTED
-----------------------------------
Two other real tables sit on the same page and are their own slices:

- "Common Modifiers" (9725-9745), nine rows of GM circumstance -- easy task
  +4 or more, adverse -4, improvised tools -2 to -4, aided +2. That is a
  fourth modifier source beside `Conditions`, `Attacks` and `Ranged`, with the
  same caller already built, and it deserves the ranges-and-scope treatment
  the others got rather than being tacked on here.
- "SPI Recovery Reference" (9718-9724), one row: rest, 1 SPI per hour, no
  strenuous activity. Nothing in the app recovers SPI and nothing in it holds
  a clock, so a per-hour rate has no caller to reach.

The difficulty grid at 9634-9664 restates chapter 3's skill ladder, which
`skill_ladder.json` already carries from the chapter itself (6ac). This is a
separate file from `extract_appendix.py` for the reason that file's own
docstring gives: one table, one extractor, provenance visible.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "A"
TABLE_RANGE = (8932, 8947)
CORE_RANGE = (8890, 8892)
POINTER_RANGE = (278, 278)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADERS = {"Roll", "Result"}

DASHES = "-–—"
ROLL_BAND = re.compile(rf"^(\d+)[{DASHES}](\d+)$")
COMPARISON = re.compile(rf"^Roll (<=|>=|<|>) Target(?: ([+{DASHES}]) ?(\d+))?$")

# The five rows SuccessRoll has priced since M0. These are the warrant for
# adopting the two rows nothing else prints, so they are checked by value and
# not merely by presence.
EXPECTED_ALWAYS_SUCCEEDS = (3, 4)
EXPECTED_ALWAYS_FAILS = (17, 18)
EXPECTED_SUCCESS_OFFSET = 0
EXPECTED_FAILURE_OFFSET = 0
EXPECTED_CRITICAL_MARGIN = 10

# The two rows this table alone prints.
DEGREES = ("Good Success", "Excellent Success")
EXPECTED_DEGREE_OFFSETS = {"Good Success": -3, "Excellent Success": -5}

EXPECTED_ORDER = [
    "Critical Success (always)",
    "Success",
    "Good Success",
    "Excellent Success",
    "Failure",
    "Critical Failure",
    "Critical Failure (always)",
]

CORE_MECHANIC = "Roll 3d6 Equal to or Under Target Number"
CRIT_LINE = "Critical Failure: 17"
# Chapter 2's pointer. Without it this table is an appendix inventing a rule;
# with it, the chapters ask for a degree and only this page supplies one.
POINTER = "the degree of success or failure often determines quality of outcome"


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    out = []
    for n in range(start, end + 1):
        # This PDF prints list bullets as U+007F (6g, and it cost a debugging
        # cycle again in 6ac).
        text = (byn.get(n) or "").replace("\x7f", " ").strip()
        if not text or FURNITURE.match(text) or text in HEADERS:
            continue
        out.append(text)
    return out


def signed(sign, digits):
    return int(digits) * (-1 if sign in DASHES else 1)


def parse_rows(cells):
    """Condition cell then result cell, seven times.

    The CONDITION is the anchor: every one of them is either a bare roll band
    or a comparison against Target, and no result cell is either. Pairing off
    the stream without checking that would let one dropped cell shift every
    row below it while still producing a plausible table.
    """
    rows, pending = [], None
    for cell in cells:
        band = ROLL_BAND.fullmatch(cell)
        comparison = COMPARISON.fullmatch(cell)
        if band or comparison:
            if pending is not None:
                raise ValueError(f"two conditions in a row: {pending!r} then {cell!r}")
            if band:
                pending = {"rollLow": int(band.group(1)), "rollHigh": int(band.group(2))}
            else:
                op, sign, digits = comparison.groups()
                pending = {
                    "comparison": op,
                    "offset": signed(sign, digits) if digits else 0,
                }
            continue
        if pending is None:
            raise ValueError(f"a result cell with no condition above it: {cell!r}")
        pending["result"] = cell
        rows.append(pending)
        pending = None
    if pending is not None:
        raise ValueError(f"the last condition has no result: {pending!r}")
    if not rows:
        raise ValueError("the degree-of-success ladder is gone")
    return rows


def find(rows, result):
    for row in rows:
        if row["result"] == result:
            return row
    raise ValueError(f"the {result!r} row is gone")


def verify(rows, core, pointer):
    if [r["result"] for r in rows] != EXPECTED_ORDER:
        raise ValueError(f"the ladder's rows changed: {[r['result'] for r in rows]}")

    # THE WARRANT. Five rows repeat what SuccessRoll already implements; the
    # two new ones are adopted only because these agree exactly.
    crit_success = find(rows, "Critical Success (always)")
    if (crit_success.get("rollLow"), crit_success.get("rollHigh")) != EXPECTED_ALWAYS_SUCCEEDS:
        raise ValueError(f"the always-succeeds band changed: {crit_success}")
    crit_failure = find(rows, "Critical Failure (always)")
    if (crit_failure.get("rollLow"), crit_failure.get("rollHigh")) != EXPECTED_ALWAYS_FAILS:
        raise ValueError(f"the always-fails band changed: {crit_failure}")

    success = find(rows, "Success")
    if success.get("comparison") != "<=" or success.get("offset") != EXPECTED_SUCCESS_OFFSET:
        raise ValueError(f"plain success is no longer roll <= target: {success}")
    failure = find(rows, "Failure")
    if failure.get("comparison") != ">" or failure.get("offset") != EXPECTED_FAILURE_OFFSET:
        raise ValueError(f"plain failure is no longer roll > target: {failure}")
    by_margin = find(rows, "Critical Failure")
    if by_margin.get("comparison") != ">=" or by_margin.get("offset") != EXPECTED_CRITICAL_MARGIN:
        raise ValueError(
            f"the fail-by-10 row changed: {by_margin}. SuccessRoll reads it as "
            "margin <= -10, which is the same rule written from the target's side")

    # The two rows nothing else in the book prints.
    for name in DEGREES:
        row = find(rows, name)
        if row.get("comparison") != "<=":
            raise ValueError(f"{name} is no longer a roll-under band: {row}")
        if row.get("offset") != EXPECTED_DEGREE_OFFSETS[name]:
            raise ValueError(
                f"{name}'s offset changed: {row.get('offset')} != "
                f"{EXPECTED_DEGREE_OFFSETS[name]}")

    # The bands OVERLAP -- Target-5 satisfies all three roll-under rows -- so
    # the most demanding match is the answer. A parse that swapped these two
    # would still look like a table.
    good = find(rows, "Good Success")["offset"]
    excellent = find(rows, "Excellent Success")["offset"]
    if not excellent < good < EXPECTED_SUCCESS_OFFSET:
        raise ValueError(
            f"the degrees are no longer strictly harder than plain success: "
            f"excellent {excellent}, good {good}")

    if CORE_MECHANIC not in core:
        raise ValueError(f"the core mechanic line is gone: {core!r}")
    if CRIT_LINE not in core:
        raise ValueError(f"the critical rules line is gone: {core!r}")
    if POINTER not in pointer:
        raise ValueError(
            "chapter 2 no longer points at degrees of success, which is what "
            "makes this appendix's table a definition rather than a summary")


def extract():
    byn = load_lines()
    rows = parse_rows(stream(byn, *TABLE_RANGE))
    core = " ".join(stream(byn, *CORE_RANGE))
    pointer = " ".join(stream(byn, *POINTER_RANGE))
    verify(rows, core, pointer)
    return rows


def main():
    rows = extract()

    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "success_ladder.json").write_text(
        json.dumps(
            {
                "sourceLines": {CHAPTER: [list(TABLE_RANGE)]},
                "entries": rows,
            },
            ensure_ascii=False, indent=1) + "\n", encoding="utf-8")

    for row in rows:
        if "rollLow" in row:
            condition = f"{row['rollLow']}-{row['rollHigh']}"
        else:
            condition = f"roll {row['comparison']} target{row['offset']:+d}"
        print(f"  {condition:26} {row['result']}")
    print("five rows repeat what SuccessRoll already implements; "
          "Good and Excellent are printed nowhere else in the book")
    return 0


if __name__ == "__main__":
    sys.exit(main())
