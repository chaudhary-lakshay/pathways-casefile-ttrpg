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
FAILURE_RANGE = (4349, 4359)
CRITICAL_RANGE = (4361, 4384)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADER = {"Weight", "Categories", "Result", "Consequence", "Roll", "Outcome"}

MINUS = "-−"
WEIGHT = re.compile(rf"^(Light|Moderate|Heavy)\s*\(([+{MINUS}]?\d+)\)$")
# "Failure by 1-4", "Failure by 10+", "Critical Failure (roll 17-18)"
FAILURE = re.compile(rf"^Failure by (\d+)(?:[{MINUS}–—](\d+)|(\+))$")
CRITICAL_LABEL = re.compile(r"^Critical Failure \(roll [\d–—-]+\)$")
BAND = re.compile(rf"^(\d+)(?:[{MINUS}–—](\d+))?$")

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
    return [without_text(r, "outcome") for r in rows]


def verify(weights, failures, critical):
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


def write(name, entries, spans):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: spans}, "entries": entries}
    (OUT / f"{name}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def main():
    byn = load_lines()
    weights = parse_weights(stream(byn, *WEIGHT_RANGE))
    failures = parse_failures(stream(byn, *FAILURE_RANGE))
    critical = parse_critical(stream(byn, *CRITICAL_RANGE))
    verify(weights, failures, critical)

    write("ritual_weights", weights, [list(WEIGHT_RANGE)])
    write("ritual_failures", failures, [list(FAILURE_RANGE)])
    write("ritual_critical", critical, [list(CRITICAL_RANGE)])

    print(f"ritual_weights: {[w['weight'] for w in weights]}")
    print(f"ritual_failures: {len(failures)} rows")
    print(f"ritual_critical: {len(critical)} bands")
    return 0


if __name__ == "__main__":
    sys.exit(main())
