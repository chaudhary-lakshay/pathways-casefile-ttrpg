"""Ritual modes, power sources, the study grid and the quirk allowance. Split out of test_tools.py.

v6.9i deleted the Entity Response table, the Supporting Skills table, the
sacrifice tiers and the worked rituals, and their tests went with them.
"""
import json

import pytest

import extract_ritual

from testsupport import CONTENT, OUT, needs_pdf


import extract_ritual_modes


def _entries(name):
    return json.loads((CONTENT / name).read_text(encoding="utf-8"))["entries"]


def quick_preparation_entries():
    return _entries("ritual_quick_preparation.json")


def target_link_entries():
    return _entries("ritual_target_link.json")


def full_preparation_entries():
    return _entries("ritual_preparation.json")


def _modes_inputs():
    byn = extract_ritual_modes.load_lines()
    return (extract_ritual_modes.joined(byn, *extract_ritual_modes.NO_DEFAULT_RANGE),
            extract_ritual_modes.joined(byn, *extract_ritual_modes.PHASE_ONE_RANGE))


def test_the_two_preparation_tables_disagree_on_excellent():
    """The book prints one preparation ladder per mode. Quick's Excellent is
    +2, Phase I's is +3; neither is repaired."""
    quick = {e["quality"]: e["modifier"] for e in quick_preparation_entries()}
    full = {e["quality"]: e["modifier"] for e in full_preparation_entries()}
    assert quick["Excellent"] == 2
    assert full["Excellent"] == 3


def test_they_agree_on_adequate_which_is_what_makes_it_a_disagreement():
    quick = {e["quality"]: e["modifier"] for e in quick_preparation_entries()}
    full = {e["quality"]: e["modifier"] for e in full_preparation_entries()}
    assert quick["Adequate"] == full["Adequate"] == 0


def test_quick_poor_and_none_are_one_row():
    """v6.9i prints "Poor/None -2 to -4": one row, a range, where Phase I
    prints Poor and None apart."""
    entries = quick_preparation_entries()
    assert [e["quality"] for e in entries] == ["Excellent", "Adequate", "Poor/None"]
    assert entries[-1]["printed"] == "-2 to -4" and entries[-1]["best"] == -4
    assert all("looksLike" not in e for e in entries)


@needs_pdf
def test_reconciled_preparation_tables_would_fail_verification():
    patched = [
        {**e, "modifier": 3} if e["quality"] == "Excellent" else e
        for e in quick_preparation_entries()
    ]
    no_default, phase_one = _modes_inputs()
    extract_ritual_modes.verify(
        quick_preparation_entries(), target_link_entries(), no_default, phase_one)
    with pytest.raises(ValueError):
        extract_ritual_modes.verify(patched, target_link_entries(), no_default, phase_one)


@needs_pdf
def test_phase_one_folds_the_link_into_its_modifier():
    """The only evidence the link is one of Phase I's declarations rather than
    a modifier beside it. Without it nothing stops a Full ritual counting a
    perfect link twice."""
    no_default, phase_one = _modes_inputs()
    assert extract_ritual_modes.LINK_FOLDED in phase_one
    with pytest.raises(ValueError, match="folds the target link"):
        extract_ritual_modes.verify(
            quick_preparation_entries(), target_link_entries(), no_default,
            phase_one.replace("target link", "target"))


def test_the_target_link_ladder_runs_perfect_to_none():
    entries = target_link_entries()
    assert [e["link"] for e in entries] == extract_ritual_modes.EXPECTED_LINKS
    assert [e["modifier"] for e in entries] == [4, 2, 0, -4]
    by_link = {e["link"]: e for e in entries}
    assert by_link["Strong"]["printed"] == "+2 to +3" and by_link["Strong"]["best"] == 3
    assert by_link["Weak"]["printed"] == "+0 to -1" and by_link["Weak"]["best"] == -1
    # The bracket is what the link looks like; None prints none.
    assert by_link["Perfect"]["looksLike"] == "name + possession"
    assert "looksLike" not in by_link["None"]


def test_the_supporting_skills_table_is_gone_with_its_source():
    for name in ("ritual_support.json", "entity_response.json",
                 "ritual_sacrifice.json", "ritual_examples.json"):
        assert not (CONTENT / name).exists(), name


def test_the_chapter_grades_a_ritual_that_worked():
    """Sec. III.A's outcome table is the only place the chapter says how WELL
    a ritual went, and Phase III sends the Full ritual to it too."""
    rows = _entries("ritual_success.json")
    assert [(r["from"], r["to"]) for r in rows] == [(3, None), (0, 2)]
    # The milder band starts at 0: a ritual that exactly hits its target
    # succeeded, and must land on a row.
    assert rows[1]["from"] == 0


@needs_pdf
def test_the_quick_failure_rows_coarsen_section_five():
    """Why only the SUCCESS half of that table is stored: its failure rows
    agree with Sec. V by coarsening ("Failure by 5+" is 5-9 and 10+ merged).
    Two rival tables would be 6y's shape and the app would pick per mode."""
    byn = extract_ritual.load_lines()
    _, here = extract_ritual.parse_outcomes(
        extract_ritual.stream(byn, *extract_ritual.OUTCOME_RANGE))
    there = {r["from"] for r in _entries("ritual_failures.json") if not r["critical"]}
    assert set(here) <= there, f"III.A starts bands at {sorted(here)}, V at {sorted(there)}"
    assert set(here) != there, "III.A now prints the same bands as Sec. V"
    full = " ".join(extract_ritual.stream(byn, *extract_ritual.FULL_RANGE))
    assert extract_ritual.FULL_DEFERS in full


@needs_pdf
def test_ritualistic_magic_prints_its_own_prohibition():
    """The no-default line is what limits Divination Arts and Ritualistic
    Magic to never standing in for each other. "cannot be substituted" is
    printed twice now: here, and Sec. VI's "cannot be substituted by simple
    communication", which is about a summons, not a skill."""
    byn = extract_ritual_modes.load_lines()
    no_default, _ = _modes_inputs()
    assert extract_ritual_modes.NO_DEFAULT_SENTENCE in no_default
    printed = [n for n, t in byn.items() if "cannot be substituted" in t]
    assert printed[0] == extract_ritual_modes.NO_DEFAULT_RANGE[0]
    assert all("by simple communication" in byn[n] for n in printed[1:]), printed


@needs_pdf
def test_losing_the_prohibition_would_fail_verification():
    _, phase_one = _modes_inputs()
    with pytest.raises(ValueError, match="no longer prints"):
        extract_ritual_modes.verify(
            quick_preparation_entries(), target_link_entries(),
            "Ritualistic Magic (IQ/Very Hard) -- your core skill.", phase_one)


def test_committed_content_claims_the_prohibition():
    """An assertion whose source sentence is unclaimed is a rule nobody can
    find again (6af)."""
    raw = json.loads((CONTENT / "ritual_quick_preparation.json").read_text(encoding="utf-8"))
    assert [3896, 3896] in raw["sourceLines"]["9"]
    assert "substituted" not in json.dumps(raw)


import extract_power_sources


def complexity_entries():
    return _entries("ritual_complexity.json")


def _power_inputs():
    byn = extract_power_sources.load_lines()
    rows = extract_power_sources.parse_complexity(
        extract_power_sources.stream(byn, *extract_power_sources.COMPLEXITY_RANGE))
    prose = " ".join(extract_power_sources.stream(byn, *extract_power_sources.PROSE_RANGE))
    return rows, prose, _entries("ritual_weights.json")


@needs_pdf
def test_the_power_sources_extract_end_to_end():
    assert extract_power_sources.main() == 0


def test_complexity_is_the_pacing_half_of_the_weight_axis():
    """v6.9i keys the Rushed / Extended table on the four weight names with
    the weight table's own base times, so SPI cost lives on the weight alone."""
    weights = {w["weight"]: w["baseTime"] for w in _entries("ritual_weights.json")}
    rows = complexity_entries()
    assert [r["complexity"] for r in rows] == ["Light", "Moderate", "Heavy", "Major"]
    for row in rows:
        assert weights[row["complexity"]] == row["baseTime"], row
        assert set(row) == {"complexity", "baseTime", "rushed", "extended"}, row


@needs_pdf
def test_parting_the_base_times_would_fail_verification():
    rows, prose, weights = _power_inputs()
    extract_power_sources.verify(rows, prose, weights)
    moved = [dict(rows[0], baseTime="2 hr")] + rows[1:]
    with pytest.raises(ValueError, match="two axes again"):
        extract_power_sources.verify(moved, prose, weights)


@needs_pdf
def test_the_sentence_numbers_are_pinned():
    """Sec. IV is sentences. Each number lives in Kotlin; this pins it."""
    _, prose, _ = _power_inputs()
    assert extract_power_sources.read_numbers(prose) == {
        "hpPerSpi": (2, 1), "participants": (1, 3), "sacrifice": (-40, 40),
        "invocation": (1, 5), "orthodox": (2,), "suspension": (-2,),
    }
    assert extract_power_sources.HP_NO_BONUS in prose


@needs_pdf
def test_a_changed_sentence_number_would_fail():
    _, prose, _ = _power_inputs()
    with pytest.raises(ValueError, match="participants"):
        extract_power_sources.read_numbers(prose.replace("(max +3)", "(max +4)"))


def test_the_sentence_numbers_are_not_committed_as_data():
    raw = json.dumps(json.loads((CONTENT / "ritual_complexity.json").read_text(encoding="utf-8")))
    for word in ("participants", "sacrifice", "invocation", "orthodox"):
        assert word not in raw


def test_rushing_always_costs_and_extending_always_pays():
    for row in complexity_entries():
        assert row["rushed"] < 0
        assert row["extended"] > 0


@needs_pdf
def test_the_study_grid_is_the_skill_ladder_cell_for_cell():
    """Line 2298 says study points "follow the same point ladder as normal
    character points" -- the book asserting the two printings agree, so the
    16 cells are compared rather than inherited."""
    lines = json.loads((OUT / "lines.json").read_text(encoding="utf-8"))
    byn = {l["n"]: l["text"] for l in lines}
    assert "same point ladder" in byn[2298], byn[2298]

    printed = {}
    for first in (2305, 2310, 2315, 2320):
        printed[int(byn[first].strip())] = [
            byn[first + 1 + col].strip() for col in range(4)
        ]

    ladder = json.loads(
        (CONTENT / "skill_ladder.json").read_text(encoding="utf-8"))["entries"][0]
    for row, cp in enumerate(sorted(printed)):
        assert ladder["cumulative"][row] == cp, (row, cp)
        for col, rung in enumerate(ladder["rungs"]):
            cell = f"Attr{rung['offsets'][row]:+d}"
            assert printed[cp][col] == cell, (cp, rung["difficulty"], printed[cp][col], cell)


@needs_pdf
def test_the_quirk_allowance_is_five_ADDITIONAL_points():
    """`PointBudget.MAX_QUIRKS`'s printing wraps across two lines, and the
    load-bearing word is ADDITIONAL: the budget is 70 + min(dis, 40) +
    min(quirks, 5), not quirks coming out of the -40. v6.9i deleted the
    Character Creation Summary box, so the -40 is read from the same
    sentence."""
    lines = json.loads((OUT / "lines.json").read_text(encoding="utf-8"))
    byn = {l["n"]: l["text"] for l in lines}
    sentence = " ".join(byn[n].strip() for n in (1201, 1202, 1203))
    assert "up to -40 points of disadvantages" in sentence, sentence
    assert "Up to 5 additional points of quirks" in sentence, sentence
    assert "-1 pt each" in sentence, sentence
