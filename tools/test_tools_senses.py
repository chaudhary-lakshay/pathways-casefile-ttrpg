"""Digestion, anti-divination, detection and spirituality. Split out of test_tools.py."""
import json

import pytest

from testsupport import CONTENT, needs_pdf


import extract_digestion


@needs_pdf
def test_the_pcr_formula_sentence_is_pinned_beside_its_table():
    """extract_digestion asserts effectiveTarget == 10 + modifier. That check is
    only meaningful while the book still PRINTS the formula, so the sentence is
    claimed alongside the table rather than left outside the span."""
    byn = extract_digestion.load_lines()
    formula = " ".join(extract_digestion.stream(
        byn, *extract_digestion.FORMULA_RANGE))
    assert extract_digestion.FORMULA in formula
    assert extract_digestion.RAW_ROLL in formula
    acting = extract_digestion.parse_acting(extract_digestion.stream(
        byn, *extract_digestion.ACTING_RANGE))
    pcr = extract_digestion.parse_pcr(extract_digestion.stream(
        byn, *extract_digestion.PCR_RANGE))
    with pytest.raises(ValueError):
        extract_digestion.verify(acting, pcr, "the formula went away")


def test_the_pcr_claims_the_prose_and_the_table():
    spans = json.loads((CONTENT / "potion_consumption.json")
                       .read_text(encoding="utf-8"))["sourceLines"]["6"]
    assert spans == [[3079, 3081], [3082, 3119]]


import extract_anti_divination

IMMUNITY = json.loads(
    (CONTENT / "sequence_immunity.json").read_text(encoding="utf-8"))["entries"]
COUNTERS = json.loads(
    (CONTENT / "anti_divination_counters.json").read_text(encoding="utf-8"))["entries"]


def immunity(label):
    return next(r for r in IMMUNITY if r["label"] == label)


def test_a_row_can_print_two_rules_and_the_second_is_not_the_first():
    """"-10" and "only a critical success can succeed" are different claims: a
    skill-20 diviner at -10 rolls against 10 and an ordinary 10 succeeds."""
    demigod = immunity("Seq 4 (Demigod)")
    assert demigod["penalty"] == -10
    assert demigod["requiresCritical"] is True
    assert immunity("Seq 3 (Saint)")["requiresCritical"] is True


def test_the_angel_row_prints_no_critical_clause():
    """Recorded, not repaired: the strongest listed target forbids nothing, so
    it is the easiest of the three for a highly skilled diviner."""
    angel = immunity("Seq 2 (Angel)")
    assert angel["penalty"] == -10
    assert angel["requiresCritical"] is False
    crit = {r["label"] for r in IMMUNITY if r["requiresCritical"]}
    assert crit == {"Seq 4 (Demigod)", "Seq 3 (Saint)"}


def test_the_seq_5_row_agrees_with_the_general_halving():
    """THE WARRANT for treating Sections 5 and 6 as one system: the row's own
    printed direct-link value is exactly half its own penalty."""
    row = immunity("Seq 5 (Beyonder Threshold)")
    assert (row["penalty"], row["linkedPenalty"]) == (-6, -3)
    assert row["linkedPenalty"] * 2 == row["penalty"]


def test_a_row_charging_nothing_carries_no_penalty_field():
    """Seq 6-9 prints a real rule that costs nothing, which is not the same as
    printing zero."""
    weak = immunity("Seq 6–9")
    assert "penalty" not in weak
    assert (weak["sequenceLow"], weak["sequenceHigh"]) == (6, 9)
    assert "Sequence Advantage" in weak["text"]


def test_a_wrapped_row_label_is_walked_on_its_brackets():
    """"Seq 5 (Beyonder" / "Threshold)" is one label. Counting two lines per
    label works today and breaks on the first one that fits on one."""
    rows = extract_anti_divination.parse_rows([
        "Seq 5 (Beyonder", "Threshold)", "Divination ... is at -6 unless ...",
        "Seq 4 (Demigod)", "Divination ... is at -10.",
    ])
    assert [r["label"] for r in rows] == [
        "Seq 5 (Beyonder Threshold)", "Seq 4 (Demigod)"]
    assert rows[0]["penalty"] == -6 and rows[1]["penalty"] == -10


def test_only_the_hp_sacrifice_carries_numbers_the_app_applies():
    """The ritual and the deity are the GM's; a field on them would say the app
    prices them."""
    assert [c["name"] for c in COUNTERS] == [
        "Sequence Advantage", "Direct Link", "HP Sacrifice",
        "Anti-Anti-Divination Ritual", "Divine Intervention",
    ]
    numbered = {c["name"] for c in COUNTERS
                if "ignoresPenalty" in c or "hpRequired" in c}
    assert numbered == {"HP Sacrifice"}
    hp = next(c for c in COUNTERS if c["name"] == "HP Sacrifice")
    # The sign is the book's: "ignores -2 of anti-divination penalties".
    assert (hp["ignoresPenalty"], hp["hpRequired"]) == (-2, 4)


def test_the_two_opposite_rounding_sentences_are_pinned():
    """AntiDivination reads these in opposite directions, on negatives."""
    prose = " ".join(c["text"] for c in COUNTERS)
    assert "still apply at half penalty, rounded up" in prose
    assert "halves all anti-divination penalties (round down)" in prose


@needs_pdf
def test_tidying_the_angel_asymmetry_would_fail_verification():
    byn = extract_anti_divination.load_lines()
    rows = [dict(r) for r in extract_anti_divination.parse_rows(
        [t for b, t in extract_anti_divination.stream(
            byn, *extract_anti_divination.IMMUNITY_RANGE) if not b])]
    counters = extract_anti_divination.parse_counters(
        extract_anti_divination.stream(
            byn, *extract_anti_divination.COUNTER_RANGE))
    extract_anti_divination.verify(rows, counters)  # agrees today
    for row in rows:
        if row["label"] == "Seq 2 (Angel)":
            row["requiresCritical"] = True
    with pytest.raises(ValueError):
        extract_anti_divination.verify(rows, counters)


@needs_pdf
def test_seq_5_disagreeing_with_the_halving_would_fail_verification():
    byn = extract_anti_divination.load_lines()
    rows = [dict(r) for r in extract_anti_divination.parse_rows(
        [t for b, t in extract_anti_divination.stream(
            byn, *extract_anti_divination.IMMUNITY_RANGE) if not b])]
    counters = extract_anti_divination.parse_counters(
        extract_anti_divination.stream(
            byn, *extract_anti_divination.COUNTER_RANGE))
    for row in rows:
        if row["label"] == "Seq 5 (Beyonder Threshold)":
            row["linkedPenalty"] = -4
    with pytest.raises(ValueError):
        extract_anti_divination.verify(rows, counters)


# ---------------------------------------------------------------------------
# Ch. 6.5 Sec. 1-4 -- the TARGET's half of anti-divination
# ---------------------------------------------------------------------------
import extract_detection


def _detection():
    byn = extract_detection.load_lines()
    return byn, extract_detection.extract()


@needs_pdf
def test_detection_extracts_all_four_tables():
    _, (awareness, outcomes, modifiers, techniques, items) = _detection()
    assert [r["pathway"] for r in awareness] == extract_detection.EXPECTED_PATHWAYS
    assert [r["result"] for r in outcomes] == extract_detection.OUTCOME_NAMES
    assert [r["situation"] for r in modifiers] == extract_detection.EXPECTED_SITUATIONS
    assert [t["id"] for t in techniques] == extract_detection.EXPECTED_TECHNIQUES
    assert [r["name"] for r in items] == extract_detection.EXPECTED_ITEMS


@needs_pdf
def test_only_two_techniques_price_a_divination_and_both_are_even():
    """The whole warrant for summing technique penalties before halving them.

    Sec. 6 halves twice with opposite printed rounding. Sum-then-halve and
    halve-then-sum differ only on odd magnitudes, and this book prints none --
    so the orders agree on every combination it can produce, and the reading
    is a decision rather than something the data settled.
    """
    _, (_, _, _, techniques, _) = _detection()
    priced = {t["id"]: t["divinationPenalty"] for t in techniques
              if "divinationPenalty" in t}
    assert priced == {"3A": -2, "3B": -4}
    assert all(v % 2 == 0 for v in priced.values())


@needs_pdf
def test_an_odd_technique_penalty_would_fail_verification(monkeypatch):
    """The guard has to survive its own expected-values pin being updated.

    A maintainer meeting a changed book updates PENALTY_TECHNIQUES to match --
    and that is exactly the moment the stacking reading needs re-deciding, so
    the evenness check is asserted with the pin already moved.
    """
    byn, (awareness, outcomes, modifiers, techniques, items) = _detection()
    techniques = [dict(t) for t in techniques]
    for t in techniques:
        if t["id"] == "3A":
            t["divinationPenalty"] = -3
    monkeypatch.setattr(
        extract_detection, "PENALTY_TECHNIQUES", {"3A": -3, "3B": -4}
    )
    with pytest.raises(ValueError, match="odd penalty"):
        extract_detection.verify(byn, awareness, outcomes, modifiers, techniques, items)


@needs_pdf
def test_the_charm_is_priced_in_both_sections():
    """One item, generally and specifically -- the overlap Detection refuses.

    Sec. 2 prices any protective item at +1; Sec. 4 prices this one at +2.
    The day they agree is the day the scoping stops being needed, so verify()
    fails then too.
    """
    _, (_, _, modifiers, _, items) = _detection()
    generic = next(r for r in modifiers
                   if r["situation"] == extract_detection.GENERIC_CHARM_SITUATION)
    pendant = next(r for r in items if r["name"] == extract_detection.PENDANT)
    assert generic["modifier"] == 1
    assert pendant["detectionBonus"] == 2


@needs_pdf
def test_the_two_charm_prices_agreeing_would_fail_verification():
    byn, (awareness, outcomes, modifiers, techniques, items) = _detection()
    modifiers = [dict(r) for r in modifiers]
    for r in modifiers:
        if r["situation"] == extract_detection.GENERIC_CHARM_SITUATION:
            r["modifier"] = 2
    with pytest.raises(ValueError, match="scoping is moot"):
        extract_detection.verify(byn, awareness, outcomes, modifiers, techniques, items)


@needs_pdf
def test_the_candle_is_scoped_to_a_ritual_and_never_to_the_detection_roll():
    _, (_, _, _, _, items) = _detection()
    candle = next(r for r in items if r["name"] == extract_detection.CANDLE)
    assert candle["ritualDetectionPenalty"] == -3
    assert "detectionBonus" not in candle
    assert extract_detection.CANDLE_SCOPE in candle["effect"]


@needs_pdf
def test_the_monster_row_penalises_the_diviner_inside_an_awareness_table():
    """A row in a table that is not what the table is about.

    Recorded, applied nowhere: routing it through `techniquePenalty` would put
    a passive, GM-discretion penalty through two halvings that both name
    "active techniques".
    """
    _, (awareness, _, _, _, _) = _detection()
    monster = next(r for r in awareness
                   if r["pathway"] == extract_detection.CROSS_AXIS_PATHWAY)
    assert monster["divinerPenalty"] == -1
    assert "bonus" not in monster and "freeRoll" not in monster
    assert sum(1 for r in awareness if "divinerPenalty" in r) == 1


@needs_pdf
def test_the_monster_row_gaining_awareness_would_fail_verification():
    byn, (awareness, outcomes, modifiers, techniques, items) = _detection()
    awareness = [dict(r) for r in awareness]
    for r in awareness:
        if r["pathway"] == extract_detection.CROSS_AXIS_PATHWAY:
            r["bonus"] = 2
    with pytest.raises(ValueError, match="grants awareness"):
        extract_detection.verify(byn, awareness, outcomes, modifiers, techniques, items)


@needs_pdf
def test_the_ward_reaches_both_rolls():
    _, (_, _, _, techniques, _) = _detection()
    ward = next(t for t in techniques if t["id"] == "3A")
    assert ward["divinationPenalty"] == -2
    assert ward["detectionBonus"] == 2
    assert ward["spi"] == 2


@needs_pdf
def test_the_technique_priced_in_prose_carries_no_number():
    """3F costs "SPI equal to what a divination of that scope would normally
    cost". No field, so a caller must refuse it rather than read it as free."""
    _, (_, _, _, techniques, _) = _detection()
    echo = next(t for t in techniques if t["id"] == "3F")
    assert "spi" not in echo
    assert "divination of that scope" in dict(
        (b["label"], b["text"]) for b in echo["bullets"]
    )["Cost"]


@needs_pdf
def test_only_the_margin_named_row_carries_a_margin():
    """Four rows named by outcome, one by margin -- the two doors again."""
    _, (_, outcomes, _, _, _) = _detection()
    assert [r["result"] for r in outcomes if "margin" in r] == ["Success by 3+"]
    assert next(r for r in outcomes if r["result"] == "Success by 3+")["margin"] == 3


@needs_pdf
def test_the_resolution_order_is_still_printed():
    """The book's own statement that these sections are separate axes."""
    byn, (awareness, outcomes, modifiers, techniques, items) = _detection()
    extract_detection.verify(byn, awareness, outcomes, modifiers, techniques, items)
    text = " ".join((byn.get(n) or "") for n in range(3737, 3755))
    for step in extract_detection.RESOLUTION_STEPS:
        assert step in text


import extract_spirituality


def sense_entries():
    return json.loads(
        (CONTENT / "spiritual_senses.json").read_text(encoding="utf-8"))["entries"]


def affinity_entries():
    return json.loads(
        (CONTENT / "spi_affinity.json").read_text(encoding="utf-8"))["entries"]


def test_chapter_four_prints_two_sense_tables():
    blocks = sense_entries()
    assert [b["skill"] for b in blocks] == [
        "Spiritual Intuition", "Spiritual Perception",
    ]
    assert [b["attr"] for b in blocks] == ["SPI", "SPI"]
    # Hard and Average, which is why they cannot share one table.
    assert [b["difficulty"] for b in blocks] == ["H", "A"]


def test_neither_sense_table_prints_a_failure_row():
    """All four rows are successes.

    So a failed roll gets NO row rather than an invented consolation, and
    nothing here overrides SuccessRoll's 3-4/17-18 -- there is nothing printed
    to override them with. The FrightCheck reading, not the PCR's.
    """
    for block in sense_entries():
        results = [r["result"] for r in block["rows"]]
        assert results == list(extract_spirituality.RESULT_ROWS)
        assert not any("Failure" in r for r in results)


def test_only_the_margin_named_rows_carry_a_margin():
    """"Success" and "Critical Success" carry none rather than a zero.

    A margin of 0 on the critical row would let a bare success satisfy it.
    """
    for block in sense_entries():
        assert [r.get("margin") for r in block["rows"]] == [None, 3, 5, None]


def test_the_seer_bonus_names_a_rank_and_not_a_pathway():
    """"+6 Seer" is the Fool's SEQUENCE 9 NAME.

    A Fool at Sequence 8 is a Clown, so applying it to the Fool pathway would
    author a rule the book did not print. Offered, never granted.
    """
    perception = sense_entries()[1]
    seer = next(m for m in perception["modifiers"] if m["situation"] == "Seer")
    assert seer["modifier"] == 6
    assert not any(m["situation"] == "Fool" for m in perception["modifiers"])


def test_chapter_four_and_appendix_a_still_price_the_same_distraction():
    """The overlap that makes the scoping necessary.

    Chapter 4 prints "-2 distracted/in combat"; Appendix A prints "Distracted
    or hurried" and "Very adverse conditions (combat, ...)". Both words are
    verbatim in both, so offering both charges one distraction twice. The day
    this stops being true is the day the scoping stops being needed.
    """
    intuition = sense_entries()[0]
    row = next(m for m in intuition["modifiers"] if m["modifier"] == -2)
    common = json.loads(
        (CONTENT / "common_modifiers.json").read_text(encoding="utf-8"))["entries"]
    situations = " ".join(e["situation"] for e in common).lower()
    for word in extract_spirituality.OVERLAP_WORDS:
        assert word in situations
        assert word in row["situation"].lower()


def test_the_affinity_claims_reconcile_with_chapter_nine():
    """Chapter 4 describing chapter 9's data, marked.

    A disagreement here would be a FINDING recorded under 4.1, never a repair.
    Today all four claims hold exactly.
    """
    tiers = affinity_entries()
    assert [t["tier"] for t in tiers] == [
        "high", "moderate", "baseline", "constrained",
    ]
    pathways = json.loads(
        (CONTENT / "pathways.json").read_text(encoding="utf-8"))["entries"]
    grants = {
        b["pathway"]: int(sum(s["delta"] for s in b["stats"] if s["attr"] == "SPI"))
        for b in pathways if b["sequence"] == 9
    }
    named = tiers[0]["pathways"]
    assert named == extract_spirituality.HIGH_AFFINITY
    assert min(grants[p] for p in named) > max(
        v for p, v in grants.items() if p not in named)
    assert grants["Chained"] == 0
    assert [p for p, v in grants.items() if v == 0] == ["Chained"]


@needs_pdf
def test_the_sense_tables_verify_against_the_book():
    blocks, tiers = extract_spirituality.extract()
    extract_spirituality.verify(blocks, tiers, extract_spirituality.load_lines())


@needs_pdf
def test_a_sense_table_that_grew_a_failure_row_is_refused():
    """The guard against inventing the row the book declined to print."""
    blocks, tiers = extract_spirituality.extract()
    blocks = [dict(b, rows=list(b["rows"])) for b in blocks]
    blocks[0]["rows"].append({"result": "Failure", "effect": "you sense nothing"})
    with pytest.raises(AssertionError):
        extract_spirituality.verify(
            blocks, tiers, extract_spirituality.load_lines())
