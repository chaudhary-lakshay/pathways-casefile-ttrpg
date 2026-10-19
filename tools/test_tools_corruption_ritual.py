"""Corruption, pools, Fright Checks and ritual extraction. Split out of test_tools.py."""
import json

import pytest

import extract_corruption

from testsupport import CONTENT, needs_pdf


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
    """v6.9i prices advancing without full digestion at 1d6 CoR, where v6.9d
    printed "10+ CoR"."""
    entries = json.loads((CONTENT / "cor_gained.json").read_text(encoding="utf-8"))["entries"]
    assert len(entries) == 6
    advancing = [e for e in entries if e["action"].startswith("Advancing sequence")]
    assert advancing[0]["dice"] == "1d6" and "minimum" not in advancing[0]


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


# --- Ritual resolution (Ch. 9, p. 60-63) -----------------------------------

import extract_ritual


def _ritual_entries(name):
    return json.loads((CONTENT / f"{name}.json").read_text(encoding="utf-8"))["entries"]


def test_every_ritual_weight_carries_its_printed_SPI_cost():
    """One cell is fixed, one a range, two open at the top."""
    rows = extract_ritual.parse_quick_table([
        "Light", "+0", "10 min", "2 SPI",
        "Moderate", "-2", "30 min", "4–6 SPI",
        "Heavy", "-4", "1 hr", "8+ SPI",
    ])
    assert [(r["spi"], r.get("spiMax"), r.get("spiOpen")) for r in rows] == [
        (2, None, None), (4, 6, None), (8, None, True)]
    assert rows[1]["baseTime"] == "30 min"


def test_a_weight_row_with_no_base_difficulty_is_reported():
    with pytest.raises(ValueError, match="prints no base difficulty"):
        extract_ritual.parse_quick_table(["Light", "10 min", "2 SPI", "x"])


def test_the_two_weight_printings_must_agree():
    """Sec. II and Sec. III.A both print Light/Moderate/Heavy; a disagreement
    is two tables, and the extraction fails rather than picking one."""
    quick = [{"weight": "Light", "modifier": 0, "baseTime": "10 min", "spi": 2}]
    with pytest.raises(ValueError, match="disagree"):
        extract_ritual.build_weights(quick, {"Light": {"modifier": 0, "spi": 3, "categories": "x"}})


def test_major_is_the_weight_section_two_does_not_list():
    """Major is printed in Sec. III.A only, so it ships with no categories
    rather than borrowed ones."""
    weights = _ritual_entries("ritual_weights")
    assert {w["weight"]: w["spi"] for w in weights} == {
        "Light": 2, "Moderate": 4, "Heavy": 8, "Major": 12}
    major = next(w for w in weights if w["weight"] == "Major")
    assert major["modifier"] == -6 and "categories" not in major and major["spiOpen"]
    assert all(w["categories"] for w in weights if w["weight"] != "Major")


def test_a_wrapped_category_cell_stays_with_its_own_row():
    rows = extract_ritual.parse_categories([
        "Moderate", "-2", "4–6 SPI",
        "Enhancement, Curse, Cleansing, Marking, Fabrication, Concealment, Oath-Sealing,",
        "Protection/Warding",
        "Heavy", "-4", "8+ SPI", "Summoning, Binding",
    ])
    assert rows["Moderate"]["categories"].endswith("Oath-Sealing, Protection/Warding")
    assert rows["Heavy"]["modifier"] == -4


def test_a_preparation_row_keeps_both_ends_of_its_range():
    """Phase I prints the ladder as prose now: no descriptive column."""
    rows = extract_ritual.parse_preparation(
        "Preparation Quality: Excellent +3, Good +1 to +2, None -4 or worse.")
    assert rows[0]["modifier"] == 3 and "best" not in rows[0]
    assert rows[1]["modifier"] == 1 and rows[1]["best"] == 2
    assert rows[2]["modifier"] == -4 and rows[2]["openEnded"] is True
    assert all("looksLike" not in r for r in rows)


def test_committed_preparation_is_the_full_ladder():
    prep = _ritual_entries("ritual_preparation")
    assert [p["quality"] for p in prep] == extract_ritual.PREPARATIONS
    assert [p["modifier"] for p in prep] == [3, 1, 0, -1, -4]


def _full_inputs():
    byn = extract_ritual.load_lines()
    full = " ".join(extract_ritual.stream(byn, *extract_ritual.FULL_RANGE))
    quick = " ".join(extract_ritual.stream(byn, *extract_ritual.QUICK_RANGE))
    success, starts = extract_ritual.parse_outcomes(
        extract_ritual.stream(byn, *extract_ritual.OUTCOME_RANGE))
    failures = _ritual_entries("ritual_failures")
    return full, quick, success, starts, failures


@needs_pdf
def test_the_flourish_scoping_rests_on_the_quick_ritual_not_naming_it():
    """FULL-only is an argument from absence, so both halves are asserted."""
    full, quick, success, starts, failures = _full_inputs()
    extract_ritual.verify_full(full, quick, success, starts, failures)
    with pytest.raises(ValueError, match="Quick ritual now names"):
        extract_ritual.verify_full(full, quick + " Flourish", success, starts, failures)
    # The formula is the only evidence the bonus reaches the ROLL.
    with pytest.raises(ValueError, match="no longer prints"):
        extract_ritual.verify_full(
            full.replace(extract_ritual.FLOURISH_FORMULA, ""), quick, success, starts, failures)


@needs_pdf
def test_the_ritual_burn_is_declared_before_the_roll():
    """v6.9i: "Before rolling ... spend additional SPI (1 SPI = +1, no cap)".
    There is no post-roll push any more."""
    full, quick, success, starts, failures = _full_inputs()
    with pytest.raises(ValueError, match="before rolling"):
        extract_ritual.verify_full(
            full, quick.replace("Before rolling", "After rolling"), success, starts, failures)


def test_committed_ritual_content_claims_the_flourish_sentence():
    """The +1 is a number inside a sentence, so it lives in Kotlin. A column
    here would make the book look like it printed a table it never did."""
    body = (CONTENT / "ritual_preparation.json").read_text(encoding="utf-8")
    assert [3962, 3969] in json.loads(body)["sourceLines"]["9"]
    assert "flourish" not in body.lower()


SUMMONING_LINES = [
    "\x7f Part 1: \"I.\" — spoken in Ancient Hermes, Jotun, Dragonese, or Elvish.",
    "\x7f Part 2: \"I summon in my name.\" — spoken in Hermes.",
    "\x7f Part 3: A three-line description of the creature: Line 1 points to the Spirit World",
    "(e.g., \"Roaming in the upper realm\"). Lines 2–3 accurately describe the creature.",
    "The summoner cannot use too many words. Ambiguity makes it difficult",
    "to predict which creature will answer.",
]
PHASE_TWO = ("The player narrates the incantation (4-part Hermes structure: "
             "Invocation, Grace, Request, Empowerment).")


def test_the_summoning_parts_survive_the_one_that_wraps():
    """Part 3 carries its example on a second line; a line-at-a-time read
    returns a fourth part beginning "(e.g." and Part 3 loses it."""
    entries = extract_ritual.build_incantations(
        PHASE_TWO, ["The incantation has three parts:"] + SUMMONING_LINES)
    parts = entries[1]["parts"]
    assert [p[:6] for p in parts] == ["Part 1", "Part 2", "Part 3"]
    assert parts[2].endswith("accurately describe the creature.")
    assert entries[1]["note"].startswith("The summoner cannot use too many words")


def test_the_two_incantation_structures_are_kept_apart():
    """The Hermes structure is named by its parts only; the summoning one is
    reference text the app shows and never selects."""
    entries = extract_ritual.build_incantations(
        PHASE_TWO, ["The incantation has three parts:"] + SUMMONING_LINES)
    assert [e["scope"] for e in entries] == ["Invocation", "Summoning a spiritual creature"]
    assert entries[0]["parts"] == ["Invocation", "Grace", "Request", "Empowerment"]
    assert [e.get("referenceOnly") for e in entries] == [None, True]


def test_phase_two_must_still_name_the_hermes_structure():
    with pytest.raises(ValueError, match="no longer names"):
        extract_ritual.build_incantations("The player narrates the incantation.",
                                          ["The incantation has three parts:"] + SUMMONING_LINES)


def test_praying_to_oneself_is_gone():
    """v6.9i deleted the three-line self-description; no structure has that
    scope any more."""
    body = (CONTENT / "incantations.json").read_text(encoding="utf-8")
    assert "Praying to Oneself" not in body
    assert json.loads(body)["sourceLines"] == {"9": [[4130, 4138]]}


def test_the_critical_failure_row_carries_no_margin_band():
    """"Critical Failure" is entered by the ROLL, not by a margin. Giving it a
    numeric band would let a margin lookup match it."""
    rows = extract_ritual.parse_failures([
        "Failure by 10+", "Severe misfire. Roll on Critical Failure Table with -2 to the result roll.",
        "Critical Failure", "Roll on Critical Failure Table immediately.",
    ])
    assert rows[0]["from"] == 10 and rows[0]["to"] is None
    assert rows[1]["critical"] is True
    assert "from" not in rows[1]


def test_committed_critical_table_covers_every_3d6_result():
    entries = _ritual_entries("ritual_critical")
    covered = {n for e in entries for n in range(e["low"], e["high"] + 1)}
    assert covered == set(range(3, 19))


def test_the_critical_table_is_not_ordered_by_severity():
    """13-14 (gain CoR) sits above 11-12 (Wrong Arrival). The book shifts this
    roll by -2 on a severe misfire, so a caller assuming monotonic severity
    reads the shift backwards."""
    outcomes = {e["low"]: e["outcome"].split(".")[0] for e in _ritual_entries("ritual_critical")}
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
    # Charging no corruption is not the same as charging zero.
    assert "corGained" not in rows[2]


def test_committed_critical_rows_charge_what_the_book_prints():
    charged = {e["printed"]: e.get("corGained")
               for e in _ritual_entries("ritual_critical") if e.get("corGained")}
    assert charged == {"13-14": 1, "15-16": 1, "17": 2}


def test_the_revelation_rows_carry_their_reductions():
    """Seven Lights -20, -5 per sacrifice; Deities -40, reducible to -10 with
    an advantage chapter 3 does not print."""
    rows = _ritual_entries("ritual_revelation")
    assert [(r["penalty"], r.get("perSacrifice"), r.get("reducibleTo")) for r in rows] \
        == [(-20, 5, None), (-40, None, -10)]
    assert extract_ritual.BLESSED_BY_DEITY in rows[1]["reduction"]
    advantages = json.loads((CONTENT / "advantages.json").read_text(encoding="utf-8"))["entries"]
    assert all(a["name"] != extract_ritual.BLESSED_BY_DEITY for a in advantages)


def test_the_contract_table_grades_every_margin_once():
    """Sec. VI's margin table: "Success by 0 (exact)" is its own row, and the
    two failure rows carry no margin."""
    contract = _ritual_entries("ritual_contract")[0]
    assert [(o.get("from"), o.get("to")) for o in contract["outcomes"]] == [
        (10, None), (7, 9), (5, 6), (3, 4), (1, 2), (0, 0), (None, None), (None, None)]
    assert (contract["spi"], contract["time"], contract["maxContracts"],
            contract["maxContractsScrollsProfessor"]) == (3, "30 minutes", 4, 8)
    assert "subtract the result" in contract["difficulty"]


@needs_pdf
def test_a_dropped_contract_band_would_fail_verification():
    byn = extract_ritual.load_lines()
    contract = extract_ritual.parse_contract(
        extract_ritual.stream(byn, *extract_ritual.CONTRACT_RANGE))
    extract_ritual.verify_contract(contract)
    contract["outcomes"] = [o for o in contract["outcomes"] if o.get("from") != 0]
    with pytest.raises(ValueError, match="margin bands"):
        extract_ritual.verify_contract(contract)


@needs_pdf
def test_losing_the_per_contract_die_sentence_would_fail():
    byn = extract_ritual.load_lines()
    lines = [l.replace("roll an extra 1d6", "take -2")
             for l in extract_ritual.stream(byn, *extract_ritual.CONTRACT_RANGE)]
    with pytest.raises(ValueError, match="no longer prints"):
        extract_ritual.parse_contract(lines)


# --- Active defenses (Ch. 5, p. 36-37) -------------------------------------
