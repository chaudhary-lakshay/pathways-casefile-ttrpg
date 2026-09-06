"""Extract Ch. 7's Section VIII worked rituals as a golden oracle (p. 69-72).

WHAT THIS IS FOR
----------------
Seven of the section's rituals print a full modifier table, a net modifier, an
effective skill and an SPI cost, all for one stated caster ("Ritualistic
Magic-14"). That makes them the chapter's own regression suite: the book
working its own arithmetic, in public, seven times. `GoldenRitualTest` replays
them against the engine the way `GoldenSheetTest` replays the 23 character
sheets -- as a PARTIAL oracle. Where the book disagrees with itself the
disagreement is recorded, never repaired.

THE SECTION PRINTS TEN RITUALS, NOT SIX
----------------------------------------
A previous slice's notes said six. There are ten: 1-7 are worked examples with
tables, and 8-10 are procedure templates that print a RANGE of base difficulty
and cost instead of one ritual's numbers. Only the first seven can be replayed.

That miscount mattered, because the two-axis SPI decision (CLAUDE.md 6aa) was
argued from "six of six". Rechecked against the seven that print a SINGLE cost
it holds and is stronger:

    R1 Enhancement    4 SPI   ladder Moderate 4-6  weight 4-6   both
    R2 Divination     3 SPI   ladder Simple   2-3  weight 2     LADDER ONLY
    R3 Summoning      7 SPI   ladder Powerful 7-10 weight 8+    LADDER ONLY
    R4 Curse          7 SPI   ladder Powerful 7-10 weight 4-6   LADDER ONLY
    R5 Oath-Sealing   5 SPI   ladder Moderate 4-6  weight 4-6   both
    R6 Fabrication    6 SPI   ladder Moderate 4-6  weight 4-6   both
    R7 Binding        9 SPI   ladder Powerful 7-10 weight 8+    both

Seven of seven inside the complexity ladder; three of seven outside the weight
column, none outside the ladder. verify() asserts both halves, so the evidence
the decision rests on cannot quietly change.

AND THE SECTION PRINTS NINE COSTS, NOT SEVEN
---------------------------------------------
"All seven costed rituals" is a count taken with the filter "has a worked
total". Rituals 8 and 9 print an `SPI Cost:` line as well, so the section
prints nine -- and neither of those two is contained by any rung of the
complexity ladder OR any row of the weight table:

    R8 Sacrificial  2-4 SPI   ladder Light 2-3  / Moderate 4-6   straddles
    R9 Dualistic    4-8 SPI   ladder Moderate 4-6 / Heavy 7-10   straddles

Not a third disagreement to repair. A template prices a CLASS of ritual across
scopes where a rung prices one ritual, which is exactly why the seven single
values each sit in a rung and these two sit in none. It is recorded because
the "seven of seven inside the ladder" evidence was measured over
WORKED_RANGE, and WORKED_RANGE stops one line before Ritual 8 -- an
assertion whose corpus excludes the only rows that could contradict it.
verify() reads both templates now and fails the day a printed row swallows
one whole.

AND THE BASE DIFFICULTIES DO NOT MATCH THE WEIGHT TABLE
---------------------------------------------------------
This is the section's own second disagreement, and it is systematic:

    R1 Enhancement   -2   weight table: Moderate -2   agrees
    R2 Divination    +0   weight table: Light    +0   agrees
    R3 Summoning     -4   weight table: Heavy    -4   agrees
    R4 Curse         -3   weight table: Moderate -2   DIFFERS
    R5 Oath-Sealing  -3   weight table: Moderate -2   DIFFERS
    R6 Fabrication   -3   weight table: Moderate -2   DIFFERS
    R7 Binding       -4   weight table: Heavy    -4   agrees

Three rituals grade INSIDE the Moderate band at -3, a value the weight table's
three rows (+0 / -2 / -4) cannot express -- and Ritual 8 prints "-2 (minor
offering) to -6 (living sacrifice)", which runs past Heavy's -4 entirely. So
Section V's modifier column is a summary the worked examples refine, exactly
as its SPI column is a summary the complexity ladder refines. Same shape,
second column.

Not repaired, and deliberately not turned into a fourth weight either: the
book prints no table of -3s, and inventing one from three examples would be
authoring content. It is recorded here and asserted by verify(), so a reader
who trusts `ritual_weights.json` alone finds out.

WHAT ELSE THE SECTION CONTRADICTS
-----------------------------------
Ritual 8's Key Modifier line prints "Trivial +0, Meaningful +2, Precious +4"
where Section IV.D prints "Ordinary (common goods, small animals) grants +0".
Same three values, and the cheapest tier has two names. Recorded the way
"Eternal Aeon" keeps both of its spellings (CLAUDE.md 6s) -- the lookups stay
exact and nothing is merged.

Ritual 8 also rolls the Critical Failure Table "with +2 to the result", where
Section VI's severe misfire rolls it at -2. Different trigger, opposite shift,
and the table is not ordered by severity (6d), so neither is "worse".
Recorded, not reconciled.

WHAT IS DELIBERATELY NOT EXTRACTED
----------------------------------
Rituals 8-10's templates: they print ranges rather than a worked total, so
there is no arithmetic to check an engine against. That reason is about the
ORACLE and says nothing about whether the lines are read -- 8 and 9 are, by
TEMPLATE_RANGE, for what they disagree with. Ritual 9 had been inside the
group this sentence excludes and outside every range the file reads, so its
silence was inherited rather than checked; it is checked now.

Ritual 10's pathway/material table (17 rows of "Fool / Silver, Paper") is real
content with no caller -- nothing in the app creates charms -- and belongs to
whichever slice does. It prints no SPI cost, which is why the count above is
nine and not ten.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "7"
WORKED_RANGE = (4422, 4638)
# Rituals 8 and 9's templates, read only so verify() can pin what they
# disagree with. Ritual 9 was inside the group this file excludes and outside
# every range it reads, so its silence was inherited rather than checked.
TEMPLATE_RANGE = (4639, 4664)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADERS = {"Category", "Base Difficulty", "Time", "Value"}
# The book's own marker for where a ritual's modifier table starts. Kept in
# the stream rather than stripped as furniture, because the Intent and Setup
# prose between the header row and the table wraps freely -- and a
# wrap-joining parse with no anchor swallows it into the first row's label.
TABLE_START = "Modifier Source"

MINUS = "-−"
TITLE = re.compile(r"^Ritual (\d+): (.+)$")
VALUE = re.compile(rf"^([+{MINUS}]\d+)$")
NET = "Net modifier"
EFFECTIVE = re.compile(rf"^Effective Skill: (\d+) ([+{MINUS}]) (\d+) = (\d+)")
SPI_COST = re.compile(r"^SPI Cost: (\d+)")
CASTER = re.compile(r"Each assumes a caster with Ritualistic Magic-(\d+)")

# "SPI Cost: 2-4 SPI", with whichever dash the flattening produced.
TEMPLATE_COST = re.compile(rf"SPI Cost: (\d+)\s*[{MINUS}–—]\s*(\d+) SPI")

TEMPLATE_TIERS = re.compile(r"Trivial \+0, Meaningful \+2, Precious \+4")
TEMPLATE_SHIFT = re.compile(r"Critical Failure Table with \+(\d+) to the result")

EXPECTED_TITLES = [
    "Luck Enhancement",
    "Divination — Seeking a Hidden Location",
    "Summoning a Minor Spirit",
    "Curse of the Unlucky Hand",
    "Oath-Sealing Between Two Parties",
    "Fabrication — Warding Talisman",
    "Binding a Spirit to an Object",
]
EXPECTED_NETS = [2, 5, 2, -1, 1, 2, 3]
EXPECTED_SPI = [4, 3, 7, 7, 5, 6, 9]
EXPECTED_BASES = [-2, 0, -4, -3, -3, -3, -4]
# The three whose base difficulty the weight table cannot express.
GRADED_INSIDE_MODERATE = {"Curse", "Oath-Sealing", "Fabrication"}


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    out = []
    for n in range(start, end + 1):
        text = (byn.get(n) or "").strip()
        if not text or FURNITURE.match(text) or text in HEADERS:
            continue
        out.append(text)
    return out


def signed(text):
    return int(text.replace("−", "-"))


def parse(cells):
    """One ritual per "Ritual N:" heading.

    Each prints a three-cell header (category / base difficulty / time), then
    prose, then label/value pairs ending at "Net modifier". The VALUE cells are
    the anchor: a bare signed integer on its own line, which no label is --
    the same shape the hit-location and shield tables key on.
    """
    rituals, current = [], None
    for cell in cells:
        title = TITLE.match(cell)
        if title:
            current = {
                "number": int(title.group(1)),
                "name": title.group(2),
                "modifiers": [],
            }
            rituals.append(current)
            continue
        if current is None:
            continue
        # "Net modifier" ENDS the table. Everything after it is the On
        # Success / On Failure / On Critical Failure prose, which is not rows
        # the parse lost -- only the Effective Skill and SPI Cost lines below
        # it are still wanted.
        if "net" in current and not (
            EFFECTIVE.match(cell) or SPI_COST.match(cell)
        ):
            continue

        if "category" not in current:
            current["category"] = cell
            continue
        if "baseDifficulty" not in current:
            if not VALUE.fullmatch(cell):
                raise ValueError(
                    f"ritual {current['number']}'s base difficulty is {cell!r}, "
                    "not a signed integer")
            current["baseDifficulty"] = signed(cell)
            continue
        if "time" not in current:
            current["time"] = cell
            continue
        if cell == TABLE_START:
            current["started"] = True
            continue

        effective = EFFECTIVE.match(cell)
        if effective:
            skill, sign, delta, total = effective.groups()
            current["casterSkill"] = int(skill)
            current["effectiveSkill"] = int(total)
            current["printedDelta"] = int(delta) if sign == "+" else -int(delta)
            continue
        cost = SPI_COST.match(cell)
        if cost:
            current["spi"] = int(cost.group(1))
            continue

        if not current.get("started"):
            # Intent and Setup prose, above the table's own header.
            continue

        if VALUE.fullmatch(cell):
            if not current["modifiers"] or "value" in current["modifiers"][-1]:
                raise ValueError(
                    f"ritual {current['number']} has a value {cell!r} with no label")
            current["modifiers"][-1]["value"] = signed(cell)
            if current["modifiers"][-1]["source"] == NET:
                current["net"] = current["modifiers"].pop()["value"]
            continue
        if current["modifiers"] and "value" not in current["modifiers"][-1]:
            # A label that wrapped onto a second line.
            current["modifiers"][-1]["source"] += f" {cell}"
            continue
        current["modifiers"].append({"source": cell})

    return [r for r in rituals if "net" in r]


def tidy(rituals):
    """Every row inside the table carries a value; a bare label is a parse loss.

    The Intent/Setup prose is already excluded by [TABLE_START], so anything
    reaching here with no value is a row whose number the parse dropped, and
    is raised rather than silently discarded.
    """
    for ritual in rituals:
        ritual.pop("started", None)
        if not ritual["modifiers"]:
            raise ValueError(f"ritual {ritual['number']} has no modifier rows at all")
        for row in ritual["modifiers"]:
            if "value" not in row:
                raise ValueError(
                    f"ritual {ritual['number']}'s row {row['source']!r} lost its value")
    return rituals


def verify(rituals, caster, weights, complexity, template):
    if len(rituals) != len(EXPECTED_TITLES):
        raise ValueError(
            f"{len(rituals)} worked rituals with tables, not {len(EXPECTED_TITLES)}. "
            "Section VIII prints ten in all; 8-10 are templates with ranges "
            "rather than one ritual's arithmetic")
    if [r["name"] for r in rituals] != EXPECTED_TITLES:
        raise ValueError(f"the worked rituals changed: {[r['name'] for r in rituals]}")
    if caster != 14:
        raise ValueError(f"the stated caster is Ritualistic Magic-{caster}, not 14")

    for ritual, want_net, want_spi, want_base in zip(
        rituals, EXPECTED_NETS, EXPECTED_SPI, EXPECTED_BASES,
    ):
        # THE BOOK'S OWN ARITHMETIC. Every modifier row, summed, is the net it
        # printed -- and the net plus the stated caster is the effective skill.
        # If either stops holding, the parse has mis-cut a row.
        total = sum(row["value"] for row in ritual["modifiers"])
        if total != ritual["net"]:
            raise ValueError(
                f"ritual {ritual['number']}'s rows sum to {total:+d} but it "
                f"prints a net of {ritual['net']:+d}: "
                f"{[(r['source'], r['value']) for r in ritual['modifiers']]}")
        if ritual["net"] != want_net:
            raise ValueError(f"ritual {ritual['number']}'s net changed: {ritual['net']}")
        if caster + ritual["net"] != ritual["effectiveSkill"]:
            raise ValueError(
                f"ritual {ritual['number']} prints an effective skill of "
                f"{ritual['effectiveSkill']}, not {caster} + {ritual['net']:+d}")
        if ritual["spi"] != want_spi:
            raise ValueError(f"ritual {ritual['number']}'s SPI changed: {ritual['spi']}")
        if ritual["baseDifficulty"] != want_base:
            raise ValueError(
                f"ritual {ritual['number']}'s base difficulty changed: "
                f"{ritual['baseDifficulty']}")
        # The first modifier row always restates the base difficulty, which is
        # what lets the header and the table check each other.
        if ritual["modifiers"][0]["value"] != ritual["baseDifficulty"]:
            raise ValueError(
                f"ritual {ritual['number']}'s header says {ritual['baseDifficulty']:+d} "
                f"and its first row says {ritual['modifiers'][0]['value']:+d}")

    # EVIDENCE FOR THE TWO-AXIS SPI DECISION (CLAUDE.md 6aa). Every costed
    # ritual falls inside the complexity ladder; three fall outside the weight
    # column. If that stopped being true the decision would need remaking, so
    # it is asserted rather than remembered.
    rungs = [(c["spi"], c["spiMax"], c["spiOpen"]) for c in complexity]
    outside_ladder = [
        r for r in rituals
        if not any(
            r["spi"] >= low and (open_top or high is None or r["spi"] <= high)
            for low, high, open_top in rungs
        )
    ]
    if outside_ladder:
        raise ValueError(
            "these worked rituals now fall outside the complexity ladder: "
            f"{[(r['number'], r['spi']) for r in outside_ladder]}. The ladder "
            "is the operative SPI table BECAUSE they all fitted it")

    outside_weights = []
    for ritual in rituals:
        weight = next(
            (w for w in weights
             if ritual["category"].lower() in w["categories"].lower()),
            None,
        )
        if weight is None:
            continue
        top = None if weight.get("spiOpen") else weight.get("spiMax") or weight["spi"]
        if ritual["spi"] < weight["spi"] or (top is not None and ritual["spi"] > top):
            outside_weights.append((ritual["number"], ritual["spi"], weight["weight"]))
    if len(outside_weights) < 3:
        raise ValueError(
            f"only {len(outside_weights)} worked rituals now fall outside the "
            "weight table's SPI column. Three did -- Divination at 3, Summoning "
            "at 7 and Curse at 7 -- and that is the whole evidence that the "
            "column is a summary the complexity ladder refines")

    # THE SECOND DISAGREEMENT: three rituals grade inside the Moderate band at
    # -3, which the weight table's +0/-2/-4 cannot express.
    printed_modifiers = {w["modifier"] for w in weights}
    graded = {
        r["category"] for r in rituals
        if r["baseDifficulty"] not in printed_modifiers
    }
    if graded != GRADED_INSIDE_MODERATE:
        raise ValueError(
            f"the rituals grading outside the weight table's modifiers are now "
            f"{graded}, not {GRADED_INSIDE_MODERATE}. Three printed -3 where "
            "the table prints -2, and that is why the table is a summary")

    # Ritual 8 names Section IV.D's cheapest tier differently and shifts the
    # critical table the opposite way. Both recorded, neither reconciled.
    if not TEMPLATE_TIERS.search(template):
        raise ValueError(
            "Ritual 8's 'Trivial +0, Meaningful +2, Precious +4' is gone. It is "
            "the same three values Section IV.D prints, with the cheapest tier "
            "under a second name")
    shift = TEMPLATE_SHIFT.search(template)
    if not shift or int(shift.group(1)) != 2:
        raise ValueError(
            "Ritual 8 rolled the Critical Failure Table at +2 where Section VI "
            "rolls it at -2; that opposite shift is gone or changed")

    # Rituals 8 and 9 are the section's eighth and ninth SPI costs, and they
    # print RANGES because they price a class of ritual across scopes. No rung
    # and no weight row contains one whole -- recorded, never repaired, and
    # the day one fits, the two axes have merged and this has to fail.
    costs = template_costs(template)
    if len(costs) != 2:
        raise ValueError(
            f"Rituals 8 and 9 print two SPI ranges; {len(costs)} were found. "
            "They are why 'all seven costed rituals' is a count taken with the "
            "filter 'has a worked total' -- the section prints nine")
    for row in list(complexity) + list(weights):
        top = None if row.get("spiOpen") else row.get("spiMax") or row["spi"]
        for low, high in costs:
            if low >= row["spi"] and (top is None or high <= top):
                named = row.get("complexity") or row.get("weight")
                raise ValueError(
                    f"a template's {low}-{high} SPI now fits one printed row "
                    f"whole ({named}); the two tables price one ritual and "
                    "the templates a class of them")


def template_costs(text):
    """The SPI ranges Rituals 8 and 9 print, in printed order.

    A range, not a number: these templates price a CLASS of ritual across
    scopes, which is why no rung of the complexity ladder and no row of the
    weight table contains one. The seven worked rituals above them each name a
    single number that does sit in a rung -- so the section prints NINE costs
    and only seven of them are a ritual's own.
    """
    return [(int(a), int(b)) for a, b in TEMPLATE_COST.findall(text)]


def extract():
    byn = load_lines()
    cells = stream(byn, *WORKED_RANGE)
    found = CASTER.search(" ".join(cells))
    if not found:
        raise ValueError("the section no longer states the caster's skill")
    caster = int(found.group(1))
    rituals = tidy(parse(cells))
    template = " ".join(stream(byn, *TEMPLATE_RANGE))

    weights = json.loads(
        (OUT / "ritual_weights.json").read_text(encoding="utf-8"))["entries"]
    complexity = json.loads(
        (OUT / "ritual_complexity.json").read_text(encoding="utf-8"))["entries"]
    verify(rituals, caster, weights, complexity, template)
    return rituals, caster


def main():
    rituals, caster = extract()

    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "ritual_examples.json").write_text(
        json.dumps(
            {"sourceLines": {CHAPTER: [list(WORKED_RANGE)]},
             "casterSkill": caster,
             "entries": rituals},
            ensure_ascii=False, indent=1) + "\n", encoding="utf-8")

    print(f"worked rituals: {len(rituals)} with tables, all at Ritualistic Magic-{caster}")
    for ritual in rituals:
        print(f"  {ritual['number']}. {ritual['category']:13} base "
              f"{ritual['baseDifficulty']:+d}  net {ritual['net']:+d}  "
              f"skill {ritual['effectiveSkill']:2}  {ritual['spi']} SPI  "
              f"({len(ritual['modifiers'])} rows)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
