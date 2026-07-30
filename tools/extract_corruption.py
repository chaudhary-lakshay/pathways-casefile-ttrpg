"""Extract the two CoR (Corruption) tables into committed JSON content.

Source shape (verified against build/extract/lines.json, not assumed). Both
tables print as a column of labels followed by a column of values, flattened
by the PDF into alternating lines -- the same shape tools/extract_digestion.py
handles.

"CoR Gained" (Ch. 6, p. 45, global lines 3061-3074):

     Action
     CoR Gained
    Using powers while drained of Spirituality
    1 CoR per use
    ...
    Ritual magic critical failure
    1d CoR

Every row prints a DIFFERENT shape of number: a flat 1, a range ("1-3"), an
open-ended floor ("10+"), and a die roll ("1d"). Only the floor is common to
all four, so `minimum` is the only figure every row can honestly carry;
`maximum` is absent where the book gives none, and `dice` is carried verbatim
rather than resolved -- a parser that turned "1d CoR" into 3.5 would be
inventing an average the book never prints.

"Corruption from Ritual Work" (Ch. 7, p. 68, global lines 4388-4397): the
3/5/8/10 threshold ladder. The book files these under ritual work explicitly
-- "Additional ritual-specific thresholds" -- so the scope is carried by the
name all the way through: ritual_cor_thresholds.json, loaded by
ContentLoader.ritualCorThresholds(). No caller can reach for them as general
CoR effects by accident. The design spec (section 5) paraphrases them as
general; the book is the authority (CLAUDE.md 4.1), so the disagreement is
recorded here rather than resolved.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

GAINED_CHAPTER = "6"
GAINED_RANGE = (3061, 3074)
THRESHOLD_CHAPTER = "7"
THRESHOLD_RANGE = (4388, 4397)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADER = {"Action", "CoR Gained", "CoR Total", "Additional Ritual Effect"}
# En dash, em dash and hyphen all appear as the range separator in this PDF.
DASH = "–—-"
GAIN = re.compile(
    rf"^(?:(\d+)(?:[{DASH}](\d+))?(\+?)|(\d+d))\s+CoR(?:\s+per\s+(.+))?$",
    re.IGNORECASE,
)
THRESHOLD = re.compile(r"^(\d+)\s+CoR$")


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    """The lines of a range, stripped, with headers and furniture dropped."""
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


def parse_gained(lines):
    """Rows of the Action / CoR Gained table."""
    rows = []
    for i in range(0, len(lines) - 1, 2):
        action, gain = lines[i], lines[i + 1]
        match = GAIN.match(gain)
        if not match:
            raise ValueError(f"unparsed CoR gain for {action!r}: {gain!r}")
        low, high, open_ended, dice, per = match.groups()
        row = {"action": action, "gain": gain}
        if dice:
            # "1d CoR" has no printed floor at all; resolving the die here
            # would put an average in the data the book never states.
            row["dice"] = dice
        else:
            row["minimum"] = int(low)
            if high:
                row["maximum"] = int(high)
            elif not open_ended:
                row["maximum"] = int(low)
            # "10+" prints no ceiling: leaving maximum absent says so.
        if per:
            row["per"] = per
        rows.append(row)
    return rows


def parse_thresholds(lines):
    """Rows of the CoR Total / Additional Ritual Effect ladder."""
    rows = []
    for i in range(0, len(lines) - 1, 2):
        match = THRESHOLD.match(lines[i])
        if not match:
            raise ValueError(f"unparsed CoR threshold: {lines[i]!r}")
        rows.append({"cor": int(match.group(1)), "effect": lines[i + 1]})
    return rows


def verify(gained, thresholds):
    if len(gained) != 6:
        raise ValueError(f"expected 6 CoR-gained rows, got {len(gained)}")
    advancing = [r for r in gained if r["action"].startswith("Advancing sequence")]
    if len(advancing) != 1 or advancing[0].get("minimum") != 10:
        raise ValueError(f"advancing-without-digestion row is not a 10+ floor: {advancing}")
    if "maximum" in advancing[0]:
        raise ValueError("the book prints '10+ CoR' with no ceiling; one was parsed")

    ladder = [r["cor"] for r in thresholds]
    if ladder != [3, 5, 8, 10]:
        raise ValueError(f"expected the 3/5/8/10 ladder, got {ladder}")
    # Corruption.ritualPenalty reads -1 off this row. If the text ever stops
    # saying so, that is the finding, not something to paper over.
    if "-1" not in thresholds[0]["effect"]:
        raise ValueError(f"the 3 CoR row no longer states a -1: {thresholds[0]['effect']!r}")


def write(name, entries, chapter, spans):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {chapter: spans}, "entries": entries}
    (OUT / f"{name}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def main():
    byn = load_lines()
    gained = parse_gained(stream(byn, *GAINED_RANGE))
    thresholds = parse_thresholds(stream(byn, *THRESHOLD_RANGE))
    verify(gained, thresholds)

    write("cor_gained", gained, GAINED_CHAPTER, [list(GAINED_RANGE)])
    write("ritual_cor_thresholds", thresholds, THRESHOLD_CHAPTER, [list(THRESHOLD_RANGE)])

    print(f"cor_gained: {len(gained)} rows")
    print(f"ritual_cor_thresholds: {[r['cor'] for r in thresholds]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
