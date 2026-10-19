"""Extract Ch. 6.5's Sequence Immunity and its counters (Sec. 5-6, global 3938-3976).

Two files, one system -- the DIVINER's side of anti-divination:

  sequence_immunity.json        what a powerful target costs to divine (Sec. 5)
  anti_divination_counters.json what a determined diviner can do about it (Sec. 6)

**A row prints up to two rules and the second is not implied by the first.**
Seq 4 and Seq 3 both print "-10" AND "only a critical success (3-4) can succeed
-- the penalty cannot be offset by skill alone". Those are different claims: a
skill-20 diviner at -10 rolls against 10, and an ordinary 10 succeeds. The
sentence exists precisely to forbid that, so `requiresCritical` is its own
field. com.pathways.rules.AntiDivination routes it into `ModEffect.RequireCrit`,
which SkillRoll has honoured since M1.

**Seq 2 (Angel) prints -10 and NO critical clause**, while the two weaker rows
do. Taken literally the strongest listed target is easier to divine at high
skill than a Demigod. That looks like a slip, and CLAUDE.md 4.1 says the book
wins and the disagreement is recorded rather than repaired -- so verify()
asserts the asymmetry is still there, and will fail if a later reading
"tidies" it.

**Two halvings with opposite printed rounding.** Direct Link "halves all
anti-divination penalties (round down)"; Sequence Advantage leaves active
techniques "at half penalty, rounded up". Both act on negatives, which is
exactly the defect class CLAUDE.md 4.3 exists for. The book's only worked
halving is Seq 5's -6 -> -3, which is even and therefore settles nothing about
the odd case -- the Major Wound trap (6n) with the parities swapped. The
reading taken, and pinned in Kotlin rather than here, is that the book halves
the MAGNITUDE: every bullet in a section titled "Countering Anti-Divination"
helps the diviner, and reading "round down" numerically on a negative would
make Direct Link HARSHER than no link at all.

That -6 -> -3 is also the warrant for treating Sections 5 and 6 as one system:
the row's own printed link value equals Direct Link's general halving, so the
two sections agree where they overlap. verify() checks it by value.

Anchors: an immunity row starts at a cell beginning "Seq" and closes its own
brackets ("Seq 5 (Beyonder" / "Threshold)" is one label), the unclosed-bracket
rule extract_wounds.py needs for the Vitals multiplier. A counter is a bullet
whose text opens "Name: ...".
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "8"
IMMUNITY_RANGE = (3849, 3874)
COUNTER_RANGE = (3875, 3887)

# This PDF prints list bullets as U+007F. See extract_defenses.py.
BULLET = "\x7f"
FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADER = {"Sequence", "Penalty"}

ROW_START = re.compile(r"^Seq\b")
BAND = re.compile(r"^Seq (\d+)(?:[–-](\d+))?")
# "is at -6 unless", "is at -10", "are at -10" -- the row's headline penalty.
PENALTY = re.compile(r"\bat (-\d+)")
EQUAL = re.compile(r"Equal-Sequence divination is at (-\d+)")
LINKED = re.compile(r"reduces the penalty to (-\d+)")
CRITICAL = re.compile(r"only a critical success", re.IGNORECASE)
COUNTER_NAME = re.compile(r"^([A-Z][A-Za-z\- ]+): (.+)$")
IGNORES = re.compile(r"ignores (-?\d+) of anti-divination penalties")
HP_COST = re.compile(r"Burning (\d+)\+ HP")

EXPECTED_ROWS = [
    "Seq 5 (Beyonder Threshold)", "Seq 4 (Demigod)", "Seq 3 (Saint)",
    "Seq 2 (Angel)", "Seq 6–9",
]
EXPECTED_COUNTERS = [
    "Sequence Advantage", "Direct Link", "HP Sacrifice",
    "Anti-Anti-Divination Ritual", "Divine Intervention",
]
# The two rows that forbid an ordinary success, and the one that does not.
EXPECTED_CRITICAL = {"Seq 4 (Demigod)", "Seq 3 (Saint)"}
NO_CRITICAL = "Seq 2 (Angel)"
# The rounding sentences AntiDivination reads. Opposite directions on purpose.
ROUNDING = [
    "still apply at half penalty, rounded up",
    "halves all anti-divination penalties (round down)",
]


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    """(is_bullet, text) per line, headers and page furniture dropped."""
    out = []
    for n in range(start, end + 1):
        text = byn.get(n)
        if text is None:
            continue
        stripped = text.strip()
        if not stripped or FURNITURE.match(stripped):
            continue
        if stripped.startswith(BULLET):
            out.append((True, stripped.lstrip(BULLET).strip()))
        elif stripped not in HEADER:
            out.append((False, stripped))
    return out


def label_at(cells, i):
    """The row label starting at cells[i], taking wrapped halves with it.

    "Seq 5 (Beyonder" / "Threshold)" is one cell. Counting lines would work
    today and break the first time a label fits on one.
    """
    parts = []
    for j in range(i, len(cells)):
        parts.append(cells[j])
        joined = " ".join(parts)
        if joined.count("(") == joined.count(")"):
            return joined, j
    raise ValueError(f"row label starting {cells[i]!r} never closes its brackets")


def parse_rows(cells):
    starts = [i for i, c in enumerate(cells) if ROW_START.match(c)]
    if not starts:
        raise ValueError("no Sequence Immunity rows found")
    rows = []
    for k, i in enumerate(starts):
        label, last = label_at(cells, i)
        end = starts[k + 1] if k + 1 < len(starts) else len(cells)
        if last + 1 >= end:
            raise ValueError(f"{label!r} has no penalty cell after it")
        text = " ".join(cells[last + 1:end])
        band = BAND.match(label)
        if band is None:
            raise ValueError(f"{label!r} prints no sequence")
        low = int(band.group(1))
        high = int(band.group(2)) if band.group(2) else low
        penalty = PENALTY.search(text)
        equal = EQUAL.search(text)
        linked = LINKED.search(text)
        rows.append({
            "label": label,
            # The book writes one band low-number-first ("Seq 6-9"), so
            # normalise rather than trusting the printed order.
            "sequenceLow": min(low, high),
            "sequenceHigh": max(low, high),
            "requiresCritical": CRITICAL.search(text) is not None,
            "text": text,
            **({"penalty": int(penalty.group(1))} if penalty else {}),
            **({"equalPenalty": int(equal.group(1))} if equal else {}),
            **({"linkedPenalty": int(linked.group(1))} if linked else {}),
        })
    return rows


def parse_counters(lines):
    joined = []
    for is_bullet, text in lines:
        if is_bullet:
            joined.append(text)
        elif joined:
            joined[-1] += " " + text
    counters = []
    for text in joined:
        named = COUNTER_NAME.match(text)
        if named is None:
            raise ValueError(f"counter bullet has no 'Name:' label: {text[:60]!r}")
        body = named.group(2)
        ignores = IGNORES.search(body)
        hp = HP_COST.search(body)
        counters.append({
            "name": named.group(1),
            "text": body,
            # Extracted rather than scraped at render time, because the app
            # applies both (extract_ritual.py's corGained, same reason).
            **({"ignoresPenalty": int(ignores.group(1))} if ignores else {}),
            **({"hpRequired": int(hp.group(1))} if hp else {}),
        })
    return counters


def verify(rows, counters):
    labels = [r["label"] for r in rows]
    if labels != EXPECTED_ROWS:
        raise ValueError(f"immunity rows changed: {labels} != {EXPECTED_ROWS}")
    names = [c["name"] for c in counters]
    if names != EXPECTED_COUNTERS:
        raise ValueError(f"counters changed: {names} != {EXPECTED_COUNTERS}")

    by = {r["label"]: r for r in rows}

    # THE WARRANT. Seq 5 prints its own direct-link value and Sec. 6 prints a
    # general halving; they must still agree, or the two sections are not one
    # system and Direct Link cannot be applied to an immunity penalty at all.
    seq5 = by["Seq 5 (Beyonder Threshold)"]
    if seq5.get("penalty") != -6 or seq5.get("linkedPenalty") != -3:
        raise ValueError(f"Seq 5 no longer prints -6 halving to -3: {seq5}")
    if seq5["linkedPenalty"] * 2 != seq5["penalty"]:
        raise ValueError(
            f"Seq 5's linked penalty {seq5['linkedPenalty']} is no longer half "
            f"of {seq5['penalty']} -- Direct Link's general rule and this row "
            "have stopped agreeing"
        )

    # Recorded, not repaired: the strongest listed row forbids nothing.
    got_critical = {r["label"] for r in rows if r["requiresCritical"]}
    if got_critical != EXPECTED_CRITICAL:
        raise ValueError(
            f"the critical-only rows changed: {got_critical} != {EXPECTED_CRITICAL}"
        )
    if by[NO_CRITICAL]["requiresCritical"]:
        raise ValueError(
            f"{NO_CRITICAL} now forbids an ordinary success. The book printed "
            "that clause for Seq 4 and Seq 3 only; if it really has changed, "
            "the reading in AntiDivination needs redoing, not this assertion"
        )
    if by[NO_CRITICAL].get("penalty") != -10:
        raise ValueError(f"{NO_CRITICAL} no longer prints -10: {by[NO_CRITICAL]}")

    # Seq 6-9 is a real row that charges nothing, which is not the same as a
    # row that prints no rule -- so it carries no penalty field at all.
    weak = by["Seq 6–9"]
    if "penalty" in weak:
        raise ValueError(f"Seq 6-9 should print no penalty: {weak}")
    if "Sequence Advantage" not in weak["text"]:
        raise ValueError("Seq 6-9 no longer points at the Sequence Advantage rule")

    for row in rows:
        if row.get("penalty", -1) >= 0 or row.get("equalPenalty", -1) >= 0:
            raise ValueError(f"{row['label']} has a non-negative penalty: {row}")

    # The two rounding sentences, read in opposite directions by Kotlin.
    prose = " ".join(c["text"] for c in counters)
    for phrase in ROUNDING:
        if phrase not in prose:
            raise ValueError(f"a rounding rule the app applies is gone: {phrase!r}")
    # ignoresPenalty keeps the sign the book prints ("ignores -2 of ...”), so a
    # caller subtracts it from the penalty rather than guessing a convention.
    hp = next(c for c in counters if c["name"] == "HP Sacrifice")
    if hp.get("ignoresPenalty") != -2 or hp.get("hpRequired") != 4:
        raise ValueError(f"HP Sacrifice's numbers changed: {hp}")
    # Only that one counter carries numbers the app applies; the ritual and the
    # deity are the GM's, and a field on them would imply otherwise.
    numbered = {
        c["name"] for c in counters if "ignoresPenalty" in c or "hpRequired" in c
    }
    if numbered != {"HP Sacrifice"}:
        raise ValueError(f"unexpected counter numbers: {numbered}")


def write(name, entries, spans):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: spans}, "entries": entries}
    (OUT / f"{name}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def main():
    byn = load_lines()
    rows = parse_rows([t for b, t in stream(byn, *IMMUNITY_RANGE) if not b])
    counters = parse_counters(stream(byn, *COUNTER_RANGE))
    verify(rows, counters)

    write("sequence_immunity", rows, [list(IMMUNITY_RANGE)])
    write("anti_divination_counters", counters, [list(COUNTER_RANGE)])

    print(f"sequence_immunity: {len(rows)} rows")
    for r in rows:
        crit = "  CRIT-ONLY" if r["requiresCritical"] else ""
        extra = ""
        if "equalPenalty" in r:
            extra += f" equal {r['equalPenalty']}"
        if "linkedPenalty" in r:
            extra += f" linked {r['linkedPenalty']}"
        print(f"  {r['label']:28} Seq {r['sequenceLow']}-{r['sequenceHigh']:<2} "
              f"{str(r.get('penalty', 'none')):>5}{extra}{crit}")
    print(f"anti_divination_counters: {len(counters)}")
    for c in counters:
        n = ""
        if "ignoresPenalty" in c:
            n += f" ignores {c['ignoresPenalty']}"
        if "hpRequired" in c:
            n += f", {c['hpRequired']} HP"
        print(f"  {c['name']:30}{n}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
