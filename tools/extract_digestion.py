"""Extract the two digestion tables of Chapter 6 into committed JSON content.

Source shape (verified against build/extract/lines.json, not assumed). Both
tables print as a column of labels followed by a column of values, flattened
by the PDF into alternating lines.

Acting Method, "Digestion Gain Per Session" (global lines 3046-3057):

     Acting Quality
     Digestion Gain Per Session
    Exemplary (fully embodies pathway nature)
     +15-20%
    ...
    None (ignores pathway nature entirely)
     0% or loses progress

Every quality prints a RANGE, not a figure -- the GM picks within it. The
last row is not a range at all ("0% or loses progress"), so it carries
losesProgress instead of a gain; a parser that forced it into the numeric
shape would invent a 0% floor the book does not give.

Potion Consumption Roll (global lines 3082-3119): digestion percentage, its
modifier, and the effective target for `3d6 <= 10 + modifier`.

    -9 / -7 / -5 / -4 / -2 / 0 / +2 / +4 / +5 / +7 / +9

That progression is NOT linear -- the steps are 2,2,1,2,2,2,2,1,2,2 -- so
the table ships verbatim as data and is never derived, the same trap the
thrust/swing table sets (spec 7.3). The effective target is printed too and
is kept as printed rather than recomputed, so a disagreement between the
book's own two columns would surface instead of being silently smoothed.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "6"
ACTING_RANGE = (3046, 3057)
PCR_RANGE = (3082, 3119)
# The two prose lines the "10 + modifier" check below was read from. Claimed
# alongside the table, because an assertion whose source sentence sits outside
# the claimed span is a rule nobody can find again.
FORMULA_RANGE = (3079, 3081)
FORMULA = "Roll 3d6 <= 10 + Digestion Modifier."
# The PCR is a raw roll: no stat, no skill. Pinned because PotionConsumption
# takes no skill level, and this sentence is the only reason it does not.
RAW_ROLL = "no stat or skill applies"

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
QUALITY = re.compile(r"^([A-Z][a-z]+)\s*\(([^)]*)\)$")
GAIN_RANGE = re.compile(r"^\+(\d+)[–-](\d+)%$")
LOSES = re.compile(r"^0% or loses progress$")
PERCENT = re.compile(r"^(\d+)%$")
MODIFIER = re.compile(r"^([+-]?\d+)$")
TARGET = re.compile(r"^(\d+)(?:\s*[—-]\s*(.+))?$")


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    """The lines of a range, stripped, with running headers dropped."""
    out = []
    for n in range(start, end + 1):
        text = byn.get(n)
        if text is None:
            continue
        stripped = text.strip()
        if not stripped or FURNITURE.match(stripped):
            continue
        out.append(stripped)
    return out


def parse_acting(lines):
    """Rows of the Acting Quality / Digestion Gain Per Session table."""
    rows = []
    i = 0
    while i < len(lines) - 1:
        quality = QUALITY.match(lines[i])
        if not quality:
            i += 1
            continue
        value = lines[i + 1]
        row = {"quality": quality.group(1), "description": quality.group(2)}
        gain = GAIN_RANGE.match(value)
        if gain:
            row["minGain"] = int(gain.group(1))
            row["maxGain"] = int(gain.group(2))
        elif LOSES.match(value):
            # The book gives no number here at all; inventing 0 would be a
            # rule, not an extraction.
            row["losesProgress"] = True
        else:
            raise ValueError(f"unparsed acting-quality gain: {value!r}")
        rows.append(row)
        i += 2
    return rows


def parse_pcr(lines):
    """Rows of the digestion / modifier / effective target table."""
    rows = []
    i = 0
    while i < len(lines) - 2:
        percent = PERCENT.match(lines[i])
        modifier = MODIFIER.match(lines[i + 1])
        target = TARGET.match(lines[i + 2])
        if not (percent and modifier and target):
            i += 1
            continue
        row = {
            "digestion": int(percent.group(1)),
            "modifier": int(modifier.group(1)),
            "effectiveTarget": int(target.group(1)),
        }
        if target.group(2):
            row["note"] = target.group(2).strip()
        rows.append(row)
        i += 3
    return rows


def verify(acting, pcr, formula=""):
    if formula:
        # The sentence the column check below is read from. Without it the two
        # columns agreeing would be a coincidence nobody had checked against
        # the book's own statement of the rule.
        for phrase in (FORMULA, RAW_ROLL):
            if phrase not in formula:
                raise ValueError(f"the PCR's printed rule is gone: {phrase!r}")
    if len(acting) != 5:
        raise ValueError(f"expected 5 acting-quality rows, got {len(acting)}")
    percentages = [r["digestion"] for r in pcr]
    if percentages != list(range(0, 101, 10)):
        raise ValueError(f"expected 0..100 by tens, got {percentages}")
    for row in pcr:
        # The book prints both columns; they must agree, or the disagreement
        # is the finding (CLAUDE.md 4.1) rather than something to smooth over.
        if row["effectiveTarget"] != 10 + row["modifier"]:
            raise ValueError(
                f"{row['digestion']}%: printed target {row['effectiveTarget']} "
                f"disagrees with 10 + {row['modifier']}"
            )


def write(name, entries, spans):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: spans}, "entries": entries}
    (OUT / f"{name}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def main():
    byn = load_lines()
    acting = parse_acting(stream(byn, *ACTING_RANGE))
    pcr = parse_pcr(stream(byn, *PCR_RANGE))
    formula = " ".join(stream(byn, *FORMULA_RANGE))
    verify(acting, pcr, formula)

    write("acting_method", acting, [list(ACTING_RANGE)])
    # Two spans: the prose that states the rule, then the table that tabulates
    # it. Parsed separately, because only the table has columns.
    write("potion_consumption", pcr, [list(FORMULA_RANGE), list(PCR_RANGE)])

    print(f"acting_method: {len(acting)} rows")
    print(f"potion_consumption: {len(pcr)} rows, "
          f"modifiers {[r['modifier'] for r in pcr]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
