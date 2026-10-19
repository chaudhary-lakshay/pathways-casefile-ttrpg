"""Defences, combat, unarmed, ranged, wounds and divination. Split out of test_tools.py."""
import json

import pytest

import extract_defenses

from testsupport import CONTENT, needs_pdf


import extract_defenses


def test_a_wrapped_defense_label_does_not_start_a_new_row():
    """"Parry" and "(Unarmed)" are one heading split across two lines. Taking
    the second for a new row would shift every remaining row by one."""
    rows = extract_defenses.parse_defenses([
        "Parry", "(Unarmed)", "Brawling or DX ÷ 2 + 3",
        "Brawling parries weapons at -3.",
        "Block", "Shield Skill ÷ 2 + 3", "Once per round.",
    ])
    assert [r["defense"] for r in rows] == ["Parry (Unarmed)", "Block"]
    assert rows[0]["notes"].startswith("Brawling parries")


def test_a_wrapped_formula_stays_in_the_formula(): 
    rows = extract_defenses.parse_defenses([
        "Dodge", "Basic Speed (drop", "fractions) + 3",
        "Universal; no weapon required.",
    ])
    assert rows[0]["formula"] == "Basic Speed (drop fractions) + 3"
    assert rows[0]["notes"] == "Universal; no weapon required."


def test_committed_defenses_are_the_four_the_book_prints():
    entries = json.loads((CONTENT / "defenses.json").read_text(encoding="utf-8"))["entries"]
    assert [e["defense"] for e in entries] == extract_defenses.EXPECTED
    # ParryBlock implements these; the content is what the GM reads beside them.
    assert "2 + 3" in next(e for e in entries if e["defense"] == "Block")["formula"]


def test_defense_modifiers_survive_their_pdf_bullets():
    entries = json.loads((CONTENT / "defense_modifiers.json").read_text(encoding="utf-8"))["entries"]
    assert len(entries) == 5
    assert any("Retreat" in e["modifier"] for e in entries)
    for phrase in extract_defenses.PINNED:
        # The rule ActiveDefenses.acrobaticDodge now applies, both signs of it.
        assert phrase in " ".join(e["modifier"] for e in entries), phrase
    # The bullet glyph itself must not reach the app.
    assert all(not e["modifier"].startswith("") for e in entries)


def test_a_bullet_that_loses_its_sign_fails_verify():
    """The guard must fail on the mutation it exists for: a sign flip inside
    the cell, not merely a bullet going missing. A check on "2 Dodge" would
    pass this flipped text, because it is a substring of "-2 Dodge"."""
    flipped = [{"modifier": "[Free Action] [Acrobatic Dodge] Roll vs. Acrobatics: "
                            "success gives -2 Dodge; failure gives +2 Dodge."}] * 5
    defenses = json.loads((CONTENT / "defenses.json").read_text(encoding="utf-8"))["entries"]
    with pytest.raises(ValueError, match="the rule the app applies is gone"):
        extract_defenses.verify(defenses, flipped)


# --- Refusals of an active defence (Ch. 5, p. 36-40) -----------------------

import extract_defense_refusals as edr


def test_a_refusal_wrapping_across_lines_is_joined_into_one_sentence():
    """UNAWARE_RANGED wraps across three lines and a page break. Read line by
    line the rule is three fragments, none of which states it."""
    byn = {
        10: "Dodging ranged attacks: You may Dodge normally. If you did not",
        11: "Page 40",
        12: "know the shot was coming, you cannot defend at all — the bullet",
        13: "arrives before you can react.",
    }
    got = edr.sentence(byn, 10, 13)
    assert "Page 40" not in got
    assert "you cannot defend at all" in got


def test_a_refusal_that_prints_a_modifier_is_a_mis_cut_sentence():
    """None of the three prints a number to apply instead -- they withdraw the
    defence outright. A signed number means the span caught a neighbouring
    rule, which for two of these is a defence MODIFIER sitting lines away."""
    bad = [{"cause": c, "printed": edr.PINNED[c]} for c in edr.CAUSES]
    bad[0]["printed"] = "Surprised characters defend at -4 and cannot defend on it."
    with pytest.raises(ValueError, match="prints a modifier"):
        edr.verify(bad, [])


def test_charge_attack_forfeits_its_defence_too():
    """p. 37 excepts "an All-Out Attack maneuver"; the maneuver table prints
    "Cannot defend next turn." on Charge Attack as well. Dropping it would
    hand a charging fighter a defence the table denies."""
    entries = [{"cause": c, "printed": edr.PINNED[c]} for c in edr.CAUSES]
    three = [
        {"maneuver": n, "description": edr.FORFEIT_PHRASE}
        for n in sorted(edr.FORFEIT_ROWS - {"Charge Attack"})
    ]
    with pytest.raises(ValueError, match="forfeit a defence changed"):
        edr.verify(entries, three)


# --- Maneuvers and attack options (Ch. 5, p. 36-37) ------------------------

import extract_combat


def test_a_wrapped_description_does_not_start_a_new_maneuver():
    """A row is a NAME only when the next line is a movement token. Reading
    "(max ACC+2). Canceled by..." as a row of its own would shift the whole
    table and give Do Nothing the wrong description."""
    rows = extract_combat.parse_maneuvers([
        "Aim (Ranged)", "Step",
        "Add weapon's ACC to your next ranged Attack. +1 per additional Aim",
        "(max ACC+2). Canceled by any Active Defense or injury.",
        "Do Nothing", "None", "If Stunned: -4 to Active Defense.",
    ])
    assert [r["maneuver"] for r in rows] == ["Aim (Ranged)", "Do Nothing"]
    assert rows[0]["description"].endswith("or injury.")


def test_only_the_maneuvers_that_charge_FP_carry_the_field():
    """Absent, not zero: a maneuver charging nothing is not one charging 0."""
    rows = extract_combat.parse_maneuvers([
        "Regular Attack", "Step", "Make one melee or ranged attack.",
        "All-Out Attack (Strong)", "Step", "Pay 2 FP. +2 to damage.",
    ])
    assert "fp" not in rows[0]
    assert rows[1]["fp"] == 2


def test_intro_prose_above_a_bullet_list_is_not_read_as_a_bullet():
    """The Attack Options list opens with three unbulleted sentences; the
    bullet flag comes from the PDF's own U+007F, not from line shape."""
    rows = extract_combat.parse_attack_options([
        (False, "Unlike full maneuvers, Attack Options are modifiers."),
        (True, "Rapid Strike: Split a melee Attack maneuver. Both attacks"),
        (False, "suffer -6 to skill."),
    ])
    assert len(rows) == 1
    assert rows[0]["option"] == "Rapid Strike"
    assert rows[0]["rule"].endswith("-6 to skill.")


def test_extra_effort_must_stay_a_free_action_with_a_price():
    rows = extract_combat.parse_extra_effort([
        (True, "[Free Action] [Heroic Charge] Pay 2 FP: Ignore Charge Attack penalties (-4 to hit)"),
    ])
    assert rows == [{"option": "Heroic Charge", "fp": 2, "effect": "Ignore Charge Attack penalties (-4 to hit)"}]
    with pytest.raises(ValueError):
        extract_combat.parse_extra_effort([(True, "[Maneuver] [Heroic Charge] Pay 2 FP: x")])


def test_committed_maneuvers_are_the_fourteen_the_book_prints():
    entries = json.loads((CONTENT / "maneuvers.json").read_text(encoding="utf-8"))["entries"]
    assert [e["maneuver"] for e in entries] == extract_combat.EXPECTED_MANEUVERS
    # The five All-Out rows, and only those, cost FP.
    charged = {e["maneuver"] for e in entries if "fp" in e}
    assert charged == {n for n in extract_combat.EXPECTED_MANEUVERS if n.startswith("All-Out")}


def test_strong_blows_scope_sentence_is_pinned_whole():
    """Both halves of "Regular melee attacks only" are a reading, and the +2
    is a Kotlin constant. Either drifting has to fail here."""
    entries = json.loads((CONTENT / "extra_effort.json").read_text(encoding="utf-8"))["entries"]
    by_option = {e["option"]: e["effect"] for e in entries}
    assert by_option["Strong Blow"] == extract_combat.PINNED_EFFORT["Strong Blow"]
    # The maneuver row the word "Regular" points at is still printed.
    maneuvers = json.loads((CONTENT / "maneuvers.json").read_text(encoding="utf-8"))["entries"]
    assert "Regular Attack" in [e["maneuver"] for e in maneuvers]
    options = json.loads((CONTENT / "attack_options.json").read_text(encoding="utf-8"))["entries"]
    # Dropping half the scope, and ADDING a clause to it, both have to fail:
    # an appended sentence leaves the old one intact while making the Kotlin
    # scope stale, so a substring check would wave it through (Codex).
    for effect in ("+2 damage.", by_option["Strong Blow"] + " Cannot combine with Rapid Strike."):
        drifted = [dict(e, effect=effect) if e["option"] == "Strong Blow" else e for e in entries]
        with pytest.raises(ValueError):
            extract_combat.verify(maneuvers, drifted, options)


def test_the_stun_penalty_is_pinned_whole_and_corroborated():
    """The -4 is a Kotlin constant and the cell is quoted verbatim on screen.

    Its warrant is a SECOND printing outside chapter 5, which is what makes the
    penalty a property of being stunned rather than of the Do Nothing row it is
    printed on -- and which says "active defenses" plural."""
    maneuvers = json.loads((CONTENT / "maneuvers.json").read_text(encoding="utf-8"))["entries"]
    by_name = {e["maneuver"]: e["description"] for e in maneuvers}
    assert by_name["Do Nothing"] == extract_combat.PINNED_MANEUVERS_EXACT["Do Nothing"]

    effort = json.loads((CONTENT / "extra_effort.json").read_text(encoding="utf-8"))["entries"]
    options = json.loads((CONTENT / "attack_options.json").read_text(encoding="utf-8"))["entries"]
    # Dropping the second clause and appending a third both have to fail: the
    # screen quotes this cell, so either changes what a GM is shown.
    for text in ("If Stunned: -4 to Active Defense.", by_name["Do Nothing"] + " Roll HT to recover."):
        drifted = [dict(e, description=text) if e["maneuver"] == "Do Nothing" else e for e in maneuvers]
        with pytest.raises(ValueError):
            extract_combat.verify(drifted, effort, options)

    byn = extract_combat.load_lines()
    assert extract_combat.stun_corroboration(byn), "nothing outside ch. 5 prints the -4"
    with pytest.raises(ValueError):
        extract_combat.verify(maneuvers, effort, options, {1: "no such rule here"})


def test_the_numbers_Attacks_kt_implements_are_still_printed():
    """com.pathways.rules.Attacks is the only copy of these; if the book stops
    printing them, that is a finding, not a silent drift."""
    entries = json.loads((CONTENT / "maneuvers.json").read_text(encoding="utf-8"))["entries"]
    by_name = {e["maneuver"]: e["description"] for e in entries}
    assert "+4 to hit melee, +1 to hit ranged" in by_name["All-Out Attack (Determined)"]
    assert "-4 to hit" in by_name["Charge Attack"]

    options = json.loads((CONTENT / "attack_options.json").read_text(encoding="utf-8"))["entries"]
    by_option = {e["option"]: e["rule"] for e in options}
    assert "-6 to skill" in by_option["Rapid Strike"]
    assert "Maximum penalty is half your skill (rounded down)" in by_option["Deceptive Attack"]


# --- Unarmed attacks (Ch. 5, p. 38-39) -------------------------------------

import extract_unarmed


def test_a_wrapped_note_does_not_swallow_the_next_attack():
    """Rows are cut on the ROLL column, not by counting four lines each."""
    rows = extract_unarmed.parse_table("Brawling", [
        "Punch", "Brawling", "thr-1 cr", "Most common attack",
        "spread over two lines",
        "Kick", "Brawling-2", "thr cr", "Longer reach, slower",
    ])
    assert [r["attack"] for r in rows] == ["Punch", "Kick"]
    assert rows[0]["notes"] == "Most common attack spread over two lines"
    assert rows[1]["rollModifier"] == -2


def test_damage_parts_are_read_once_and_kept_beside_the_printed_cell():
    rows = extract_unarmed.parse_table("Boxing", [
        "Uppercut", "Boxing-2", "thr+2 cr", "Upward strike",
    ])
    assert rows[0]["base"] == "thr"
    assert rows[0]["modifier"] == 2
    assert rows[0]["type"] == "cr"
    assert rows[0]["damage"] == "thr+2 cr"


def test_a_special_damage_cell_carries_no_parts_at_all():
    """Shove knocks down; an attack dealing no damage is not one dealing 0."""
    rows = extract_unarmed.parse_table("Brawling", [
        "Shove", "Brawling-2", "Special", "Knock down: target rolls DX or fall",
    ])
    assert "base" not in rows[0]
    assert rows[0]["damage"] == "Special"


def test_an_unreadable_damage_cell_is_an_error_not_a_guess():
    with pytest.raises(ValueError):
        extract_unarmed.parse_table("Brawling", [
            "Bite", "Brawling-1", "lots cr", "not in the book",
        ])


def test_the_grappling_table_is_cut_on_its_own_roll_column():
    """Maneuver / Roll / Opposed Roll / Notes -- four columns, and the roll
    names a DIFFERENT skill on the last row, so the anchor cannot be one
    skill's name the way the two damage tables' is."""
    rows = extract_unarmed.parse_grappling([
        "Grapple", "Wrestling", "DX or Escape", "Initiate grapple; both roll DX",
        "Takedown", "Wrestling-1", "DX or Wrestling", "Knock down;",
        "target falls prone",
        "Escape", "Escape-2", "DX or Wrestling", "Break free from grapple or pin",
    ], damage="None (grapple only)")
    assert [(r["skill"], r["attack"], r["rollModifier"]) for r in rows] == [
        ("Wrestling", "Grapple", 0),
        ("Wrestling", "Takedown", -1),
        ("Escape", "Escape", -2),
    ]
    assert rows[0]["opposedRoll"] == "DX or Escape"
    assert rows[1]["notes"] == "Knock down; target falls prone"


def test_the_grappling_rows_carry_no_damage_parts():
    """The Wrestling table prints no Damage column at all. Its rows take the
    summary table's own cell for that skill and no parts -- Shove's shape,
    which is already in this file and is what disproves "no damage, no row"."""
    rows = extract_unarmed.parse_grappling(
        ["Pin", "Wrestling-2", "DX or Wrestling", "Immobilize; target cannot act"],
        damage="None (grapple only)",
    )
    assert "base" not in rows[0]
    assert rows[0]["damage"] == "None (grapple only)"


def test_only_the_grapplers_maneuvers_are_offered_as_attacks():
    """Three of the four rows are the GRAPPLER's; Escape-2 is rolled by the
    person being held. This table is keyed by the acting character's skill and
    consumed by an attack picker, so storing Escape here would offer a
    character with that skill an escape from a grapple they are not in. A gap,
    recorded -- not a refusal."""
    entries = json.loads(
        (CONTENT / "unarmed_attacks.json").read_text(encoding="utf-8"))["entries"]
    assert [e["attack"] for e in entries if e["skill"] == "Wrestling"] == [
        "Grapple", "Takedown", "Pin",
    ]
    assert not any(e["skill"] == "Escape" for e in entries)
    assert extract_unarmed.GRAPPLED_ROW == "Escape"


def test_committed_unarmed_attacks_match_the_two_printed_tables():
    entries = json.loads((CONTENT / "unarmed_attacks.json").read_text(encoding="utf-8"))["entries"]
    by_skill = {}
    for e in entries:
        by_skill.setdefault(e["skill"], []).append(e["attack"])
    assert by_skill == extract_unarmed.EXPECTED
    # Damage applies no wounding multiplier; that is only safe while every
    # listed unarmed attack is crushing thrust.
    wounding = [e for e in entries if "base" in e]
    assert {e["base"] for e in wounding} == {"thr"}
    assert {e["type"] for e in wounding} == {"cr"}


# --- Ranged combat (Ch. 5, p. 39-40) ---------------------------------------

import extract_ranged


def test_a_row_is_cut_on_the_modifier_sign_not_by_counting():
    rows = extract_ranged.pair([
        "Target is stationary", "+0",
        "Target walking (up to 3 meters/sec)", "-1",
    ])
    assert rows == [
        ("Target is stationary", "+0"),
        ("Target walking (up to 3 meters/sec)", "-1"),
    ]


def test_an_open_band_carries_no_upper_bound():
    bands = extract_ranged.parse_bands([
        "51–100 meters", "-5",
        "100+ meters", "-6 or worse (GM discretion)",
    ])
    assert bands[0]["from"] == 51 and bands[0]["to"] == 100
    assert bands[1]["from"] == 100 and "to" not in bands[1]
    assert bands[1]["gmDiscretion"] is True


def test_the_aim_rows_are_not_read_as_plus_two():
    """"+ weapon's Acc +2 (max Acc +2 total)" opens with a sign and contains
    +2 twice; neither is the modifier. The first is what the row adds ON TOP
    of the weapon's Acc and the second restates the ceiling, so anchoring to
    the Acc mention is what stops the restatement being read as a second
    bonus."""
    rows = extract_ranged.parse_modifiers([
        "Shooter used Aim for 3+ turns", "+ weapon's Acc +2 (max Acc +2 total)",
    ])
    assert rows[0]["weaponAcc"] is True
    assert "modifier" not in rows[0]
    assert rows[0]["accBonus"] == 2


def test_a_row_with_a_melee_value_keeps_both():
    rows = extract_ranged.parse_modifiers([
        "Target is prone", "-2 ranged (but -4 in melee)",
    ])
    assert rows[0]["modifier"] == -2
    assert rows[0]["melee"] == -4


def test_a_printed_range_keeps_both_ends():
    rows = extract_ranged.parse_modifiers([
        "Darkness or heavy fog", "-3 to -9 depending on severity",
    ])
    assert rows[0]["modifier"] == -3
    assert rows[0]["worstCase"] == -9


SPEED_ROWS = [
    "Target is stationary", "+0",
    "Target walking (up to 3 meters/sec)", "-1",
    "Target running (4+ meters/sec)", "-2",
]


def test_a_row_that_prints_the_targets_speed_is_marked():
    """Three rows are intervals of ONE quantity, by the bounds they print and
    by the word "stationary". A target has one speed, so Ranged.modifiers
    refuses two of them. The shooter's own movement is a separate fact."""
    rows = extract_ranged.parse_modifiers(SPEED_ROWS + ["Shooter moved this turn", "-2"])
    assert [r.get("targetSpeed") for r in rows] == [True, True, True, None]


def test_speeds_that_overlap_are_refused():
    """The reading rests on the printed bounds being disjoint: no speed is
    both "up to 3" and "4+". A re-extraction where they meet takes the
    warrant away, and that is the finding."""
    extract_ranged.verify_speeds(extract_ranged.parse_modifiers(SPEED_ROWS))
    met = [c.replace("up to 3", "up to 4") for c in SPEED_ROWS]
    with pytest.raises(ValueError, match="disjoint"):
        extract_ranged.verify_speeds(extract_ranged.parse_modifiers(met))
    with pytest.raises(ValueError, match="three rows"):
        extract_ranged.verify_speeds(extract_ranged.parse_modifiers(SPEED_ROWS[:4]))


def test_committed_speed_rows_are_the_three_the_book_prints():
    entries = json.loads((CONTENT / "ranged_modifiers.json").read_text(encoding="utf-8"))["entries"]
    assert [e["condition"] for e in entries if e.get("targetSpeed")] == SPEED_ROWS[::2]


def test_committed_bands_still_overlap_at_a_hundred_metres():
    """The book's own arithmetic: "51-100" is -5 and "100+" is -6, so exactly
    100 matches both. Recorded, never repaired -- Ranged.penaltyFor documents
    which wins."""
    entries = json.loads((CONTENT / "range_penalties.json").read_text(encoding="utf-8"))["entries"]
    assert len(entries) == 7
    closed = next(e for e in entries if e["printed"].startswith("51"))
    openband = next(e for e in entries if "to" not in e)
    assert closed["to"] == 100 and openband["from"] == 100


def test_committed_ranged_modifiers_are_the_nine_the_book_prints():
    entries = json.loads((CONTENT / "ranged_modifiers.json").read_text(encoding="utf-8"))["entries"]
    assert [e["condition"] for e in entries] == extract_ranged.EXPECTED_MODIFIERS
    assert sum(1 for e in entries if e.get("weaponAcc")) == 2


# --- Hit locations and wounding multipliers (Ch. 5, p. 40-41) ---------------

import extract_wounds


def test_a_row_is_cut_on_the_bare_signed_penalty():
    rows = extract_wounds.rows([
        "Torso", "+0", "×1", "Default location. No special effect.",
        "Skull", "-7", "×4", "Knockdown at -10; DR 2 (skull bone, stacks with helmet)",
    ])
    assert [(r[0], r[1]) for r in rows] == [("Torso", 0), ("Skull", -7)]
    assert rows[0][2] == ["×1", "Default location. No special effect."]


def test_an_effect_holding_a_signed_number_does_not_start_a_row():
    """"Knockdown at -5; risk of blindness" contains -5. It is not bare, so it
    cannot be mistaken for the penalty column -- which would shift every row
    below it by one."""
    rows = extract_wounds.rows([
        "Face", "-5", "×1", "Knockdown at -5; risk of blindness or disfigurement",
    ])
    assert len(rows) == 1 and rows[0][1] == -5


def test_the_neck_multiplier_wraps_and_varies_by_damage_type():
    """"Cr x1.5 / Cut x2 / Imp x2" prints across two lines. Reading the first
    number would call a cutting hit to the neck x1.5."""
    printed, effect = extract_wounds.split_cells([
        "Cr ×1.5 / Cut", "×2 / Imp ×2",
        "Knockdown roll at -5; choke possible if damage > HP/2",
    ])
    assert printed == "Cr ×1.5 / Cut ×2 / Imp ×2"
    assert effect.startswith("Knockdown roll")
    assert extract_wounds.parse_multiplier(printed) == (1.0, {"cr": 1.5, "cut": 2.0, "imp": 2.0})


def test_the_vitals_restriction_survives_its_line_break():
    """"x3 (imp/pi only)" wraps mid-bracket. Dropping "only)" as noise would
    triple a punch to the vitals, which the book says is x1."""
    printed, effect = extract_wounds.split_cells([
        "×3 (imp/pi", "only)", "Extra shock (adds +1 turn to stun duration); bleeding risk",
    ])
    assert printed == "×3 (imp/pi only)"
    assert effect.startswith("Extra shock")
    assert extract_wounds.parse_multiplier(printed) == (1.0, {"imp": 3.0, "pi": 3.0})


def test_a_plain_multiplier_reaches_every_damage_type():
    assert extract_wounds.parse_multiplier("×4") == (4.0, {})


def test_only_the_abbreviations_the_book_prints_are_emitted():
    """GURPS writes the piercing rows "pi-" and "pi+". This book never does,
    and a key it never wrote would answer for a row it never printed."""
    entries = json.loads((CONTENT / "wounding_multipliers.json").read_text(encoding="utf-8"))["entries"]
    assert [e["type"] for e in entries] == extract_wounds.EXPECTED_TYPES
    keyed = {e["type"]: e["abbreviation"] for e in entries if "abbreviation" in e}
    assert keyed == {"Crushing": "cr", "Cutting": "cut", "Impaling": "imp", "Piercing": "pi"}
    assert "abbreviation" not in next(e for e in entries if e["type"] == "Piercing (small)")


def test_committed_hit_locations_are_the_nine_the_book_prints():
    entries = json.loads((CONTENT / "hit_locations.json").read_text(encoding="utf-8"))["entries"]
    assert [e["target"] for e in entries] == extract_wounds.EXPECTED_LOCATIONS
    byname = {e["target"]: e for e in entries}
    assert byname["Torso"]["penalty"] == 0 and byname["Eye"]["penalty"] == -9
    # Only the two cells that vary by damage type carry byType.
    assert {t for t, e in byname.items() if "byType" in e} == {"Neck", "Vitals"}


def test_a_major_wound_row_is_cut_on_the_closed_result_column():
    """The Effect cells hold "-4" and "1d6"; a number rule would cut them
    wrong. The Result column is three known strings, so it is the anchor."""
    rows = extract_wounds.parse_major_wounds([
        "Success", "-4 to all rolls for 1 turn from shock and pain",
        "Failure", "Stunned for 1d6 seconds (roll HT each turn to recover early)",
        "Fail by 5+", "Knocked down and unconscious for 1d hours",
    ])
    assert [r["result"] for r in rows] == extract_wounds.RESULTS
    assert rows[2]["failBy"] == 5
    # The first two rows are reached by outcome, not by margin, so they carry
    # no margin at all rather than a margin of zero.
    assert "failBy" not in rows[0] and "failBy" not in rows[1]


def test_committed_major_wound_table_is_the_three_rows_printed():
    entries = json.loads((CONTENT / "major_wound.json").read_text(encoding="utf-8"))["entries"]
    assert [e["result"] for e in entries] == extract_wounds.RESULTS
    assert entries[0]["effect"].startswith("-4 to all rolls")


# --- Divination Arts (Ch. 6.5) ----------------------------------------------

import extract_divination


def test_the_overnight_time_cell_is_rejoined_across_a_mid_word_split():
    """The PDF cut "Overnight" into "Overnig" and "ht". No cell-shaped rule can
    see that; joining without a separator and checking the printed times can."""
    assert extract_divination.join_time(["Overnig", "ht"]) == "Overnight"
    assert extract_divination.join_time(["30 sec"]) == "30 sec"
    with pytest.raises(ValueError):
        extract_divination.join_time(["whenever"])


def test_a_wrapped_method_name_is_one_row_not_two():
    """"Dream" / "Interpretation" is one method. Read as two, every column
    below it shifts by a row."""
    rows = extract_divination.parse_methods([
        "Pendulum", "Yes/no questions", "1 min", "1", "The most common method.",
        "Coin", "Binary outcomes", "30 sec", "1", "Simplest method.",
        "Dowsing Rods", "Finding objects", "5 min", "1–2", "Uses L-shaped rods.",
        "Dream", "Interpretation", "Complex questions", "Overnig", "ht", "2", "Set an intent.",
        "Mirror Scrying", "Viewing distant places", "10 min", "2", "A darkened mirror.",
        "Crystal Ball", "Precise visions", "15 min", "2", "A quartz sphere.",
        "Tarot / Cards", "Pattern recognition", "10 min", "2", "A spread of cards.",
        "Bone Oracle", "Spirit communication", "15 min", "2", "Toss inscribed bones.",
    ])
    assert [r["method"] for r in rows] == extract_divination.METHODS
    dream = rows[3]
    assert dream["time"] == "Overnight" and dream["spi"] == 2
    assert rows[2]["spiMax"] == 2, "Dowsing Rods cost 1-2 SPI"
    assert "spiMax" not in rows[0]


def test_the_cross_reference_row_carries_no_modifier():
    """"See Step 1 table" is a pointer, not a number. Parsed as 0 it would
    silently drop the whole of Step 1."""
    rows = extract_divination.parse_modifiers([
        "Question difficulty", "See Step 1 table",
        "Target link (true name, possession, blood)", "+0 to +4 (same as ritual link rules)",
    ])
    assert rows[0]["crossReference"] is True and "modifier" not in rows[0]
    assert rows[1]["modifier"] == 0 and rows[1]["best"] == 4


def test_a_repeating_penalty_is_marked_as_one():
    rows = extract_divination.parse_modifiers([
        "Previous divination about the same subject (cumulative)", "-2 per repeat",
    ])
    assert rows[0]["modifier"] == -2 and rows[0]["perRepeat"] is True


def test_a_wrapped_difficulty_band_keeps_both_halves():
    rows = extract_divination.parse_difficulty([
        "Trivial — personal, present,", "simple", "+2", '"Is there danger behind this door?"',
        "Extreme — divine, fate-bound,", "cosmic", "-6 or worse",
        '"What is the true name of the Hidden One?"',
    ])
    assert rows[0]["band"] == "Trivial — personal, present, simple"
    assert rows[0]["modifier"] == 2 and "openEnded" not in rows[0]
    assert rows[1]["modifier"] == -6 and rows[1]["openEnded"] is True


def test_committed_clarity_table_has_one_row_named_by_outcome():
    entries = json.loads((CONTENT / "divination_clarity.json").read_text(encoding="utf-8"))["entries"]
    assert [e["result"] for e in entries] == extract_divination.CLARITY_RESULTS
    critical = [e for e in entries if e.get("isCritical")]
    assert len(critical) == 1
    assert "Critical Failure Table" in critical[0]["effect"]


def _divination_inputs():
    byn = extract_divination.load_lines()
    method_lines = extract_divination.stream(byn, *extract_divination.METHOD_RANGE)
    modifier_lines = extract_divination.stream(byn, *extract_divination.MODIFIER_RANGE)
    return (extract_divination.parse_methods(method_lines),
            extract_divination.parse_difficulty(
                extract_divination.stream(byn, *extract_divination.DIFFICULTY_RANGE)),
            extract_divination.parse_modifiers(modifier_lines),
            extract_divination.parse_clarity(
                extract_divination.stream(byn, *extract_divination.CLARITY_RANGE)),
            method_lines + modifier_lines,
            extract_divination.stream(byn, *extract_divination.SKILL_RANGE))


@needs_pdf
def test_divination_arts_defaults_to_spi_and_never_to_ritualistic_magic():
    """v6.9i: "Divination Arts defaults to SPI-4" and "a Beyonder cannot
    substitute one for the other". v6.9d's cross default (Ritualistic Magic-2
    at 12+, and vice versa) is gone, and losing either sentence fails."""
    methods, difficulty, modifiers, clarity, prose, skill = _divination_inputs()
    extract_divination.verify(methods, difficulty, modifiers, clarity, prose, skill)
    section = " ".join(skill)
    assert "Ritualistic Magic-2" not in section
    for phrase in (extract_divination.DEFAULT, extract_divination.NO_SUBSTITUTE):
        cut = [l.replace(phrase, "") for l in skill]
        with pytest.raises(ValueError, match="no longer prints"):
            extract_divination.verify(methods, difficulty, modifiers, clarity, prose, cut)


@needs_pdf
def test_the_divination_burn_is_declared_before_the_roll():
    """v6.9d let the diviner burn SPI "after seeing your result"; v6.9i says
    "before rolling". The push is gone with it."""
    methods, difficulty, modifiers, clarity, prose, skill = _divination_inputs()
    moved = [l.replace("before rolling", "after rolling") for l in prose]
    with pytest.raises(ValueError, match="rule the app applies is gone"):
        extract_divination.verify(methods, difficulty, modifiers, clarity, moved, skill)


def test_the_scope_and_support_tables_are_gone_with_their_source():
    for name in ("divination_scope.json", "divination_support.json"):
        assert not (CONTENT / name).exists(), name


def test_committed_divination_content_claims_the_default_sentence():
    """The -4 lives in Kotlin; the span is claimed so the rule stays
    findable (6af)."""
    body = json.loads((CONTENT / "divination_methods.json").read_text(encoding="utf-8"))
    assert list(extract_divination.SKILL_RANGE) in body["sourceLines"]["8"]


# --- Shields (Ch. 5, p. 37) -------------------------------------------------
