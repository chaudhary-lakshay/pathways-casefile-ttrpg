"""Extract the ritual resolution tables (Ch. 9, p. 60-62) into committed JSON.

v6.9i rebuilt this chapter. What it prints now, and where each file reads it:

WEIGHTS. Two printings of one ladder. Sec. II's Effect Categories table
(3897-3917) gives Light / Moderate / Heavy with their example categories;
Sec. III.A's Quick table (3925-3945) adds a fourth weight, Major (-6, 3 hr,
12+ SPI), and the base time. Major is printed in Sec. III.A ONLY, so it ships
with no `categories` rather than borrowed ones -- the book names none.
verify() pins that the three shared rows agree on modifier and SPI.

OUTCOMES. Sec. III.A's outcome table (3946-3957) is the only place the book
grades a ritual that WORKED, and Phase III sends the Full ritual to it too
("Resolve using the Quick Resolution outcome table"). Only its two success
rows are written. Its failure rows are Sec. V's, coarsened: "Failure by 5+"
is the union of Sec. V's 5-9 and 10+. verify_outcomes() checks every band it
prints is a band Sec. V starts, so the day they become two tables this fails
instead of the app picking one silently. This reading predates v6.9i (6bb)
and the page still supports it.

FAILURES (4028-4039) and the CRITICAL FAILURE TABLE (4040-4061). The critical
table's outcomes are NOT ordered by severity: 13-14 (1 CoR) sits above 11-12
(Wrong Arrival), and the book shifts the roll by -2 for a severe misfire.

PREPARATION. Phase I (3963-3964) is prose now: "Excellent +3, Good +1 to +2,
Adequate +0, Poor -1 to -2, None -4 or worse." The descriptive column v6.9d
printed is gone, so the rows carry no `looksLike`.

FLOURISH (3965-3967). "GM may award +1 Flourish Bonus for good roleplay.
Effective skill = Ritualistic Magic + Preparation Mod + Flourish Bonus." The +1
lives in Kotlin (`RitualMode.FLOURISH_BONUS`); this file pins the sentence.
It is FULL-only: Sec. III.A never names it.

REVELATION PENALTIES (4008-4025), new in v6.9i. Two entity types, a roll
penalty each, and a reduction each printed as prose with one number in it.
The Deities row reduces "if the caster possesses the Blessed by Deity
advantage" -- an advantage chapter 3 does not print (its blessings are the
Seven Orthodox Gods table, 75 pts each). Recorded, not resolved:
BLESSED_BY_DEITY pins that the name is absent from advantages.json.

SPIRIT WORLD CREATURE CONTRACT (4065-4129), new in v6.9i. Sec. VI's
contract ritual: "The base penalty equals your Sequence number" and "For each
creature you already have under contract, roll an extra 1d6 and subtract the
result" -- both sentences pinned here, the arithmetic in Kotlin. 3 SPI, 30
minutes, at most 4 contracts (8 for Scrolls Professor). The margin table
(4103-4123) grades a contract that WORKED and the demands it costs; "Success
by 0 (exact)" is its own row. The Sequence 9 reference table (4087-4102) is a
worked example of the same sentences and is claimed, not extracted.

INCANTATIONS. v6.9i keeps two structures:
  * the four-part Hermes one, named in Phase II (3965) by its parts only --
    "Invocation, Grace, Request, Empowerment" -- with no worked text;
  * the summoning incantation of Sec. VI (4130-4138), three parts.
The three-line self-description v6.9d printed for Praying to Oneself is gone,
so no structure has that scope any more.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "9"
CATEGORY_RANGE = (3897, 3917)
QUICK_RANGE = (3923, 3961)
QUICK_TABLE_RANGE = (3925, 3945)
OUTCOME_RANGE = (3946, 3957)
FULL_RANGE = (3962, 3969)
REVELATION_RANGE = (4008, 4025)
FAILURE_RANGE = (4026, 4039)
CRITICAL_RANGE = (4040, 4061)
SUMMONING_RANGE = (4130, 4138)
CONTRACT_RANGE = (4065, 4129)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADER = {
    "Weight", "Base Mod", "SPI Cost", "Example Categories", "Effect Weight",
    "Base", "Difficulty", "Base Time", "Result", "Outcome", "Consequence",
    "Roll", "Entity Type", "Roll Penalty", "Reduction",
    "Critical Failure Table (3d6)",
}
MINUS = "-−"
WEIGHTS = ["Light", "Moderate", "Heavy", "Major"]
SIGNED = re.compile(rf"^[+{MINUS}]\d+$")
SPI_COST = re.compile(r"^(\d+)(?:\s*[-−–—]\s*(\d+)|\s*(\+))?\s*SPI$")

PREPARATION = re.compile(
    rf"(Excellent|Good|Adequate|Poor|None)\s+([+{MINUS}]\d+)"
    rf"(?:\s+to\s+([+{MINUS}]\d+))?(\s+or worse)?"
)
PREPARATIONS = ["Excellent", "Good", "Adequate", "Poor", "None"]

FLOURISH = re.compile(rf"([+{MINUS}]\d+) Flourish Bonus")
FLOURISH_FORMULA = "Effective skill = Ritualistic Magic + Preparation Mod + Flourish Bonus"
INVOCATION_BONUS = "Invocation bonus (GM discretion, +1 to +5)"
FULL_DEFERS = "Resolve using the Quick Resolution outcome table"
HERMES = re.compile(r"4-part Hermes structure: ([^)]+)\)")

FAILURE = re.compile(rf"^Failure by (\d+)(?:[{MINUS}–—](\d+)|(\+))$")
SUCCESS = re.compile(rf"^Success by (\d+)(?:[{MINUS}–—](\d+)|(\+))$")
QUICK_CRITICAL = re.compile(rf"^Critical Failure \([{MINUS}–—\d]+\)$")
CRITICAL_LABEL = "Critical Failure"
BAND = re.compile(rf"^(\d+)(?:[{MINUS}–—](\d+))?$")
CRITICAL_COR = re.compile(r"\bGain (\d+) CoR\b", re.IGNORECASE)
EXPECTED_CRITICAL_BANDS = [(3, 4), (5, 6), (7, 8), (9, 10), (11, 12),
                           (13, 14), (15, 16), (17, 17), (18, 18)]

ENTITIES = ["Seven Lights of the Divine Throne", "Deities (Orthodox or Hidden)"]
PER_SACRIFICE = re.compile(rf"^([{MINUS}]\d+) per sacrifice")
REDUCIBLE_TO = re.compile(rf"^Reducible to ([{MINUS}]\d+)")
BLESSED_BY_DEITY = "Blessed by Deity"

CONTRACT_BASE = "The base penalty equals your Sequence number"
CONTRACT_PER_HELD = ("For each creature you already have under contract, "
                     "roll an extra 1d6 and subtract the result")
CONTRACT_TERMS = re.compile(
    r"SPI Cost: (\d+) SPI\. Time: ([^.]+)\. Setup: (.+?) Maximum contracts: "
    r"(\d+) \((\d+) for Scrolls Professor\)\.")
CONTRACT_EXACT = "Success by 0 (exact)"
CONTRACT_LABELS = ("Failure", "Critical Failure", CONTRACT_EXACT)
METHOD = re.compile(r"^\x7f?\s*(Method \d) — ([^:]+): (.+)$")

SUMMONING_PART = re.compile(r"^\x7f?\s*Part (\d):")
BULLET = "\x7f"


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


def signed(text):
    return int(text.replace("−", "-"))


def spi_fields(cell):
    match = SPI_COST.match(cell.replace("−", "-"))
    if not match:
        raise ValueError(f"not an SPI cost: {cell!r}")
    low, high, plus = match.groups()
    out = {"spi": int(low)}
    if high:
        out["spiMax"] = int(high)
    if plus:
        out["spiOpen"] = True
    return out


def parse_categories(lines):
    """Sec. II: Weight / Base Mod / SPI Cost / Example Categories (wraps)."""
    starts = [i for i, l in enumerate(lines) if l in WEIGHTS]
    rows = {}
    for pos, i in enumerate(starts):
        end = starts[pos + 1] if pos + 1 < len(starts) else len(lines)
        rows[lines[i]] = {
            "modifier": signed(lines[i + 1]),
            **spi_fields(lines[i + 2]),
            "categories": " ".join(lines[i + 3:end]),
        }
    return rows


def parse_quick_table(lines):
    """Sec. III.A: Effect Weight / Base Difficulty / Base Time / SPI Cost."""
    rows = []
    for i, line in enumerate(lines):
        if line not in WEIGHTS:
            continue
        mod, time, cost = lines[i + 1:i + 4]
        if not SIGNED.match(mod):
            raise ValueError(f"{line!r} prints no base difficulty: {mod!r}")
        rows.append({"weight": line, "modifier": signed(mod), "baseTime": time,
                     **spi_fields(cost)})
    return rows


def build_weights(quick, categories):
    out = []
    for row in quick:
        listed = categories.get(row["weight"])
        if listed is not None:
            for key in ("modifier", "spi", "spiMax", "spiOpen"):
                if listed.get(key) != row.get(key):
                    raise ValueError(
                        f"{row['weight']}: Sec. II prints {key}={listed.get(key)!r} "
                        f"and Sec. III.A {row.get(key)!r}; the two printings disagree"
                    )
            row = {**row, "categories": listed["categories"]}
        out.append(row)
    return out


def collect(lines, is_label, start_row):
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


def margin_row(match, line):
    low, high, plus = match.groups()
    return {"printed": line, "from": int(low), "to": None if plus else int(high)}


def parse_outcomes(lines):
    """Sec. III.A's outcome table: (success rows, failure band starts)."""
    def is_label(line):
        return bool(SUCCESS.match(line) or FAILURE.match(line) or QUICK_CRITICAL.match(line))

    def start(line):
        success = SUCCESS.match(line)
        if success:
            return margin_row(success, line) | {"graded": True}
        return {"printed": line, "graded": False}

    rows = collect(lines, is_label, start)
    success = [without_text({k: v for k, v in r.items() if k != "graded"}, "outcome")
               for r in rows if r["graded"]]
    starts = [int(FAILURE.match(r["printed"]).group(1))
              for r in rows if FAILURE.match(r["printed"])]
    return success, starts


def parse_failures(lines):
    def start(line):
        if line == CRITICAL_LABEL:
            # Entered by the ROLL, not a margin, so it carries no from/to.
            return {"printed": line, "critical": True}
        return margin_row(FAILURE.match(line), line) | {"critical": False}

    rows = collect(lines, lambda l: bool(FAILURE.match(l)) or l == CRITICAL_LABEL, start)
    return [without_text(r, "consequence") for r in rows]


def parse_critical(lines):
    def start(line):
        low, high = BAND.match(line).groups()
        return {"low": int(low), "high": int(high) if high else int(low), "printed": line}

    out = []
    for row in collect(lines, lambda l: BAND.match(l) is not None, start):
        entry = without_text(row, "outcome")
        charge = CRITICAL_COR.search(entry["outcome"])
        if charge:
            entry["corGained"] = int(charge.group(1))
        out.append(entry)
    return out


def parse_preparation(full):
    rows = []
    for quality, value, best, worse in PREPARATION.findall(full):
        entry = {"quality": quality, "modifier": signed(value),
                 "printed": value + (f" to {best}" if best else "") + worse}
        if best:
            entry["best"] = signed(best)
        if worse:
            entry["openEnded"] = True
        rows.append(entry)
    return rows


def parse_revelation(lines):
    starts = [i for i, l in enumerate(lines) if l in ENTITIES]
    rows = []
    for pos, i in enumerate(starts):
        end = starts[pos + 1] if pos + 1 < len(starts) else len(lines)
        body = lines[i + 2:end]
        tip = next((k for k, l in enumerate(body) if l.startswith("Tip:")), len(body))
        reduction = " ".join(body[:tip])
        row = {"entity": lines[i], "penalty": signed(lines[i + 1]), "reduction": reduction}
        per = PER_SACRIFICE.match(reduction)
        floor = REDUCIBLE_TO.match(reduction)
        if per:
            row["perSacrifice"] = -signed(per.group(1))
        if floor:
            row["reducibleTo"] = signed(floor.group(1))
        rows.append(row)
    return rows


def parse_contract(lines):
    text = " ".join(lines)
    for sentence in (CONTRACT_BASE, CONTRACT_PER_HELD):
        if sentence not in text:
            raise ValueError(f"Sec. VI no longer prints {sentence!r}")
    terms = CONTRACT_TERMS.search(text)
    if not terms:
        raise ValueError("Sec. VI's contract terms changed shape")
    spi, time, setup, most, scrolls = terms.groups()

    start = lines.index("Obtaining a Contracted Creature")
    end = lines.index("Spirit World Creature Contract")
    methods = collect(lines[start + 2:end], lambda l: bool(METHOD.match(l)),
                      lambda l: {"line": l})
    method_rows = []
    for row in methods:
        number, name, body = METHOD.match(row["line"]).groups()
        tail = row.get("text", "")
        method_rows.append({"method": number, "name": name,
                            "text": f"{body} {tail}".strip()})

    first = lines.index("Degree of Success determines the creature's demands:")
    last = next(i for i, l in enumerate(lines) if l.startswith("What a creature demands"))

    def is_label(line):
        return bool(SUCCESS.match(line)) or line in CONTRACT_LABELS

    def start_row(line):
        success = SUCCESS.match(line)
        if success:
            return margin_row(success, line)
        if line == CONTRACT_EXACT:
            return {"printed": line, "from": 0, "to": 0}
        return {"printed": line, "failure": True, "critical": line == "Critical Failure"}

    table = [l.lstrip(BULLET).strip() for l in lines[first + 1:last] if l != "Margin"]
    outcomes = [without_text(r, "outcome") for r in collect(table, is_label, start_row)]
    return {
        "difficulty": text[text.index("Difficulty: "):text.index(" SPI Cost:")],
        "spi": int(spi), "time": time, "setup": setup.strip(),
        "maxContracts": int(most), "maxContractsScrollsProfessor": int(scrolls),
        "methods": method_rows,
        "outcomes": outcomes,
        "demands": " ".join(lines[last:]),
    }


def verify_contract(contract):
    bands = [(o.get("from"), o.get("to")) for o in contract["outcomes"]]
    if bands != [(10, None), (7, 9), (5, 6), (3, 4), (1, 2), (0, 0), (None, None), (None, None)]:
        raise ValueError(f"contract margin bands changed: {bands}")
    if [o["printed"] for o in contract["outcomes"][-2:]] != ["Failure", "Critical Failure"]:
        raise ValueError("contract failure rows changed")
    if (contract["spi"], contract["maxContracts"], contract["maxContractsScrollsProfessor"]) \
            != (3, 4, 8):
        raise ValueError(f"contract terms changed: {contract}")
    if [m["method"] for m in contract["methods"]] != ["Method 1", "Method 2"]:
        raise ValueError(f"contract methods changed: {contract['methods']}")


def build_incantations(full, summoning):
    hermes = HERMES.search(full)
    if not hermes:
        raise ValueError("Phase II no longer names the 4-part Hermes structure")
    parts, current = [], None
    note = []
    for line in summoning[1:]:
        if SUMMONING_PART.match(line):
            if current is not None:
                parts.append(current)
            current = line.lstrip(BULLET).strip()
        elif current is not None and not line.startswith("The summoner cannot"):
            if note:
                note.append(line)
            else:
                current = f"{current} {line}"
        else:
            note.append(line)
    if current is not None:
        parts.append(current)
    return [
        {
            "structure": "Four-part Hermes",
            "scope": "Invocation",
            "text": hermes.group(0).rstrip(")"),
            "parts": [p.strip() for p in hermes.group(1).split(",")],
        },
        {
            "structure": "Three-part summoning incantation",
            "scope": "Summoning a spiritual creature",
            "text": summoning[0],
            "parts": parts,
            "note": " ".join(note),
            "referenceOnly": True,
        },
    ]


def verify(weights, failures, critical, preparation, revelation, incantations):
    if [w["weight"] for w in weights] != WEIGHTS:
        raise ValueError(f"weights changed: {[w['weight'] for w in weights]}")
    if [w["modifier"] for w in weights] != [0, -2, -4, -6]:
        raise ValueError(f"weight modifiers changed: {[w['modifier'] for w in weights]}")
    if [w.get("spi") for w in weights] != [2, 4, 8, 12]:
        raise ValueError(f"ritual SPI costs changed: {[w.get('spi') for w in weights]}")
    if [w["weight"] for w in weights if "categories" not in w] != ["Major"]:
        raise ValueError("Major used to be the only weight Sec. II does not list")

    margins = [(f.get("from"), f.get("to")) for f in failures if not f["critical"]]
    if margins != [(1, 4), (5, 9), (10, None)]:
        raise ValueError(f"failure bands changed: {margins}")
    if sum(f["critical"] for f in failures) != 1:
        raise ValueError("expected exactly one critical-failure row")

    bands = [(c["low"], c["high"]) for c in critical]
    if bands != EXPECTED_CRITICAL_BANDS:
        raise ValueError(f"critical bands changed: {bands}")
    charges = {c["printed"]: c.get("corGained") for c in critical if c.get("corGained")}
    if charges != {"13-14": 1, "15-16": 1, "17": 2}:
        raise ValueError(f"CoR charges changed: {charges}")

    if [p["quality"] for p in preparation] != PREPARATIONS:
        raise ValueError(f"preparation qualities changed: {preparation}")
    if [p["modifier"] for p in preparation] != [3, 1, 0, -1, -4]:
        raise ValueError(f"preparation modifiers changed: {preparation}")
    if not preparation[-1].get("openEnded"):
        raise ValueError("'None' used to print '-4 or worse'; the floor is gone")

    if [r["entity"] for r in revelation] != ENTITIES:
        raise ValueError(f"revelation entities changed: {revelation}")
    if [(r["penalty"], r.get("perSacrifice"), r.get("reducibleTo")) for r in revelation] \
            != [(-20, 5, None), (-40, None, -10)]:
        raise ValueError(f"revelation numbers changed: {revelation}")
    advantages = json.loads((OUT / "advantages.json").read_text(encoding="utf-8"))["entries"]
    if any(a["name"] == BLESSED_BY_DEITY for a in advantages):
        raise ValueError(f"{BLESSED_BY_DEITY!r} is now an advantage; the recorded gap closed")
    if BLESSED_BY_DEITY not in revelation[1]["reduction"]:
        raise ValueError("the Deities row no longer names Blessed by Deity")

    if incantations[0]["parts"] != ["Invocation", "Grace", "Request", "Empowerment"]:
        raise ValueError(f"the Hermes parts changed: {incantations[0]['parts']}")
    if [p[:6] for p in incantations[1]["parts"]] != ["Part 1", "Part 2", "Part 3"]:
        raise ValueError(f"the summoning parts changed: {incantations[1]['parts']}")


def verify_full(full, quick, success, failure_starts, failures):
    if FLOURISH_FORMULA not in full:
        raise ValueError(f"Phase II no longer prints {FLOURISH_FORMULA!r}")
    bonus = FLOURISH.search(full)
    if not bonus or signed(bonus.group(1)) != 1:
        raise ValueError("the Flourish Bonus is no longer +1")
    if "Flourish" in quick:
        raise ValueError("the Quick ritual now names the Flourish Bonus too")
    if INVOCATION_BONUS not in full:
        raise ValueError(f"Phase II no longer prints {INVOCATION_BONUS!r}")
    if FULL_DEFERS not in full:
        raise ValueError("Phase III no longer sends the Full ritual to the Quick outcome table")
    if [(r["from"], r["to"]) for r in success] != [(3, None), (0, 2)]:
        raise ValueError(f"the ritual success bands changed: {success}")
    theirs = {f["from"] for f in failures if not f["critical"]}
    if not set(failure_starts) <= theirs:
        raise ValueError(
            f"Sec. III.A's failure bands start at {failure_starts} and Sec. V's at "
            f"{sorted(theirs)}: no longer a coarsening, so the app must choose per mode"
        )
    if "spend additional SPI (1 SPI = +1, no cap)" not in quick or "Before rolling" not in quick:
        raise ValueError("the ritual burn is no longer declared before rolling")


def write(name, entries, spans):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: spans}, "entries": entries}
    (OUT / f"{name}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def main():
    byn = load_lines()
    weights = build_weights(parse_quick_table(stream(byn, *QUICK_TABLE_RANGE)),
                            parse_categories(stream(byn, *CATEGORY_RANGE)))
    full = " ".join(stream(byn, *FULL_RANGE))
    quick = " ".join(stream(byn, *QUICK_RANGE))
    success, failure_starts = parse_outcomes(stream(byn, *OUTCOME_RANGE))
    failures = parse_failures(stream(byn, *FAILURE_RANGE)[2:])
    critical = parse_critical(stream(byn, *CRITICAL_RANGE))
    preparation = parse_preparation(full)
    revelation = parse_revelation(stream(byn, *REVELATION_RANGE)[1:])
    incantations = build_incantations(full, stream(byn, *SUMMONING_RANGE)[1:])
    verify(weights, failures, critical, preparation, revelation, incantations)
    verify_full(full, quick, success, failure_starts, failures)
    contract = parse_contract(stream(byn, *CONTRACT_RANGE))
    verify_contract(contract)

    write("ritual_weights", weights, [list(CATEGORY_RANGE), list(QUICK_TABLE_RANGE)])
    write("ritual_success", success, [list(OUTCOME_RANGE)])
    write("ritual_preparation", preparation, [list(FULL_RANGE)])
    write("ritual_failures", failures, [list(FAILURE_RANGE)])
    write("ritual_critical", critical, [list(CRITICAL_RANGE)])
    write("ritual_revelation", revelation, [list(REVELATION_RANGE)])
    write("incantations", incantations, [list(SUMMONING_RANGE)])
    write("ritual_contract", [contract], [list(CONTRACT_RANGE)])

    print(f"ritual_weights: {[(w['weight'], w['modifier'], w['spi']) for w in weights]}")
    print(f"ritual_preparation: {[(p['quality'], p['printed']) for p in preparation]}")
    print(f"ritual_failures: {len(failures)} rows; ritual_critical: {len(critical)} bands")
    print(f"ritual_revelation: {[(r['entity'], r['penalty']) for r in revelation]}")
    print(f"incantations: {[i['scope'] for i in incantations]}")
    print(f"ritual_contract: {[o['printed'] for o in contract['outcomes']]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
