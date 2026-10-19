"""Extract Ch. 2's Primary Attributes costs and Basic Lift / encumbrance (p. 9-10).

PRIMARY ATTRIBUTES (353-366)
-----------------------------
    ST, DX, IQ, and HT begin at 9. Raising them costs a flat rate per level:
    ST 10 pts, DX 20 pts, IQ 20 pts, HT 20 pts.

v6.9d doubled each level and priced HT like ST. v6.9i is FLAT and prices HT
like DX and IQ. Ch. 11's worked example agrees ("A Sailor buys ST 10 (10
pts) ... They buy ST 11 (20 pts)"), and verify() pins it, because a
cumulative 20 for two ST levels is what flat-10 means and what doubling does
not. AttributeCosts reads `attribute_costs.json`.

BASIC LIFT AND ENCUMBRANCE (400-428)
-------------------------------------
"Basic Lift (BL) = (ST x ST) / 5 lbs." Six load levels, each a multiple of
BL with a Move and Dodge penalty; Overload also "cannot run". The formula is
a sentence and lives in Kotlin (`Encumbrance.basicLift`); the table is data.
Lifting ST and the push/lift ST roll are pinned prose.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "2"
ATTRIBUTE_RANGE = (353, 366)
LIFT_RANGE = (400, 428)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
ATTRIBUTE_ROW = re.compile(r"^(ST|DX|IQ|HT) \(\w+\)$")
COST_CELL = re.compile(r"^(\d+) pts$")
FLAT = "Raising them costs a flat rate per level"
BEGIN_AT = "ST, DX, IQ, and HT begin at 9"
EXPECTED_COSTS = {"ST": 10, "DX": 20, "IQ": 20, "HT": 20}

LEVEL_ROW = re.compile(r"^(None|Light|Medium|Heavy|Very Heavy|Overload) \((\d)\)$")
LOAD_CELL = re.compile(r"^(?:BL or less|Up to (\d+) × BL)$")
PENALTY_CELL = re.compile(r"^-(\d+) Move, -(\d+) Dodge(; cannot run)?$")
BASIC_LIFT = "Basic Lift (BL) = (ST × ST) / 5 lbs."
LIFTING_ST = "Lifting ST only affects lifting/carrying"
PUSH_ROLL = "You can push or lift objects heavier than your load limit with a ST roll."


def load_lines():
    return {row["n"]: row["text"] for row in json.loads(LINES_PATH.read_text("utf-8"))}


def stream(byn, first, last):
    out = []
    for n in range(first, last + 1):
        text = (byn.get(n) or "").strip()
        if text and not FURNITURE.match(text):
            out.append(text)
    return out


def attributes(cells):
    rows = []
    for i, cell in enumerate(cells):
        name = ATTRIBUTE_ROW.match(cell)
        if name:
            cost = COST_CELL.match(cells[i + 1])
            if not cost:
                raise ValueError(f"{cell!r} prints no cost: {cells[i + 1]!r}")
            rows.append({"attr": name.group(1), "costPerLevel": int(cost.group(1))})
    return rows


def encumbrance(cells):
    rows = []
    for i, cell in enumerate(cells):
        level = LEVEL_ROW.match(cell)
        if not level:
            continue
        load, penalty = LOAD_CELL.match(cells[i + 1]), cells[i + 2]
        if not load:
            raise ValueError(f"{cell!r} prints no load: {cells[i + 1]!r}")
        row = {"level": int(level.group(2)), "name": level.group(1),
               "maxLoadBl": int(load.group(1)) if load.group(1) else 1,
               "movePenalty": 0, "dodgePenalty": 0}
        hit = PENALTY_CELL.match(penalty)
        if hit:
            row["movePenalty"], row["dodgePenalty"] = -int(hit.group(1)), -int(hit.group(2))
            if hit.group(3):
                row["cannotRun"] = True
        elif penalty != "No penalty":
            raise ValueError(f"{cell!r}: unreadable penalty {penalty!r}")
        rows.append(row)
    return rows


def verify(costs, levels, attribute_text, lift_text):
    if {r["attr"]: r["costPerLevel"] for r in costs} != EXPECTED_COSTS:
        raise ValueError(f"attribute costs changed: {costs}")
    for phrase in (FLAT, BEGIN_AT):
        if phrase not in attribute_text:
            raise ValueError(f"the attribute paragraph no longer says {phrase!r}")
    if [r["level"] for r in levels] != [0, 1, 2, 3, 4, 5]:
        raise ValueError(f"encumbrance levels changed: {levels}")
    if [r["maxLoadBl"] for r in levels] != [1, 2, 3, 6, 10, 15]:
        raise ValueError(f"load multiples changed: {levels}")
    if [r["movePenalty"] for r in levels] != [0, -1, -2, -3, -4, -5]:
        raise ValueError(f"Move penalties changed: {levels}")
    if [r["dodgePenalty"] for r in levels] != [r["movePenalty"] for r in levels]:
        raise ValueError("Move and Dodge penalties used to be equal at every level")
    if [r.get("cannotRun", False) for r in levels] != [False] * 5 + [True]:
        raise ValueError("only Overload used to forbid running")
    for phrase in (BASIC_LIFT, LIFTING_ST, PUSH_ROLL):
        if phrase not in lift_text:
            raise ValueError(f"the Basic Lift section no longer says {phrase!r}")
    # Ch. 11's flat-rate example: ST 11 costs 20 cumulative.
    pathways_intro = " ".join(stream(load_lines(), 4753, 4754))
    if "ST 11 (20 pts)" not in pathways_intro:
        raise ValueError("ch. 11 no longer prices ST 11 at 20 pts; re-read the flat rate")


def write(name, spans, entries):
    (OUT / name).write_text(
        json.dumps({"sourceLines": {CHAPTER: spans}, "entries": entries},
                   indent=1, ensure_ascii=False) + "\n", "utf-8")


def main():
    byn = load_lines()
    attribute_cells, lift_cells = stream(byn, *ATTRIBUTE_RANGE), stream(byn, *LIFT_RANGE)
    costs, levels = attributes(attribute_cells), encumbrance(lift_cells)
    verify(costs, levels, " ".join(attribute_cells), " ".join(lift_cells))
    write("attribute_costs.json", [list(ATTRIBUTE_RANGE)], costs)
    write("encumbrance.json", [list(LIFT_RANGE)], levels)
    print(f"attribute_costs: {costs}")
    print(f"encumbrance: {[(r['name'], r['maxLoadBl'], r['movePenalty']) for r in levels]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
