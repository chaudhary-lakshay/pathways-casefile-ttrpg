"""Extract skills, advantages, disadvantages and the thrust/swing table into
committed JSON content for :core:rules.

Source shapes (verified against build/extract/lines.json, not assumed):

Skills (Ch. 3, "Complete Skill List by Category", global lines 1664-2246):
    Guns (Pistol)
     DX/Easy
    Derringers, semi-automatic pistols
Three-line records: name / " ATTR/Difficulty" / notes (notes may wrap).
Category banners look like "— COMBAT SKILLS —"; the header trio
(" Skill"/" Attr / Difficulty"/" Notes") appears once, right after the
section title. Trailing appendix content (Study Points optional rule,
Character Creation Summary, global lines 2181-2246) is a different table
shape entirely and is NOT part of the skill list; it is deliberately
excluded and shows up as unclaimed in coverage.py. The single "Inventor!"
entry uses a "Wildcard" difficulty that the closed Difficulty enum
(Easy/Average/Hard/Very Hard) cannot represent, so it is also excluded
(and reported, not silently invented).

Advantages (Ch. 3, "Step 2: Advantages", global lines 530-1118) and
Disadvantages ("Step 3: Disadvantages", 1119-1569) share the same
name / cost / effect triple shape, with category banners of the form
"Advantage"/"Cost"/"Effect" (or "Effect / When It Triggers") appearing as
a 3-line header directly under a banner title. Two non-standard subtables
(Languages, lines 633-712, and Appearance, lines 714-744) use different
column headers entirely (Language/Region, Level/Reaction Modifier) and no
skill in any of the 23 fixtures references them, so they are excluded by
exact line range rather than forced through the Name/Cost/Effect parser.
The "Seven Orthodox Gods Blessings" table (1098-1118) has no per-row cost
line at all (cost is stated once in prose, "each blessing costs 75
points") and is excluded the same way, by virtue of never producing a
cost-line match.

Some disadvantages (e.g. "Obsession", "Code of Honor", "Sense of Duty",
"Social Stigma", "Secret", "Blood Feud") print a variable point cost as a
range ("-5 to -10") rather than a single value -- the specific point cost
is chosen per-character within that range, so there is no single "the"
cost to report. Those entries carry costMin/costMax (both positive
magnitudes, consistent with the fixed-cost convention below) instead of
cost. Every entry has EITHER cost XOR (costMin AND costMax); never
neither -- a record with no cost representation at all is a hard parse
failure (see the assertion in extract_traits), not a silently-omitted
field.

Names almost always fit on one line, but wrap when long: "Church
Organisation" / "Informant" is a single advantage split across two lines
by the PDF's column width. These are rare (verified by dumping every
block of 3+ lines between cost lines and checking each one by hand) and
are listed explicitly in ADV_NAME_OVERRIDES / DIS_NAME_OVERRIDES below,
keyed by the line number of the record's cost line.

Record-boundary note (defect fixed here): the layout is
    Name
     Cost-or-Attr/Difficulty
    Notes (may wrap onto multiple lines, continuing right up to the next
    record's Name line)
so for any given cost/attr-diff line, the text immediately BEFORE it
(back to the previous cost/attr-diff line) is the CURRENT record's name,
but the text immediately AFTER it (up to the NEXT record's name) is also
the CURRENT record's notes -- not the next record's. The previous version
of this script computed that trailing span but attributed it to the
following record, shifting every entry's notes back by one position. The
fix below builds one block per cost line (name only) and then explicitly
assigns each block's leading "pre-name" text as the *previous* record's
notes, with the true last record's trailing text (to the end of the
extraction range) captured separately.
"""
import json, pathlib, re

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

FURNITURE = re.compile(r"^Powered by GURPS: Pathways|^Page \d+$")
WRAPPER_TITLE = re.compile(
    r"^(Step \d+:.*|Mundane Advantages|Supernatural Advantages"
    r"|Mundane Disadvantages|Supernatural Disadvantages)$"
)

# Languages (633-712) and Appearance (714-744) use non-standard table
# shapes (Language/Region columns; Level/Reaction Modifier columns) with
# no fixture ever referencing an entry from either. The "Seven Orthodox
# Gods Blessings" table (1098-1118) has no per-row cost line -- cost is
# stated once in prose ("each blessing costs 75 points") -- and uses a
# Deity/Effect shape instead of Name/Cost/Effect. All three are excluded
# by exact range rather than forced through the general parser.
ADV_EXCLUDE_RANGES = [(633, 744), (1098, 1118)]

DIFFICULTY_WORDS = {"Easy": "E", "Average": "A", "Hard": "H", "Very Hard": "VH"}


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def clean_stream(byn, start, end, exclude_ranges=()):
    out = []
    for n in range(start, end + 1):
        t = byn.get(n)
        if t is None:
            continue
        if FURNITURE.match(t.strip()):
            continue
        if any(a <= n <= b for a, b in exclude_ranges):
            continue
        out.append((n, t))
    return out


def strip_wrappers_and_banners(stream, header_word):
    """Remove banner titles + their 3-line header trio, and supercategory
    wrapper titles + their lead-in prose (which always run up to the next
    banner with zero cost lines in between -- verified against the data,
    not assumed)."""
    result = []
    i = 0
    n_items = len(stream)
    while i < n_items:
        n, t = stream[i]
        tstrip = t.strip()
        if i + 1 < n_items and stream[i + 1][1].strip() == header_word:
            i += 4  # banner title + 3-line header trio
            continue
        if WRAPPER_TITLE.match(tstrip):
            j = i + 1
            while j < n_items:
                if j + 1 < n_items and stream[j + 1][1].strip() == header_word:
                    break
                j += 1
            i = j
            continue
        result.append((n, t))
        i += 1
    return result


def cost_line_adv(t):
    t2 = t.strip()
    if t2.lower() == "varies":
        return True
    if re.match(r"^[+]?\d+\s+or\s+\d+\s*pts?(/level)?$", t2, re.IGNORECASE):
        return True
    return bool(re.match(r"^[+]?\d+(\s*[–—-]\s*\d+)?\s*pts?(/level)?$", t2, re.IGNORECASE))


def cost_line_dis(t):
    t2 = t.strip()
    return bool(re.match(r"^-\d+(\s*(to|–|—)\s*-?\d+)?\s*(pts?)?$", t2, re.IGNORECASE))


# Verified by hand against every block of 3+ lines between two cost lines
# in the advantages/disadvantages ranges (see module docstring): these are
# the only records whose printed name wraps across more than one line.
# Keyed by the global line number of the record's cost line.
ADV_NAME_OVERRIDES = {
    570: [568, 569],   # "Church Organisation" / "Informant"
    762: [760, 761],   # "Connections: Church Lay" / "Staff"
    767: [765, 766],   # "Connections: Press" / "Credentials"
}
DIS_NAME_OVERRIDES = {
    1415: [1413, 1414],  # "Compulsive Behavior" / "(Indulge Evil Desires) SC 6"
    1458: [1456, 1457],  # "Pathway Resonance" / "(Hostile)"
    1483: [1481, 1482],  # "Compulsion: Collection" / "(pathway)"
    1492: [1490, 1491],  # "Compulsion: Preserve the" / "Dead"
}

SELF_CONTROL_SUFFIX = re.compile(r"\s*\((\d+)\)$")  # e.g. "Curious (12)" -> "Curious"


def strip_self_control_number(name: str) -> str:
    return SELF_CONTROL_SUFFIX.sub("", name).strip()


# ---------------------------------------------------------------------------
# Self-control rolls (6am)
#
# Chapter 3 prints a self-control number in THREE places and this extractor
# read none of them:
#
#   A. inside the trait NAME  -- "Curious (12)", nine rows;
#      `strip_self_control_number` has deleted it since M0.
#   B. inside the effect cell -- "Must roll vs. 12 or investigate any
#      mystery encountered", "Must roll vs. Will-3 to reverse your stated
#      position" -- flat numbers and attribute offsets.
#   C. as a bare 3d6 line     -- "roll 3d6 <= 6 or indulge fully", exactly
#      one row, whose name carries "SC 6" instead of "(6)".
#
# Three printed spellings of one mechanic. None is merged into another:
# each has its own anchor, and verify() fails if any of the three stops
# being present.
#
# **The warrant for reading A is that five rows print BOTH A and B and all
# five agree exactly** (Curious, Greed, Bad Temper, Bully, Paranoia). That
# agreement is what licenses reading the bare number on the four rows --
# Overconfidence, Obsession, Nightmares, Hallucinations -- whose effect
# cell names no roll at all, so the name is the only place their number
# exists. Overconfidence is on 11 of the 23 premades. Without A those 11
# characters carry a printed number with no way to roll it. The same
# per-table standard the appendix's matching "Pi" had to meet (6v) and the
# artifacts' own "Seq N equivalent" headers met (6af): a join is licensed
# by rows that print both sides and reconcile, never by a source looking
# reliable.
#
# The anchor for B is "roll vs." and nothing looser. Two nearby phrasings
# would be swallowed by a rule that merely looked for a number to roll
# under, and neither is a self-control roll:
#   - Duty's "(roll 9 or less)" is how OFTEN the duty comes up, not a roll
#     made to resist it;
#   - Phobia's "Fright Check at -2" is a join to Chapter 5's own table.
# Both stay report-only, and verify() pins both sentences, so the narrow
# reading stays a decision rather than becoming an accident -- the guard
# the artifacts' two HP regexes needed (6af).
# ---------------------------------------------------------------------------

# "Must roll vs. 12 ...", "Must roll vs. Will-3 ...", "Roll vs. Will to ..."
NOTES_SELF_CONTROL = re.compile(
    r"\broll\s+vs\.\s*(?:(\d+)|(Will|IQ|HT|Per|ST|DX|SPI))\s*([+-]\s*\d+)?",
    re.IGNORECASE,
)

# The one row that prints the roll outright instead of naming a target.
NOTES_BARE_3D6 = re.compile(r"\broll\s+3d6\s*[≤<]=?\s*(\d+)")

# ---------------------------------------------------------------------------
# Spelling D -- a roll REQUIRED without the words "roll vs." (6bu)
#
# Five rows call for the same roll in five different word orders, and none
# uses B's phrasing:
#
#   Workaholic          "rolls to take a day off at Will-3"
#   Compulsive Gambling "requires Will roll at -2 to leave a game while ahead"
#   Bloodlust           "IQ roll necessary to accept a surrender"
#   Coldblooded         "Roll Will when suppressing evil desires"
#   Reluctant Killer    "Will rolls required to use violence again"
#
# **Workaholic is on two premades** (Factory Foreman, Private Investigator), so
# until this anchor existed the app printed the cell and offered no roll --
# 6bt's review found it while blocking Workaholic out of that slice.
#
# The warrant is NOT 6am's A/B agreement, which is unavailable: no row prints
# two of these spellings, so there is nothing to reconcile. What licenses it is
# that these cells add no new mechanic -- the same two target shapes (a flat
# number, or an attribute with a signed offset) and the same direction, where
# success is the character holding out. Five existing B rows already make
# success perform the resisting action (Stubborn "to reverse your stated
# position", Laziness "to undertake any sustained effort", Charity "to avoid
# helping", Reckless "to back down", Bad Temper "to avoid angry outbursts"), so
# no second outcome model is needed and `SelfControlOutcome.held` is unchanged.
# A design review's blocking finding: I had proposed a polarity field on the
# false premise that these rows inverted the direction.
#
# The provenance stays "effect" rather than gaining a value of its own.
# `Content.kt` REQUIRES source in {both, name, effect}, so a new value would
# fail content loading at construction -- and these are effect-cell spellings,
# which is exactly what "effect" already means.
#
# **The offset must belong to the TARGET, never to the roll.** That is the whole
# discriminator against ~80 modifier cells of near-identical shape:
#
#   Fit                 "+1 to all HT rolls"                   -- bonus TO the roll
#   Hard to Kill        "+1/level to HT rolls to avoid death"   -- bonus, plus an
#                                                                  infinitive clause
#   High Pain Threshold "+3 on HT rolls to avoid knockdown"     -- bonus
#   Incorruptible Will  "+3 to all Will rolls to resist ..."    -- bonus, and the
#                                       most self-control-shaped phrase in the book
#   Ether Body Damage   "All SPI-based rolls at -1"             -- penalty TO the roll
#   Grief-Stricken      "Will rolls to act clearly are at -2"   -- penalty TO the roll
#   Medical Training    "Use Physician at IQ-2"                 -- a skill DEFAULT
#   Marked by Ritual    "+2 to one type of SPI roll"            -- a bonus
#
# So a signed number introduced by "to"/"on"/"are at" is a modifier and a signed
# number bound to the attribute ("at Will-3", "Will roll at -2") is a target.
# Grief-Stricken is refused on exactly that reading -- its offset is presented
# as applying to a roll the GM already called for.
# ---------------------------------------------------------------------------

# "rolls to take a day off at Will-3", "requires Will roll at -2", "IQ roll
# necessary", "Roll Will when ...", "Will rolls required to ...". The attribute
# and its offset are adjacent; nothing here matches a bonus introduced by
# "to"/"on"/"are at".
NOTES_REQUIRED_ROLL = re.compile(
    r"(?:\broll\s+(?P<a1>Will|IQ|HT|Per|ST|DX|SPI)\b"
    r"|\b(?P<a2>Will|IQ|HT|Per|ST|DX|SPI)\s+rolls?\s+(?:at\s*(?P<o2>[+-]\s*\d+)\s*)?"
    r"(?:required|necessary)"
    r"|\brolls?\b[^.;]{0,40}?\bat\s+(?P<a3>Will|IQ|HT|Per|ST|DX|SPI)\s*(?P<o3>[+-]\s*\d+)?"
    r"|\b(?P<a4>Will|IQ|HT|Per|ST|DX|SPI)\s+roll\s+at\s*(?P<o4>[+-]\s*\d+))",
    re.IGNORECASE,
)

# Printed near a self-control number and deliberately NOT one of them. Each is
# pinned by verify(), so a narrow reading stays a decision rather than becoming
# an accident.
#
# **Three of these were invisible to every sweep this repo has run**, and the
# reason is not a regex slip: a Fright Check is a roll the book NAMES rather
# than describes, so no filter keyed on the word "roll" can see it. Only
# `Phobia (specify)` was ever pinned, and only because its cell happens to also
# say "-1 to all rolls". A census has to enumerate the mechanics the book has
# names for, which is what NAMED_MECHANICS below does.
NOT_SELF_CONTROL = (
    "roll 9 or less",       # Duty -- frequency of appearance
    "Fright Check at -2",   # Phobia (specify) -- a join to chapter 5
)

# Every row naming a mechanic the book has its OWN name for, and what must be
# true of it. None is a self-control roll, and each would have been a plausible
# wrong parse.
SELF_CONTROL_REQUIRED = {
    "Workaholic": {"attribute": "Will", "offset": -3},
    "Compulsive Gambling": {"attribute": "Will", "offset": -2},
    "Bloodlust": {"attribute": "IQ", "offset": 0},
    "Coldblooded": {"attribute": "Will", "offset": 0},
    "Reluctant Killer": {"attribute": "Will", "offset": 0},
}

NAMED_MECHANIC_REFUSALS = {
    "Phobia: Sacred Symbols": "Fright Check at -2",
    "Chronophobia (Temporal)": "Fright Check",
    "Soul Scar": "Fright Check failure",
    "Grief-Stricken": "Will rolls to act clearly are at -2",
    "Medical Training (Informal)": "at IQ-2",
    "Marked by Ritual": "SPI roll",
    "Natural Leader": "morale roll",
    "Uncanny Survivor": "death roll",
    # The modifier rows whose shape is closest to a target, and therefore the
    # ones the discriminator exists to exclude. **They were missing from this
    # set until a RED-proof exposed it**: widening the anchor to accept
    # "SPI-based rolls at -1" produced no failure, because verify() was only
    # pinning the rows I had thought of. A guard that does not cover the false
    # positive it was written for is not a guard.
    "Ether Body Damage": "All SPI-based rolls at -1",
    "Fit": "+1 to all HT rolls",
    "Hard to Kill": "HT rolls to avoid death",
    "High Pain Threshold": "HT rolls to avoid knockdown",
    "Alertness": "to all Per rolls",
    "Incorruptible Will": "Will rolls to resist",
}

ATTRIBUTE_SPELLING = {"will": "Will", "iq": "IQ", "ht": "HT",
                      "per": "Per", "st": "ST", "dx": "DX", "spi": "SPI"}


def parse_self_control(name_with_number: str, notes: str):
    """The self-control roll a disadvantage row prints, or None.

    `target` and `attribute` are mutually exclusive. A flat "vs. 12" is not
    an attribute roll, and keeping the two apart is what stops a potion
    that raises Will from quietly making Curious easier to resist.
    """
    in_name = SELF_CONTROL_SUFFIX.search(name_with_number)
    from_name = int(in_name.group(1)) if in_name else None

    from_effect = None
    m = NOTES_SELF_CONTROL.search(notes)
    if m:
        flat, attr, offset = m.group(1), m.group(2), m.group(3)
        off = int(offset.replace(" ", "")) if offset else 0
        if flat is not None:
            # An offset on a flat target is a shape the book never prints,
            # so reading one would be inventing arithmetic.
            if off:
                raise ValueError(
                    f"{name_with_number!r}: flat self-control target {flat} "
                    f"carries offset {offset!r}, which this book never prints"
                )
            from_effect = {"target": int(flat)}
        else:
            from_effect = {"attribute": ATTRIBUTE_SPELLING[attr.lower()], "offset": off}
    else:
        bare = NOTES_BARE_3D6.search(notes)
        if bare:
            from_effect = {"target": int(bare.group(1))}
        else:
            # Spelling D. Tried last, so a row printing "roll vs." is never
            # read by the looser anchor -- B stays the narrow reading it was
            # built to be, and D only ever answers where B found nothing.
            req = NOTES_REQUIRED_ROLL.search(notes)
            if req:
                attr = req.group("a1") or req.group("a2") or req.group("a3") or req.group("a4")
                raw = req.group("o2") or req.group("o3") or req.group("o4")
                from_effect = {
                    "attribute": ATTRIBUTE_SPELLING[attr.lower()],
                    "offset": int(raw.replace(" ", "")) if raw else 0,
                }

    if from_name is not None and from_effect is not None:
        # The five rows that print it twice. They agree today; if a later
        # reading ever splits them that is a finding, not something to
        # average away.
        if from_effect.get("target") != from_name:
            raise ValueError(
                f"{name_with_number!r}: name prints self-control {from_name} "
                f"but the effect cell prints {from_effect!r}; the book "
                f"disagrees with itself and that must be recorded, not merged"
            )
        return {"target": from_name, "source": "both"}
    if from_name is not None:
        return {"target": from_name, "source": "name"}
    if from_effect is not None:
        return {**from_effect, "source": "effect"}
    return None


def parse_adv_cost(t):
    t2 = t.strip()
    per_level = "/level" in t2.lower()
    body = t2.lower().replace("/level", "").strip()
    if body == "varies":
        return {"variableCost": True, "perLevel": per_level}
    body = t2.replace("/level", "").replace("pts", "").replace("pt", "").strip()
    m = re.match(r"^\+?(\d+)\s+or\s+(\d+)$", body, re.IGNORECASE)
    if m:
        return {"costMin": int(m.group(1)), "costMax": int(m.group(2)), "perLevel": per_level}
    m = re.match(r"^\+?(\d+)\s*[–—-]\s*(\d+)$", body)
    if m:
        return {"costMin": int(m.group(1)), "costMax": int(m.group(2)), "perLevel": per_level}
    m = re.match(r"^\+?(\d+)$", body)
    if m:
        return {"cost": int(m.group(1)), "perLevel": per_level}
    raise ValueError(f"unparsed advantage cost: {t!r}")


def parse_dis_cost(t):
    body = t.strip().replace("pts", "").replace("pt", "").strip()
    m = re.match(r"^-(\d+)\s*(?:to|–|—)\s*-?(\d+)$", body, re.IGNORECASE)
    if m:
        lo, hi = int(m.group(1)), int(m.group(2))
        return {"costMin": min(lo, hi), "costMax": max(lo, hi)}
    m = re.match(r"^-(\d+)$", body)
    if m:
        return {"cost": int(m.group(1))}
    raise ValueError(f"unparsed disadvantage cost: {t!r}")


def _require_cost_shape(entry, cost_n):
    has_single = "cost" in entry
    has_range = "costMin" in entry and "costMax" in entry
    if not has_single and not has_range and not entry.get("variableCost"):
        raise ValueError(
            f"record ending at cost line {cost_n} has no cost representation "
            f"at all (name={entry.get('name')!r}); refusing to write a silent gap"
        )


def extract_traits(byn, start, end, header_word, cost_fn, cost_parser, overrides, exclude_ranges=()):
    stream = clean_stream(byn, start, end, exclude_ranges)
    stream = strip_wrappers_and_banners(stream, header_word)
    cost_idx = [i for i, (n, t) in enumerate(stream) if cost_fn(t)]

    # Pass 1: one block per cost line, containing ONLY that record's name
    # (the last line(s) before its cost line) and the "pre-name" text that
    # precedes it, which actually belongs to the PREVIOUS record's notes
    # (see docstring). We deliberately do not attach pre_name_lines to the
    # record being built here.
    blocks = []
    prev_end = -1
    for ci in cost_idx:
        block = stream[prev_end + 1:ci]
        if not block:
            raise ValueError(f"no name found before cost line {stream[ci][0]}")
        cost_n, cost_t = stream[ci]
        override = overrides.get(cost_n)
        if override:
            name_lines = [(n, t) for n, t in block if n in override]
            pre_name_lines = [(n, t) for n, t in block if n not in override]
        else:
            name_lines = [block[-1]]
            pre_name_lines = block[:-1]
        blocks.append({
            "name_lines": name_lines,
            "pre_name_lines": pre_name_lines,  # notes for the PREVIOUS record
            "cost_n": cost_n,
            "cost_t": cost_t,
        })
        prev_end = ci

    # Trailing text after the last cost line belongs to the last record's
    # own notes (it has no "next name line" to be mistaken for).
    trailing_notes_lines = stream[prev_end + 1:]

    entries = []
    spans = []
    for i, b in enumerate(blocks):
        printed_name = " ".join(t.strip() for _, t in b["name_lines"])
        name = strip_self_control_number(printed_name)
        cost_fields = cost_parser(b["cost_t"])
        notes_lines = blocks[i + 1]["pre_name_lines"] if i + 1 < len(blocks) else trailing_notes_lines
        notes = " ".join(t.strip() for _, t in notes_lines)

        entry = {"name": name, **cost_fields, "notes": notes}
        # Disadvantages only. Self-control is a disadvantage mechanic and no
        # advantage row prints a "(N)" name; reading advantage prose for
        # "roll vs." would invent a resistance roll for a thing you bought.
        if header_word == "Disadvantage":
            # Read from the PRINTED name, before the number is stripped off.
            self_control = parse_self_control(printed_name, notes)
            if self_control is not None:
                entry["selfControl"] = self_control
        _require_cost_shape(entry, b["cost_n"])
        entries.append(entry)

        first_line = b["name_lines"][0][0]
        last_line = notes_lines[-1][0] if notes_lines else b["cost_n"]
        spans.append([first_line, last_line])

    spans.sort()
    merged = []
    for a, bb in spans:
        if merged and a <= merged[-1][1] + 1:
            merged[-1][1] = max(merged[-1][1], bb)
        else:
            merged.append([a, bb])

    return entries, merged


SKILL_ATTR_DIFF = re.compile(
    r"^(ST|DX|IQ|HT|Will|Per|SPI)/(Easy|Average|Hard|Very Hard|Wildcard)$"
)
SKILL_BANNER = re.compile(r"^— .+ —$")

SKILL_HEADER_WORD = "Skill"
SKILL_SECTION_END = 2181  # "Study Points (Optional Rule)" appendix begins here;
                           # different table shape, no fixture references it.

# The fifteen banners the Complete Skill List is printed under. They were
# DISCARDED for four milestones -- the loop below used to do `i += 1` on a
# banner and move on -- so a trait cell reading "+3 to social skill rolls"
# named a group this book prints and nothing could identify it. The same
# "a normaliser can delete a rule" shape as strip_self_control_number.
#
# Stored VERBATIM rather than slugged: "SPIRITUAL SKILLS (SPI-based)" carries
# the book's own parenthetical, and normalising these into tidy keys is how the
# next reader loses the ability to grep the page they came from.
SOCIAL_SKILLS = "SOCIAL SKILLS"
SKILL_CATEGORIES = {
    "COMBAT SKILLS",
    SOCIAL_SKILLS,
    "KNOWLEDGE SKILLS",
    "THIEF SKILLS",
    "PHYSICAL SKILLS",
    "CRAFT & TECHNICAL",
    "SPIRITUAL SKILLS (SPI-based)",
    "SURVIVAL & OTHER",
    "INVESTIGATION & DETECTION",
    "PERFORMANCE & ARTS",
    "ACADEMIC",
    "TRADE & CRAFT",
    "ANIMALS & OUTDOORS",
    "UNDERWORLD & COVERT",
    "MILITARY & NAVAL",
}

# The sixteen skills under SOCIAL_SKILLS. This membership IS the join that
# applies "social skill rolls", so it is pinned rather than trusted: a row
# leaving the set silently withdraws a printed bonus, and a row joining it
# silently invents one.
SOCIAL_SKILL_NAMES = {
    "Fast-Talk", "Intimidation", "Diplomacy", "Public Speaking", "Acting",
    "Leadership", "Detect Lies", "Interrogation", "Observation", "Psychology",
    "Savoir-Faire", "Sex Appeal", "Panhandling", "Disguise", "Forgery", "Holdout",
}


def clean_skill_stream(byn, start, end):
    out = []
    for n in range(start, end + 1):
        t = byn.get(n)
        if t is None:
            continue
        if FURNITURE.match(t.strip()):
            continue
        out.append((n, t))
    return out


def strip_skill_structure(stream):
    """Drop the table's furniture, and return which banner each line sits under.

    The category comes back as a separate line -> banner map rather than as a
    third element of every tuple: the record-boundary walk below indexes these
    tuples in four places, and widening them there would be a bigger change
    than the rule warrants.
    """
    result = []
    category_by_line = {}
    category = None
    i = 0
    n_items = len(stream)
    while i < n_items:
        n, t = stream[i]
        tstrip = t.strip()
        if tstrip == "Complete Skill List by Category":
            i += 4  # title + " Skill" / " Attr / Difficulty" / " Notes"
            continue
        if SKILL_BANNER.match(tstrip):
            category = tstrip.strip("—").strip()
            i += 1  # category banner only; no per-category header repeats
            continue
        category_by_line[n] = category
        result.append((n, t))
        i += 1
    return result, category_by_line


SPECIFY_PAREN = re.compile(r"\s*\((specify[^)]*|any)\)$", re.IGNORECASE)


def parse_skill_name(raw: str):
    """'Guns (Pistol)' -> ('Guns', 'Pistol'); 'Law (specify)' -> ('Law', None)."""
    m = SPECIFY_PAREN.search(raw)
    if m:
        return raw[: m.start()].strip(), None
    m = re.match(r"^(.*?)\s*\((.*)\)\s*$", raw.strip())
    if m:
        return m.group(1).strip(), m.group(2).strip()
    return raw.strip(), None


def is_specializable(raw_name: str, notes: str) -> bool:
    """A skill is specializable when the book marks it with a
    "(specify...)" style suffix -- most often at the end of the notes
    ("Specific region (specify)" for Area Knowledge, "Specific secret
    knowledge (specify type)" for Hidden Lore), but occasionally directly
    on the name line itself ("Law (specify)", "Connoisseur (specify)").
    Detected from the text in either place, not from a hardcoded list of
    skill names."""
    return bool(SPECIFY_PAREN.search(raw_name.strip()) or SPECIFY_PAREN.search(notes.strip()))


def extract_skills(byn):
    stream = clean_skill_stream(byn, 1664, SKILL_SECTION_END - 1)
    stream, category_by_line = strip_skill_structure(stream)
    cost_idx = [i for i, (n, t) in enumerate(stream) if SKILL_ATTR_DIFF.match(t.strip())]

    # Same record-boundary fix as extract_traits: build one block per
    # attr/difficulty line (name only), then assign each block's
    # "pre-name" text to the PREVIOUS record's notes.
    blocks = []
    prev_end = -1
    for ci in cost_idx:
        block = stream[prev_end + 1:ci]
        if not block:
            raise ValueError(f"no name found before skill attr/diff line {stream[ci][0]}")
        name_lines = [block[-1]]
        pre_name_lines = block[:-1]
        cost_n, cost_t = stream[ci]
        blocks.append({
            "name_lines": name_lines,
            "pre_name_lines": pre_name_lines,
            "cost_n": cost_n,
            "cost_t": cost_t,
        })
        prev_end = ci

    trailing_notes_lines = stream[prev_end + 1:]

    entries = []
    spans = []
    skipped = []
    for i, b in enumerate(blocks):
        cost_n, cost_t = b["cost_n"], b["cost_t"]
        attr_code, diff_word = cost_t.strip().split("/")
        raw_name = b["name_lines"][0][1].strip()
        notes_lines = blocks[i + 1]["pre_name_lines"] if i + 1 < len(blocks) else trailing_notes_lines

        if diff_word == "Wildcard":
            # Difficulty is a closed Easy/Average/Hard/Very Hard enum;
            # "Inventor!" (IQ/Wildcard) has no representation and is
            # skipped rather than forced into a wrong bucket.
            skipped.append((cost_n, raw_name, "Wildcard difficulty has no Difficulty enum value"))
            continue

        name, spec = parse_skill_name(raw_name)
        notes = " ".join(t.strip() for _, t in notes_lines)
        first_line = b["name_lines"][0][0]
        last_line = notes_lines[-1][0] if notes_lines else cost_n
        # The banners partition the list, so a record with none means the walk
        # started above the first banner and the section bounds have moved.
        # Refused rather than defaulted: a skill silently filed under no
        # category withdraws every category-scoped rule from it in silence.
        category = category_by_line.get(first_line)
        if category is None:
            raise ValueError(f"line {first_line}: {name!r} sits under no category banner")
        entries.append({
            "name": name,
            "specialization": spec,
            "attr": attr_code,
            "difficulty": DIFFICULTY_WORDS[diff_word],
            "specializable": is_specializable(raw_name, notes),
            "category": category,
            "notes": notes,
        })
        spans.append([first_line, last_line])

    spans.sort()
    merged = []
    for a, bb in spans:
        if merged and a <= merged[-1][1] + 1:
            merged[-1][1] = max(merged[-1][1], bb)
        else:
            merged.append([a, bb])

    return entries, merged, skipped


def write_content_file(name, entries, spans):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {
        "sourceLines": {"3": spans} if spans else {},
        "entries": entries,
    }
    (OUT / f"{name}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def extract_thrust_swing():
    from openpyxl import load_workbook
    import glob, os

    src_dir = pathlib.Path(os.path.expanduser("~/Downloads/LOTM TTRPG Sheets"))
    fixtures = sorted(glob.glob(str(src_dir / "*.xlsx")))
    fixtures = [f for f in fixtures if not pathlib.Path(f).name.startswith("~$")]
    if not fixtures:
        raise SystemExit("no source .xlsx sheets found to read ThrustTable from")

    # ThrustTable is byte-identical across all 23 fixtures (per Task 10
    # brief); read the first one but verify against a second to be sure
    # rather than trusting that claim blindly.
    def read_table(path):
        ws = load_workbook(str(path), data_only=True)["ThrustTable"]
        grid = [list(r) for r in ws.iter_rows(values_only=True)]
        rows = []
        for row in grid:
            if len(row) < 4:
                continue
            st, thrust, swing = row[1], row[2], row[3]
            if not isinstance(st, (int, float)):
                continue
            rows.append({"st": int(st), "thrust": str(thrust).strip(), "swing": str(swing).strip()})
        return rows

    first = read_table(fixtures[0])
    second = read_table(fixtures[-1])
    if first != second:
        raise ValueError(
            f"ThrustTable differs between {fixtures[0]} and {fixtures[-1]}; "
            "not byte-identical as assumed, need per-row reconciliation"
        )
    if len(first) != 30:
        raise ValueError(f"expected 30 ST rows, got {len(first)}")
    return first


# The five rows that print their self-control number in BOTH the name and
# the effect cell. Their agreement is the entire warrant for reading the
# bare number on the four rows that print it only in the name.
SELF_CONTROL_IN_BOTH = {"Curious": 12, "Greed": 12, "Bad Temper": 12,
                        "Bully": 12, "Paranoia": 12}
# Printed as "(12)" and NOWHERE else -- the effect cell names no roll at
# all, so without the name the rule has no number. Overconfidence is on 11
# of the 23 premades.
SELF_CONTROL_IN_NAME_ONLY = {"Overconfidence": 12, "Obsession": 12,
                             "Nightmares": 12, "Hallucinations": 12}
# The third spelling: neither "(N)" nor "roll vs.", but the roll itself.
SELF_CONTROL_BARE_3D6 = "Compulsive Behavior (Indulge Evil Desires) SC 6"


def verify(dis_entries, adv_entries=()):
    """Fail the extract if the self-control reading loses its evidence.

    Both catalogues, because NAMED_MECHANIC_REFUSALS spans them: a morale roll
    and a death roll are printed on ADVANTAGES, and a guard that could not see
    them is a guard that misses the mechanics it exists to enumerate.
    """
    byname = {e["name"]: e for e in list(dis_entries) + list(adv_entries)}

    for name, target in SELF_CONTROL_IN_BOTH.items():
        sc = byname[name].get("selfControl")
        if sc != {"target": target, "source": "both"}:
            raise ValueError(
                f"{name}: expected the name and the effect cell to agree on "
                f"{target}; got {sc!r}. That agreement is the only warrant "
                f"for reading the bare number on {sorted(SELF_CONTROL_IN_NAME_ONLY)}"
            )

    for name, target in SELF_CONTROL_IN_NAME_ONLY.items():
        sc = byname[name].get("selfControl")
        if sc != {"target": target, "source": "name"}:
            raise ValueError(f"{name}: expected name-only self-control {target}, got {sc!r}")
        # The claim that makes this row interesting: its effect cell really
        # does name no roll. If one ever gains a "roll vs.", it becomes a
        # "both" row and the warrant above gets stronger, not weaker -- but
        # it must be noticed rather than silently reclassified.
        if "roll vs." in byname[name]["notes"].lower():
            raise ValueError(
                f"{name}: effect cell now names a roll; it is no longer a "
                f"name-only row and SELF_CONTROL_IN_NAME_ONLY must be re-read"
            )

    sc = byname[SELF_CONTROL_BARE_3D6].get("selfControl")
    if sc != {"target": 6, "source": "effect"}:
        raise ValueError(f"{SELF_CONTROL_BARE_3D6}: expected bare-3d6 target 6, got {sc!r}")

    # Three spellings, three anchors, none merged into another.
    sources = {e["selfControl"]["source"] for e in dis_entries if "selfControl" in e}
    if sources != {"both", "name", "effect"}:
        raise ValueError(f"expected all three printed spellings to survive; got {sources}")

    # An attribute row, to pin that the offset is read and signed correctly.
    if byname["Stubborn"].get("selfControl") != {
        "attribute": "Will", "offset": -3, "source": "effect"
    }:
        raise ValueError(f"Stubborn: got {byname['Stubborn'].get('selfControl')!r}")
    if byname["Impulsive"].get("selfControl") != {
        "attribute": "IQ", "offset": -2, "source": "effect"
    }:
        raise ValueError(f"Impulsive: got {byname['Impulsive'].get('selfControl')!r}")
    # A bare attribute with no offset is offset 0, not a missing field.
    if byname["Loner"].get("selfControl") != {
        "attribute": "Will", "offset": 0, "source": "effect"
    }:
        raise ValueError(f"Loner: got {byname['Loner'].get('selfControl')!r}")

    # The two phrasings the narrow anchor deliberately refuses. Both print a
    # number to roll under and neither is a self-control roll, so if either
    # sentence goes the narrow reading has lost the reason it is narrow.
    duty = byname["Duty (specify)"]
    if NOT_SELF_CONTROL[0] not in duty["notes"]:
        raise ValueError("Duty no longer prints 'roll 9 or less'; re-read the anchor")
    if "selfControl" in duty:
        raise ValueError("Duty's frequency roll was read as a self-control roll")
    phobia = byname["Phobia (specify)"]
    if NOT_SELF_CONTROL[1] not in phobia["notes"]:
        raise ValueError("Phobia no longer prints 'Fright Check at -2'; re-read the anchor")
    if "selfControl" in phobia:
        raise ValueError("Phobia's Fright Check was read as a self-control roll")

    # Spelling D's five rows, each in a different word order (6bu).
    for name, expected in SELF_CONTROL_REQUIRED.items():
        sc = byname[name].get("selfControl")
        if sc != {**expected, "source": "effect"}:
            raise ValueError(f"{name}: expected required-roll {expected}, got {sc!r}")
        # ...and none of them prints B's phrasing, which is the whole reason a
        # fourth anchor exists. If one ever does it becomes a "both"-style
        # agreement row and the warrant gets stronger -- but it must be noticed.
        if "roll vs." in byname[name]["notes"].lower():
            raise ValueError(
                f"{name} now prints 'roll vs.'; it is no longer a spelling-D row "
                f"and SELF_CONTROL_REQUIRED must be re-read"
            )

    # Every row naming a mechanic this book has its own name for. None is a
    # self-control roll, and three of them were invisible to every sweep this
    # repo has run, because a Fright Check is a roll the book NAMES rather than
    # describes -- no filter keyed on the word "roll" can see it.
    for name, phrase in NAMED_MECHANIC_REFUSALS.items():
        row = byname.get(name) or {}
        if not row:
            raise ValueError(f"{name} is no longer printed; re-read the refusal set")
        if phrase not in row["notes"]:
            raise ValueError(f"{name} no longer prints {phrase!r}; re-read the refusal")
        if "selfControl" in row:
            raise ValueError(
                f"{name}: {phrase!r} was read as a self-control roll. It is a "
                f"mechanic the book names separately, or a modifier to a roll "
                f"that already exists"
            )


def verify_skills(skill_entries):
    """Fail the extract if the category banners stop being what they were read as.

    Two claims, and the second is the load-bearing one. The banners PARTITION
    the list, so every row has exactly one; and the SOCIAL banner's membership
    is the entire join that applies a cell saying "social skill rolls", so a
    row leaving or joining that set changes a rule rather than a label.
    """
    found = {e["category"] for e in skill_entries}
    if found != SKILL_CATEGORIES:
        raise ValueError(
            f"skill category banners changed: missing {SKILL_CATEGORIES - found}, "
            f"unexpected {found - SKILL_CATEGORIES}"
        )
    social = {e["name"] for e in skill_entries if e["category"] == SOCIAL_SKILLS}
    if social != SOCIAL_SKILL_NAMES:
        raise ValueError(
            f"the SOCIAL banner's membership changed: left {SOCIAL_SKILL_NAMES - social}, "
            f"joined {social - SOCIAL_SKILL_NAMES}. That set is what applies every "
            f'"social skill rolls" cell, so the reading must be made again'
        )
    # Streetwise is the evidence that Charisma's bracketed list is its own
    # enumeration and not a reference to the SOCIAL banner. If the book ever
    # moved it, that reading would have to be re-made rather than inherited.
    streetwise = next(e for e in skill_entries if e["name"] == "Streetwise")
    if streetwise["category"] != "KNOWLEDGE SKILLS":
        raise ValueError(
            f"Streetwise is now {streetwise['category']!r}; Charisma's bracket "
            f"was read as its own list BECAUSE Streetwise sat outside SOCIAL"
        )


def main():
    byn = load_lines()

    adv_entries, adv_spans = extract_traits(
        byn, 530, 1118, "Advantage", cost_line_adv, parse_adv_cost,
        ADV_NAME_OVERRIDES, ADV_EXCLUDE_RANGES,
    )
    dis_entries, dis_spans = extract_traits(
        byn, 1119, 1569, "Disadvantage", cost_line_dis, parse_dis_cost,
        DIS_NAME_OVERRIDES,
    )
    verify(dis_entries, adv_entries)
    skill_entries, skill_spans, skipped_skills = extract_skills(byn)
    verify_skills(skill_entries)
    thrust_swing_rows = extract_thrust_swing()

    write_content_file("advantages", adv_entries, adv_spans)
    write_content_file("disadvantages", dis_entries, dis_spans)
    write_content_file("skills", skill_entries, skill_spans)
    write_content_file("thrust_swing", thrust_swing_rows, [])

    print(f"advantages: {len(adv_entries)} entries, spans={adv_spans}")
    print(f"disadvantages: {len(dis_entries)} entries, spans={dis_spans}")
    print(f"skills: {len(skill_entries)} entries, spans={skill_spans}")
    if skipped_skills:
        print(f"skills skipped ({len(skipped_skills)}):")
        for n, name, reason in skipped_skills:
            print(f"  line {n}: {name!r} -- {reason}")
    print(f"thrust_swing: {len(thrust_swing_rows)} rows")


if __name__ == "__main__":
    main()
