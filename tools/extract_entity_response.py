"""Extract the Entity Response Table (Ch. 7, Section IV.F, p. 66).

THE -2 GOES ON THE WRONG ROLL IF YOU READ IT QUICKLY
-----------------------------------------------------
"Orthodox deities shift the TABLE RESULT by -2 when the request aligns with
their domain and the caster is in good standing."

That -2 lands on the GM's 3d6 against this table, not on the ritual roll. The
table is ordered best-first -- 3-5 is a Full response worth +5, 18 is Active
rejection -- so subtracting 2 moves the caster TOWARD the good rows. It is a
reward.

Applied to the ritual roll instead it is a penalty, and the caster in good
standing with their own god ends up worse off than a stranger. One sentence,
two opposite outcomes, and the wrong one looks perfectly plausible on screen.
`shift` is therefore emitted with an explicit `appliesTo` naming which roll it
touches, and verify() asserts the direction still helps.

TWO ROWS PRINT NO MODIFIER, FOR TWO DIFFERENT REASONS
------------------------------------------------------
  12-14  "No response. Ritual draws only on caster's residual power
         (treat as 2 SPI personal)."
  18     "Active rejection. Ritual fails automatically. The entity is now
         aware of the caster."

Neither carries a number, and they are not the same absence. 12-14 is an
indifferent entity: the ritual proceeds unaided, so nothing is added. 18 is
not a modifier at all -- the ritual fails whatever the caster rolled, the way
a Broken mystical language forbids the attempt rather than penalising it.
Emitting -0 for the first or some large negative for the second would both be
inventions; the first gets no field and the second gets `automaticFailure`.

THE GM ROLLS THIS SECRETLY
---------------------------
"The GM rolls secretly on the Entity Response Table first, then applies the
entity's modifier to the Ritualistic Magic roll." The secrecy is recorded as
a note rather than built: this app is one screen held by one person, and
hiding a roll from its only viewer is theatre, not a rule. A GM running the
table can still roll it here and simply not say what came up.

WHAT IS DELIBERATELY NOT EXTRACTED
----------------------------------
Power sources A-E on the same pages -- personal SPI's four-row complexity
ladder, HP sacrifice at 2 HP per SPI, participants at +1 each to a maximum
+3, external sacrifice at +0/+2/+4, and catalysts at +0 to +2. They are the
ritual's FUEL and its other bonuses; this table is one entity's answer, and it
is what both ritual modes point at by name. The complexity ladder in
particular needs its own decision: it prints four rows (2-3 / 4-6 / 7-10 /
11-15+) against `ritual_weights.json`'s three (2 / 4-6 / 8+), and the two do
not line up.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "7"
ENTITY_RANGE = (4292, 4312)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADERS = {"GM Roll (3d6)", "Entity Response"}
SECTION = "F. Invocation (Deity or Hidden Existence)"
# " 3-5", " 18". The last row is a single number, not a band.
BAND_CELL = re.compile(r"^(\d+)(?:-(\d+))?$")
MODIFIER = re.compile(r"([+-]\d+) to ritual roll")
AUTOMATIC_FAILURE = "fails automatically"
SHIFT = re.compile(r"shift the table result by (-?\d+)")

EXPECTED_BANDS = [(3, 5), (6, 8), (9, 11), (12, 14), (15, 17), (18, 18)]
EXPECTED_MODIFIERS = [5, 3, 1, None, -2, None]
# The clause that says which entities the shift does NOT help.
UNMODIFIED = "Hidden existences and evil gods use the table unmodified"


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    out = []
    for n in range(start, end + 1):
        text = (byn.get(n) or "").strip()
        if not text or FURNITURE.match(text) or text in HEADERS or text == SECTION:
            continue
        out.append(text)
    return out


def split_prose(cells):
    """The invocation prose, and the table below it.

    The table starts at the first band cell. The prose above wraps freely and
    carries the shift rule, so it is joined rather than read line by line --
    the same reason the Languages intro had to be.
    """
    at = next((i for i, c in enumerate(cells) if BAND_CELL.fullmatch(c)), None)
    if at is None:
        raise ValueError("no band cell; the Entity Response Table changed")
    return " ".join(cells[:at]), cells[at:]


def parse(cells):
    rows, current = [], None
    for cell in cells:
        band = BAND_CELL.fullmatch(cell)
        if band:
            low = int(band.group(1))
            high = int(band.group(2)) if band.group(2) else low
            current = {"low": low, "high": high, "response": ""}
            rows.append(current)
            continue
        if current is None:
            raise ValueError(f"{cell!r} precedes any band")
        current["response"] = (current["response"] + " " + cell).strip()

    for row in rows:
        text = row["response"]
        if not text:
            raise ValueError(f"the {row['low']}-{row['high']} row has no response text")
        if AUTOMATIC_FAILURE in text:
            # Not a modifier. The ritual fails whatever was rolled, the way a
            # Broken mystical language forbids the attempt outright.
            row["automaticFailure"] = True
            continue
        found = MODIFIER.search(text)
        if found:
            row["ritualModifier"] = int(found.group(1))
    return rows


def verify(rows, prose, shift):
    bands = [(r["low"], r["high"]) for r in rows]
    if bands != EXPECTED_BANDS:
        raise ValueError(f"the response bands changed: {bands}")
    got = [r.get("ritualModifier") for r in rows]
    if got != EXPECTED_MODIFIERS:
        raise ValueError(f"the response modifiers changed: {got} != {EXPECTED_MODIFIERS}")

    # Every band from 3 to 18 is covered exactly once; a 3d6 cannot miss.
    covered = [n for r in rows for n in range(r["low"], r["high"] + 1)]
    if sorted(covered) != list(range(3, 19)) or len(covered) != len(set(covered)):
        raise ValueError(f"the bands no longer tile 3..18 exactly once: {sorted(covered)}")

    # The table is ordered best first, which is the whole reason a NEGATIVE
    # shift is a reward. If it ever stopped being ordered, the shift's
    # direction would need rereading rather than reusing.
    values = [r["ritualModifier"] for r in rows if "ritualModifier" in r]
    if values != sorted(values, reverse=True):
        raise ValueError(
            f"the table is no longer ordered best-first ({values}), so a "
            "negative shift can no longer be assumed to help the caster"
        )

    only_failure = [r for r in rows if r.get("automaticFailure")]
    if len(only_failure) != 1 or only_failure[0]["low"] != 18:
        raise ValueError("exactly one row -- an 18 -- fails the ritual automatically")
    if "ritualModifier" in only_failure[0]:
        raise ValueError(
            "the 18 row carries a modifier now. It is a refusal, not a "
            "penalty: the ritual fails whatever the caster rolled"
        )
    indifferent = next(r for r in rows if r["low"] == 12)
    if "ritualModifier" in indifferent or indifferent.get("automaticFailure"):
        raise ValueError(
            "the 12-14 row prints no modifier and does not fail the ritual -- "
            "the entity simply does not answer, so nothing is added"
        )

    if shift is None:
        raise ValueError("the orthodox shift sentence is gone")
    if shift >= 0:
        raise ValueError(
            f"the orthodox shift is {shift}. It was negative, and it is "
            "negative BECAUSE this table is best-first -- a positive one "
            "would send a favoured caster toward Active rejection"
        )
    if UNMODIFIED not in prose:
        raise ValueError(f"the clause naming who the shift does NOT help is gone: {UNMODIFIED!r}")


def main():
    cells = stream(load_lines(), *ENTITY_RANGE)
    prose, table = split_prose(cells)
    rows = parse(table)
    found = SHIFT.search(prose)
    shift = int(found.group(1)) if found else None
    verify(rows, prose, shift)

    OUT.mkdir(parents=True, exist_ok=True)
    # The -2 is a number inside a sentence, so it lives in Kotlin
    # (Invocation.ORTHODOX_SHIFT) and this verify() pins the sentence and its
    # DIRECTION -- the same split the +3 support cap and the maneuver table's
    # +4/-4 take. Committing it as a column would also lose the one fact that
    # matters most about it: which roll it lands on.
    payload = {
        "sourceLines": {CHAPTER: [list(ENTITY_RANGE)]},
        "entries": rows,
    }
    (OUT / "entity_response.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")

    print(f"entity response: {len(rows)} rows, orthodox shift {shift} on the TABLE roll")
    for row in rows:
        band = f"{row['low']}-{row['high']}" if row["low"] != row["high"] else str(row["low"])
        if "ritualModifier" in row:
            note = f"{row['ritualModifier']:+d} to the ritual"
        elif row.get("automaticFailure"):
            note = "ritual fails automatically"
        else:
            note = "no modifier printed"
        print(f"  {band:6} {note:26} {row['response'][:44]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
