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


def test_the_grid_is_one_column_short_of_the_ladder():
    """256 pts is printed as a cost and given no offset, so SkillLadder
    extrapolating that rung by +1 is a reading rather than a transcription."""
    table = ladder_table()
    assert len(table["cumulative"]) == 9
    assert all(len(r["offsets"]) == 8 for r in table["rungs"])


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
    """Core 4e is 1/2/4/8/12/16 with +4 per level past Attr+2. Using it would
    make 14 of the 23 sheets' 232 skill levels wrong."""
    cumulative = ladder_table()["cumulative"]
    assert 12 not in cumulative
    assert all(b == a * 2 for a, b in zip(cumulative, cumulative[1:]))


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

ARTIFACTS = json.loads(
    (CONTENT / "sealed_artifacts.json").read_text(encoding="utf-8"))["entries"]
GRADES = json.loads(
    (CONTENT / "sealed_artifact_grades.json").read_text(encoding="utf-8"))["entries"]


def artifact(name):
    return next(a for a in ARTIFACTS if a["name"] == name)


def test_every_artifact_confirms_its_own_grades_printed_band():
    """THE WARRANT for joining the two tables. Each artifact header prints its
    own "Seq N equivalent"; all four land inside their grade's printed band, so
    the grade table's power column is confirmed four times independently."""
    bands = {g["grade"]: (g["sequenceLow"], g["sequenceHigh"]) for g in GRADES}
    assert bands == {3: (8, 9), 2: (5, 7), 1: (3, 4), 0: (1, 2)}
    for a in ARTIFACTS:
        low, high = bands[a["grade"]]
        assert low <= a["sequenceEquivalent"] <= high, a["name"]


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


def test_the_spi_cell_is_the_ability_anchor():
    """Three of the four ability tables sit across a page break, so only the
    cost cell has a fixed shape. The name is the line before it."""
    rows = extract_artifacts.parse_abilities([
        "Bribe — Weaken", "1 SPI", "Touch the palm to a target.",
        "False Majesty", "1 SPI/turn", "An aura of command.",
    ])
    assert [r["name"] for r in rows] == ["Bribe — Weaken", "False Majesty"]
    assert rows[0]["perTurn"] is False and rows[1]["perTurn"] is True
    assert rows[0]["effect"] == "Touch the palm to a target."


def test_only_the_per_use_hp_rule_is_charged():
    """Three of the four artifacts print an HP number in their drawbacks and
    only the gauntlet's is per use. The others are conditional on telling a
    lie, on touching the flame, or on a use count."""
    per_use = {a["name"]: a["hpPerUse"] for a in ARTIFACTS if "hpPerUse" in a}
    assert per_use == {"Gauntlet of the False Baron": 1}
    per_ability = {ab["name"]: ab["hpCost"]
                   for a in ARTIFACTS for ab in a["abilities"] if "hpCost" in ab}
    assert per_ability == {"Extinguish Memory": 1}
    # The uncharged HP is still printed, so the narrow reading stays a decision.
    prose = " ".join(d for a in ARTIFACTS for d in a["drawbacks"])
    assert "the flagon grows hot (1 HP burn to the hand)" in prose
    assert "the user loses 1 HP permanently" in prose


def test_an_ability_charging_no_hp_carries_no_field():
    """Charging no HP is not charging zero -- the RitualCritical.corGained
    distinction, one chapter over."""
    for a in ARTIFACTS:
        for ab in a["abilities"]:
            if ab["name"] != "Extinguish Memory":
                assert "hpCost" not in ab, (a["name"], ab["name"])
    assert "hpPerUse" not in artifact("The Peerless Flagon")


def test_no_artifact_ate_a_label_or_lost_a_section():
    """The first ability NAME is the line before the first SPI cell and nothing
    marks it, so it lands in appearance and has to be taken back. If that ever
    slipped, the appearance prose would swallow it."""
    assert len(ARTIFACTS) == 4
    assert sum(len(a["abilities"]) for a in ARTIFACTS) == 13
    for a in ARTIFACTS:
        assert "Appearance" not in a["appearance"], a["name"]
        assert "Drawbacks" not in a["appearance"], a["name"]
        assert len(a["drawbacks"]) == 3 and a["sealing"], a["name"]
        assert not a["appearance"].endswith(a["abilities"][0]["name"]), a["name"]
    assert artifact("Gauntlet of the False Baron")["abilities"][0]["name"] \
        == "Bribe — Weaken"


@needs_pdf
def test_an_artifact_outside_its_grades_band_would_fail_verification():
    """The join is warranted only while the two tables agree. If one artifact
    stopped confirming its grade, joining them would need rereading."""
    grades = extract_artifacts.parse_grades(
        [t for b, t in extract_artifacts.stream(
            extract_artifacts.load_lines(), *extract_artifacts.GRADE_RANGE) if not b])
    artifacts = [dict(a) for a in extract_artifacts.parse_artifacts(
        extract_artifacts.stream(
            extract_artifacts.load_lines(), *extract_artifacts.ARTIFACT_RANGE))]
    intro = " ".join(t for b, t in extract_artifacts.stream(
        extract_artifacts.load_lines(), *extract_artifacts.INTRO_RANGE))
    extract_artifacts.verify(grades, artifacts, intro)  # agrees today
    artifacts[0]["sequenceEquivalent"] = 9
    with pytest.raises(ValueError):
        extract_artifacts.verify(grades, artifacts, intro)


@needs_pdf
def test_charging_hp_on_a_conditional_drawback_would_fail_verification():
    grades = extract_artifacts.parse_grades(
        [t for b, t in extract_artifacts.stream(
            extract_artifacts.load_lines(), *extract_artifacts.GRADE_RANGE) if not b])
    artifacts = [dict(a) for a in extract_artifacts.parse_artifacts(
        extract_artifacts.stream(
            extract_artifacts.load_lines(), *extract_artifacts.ARTIFACT_RANGE))]
    intro = " ".join(t for b, t in extract_artifacts.stream(
        extract_artifacts.load_lines(), *extract_artifacts.INTRO_RANGE))
    for a in artifacts:
        a["hpPerUse"] = 1
    with pytest.raises(ValueError):
        extract_artifacts.verify(grades, artifacts, intro)
