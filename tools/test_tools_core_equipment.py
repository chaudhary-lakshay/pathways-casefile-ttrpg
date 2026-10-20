"""Core rules, blessings, equipment, secondary costs and the appendix's second printings. Split out of test_tools.py."""
import json, re, subprocess, sys

import pytest

import extract_appendix

from testsupport import CONTENT, OUT, ROOT, needs_pdf


import extract_core_rules


def core_entries(name):
    return json.loads((CONTENT / f"{name}.json").read_text(encoding="utf-8"))["entries"]


def test_chapter_two_prints_two_burnable_pools():
    """v6.9i: neither pool is capped any more ("Max +5" is gone), and each
    is gated -- FP by Fit / Very Fit, SPI to combat. The gates are stored as
    data; the cap is absent rather than a large number."""
    fp, spi = core_entries("effort_burn")
    assert (fp["pool"], fp["kind"]) == ("FP", "physical")
    assert (spi["pool"], spi["kind"]) == ("SPI", "mystical")
    assert "max" not in fp and "max" not in spi
    assert fp["perPoint"] == spi["perPoint"] == 1
    assert fp["combatTraits"] == ["Fit", "Very Fit"]
    assert fp["outOfCombatTraits"] == ["Very Fit"]
    assert spi["combatTraits"] == [] and "outOfCombatTraits" not in spi


def test_both_burn_scopes_survive_their_wrap():
    """FP's scope cell keeps its bracketed example list whole."""
    fp, spi = core_entries("effort_burn")
    for row in (fp, spi):
        assert row["scope"].count("(") == row["scope"].count(")"), row["scope"]
    assert fp["scope"].endswith("lifting, etc.)"), fp["scope"]
    assert spi["scope"] == "any SPI-based or supernatural skill roll"


def test_chapter_two_tabulates_the_margin_bands_appendix_a_names():
    """The correction to 6ad.

    That section said chapter 2 "tabulates nothing". It tabulates the same
    three edges -- 0-2, 3-4, 5+ -- under different names. The bulleted list was
    invisible because this PDF prints list bullets as U+007F. Only Appendix A
    NAMES the bands, which is what makes it a definition rather than a
    summary, and that asymmetry is asserted here so a later reading cannot
    quietly erase it.
    """
    success = [b for b in core_entries("core_mechanic") if b["outcome"] == "Success"]
    assert [b["low"] for b in success] == [0, 3, 5]
    assert [b.get("high") for b in success] == [2, 4, None]
    ladder = core_entries("success_ladder")
    edges = sorted({-(e.get("offset") or 0) for e in ladder if e.get("comparison") == "<="})
    assert edges == [0, 3, 5]
    named = {e["result"] for e in ladder}
    assert {"Good Success", "Excellent Success"} <= named
    assert not any("Good Success" in b["meaning"] for b in success)


def test_very_hard_prints_a_refusal_and_not_a_number():
    """"No default -- cannot attempt untrained" is not a large penalty."""
    defs = core_entries("skill_defaults")
    assert [d["code"] for d in defs] == ["E", "A", "H", "VH"]
    assert [d.get("penalty") for d in defs] == [-4, -5, -6, None]
    assert "penalty" not in defs[-1]


def test_the_six_quick_contests_survive_their_trailing_paragraph():
    """The table ends where the resolution paragraph begins, by MARKER.

    A wrapped sentence -- "...if the situation" / "allows. If one succeeds..."
    -- reads as two more cells to anything that counts rows instead.
    """
    rows = core_entries("quick_contests")
    assert len(rows) == 6
    assert rows[0]["situation"] == "Deception vs. detection"
    assert any(r["attacker"] == "Ritualistic Magic" for r in rows)
    assert not any("allows." in r["situation"] for r in rows)


@needs_pdf
def test_the_core_rules_verify_against_the_book():
    bands, pools, rows, defs, secs = extract_core_rules.extract()
    extract_core_rules.verify(
        bands, pools, rows, defs, secs, extract_core_rules.load_lines())


@needs_pdf
def test_a_burn_cap_coming_back_is_refused():
    """v6.9i deleted v6.9d's "Max +5" on FP; a cap reappearing is a finding."""
    bands, pools, rows, defs, secs = extract_core_rules.extract()
    byn = dict(extract_core_rules.load_lines())
    byn[463] = byn[463] + " Max +5 per roll."
    with pytest.raises(AssertionError, match="burn cap"):
        extract_core_rules.verify(bands, pools, rows, defs, secs, byn)


# --- The Seven Orthodox Gods Blessings (Ch. 3, p. 19) ----------------------

import extract_blessings as ebl


def _blessing_lines():
    """The table's row range, as the book prints it."""
    return {
        1184: "Goddess of Evernight",
        1185: "Dream Lucidity, Concealment. Most divination fails against you.",
        1186: "God of Combat",
        1187: "Herculean strength & catlike reflexes. +3 ST, +3 DX, +3 HT.",
    }


def test_a_wrapped_effect_cell_stays_with_its_deity():
    """A deity name carries no full stop and an effect cell ends with one, so
    a continuation line joins the row above rather than opening a new one.
    Counting two lines per row works today and breaks on the first wrap."""
    byn = dict(_blessing_lines())
    byn[1187] = "Herculean strength & catlike reflexes."
    byn[1188] = "+3 ST, +3 DX, +3 HT."
    rows = ebl.parse(byn)
    assert [r["deity"] for r in rows] == ["Goddess of Evernight", "God of Combat"]
    assert rows[1]["effect"].endswith("+3 ST, +3 DX, +3 HT.")


def test_an_effect_that_lost_its_full_stop_fails_rather_than_shifting_the_table():
    """The loud failure the shape rule buys: an effect read as an eighth deity
    changes the row count, which verify() refuses -- rather than shipping a
    table whose cells have all moved up one."""
    byn = dict(_blessing_lines())
    byn[1187] = "Herculean strength & catlike reflexes, +3 ST, +3 DX, +3 HT"
    rows = ebl.parse(byn)
    assert len(rows) == 3  # the effect became a deity of its own


@needs_pdf
def test_the_price_has_no_source_if_its_one_sentence_goes():
    """The table prints no cost column at all -- the price is stated once, in
    prose, for all seven rows. That sentence is the only place it exists."""
    byn = ebl.load_lines()
    payload = ebl.build(byn)
    broken = dict(byn)
    broken[1178] = "The Seven Orthodox Churches bless their faithful."
    with pytest.raises(ValueError, match="75-point sentence"):
        ebl.verify(broken, payload, [], [], _knowledge_skills())


def _knowledge_skills():
    return [{"name": "Occultism", "category": ebl.KNOWLEDGE_BANNER}]


@needs_pdf
def test_the_slash_reading_fails_the_day_the_book_prints_occult_skills():
    """THE WARRANT. "occult/knowledge skills" is read as chapter 3's KNOWLEDGE
    banner because "occult skill" is printed nowhere -- one half of the slash
    names no group at all. If the book ever prints it, the slash may be
    joining two groups and the reading has to be made again."""
    byn = ebl.load_lines()
    payload = ebl.build(byn)
    ebl.verify(byn, payload, [], [], _knowledge_skills())  # green as committed
    widened = dict(byn)
    widened[max(byn) + 1] = "Add +1 to all occult skills."
    with pytest.raises(ValueError, match="occult skill"):
        ebl.verify(widened, payload, [], [], _knowledge_skills())


@needs_pdf
def test_a_deity_that_is_also_a_catalogue_trait_is_a_second_printing():
    """Disjointness, the check extract_appearance.py already makes: a name in
    both lists would make matching a bare name ambiguous, and would mean this
    table restates rows another file already carries."""
    byn = ebl.load_lines()
    payload = ebl.build(byn)
    clash = [{"name": "Earth Mother"}]
    with pytest.raises(ValueError, match="also a catalogue trait"):
        ebl.verify(byn, payload, clash, [], _knowledge_skills())


@needs_pdf
def test_the_two_mechanical_cells_are_pinned_exactly():
    """The screen quotes these, and a re-cut cell still reads plausibly."""
    byn = ebl.load_lines()
    payload = ebl.build(byn)
    payload["entries"] = [dict(e) for e in payload["entries"]]
    row = next(e for e in payload["entries"] if e["deity"] == "God of Combat")
    row["effect"] = "Herculean strength & catlike reflexes. +2 ST, +2 DX, +2 HT."
    with pytest.raises(ValueError, match="God of Combat no longer reads"):
        ebl.verify(byn, payload, [], [], _knowledge_skills())


import extract_equipment


def equipment_entries():
    return json.loads((CONTENT / "equipment.json").read_text(encoding="utf-8"))["entries"]


def equipment_named(name):
    return next(e for e in equipment_entries() if e["name"] == name)


def test_the_equipment_table_keeps_its_four_categories_in_order():
    entries = equipment_entries()
    assert len(entries) == 77
    seen = list(dict.fromkeys(e["category"] for e in entries))
    assert seen == extract_equipment.CATEGORIES
    assert all(e["name"] and e["cost"] and e["notes"] for e in entries)


def test_the_notes_cell_is_verbatim_and_no_modifier_is_derived():
    """The bonus is never applied, so nothing parses a number out of the cell.
    A `modifier` field would be a value with no consumer -- and the seed of
    stacking two disguise kits and inverting a penalty aimed at the observer."""
    assert all(set(e) == {"name", "cost", "category", "notes", "skills"}
               for e in equipment_entries())
    chest = equipment_named("Portable medicine chest")
    assert chest["notes"] == "Field hospital; +2 to First Aid & Surgery; 50 doses"


def test_a_mention_is_not_a_signed_clause():
    """"Lock picks (set)" says "Required for Lockpicking skill" and grants
    nothing. A substring matcher would offer it on every Lockpicking roll as
    though it helped; only a SIGNED clause joins."""
    picks = equipment_named("Lock picks (set)")
    assert "Lockpicking" in picks["notes"]
    assert picks["skills"] == []
    # And the row one line below it, which does carry a sign.
    assert equipment_named("Lockpick set (basic)")["skills"] == [
        {"name": "Lockpicking", "specialization": None}
    ]


@needs_pdf
def test_loosening_the_predicate_into_a_mention_fails_verification():
    """The guard is load-bearing: if the matcher ever resolved a mention, the
    lock picks would join Lockpicking and verify() must refuse the run."""
    index = extract_equipment.skill_index()
    tagged = extract_equipment.tag_categories(
        extract_equipment.stream(extract_equipment.load_lines(),
                                 *extract_equipment.EQUIPMENT_RANGE)
    )
    rows = extract_equipment.parse(tagged, index)
    patched = [
        {**r, "skills": [{"name": "Lockpicking", "specialization": None}]}
        if r["name"] == "Lock picks (set)" else r
        for r in rows
    ]
    with pytest.raises(ValueError, match="Lock picks"):
        extract_equipment.verify(patched, index)


def test_one_bonus_may_name_two_skills_and_another_offers_a_choice():
    """The book joins with "&" and chooses with "or", on the same page. Both
    halves of a conjunction join; so does the resolvable half of a choice."""
    assert equipment_named("Portable medicine chest")["skills"] == [
        {"name": "First Aid", "specialization": None},
        {"name": "Surgery", "specialization": None},
    ]
    assert equipment_named("Holy symbol (quality)")["skills"] == [
        {"name": "Ritualistic Magic", "specialization": None},
        {"name": "Religious Ritual", "specialization": None},
    ]
    # "+1 to Fortune-Telling or Psychology" -- only the second half is printed
    # in chapter 3's list, under that spelling.
    assert equipment_named("Crystal ball (glass)")["skills"] == [
        {"name": "Psychology", "specialization": None}
    ]


def test_the_condition_is_trimmed_before_the_target_is_split():
    """"+1 to First Aid on burns & rashes" is one skill, not two. Splitting on
    "&" before trimming the condition would claim a skill called "rashes"."""
    assert equipment_named("Herbal salves (3x)")["skills"] == [
        {"name": "First Aid", "specialization": None}
    ]
    assert extract_equipment.split_targets(
        extract_equipment.trim_condition("First Aid on burns & rashes")
    ) == ["First Aid"]


def test_a_specialization_is_part_of_the_identity():
    """The compass reads "Navigation (Land) +1". A character rolling the bare
    Navigation row is rolling a different skill and must not see it."""
    assert equipment_named("Compass (brass)")["skills"] == [
        {"name": "Navigation", "specialization": "Land"}
    ]
    index = extract_equipment.skill_index()
    assert ("Navigation", "Land") in index and ("Navigation", None) in index


def test_a_bonus_naming_an_instance_of_an_open_row_joins_it():
    """Chapter 3 leaves two of these rows OPEN, and that is what makes an
    instance of them a match rather than a miss.

    "Connoisseur" and "Savoir-Faire" are printed once each with no
    specialization and marked specializable -- a template the player fills in.
    Chapter 8 prices instances of both. A strict key match reads those as
    skills chapter 3 never printed and drops the bonus, which is 6cz's defect
    on the WRITING side of the same join: `Equipment.forSkill` was taught that
    an open row is matched by its instance and this was not.
    """
    index = extract_equipment.skill_index()
    assert index[("Connoisseur", None)] is True
    assert index[("Savoir-Faire", None)] is True
    assert ("Connoisseur", "Books") not in index
    assert ("Savoir-Faire", "High Society") not in index
    # Stored as the book prints it: the bonus is scoped to that instance, so a
    # character with Connoisseur (Antiques) must not be offered it.
    assert equipment_named("Rare book catalogue")["skills"] == [
        {"name": "Connoisseur", "specialization": "Books"}
    ]
    assert equipment_named("Fine clothes (quality)")["skills"] == [
        {"name": "Savoir-Faire", "specialization": "High Society"}
    ]


def test_an_unprinted_specialization_of_an_ENUMERATED_row_joins_nothing():
    """The widening stops where the book stops leaving the row open.

    Chapter 3 prints Navigation three times -- bare, (Sea), (Land) -- and
    marks it NOT specializable, so the list enumerates its own instances and
    "Navigation (Air)" is a skill this book does not have. Reading every
    parenthesis as an instance would invent it.
    """
    index = extract_equipment.skill_index()
    assert index[("Navigation", None)] is False
    assert extract_equipment.joins(("Navigation", "Land"), index)
    assert not extract_equipment.joins(("Navigation", "Air"), index)
    assert extract_equipment.joins(("Connoisseur", "Books"), index)
    # An open row is matched by an instance, never by a name it never printed.
    assert not extract_equipment.joins(("Sommelier", "Wine"), index)


@needs_pdf
def test_every_clause_target_that_joins_nothing_is_recorded():
    """The guard that was missing, and the reason this slice exists.

    verify() checked that no RECORDED exclusion resolved -- and never that
    every unresolved target is recorded. A new clause naming a skill under a
    spelling chapter 3 does not print would be dropped in silence, with the
    row count, the pinned joins and the joiner set all still green. The set
    below is the closed answer, so a new drop has to be read by a human.
    """
    index = extract_equipment.skill_index()
    tagged = extract_equipment.tag_categories(
        extract_equipment.stream(extract_equipment.load_lines(),
                                 *extract_equipment.EQUIPMENT_RANGE)
    )
    rows = extract_equipment.parse(tagged, index)
    unresolved = {
        t for r in rows for t in extract_equipment.clause_targets(r["notes"])
        if not extract_equipment.joins(extract_equipment.as_key(t), index)
    }
    assert unresolved == extract_equipment.RECORDED_UNJOINED
    # Each of the three sets earns its place: a roll the book never prints as
    # a skill, a skill chapter 3 omits, and one it spells differently. Their
    # union is asserted above, so what is left to check is that they are
    # DISJOINT -- a name recorded in two of them is two records of one fact,
    # and the union cannot see that.
    sets = (extract_equipment.UNPRINTED_TARGETS,
            extract_equipment.ABSENT_FROM_CHAPTER_3,
            extract_equipment.HYPHENATED_IN_CHAPTER_8)
    assert sum(len(x) for x in sets) == len(extract_equipment.RECORDED_UNJOINED)
    # v6.9i reworded the Velvet shawl note, so "reaction" left this set.
    assert "reaction" not in extract_equipment.UNPRINTED_TARGETS
    assert extract_equipment.HYPHENATED_IN_CHAPTER_8 == {"Fortune-Telling"}


@needs_pdf
def test_a_target_that_joins_nothing_and_is_unrecorded_fails_verification():
    """RED-proof for the reverse guard, mutating the direction that FAILS
    silently: a new clause appearing, not an old one going away."""
    index = extract_equipment.skill_index()
    tagged = extract_equipment.tag_categories(
        extract_equipment.stream(extract_equipment.load_lines(),
                                 *extract_equipment.EQUIPMENT_RANGE)
    )
    rows = extract_equipment.parse(tagged, index)
    patched = [
        # A name chapter 3 does not print. Cryptography would have been the
        # obvious mutation and it IS in the skill list, so it resolves and
        # proves nothing -- a mutation has to land where the guard looks.
        {**r, "notes": r["notes"] + "; +1 to Basket Weaving"}
        if r["name"] == "Magnifying glass" else r
        for r in rows
    ]
    with pytest.raises(ValueError, match="Basket Weaving"):
        extract_equipment.verify(patched, index)


def test_four_rolls_are_priced_without_ever_being_printed_as_skills():
    """"spot", "fool officials", "infection-resistance rolls" and "ritual
    effects" are rolls this book charges for and never lists. Guessing one
    into Observation or Search would be authoring content."""
    joined = {s["name"] for e in equipment_entries() for s in e["skills"]}
    assert not (joined & extract_equipment.UNPRINTED_TARGETS)
    for name in ("Trap wire (10 m)", "Dark clothing (set)", "False badge (tin)",
                 "Antiseptic (bottle)", "Holy symbol (wooden)"):
        assert equipment_named(name)["skills"] == []


def test_chapter_8_grants_bonuses_to_two_skills_chapter_3_never_prints():
    """A 4.1 disagreement between two chapters of one book: recorded here,
    never repaired by inventing a skill row for either."""
    index = extract_equipment.skill_index()
    for absent in extract_equipment.ABSENT_FROM_CHAPTER_3:
        assert (absent, None) not in index
    assert "Forced Entry" in equipment_named("Heavy boots (leather)")["notes"]
    assert equipment_named("Heavy boots (leather)")["skills"] == []
    assert "Gather Information" in equipment_named("Press badge")["notes"]
    assert equipment_named("Press badge")["skills"] == []


def test_the_fortune_seam_is_the_books_own_and_is_not_normalised():
    """Chapter 8 spells it "Fortune-Telling"; chapter 3's list prints "Fortune
    Telling". Matching across the hyphen would decide what the book meant on
    resemblance alone (6by), so both decks join nothing."""
    index = extract_equipment.skill_index()
    assert ("Fortune Telling", None) in index
    assert ("Fortune-Telling", None) not in index
    for deck in ("Tarot deck (common)", "Tarot deck (quality)"):
        assert "Fortune-Telling" in equipment_named(deck)["notes"]
        assert equipment_named(deck)["skills"] == []


# --- The Secondary Characteristics cost column (Ch. 2, p. 8-9) -------------


def secondary_rows():
    return core_entries("secondary_costs")


@needs_pdf
def test_the_book_prices_a_secondary_in_exactly_one_place():
    """`extract_core_rules` used to skip this table because "ch 3 prints the
    costs, which are already extracted". Chapter 3 prints no such thing -- its
    `pts/level` lines are advantage rows -- and "Cost to Modify" occurs once in
    the whole book, on page 8."""
    byn = extract_core_rules.load_lines()
    printing = [n for n, t in byn.items() if "Cost to Modify" in t]
    assert printing == [371], printing
    per_level = [
        n for n, t in byn.items()
        if re.search(r"(pts?|points?) per (level|0\.25)", t, re.I)
    ]
    # All six on pages 8-9, none anywhere else in the book.
    assert per_level and all(353 <= n <= 399 for n in per_level), sorted(per_level)


def test_the_nine_rows_split_six_priced_from_three_refused():
    rows = secondary_rows()
    assert [r["key"] for r in rows] == [
        "hp", "will", "per", "fp", "spi", "cor", "basicSpeed", "basicMove", "digestion",
    ]
    priced = {r["key"]: r["pointsPerStep"] for r in rows if r["pointsPerStep"] is not None}
    assert priced == {
        "hp": 2, "will": 10, "per": 10, "fp": 3, "basicSpeed": 5, "basicMove": 5,
    }


def test_an_unpriced_row_keeps_the_words_that_refuse_it():
    """Absent, not zero. "Fixed at 0" and "never bought" are rules, and a 0
    would read as a free purchase -- the distinction this repo already draws
    between printing nothing and printing zero."""
    by_key = {r["key"]: r for r in secondary_rows()}
    assert by_key["spi"]["cost"] == "Fixed at 0; see Chapter 4"
    assert by_key["cor"]["cost"] == "Tracked in play; never bought"
    assert by_key["digestion"]["cost"] == "Tracked through acting"
    assert all(by_key[k]["pointsPerStep"] is None for k in ("spi", "cor", "digestion"))
    assert all(by_key[k]["step"] is None for k in ("spi", "cor", "digestion"))


def test_basic_speed_is_the_only_row_priced_per_fraction():
    """A parser that dropped the unit would price a whole point of Basic Speed
    at 5 instead of 20, and SecondaryDelta counts quarters because of it."""
    by_key = {r["key"]: r for r in secondary_rows()}
    assert by_key["basicSpeed"]["step"] == "0.25"
    assert all(
        r["step"] in (None, "level")
        for r in secondary_rows() if r["key"] != "basicSpeed"
    )


@needs_pdf
def test_a_renamed_characteristic_is_a_finding_not_a_slug():
    """The printed name -> key map is pinned. A row the book renames must stop
    the run rather than silently produce a key no consumer joins on."""
    byn = dict(extract_core_rules.load_lines())
    byn[372] = "Hit Points"
    with pytest.raises(ValueError, match="no consumer has a key for"):
        extract_core_rules.secondaries(byn)


@needs_pdf
def test_a_moved_price_fails_verification():
    """These six numbers bill every character PointBudget reports on."""
    bands, pools, rows, defs, secs = extract_core_rules.extract()
    secs = [dict(s) for s in secs]
    secs[0]["pointsPerStep"] = 4
    with pytest.raises(ValueError, match="changed price"):
        extract_core_rules.verify(
            bands, pools, rows, defs, secs, extract_core_rules.load_lines())


@needs_pdf
def test_pricing_a_row_the_table_refuses_fails_verification():
    bands, pools, rows, defs, secs = extract_core_rules.extract()
    secs = [dict(s) for s in secs]
    spi = next(s for s in secs if s["key"] == "spi")
    spi["pointsPerStep"] = 5
    with pytest.raises(ValueError, match="is now priced"):
        extract_core_rules.verify(
            bands, pools, rows, defs, secs, extract_core_rules.load_lines())


# Chapter 5's own rows, as the book prints them on lines 2826 and 2830.
CHAPTER_5_MAJOR_WOUND = {
    "Failure": "Stunned for 1d6 seconds (roll HT each turn to recover early)",
    "Fail by 5+": "Knocked down and unconscious for 1d hours",
}
APPENDIX_MAJOR_WOUND = (
    "Major Wound: Hit > ½ HP -> HT or stunned 1d6 sec | Fail by 5+ -> down 15 min"
)


def test_the_appendix_summary_is_cut_into_the_clauses_it_summarises():
    """Two clauses, each a condition and an outcome, on one line."""
    summary = extract_appendix.parse_major_wound(APPENDIX_MAJOR_WOUND)
    assert summary == {
        "Hit > ½ HP": "HT or stunned 1d6 sec",
        "Fail by 5+": "down 15 min",
    }


def test_the_appendix_and_chapter_five_really_do_disagree_on_the_worst_row():
    """A guard against silent repair, in the 4.2 shape.

    Chapter 5's own table says a Major Wound failed by 5+ leaves you "Knocked
    down and unconscious for 1d hours". Appendix A's summary of that same
    table says "down 15 min". Chapter 5 governs -- a summary may define but
    not overrule -- so this is RECORDED, never repaired, and the recording is
    only honest while both printings still say what it claims.
    """
    summary = extract_appendix.parse_major_wound(APPENDIX_MAJOR_WOUND)
    extract_appendix.verify_major_wound(summary, CHAPTER_5_MAJOR_WOUND)

    # The exclusion is stale the day they agree, and a stale exclusion is how
    # a repair goes unnoticed.
    with pytest.raises(ValueError, match="no longer disagree"):
        extract_appendix.verify_major_wound(
            summary, {**CHAPTER_5_MAJOR_WOUND, "Fail by 5+": "down 15 min"})

    # And the clause they DO agree on is checked in the same pass, because
    # "already claimed" was never the same question as "agrees".
    moved = dict(summary)
    moved["Hit > ½ HP"] = "HT or stunned 2d6 sec"
    with pytest.raises(ValueError, match="agreed with"):
        extract_appendix.verify_major_wound(moved, CHAPTER_5_MAJOR_WOUND)


def test_the_agreement_check_reads_both_printings_not_just_the_summary():
    """A one-way check has a blind side (6dh), and this one had it.

    The agreement clause was compared against the appendix's wording alone,
    so chapter 5's own row could drift to anything at all while the guard
    kept passing. Found by review, not by the tests above.
    """
    summary = extract_appendix.parse_major_wound(APPENDIX_MAJOR_WOUND)
    drifted = {**CHAPTER_5_MAJOR_WOUND, "Failure": "Knocked out for 2d6 seconds"}
    with pytest.raises(ValueError, match="chapter 5 now reads"):
        extract_appendix.verify_major_wound(summary, drifted)


@needs_pdf
def test_the_recorded_disagreement_names_both_printings():
    """A prose-only record is invisible to every sweep this repo runs (6cj).

    The two hit-location disagreements this file found were written into a
    docstring, and the Major Wound one was missed for six slices while being
    printed to the player on the damage screen.
    """
    both = extract_appendix.SUMMARY_DISAGREEMENTS["Fail by 5+"]
    assert both == ("down 15 min", "Knocked down and unconscious for 1d hours")
    byn = {r["n"]: r["text"] for r in
           json.loads((ROOT / "build" / "extract" / "lines.json")
                      .read_text(encoding="utf-8"))}
    assert byn[8931].strip() == APPENDIX_MAJOR_WOUND
    # Every chapter-5 string the tests above feed the guard, tied to the line
    # the book prints it on. Without this the guard would be checking the
    # appendix against strings typed HERE -- a value asserted on the wrong
    # side of the thing that produces it, which is the hole 6dk shipped once.
    for row, n in extract_appendix.SUMMARISES.values():
        assert byn[n].strip() == CHAPTER_5_MAJOR_WOUND[row], (row, n)
    assert CHAPTER_5_MAJOR_WOUND["Fail by 5+"] == both[1]


# --- Appendix A's facing line (Ch. A, p. 159) ------------------------------


@needs_pdf
def test_the_appendix_prints_the_facing_penalty_a_second_time():
    """`ActiveDefenses`' KDoc used to say the facing rule "has no second
    printing anywhere in the book -- checked, not assumed". This is that
    second printing. It is pinned because the KDoc now cites it: an argument
    resting on a sentence needs the sentence guarded, or the correction
    becomes the next stale claim."""
    byn = {r["n"]: r["text"] for r in
           json.loads((ROOT / "build" / "extract" / "lines.json")
                      .read_text(encoding="utf-8"))}
    assert byn[8926].strip() == "Direction Penalties: Side -2 | Rear -4"
    # Its neighbour enumerates all three defences, which is what made the line
    # look like it could widen chapter 5's singular.
    assert byn[8925].strip().startswith("Active Defenses: Dodge =")
    assert "Parry" in byn[8925] and "Block" in byn[8925]


@needs_pdf
def test_the_appendix_line_names_no_defence_so_it_widens_nothing():
    """The whole refusal now rests on ONE property of line 9667: it carries no
    noun (8926 since v6.9i). To widen chapter 5's singular "to defense", a second printing has to
    say something the first did not, and this one says strictly less. If the
    line ever acquires "defense" or "defenses", that reason has expired."""
    byn = {r["n"]: r["text"] for r in
           json.loads((ROOT / "build" / "extract" / "lines.json")
                      .read_text(encoding="utf-8"))}
    assert not re.search(r"defen[cs]e", byn[8926], re.I), byn[8926]
    # And chapter 5's own two bullets still print the singular they are
    # refused on. Both halves have to hold for the refusal to stand.
    for n in (2555, 2556):
        assert re.search(r"to defense\b", byn[n], re.I), byn[n]
        assert not re.search(r"to defenses\b", byn[n], re.I), byn[n]


@needs_pdf
def test_mechanical_filter_sees_a_cost_priced_in_a_bare_pool():
    """A cost is not always signed, and the filter used to miss the ones
    that are not.

    coverage.py admitted "5 pts" from the start -- a bare integer followed
    by the unit a rule reads -- and admitted no other pool. So the book's
    ability costs were read as flavour. These are real book lines, quoted
    by number rather than invented, and the assertion that matters is the
    SECOND one in each pair: the old pattern did not match them. Without
    that half this test would pass against the narrow filter it exists to
    replace.
    """
    import coverage as coverage_tool

    # "The old filter" is derived by removing the widening alternative from
    # the pattern that actually ships, never retyped. A hand-copied duplicate
    # stops representing the old filter the moment anyone edits one of the
    # four shared alternatives, and then the negative assertions below are
    # made against a pattern nobody runs.
    widening = r"|\b\d+\s*(?:SPI|HP|FP|CoR|DR)\b"
    live = coverage_tool.MECHANICAL.pattern
    assert live.endswith(widening), (
        "the pool alternative is no longer the last one in MECHANICAL, so "
        f"slicing it off no longer yields the pre-change filter: {live!r}"
    )
    narrow = re.compile(live[: -len(widening)])
    byn = {l["n"]: l["text"] for l in json.loads((OUT / "lines.json").read_text(encoding="utf-8"))}

    for n, quote in [
        (4170, "4 HP"),
        (4171, "1 SPI"),
        (2367, "1 SPI"),
    ]:
        text = byn[n]
        assert quote in text, f"line {n} no longer prints {quote!r}: {text!r}"
        assert coverage_tool.MECHANICAL.search(text), f"line {n} is still invisible"
        assert not narrow.search(text), (
            f"line {n} was already visible to the narrow filter, so it cannot "
            "be evidence for widening it"
        )

    # The condition is the unit, not the digit. A number followed by
    # anything that is not a pool the rules read stays flavour.
    assert not coverage_tool.MECHANICAL.search("It is around 5 cm in length.")
    assert not coverage_tool.MECHANICAL.search("The ritual takes 20 minutes to prepare.")


@needs_pdf
def test_spirit_vision_reports_a_completion_it_earned():
    """The blind spot moved the denominator, not just the miss count.

    A line the filter cannot see leaves BOTH sides of the ratio, so v6.9d's
    chapter 7.6 once reported 100.0% while no content file claimed it. v6.9i
    folded the guide into the end of chapter 5 and moved the cost sentence to
    chapter 4, so the percentage no longer isolates it at all. What the test
    asserts is what the number stood in for: each priced Spirit Vision line
    is visible to the filter AND claimed by a content file.
    """
    import coverage as coverage_tool

    byn = {l["n"]: l["text"] for l in json.loads((OUT / "lines.json").read_text(encoding="utf-8"))}
    claimed = set()
    content = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"
    for f in content.rglob("*.json"):
        data = json.loads(f.read_text(encoding="utf-8"))
        for spans in (data.get("sourceLines") or {}).values():
            for a, b in spans:
                claimed.update(range(a, b + 1))
    for n in (2367, 3082):
        assert "1 SPI" in byn[n], f"line {n} no longer prices Spirit Vision: {byn[n]!r}"
        assert coverage_tool.MECHANICAL.search(byn[n])
        assert n in claimed, f"line {n}, a Spirit Vision cost, is claimed by no content file"


# --- The fatigue row's silence about rounding (Ch. 5, p. 42) ----------------


@needs_pdf
def test_the_fatigue_row_prints_no_rounding_direction():
    """`Pools.halvedForFatigue` rounds UP and its KDoc says the direction is
    this app's choice because the row prints none. That is an argument resting
    on a sentence, so the sentence is pinned. If line 3490 ever acquires a
    direction, the choice stops being a choice and the KDoc has expired."""
    byn = {r["n"]: r["text"] for r in
           json.loads((ROOT / "build" / "extract" / "lines.json")
                      .read_text(encoding="utf-8"))}
    assert byn[3490].strip() == "-1 to all rolls; Move and Dodge halved"
    assert byn[3489].strip() == "At 1/3 FP or below"
    assert not re.search(r"round(ed)?\s+(up|down)", byn[3490], re.I), byn[3490]


@needs_pdf
def test_the_book_does_print_a_direction_where_it_cares():
    """The other half of the argument: the row's silence means something only
    because this book DOES print directions elsewhere. Two of them, in both
    directions, so there is no house convention to fall back on."""
    byn = {r["n"]: r["text"] for r in
           json.loads((ROOT / "build" / "extract" / "lines.json")
                      .read_text(encoding="utf-8"))}
    assert re.search(r"rounded down", byn[2527], re.I), byn[2527]
    assert re.search(r"round up", byn[5341], re.I), byn[5341]
