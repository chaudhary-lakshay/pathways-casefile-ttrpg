"""Shields, spirit damage, boons, non-standard pathways, weapons, wealth, the appendix, appearance and languages. Split out of test_tools.py."""
import json

import pytest

import extract_shields

from testsupport import CONTENT, needs_pdf


import extract_shields


def test_a_wrapped_shield_name_is_walked_back_on_its_brackets():
    """"Light (buckler," / "target)" is one cell. Counting two lines per name
    would break the first time the book prints one that fits on one line."""
    cells = ["Light (buckler,", "target)", "+1", "3", "15", "2 kg", "£2", "worn"]
    assert extract_shields.name_before(cells, 2) == "Light (buckler, target)"
    # A name that does not wrap is taken whole, not glued to the row above.
    assert extract_shields.name_before(["Tower", "+3"], 1) == "Tower"


def test_a_note_holding_a_signed_number_does_not_start_a_row():
    """"-1 to Block vs. ranged" and "Move -1 while Readied" both sit in Notes
    cells. Only the bare signed integer is the DB column."""
    rows = extract_shields.parse([
        "Light (buckler,", "target)", "+1", "3", "15", "2 kg", "£2",
        "Worn on forearm; frees hand; -1 to Block vs. ranged",
    ])
    assert len(rows) == 1
    assert rows[0]["db"] == 1 and rows[0]["coverDr"] == 3 and rows[0]["hp"] == 15
    assert rows[0]["notes"].endswith("-1 to Block vs. ranged")


def test_a_bullet_takes_the_lines_that_wrap_out_of_it():
    rules = extract_shields.join_bullets([
        (False, "table row that is not a bullet"),
        (True, "Shield as cover: treat it as having Cover"),
        (False, "DR equal to its listed value × 1.5 for this purpose."),
        (True, "Shield Bash: damage = thr-2 cr for light."),
    ])
    assert len(rules) == 2
    assert rules[0].endswith("for this purpose.")


def test_committed_shields_are_the_four_the_book_prints():
    entries = json.loads((CONTENT / "shields.json").read_text(encoding="utf-8"))["entries"]
    assert [e["type"] for e in entries] == extract_shields.EXPECTED_TYPES
    # Weight and Cost stay text: the improvised row prints "varies" and a dash.
    improvised = entries[3]
    assert improvised["weight"] == "varies" and not improvised["cost"].isdigit()


# --- Spirit body damage (Ch. 5, p. 40-41) -----------------------------------

import extract_spirit


def test_a_bullet_that_wraps_is_rejoined_on_its_sentence():
    """"(e.g. skull" ends nothing, so the next line belongs to it. Cutting by
    position would leave half a rule and start the next one mid-word."""
    rules = extract_spirit.parse([
        (False, "Damage Application:"),
        (True, "Wounding multipliers apply normally to the chosen pool (e.g. skull"),
        (False, "×4, vitals ×3) stack with wounding multipliers when applied to HP."),
    ])
    assert len(rules) == 1
    assert rules[0]["rule"].endswith("when applied to HP.")
    assert rules[0]["section"] == "Damage Application"


def test_a_finished_bullet_does_not_swallow_the_note_after_it():
    """The section's last line is a standalone note, and the recovery bullet
    above it ends in a full stop. Joining by position would glue them."""
    rules = extract_spirit.parse([
        (False, "Recovery follows each pool's normal rate:"),
        (True, "CoR from Soul Debt are permanent and require special rituals to remove."),
        (False, "Non-Beyonders (SPI 0): Can only choose HP or FP — no spirituality to sacrifice."),
    ])
    assert len(rules) == 2
    assert rules[1]["section"] == "Non-Beyonders (SPI 0)"


def test_the_threshold_group_is_dropped_but_still_anchors_the_section_after_it():
    """Its five lines live in pool_thresholds.json; a second copy would be a
    rule with two homes. Dropping it must not shift what follows."""
    rules = extract_spirit.parse([
        (False, "Threshold Penalties by Pool:"),
        (True, "HP ≤ 0: Standard GURPS death/dying rules."),
        (False, "Damage Expression: Spirit body damage is given either as:"),
        (True, "Flat dice: roll damage, then choose a pool."),
    ])
    assert [r["section"] for r in rules] == ["Damage Expression"]


def test_committed_spirit_rules_keep_the_phrases_the_app_states():
    entries = json.loads((CONTENT / "spirit_damage.json").read_text(encoding="utf-8"))["entries"]
    assert list(dict.fromkeys(e["section"] for e in entries)) == extract_spirit.EXPECTED_SECTIONS
    body = " ".join(e["rule"] for e in entries)
    for phrase in extract_spirit.PINNED:
        assert phrase in body
    assert extract_spirit.DROPPED not in {e["section"] for e in entries}


import extract_boons


def test_a_modifier_bullet_splits_into_its_bracketed_options():
    rows = extract_boons.parse_options(
        "Sacrifice quality",
        "A perfunctory sacrifice (-2 to -4), an appropriate sacrifice (+0), a "
        "meaningful sacrifice (+1 to +3), or a great sacrifice that aligns "
        "perfectly with the Great One's domain (+4 to +6).",
    )
    assert [r["modifier"] for r in rows] == [-2, 0, 1, 4]
    assert [r.get("best") for r in rows] == [-4, None, 3, 6]
    # The connector is dropped; the rest of the label is the book's own text,
    # commas and all -- and no printed label contains one, which is why the
    # comma can be the boundary.
    assert rows[3]["label"] == "a great sacrifice that aligns perfectly with the Great One's domain"


def test_the_boon_level_bullet_is_kept_whole_because_it_is_a_rule():
    """Three examples and then "doubling the penalty with each sequence".
    Parsed into rows it would stop at Sequence 7 and the ladder would have to
    be guessed; kept whole, the ladder is implemented and tested in Kotlin."""
    rows, rule = extract_boons.parse_modifiers([
        "Ritual quality: Sloppy or rushed (-3), standard preparation (+0).",
        "Boon level: Sequence 9 equivalent (+0), Sequence 8 (-2), Sequence 7 "
        "(-4), and so on, doubling the penalty with each sequence.",
    ])
    assert {r["group"] for r in rows} == {"Ritual quality"}
    assert "doubling the penalty with each sequence" in rule


def test_a_modifier_bullet_with_no_options_is_a_finding_not_an_empty_group():
    with pytest.raises(ValueError):
        extract_boons.parse_options("Sacrifice quality", "some prose with no brackets")


def test_a_bullet_continuation_rejoins_across_the_page_break():
    byn = {
        1: "\x7f Boon level: Sequence 9 equivalent (+0), and so on — see Sacrifice",
        2: "Powered by GURPS: Pathways — Lord of the Mysteries",
        3: "Page 145",
        4: "quality modifiers above.",
    }
    assert extract_boons.bullets(byn, 1, 4) == [
        "Boon level: Sequence 9 equivalent (+0), and so on — see Sacrifice "
        "quality modifiers above."
    ]


def test_the_committed_boon_ritual_keeps_time_and_weight_apart():
    """Heavy is how long it takes; Moderate is what it rolls at. Collapsing
    them rolls at -4 and charges 8+ SPI for a 4-6 SPI ritual."""
    entry = json.loads((CONTENT / "boon_granting.json").read_text(encoding="utf-8"))["entries"][0]
    assert entry["complexity"] == "Heavy"
    assert entry["weight"] == "Moderate"
    assert (entry["weightModifier"], entry["spi"], entry["spiMax"]) == (-2, 4, 6)
    assert entry["skill"] == "Ritualistic Magic"
    # Both printings of the Moderate cost must agree with Chapter 7's own.
    weights = json.loads((CONTENT / "ritual_weights.json").read_text(encoding="utf-8"))["entries"]
    moderate = next(w for w in weights if w["weight"] == "Moderate")
    assert (moderate["spi"], moderate["spiMax"]) == (entry["spi"], entry["spiMax"])


def test_the_committed_boon_modifiers_are_two_groups_of_four():
    entries = json.loads((CONTENT / "boon_modifiers.json").read_text(encoding="utf-8"))["entries"]
    groups = {}
    for row in entries:
        groups.setdefault(row["group"], []).append(row)
    assert sorted(groups) == ["Ritual quality", "Sacrifice quality"]
    assert all(len(rows) == 4 for rows in groups.values())


import extract_non_standard


def test_the_non_standard_blocks_are_not_in_the_potion_file():
    """Chapter 14's pathways advance by boon. PotionDigestion walks whatever
    pathways.json holds, so one name in both files makes a boon-granted path
    drinkable -- the reason these are a separate file at all."""
    granted = json.loads(
        (CONTENT / "non_standard_pathways.json").read_text(encoding="utf-8")
    )["entries"]
    potions = json.loads((CONTENT / "pathways.json").read_text(encoding="utf-8"))["entries"]
    assert not {e["pathway"] for e in granted} & {e["pathway"] for e in potions}


def test_the_non_standard_ladder_has_the_holes_the_intro_warns_about():
    entries = json.loads(
        (CONTENT / "non_standard_pathways.json").read_text(encoding="utf-8")
    )["entries"]
    assert len(entries) == extract_non_standard.EXPECTED_BLOCKS
    with_nine = {e["pathway"] for e in entries if e["sequence"] == 9}
    gaps = {e["pathway"] for e in entries if e["sequence"] == 8} - with_nine
    assert gaps == extract_non_standard.SEQUENCE_8_WITHOUT_9


def test_both_spellings_of_one_pathway_survive_the_extraction():
    """Ch. 14 prints "Eternal Aeon (Inevitability)", ch. 15 "Eternal Aeon".
    Recorded, never repaired -- which is why the gap list above has four
    entries and only three are real gaps."""
    entries = json.loads(
        (CONTENT / "non_standard_pathways.json").read_text(encoding="utf-8")
    )["entries"]
    printed = {e["pathway"] for e in entries}
    for spelling in extract_non_standard.PATHWAY_NAME_DISAGREEMENT:
        assert spelling in printed


def test_the_three_silent_ability_sections_stay_named():
    """An " Ability" heading with nothing under it is what the BOOK prints in
    three blocks; in each the next line is a page break or the next header."""
    entries = json.loads(
        (CONTENT / "non_standard_pathways.json").read_text(encoding="utf-8")
    )["entries"]
    silent = {(e["name"], e["sequence"]) for e in entries if not e["ability"]}
    assert silent == extract_non_standard.ABILITY_MISSING


def test_a_block_that_grew_an_ability_would_fail_verification():
    entries = [
        {"name": "Villain", "sequence": 9, "pathway": "Mother Goddess of Depravity",
         "ability": "something the book does not print"},
    ]
    with pytest.raises(ValueError):
        extract_non_standard.verify(entries)


import extract_weapons


def test_the_book_does_print_a_weapon_table():
    """Four sections of the handover doc said it did not, and three features
    were deferred on that premise. It is in chapter 8, which coverage.py
    reported at 0.0% because nothing had ever read it."""
    entries = json.loads((CONTENT / "weapons.json").read_text(encoding="utf-8"))["entries"]
    assert [e["name"] for e in entries] == extract_weapons.EXPECTED_WEAPONS
    assert all(e["skill"] and e["cost"] for e in entries)


def test_a_wrapped_damage_cell_keeps_both_attacks():
    """The Combat Knife prints "sw-1 cut / thr" then "imp" on the next line.
    Reading the first mode only drops half of what it can do."""
    entries = json.loads((CONTENT / "weapons.json").read_text(encoding="utf-8"))["entries"]
    knife = next(e for e in entries if e["name"] == "Combat Knife")
    assert [m["printed"] for m in knife["damage"]] == ["sw-1 cut", "thr imp"]
    assert [m["type"] for m in knife["damage"]] == ["cut", "imp"]
    assert knife["damage"][0]["base"] == "sw" and knife["damage"][0]["modifier"] == -1


def test_only_a_firearm_carries_a_range_in_metres():
    """"C" and "C/1" are reach. Parsed as distances, Ranged.bandFor would put
    a sword one metre away and apply a range penalty to it."""
    entries = json.loads((CONTENT / "weapons.json").read_text(encoding="utf-8"))["entries"]
    revolver = next(e for e in entries if e["name"] == "Revolver (.38 cal)")
    assert (revolver["halfRange"], revolver["maxRange"]) == (150, 1800)
    for melee in ("Combat Knife", "Broadsword", "Truncheon/Baton", "Hand Axe"):
        row = next(e for e in entries if e["name"] == melee)
        assert row["halfRange"] is None and row["maxRange"] is None


def test_chapter_8_prints_a_damage_type_chapter_5_cannot_price():
    """The Bolt-Action Rifle deals "pi+"; chapter 5's wounding table never
    abbreviates its Large Piercing row. Recorded, never repaired -- adding the
    abbreviation would be editing a chapter that does not print it."""
    entries = json.loads((CONTENT / "weapons.json").read_text(encoding="utf-8"))["entries"]
    assert extract_weapons.orphan_types(entries) == extract_weapons.ORPHAN_TYPES
    rifle = next(e for e in entries if e["name"] == "Bolt-Action Rifle")
    assert rifle["damage"][0]["type"] == "pi+"
    # And the same table gives "Hunting rifles" as its Large Piercing example
    # while chapter 8 prints that gun as plain pi. Both disagreements stand.
    assert next(e for e in entries if e["name"] == "Hunting Rifle")["damage"][0]["type"] == "pi"


def test_a_reconciled_rifle_would_fail_verification():
    """If a future extraction quietly agreed the two chapters, that is a
    finding about the data, not a repair to copy forward."""
    entries = json.loads((CONTENT / "weapons.json").read_text(encoding="utf-8"))["entries"]
    patched = [
        {**e, "damage": [{**e["damage"][0], "type": "pi+"}]}
        if e["name"] == "Hunting Rifle" else e
        for e in entries
    ]
    with pytest.raises(ValueError):
        extract_weapons.verify(patched)


def test_the_shotgun_note_attaches_to_the_shotgun():
    entries = json.loads((CONTENT / "weapons.json").read_text(encoding="utf-8"))["entries"]
    shotgun = next(e for e in entries if e["name"] == "Double-Barrel Shotgun")
    assert "pellets" in shotgun["notes"]
    assert all(not e["notes"] for e in entries if e["name"] != "Double-Barrel Shotgun")


def test_the_longer_aim_adds_two_on_top_of_the_weapons_acc():
    """"+ weapon's Acc +2 (max Acc +2 total)". Read as the modifier itself,
    +2 would make the longer aim WORSE than the short one for every weapon
    with Acc 3 or better."""
    entries = json.loads(
        (CONTENT / "ranged_modifiers.json").read_text(encoding="utf-8")
    )["entries"]
    aim = [e for e in entries if e.get("weaponAcc")]
    assert len(aim) == 2
    assert sorted(e.get("accBonus", 0) for e in aim) == [0, 2]
    assert all("modifier" not in e for e in aim)


import extract_wealth


def wealth_entries():
    return json.loads((CONTENT / "wealth.json").read_text(encoding="utf-8"))["entries"]


def wealth_note():
    _, note = extract_wealth.split_note(
        extract_wealth.stream(extract_wealth.load_lines(), *extract_wealth.WEALTH_RANGE)
    )
    return note


def test_the_wealth_ladder_climbs_on_every_column():
    entries = wealth_entries()
    assert [e["status"] for e in entries] == extract_wealth.EXPECTED_STATUSES
    assert [e["points"] for e in entries] == [-25, -15, -10, 0, 5, 15, 25]
    assert [e["startingMoney"] for e in entries] == [
        "£0", "5 soli", "15 soli", "£1", "£5", "£50", "£100",
    ]
    assert all(e["lifestyle"] for e in entries)


def test_the_two_kinds_of_minus_are_both_read():
    """The point costs are ASCII-signed ("-25 pts") and the reaction column is
    U+2212 (" -2"). A parser that knows only one silently drops half the
    negative numbers on this page."""
    broke = next(e for e in wealth_entries() if e["status"] == "Dead Broke")
    assert broke["points"] == -25 and broke["reactionModifier"] == -2


def test_the_reaction_ladder_is_not_symmetric_around_average():
    """The paragraph says "+1 per Status level above Average ... and -1 per
    level below". Above Average the column agrees exactly. Below it, it does
    not: Struggling is one level down and prints 0, and the paragraph's own
    example puts Poor -- two levels down -- at -1."""
    by_status = {e["status"]: e for e in wealth_entries()}
    assert [by_status[s]["reactionModifier"] for s in
            ("Comfortable", "Wealthy", "Very Wealthy")] == [1, 2, 3]
    assert by_status["Struggling"]["reactionModifier"] == 0
    assert by_status["Poor"]["reactionModifier"] == -1


@needs_pdf
def test_a_straightened_ladder_would_fail_verification():
    """Giving Struggling the -1 the paragraph implies is a repair, not a fix.
    The printed column is the authority (CLAUDE.md 4.1)."""
    patched = [
        {**e, "reactionModifier": -1} if e["status"] == "Struggling" else e
        for e in wealth_entries()
    ]
    with pytest.raises(ValueError):
        extract_wealth.verify(patched, wealth_note())


def test_three_levels_are_printed_only_in_chapter_8():
    """Chapter 3's advantage and disadvantage lists carry four wealth levels.
    Average, Wealthy and Very Wealthy are not among them, so nothing could
    give a character the two levels above Comfortable until this table."""
    shared = extract_wealth.chapter_3_levels()
    assert shared == extract_wealth.CHAPTER_3_LEVELS
    only_here = {e["status"] for e in wealth_entries()} - set(shared)
    assert only_here == extract_wealth.ONLY_IN_CHAPTER_8


def test_the_two_chapters_agree_where_they_overlap():
    """Both price Poor at 15 points of disadvantage. If they ever stopped
    agreeing that would be a finding about the book, not a column to pick."""
    by_status = {e["status"]: e["points"] for e in wealth_entries()}
    for status, points in extract_wealth.CHAPTER_3_LEVELS.items():
        assert by_status[status] == points


@needs_pdf
def test_a_chapter_that_repriced_a_shared_level_would_fail_verification():
    patched = [
        {**e, "points": -20} if e["status"] == "Poor" else e for e in wealth_entries()
    ]
    with pytest.raises(ValueError):
        extract_wealth.verify(patched, wealth_note())


import extract_appendix


def appendix_entries():
    return json.loads(
        (CONTENT / "damage_abbreviations.json").read_text(encoding="utf-8")
    )["entries"]


def test_the_appendix_prints_the_abbreviation_chapter_5_omits():
    """Four doc sections, and two tests, recorded "this book prints no
    abbreviation for pi+". Appendix A prints it, at 0.0% coverage — the same
    way chapter 8's weapon table stayed invisible for four milestones."""
    by_type = {e["type"]: e for e in appendix_entries()}
    assert by_type["Large Piercing"]["abbreviation"] == "pi+"
    assert by_type["Large Piercing"]["multiplier"] == 1.5
    assert by_type["Piercing (small)"]["abbreviation"] == "pi (small)"


def test_the_appendix_adds_a_label_and_never_a_number():
    """The basis for adopting the label: every multiplier it repeats already
    matches chapter 5, so nothing new is claimed about the book."""
    chapter5 = extract_appendix.chapter_5_types()
    for row in appendix_entries():
        assert chapter5[row["type"]][0] == row["multiplier"]


def test_only_the_piercing_group_is_read_as_abbreviations():
    """The appendix writes "Crush" where chapter 5 abbreviates "cr", so its
    other cells are short names. Adopting them would give one damage type two
    spellings. "Pi" matching chapter 5's own "pi" is the whole evidence that
    this group is different."""
    labelled = {e["printedAs"] for e in appendix_entries() if e.get("abbreviation")}
    assert labelled == extract_appendix.ABBREVIATED_GROUP
    unlabelled = {e["printedAs"] for e in appendix_entries() if not e.get("abbreviation")}
    assert unlabelled == {"Crush", "Cut", "Impale", "Burn", "Toxic"}


def test_a_disagreeing_multiplier_would_fail_verification():
    """If the appendix ever priced a row differently from chapter 5, that is a
    finding about the book, not a column to prefer."""
    patched = [
        {**e, "multiplier": 2.5} if e["type"] == "Large Piercing" else e
        for e in appendix_entries()
    ]
    with pytest.raises(ValueError):
        extract_appendix.verify(patched)


def test_losing_the_matching_pi_would_fail_verification():
    """"Pi" agreeing with chapter 5 is the only evidence these are labels and
    not short names. Without it the group has no standing."""
    patched = [e for e in appendix_entries() if e["type"] != "Piercing"]
    with pytest.raises(ValueError):
        extract_appendix.verify(patched)


import extract_appearance


def appearance_entries():
    return json.loads((CONTENT / "appearance.json").read_text(encoding="utf-8"))["entries"]


def appearance_note():
    _, note = extract_appearance.stream(
        extract_appearance.load_lines(), *extract_appearance.APPEARANCE_RANGE
    )
    return note


def test_the_appearance_ladder_runs_best_to_worst():
    entries = appearance_entries()
    assert [e["level"] for e in entries] == extract_appearance.EXPECTED_LEVELS
    assert [e["points"] for e in entries] == [1, 4, 8, -4, -8, -16, -20, -24]
    assert [e["reactionModifier"] for e in entries] == [1, 2, 3, -1, -2, -4, -5, -6]


def test_the_reaction_column_is_not_derivable_from_the_cost():
    """If it were, the second column would be redundant and reading it would be
    arithmetic rather than extraction. It is not: the good half doubles its
    cost per step while its modifier climbs by one, and the bad half skips -12
    entirely, so -8 pts gives -2 and -16 gives -4."""
    by_level = {e["level"]: e for e in appearance_entries()}
    assert (by_level["Attractive"]["points"], by_level["Attractive"]["reactionModifier"]) == (1, 1)
    assert (by_level["Ugly"]["points"], by_level["Ugly"]["reactionModifier"]) == (-8, -2)
    assert (by_level["Hideous"]["points"], by_level["Hideous"]["reactionModifier"]) == (-16, -4)
    assert not any(-12 == e["points"] for e in appearance_entries())


def test_chapter_3_prints_none_of_these_levels():
    """Starting Wealth was HALF printed in chapter 3's trait lists, which is
    what made "Wealth: Poor" the spelling to match on. Appearance is printed
    there zero times, so the trait carries the level's own name and no prefix.
    The one row whose name says "Appearance" is a potion's doing."""
    named = extract_appearance.appearance_named_traits()
    assert named == [extract_appearance.NOT_AN_APPEARANCE_LEVEL]
    assert extract_appearance.NOT_AN_APPEARANCE_LEVEL not in {
        e["level"] for e in appearance_entries()
    }


def test_no_level_is_free_so_ordinary_looks_are_not_a_row():
    """Wealth prints Average at 0 pts. This table's cheapest row is Attractive
    at 1 point, so ordinary looks are the absence of a trait, not a level."""
    assert all(e["points"] != 0 for e in appearance_entries())


@needs_pdf
def test_a_level_that_crossed_the_two_columns_would_fail_verification():
    """A level that costs points and does not help -- or refunds them and does
    not hurt -- means a row was read out of order."""
    patched = [
        {**e, "reactionModifier": -1} if e["level"] == "Attractive" else e
        for e in appearance_entries()
    ]
    with pytest.raises(ValueError):
        extract_appearance.verify(patched, appearance_note())


@needs_pdf
def test_folding_in_unsettling_appearance_would_fail_verification():
    """4 pts, -1 to all reaction rolls, and "a common consequence of Abyss and
    Chained Pathway potions" -- suffered in play, not chosen at creation.
    Pricing it as this table's ninth row would sell a disfigurement."""
    patched = appearance_entries() + [
        {"level": extract_appearance.NOT_AN_APPEARANCE_LEVEL,
         "points": -4, "reactionModifier": -1}
    ]
    with pytest.raises(ValueError):
        extract_appearance.verify(patched, appearance_note())


@needs_pdf
def test_the_note_disclaiming_the_gurps_costs_must_survive():
    """"Appearance point costs have been adjusted for the Pathways setting and
    differ from standard GURPS values." Every number here is this book's, so a
    reviewer reaching for GURPS 4e (Attractive at 4 pts, not 1) would be
    correcting the book with its own source material."""
    assert extract_appearance.NOTE_PHRASE in appearance_note()
    with pytest.raises(ValueError):
        extract_appearance.verify(appearance_entries(), "some other sentence")


import extract_languages


def language_entries():
    return json.loads((CONTENT / "languages.json").read_text(encoding="utf-8"))["entries"]


def proficiency_entries():
    return json.loads(
        (CONTENT / "language_proficiency.json").read_text(encoding="utf-8")
    )["entries"]


def language_notes():
    return [
        e["text"] for e in
        json.loads((CONTENT / "language_notes.json").read_text(encoding="utf-8"))["entries"]
    ]


def test_twelve_languages_in_two_lists():
    entries = language_entries()
    assert [e["name"] for e in entries if e["type"] == "Common"] == extract_languages.EXPECTED_COMMON
    assert [e["name"] for e in entries if e["type"] == "Mystical"] == extract_languages.EXPECTED_MYSTICAL


def test_the_same_three_rows_are_priced_twice():
    """Native / Accented / Broken appear under both tables at 6/4/2 and
    30/20/10. Nothing inside a row says which table it came from -- only the
    header above it does -- so a parser keyed on the name alone prices a
    mystical Native at a fifth of the book's cost."""
    by_key = {(e["type"], e["proficiency"]): e["points"] for e in proficiency_entries()}
    assert [by_key[("Common", p)] for p in extract_languages.EXPECTED_PROFICIENCIES] == [6, 4, 2]
    assert [by_key[("Mystical", p)] for p in extract_languages.EXPECTED_PROFICIENCIES] == [30, 20, 10]


@needs_pdf
def test_a_merged_proficiency_table_would_fail_verification():
    patched = [
        {**e, "points": 6} if e["type"] == "Mystical" and e["proficiency"] == "Native" else e
        for e in proficiency_entries()
    ]
    with pytest.raises(ValueError):
        extract_languages.verify(language_entries(), patched, language_notes())


def test_only_the_mystical_table_speaks_about_rituals():
    """A common row carries neither ritual field. Saying nothing is not the
    same as permitting a ritual at no penalty -- the distinction the ritual
    critical table already draws about corruption."""
    for row in proficiency_entries():
        assert ("canPerformRituals" in row) == (row["type"] == "Mystical")
    mystical = {e["proficiency"]: e for e in proficiency_entries() if e["type"] == "Mystical"}
    assert mystical["Native"]["ritualModifier"] == 0
    assert mystical["Accented"]["ritualModifier"] == -2
    assert mystical["Broken"]["canPerformRituals"] is False
    assert "ritualModifier" not in mystical["Broken"]


def test_the_accented_row_names_a_penalty_without_granting_permission():
    """"Functional but imperfect. -2 to language-dependent skill rolls.
    Rituals using the language at -2." It never says the caster MAY cast, so
    requiring those words as well made this row unreadable -- printing a
    ritual modifier is the permission."""
    accented = next(
        e for e in proficiency_entries()
        if e["type"] == "Mystical" and e["proficiency"] == "Accented"
    )
    assert "Can perform rituals" not in accented["effect"]
    assert accented["canPerformRituals"] is True


def test_a_seven_character_wrap_is_not_a_thirteenth_language():
    """Balam's note wraps onto "similar" -- short enough to look exactly like
    a language name to the short-line rule that cuts these rows. It has to
    land inside the note it belongs to."""
    balam = next(e for e in language_entries() if e["name"] == "Balam (Dutan)")
    assert balam["notes"].endswith("structurally similar")
    assert all(e["name"] != "similar" for e in language_entries())


def test_the_intro_rule_survives_the_page_break():
    """"Spoken-only or written-only costs" ends one line and "half." starts
    the next, with a page footer between them. Matched per line, the rule
    simply is not there."""
    assert any(extract_languages.SPOKEN_WRITTEN_PHRASE in n for n in language_notes())
    assert any(extract_languages.HALVING_PHRASE in n for n in language_notes())


def test_the_footnote_is_not_part_of_the_broken_row():
    """Left in the stream it became the tail of Broken's effect cell, so the
    app would have quoted a trait footnote as a rule about speaking badly.
    It is found by its own shape: it prices traits inline, "(10 pts)"."""
    broken = next(
        e for e in proficiency_entries()
        if e["type"] == "Mystical" and e["proficiency"] == "Broken"
    )
    assert "Language Talent" not in broken["effect"]
    assert broken["effect"].endswith("Cannot perform rituals in the language.")
    assert any("Language Talent (10 pts)" in n for n in language_notes())


def test_chapter_3_points_at_this_section_and_then_disagrees_with_it():
    """Unlike Appearance, whose levels the trait lists print zero times, this
    section is cross-referenced by "Language (specify)". Two neighbouring rows
    then contradict it, and both are recorded rather than repaired."""
    pointer = extract_languages.trait_named(extract_languages.CROSS_REFERENCE)
    assert "Languages section" in pointer["notes"]

    # The section says "two COMMON languages ... does not apply to mystical";
    # the trait row says "two additional languages" and drops the restriction.
    assert "common" not in extract_languages.trait_named("Multilingual")["notes"]
    # And this row names a language neither table prints.
    dead = extract_languages.trait_named(extract_languages.DEAD_LANGUAGE_TRAIT)
    assert extract_languages.UNPRINTED_LANGUAGE in dead["notes"]
    assert all(
        e["name"] != extract_languages.UNPRINTED_LANGUAGE for e in language_entries()
    )


@needs_pdf
def test_a_repaired_multilingual_row_would_fail_verification():
    """If someone "fixed" the trait row to agree with the section, the
    disagreement would vanish silently. verify() fails instead."""
    original = extract_languages.trait_named
    try:
        extract_languages.trait_named = lambda name: (
            {"name": name, "notes": "Fluent in two additional common languages."}
            if name == "Multilingual" else original(name)
        )
        with pytest.raises(ValueError):
            extract_languages.verify(
                language_entries(), proficiency_entries(), language_notes()
            )
    finally:
        extract_languages.trait_named = original
