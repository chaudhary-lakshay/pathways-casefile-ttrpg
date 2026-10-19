"""Attribute costs, encumbrance (Ch. 2) and rest (Ch. 7), all new in v6.9i."""
import json

import pytest

import extract_attributes
import extract_rest

from testsupport import CONTENT, needs_pdf


def _entries(name):
    return json.loads((CONTENT / name).read_text(encoding="utf-8"))["entries"]


def test_attributes_cost_a_flat_rate_per_level():
    """v6.9d doubled each level (ST 10 -> 20 -> 40). v6.9i prints a flat
    rate: ST 10, DX/IQ/HT 20 per level."""
    costs = {r["attr"]: r["costPerLevel"] for r in _entries("attribute_costs.json")}
    assert costs == {"ST": 10, "DX": 20, "IQ": 20, "HT": 20}


def test_an_attribute_row_with_no_cost_is_reported():
    with pytest.raises(ValueError, match="prints no cost"):
        extract_attributes.attributes(["ST (Strength)", "a lot"])


def test_encumbrance_runs_none_to_overload():
    """Move and Dodge take the same penalty at every level; only Overload
    forbids running. BL = ST x ST / 5 is a sentence, so it lives in Kotlin."""
    levels = _entries("encumbrance.json")
    assert [r["name"] for r in levels] == [
        "None", "Light", "Medium", "Heavy", "Very Heavy", "Overload"]
    assert [r["maxLoadBl"] for r in levels] == [1, 2, 3, 6, 10, 15]
    assert [r["movePenalty"] for r in levels] == [0, -1, -2, -3, -4, -5]
    assert all(r["dodgePenalty"] == r["movePenalty"] for r in levels)
    assert [r.get("cannotRun", False) for r in levels] == [False] * 5 + [True]


def _attribute_inputs():
    byn = extract_attributes.load_lines()
    a = extract_attributes.stream(byn, *extract_attributes.ATTRIBUTE_RANGE)
    lift = extract_attributes.stream(byn, *extract_attributes.LIFT_RANGE)
    return extract_attributes.attributes(a), extract_attributes.encumbrance(lift), \
        " ".join(a), " ".join(lift)


@needs_pdf
def test_a_doubling_attribute_cost_would_fail_verification():
    costs, levels, a, lift = _attribute_inputs()
    extract_attributes.verify(costs, levels, a, lift)
    with pytest.raises(ValueError, match="flat rate"):
        extract_attributes.verify(costs, levels, a.replace("flat rate", "doubling rate"), lift)
    doubled = [dict(r, costPerLevel=r["costPerLevel"] * 2) for r in costs]
    with pytest.raises(ValueError, match="attribute costs changed"):
        extract_attributes.verify(doubled, levels, a, lift)


@needs_pdf
def test_losing_the_basic_lift_formula_would_fail_verification():
    costs, levels, a, lift = _attribute_inputs()
    with pytest.raises(ValueError, match="Basic Lift"):
        extract_attributes.verify(
            costs, levels, a, lift.replace(extract_attributes.BASIC_LIFT, ""))


def _rest():
    return json.loads((CONTENT / "rest.json").read_text(encoding="utf-8"))


def test_a_long_rest_is_three_times_a_short_one_and_rolls_twice():
    rests = {r["name"]: r for r in _rest()["rests"]}
    short, long_ = rests["Short Rest"], rests["Long Rest"]
    assert (short["hours"], short["focusedTaskRolls"], short["allFp"]) == (3, 1, False)
    assert (long_["hours"], long_["focusedTaskRolls"], long_["allFp"]) == (9, 2, True)
    assert long_.get("countsAsActingSession") is True
    assert "countsAsActingSession" not in short


def test_cogitation_doubles_spi_recovery():
    assert _rest()["spi"] == {"perHour": 1, "cogitationPerHour": 2,
                              "cogitationSkill": "Cogitation"}


def test_the_one_third_fp_row_is_what_pools_applies():
    thresholds = {r["name"]: r["effect"] for r in _rest()["fpThresholds"]}
    assert thresholds["At 1/3 FP or below"] == "-1 to all rolls; Move and Dodge halved"


@needs_pdf
def test_the_hp_rate_disagreement_with_chapter_5_is_guarded():
    """Ch. 7's Short Rest prints "1 HP per 8 hours of rest"; ch. 5 prices
    natural recovery per day. Ch. 5 governs HP ("HP recovery remains in
    Chapter 5"), and the disagreement is recorded, never repaired: the day ch.
    5 stops printing a daily rate the guard fails."""
    data = _rest()
    byn = extract_rest.load_lines()
    text = " ".join(extract_rest.stream(byn, *extract_rest.REST_RANGE))
    extract_rest.verify(data, text, "Per day; rest & food required")
    with pytest.raises(ValueError, match="per day"):
        extract_rest.verify(data, text, "Per 8 hours")
    with pytest.raises(ValueError, match="ch7Healing"):
        extract_rest.verify(data, text.replace("1 HP per 8 hours", "1 HP per day"),
                            "Per day; rest & food required")
