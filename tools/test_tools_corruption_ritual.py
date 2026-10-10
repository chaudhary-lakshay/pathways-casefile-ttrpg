"""Corruption, pools, Fright Checks and ritual extraction. Split out of test_tools.py."""
import json

import pytest

import extract_corruption

from testsupport import CONTENT


import extract_corruption


def test_cor_gain_rows_parse_each_printed_number_shape():
    rows = extract_corruption.parse_gained([
        "Using powers while drained of Spirituality", "1 CoR per use",
        "Seeing or Hearing things you're not supposed to", "1–3 CoR per session",
        "Advancing sequence without full digestion", "10+ CoR",
        "Ritual magic critical failure", "1d CoR",
    ])
    assert rows[0] == {
        "action": "Using powers while drained of Spirituality",
        "gain": "1 CoR per use", "minimum": 1, "maximum": 1, "per": "use",
    }
    assert rows[1]["minimum"] == 1 and rows[1]["maximum"] == 3
    # "10+" prints no ceiling; inventing one would cap a cost the book leaves open
    assert rows[2]["minimum"] == 10 and "maximum" not in rows[2]
    # "1d" prints no floor; averaging the die here would be invention, not extraction
    assert rows[3]["dice"] == "1d" and "minimum" not in rows[3]


def test_an_unparsed_cor_gain_is_reported_not_dropped():
    with pytest.raises(ValueError, match="unparsed CoR gain"):
        extract_corruption.parse_gained(["Some new action", "a fistful of CoR"])


def test_cor_thresholds_are_extracted_as_ritual_specific():
    """The book files the 3/5/8/10 ladder under "Corruption from Ritual Work"
    and calls them "additional ritual-specific thresholds". The design spec
    paraphrases them as general CoR thresholds; the book wins, so they ship
    under the ritual name and the disagreement stays visible."""
    entries = json.loads((CONTENT / "ritual_cor_thresholds.json").read_text(encoding="utf-8"))["entries"]
    assert [e["cor"] for e in entries] == [3, 5, 8, 10]
    assert "-1" in entries[0]["effect"]
    assert entries[3]["cor"] == 10  # NOT the max-CoR loss point: that is Will


def test_committed_cor_gained_covers_every_action_the_book_prints():
    entries = json.loads((CONTENT / "cor_gained.json").read_text(encoding="utf-8"))["entries"]
    assert len(entries) == 6
    advancing = [e for e in entries if e["action"].startswith("Advancing sequence")]
    assert advancing[0]["minimum"] == 10


# --- HP / SPI pool thresholds (Ch. 5) --------------------------------------

import extract_pools


def test_a_wrapped_effect_line_stays_with_its_own_level():
    """The 0 SPI effect wraps onto a second line. Pairing rows positionally
    would cut "Spirit Vision shuts off" loose and shift every later row."""
    rows = extract_pools.parse([
        "0 SPI",
        "Spiritual Exhaustion: All Beyonder abilities deactivate;",
        "Spirit Vision shuts off",
        "Below 0 SPI",
        "Soul Debt: +1 CoR per 1 point below 0.",
    ], "SPI")
    assert [r["level"] for r in rows] == ["0 SPI", "Below 0 SPI"]
    assert rows[0]["effect"].endswith("Spirit Vision shuts off")
    assert rows[1]["effect"].startswith("Soul Debt")


def test_a_level_with_no_effect_text_is_reported_not_shipped():
    with pytest.raises(ValueError, match="has no effect text"):
        extract_pools.parse(["0 HP", "-HP", "Roll vs. HT or die"], "HP")


def test_committed_pool_thresholds_match_the_printed_ladders():
    entries = json.loads((CONTENT / "pool_thresholds.json").read_text(encoding="utf-8"))["entries"]
    for pool, expected in extract_pools.EXPECTED.items():
        assert [e["level"] for e in entries if e["pool"] == pool] == expected


def test_fp_has_no_table_and_is_deliberately_absent():
    """The book gives FP thresholds only in prose, in two separate sections.
    Pools carries them as constants; a row here would be authored, not
    extracted."""
    entries = json.loads((CONTENT / "pool_thresholds.json").read_text(encoding="utf-8"))["entries"]
    assert {e["pool"] for e in entries} == {"HP", "SPI"}


# --- Fright Check tables (Ch. 5, p. 43-44) ---------------------------------

import extract_fright


def test_a_cell_is_not_ended_by_the_abbreviation_it_contains():
    """The 12 row reads "roll vs." mid-clause. Ending the cell at that period
    would cut every column of that row in half and shift the rest."""
    rows = extract_fright.parse_effects([
        "12",
        "Retching for (25 − HT) sec; roll vs.", "Will to recover.",
        "Ecstasy for (25 − Will) sec; roll vs.", "Will to recover.",
        "Dazed for (25 − IQ) sec; roll vs. IQ to", "recover.",
    ])
    assert rows[0]["fright"] == "Retching for (25 − HT) sec; roll vs. Will to recover."
    assert rows[0]["confusion"].endswith("roll vs. IQ to recover.")


def test_cells_of_unequal_length_still_split_three_ways():
    """22-23 runs to seven lines: 2 + 2 + 3. Splitting the lines evenly would
    mis-cut this row and 24-29 while looking correct everywhere else."""
    rows = extract_fright.parse_effects([
        "22–23",
        "New −15 pt disadvantage: Severe", "Phobia or Paranoia.",
        "New −15 pt disadvantage: Extreme", "Fanaticism or self-sacrificing Vow.",
        "New −15 pt disadvantage: Dementia", "(age-related confusion) or Deep", "Sleeper.",
    ])
    assert rows[0]["confusion"].endswith("or Deep Sleeper.")


def test_a_row_missing_a_column_is_reported_not_shipped():
    with pytest.raises(ValueError, match="parsed 2 cells"):
        extract_fright.parse_effects([
            "13–15", "Acquire a quirk.", "Acquire a devotion.",
        ])


def test_a_trigger_range_keeps_both_ends():
    rows = extract_fright.parse_triggers([
        "Encountering a monster or undead creature", "-2 to -6",
        "Outer God influence or manifestation", "-8 or worse",
    ])
    assert rows[0]["modifier"] == -2 and rows[0]["worstCase"] == -6
    # "-8 or worse" prints no floor; claiming one would cap the GM's worst case
    assert rows[1]["modifier"] == -8 and rows[1]["openEnded"] is True
    assert "worstCase" not in rows[1]


def test_committed_fright_bands_tile_without_gaps():
    entries = json.loads((CONTENT / "fright_effects.json").read_text(encoding="utf-8"))["entries"]
    assert [(e["low"], e["high"]) for e in entries] == extract_fright.EXPECTED_BANDS
    for lower, upper in zip(entries, entries[1:]):
        assert upper["low"] == lower["high"] + 1
    assert entries[-1]["high"] is None, "35+ has no ceiling"


def test_an_odd_trigger_stream_is_reported_not_silently_paired():
    with pytest.raises(ValueError, match="pairs must be even"):
        extract_fright.parse_triggers(["Witnessing a supernatural death", "-4", "A new row"])


def test_a_recovery_roll_is_anchored_on_the_roll_and_not_on_a_duration():
    """The attribute a cell ROLLS is not the only attribute it names.

    Row 12's fright cell counts down from HT and rolls Will; its awe cell
    names Will twice in two roles, "(25 - Will)" as the duration and "roll
    vs. Will" as the roll. A search for an attribute NAME finds the wrong
    token in two of the three, so the anchor is the roll's own phrase -- the
    6do rule that a candidate anchor colliding with another column's values
    is not an anchor.
    """
    rows = extract_fright.parse_effects([
        "12",
        "Retching for (25 − HT) sec; roll vs.", "Will to recover.",
        "Ecstasy for (25 − Will) sec; roll vs.", "Will to recover.",
        "Dazed for (25 − IQ) sec; roll vs. IQ to", "recover.",
    ])
    assert rows[0]["frightRecovery"] == {"attribute": "Will", "state": "Retching"}
    assert rows[0]["aweRecovery"] == {"attribute": "Will", "state": "Ecstasy"}
    assert rows[0]["confusionRecovery"] == {"attribute": "IQ", "state": "Dazed"}


def test_a_row_that_prints_no_roll_carries_no_recovery():
    """Six of the nine bands print an outcome and no way out of it.

    Emitting a recovery for them would let the screen offer a roll the book
    withholds, which is the same class as offering a defence it does not
    grant.
    """
    rows = extract_fright.parse_effects([
        "13–15",
        "Acquire a quirk: mild phobia or", "superstition.",
        "Acquire a quirk: fascination or", "devotion to the trigger.",
        "Acquire a quirk: persistent confusion", "or memory tic.",
    ])
    assert rows[0]["frightRecovery"] is None
    assert rows[0]["aweRecovery"] is None
    assert rows[0]["confusionRecovery"] is None


def test_the_stunned_rows_are_the_only_ones_the_stun_clause_reaches():
    """The state is the book's own leading word, so the engine never guesses.

    Rows 4-9 and 10-11 print "Stunned" in all three columns; row 12 prints
    three different states. Combat Reflexes' "+6 to recover from stun" is
    scoped on that word, and storing a boolean here would make this extractor
    decide what a stun IS rather than structure what the cell printed.
    """
    entries = json.loads((CONTENT / "fright_effects.json").read_text(encoding="utf-8"))["entries"]
    by_band = {e["printed"]: e for e in entries}
    states = {
        band: {
            col: (by_band[band][f"{col}Recovery"] or {}).get("state")
            for col in ("fright", "awe", "confusion")
        }
        for band in by_band
    }
    stunned = {b for b, cols in states.items() if set(cols.values()) == {"Stunned"}}
    assert len(stunned) == 2, f"expected two all-Stunned bands, got {stunned}"
    twelve = next(b for b in by_band if by_band[b]["low"] == 12)
    assert set(states[twelve].values()) == {"Retching", "Ecstasy", "Dazed"}
    # Every band with a recovery has one in ALL THREE columns or in none: the
    # table never gives one column a way out that another lacks.
    for band, cols in states.items():
        present = [c for c, v in cols.items() if v is not None]
        assert len(present) in (0, 3), f"{band} offers recovery in {present} only"


def test_an_unparsable_recovery_is_reported_not_dropped():
    """A cell that says "roll vs." and then something that is not an
    attribute is a parse that drifted, not a row with no recovery."""
    with pytest.raises(ValueError, match="recovery"):
        extract_fright.parse_effects([
            "4–9",
            "Stunned 1 sec; roll vs. Fortitude each", "sec to recover.",
            "Stunned 1 sec; roll vs. Will each sec to", "recover.",
            "Stunned 1 sec; roll vs. IQ each sec to", "recover.",
        ])


def test_a_computed_duration_is_anchored_on_the_parenthetical_not_on_the_roll():
    """The attribute a cell COUNTS DOWN FROM is not the one it rolls.

    Row 12's fright cell counts down from HT and rolls Will, so reusing the
    recovery anchor -- or searching for an attribute name -- answers Will for
    a duration the book computes from HT. Its awe cell prints Will in both
    roles and cannot tell the two readings apart, which is why the fright
    column is the one that decides this parse.
    """
    rows = extract_fright.parse_effects([
        "12",
        "Retching for (25 − HT) sec; roll vs.", "Will to recover.",
        "Ecstasy for (25 − Will) sec; roll vs.", "Will to recover.",
        "Dazed for (25 − IQ) sec; roll vs. IQ to", "recover.",
    ])
    assert rows[0]["frightComputedDuration"] == {"base": 25, "attribute": "HT"}
    assert rows[0]["aweComputedDuration"] == {"base": 25, "attribute": "Will"}
    assert rows[0]["confusionComputedDuration"] == {"base": 25, "attribute": "IQ"}


def test_a_printed_duration_with_no_attribute_in_it_is_not_computed():
    """Rows 4-9 print "1 sec" and 10-11 "1d-2d sec".

    Those are durations too, and neither is a function of the character: one
    is already resolved in the cell and the other is a range the GM rolls.
    The boundary is the table's own -- a duration with a STAT OPERAND is the
    only kind this app can work out -- so the other two stay prose rather
    than being structured into a field nothing could compute.
    """
    stunned = extract_fright.parse_effects([
        "4–9",
        "Stunned 1 sec; roll vs. Will each", "sec to recover.",
        "Stunned 1 sec; roll vs. Will each sec to", "recover.",
        "Stunned 1 sec; roll vs. IQ each sec to", "recover.",
    ])
    dice = extract_fright.parse_effects([
        "10–11",
        "Stunned 1d–2d sec; roll vs. Will to", "recover.",
        "Stunned 1d–2d sec; roll vs. Will to", "recover.",
        "Stunned 1d–2d sec; roll vs. IQ to", "recover.",
    ])
    for rows in (stunned, dice):
        for column in ("fright", "awe", "confusion"):
            assert rows[0][f"{column}ComputedDuration"] is None


def test_a_computed_duration_naming_an_unknown_attribute_is_reported():
    """A parenthetical this book prints no attribute for is a parse that
    drifted, not a cell whose duration is fixed."""
    with pytest.raises(ValueError, match="duration"):
        extract_fright.parse_effects([
            "12",
            "Retching for (25 − Fortitude) sec; roll", "vs. Will to recover.",
            "Ecstasy for (25 − Will) sec; roll vs.", "Will to recover.",
            "Dazed for (25 − IQ) sec; roll vs. IQ to", "recover.",
        ])


def test_the_committed_row_counts_down_from_an_attribute_it_does_not_roll():
    """The one band with a computed duration, and the seam that proves the
    two anchors did not collapse.

    Asserted against the committed file rather than a hand-typed cell: what
    would be wrong is the duration reading the recovery's attribute, and that
    is only visible where the book prints two different ones on one cell.
    """
    entries = json.loads((CONTENT / "fright_effects.json").read_text(encoding="utf-8"))["entries"]
    computed = [
        e["printed"] for e in entries
        if any(e.get(f"{c}ComputedDuration") for c in ("fright", "awe", "confusion"))
    ]
    assert computed == ["12"], f"expected only band 12 to compute a duration, got {computed}"
    row = next(e for e in entries if e["printed"] == "12")
    assert row["frightComputedDuration"]["attribute"] == "HT"
    assert row["frightRecovery"]["attribute"] == "Will"
    # All three bases are the same number today; if a re-extraction ever splits
    # them the arithmetic below stops being one rule.
    assert {row[f"{c}ComputedDuration"]["base"] for c in ("fright", "awe", "confusion")} == {25}


# --- Ritual resolution (Ch. 7, p. 67-68) -----------------------------------

import extract_ritual


def test_every_ritual_weight_carries_its_printed_SPI_cost():
    """One cell is fixed, one a range, one open at the top. The app charged
    nothing for a ritual until these were read."""
    costs = extract_ritual.parse_weight_costs([
        "Light", "+0", "2 SPI", "Divination, minor communication",
        "Moderate", "-2", "4–6 SPI", "Enhancement, curse",
        "Heavy", "-4", "8+ SPI", "Summoning, binding",
    ])
    assert costs["Light"] == (2, None, False)
    assert costs["Moderate"] == (4, 6, False)
    assert costs["Heavy"] == (8, None, True)


def test_a_preparation_row_keeps_both_ends_of_its_range():
    rows = extract_ritual.parse_preparation([
        "Excellent", "+3", "Sanctified space, rare materials",
        "Good", "+1 to +2", "Domain-matched candles, proper altar",
        "None", "-4 or worse", "Open street, wrong materials",
    ])
    assert rows[0]["modifier"] == 3 and "best" not in rows[0]
    assert rows[1]["modifier"] == 1 and rows[1]["best"] == 2
    assert rows[2]["modifier"] == -4 and rows[2]["openEnded"] is True


def test_a_wrapped_preparation_description_stays_with_its_row():
    rows = extract_ritual.parse_preparation([
        "Excellent", "+3", "Sanctified space, rare domain-matched materials,",
        "celestial timing, perfect link",
        "Adequate", "+0", "Correct materials, clean space",
    ])
    assert rows[0]["looksLike"].endswith("perfect link")
    assert rows[1]["quality"] == "Adequate"


def test_the_flourish_bonus_is_read_across_the_line_it_wraps_on():
    """The words naming the bonus and the number are on different lines.

    Neither line alone states the rule -- one has a name with no value, the
    other a value with no name -- so a line-at-a-time scan reads it as absent.
    """
    bonus, joined = extract_ritual.parse_flourish([
        "Phase II — Conduct",
        "The player narrates the incantation (4-part Hermes structure — see "
        "reference below). The GM may award a Flourish Bonus",
        "(+1) for good in-character delivery.",
        extract_ritual.FLOURISH_FORMULA,
    ])
    assert bonus == 1
    assert extract_ritual.FLOURISH_FORMULA in joined


def test_the_flourish_is_not_read_off_the_first_bracket_in_the_paragraph():
    """The line above it ends "(4-part Hermes structure ...)". A rule that took
    the first bracketed number would read the structure, not the bonus."""
    with pytest.raises(ValueError, match="Flourish Bonus"):
        extract_ritual.parse_flourish([
            "The player narrates the incantation (4-part Hermes structure).",
            "The GM may award a bonus for good in-character delivery.",
        ])


def test_the_flourish_scoping_rests_on_the_quick_ritual_not_naming_it():
    """FULL-only is an argument from absence, so both halves are asserted."""
    phase_two = (
        "The GM may award a Flourish Bonus (+1) for good in-character delivery. "
        + extract_ritual.FLOURISH_FORMULA
    )
    quick = ("Step 3 — Apply Modifiers Sum the following two modifiers: "
             "1. Preparation Quality 2. Target Link")
    canon = ("The novel's full procedure is preserved here as a reference. "
             "Wrong timing for the entity gives -1 to -2.")
    preparation = [{"looksLike": "Sanctified space, celestial timing, perfect link"}]
    extract_ritual.verify_flourish(1, phase_two, quick, canon, preparation)

    # A Quick ritual that named it would make RitualMode's guard wrong.
    with pytest.raises(ValueError, match="Full-mode only"):
        extract_ritual.verify_flourish(
            1, phase_two, quick + " Flourish Bonus", canon, preparation)
    # The formula is the only evidence the bonus reaches the ROLL.
    with pytest.raises(ValueError, match="effective-skill formula"):
        extract_ritual.verify_flourish(
            1, "The GM may award a Flourish Bonus (+1).", quick, canon, preparation)
    # And the canon timing penalty is refused only while it is still printed.
    with pytest.raises(ValueError, match="Wrong timing"):
        extract_ritual.verify_flourish(
            1, phase_two, quick, "preserved here as a reference", preparation)
    with pytest.raises(ValueError, match="celestial timing"):
        extract_ritual.verify_flourish(
            1, phase_two, quick, canon, [{"looksLike": "Sanctified space"}])


def test_committed_ritual_content_claims_the_flourish_sentence():
    """An assertion whose source sentence is unclaimed is a rule nobody can
    find again -- the bookkeeping the PCR formula needed in 6af."""
    body = (CONTENT / "ritual_preparation.json").read_text(encoding="utf-8")
    assert [4140, 4143] in json.loads(body)["sourceLines"]["7"]
    # The +1 is a number inside a sentence, so it lives in Kotlin. A column
    # here would make the book look like it printed a table it never did.
    assert "flourish" not in body.lower()


HERMES_PART_LINES = [
    "\x7f Part 1 — Invocation: A prayer for someone's power. Replace 'someone' "
    "with the entity's honorific name, symbol,",
    "or domain. E.g.: \"The existence that rules the concealed path and controls fate...\"",
    "\x7f Part 2 — Grace: \"I pray for the God's loving grace.\"",
    "\x7f Part 3 — Request: What you pray for. Must be brief — finish in one sentence.",
    "\x7f Part 4 — Empowerment: Invoke a specific herb or ingredient to empower "
    "the request. E.g.: \"Sun Flower, a herb",
    "that belongs to the Sun. Please bestow your powers to my incantation.\"",
]
HERMES_INTRO = ["The standard incantation has four parts, spoken in Hermes:"]
HERMES_NOTE = [
    "As long as the four-part structure is followed and the key meaning is "
    "expressed in Hermes, the rest can be left to the caster's",
    "creativity. For Praying to Oneself, replace the four-part structure with "
    "the three-line self-description (see Section IV —",
    "Personal Spirituality).",
]
SELF_LINES = [
    "Praying to Oneself — Ritualistic magic can also be directed inward, "
    "drawing power from your own spirituality without",
    "petitioning any god. To do so, craft a three-line description of your "
    "being that precisely identifies your spiritual location",
    "within the Wall of Spirituality. For example: \"Cordu Village's Trickster "
    "King, Aurore Lee's younger brother, An entity known",
    "as Lumian Lee...\" The benefit is total independence from divine "
    "constraints — the weakness is that the result is limited by your",
    "personal power. A weak Beyonder gets a weak result.",
]
SUMMONING_LINES = [
    "In order to summon a mystical being one needs to discover or create a "
    "three-line incantation that points to a specific spiritual",
    "creature. The first line should be the resident realm of the creature "
    "(The Unfounded, the Spirit Realm, the Darkness, etc.). The",
    "second and third lines should be accurate descriptors of the creature.",
]


def test_the_four_parts_survive_the_two_that_wrap():
    """Parts 1 and 4 each carry their EXAMPLE on a second line.

    A line-at-a-time read returns six parts, two of them fragments beginning
    "or domain." and "that belongs to the Sun." -- and the two parts whose
    worked example is the whole reason to print them lose it.
    """
    parts = extract_ritual.parse_parts(HERMES_PART_LINES)
    assert len(parts) == 4
    assert [p.split(":")[0] for p in parts] == [
        "Part 1 — Invocation", "Part 2 — Grace",
        "Part 3 — Request", "Part 4 — Empowerment",
    ]
    assert parts[0].endswith('controls fate..."')
    assert parts[3].endswith('powers to my incantation."')


def test_the_three_printed_incantation_structures_are_kept_apart():
    """Three structures, two of them three-line, and they are NOT one row.

    The self-description identifies the caster's own spiritual location; the
    summoning incantation identifies a creature by realm and descriptors.
    Merging them on the shared shape would print a rule the book does not.
    """
    entries = extract_ritual.build_incantations(
        HERMES_INTRO, HERMES_PART_LINES, HERMES_NOTE, SELF_LINES, SUMMONING_LINES)
    # The scopes as the page prints them, NOT extract_ritual's own constant --
    # comparing a builder's output with the list it built from cannot fail.
    assert [e["scope"] for e in entries] == [
        "Invocation", "Praying to Oneself", "Summoning a spiritual creature",
    ]
    assert [len(e["parts"]) for e in entries] == [4, 0, 0]
    # The book's own join, stored where it selects.
    assert "replace the four-part structure" in entries[0]["note"]
    assert "Lumian Lee" in entries[1]["text"]
    assert "resident realm of the creature" in entries[2]["text"]
    # The note is printed AFTER the parts and is kept apart from the intro so
    # the screen can render the page's own order.
    assert entries[0]["text"].endswith("spoken in Hermes:")
    # Only chapter 7's pair is selectable by the power source the app models.
    assert [e.get("referenceOnly") for e in entries] == [None, None, True]


def test_phase_two_still_names_the_structure_the_screen_shows():
    """The cross-reference is the whole warrant for showing it there.

    Phase II's own sentence says "(4-part Hermes structure — see reference
    below)". The app claimed that sentence and rendered only its second half,
    so the Flourish chip awarded +1 for narrating a structure nothing showed.
    If the book stops naming it, the reference beside that chip is no longer
    the one Phase II points at.
    """
    entries = extract_ritual.build_incantations(
        HERMES_INTRO, HERMES_PART_LINES, HERMES_NOTE, SELF_LINES, SUMMONING_LINES)
    phase_two = (
        "The player narrates the incantation (4-part Hermes structure — see "
        "reference below). The GM may award a Flourish Bonus (+1) for good "
        "in-character delivery."
    )
    extract_ritual.verify_incantations(entries, phase_two)

    with pytest.raises(ValueError, match="no longer names"):
        extract_ritual.verify_incantations(
            entries, "The player narrates the incantation. The GM may award a "
                     "Flourish Bonus (+1).")
    # And a structure that lost a part is a structure nobody can recite.
    short = [dict(e) for e in entries]
    short[0]["parts"] = short[0]["parts"][:3]
    with pytest.raises(ValueError, match="four parts"):
        extract_ritual.verify_incantations(short, phase_two)


def test_committed_incantations_claim_all_three_printings():
    """Two chapters, three spans. Chapter 7.5 is claimed by nothing else at
    all, and this is its first."""
    body = json.loads((CONTENT / "incantations.json").read_text(encoding="utf-8"))
    assert body["sourceLines"]["7"] == [[3992, 3996], [4195, 4204]]
    assert body["sourceLines"]["7.5"] == [[4718, 4720]]


def test_committed_ritual_content_carries_cost_and_preparation():
    weights = json.loads((CONTENT / "ritual_weights.json").read_text(encoding="utf-8"))["entries"]
    assert {w["weight"]: w["spi"] for w in weights} == {"Light": 2, "Moderate": 4, "Heavy": 8}
    assert next(w for w in weights if w["weight"] == "Heavy")["spiOpen"] is True
    prep = json.loads((CONTENT / "ritual_preparation.json").read_text(encoding="utf-8"))["entries"]
    assert [p["quality"] for p in prep] == extract_ritual.PREPARATIONS


def test_the_critical_failure_row_carries_no_margin_band():
    """"Critical Failure (roll 17-18)" is entered by the ROLL, not by a margin.
    Giving it a numeric band would let a margin lookup match it and skip the
    real severe-misfire row."""
    rows = extract_ritual.parse_failures([
        "Failure by 10+", "Severe misfire. Roll on Critical Failure Table with -2 to the result roll.",
        "Critical Failure (roll 17-18)", "Roll on Critical Failure Table immediately.",
    ])
    assert rows[0]["from"] == 10 and rows[0]["to"] is None
    assert rows[1]["critical"] is True
    assert "from" not in rows[1]


def test_wrapped_ritual_prose_stays_with_its_own_row():
    rows = extract_ritual.parse_weights([
        "Moderate (-2)",
        "Enhancement, Curse, Cleansing, Marking, Fabrication, Concealment, Oath-Sealing,",
        "Protection/Warding",
        "Heavy (-4)",
        "Summoning, Binding, Unraveling, Affliction, Transference, Soul-Anchoring",
    ])
    assert rows[0]["categories"].endswith("Oath-Sealing, Protection/Warding")
    assert rows[1]["modifier"] == -4


def test_committed_critical_table_covers_every_3d6_result():
    entries = json.loads((CONTENT / "ritual_critical.json").read_text(encoding="utf-8"))["entries"]
    covered = {n for e in entries for n in range(e["low"], e["high"] + 1)}
    assert covered == set(range(3, 19))


def test_the_critical_table_is_not_ordered_by_severity():
    """3-4 is Backlash and 18 is Catastrophe, but 13-14 (gain CoR) sits above
    11-12 (Wrong Arrival). The book shifts this roll by -2 on a severe
    misfire, so a caller assuming monotonic severity reads the shift
    backwards."""
    entries = json.loads((CONTENT / "ritual_critical.json").read_text(encoding="utf-8"))["entries"]
    outcomes = {e["low"]: e["outcome"].split(".")[0] for e in entries}
    assert outcomes[3] == "Backlash"
    assert outcomes[11] == "Wrong Arrival"
    assert outcomes[13] == "Corruption"
    assert outcomes[18] == "Catastrophe"


def test_the_cor_a_critical_row_charges_is_read_from_its_prose():
    rows = extract_ritual.parse_critical([
        "13-14", "Corruption. Gain 1 CoR. The ritual still fails.",
        "17", "Soul Exposure. Gain 2 CoR. Nightmares for 1d weeks.",
        "18", "Catastrophe. The GM invents something terrible.",
    ])
    assert rows[0]["corGained"] == 1
    assert rows[1]["corGained"] == 2
    # A row that charges nothing carries no key at all rather than a 0: the
    # book charging no corruption is not the same as charging zero.
    assert "corGained" not in rows[2]


def test_committed_critical_rows_charge_what_the_book_prints():
    entries = json.loads((CONTENT / "ritual_critical.json").read_text(encoding="utf-8"))["entries"]
    charged = {e["printed"]: e.get("corGained") for e in entries if e.get("corGained")}
    assert charged == {"13-14": 1, "15-16": 1, "17": 2}


# --- Active defenses (Ch. 5, p. 36-37) -------------------------------------
