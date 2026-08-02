"""Extract the ritual resolution tables (Ch. 7, p. 67-68) into committed JSON.

Three tables, all label-then-prose with effects that wrap onto extra lines --
the same shape tools/extract_pools.py handles, and for the same reason: rows
cannot be paired positionally.

WEIGHTS (global lines 4337-4345), the modifier a ritual's category carries:

    Light (+0)
    Divination (1-2 SPI via Divination Arts), Communication (minor), Warding (simple)
    Moderate (-2)
    Enhancement, Curse, Cleansing, Marking, Fabrication, Concealment, Oath-Sealing,
    Protection/Warding

FAILURE BANDS (global lines 4349-4359), what a missed roll costs by margin.
The last band is not a margin at all -- "Critical Failure (roll 17-18)" is a
roll, not a distance -- so it ships with `critical` set and no margin, rather
than being forced into a numeric band it does not occupy.

CRITICAL FAILURE TABLE (global lines 4361-4384), 3d6 with nine bands. Its
outcomes are NOT ordered by severity: 3-4 is Backlash and 18 is Catastrophe,
but 13-14 (1 CoR) sits above 11-12 (Wrong Arrival). That matters because the
book shifts this roll by -2 for a severe misfire, and a caller that assumed
monotonic severity would read the shift backwards.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "7"
WEIGHT_RANGE = (4337, 4345)
# The SAME three weights, printed a second time with the columns the summary
# above leaves out. The summary is where the categories come from; this is the
# only place the SPI cost appears, and a ritual that costs nothing is a ritual
# the app lets a caster spam.
WEIGHT_COST_RANGE = (4318, 4335)
PREPARATION_RANGE = (4120, 4139)
FAILURE_RANGE = (4349, 4359)
CRITICAL_RANGE = (4361, 4384)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADER = {
    "Weight", "Categories", "Result", "Consequence", "Roll", "Outcome",
    "Base Mod", "Typical SPI", "Example Categories",
    "Preparation Quality", "Modifier", "What This Looks Like",
}

# "2 SPI", "4-6 SPI", "8+ SPI" -- one fixed, one a range the GM picks inside,
# one open at the top.
SPI_COST = re.compile(r"^(\d+)(?:\s*[-−–—]\s*(\d+)|\s*(\+))?\s*SPI$")
WEIGHT_NAME = re.compile(r"^(Light|Moderate|Heavy)$")
# "Excellent", "Good", "Adequate", "Poor", "None" -- the closed column.
PREPARATIONS = ["Excellent", "Good", "Adequate", "Poor", "None"]
# "+3", "+1 to +2", "-4 or worse"
PREP_MODIFIER = re.compile(r"^([+\-−]\d+)(?:\s*to\s*([+\-−]\d+))?")

MINUS = "-−"
WEIGHT = re.compile(rf"^(Light|Moderate|Heavy)\s*\(([+{MINUS}]?\d+)\)$")
# "Failure by 1-4", "Failure by 10+", "Critical Failure (roll 17-18)"
FAILURE = re.compile(rf"^Failure by (\d+)(?:[{MINUS}–—](\d+)|(\+))$")
CRITICAL_LABEL = re.compile(r"^Critical Failure \(roll [\d–—-]+\)$")
BAND = re.compile(rf"^(\d+)(?:[{MINUS}–—](\d+))?$")
# "Corruption. Gain 1 CoR." / "... Gain 2 CoR. Nightmares for 1d weeks."
CRITICAL_COR = re.compile(r"\bGain (\d+) CoR\b", re.IGNORECASE)

EXPECTED_CRITICAL_BANDS = [(3, 4), (5, 6), (7, 8), (9, 10), (11, 12),
                           (13, 14), (15, 16), (17, 17), (18, 18)]


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
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


def normalise(value):
    return value.replace("−", "-")


def collect(lines, is_label, start_row):
    """Rows of a label/prose table, wrapped prose rejoined onto its own row."""
    rows = []
    for line in lines:
        if is_label(line):
            rows.append(start_row(line))
        elif rows:
            rows[-1]["text"] = f"{rows[-1].get('text', '')} {line}".strip()
        else:
            raise ValueError(f"prose before any label: {line!r}")
    for row in rows:
        if not row.get("text"):
            raise ValueError(f"row {row!r} has no text")
    return rows


def without_text(row, key):
    return {k: v for k, v in row.items() if k != "text"} | {key: row["text"]}


def parse_weights(lines):
    def start(line):
        match = WEIGHT.match(line)
        return {"weight": match.group(1), "modifier": int(normalise(match.group(2)))}

    rows = collect(lines, lambda l: WEIGHT.match(l) is not None, start)
    return [without_text(r, "categories") for r in rows]


def parse_weight_costs(lines):
    """{weight: (spi, spiMax, spiOpen)} off the fuller Effect Categories table.

    Anchored on the weight NAME, which is a closed set of three; the Base Mod
    column repeats what the summary table already gives, so only the SPI cell
    is read here.
    """
    costs = {}
    for i, line in enumerate(lines):
        if not WEIGHT_NAME.match(line):
            continue
        cell = next(
            (c for c in lines[i + 1:i + 4] if SPI_COST.match(normalise(c))),
            None,
        )
        if cell is None:
            raise ValueError(f"{line!r} prints no SPI cost")
        low, high, plus = SPI_COST.match(normalise(cell)).groups()
        costs[line] = (int(low), int(high) if high else None, bool(plus))
    return costs


def parse_preparation(lines):
    """Preparation Quality / Modifier / What This Looks Like (Phase I)."""
    rows = []
    for i, line in enumerate(lines):
        if line not in PREPARATIONS:
            continue
        if i + 1 >= len(lines):
            raise ValueError(f"{line!r} prints no modifier")
        cell = normalise(lines[i + 1])
        match = PREP_MODIFIER.match(cell)
        if not match:
            raise ValueError(f"{line!r} prints an unreadable modifier: {cell!r}")
        entry = {
            "quality": line,
            "modifier": int(match.group(1)),
            "printed": lines[i + 1],
        }
        if match.group(2):
            # "+1 to +2" and "-1 to -2": a range the GM picks inside.
            entry["best"] = int(match.group(2))
        if "worse" in cell:
            # "-4 or worse" is a floor, not a value.
            entry["openEnded"] = True
        rows.append(entry)
    # The prose cell runs from the modifier to the next quality, and two of
    # them wrap onto a second line.
    starts = [i for i, line in enumerate(lines) if line in PREPARATIONS]
    for pos, i in enumerate(starts):
        end = starts[pos + 1] if pos + 1 < len(starts) else len(lines)
        rows[pos]["looksLike"] = " ".join(lines[i + 2:end])
        if not rows[pos]["looksLike"]:
            raise ValueError(f"{rows[pos]['quality']!r} has no description")
    return rows


def parse_failures(lines):
    def start(line):
        if CRITICAL_LABEL.match(line):
            # Not a margin band at all: this row is entered by the ROLL, so it
            # carries no from/to. Giving it one would let a margin lookup match
            # it and skip the real severe-misfire row.
            return {"printed": line, "critical": True}
        low, high, plus = FAILURE.match(line).groups()
        return {
            "printed": line,
            "from": int(low),
            "to": None if plus else int(high),
            "critical": False,
        }

    def is_label(line):
        return FAILURE.match(line) is not None or CRITICAL_LABEL.match(line) is not None

    rows = collect(lines, is_label, start)
    return [without_text(r, "consequence") for r in rows]


def parse_critical(lines):
    def start(line):
        low, high = BAND.match(line).groups()
        return {"low": int(low), "high": int(high) if high else int(low), "printed": line}

    rows = collect(lines, lambda l: BAND.match(l) is not None, start)
    out = []
    for row in rows:
        entry = without_text(row, "outcome")
        # The cost is printed inside the prose ("Corruption. Gain 1 CoR."), so
        # it is read here rather than in the app: a UI that scanned rule text
        # for numbers at render time would be a parser nobody tests.
        charge = CRITICAL_COR.search(entry["outcome"])
        if charge:
            entry["corGained"] = int(charge.group(1))
        out.append(entry)
    return out


def verify(weights, failures, critical, preparation):
    if [w["weight"] for w in weights] != ["Light", "Moderate", "Heavy"]:
        raise ValueError(f"weights changed: {weights}")
    if [w["modifier"] for w in weights] != [0, -2, -4]:
        raise ValueError(f"weight modifiers changed: {[w['modifier'] for w in weights]}")

    margins = [(f.get("from"), f.get("to")) for f in failures if not f["critical"]]
    if margins != [(1, 4), (5, 9), (10, None)]:
        raise ValueError(f"failure bands changed: {margins}")
    if sum(f["critical"] for f in failures) != 1:
        raise ValueError("expected exactly one critical-failure row")

    bands = [(c["low"], c["high"]) for c in critical]
    if bands != EXPECTED_CRITICAL_BANDS:
        raise ValueError(f"critical bands changed: {bands} != {EXPECTED_CRITICAL_BANDS}")
    # 3d6 cannot roll outside 3..18, and every value in it must find a row.
    covered = {n for c in critical for n in range(c["low"], c["high"] + 1)}
    if covered != set(range(3, 19)):
        raise ValueError(f"critical table does not cover 3..18: {sorted(covered)}")

    # Three rows charge CoR and the rest charge none. If that changes, the
    # ritual screen starts under- or over-charging silently.
    charges = {c["printed"]: c.get("corGained") for c in critical if c.get("corGained")}
    if charges != {"13-14": 1, "15-16": 1, "17": 2}:
        raise ValueError(f"CoR charges changed: {charges}")

    # Every weight must cost something. A ritual the app charges nothing for
    # is one a caster can repeat forever, which is the opposite of what SPI is
    # in this book for.
    costs = {w["weight"]: w.get("spi") for w in weights}
    if costs != {"Light": 2, "Moderate": 4, "Heavy": 8}:
        raise ValueError(f"ritual SPI costs changed: {costs}")
    if not next(w for w in weights if w["weight"] == "Heavy").get("spiOpen"):
        raise ValueError("Heavy used to print '8+ SPI'; the open end is gone")
    if next(w for w in weights if w["weight"] == "Moderate").get("spiMax") != 6:
        raise ValueError("Moderate used to print '4-6 SPI'; the range is gone")

    got = [p["quality"] for p in preparation]
    if got != PREPARATIONS:
        raise ValueError(f"preparation qualities changed: {got} != {PREPARATIONS}")
    if [p["modifier"] for p in preparation] != [3, 1, 0, -1, -4]:
        raise ValueError(f"preparation modifiers changed: {preparation}")
    if not next(p for p in preparation if p["quality"] == "None").get("openEnded"):
        raise ValueError("'None' used to print '-4 or worse'; the floor is gone")


def write(name, entries, spans):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: spans}, "entries": entries}
    (OUT / f"{name}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def main():
    byn = load_lines()
    weights = parse_weights(stream(byn, *WEIGHT_RANGE))
    costs = parse_weight_costs(stream(byn, *WEIGHT_COST_RANGE))
    for weight in weights:
        low, high, open_ended = costs[weight["weight"]]
        weight["spi"] = low
        if high is not None:
            weight["spiMax"] = high
        if open_ended:
            weight["spiOpen"] = True
    preparation = parse_preparation(stream(byn, *PREPARATION_RANGE))
    failures = parse_failures(stream(byn, *FAILURE_RANGE))
    critical = parse_critical(stream(byn, *CRITICAL_RANGE))
    verify(weights, failures, critical, preparation)

    write("ritual_weights", weights, [list(WEIGHT_RANGE), list(WEIGHT_COST_RANGE)])
    write("ritual_preparation", preparation, [list(PREPARATION_RANGE)])
    write("ritual_failures", failures, [list(FAILURE_RANGE)])
    write("ritual_critical", critical, [list(CRITICAL_RANGE)])

    print(f"ritual_weights: {[(w['weight'], w['spi']) for w in weights]}")
    print(f"ritual_preparation: {[(p['quality'], p['printed']) for p in preparation]}")
    print(f"ritual_failures: {len(failures)} rows")
    print(f"ritual_critical: {len(critical)} bands")
    return 0


if __name__ == "__main__":
    sys.exit(main())
