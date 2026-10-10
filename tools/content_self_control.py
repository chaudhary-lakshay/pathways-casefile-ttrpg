"""Self-control rolls read out of the disadvantage table's cells, and the
check that fails the extract when a reading drifts. Split out of
extract_content.py, which calls both."""
import re


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
