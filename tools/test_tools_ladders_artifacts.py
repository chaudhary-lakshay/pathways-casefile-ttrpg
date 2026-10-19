"""The skill and success ladders, common modifiers and sealed artifacts. Split out of test_tools.py."""
import json

import pytest

from testsupport import CONTENT, needs_pdf


import extract_skill_ladder


def ladder_table():
    return json.loads(
        (CONTENT / "skill_ladder.json").read_text(encoding="utf-8"))["entries"][0]


def test_the_skill_grid_is_regular_in_both_directions():
    """Each difficulty row is the one above it shifted down by one, and each
    climbs by one along its length. That double regularity is the entire
    reason Difficulty carries ONE firstRungOffset instead of a stored row."""
    rows = [r["offsets"] for r in ladder_table()["rungs"]]
    for above, below in zip(rows, rows[1:]):
        assert [x - 1 for x in above] == below
    for row in rows:
        assert row == list(range(row[0], row[0] + len(row)))


def test_the_grid_has_a_column_for_every_rung():
    """v6.9i prints nine costs and nine offsets per row: no rung is
    extrapolated any more (v6.9d's 256 pts had no offset)."""
    table = ladder_table()
    assert len(table["cumulative"]) == 9
    assert all(len(r["offsets"]) == 9 for r in table["rungs"])


def test_the_worked_skill_examples_reproduce_from_the_grid():
    """Eight printed steps across two difficulties. Each is the attribute plus
    the difficulty's offset plus the rung index."""
    table = ladder_table()
    offsets = {r["difficulty"]: r["offsets"] for r in table["rungs"]}
    steps = 0
    for example in table["examples"]:
        row = offsets[example["difficulty"]]
        for step in example["steps"]:
            rung = max(
                i for i, c in enumerate(table["cumulative"]) if c <= step["points"])
            assert example["attributeLevel"] + row[0] + rung == step["level"]
            steps += 1
    assert steps == 8


def test_the_ladder_is_not_gurps_core():
    """Core 4e is 1/2/4/8/12/16 with +4 per level past Attr+2. v6.9i doubles
    up to 32 and then climbs by a flat 16."""
    table = ladder_table()
    assert 12 not in table["cumulative"]
    assert table["cumulative"] == [1, 2, 4, 8, 16, 32, 48, 64, 80]
    assert table["incremental"][-4:] == [16, 16, 16, 16]


@needs_pdf
def test_the_worked_examples_need_the_bullet_stripped():
    """This PDF prints list bullets as U+007F and the worked examples are a
    bulleted list, so an unstripped cell starts with a control character and
    every step regex silently matches nothing -- the parse looked like a
    table with no examples rather than like a failure."""
    _, _, _, examples = extract_skill_ladder.extract()
    assert sum(len(e["steps"]) for e in examples) == 8


@needs_pdf
def test_straightening_the_grid_would_fail_verification():
    cumulative, _, offsets, examples = extract_skill_ladder.extract()
    byn = extract_skill_ladder.load_lines()
    prose = " ".join(
        extract_skill_ladder.stream(byn, *extract_skill_ladder.PROSE_RANGE))
    bent = {d: list(v) for d, v in offsets.items()}
    bent["Hard"] = [o + 1 for o in bent["Hard"]]
    with pytest.raises(ValueError):
        extract_skill_ladder.verify(cumulative, bent, examples, prose)


import extract_success_ladder


def ladder_rows():
    return json.loads(
        (CONTENT / "success_ladder.json").read_text(encoding="utf-8"))["entries"]


def row_named(result):
    return next(r for r in ladder_rows() if r["result"] == result)


def test_the_two_degrees_are_printed_nowhere_else_in_the_book():
    """The whole reason this is a definition rather than a summary. If either
    phrase ever turns up in a chapter, that chapter is the authority and this
    extract is repeating it -- which is the situation 6v warns about."""
    byn = extract_success_ladder.load_lines()
    low, high = extract_success_ladder.TABLE_RANGE
    elsewhere = [
        n for n, text in byn.items()
        if not low <= n <= high
        and ("Good Success" in text or "Excellent Success" in text)
    ]
    assert elsewhere == []


def test_five_rows_repeat_rules_the_engine_already_implements():
    """The warrant for adopting the two new rows. Checked by VALUE, because a
    presence check would pass on a table that had quietly changed its
    numbers."""
    assert (row_named("Critical Success (always)")["rollLow"],
            row_named("Critical Success (always)")["rollHigh"]) == (3, 4)
    assert (row_named("Critical Failure (always)")["rollLow"],
            row_named("Critical Failure (always)")["rollHigh"]) == (17, 18)
    assert row_named("Success")["comparison"] == "<="
    assert row_named("Success")["offset"] == 0
    assert row_named("Failure")["comparison"] == ">"
    assert row_named("Critical Failure")["offset"] == 10


def test_a_row_is_named_by_the_roll_or_by_the_target_never_both():
    """Two doors, the same shape as the divination clarity and ritual critical
    tables. A row carrying both would mean the parse paired the wrong cells."""
    for row in ladder_rows():
        by_roll = "rollLow" in row
        by_target = "comparison" in row
        assert by_roll != by_target, row


def test_the_degrees_are_strictly_harder_than_plain_success():
    """The roll-under bands overlap, so the order is what makes the ladder
    readable -- a swap would still produce a table that looks right."""
    plain = row_named("Success")["offset"]
    good = row_named("Good Success")["offset"]
    excellent = row_named("Excellent Success")["offset"]
    assert excellent < good < plain
    assert (good, excellent) == (-3, -5)


@needs_pdf
def test_straightening_a_degree_would_fail_verification():
    rows = extract_success_ladder.extract()
    byn = extract_success_ladder.load_lines()
    core = " ".join(
        extract_success_ladder.stream(byn, *extract_success_ladder.CORE_RANGE))
    pointer = " ".join(
        extract_success_ladder.stream(byn, *extract_success_ladder.POINTER_RANGE))
    bent = [dict(r) for r in rows]
    for row in bent:
        if row["result"] == "Excellent Success":
            row["offset"] = -2          # now easier than Good
    with pytest.raises(ValueError):
        extract_success_ladder.verify(bent, core, pointer)


@needs_pdf
def test_losing_chapter_2s_pointer_would_fail_verification():
    """Without it the appendix is inventing a rule instead of supplying the
    table five abilities and one chapter already point at."""
    rows = extract_success_ladder.extract()
    byn = extract_success_ladder.load_lines()
    core = " ".join(
        extract_success_ladder.stream(byn, *extract_success_ladder.CORE_RANGE))
    with pytest.raises(ValueError):
        extract_success_ladder.verify(rows, core, "margins matter, somehow")


@needs_pdf
def test_a_dropped_cell_is_caught_rather_than_shifting_every_row():
    """Pairing the stream off blindly would let one missing result cell shift
    every row below it and still yield a plausible table."""
    with pytest.raises(ValueError):
        extract_success_ladder.parse_rows(
            ["3-4", "Critical Success (always)", "Roll <= Target", "Roll <= Target -3"])


import extract_common_modifiers


def common_rows():
    return json.loads(
        (CONTENT / "common_modifiers.json").read_text(encoding="utf-8"))["entries"]


def common_row(situation):
    return next(r for r in common_rows() if r["situation"] == situation)


UNTRAINED = "Using untrained skill (default)"


def test_the_untrained_row_carries_no_modifier():
    """It is not a modifier: it replaces the roll with an attribute default.
    Applied to a skill it would stack on SkillLadder.defaultFor AND subtract
    from the wrong number. Printing no value is not printing zero."""
    row = common_row(UNTRAINED)
    assert row["isDefault"] is True
    assert "modifier" not in row
    assert row["printed"] == "Attribute -4 to -6"


def test_the_untrained_range_is_the_engines_own_default_penalties():
    """The warrant for the whole table. Difficulty.defaultPenalty is -4 Easy,
    -5 Average, -6 Hard, and this row prints that column's two ends -- the one
    row here the engine can be checked against, agreeing exactly."""
    assert extract_common_modifiers.EXPECTED_DEFAULT_ENDS == (-4, -6)
    printed = common_row(UNTRAINED)["printed"]
    assert "-4" in printed and "-6" in printed


def test_routine_conditions_print_a_real_zero():
    """A row that prints +0 is a choice that adds nothing. The untrained row is
    the other thing -- a row that prints no modifier at all."""
    assert common_row("Routine conditions")["modifier"] == 0


def test_every_other_row_carries_a_value():
    for row in common_rows():
        if row["situation"] == UNTRAINED:
            continue
        assert "modifier" in row, row


def test_the_gentle_end_is_what_a_range_offers():
    """Two ranges and two open ends. The value offered is nearer zero and the
    other bound is kept, so a GM's pick is validated against the book."""
    good = common_row("Good tools / equipment")
    assert (good["modifier"], good["otherEnd"]) == (1, 2)
    poor = common_row("Improvised / poor tools")
    assert (poor["modifier"], poor["otherEnd"]) == (-2, -4)
    easy = common_row("Easy task (well-lit, calm, plenty of time)")
    assert easy["modifier"] == 4 and easy["isOpenEnded"] is True
    assert "otherEnd" not in easy


def test_the_conditional_row_keeps_its_condition_in_the_printed_cell():
    """"+2 (if they succeed first)" -- the condition is another character's
    roll, which this app cannot test, so it is shown rather than applied."""
    row = common_row("Aided by someone with the same skill")
    assert row["modifier"] == 2
    assert "if they succeed first" in row["printed"]


def test_this_table_and_chapter_5_both_price_darkness():
    """Why the general table is not offered on a ranged attack: offering both
    charges one dark room twice."""
    assert any("dark" in r["situation"] for r in common_rows())
    ranged = json.loads(
        (CONTENT / "ranged_modifiers.json").read_text(encoding="utf-8"))["entries"]
    assert any("Darkness" in r["condition"] for r in ranged)


@needs_pdf
def test_pricing_the_untrained_row_would_fail_verification():
    rows = [dict(r) for r in extract_common_modifiers.extract()]
    for row in rows:
        if row["situation"] == UNTRAINED:
            row["modifier"] = -4
    with pytest.raises(ValueError):
        extract_common_modifiers.verify(rows)


@needs_pdf
def test_flattening_a_range_would_fail_verification():
    rows = [dict(r) for r in extract_common_modifiers.extract()]
    for row in rows:
        row.pop("otherEnd", None)
    with pytest.raises(ValueError):
        extract_common_modifiers.verify(rows)


@needs_pdf
def test_a_dropped_modifier_cell_is_caught():
    """Pairing the stream off blindly would shift every row below the gap and
    still produce a table that sums."""
    with pytest.raises(ValueError):
        extract_common_modifiers.parse_rows(
            ["Routine conditions", "Distracted or hurried", "-2"])


import extract_artifacts

GRADES = json.loads(
    (CONTENT / "sealed_artifact_grades.json").read_text(encoding="utf-8"))["entries"]


def test_every_grade_prints_a_sequence_band():
    """v6.9i deleted the four unique artifacts that used to confirm each band
    independently, so the bands are pinned on the grade table alone."""
    bands = {g["grade"]: (g["sequenceLow"], g["sequenceHigh"]) for g in GRADES}
    assert bands == {3: (8, 9), 2: (5, 7), 1: (3, 4), 0: (1, 2)}


def test_the_unique_artifacts_file_is_gone_with_its_source():
    """v6.9i prints no "Unique Sealed Artifacts" section; a stale file would
    keep feeding abilities the book no longer prints."""
    assert not (CONTENT / "sealed_artifacts.json").exists()


def test_a_grade_gates_on_rank_and_never_on_a_sequence():
    """The fourth column is "Church Access Rules" and every cell names a rank.
    Nothing in it names a Sequence the wielder must have reached, which is why
    SealedArtifacts exposes no canUse."""
    assert [g["grade"] for g in GRADES] == [3, 2, 1, 0]
    for g in GRADES:
        assert any(w in g["churchAccess"]
                   for w in ("Nighthawk", "Bishop", "confidential")), g


def test_a_power_cell_wrapping_onto_a_bracket_is_one_cell():
    """"Low-Sequence Beyonder" / "(Seq 8-9)" is one column. A line opening with
    "(" cannot start a cell, which is the unclosed-bracket rule the Vitals
    multiplier already needs."""
    rows = extract_artifacts.parse_grades([
        "Grade 3", "Considerable", "Low-Sequence Beyonder", "(Seq 8–9)",
        "Formal Nighthawk members and above",
    ])
    assert rows[0]["powerEquivalent"] == "Low-Sequence Beyonder (Seq 8–9)"
    assert rows[0]["danger"] == "Considerable"
    assert (rows[0]["sequenceLow"], rows[0]["sequenceHigh"]) == (8, 9)
    # A power cell that does NOT wrap keeps the danger cell intact beside it.
    flat = extract_artifacts.parse_grades([
        "Grade 1", "Highly", "Dangerous", "Saint (Seq 3–4)", "Diocesan Bishop",
    ])
    assert flat[0]["danger"] == "Highly Dangerous"
    assert flat[0]["powerEquivalent"] == "Saint (Seq 3–4)"


def _grades_and_intro():
    byn = extract_artifacts.load_lines()
    grades = extract_artifacts.parse_grades(
        [t for b, t in extract_artifacts.stream(byn, *extract_artifacts.GRADE_RANGE) if not b])
    intro = " ".join(t for b, t in extract_artifacts.stream(byn, *extract_artifacts.INTRO_RANGE))
    return grades, intro


@needs_pdf
def test_an_access_cell_naming_no_rank_would_fail_verification():
    grades, intro = _grades_and_intro()
    extract_artifacts.verify(grades, intro)  # agrees today
    grades[0] = dict(grades[0], churchAccess="Sequence 8 and above")
    with pytest.raises(ValueError):
        extract_artifacts.verify(grades, intro)


@needs_pdf
def test_losing_the_classification_sentence_would_fail_verification():
    grades, _ = _grades_and_intro()
    with pytest.raises(ValueError):
        extract_artifacts.verify(grades, "Artifacts are graded.")
