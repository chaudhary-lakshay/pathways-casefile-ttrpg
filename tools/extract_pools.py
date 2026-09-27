"""Extract the HP and SPI threshold ladders (Ch. 5) into committed JSON.

Source shape (verified against build/extract/lines.json, not assumed). Both
tables print as "Level / Effect" pairs flattened into alternating lines, but
UNLIKE the digestion and CoR tables an effect can wrap onto a second line:

    0 SPI
    Spiritual Exhaustion: All Beyonder abilities deactivate; cannot activate
    Spirit Vision shuts off

So rows are not paired positionally. A level label is a short line naming its
pool ("0 HP", "-5xHP", "<= 1/3 Max SPI"); everything after it belongs to that
row's effect until the next label. verify() then asserts the exact label list,
so a source change breaks loudly instead of silently re-cutting the rows.

FP has NO table of its own and is deliberately absent here. Its thresholds are
printed twice, in prose, in two different sections (p. 41 "FP below 0: -1 to
all rolls per 1 FP below 0", p. 42's recovery table rows "At 0 FP" and "At
1/3 FP or below"). Inventing a fourth table shape to hold them would be
authoring, not extraction; com.pathways.rules.Pools carries them as documented
constants instead.

The level labels are kept VERBATIM and no threshold arithmetic is encoded
here: "-5xHP" means -5 times the character's own maximum, which is a rule, not
a datum. Pools owns the comparison; PoolsTest asserts the labels it assumes
are still the labels the book prints.
"""
import json, pathlib, re, sys

import extract_pathways as pathways

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

SPI_RANGE = (2346, 2356)
HP_RANGE = (2697, 2708)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADER = {"HP Level", "SPI Level", "Effect"}
# A level label is short and names its pool; an effect line is prose.
LABEL_MAX_LEN = 20
POOL = re.compile(r"\b(HP|SPI)\b")

EXPECTED = {
    "HP": ["HP to 1", "1/3 HP remaining", "0 HP", "-HP", "-5×HP"],
    "SPI": ["SPI to 1", "<= 1/3 Max SPI", "0 SPI", "Below 0 SPI"],
}


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


def is_label(line):
    return len(line) <= LABEL_MAX_LEN and POOL.search(line) is not None


def parse(lines, pool):
    """Rows of one Level / Effect ladder, wrapped effects rejoined."""
    rows = []
    for line in lines:
        if is_label(line):
            rows.append({"pool": pool, "level": line, "effect": ""})
        elif rows:
            # A wrapped effect line: rejoin rather than drop. Dropping it lost
            # "Spirit Vision shuts off" from the 0 SPI row.
            rows[-1]["effect"] = (rows[-1]["effect"] + " " + line).strip()
        else:
            raise ValueError(f"{pool}: effect line before any level label: {line!r}")
    for row in rows:
        if not row["effect"]:
            raise ValueError(f"{pool}: level {row['level']!r} has no effect text")
    return rows


def verify(rows):
    for pool, expected in EXPECTED.items():
        levels = [r["level"] for r in rows if r["pool"] == pool]
        if levels != expected:
            raise ValueError(f"{pool} levels changed: {levels} != {expected}")
    # Pools reads both penalties off these two rows as constants.
    reeling = next(r for r in rows if r["level"] == "1/3 HP remaining")
    if "-1" not in reeling["effect"]:
        raise ValueError(f"the reeling row no longer states -1: {reeling['effect']!r}")
    attrition = next(r for r in rows if r["level"] == "<= 1/3 Max SPI")
    if "-3" not in attrition["effect"]:
        raise ValueError(f"the attrition row no longer states -3: {attrition['effect']!r}")


def chapter_spans(heading_of):
    """Which chapter each printed range is claimed under.

    This was `CHAPTER = "5"` -- the question answered by hand, and answered
    wrongly: the SPI ladder prints in chapter 4 (Spirituality) and only the
    HP one is chapter 5's. b1c8c4e refiled it by hand and a re-run of this
    file put it straight back, because the constant knew nothing about the
    correction. It goes through the same `chapter_at` the potion tables use,
    which is the one place that answers this.

    A range no heading precedes RAISES rather than returning None, which
    would write the key as JSON `null` -- a chapter nobody can claim and no
    report would name.
    """
    spans = {}
    for span in (SPI_RANGE, HP_RANGE):
        cid = pathways.chapter_at(span[0], heading_of)
        if cid is None:
            raise ValueError(f"no chapter heading precedes line {span[0]}")
        spans.setdefault(cid, []).append(list(span))
    return spans


def main():
    byn = load_lines()
    rows = parse(stream(byn, *SPI_RANGE), "SPI") + parse(stream(byn, *HP_RANGE), "HP")
    verify(rows)

    OUT.mkdir(parents=True, exist_ok=True)
    payload = {
        "sourceLines": chapter_spans(pathways.heading_of_chapters(pathways.load_lines())),
        "entries": rows,
    }
    (OUT / "pool_thresholds.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )
    print(f"pool_thresholds: {len(rows)} rows "
          f"({sum(r['pool'] == 'HP' for r in rows)} HP, {sum(r['pool'] == 'SPI' for r in rows)} SPI)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
