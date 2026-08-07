"""Extract Ch. 6.5's Sec. 1-4 -- the TARGET's half of anti-divination.

Five files, one section (global 3755-3937):

  divination_awareness.json      who notices without rolling (Sec. 1)
  detection_outcomes.json        what noticing tells you (Sec. 2)
  detection_modifiers.json       what makes noticing easier (Sec. 2)
  anti_divination_techniques.json what the target spends SPI to do (Sec. 3)
  anti_divination_items.json     what the target can buy instead (Sec. 4)

extract_anti_divination.py took Sec. 5-6, the DIVINER's half, and left this
deliberately. The seam between them is `AntiDivination.against`'s
`techniquePenalty` parameter, which has been a scalar with no caller since
that slice; Sec. 3 is what fills it.

WHOSE ROLL IS IT
-----------------
Sec. 2 is a roll made by the TARGET, and this app holds exactly one character.
In the divination dialog that character is the DIVINER: the target is a
declared Sequence with no sheet, so there is no SPI or Per to roll and no
amount of UI would produce one. So the detection roll does not live there. It
lives where the played character IS the target, as its own entry point, and
what goes into the divination dialog is Sec. 3 as a DECLARATION about the
target -- the penalty the diviner is up against.

The same GM fact reaches both rolls with opposite sign, and that is not a
disagreement to reconcile: "the diviner has your true name + personal item"
is Sec. 6's Direct Link, which halves the diviner's penalty, and also a -2 on
the target's detection roll. Two rolls, two effects, one fact. verify() pins
both so a later reading cannot collapse them.

No gate on who may roll. Sec. 2 scopes itself to "a Beyonder with passive
awareness ... or when any Beyonder suspects", but the roll it defines is
SPI or Per whichever is higher, and a mortal has a Per. Who suspects is the
GM's call, the same reading that refused to enforce the Sealed Artifact grade
table's Power Equivalent column as a wielder requirement (CLAUDE.md 6af).

TWO AXES, NOT ONE TABLE
------------------------
Sec. 1's +2s and Sec. 2's situation modifiers move the same roll, and they are
still two tables. Sec. 1's primary output is not a number at all -- it is a
GATE: automatic awareness, a free roll, or nothing. Three of its eight rows
print a bonus and one prints no roll at all. Sec. 2's six rows are the
situation, unconditional, and apply to whoever is rolling.

Two more sources feed the same roll: Sec. 3A's ward grants "+2 on detection
rolls" and Sec. 4's Concealment Pendant "+2 to detect divination". So the
detection roll has four declared sources exactly as a skill roll has four --
`Conditions`, `Attacks`, `Ranged`, `CommonModifiers` -- and the reason is the
same one every time: the scope travels with the number, so the tables stay
apart and the caller composes them.

That immediately produces one overlap. Sec. 2 prints "Target carries a
protective charm or item / +1" and Sec. 4 prints a charm worth +2. One item,
priced twice, generally and specifically -- the shape Appendix A's "adverse
conditions" and chapter 5's darkness row already had (6ae). Specific beats
general: naming the pendant excludes the generic row, and verify() pins that
the overlap is still really there.

The candle does NOT join them. Its -3 is "suppresses detection of that ritual
by -3 to anyone sensing it" -- sensing a ritual being performed, which is not
the being-divined roll Sec. 2 defines. It is stored under its own key so it
cannot be summed into a detection total by a caller reaching for the nearest
number.

STACKING AND THE TWO HALVINGS
------------------------------
Sec. 3 says "All active techniques stack -- their penalties add together when
a divination is attempted." Sec. 6 then halves, twice, in opposite printed
directions (6ag). Sum first, then halve: the book's sentence makes the
techniques ONE penalty by the time a divination is attempted, which is the
moment Sec. 6 acts on. That is also what `AntiDivination.against` already
implements, so no signature moves.

The printed data cannot tell the two orders apart. Only 3A (-2) and 3B (-4)
print a flat penalty and both are EVEN, so summing then halving and halving
then summing agree on every combination this book can produce -- the Major
Wound trap (6n) again, with the parities the other way round. verify()
asserts the evenness, so the first odd technique forces a re-read instead of
silently picking a side.

A ROW IN THE WRONG TABLE
-------------------------
Wheel of Fortune (Monster) is in the passive-awareness table and grants no
awareness. It prints "any divination attempt against a Monster may suffer -1"
-- a penalty on the DIVINER, at the GM's discretion, from a passive and
unconscious source. It is recorded and deliberately not applied anywhere:
routing it through `techniquePenalty` would subject it to two halvings that
name "active techniques", which it is not, and offering it as a chip would put
a number the GM must first grant beside penalties the target actually paid SPI
for. Same family as divination's "See Step 1 table" row (6p) and the untrained
row in Common Modifiers (6ae): a row inside a table that is not what the table
is about.

DURATIONS ARE NOT TRACKED
--------------------------
Every technique prints one -- 1 hour, 1d6 minutes, 24 hours -- and nothing in
this app holds a clock (the same reason Appendix A's SPI recovery was left,
6ad). Techniques are declarations: the target spends the SPI through the
tracker and says the ward is up. The duration text is committed so a GM can
read it.

PARSING
--------
Sec. 1's three columns are told apart by INDENTATION: the Pathway cell is the
only one this PDF indents, so a run of indented lines is a pathway and the
"Seq ..." line after it starts the middle cell. The Hermit's middle cell wraps
("Seq 9+ (Eyes of Mystery" / "Prying)") and its continuation is NOT indented,
so it is taken by the unclosed-bracket rule extract_wounds.py needed for the
Vitals multiplier. Sec. 2's outcome table indents the opposite column, and
Sec. 2's modifier table indents the value -- so indentation is an anchor per
table, never across them.

Sec. 4's item names are a closed set of six, the same anchor the divination
methods table needed (6p): its name cells wrap in three different shapes
("(Darkness charm)", "Talisman (Fool" / "pathway)", "Mystical Candle of" /
"Obscurity") and no shape rule separates all three from an effect cell.

This PDF prints list bullets as U+007F (6g).
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "6.5"
QUICK_REFERENCE_RANGE = (3737, 3754)  # pinned, not extracted
AWARENESS_RANGE = (3755, 3807)
OUTCOME_RANGE = (3808, 3830)
MODIFIER_RANGE = (3831, 3844)
TECHNIQUE_RANGE = (3845, 3903)
ITEM_RANGE = (3904, 3937)

BULLET = "\x7f"
FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")

MINUS = "-−"
SIGNED = rf"[+{MINUS}]\d+"

# --- Sec. 1 ----------------------------------------------------------------
AWARENESS_HEADERS = {"Pathway", "Seq & Ability", "Detection"}
SEQ_CELL = re.compile(r"^Seq\b")
AUTOMATIC = re.compile(r"Automatic awareness")
FREE_ROLL = re.compile(r"\b(?:Free|Automatic) (Per|SPI) roll")
BONUS = re.compile(r"\+(\d+)")
DIVINER_PENALTY = re.compile(rf"may suffer ({SIGNED})")
SENTENCE_SPLIT = re.compile(r"(?<=\.)\s+")

EXPECTED_PATHWAYS = [
    "Fool (Seer)",
    "Darkness (Sleepless -> Midnight Poet -> Nightmare)",
    "Hanged Man (Secrets Supplicant)",
    "Hermit (Mystery Pryer)",
    "Justiciar (Arbiter -> Sheriff)",
    "Wheel of Fortune (Monster)",
    "Fool / Error / Door",
    "Any pathway",
]
# The row that grants no awareness and penalises the diviner instead.
CROSS_AXIS_PATHWAY = "Wheel of Fortune (Monster)"
# The universal row: automatic, but only against a weaker diviner.
UNIVERSAL_PATHWAY = "Any pathway"
QUICK_CONTEST = "Quick Contest of SPI"

# --- Sec. 2 ----------------------------------------------------------------
OUTCOME_HEADERS = {"Roll Result", "Outcome"}
OUTCOME_NAMES = [
    "Success", "Success by 3+", "Critical Success", "Failure", "Critical Failure",
]
OUTCOME_KINDS = {
    "Success": "SUCCESS",
    "Success by 3+": "SUCCESS",
    "Critical Success": "CRITICAL_SUCCESS",
    "Failure": "FAILURE",
    "Critical Failure": "CRITICAL_FAILURE",
}
OUTCOME_MARGIN = re.compile(r"^Success by (\d+)\+$")
# The sentence that defines the roll. Two attributes, take the higher.
ROLL_PHRASE = "SPI or Per roll (whichever is higher)"
# ...and the clause that scopes it to Sec. 1's OWN population, which is what
# makes this a disagreement rather than two rules about different people.
PASSIVE_SCOPE_PHRASE = "When a Beyonder with passive awareness is being divined"
# The rows whose own sentence names the attribute for the roll they grant.
# They do NOT all name the same one, and that is the entire warrant for
# letting a row beat ROLL_PHRASE: one attribute throughout would read as
# loose phrasing for Sec. 2's rule, four rows splitting 2-2 does not.
NAMED_ROLL_ROWS = {
    "Fool (Seer)": "PER",
    "Darkness (Sleepless -> Midnight Poet -> Nightmare)": "SPI",
    "Hermit (Mystery Pryer)": "SPI",
    "Fool / Error / Door": "PER",
}

MODIFIER_HEADERS = {"Situation", "Modifier"}
MODIFIER_VALUE = re.compile(rf"^({SIGNED})$")
EXPECTED_SITUATIONS = [
    "Diviner is 2+ Sequences weaker than the target",
    "Diviner is 2+ Sequences stronger than the target",
    "Target carries a protective charm or item",
    "Diviner has the target's true name + personal item",
    "Target is in a warded or sacred space",
    "Diviner and target are of equal Sequence",
]
# One item, priced twice: generally here and specifically in Sec. 4.
GENERIC_CHARM_SITUATION = "Target carries a protective charm or item"
# The same GM fact as Sec. 6's Direct Link, on the other side of the table.
TRUE_NAME_SITUATION = "Diviner has the target's true name + personal item"

# --- Sec. 3 ----------------------------------------------------------------
TECHNIQUE_HEADER = re.compile(r"^(3[A-F])\. (.+?) \((.+)\)$")
TECHNIQUE_BULLET = re.compile(
    r"^(Cost|Duration|Effect|Bonus|Limitation|Counter|Extension|Deception"
    r"|Penalty|Detection): (.*)$"
)
TECHNIQUE_SPI = re.compile(r"^(\d+) SPI$")
EFFECT_PENALTY = re.compile(rf"divination[^.]*?\bat ({SIGNED})")
DETECTION_BONUS = re.compile(r"\+(\d+) on detection rolls")
STACK_PHRASE = "their penalties add together"
EXPECTED_TECHNIQUES = ["3A", "3B", "3C", "3D", "3E", "3F"]
# The only two rows that price a divination attempt. Both even -- see the
# module docstring on why that is the whole warrant for summing before halving.
PENALTY_TECHNIQUES = {"3A": -2, "3B": -4}

# --- Sec. 4 ----------------------------------------------------------------
ITEM_HEADERS = {"Item", "Effect", "Cost"}
EXPECTED_ITEMS = [
    "Concealment Pendant (Darkness charm)",
    "Eyes of the Spirit World (Hermit charm)",
    "Blank Paper Talisman (Fool pathway)",
    "Mystical Candle of Obscurity",
    "Silver Mirror of Reflection",
    "Salt of Spiritual Clarity",
]
ITEM_COST = re.compile(r"^£")
ITEM_DETECTION_BONUS = re.compile(r"^\+(\d+) to detect divination")
ITEM_RITUAL_PENALTY = re.compile(rf"detection of that ritual by ({SIGNED})")
PENDANT = "Concealment Pendant (Darkness charm)"
CANDLE = "Mystical Candle of Obscurity"
# The candle's number is about sensing a RITUAL, not about being divined.
CANDLE_SCOPE = "detection of that ritual"

# The Quick Reference this section opens with. Not extracted -- it names no
# number of its own -- but it is the book's own statement that Sec. 1 gates,
# Sec. 2 rolls, Sec. 3 penalises the diviner and Sec. 6 answers back, which is
# the evidence these are separate axes rather than one table.
RESOLUTION_STEPS = [
    "Step 1 — Sequence Immunity",
    "Step 2 — Passive Awareness",
    "Step 3 — Detection Roll",
    "Step 4 — Active Countermeasures",
    "Step 5 — Counter-Countermeasures",
    "Step 6 — Resolution",
]


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def raw(byn, start, end):
    """(is_indented, text) per line -- three tables anchor on the indent."""
    out = []
    for n in range(start, end + 1):
        text = byn.get(n)
        if text is None:
            continue
        indented = text.startswith(" ")
        stripped = text.replace(BULLET, " ").strip()
        if not stripped or FURNITURE.match(stripped):
            continue
        out.append((indented, stripped))
    return out


def signed(text):
    return int(text[1:]) * (-1 if text[0] in MINUS else 1)


def normalise(text):
    return re.sub(r"\s+", " ", text).strip()


# ---------------------------------------------------------------------------
# Sec. 1 -- Passive Awareness
# ---------------------------------------------------------------------------
def parse_awareness(lines):
    """Pathway cell (indented) / Seq cell (starts "Seq") / Detection cell.

    The pathway column is the only one this PDF indents, so a run of indented
    lines opens a row. The middle cell can wrap without indentation, so it is
    closed by its own brackets rather than by counting lines.
    """
    cells = [(ind, t) for ind, t in lines if t not in AWARENESS_HEADERS]
    rows, i = [], 0
    # The section header, its intro paragraph and the Seq 4+ note come first.
    while i < len(cells) and not cells[i][0]:
        i += 1
    if i == len(cells):
        raise ValueError("no Passive Awareness rows found")
    while i < len(cells):
        pathway_parts = []
        while i < len(cells) and cells[i][0]:
            pathway_parts.append(cells[i][1])
            i += 1
        pathway = normalise(" ".join(pathway_parts))
        if i == len(cells) or not SEQ_CELL.match(cells[i][1]):
            raise ValueError(f"{pathway!r} is not followed by a Seq cell")
        seq_parts = [cells[i][1]]
        i += 1
        # "Seq 9+ (Eyes of Mystery" / "Prying)" is one cell.
        while " ".join(seq_parts).count("(") != " ".join(seq_parts).count(")"):
            if i == len(cells):
                raise ValueError(f"{pathway!r}'s Seq cell never closes its brackets")
            seq_parts.append(cells[i][1])
            i += 1
        detection = []
        while i < len(cells) and not cells[i][0]:
            detection.append(cells[i][1])
            i += 1
        if not detection:
            raise ValueError(f"{pathway!r} has no Detection cell")
        rows.append(build_awareness(pathway, normalise(" ".join(seq_parts)),
                                    normalise(" ".join(detection))))
    return rows


def build_awareness(pathway, sequence, detection):
    row = {"pathway": pathway, "sequence": sequence, "detection": detection}
    if AUTOMATIC.search(detection):
        row["isAutomatic"] = True
    roll = FREE_ROLL.search(detection)
    if roll:
        row["freeRoll"] = roll.group(1).upper()
    bonus = BONUS.search(detection)
    if bonus:
        row["bonus"] = int(bonus.group(1))
        # The sentence, never a parsed condition. Three of the four bonuses are
        # scoped to what the divination is ABOUT ("related to their hidden
        # knowledge", "concerns fate, concealment, or secrets"), which this app
        # cannot test -- so the scope travels with the number and the GM reads
        # it, the same call a wealth level's reaction modifier takes (6u).
        for sentence in SENTENCE_SPLIT.split(detection):
            if "+" + bonus.group(1) in sentence:
                row["bonusText"] = sentence
                break
    penalty = DIVINER_PENALTY.search(detection)
    if penalty:
        # Recorded, applied nowhere. See the module docstring.
        row["divinerPenalty"] = signed(penalty.group(1))
    return row


# ---------------------------------------------------------------------------
# Sec. 2 -- Detection Mechanics
# ---------------------------------------------------------------------------
def parse_outcomes(lines):
    """Result cell (indented) then Outcome cell, five times."""
    cells = [(ind, t) for ind, t in lines if t not in OUTCOME_HEADERS]
    rows, i = [], 0
    while i < len(cells) and not cells[i][0]:
        i += 1  # the section header and its intro paragraph
    if i == len(cells):
        raise ValueError("no Detection Mechanics rows found")
    while i < len(cells):
        result = cells[i][1]
        i += 1
        effect = []
        while i < len(cells) and not cells[i][0]:
            effect.append(cells[i][1])
            i += 1
        if not effect:
            raise ValueError(f"the {result!r} row has no outcome cell")
        if result not in OUTCOME_KINDS:
            raise ValueError(f"unknown detection result cell: {result!r}")
        row = {
            "result": result,
            "outcome": OUTCOME_KINDS[result],
            "effect": normalise(" ".join(effect)),
        }
        margin = OUTCOME_MARGIN.match(result)
        if margin:
            row["margin"] = int(margin.group(1))
        rows.append(row)
    return rows


def parse_detection_modifiers(lines):
    """Situation cell then a bare signed number, six times.

    The VALUE is the anchor here -- the opposite column to the table above it,
    which is why indentation is read per table and never across them.
    """
    cells = [t for _, t in lines if t not in MODIFIER_HEADERS]
    rows, situation = [], []
    for cell in cells:
        value = MODIFIER_VALUE.match(cell)
        if value is None:
            situation.append(cell)
            continue
        if not situation:
            raise ValueError(f"a modifier with no situation above it: {cell!r}")
        rows.append({
            "situation": normalise(" ".join(situation)),
            "modifier": signed(value.group(1)),
        })
        situation = []
    if situation:
        raise ValueError(f"the last situation has no modifier: {situation!r}")
    if not rows:
        raise ValueError("the detection modifier table is gone")
    return rows


# ---------------------------------------------------------------------------
# Sec. 3 -- Active Techniques
# ---------------------------------------------------------------------------
def parse_techniques(lines):
    techniques, current, label = [], None, None
    for _, text in lines:
        header = TECHNIQUE_HEADER.match(text)
        if header:
            current = {
                "id": header.group(1),
                "name": header.group(2),
                "availability": header.group(3),
                "bullets": {},
            }
            techniques.append(current)
            label = None
            continue
        if current is None:
            continue  # the section header and its intro paragraph
        bullet = TECHNIQUE_BULLET.match(text)
        if bullet:
            label = bullet.group(1)
            if label in current["bullets"]:
                raise ValueError(f"{current['id']} prints {label} twice")
            current["bullets"][label] = bullet.group(2)
            continue
        if label is None:
            continue  # a technique's own intro paragraph
        current["bullets"][label] = normalise(
            current["bullets"][label] + " " + text
        )
    if not techniques:
        raise ValueError("no active anti-divination techniques found")
    return [build_technique(t) for t in techniques]


def build_technique(t):
    bullets = t["bullets"]
    row = {
        "id": t["id"],
        "name": t["name"],
        "availability": t["availability"],
        "bullets": [{"label": k, "text": v} for k, v in bullets.items()],
    }
    cost = TECHNIQUE_SPI.match(bullets.get("Cost", ""))
    if cost:
        # 3F prices itself as "SPI equal to what a divination of that scope
        # would normally cost", which is a rule and not a number. No field --
        # the distinction RitualCritical.corGained draws between charging
        # nothing and charging zero.
        row["spi"] = int(cost.group(1))
    penalty = EFFECT_PENALTY.search(bullets.get("Effect", ""))
    if penalty:
        row["divinationPenalty"] = signed(penalty.group(1))
    bonus = DETECTION_BONUS.search(bullets.get("Bonus", ""))
    if bonus:
        row["detectionBonus"] = int(bonus.group(1))
    return row


# ---------------------------------------------------------------------------
# Sec. 4 -- Items
# ---------------------------------------------------------------------------
def parse_items(lines):
    """Name / Effect / Cost, with the six names as the anchor.

    The name cells wrap in three shapes and none of them is distinguishable
    from an effect cell by shape alone, so the closed set is what cuts them --
    the same anchor the eight divination method names are (6p).
    """
    cells = [(ind, t) for ind, t in lines if t not in ITEM_HEADERS]
    wanted = {normalise(n): n for n in EXPECTED_ITEMS}
    start = first_name(cells, wanted)
    rows, i = [], start
    while i < len(cells):
        name, i = take_name(cells, i, wanted)
        effect = []
        while i < len(cells) and not ITEM_COST.match(cells[i][1]):
            effect.append(cells[i][1])
            i += 1
        if i == len(cells):
            raise ValueError(f"{name!r} has no cost cell")
        cost = [cells[i][1]]
        i += 1
        # The cost cell wraps indented (" charge", " uses)"); an item name is
        # never indented, so the indent closes the cell.
        while i < len(cells) and cells[i][0]:
            cost.append(cells[i][1])
            i += 1
        rows.append(build_item(name, normalise(" ".join(effect)),
                               normalise(" ".join(cost))))
    if not rows:
        raise ValueError("no anti-divination items found")
    return rows


def first_name(cells, wanted):
    for i in range(len(cells)):
        for j in range(i + 1, min(i + 4, len(cells)) + 1):
            if normalise(" ".join(c[1] for c in cells[i:j])) in wanted:
                return i
    raise ValueError("no anti-divination items found")


def take_name(cells, i, wanted):
    parts = []
    for j in range(i, min(i + 4, len(cells))):
        parts.append(cells[j][1])
        joined = normalise(" ".join(parts))
        if joined in wanted:
            return wanted[joined], j + 1
    raise ValueError(f"no item name starts at {cells[i][1]!r}")


def build_item(name, effect, cost):
    row = {"name": name, "effect": effect, "cost": cost}
    bonus = ITEM_DETECTION_BONUS.match(effect)
    if bonus:
        row["detectionBonus"] = int(bonus.group(1))
    ritual = ITEM_RITUAL_PENALTY.search(effect)
    if ritual:
        # Its own key on purpose: this number is about sensing a RITUAL being
        # performed, not about the Sec. 2 roll, so it must not be reachable
        # from a detection total.
        row["ritualDetectionPenalty"] = signed(ritual.group(1))
    return row


# ---------------------------------------------------------------------------
def verify(byn, awareness, outcomes, modifiers, techniques, items):
    text = " ".join(
        (byn.get(n) or "") for n in range(QUICK_REFERENCE_RANGE[0], ITEM_RANGE[1] + 1)
    )

    # The book's own resolution order -- the evidence that these are separate
    # axes with separate jobs, rather than one table to be merged.
    for step in RESOLUTION_STEPS:
        if step not in text:
            raise ValueError(f"the Quick Reference no longer prints {step!r}")

    if [r["pathway"] for r in awareness] != EXPECTED_PATHWAYS:
        raise ValueError(f"Sec. 1's rows changed: {[r['pathway'] for r in awareness]}")

    # A diviner-facing penalty sitting inside a target-awareness table.
    monster = next(r for r in awareness if r["pathway"] == CROSS_AXIS_PATHWAY)
    if monster.get("divinerPenalty") is None:
        raise ValueError("the Monster row no longer penalises the diviner")
    if monster.get("bonus") is not None or monster.get("freeRoll"):
        raise ValueError("the Monster row now grants awareness after all")

    universal = next(r for r in awareness if r["pathway"] == UNIVERSAL_PATHWAY)
    if not universal.get("isAutomatic"):
        raise ValueError("the Seq 4+ row no longer grants automatic awareness")
    if QUICK_CONTEST not in universal["detection"]:
        raise ValueError("the Seq 4+ row no longer caps automatic awareness")

    if ROLL_PHRASE not in text:
        raise ValueError(f"Sec. 2 no longer defines the roll as {ROLL_PHRASE!r}")
    # THE DISAGREEMENT the roll is read through, pinned from BOTH sides.
    # Sec. 2 scopes "whichever is higher" to characters with passive awareness
    # -- Sec. 1's own population -- and four Sec. 1 rows then name an attribute
    # anyway, splitting 2-2 between Per and SPI. The row wins for the roll the
    # row grants; Sec. 2 keeps the roll made on suspicion alone. Neither side
    # is repaired, so both sentences have to stay findable.
    if PASSIVE_SCOPE_PHRASE not in text:
        raise ValueError(
            f"Sec. 2 no longer scopes its roll with {PASSIVE_SCOPE_PHRASE!r}; it "
            "may no longer overlap Sec. 1 at all, so re-read before trusting "
            "Detection.attributeFor's freeRoll"
        )
    named = {r["pathway"]: r["freeRoll"] for r in awareness if r.get("freeRoll")}
    if named != NAMED_ROLL_ROWS:
        raise ValueError(f"the rows naming their own attribute changed: {named}")
    if len(set(named.values())) < 2:
        raise ValueError(
            "the named rolls no longer disagree with each other; one attribute "
            "throughout would read as loose phrasing for Sec. 2's rule"
        )
    if [r["result"] for r in outcomes] != OUTCOME_NAMES:
        raise ValueError(
            f"Sec. 2's outcome rows changed: {[r['result'] for r in outcomes]}"
        )
    # One row named by MARGIN among four named by OUTCOME -- the two doors
    # FrightCheck, RitualResolution and Wounds each got wrong once.
    if [r["result"] for r in outcomes if "margin" in r] != ["Success by 3+"]:
        raise ValueError("the margin-named row is no longer the only one")

    if [r["situation"] for r in modifiers] != EXPECTED_SITUATIONS:
        raise ValueError(
            f"Sec. 2's modifier rows changed: {[r['situation'] for r in modifiers]}"
        )

    # The same GM fact as Sec. 6's Direct Link, on the target's side of it.
    if not any(r["situation"] == TRUE_NAME_SITUATION for r in modifiers):
        raise ValueError("the true-name row is gone from the detection table")
    counters = OUT / "anti_divination_counters.json"
    if counters.exists():
        linked = json.loads(counters.read_text(encoding="utf-8"))["entries"]
        if not any("true name" in c["text"] for c in linked):
            raise ValueError("Sec. 6's Direct Link no longer names a true name")

    if STACK_PHRASE not in text:
        raise ValueError(f"Sec. 3 no longer says {STACK_PHRASE!r}")
    if [t["id"] for t in techniques] != EXPECTED_TECHNIQUES:
        raise ValueError(f"Sec. 3's techniques changed: {[t['id'] for t in techniques]}")
    priced = {t["id"]: t["divinationPenalty"] for t in techniques
              if "divinationPenalty" in t}
    if priced != PENALTY_TECHNIQUES:
        raise ValueError(f"the priced techniques changed: {priced}")
    # THE WARRANT for summing before halving: every printed penalty is even, so
    # the two orders cannot be told apart by this book's own numbers. The first
    # odd one must force a re-read rather than silently pick a side.
    odd = {k: v for k, v in priced.items() if v % 2 != 0}
    if odd:
        raise ValueError(
            f"a technique now prints an odd penalty ({odd}); sum-then-halve and "
            "halve-then-sum no longer agree -- see AntiDivination and CLAUDE.md 6ah"
        )
    # 3A feeds BOTH rolls: a penalty on the diviner's and a bonus on the
    # target's. If that ever stopped being true the four-source reading would
    # need re-checking.
    ward = next(t for t in techniques if t["id"] == "3A")
    if "detectionBonus" not in ward or "divinationPenalty" not in ward:
        raise ValueError("3A no longer reaches both rolls")

    if [r["name"] for r in items] != EXPECTED_ITEMS:
        raise ValueError(f"Sec. 4's items changed: {[r['name'] for r in items]}")
    # One item, priced twice -- generally in Sec. 2 and specifically here. The
    # day this overlap goes is the day the scoping stops being needed.
    pendant = next(r for r in items if r["name"] == PENDANT)
    generic = next(r for r in modifiers if r["situation"] == GENERIC_CHARM_SITUATION)
    if pendant.get("detectionBonus") is None or generic["modifier"] <= 0:
        raise ValueError("the charm is no longer priced in both sections")
    if pendant["detectionBonus"] == generic["modifier"]:
        raise ValueError("the two charm prices now agree; the scoping is moot")
    # The candle's number is scoped to a ritual, so it never joins the above.
    candle = next(r for r in items if r["name"] == CANDLE)
    if CANDLE_SCOPE not in candle["effect"]:
        raise ValueError("the candle's penalty is no longer scoped to a ritual")
    if candle.get("detectionBonus") is not None:
        raise ValueError("the candle now reaches the detection roll")


def write(name, spans, entries):
    path = OUT / name
    path.write_text(
        json.dumps({"sourceLines": {CHAPTER: spans}, "entries": entries},
                   indent=1, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    return path


def extract():
    byn = load_lines()
    awareness = parse_awareness(raw(byn, *AWARENESS_RANGE))
    outcomes = parse_outcomes(raw(byn, *OUTCOME_RANGE))
    modifiers = parse_detection_modifiers(raw(byn, *MODIFIER_RANGE))
    techniques = parse_techniques(raw(byn, *TECHNIQUE_RANGE))
    items = parse_items(raw(byn, *ITEM_RANGE))
    verify(byn, awareness, outcomes, modifiers, techniques, items)
    return awareness, outcomes, modifiers, techniques, items


def main():
    awareness, outcomes, modifiers, techniques, items = extract()
    written = [
        write("divination_awareness.json", [list(AWARENESS_RANGE)], awareness),
        write("detection_outcomes.json", [list(OUTCOME_RANGE)], outcomes),
        write("detection_modifiers.json", [list(MODIFIER_RANGE)], modifiers),
        write("anti_divination_techniques.json", [list(TECHNIQUE_RANGE)], techniques),
        write("anti_divination_items.json", [list(ITEM_RANGE)], items),
    ]
    for path in written:
        print(f"wrote {path.name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
