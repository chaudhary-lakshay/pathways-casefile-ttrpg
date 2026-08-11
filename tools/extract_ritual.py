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

THE FLOURISH BONUS (global lines 4140-4143), Phase II's +1
-----------------------------------------------------------
"The GM may award a Flourish Bonus (+1) for good in-character delivery",
followed immediately by the book stating what it is FOR:

    Effective skill = Ritualistic Magic + Preparation Mod + Flourish Bonus

That formula line is the evidence the bonus reaches the roll. It is NOT read
as exhaustive -- weight, CoR, support and language all reach the same target
and it names none of them. It is Phase II's summary of Phase II.

The number and the words naming it are on DIFFERENT LINES: the sentence wraps
between "Flourish Bonus" and "(+1) for good in-character delivery", so neither
line alone says what the rule is. The lines are joined before the bonus is
read -- the same rule the "Spoken-only ... costs / half." page break needed in
extract_languages.py.

It belongs to the FULL ritual alone. "Flourish" appears exactly twice in this
book, both inside Phase II, and the Quick Ritual's Step 3 says "Sum the
following two modifiers" and then names Preparation Quality and Target Link.
That is the mirror image of the target link, which is Quick's alone because
Full's preparation rows name their links (see extract_ritual_modes.py), and
verify() pins both halves: the two occurrences, and the absence from Step 3.

The +1 itself lives in Kotlin (`RitualMode.FLOURISH_BONUS`) because it is a
number inside a sentence rather than a table -- the split the maneuver table's
+4/-4, the +3 support cap and the orthodox -2 all take. This file claims the
sentence it was read from, so the rule stays findable.

AND THE CANON PROCEDURE'S TIMING PENALTY IS DELIBERATELY NOT APPLIED
---------------------------------------------------------------------
Section C, step 1 prints "Wrong timing for the entity gives -1 to -2" (line
4161). It is refused on two printed facts, both pinned:

  * Phase I already declares "Timing: Does it match the entity's domain?"
    among the choices that "feed into a single Preparation Modifier", and
    Excellent's own cell names "celestial timing". Offering the canon penalty
    beside it charges one mistimed ritual twice -- the scoping that keeps
    Appendix A's adverse conditions off a shot already priced by chapter 5's
    darkness row.
  * The book labels Section C itself: "The novel's full procedure is preserved
    here as a reference ... when you want the full texture of the ritual."

Both are recorded rather than assumed, because "it is reference prose" is a
reason that has to survive the page printing a number.
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
# Phase II. Four lines: the heading, the wrapped Flourish sentence, and the
# formula that says what the bonus is added to.
FLOURISH_RANGE = (4140, 4143)
FAILURE_RANGE = (4349, 4359)
CRITICAL_RANGE = (4361, 4384)

# Section A, the whole Quick Ritual. Read only to prove what is NOT in it.
QUICK_RITUAL_RANGE = (4036, 4105)
# Step 4 -- "Roll & Resolve". Thirty lines claimed by nobody until the seam
# sweep (6bb), holding two tables and one rule:
#
#   * a THIRD printing of the weight table, agreeing with Section V's
#   * the chapter's only ritual OUTCOME table, whose two success rows are
#     printed nowhere else in the book
#   * the quick ritual's burning clause, which ch. 2's rule already covers
#
# It is not the quick mode's private table. Phase III of the full ritual says
# "Then resolve using the same outcome table as Quick Ritual (Step 4)", so
# this one table serves both modes -- and its failure rows are a COARSENING
# of Section VI's, not a rival to them: every band here is a union of bands
# there, with the same sentences abbreviated, which is what p. 61's "the same
# underlying canon procedure ... different levels of granularity" describes.
STEP_FOUR_RANGE = (4076, 4105)
# The outcome table inside it, from its first label to its last prose line.
OUTCOME_RANGE = (4094, 4103)
# Phase III -- Close, whose last line is what makes Step 4's table general.
FULL_CLOSE_RANGE = (4147, 4154)
FULL_DEFERS_TO_STEP_FOUR = "the same outcome table as Quick Ritual"
GRANULARITY_SENTENCE = "different levels of granularity"
QUICK_BURN = "1 SPI = +1"
# Section C, the canon procedure, whose timing penalty is refused.
CANON_RANGE = (4155, 4165)

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
# "Success by 3+", "Success by 0-2" -- the same shape one word over, and the
# only place this book grades a ritual that WORKED.
SUCCESS = re.compile(rf"^Success by (\d+)(?:[{MINUS}–—](\d+)|(\+))$")
# Step 4 writes "Critical Failure (17-18)" where Section VI writes "Critical
# Failure (roll 17-18)", so CRITICAL_LABEL does not match it. It is needed
# only as a row BOUNDARY, never stored.
STEP_FOUR_CRITICAL = re.compile(rf"^Critical Failure \([{MINUS}–—\d]+\)$")
CRITICAL_LABEL = re.compile(r"^Critical Failure \(roll [\d–—-]+\)$")
BAND = re.compile(rf"^(\d+)(?:[{MINUS}–—](\d+))?$")
# "Corruption. Gain 1 CoR." / "... Gain 2 CoR. Nightmares for 1d weeks."
CRITICAL_COR = re.compile(r"\bGain (\d+) CoR\b", re.IGNORECASE)

# "... may award a Flourish Bonus (+1) for good in-character delivery."
# Anchored on the NAME, not on the first bracketed number in the paragraph:
# the line above it ends "(4-part Hermes structure - see reference below)",
# which a bare bracket rule would read instead.
FLOURISH = re.compile(r"Flourish Bonus\s*\(([+\-−]\d+)\)", re.IGNORECASE)
FLOURISH_NAME = "Flourish Bonus"
# The book's own statement of what the bonus is added to. Evidence, not a
# complete list of the target's terms.
FLOURISH_FORMULA = "Effective skill = Ritualistic Magic + Preparation Mod + Flourish Bonus"
# Quick's Step 3, which names its modifiers and stops at two.
QUICK_SUMS_TWO = "Sum the following two modifiers"
# The Phase I preparation cell that already prices timing.
PREPARATION_PRICES_TIMING = "celestial timing"
# Section C's own label, and the penalty it prints that is not applied.
CANON_IS_REFERENCE = "preserved here as a reference"
CANON_TIMING_PENALTY = "Wrong timing for the entity gives"

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


def parse_step_four(lines):
    """The two SUCCESS rows of Step 4's outcome table.

    Only the success half is returned. The failure half of the same table is
    Section VI's, coarsened -- verify_step_four checks that -- and storing a
    second copy of rows this repo already commits is the silent merge 4.1
    forbids, the call 6ac made for the study page's truncated CP grid.

    EVERY label of the table is a boundary, not only the two being kept. The
    success rows come first and the failure rows follow them, so a predicate
    matching successes alone makes the last success cell swallow the whole
    failure half -- the trailing-paragraph trap extract_core and
    extract_rituals were each caught by. The rows are dropped after the
    boundaries are drawn, never before.
    """
    def is_label(line):
        return (
            SUCCESS.match(line) is not None
            or FAILURE.match(line) is not None
            or STEP_FOUR_CRITICAL.match(line) is not None
        )

    def start(line):
        match = SUCCESS.match(line)
        if not match:
            return {"printed": line, "graded": False}
        low, high, plus = match.groups()
        return {
            "printed": line,
            "from": int(low),
            "to": None if plus else int(high),
            "graded": True,
        }

    rows = collect(lines, is_label, start)
    kept = [without_text(r, "outcome") for r in rows if r["graded"]]
    if len(kept) == len(rows):
        raise ValueError(
            "Step 4's outcome table now prints successes only. Its failure "
            "rows are what prove it is Section VI coarsened rather than a "
            "rival table, so their absence needs the page re-read"
        )
    return [{k: v for k, v in row.items() if k != "graded"} for row in kept]


def verify_step_four(success, cells, failures, weights, full_close):
    """Why the success rows are adopted and the failure rows are not.

    Both halves come off the SAME table, so its failure rows agreeing with
    Section VI is the entire warrant for trusting its unrepeated success
    rows -- the per-table standard 6ad set, and 6v is why it cannot be a
    per-source one.
    """
    # Compared on the PARSED bands, never on the printed label: this page
    # prints "0-2" with an en dash where Section VI uses a hyphen, and a
    # string match would fail on the glyph while the rule was intact -- the
    # two-kinds-of-minus trap the wealth ladder set first (6u).
    bands = [(r["from"], r["to"]) for r in success]
    if bands != [(3, None), (0, 2)]:
        raise ValueError(f"the ritual success bands changed: {success}")
    if not all(r["printed"].startswith("Success by") for r in success):
        raise ValueError(f"a success row is no longer labelled by margin: {success}")
    # Best-first, and they must not overlap: a margin of 3 belongs to one row.
    if success[0]["from"] <= success[1]["to"]:
        raise ValueError(f"the two success bands now overlap: {success}")
    if success[1]["from"] != 0:
        raise ValueError("the milder success band no longer starts at 0, so a "
                         "ritual that just scraped in would land on no row")

    # The failure half COARSENS Section VI rather than contradicting it: every
    # band printed here must be a union of bands printed there. If that ever
    # stops being true they are two tables, the shape 6y found for the two
    # preparation ladders, and the app has to pick per mode.
    bands = [m.groups() for m in (FAILURE.match(c) for c in cells) if m]
    edges = {int(low) for low, _, _ in bands}
    theirs = {row["from"] for row in failures if not row["critical"]}
    if not edges <= theirs:
        raise ValueError(
            f"Step 4's failure bands start at {sorted(edges)} and Section VI's "
            f"at {sorted(theirs)}. Step 4's used to be a subset -- a coarser "
            "view of the same rule. They are now two tables and the app must "
            "choose one per mode; see CLAUDE.md 6y for that shape"
        )

    # And the full ritual is not off using some other table.
    if FULL_DEFERS_TO_STEP_FOUR not in " ".join(full_close):
        raise ValueError(
            "Phase III no longer sends the full ritual to Step 4's outcome "
            "table. These success bands are applied to BOTH modes only "
            "because it does"
        )

    # The third printing of the weight table, which is why this block can be
    # claimed without storing it twice.
    joined = " ".join(cells)
    for row in weights:
        if f"{row['weight']} (" not in joined and row["weight"] not in joined:
            raise ValueError(f"Step 4 no longer prints the {row['weight']} weight")
    if QUICK_BURN not in joined:
        raise ValueError("Step 4 no longer prints the burning clause ch. 2 covers")
    return success


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


def parse_flourish(cells):
    """The Flourish Bonus's value, read from the joined Phase II paragraph.

    The cells are joined before the search because the sentence WRAPS between
    the words naming the bonus and the number itself -- "... may award a
    Flourish Bonus" ends one line and "(+1) for good in-character delivery."
    begins the next. Scanning line by line finds a name with no number, or a
    number with no name, and either reads as the rule being absent.
    """
    joined = " ".join(cells)
    found = FLOURISH.search(joined)
    if not found:
        raise ValueError(
            f"no '{FLOURISH_NAME} (+N)' in Phase II; the paragraph reads {joined!r}"
        )
    return int(found.group(1).replace("−", "-")), joined


def verify_flourish(bonus, phase_two, quick, canon, preparation):
    """The bonus, and the two scoping decisions that rest on the same pages."""
    if bonus != 1:
        raise ValueError(f"the Flourish Bonus used to be +1; the page now prints {bonus:+d}")
    # The book's own statement that this bonus reaches the roll. Without it the
    # +1 would be a number in a paragraph with nothing saying where it lands.
    if FLOURISH_FORMULA not in phase_two:
        raise ValueError(
            "Phase II no longer prints its effective-skill formula. That line "
            "is the only evidence the Flourish Bonus is added to the ritual "
            f"roll at all; expected {FLOURISH_FORMULA!r}"
        )

    # FULL-only, and the evidence is an absence, so both halves are checked.
    # This is the mirror of the target link, which is QUICK's alone.
    if FLOURISH_NAME.lower() not in phase_two.lower():
        raise ValueError("Phase II no longer names the Flourish Bonus")
    if QUICK_SUMS_TWO not in quick:
        raise ValueError(
            "the Quick Ritual's Step 3 no longer says it sums exactly two "
            f"modifiers ({QUICK_SUMS_TWO!r}). That sentence is the whole reason "
            "the Flourish Bonus is scoped to the Full ritual"
        )
    if FLOURISH_NAME.lower() in quick.lower():
        raise ValueError(
            "the Quick Ritual now names the Flourish Bonus too, so it is no "
            "longer Full-mode only and RitualMode's guard is wrong"
        )

    # And the canon timing penalty, refused because Phase I already prices it.
    if CANON_TIMING_PENALTY not in canon:
        raise ValueError(
            f"Section C no longer prints {CANON_TIMING_PENALTY!r}. The refusal to "
            "apply it is only a decision while the number is still there"
        )
    if CANON_IS_REFERENCE not in canon:
        raise ValueError(
            "Section C no longer labels itself a reference; the second half of "
            "the reason for not applying its timing penalty is gone"
        )
    priced = " ".join(p["looksLike"] for p in preparation).lower()
    if PREPARATION_PRICES_TIMING not in priced:
        raise ValueError(
            "the Phase I preparation rows no longer mention timing "
            f"({PREPARATION_PRICES_TIMING!r}), so nothing shows that the canon "
            "procedure's -1 to -2 would be charging one mistimed ritual twice"
        )


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

    # Phase II's +1. Nothing is written for it -- the number lives in Kotlin --
    # but the sentence it was read from is claimed below, so an assertion whose
    # source is unfindable does not happen again (the PCR formula, 6af).
    bonus, phase_two = parse_flourish(stream(byn, *FLOURISH_RANGE))
    verify_flourish(
        bonus,
        phase_two,
        " ".join(stream(byn, *QUICK_RITUAL_RANGE)),
        " ".join(stream(byn, *CANON_RANGE)),
        preparation,
    )

    # Step 4's outcome table. Only its success half is written; its failure
    # half is Section VI coarsened, and its weight table is Section V's a
    # third time. The whole block is claimed all the same, because every part
    # of it is either stored or asserted, and an assertion whose source is
    # unclaimed is a rule nobody can find again (6af).
    step_four = stream(byn, *STEP_FOUR_RANGE)
    success = verify_step_four(
        parse_step_four(stream(byn, *OUTCOME_RANGE)),
        step_four,
        failures,
        weights,
        stream(byn, *FULL_CLOSE_RANGE),
    )
    write("ritual_success", success, [list(STEP_FOUR_RANGE)])

    write("ritual_weights", weights, [list(WEIGHT_RANGE), list(WEIGHT_COST_RANGE)])
    write("ritual_preparation", preparation,
          [list(PREPARATION_RANGE), list(FLOURISH_RANGE)])
    write("ritual_failures", failures, [list(FAILURE_RANGE)])
    write("ritual_critical", critical, [list(CRITICAL_RANGE)])

    print(f"ritual_weights: {[(w['weight'], w['spi']) for w in weights]}")
    print(f"ritual_preparation: {[(p['quality'], p['printed']) for p in preparation]}")
    print(f"ritual_failures: {len(failures)} rows")
    print(f"ritual_critical: {len(critical)} bands")
    print(f"flourish bonus: {bonus:+d} (Full ritual only; lives in RitualMode)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
