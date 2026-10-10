"""Ritual modes, entity response, power sources and the worked rituals. Split out of test_tools.py."""
import json

import pytest

import extract_ritual

from testsupport import CONTENT, OUT, needs_pdf


import extract_ritual_modes


def _entries(name):
    return json.loads((CONTENT / name).read_text(encoding="utf-8"))["entries"]


def support_entries():
    return _entries("ritual_support.json")


def quick_preparation_entries():
    return _entries("ritual_quick_preparation.json")


def target_link_entries():
    return _entries("ritual_target_link.json")


def full_preparation_entries():
    return _entries("ritual_preparation.json")


def ritual_cap_sentence():
    cells = extract_ritual_modes.stream(
        extract_ritual_modes.load_lines(), *extract_ritual_modes.SUPPORT_RANGE
    )
    return next(c for c in cells if extract_ritual_modes.CAP_SENTENCE in c)


def test_the_two_preparation_tables_disagree_on_excellent():
    """The book prints one preparation table per mode. Quick's Excellent is
    +2, Phase I's is +3, and the app applied Phase I's to every ritual --
    including the routine ones the Quick table governs."""
    quick = {e["quality"]: e["modifier"] for e in quick_preparation_entries()}
    full = {e["quality"]: e["modifier"] for e in full_preparation_entries()}
    assert quick["Excellent"] == 2
    assert full["Excellent"] == 3


def test_they_agree_on_adequate_which_is_what_makes_it_a_disagreement():
    """Two unrelated ladders would differ everywhere. These are two printings
    of one idea that part company on exactly one row."""
    quick = {e["quality"]: e["modifier"] for e in quick_preparation_entries()}
    full = {e["quality"]: e["modifier"] for e in full_preparation_entries()}
    assert quick["Adequate"] == full["Adequate"] == 0


@needs_pdf
def test_reconciled_preparation_tables_would_fail_verification():
    """If someone "fixed" the quick table to match Phase I, the whole mode
    distinction would vanish silently."""
    patched = [
        {**e, "modifier": 3} if e["quality"] == "Excellent" else e
        for e in quick_preparation_entries()
    ]
    with pytest.raises(ValueError):
        extract_ritual_modes.verify(
            support_entries(), ritual_cap_sentence(), patched, target_link_entries()
        )


def test_the_full_tables_rows_name_their_links():
    """The only evidence the link is folded INTO Phase I's modifier rather
    than added beside it. Without this phrasing nothing stops a full ritual
    counting a perfect link twice, +8 on a 3d6 roll."""
    described = " ".join(e["looksLike"].lower() for e in full_preparation_entries())
    for phrase in extract_ritual_modes.LINKS_NAMED_BY_FULL_TABLE:
        assert phrase in described


@needs_pdf
def test_losing_that_phrasing_would_fail_verification():
    original = extract_ritual_modes.full_preparation
    try:
        extract_ritual_modes.full_preparation = lambda: [
            {**e, "looksLike": e["looksLike"].replace("link", "thing")}
            for e in full_preparation_entries()
        ]
        with pytest.raises(ValueError):
            extract_ritual_modes.verify(
                support_entries(), ritual_cap_sentence(),
                quick_preparation_entries(), target_link_entries(),
            )
    finally:
        extract_ritual_modes.full_preparation = original


def test_the_target_link_ladder_runs_perfect_to_none():
    entries = target_link_entries()
    assert [e["link"] for e in entries] == extract_ritual_modes.EXPECTED_LINKS
    assert [e["modifier"] for e in entries] == [4, 2, 0, -4]
    # Ranges are carried at both ends, applied at the end nearer zero.
    by_link = {e["link"]: e for e in entries}
    assert by_link["Strong"]["printed"] == "+2 to +3" and by_link["Strong"]["best"] == 3
    assert by_link["Weak"]["printed"] == "+0 to -1" and by_link["Weak"]["best"] == -1


def test_five_supporting_skills_each_grant_one():
    entries = support_entries()
    assert [e["skill"] for e in entries] == extract_ritual_modes.EXPECTED_SUPPORT
    assert all(e["bonus"] == 1 for e in entries)
    assert all(e["notes"] for e in entries)


def test_the_last_supporting_skills_note_wraps_and_is_joined():
    """"When the ritual's material component or timing falls within that" /
    "domain". Counting three cells per row would make "domain" the next
    skill's name."""
    professional = next(
        e for e in support_entries() if e["skill"].startswith("Professional")
    )
    assert professional["notes"].endswith("that domain")


def test_the_cap_is_a_sentence_not_a_column():
    """Five skills grant five points and the book allows three. The number
    lives in Kotlin (RitualMode.SUPPORT_CAP) the same way the maneuver
    table's +4/-4 do; this pins the sentence it was read from."""
    assert extract_ritual_modes.CAP_SENTENCE in ritual_cap_sentence()
    assert sum(e["bonus"] for e in support_entries()) > extract_ritual_modes.SUPPORT_CAP
    # And the cap is deliberately not committed as data.
    raw = json.loads((CONTENT / "ritual_support.json").read_text(encoding="utf-8"))
    assert "cap" not in raw


def test_the_chapter_grades_a_ritual_that_worked():
    """Ch. 7 Step 4 (p. 62). Section VI is "Failure & Consequences" and prints
    no success row, so this is the only place the chapter says how WELL a
    ritual went."""
    rows = json.loads(
        (CONTENT / "ritual_success.json").read_text(encoding="utf-8"))["entries"]
    assert [(r["from"], r["to"]) for r in rows] == [(3, None), (0, 2)]
    assert "surpasses intent" in rows[0]["outcome"]
    assert "as intended" in rows[1]["outcome"]
    # The milder band starts at 0, not 1: a ritual that exactly hits its
    # target succeeded, and must land on a row.
    assert rows[1]["from"] == 0


@needs_pdf
def test_step_fours_failure_rows_coarsen_section_six():
    """Why only the SUCCESS half of that table is stored.

    Both halves come off one table, so its failure rows agreeing with Section
    VI is the entire warrant for trusting its unrepeated success rows -- the
    per-table standard 6ad set. And they agree by COARSENING: Step 4's
    "Failure by 5+" is Section VI's "5-9" and "10+" merged, with the same
    sentences abbreviated, which is what p. 61's "the same underlying canon
    procedure ... different levels of granularity" describes. Two rival
    tables would be 6y's shape and the app would have to pick per mode.
    """
    byn = extract_ritual.load_lines()
    cells = extract_ritual.stream(byn, *extract_ritual.STEP_FOUR_RANGE)
    failures = json.loads(
        (CONTENT / "ritual_failures.json").read_text(encoding="utf-8"))["entries"]

    here = {int(m.group(1)) for m in
            (extract_ritual.FAILURE.match(c) for c in cells) if m}
    there = {r["from"] for r in failures if not r["critical"]}
    assert here <= there, f"Step 4 starts bands at {sorted(here)}, VI at {sorted(there)}"
    # And the coarsening is real rather than the two being identical.
    assert here != there, (
        "Step 4 now prints the same bands as Section VI. It used to print "
        "fewer, which is what made it a summary rather than a rival table"
    )

    # The full ritual is not off using some other table.
    close = " ".join(extract_ritual.stream(byn, *extract_ritual.FULL_CLOSE_RANGE))
    assert extract_ritual.FULL_DEFERS_TO_STEP_FOUR in close


@needs_pdf
def test_the_step_four_block_is_claimed_whole():
    """It holds a third printing of the weight table and the burning clause
    besides the outcome rows. Neither is stored -- both are asserted -- and an
    assertion whose source sentence is unclaimed is a rule nobody can find
    again (6af)."""
    raw = json.loads((CONTENT / "ritual_success.json").read_text(encoding="utf-8"))
    assert raw["sourceLines"]["7"] == [list(extract_ritual.STEP_FOUR_RANGE)]
    joined = " ".join(
        extract_ritual.stream(extract_ritual.load_lines(),
                              *extract_ritual.STEP_FOUR_RANGE))
    for weight in ("Light", "Moderate", "Heavy"):
        assert weight in joined
    assert extract_ritual.QUICK_BURN in joined
    # The weights are stored once, by ritual_weights.json, not twice.
    assert "SPI" not in json.dumps(raw["entries"])


@needs_pdf
def test_ritualistic_magic_prints_its_own_prohibition():
    """Ch. 7 II p. 61, one line above the supporting-skill table this module
    already walked -- the seam the sweep found (6ba).

    It is the only thing limiting ch. 6.5's "and vice versa" to one
    direction, and the reason it is adopted is that "cannot be substituted"
    appears NOWHERE else in the book. If it ever turned up under other Very
    Hard blurbs it would be boilerplate and the refusal would need re-arguing,
    so the uniqueness is asserted rather than recalled.
    """
    byn = extract_ritual_modes.load_lines()
    seam = " ".join(
        extract_ritual_modes.stream(byn, *extract_ritual_modes.NO_DEFAULT_RANGE))
    assert extract_ritual_modes.NO_DEFAULT_SENTENCE in seam
    assert extract_ritual_modes.NEVER_REPLACE_SENTENCE in seam

    printed = [n for n, t in byn.items() if "cannot be substituted" in t]
    assert printed == [extract_ritual_modes.NO_DEFAULT_RANGE[0]], (
        f"'cannot be substituted' is printed on lines {printed}. It used to "
        "appear once, which is what made it emphasis about this skill rather "
        "than boilerplate; re-read p. 61 before trusting the refusal"
    )


@needs_pdf
def test_losing_the_prohibition_would_fail_verification():
    with pytest.raises(ValueError, match="ch. 7 II no longer prints"):
        extract_ritual_modes.verify(
            support_entries(), ritual_cap_sentence(),
            quick_preparation_entries(), target_link_entries(),
            no_default="Ritualistic Magic (IQ/Very Hard) -- your core skill.",
        )


def test_committed_content_claims_the_prohibition():
    """An assertion whose source sentence is unclaimed is a rule nobody can
    find again (6af). Two spans, and the prose is deliberately not stored."""
    raw = json.loads((CONTENT / "ritual_support.json").read_text(encoding="utf-8"))
    spans = raw["sourceLines"]["7"]
    assert [4007, 4008] in spans and [4009, 4028] in spans
    assert "substituted" not in json.dumps(raw)


import extract_entity_response


def entity_entries():
    return _entries("entity_response.json")


def entity_prose():
    cells = extract_entity_response.stream(
        extract_entity_response.load_lines(), *extract_entity_response.ENTITY_RANGE
    )
    prose, _ = extract_entity_response.split_prose(cells)
    return prose


def test_the_six_bands_tile_a_3d6_exactly():
    entries = entity_entries()
    assert [(e["low"], e["high"]) for e in entries] == extract_entity_response.EXPECTED_BANDS
    covered = sorted(n for e in entries for n in range(e["low"], e["high"] + 1))
    assert covered == list(range(3, 19))


def test_the_table_is_ordered_best_first():
    """Which is the whole reason a NEGATIVE shift is a reward. If this order
    ever broke, the shift's direction would need rereading rather than
    reusing."""
    values = [e["ritualModifier"] for e in entity_entries() if "ritualModifier" in e]
    assert values == sorted(values, reverse=True) == [5, 3, 1, -2]


def test_the_orthodox_shift_is_negative_and_lands_on_the_table_roll():
    """"Orthodox deities shift the table result by -2." On the GM's 3d6, not
    on the ritual roll. Applied to the ritual roll it penalises the caster in
    good standing -- one sentence, two opposite outcomes."""
    prose = entity_prose()
    assert extract_entity_response.SHIFT.search(prose).group(1) == "-2"
    assert "shift the table result" in prose
    assert extract_entity_response.UNMODIFIED in prose


@needs_pdf
def test_a_positive_shift_would_fail_verification():
    """A positive shift would send a favoured caster toward Active rejection."""
    with pytest.raises(ValueError):
        extract_entity_response.verify(entity_entries(), entity_prose(), 2)


@needs_pdf
def test_an_unordered_table_would_fail_verification():
    patched = [
        {**e, "ritualModifier": 9} if e["low"] == 15 else e for e in entity_entries()
    ]
    with pytest.raises(ValueError):
        extract_entity_response.verify(patched, entity_prose(), -2)


def test_the_two_silent_rows_are_silent_for_different_reasons():
    """12-14 is an entity that does not answer -- nothing is added, and the
    ritual still happens. 18 is a refusal: it fails whatever was rolled, the
    way a Broken mystical language forbids the attempt rather than pricing
    it. Emitting -0 for one or a large negative for the other invents both."""
    by_low = {e["low"]: e for e in entity_entries()}
    assert "ritualModifier" not in by_low[12]
    assert not by_low[12].get("automaticFailure")
    assert by_low[18]["automaticFailure"] is True
    assert "ritualModifier" not in by_low[18]


@needs_pdf
def test_pricing_the_rejection_would_fail_verification():
    patched = [
        {**e, "ritualModifier": -10} if e["low"] == 18 else e for e in entity_entries()
    ]
    with pytest.raises(ValueError):
        extract_entity_response.verify(patched, entity_prose(), -2)


def test_the_shift_is_not_committed_as_data():
    """It is a number inside a sentence, so it lives in Kotlin
    (Invocation.ORTHODOX_SHIFT) -- and a committed column would lose the fact
    that matters most about it: which roll it lands on."""
    raw = json.loads((CONTENT / "entity_response.json").read_text(encoding="utf-8"))
    assert "shift" not in raw


import extract_power_sources


def complexity_entries():
    return json.loads(
        (CONTENT / "ritual_complexity.json").read_text(encoding="utf-8"))["entries"]


def weight_entries():
    return json.loads(
        (CONTENT / "ritual_weights.json").read_text(encoding="utf-8"))["entries"]


def sacrifice_entries():
    return json.loads(
        (CONTENT / "ritual_sacrifice.json").read_text(encoding="utf-8"))["entries"]


@needs_pdf
def test_the_power_sources_extract_end_to_end():
    rows, tiers, numbers = extract_power_sources.extract()
    assert [r["complexity"] for r in rows] == ["Light", "Moderate", "Heavy", "Major"]
    assert [t["tier"] for t in tiers] == ["Ordinary", "Meaningful", "Precious"]
    assert numbers["suspension"] == -2


def test_the_complexity_ladder_has_a_rung_the_weight_table_never_prints():
    """Section V prints three weights; the Time & Complexity table keys on
    four. Major is the extra one, and it carries no base difficulty modifier
    because the weight table -- the only place that column exists -- has no
    row for it. That is why it lives on the complexity axis."""
    assert [r["complexity"] for r in complexity_entries()] == [
        "Light", "Moderate", "Heavy", "Major",
    ]
    assert [w["weight"] for w in weight_entries()] == ["Light", "Moderate", "Heavy"]


def test_the_two_spi_tables_disagree_at_both_ends_and_agree_in_the_middle():
    """The whole reason this is a second axis and not a merge. Section V
    prices Light at a flat 2 and Heavy at 8+; the ladder says 2-3 and 7-10.
    The book's own Ritual 2 charges 3 for a Light divination and Ritual 3
    charges 7 for a Heavy summoning -- outside the weight column both times,
    inside the ladder both times. Moderate agrees exactly, and that single
    agreement is what makes the other two a real difference rather than two
    unrelated ladders."""
    weights = {w["weight"]: w for w in weight_entries()}
    rows = complexity_entries()

    assert (weights["Light"]["spi"], weights["Light"].get("spiMax")) == (2, None)
    assert (rows[0]["spi"], rows[0]["spiMax"]) == (2, 3)

    assert weights["Heavy"]["spi"] == 8
    assert (rows[2]["spi"], rows[2]["spiMax"]) == (7, 10)

    assert (weights["Moderate"]["spi"], weights["Moderate"]["spiMax"]) == (
        rows[1]["spi"], rows[1]["spiMax"],
    )


@needs_pdf
def test_reconciling_the_two_spi_tables_would_fail_verification():
    """The failure mode this slice exists to prevent: a later parse quietly
    straightening the ladder onto the weight column, or the reverse."""
    rows, tiers, numbers = extract_power_sources.extract()
    byn = extract_power_sources.load_lines()
    prose = " ".join(extract_power_sources.stream(byn, *extract_power_sources.PROSE_RANGE))
    worked = ("Two trained assistants", 2)

    straightened = [dict(r) for r in rows]
    straightened[0]["spi"], straightened[0]["spiMax"] = 2, None
    with pytest.raises(ValueError):
        extract_power_sources.verify(
            straightened, tiers, numbers, prose, weight_entries(), worked)


@needs_pdf
def test_losing_the_moderate_agreement_would_fail_verification():
    rows, tiers, numbers = extract_power_sources.extract()
    byn = extract_power_sources.load_lines()
    prose = " ".join(extract_power_sources.stream(byn, *extract_power_sources.PROSE_RANGE))
    drifted = [dict(r) for r in rows]
    drifted[1]["spi"] = 5
    with pytest.raises(ValueError):
        extract_power_sources.verify(
            drifted, tiers, numbers, prose, weight_entries(),
            ("Two trained assistants", 2))


@needs_pdf
def test_the_worked_ritual_is_what_settles_beyond_the_first():
    """"Each assistant beyond the first also grants +1" reads two ways.
    Ritual 3's "Two trained assistants  +2" is the only thing in the chapter
    that says which: the first participant is the CASTER, so every assistant
    counts. Read the other way, that row would be +1."""
    rows, tiers, numbers = extract_power_sources.extract()
    byn = extract_power_sources.load_lines()
    prose = " ".join(extract_power_sources.stream(byn, *extract_power_sources.PROSE_RANGE))
    with pytest.raises(ValueError):
        extract_power_sources.verify(
            rows, tiers, numbers, prose, weight_entries(),
            ("Two trained assistants", 1))


@needs_pdf
def test_the_hp_fuel_clause_is_pinned():
    """"HP sacrifice does not grant a bonus to the roll" sits one line under
    "every 2 HP spent counts as 1 SPI" -- exactly the position from which a
    fuel rule gets read as a modifier."""
    rows, tiers, numbers = extract_power_sources.extract()
    byn = extract_power_sources.load_lines()
    prose = " ".join(extract_power_sources.stream(byn, *extract_power_sources.PROSE_RANGE))
    scrubbed = prose.replace(extract_power_sources.HP_NO_BONUS, "")
    with pytest.raises(ValueError):
        extract_power_sources.verify(
            rows, tiers, numbers, scrubbed, weight_entries(),
            ("Two trained assistants", 2))


def test_the_sentence_numbers_are_not_committed_as_data():
    """+1 per assistant, +2 per sentient being, +0 to +2 for a catalyst and
    the suspension -2 are all values inside prose. Committing columns for
    them would make the book look like it printed tables it never printed --
    the split the support cap and the orthodox shift already take."""
    raw = json.loads((CONTENT / "ritual_complexity.json").read_text(encoding="utf-8"))
    assert "participantBonus" not in json.dumps(raw)
    assert "suspension" not in json.dumps(raw)
    sacrifice = json.loads((CONTENT / "ritual_sacrifice.json").read_text(encoding="utf-8"))
    assert "sentientBonus" not in json.dumps(sacrifice)


def test_the_sacrifice_tiers_keep_their_examples():
    """A GM choosing a tier needs to know what counts as Precious."""
    for tier in sacrifice_entries():
        assert tier["looksLike"].strip()
    assert [(t["tier"], t["modifier"]) for t in sacrifice_entries()] == [
        ("Ordinary", 0), ("Meaningful", 2), ("Precious", 4),
    ]


def test_rushing_always_costs_and_extending_always_pays():
    for row in complexity_entries():
        assert row["rushed"] < 0
        assert row["extended"] > 0


@needs_pdf
def test_the_study_grid_is_the_skill_ladder_cell_for_cell():
    """Line 2204 says study points "follow the same exponential ladder as
    normal character points" -- which is the book asserting the two printings
    agree, not a comparison of them. Three findings this session were a
    summary saying something true about itself and disagreeing with the table
    it summarised, so the 16 cells are compared rather than inherited."""
    lines = json.loads((OUT / "lines.json").read_text(encoding="utf-8"))
    byn = {l["n"]: l["text"] for l in lines}
    assert "same exponential ladder" in byn[2204], byn[2204]

    # Cumulative CP down the left, four difficulties across: 2211 is "1" and
    # 2212-2215 are its row, then 2216 and so on.
    printed = {}
    for row, first in enumerate((2211, 2216, 2221, 2226)):
        printed[int(byn[first].strip())] = [
            byn[first + 1 + col].strip() for col in range(4)
        ]

    ladder = json.loads(
        (CONTENT / "skill_ladder.json").read_text(encoding="utf-8"))["entries"][0]
    for row, cp in enumerate(sorted(printed)):
        assert ladder["cumulative"][row] == cp, (row, cp)
        for col, rung in enumerate(ladder["rungs"]):
            offset = rung["offsets"][row]
            cell = f"Attr{offset:+d}".replace("+0", "+0")
            assert printed[cp][col] == cell, (cp, rung["difficulty"], printed[cp][col], cell)


@needs_pdf
def test_the_quirk_allowance_is_five_ADDITIONAL_points():
    """`PointBudget.MAX_QUIRKS` is the one budget constant with no tie to a
    printed line, and the reason is 6dn's shape again: the guard that pins the
    other two reads the Character Creation Summary box, which prints the -40
    and says nothing about quirks. Its corpus excludes the constant it cannot
    see.

    The printing wraps across two lines, and the load-bearing word is
    ADDITIONAL: it is what makes the budget 70 + min(dis, 40) + min(quirks, 5)
    rather than quirks coming out of the -40."""
    lines = json.loads((OUT / "lines.json").read_text(encoding="utf-8"))
    # Keyed by the line's own number rather than sliced positionally, so the
    # numbers quoted above are what is actually read (a review's finding: a
    # positional slice proves nothing about the line it claims to cite).
    byn = {l["n"]: l["text"] for l in lines}
    sentence = " ".join(byn[n].strip() for n in (1121, 1122))
    assert "Up to 5 additional points of quirks" in sentence, sentence
    assert "-1 pt each" in sentence, sentence
    # The corpus the Kotlin guard reads, and what it cannot say.
    summary = " ".join(byn[n].strip() for n in range(2239, 2245))
    assert "40" in summary and "quirk" not in summary.lower(), summary


import extract_worked_rituals


def worked_entries():
    return json.loads(
        (CONTENT / "ritual_examples.json").read_text(encoding="utf-8"))["entries"]


def test_the_section_prints_seven_costed_rituals_not_six():
    """A previous slice's notes said six. Section VIII prints ten; seven carry
    a full modifier table and one worked total, and 8-10 are templates that
    print ranges instead. The miscount mattered because the two-axis SPI
    decision was argued from 'six of six'."""
    assert len(worked_entries()) == 7


def test_the_books_own_arithmetic_holds_in_every_worked_ritual():
    for ritual in worked_entries():
        assert sum(m["value"] for m in ritual["modifiers"]) == ritual["net"]
        assert ritual["casterSkill"] + ritual["net"] == ritual["effectiveSkill"]


def test_all_seven_fit_the_ladder_and_three_miss_the_weight_column():
    """The evidence the fuel decision rests on, asserted rather than
    remembered: seven of seven inside the complexity ladder, three of seven
    outside Section V's cost column, none the other way round."""
    rungs = json.loads(
        (CONTENT / "ritual_complexity.json").read_text(encoding="utf-8"))["entries"]
    weights = json.loads(
        (CONTENT / "ritual_weights.json").read_text(encoding="utf-8"))["entries"]

    for ritual in worked_entries():
        assert any(
            ritual["spi"] >= r["spi"]
            and (r["spiOpen"] or r["spiMax"] is None or ritual["spi"] <= r["spiMax"])
            for r in rungs
        ), f"ritual {ritual['number']} fell off the ladder"

    outside = []
    for ritual in worked_entries():
        weight = next(
            (w for w in weights
             if ritual["category"].lower() in w["categories"].lower()), None)
        if weight is None:
            continue
        top = None if weight.get("spiOpen") else weight.get("spiMax") or weight["spi"]
        if ritual["spi"] < weight["spi"] or (top is not None and ritual["spi"] > top):
            outside.append(ritual["number"])
    assert outside == [2, 3, 4]


def test_three_worked_rituals_grade_inside_the_moderate_band():
    """Curse, Oath-Sealing and Fabrication all print -3 where the weight table
    prints Moderate -2, and Ritual 8 runs to -6 where the table stops at -4.
    So Section V's modifier column is a summary the worked examples grade
    inside -- the same shape as its SPI column. Never repaired into a fourth
    weight: the book prints no table of -3s."""
    weights = json.loads(
        (CONTENT / "ritual_weights.json").read_text(encoding="utf-8"))["entries"]
    printed = {w["modifier"] for w in weights}
    graded = [r for r in worked_entries() if r["baseDifficulty"] not in printed]
    assert [r["category"] for r in graded] == ["Curse", "Oath-Sealing", "Fabrication"]
    assert all(r["baseDifficulty"] == -3 for r in graded)
    assert -3 not in printed


@needs_pdf
def test_swallowing_the_intent_prose_would_fail_verification():
    """The parse that shipped first joined a wrapped label onto the line above
    it with no anchor, which ate each ritual's Intent and Setup paragraphs
    into its first modifier row. The book prints its own anchor -- a
    "Modifier Source" header -- and every row inside it carries a value."""
    for ritual in worked_entries():
        for row in ritual["modifiers"]:
            assert "Intent:" not in row["source"]
            assert "Setup:" not in row["source"]
            assert len(row["source"]) < 70


@needs_pdf
def test_losing_a_worked_ritual_would_fail_verification():
    rituals, caster = extract_worked_rituals.extract()
    byn = extract_worked_rituals.load_lines()
    template = " ".join(
        extract_worked_rituals.stream(byn, *extract_worked_rituals.TEMPLATE_RANGE))
    weights = json.loads(
        (CONTENT / "ritual_weights.json").read_text(encoding="utf-8"))["entries"]
    rungs = json.loads(
        (CONTENT / "ritual_complexity.json").read_text(encoding="utf-8"))["entries"]
    with pytest.raises(ValueError):
        extract_worked_rituals.verify(
            rituals[:-1], caster, weights, rungs, template)


@needs_pdf
def test_ritual_nine_cannot_stand_in_for_ritual_eights_own_facts():
    """Widening TEMPLATE_RANGE put two rituals in one blob, and Ritual 8's two
    pinned disagreements are searched in it. A guard about Ritual 8 must not be
    satisfied by text Ritual 9 printed -- so the blob is cut at the book's own
    "Ritual 9:" title before those two are looked for."""
    rituals, caster = extract_worked_rituals.extract()
    byn = extract_worked_rituals.load_lines()
    template = " ".join(
        extract_worked_rituals.stream(byn, *extract_worked_rituals.TEMPLATE_RANGE))
    weights = json.loads(
        (CONTENT / "ritual_weights.json").read_text(encoding="utf-8"))["entries"]
    rungs = json.loads(
        (CONTENT / "ritual_complexity.json").read_text(encoding="utf-8"))["entries"]

    tiers = "Trivial +0, Meaningful +2, Precious +4"
    eight, nine = template.split("Ritual 9:", 1)
    moved = eight.replace(tiers, "") + "Ritual 9:" + nine + " " + tiers
    with pytest.raises(ValueError, match="is gone"):
        extract_worked_rituals.verify(rituals, caster, weights, rungs, moved)


@needs_pdf
def test_a_row_that_swallowed_a_template_range_would_fail_verification():
    """4.2 shape. The two templates sit outside both tables because they price
    a class of ritual, not one ritual. The day a printed row contains a whole
    template range the extraction FAILS, rather than the two axes merging with
    every assertion still green."""
    rituals, caster = extract_worked_rituals.extract()
    byn = extract_worked_rituals.load_lines()
    template = " ".join(
        extract_worked_rituals.stream(byn, *extract_worked_rituals.TEMPLATE_RANGE))
    weights = json.loads(
        (CONTENT / "ritual_weights.json").read_text(encoding="utf-8"))["entries"]
    rungs = json.loads(
        (CONTENT / "ritual_complexity.json").read_text(encoding="utf-8"))["entries"]
    swallowing = rungs + [
        {"complexity": "Invented", "spi": 2, "spiMax": 20, "spiOpen": False},
    ]
    with pytest.raises(ValueError, match="fits one printed row whole"):
        extract_worked_rituals.verify(
            rituals, caster, weights, swallowing, template)


@needs_pdf
def test_the_two_templates_price_a_class_no_rung_contains():
    """Section VIII prints NINE SPI costs, not seven.

    The seven worked rituals each name one number and each sits inside one
    rung of the complexity ladder. Rituals 8 and 9 print RANGES -- 2-4 and
    4-8 -- and no rung, and no row of Section V's weight table, contains
    either. That is not an error to repair: a template prices a CLASS of
    ritual across scopes where a rung prices one ritual. It is recorded
    because "all seven costed rituals" was a count taken with the filter
    "has a worked total", and the section's own costed rituals number nine.
    """
    byn = extract_worked_rituals.load_lines()
    template = " ".join(
        extract_worked_rituals.stream(byn, *extract_worked_rituals.TEMPLATE_RANGE))
    assert extract_worked_rituals.template_costs(template) == [(2, 4), (4, 8)]

    rungs = json.loads(
        (CONTENT / "ritual_complexity.json").read_text(encoding="utf-8"))["entries"]
    weights = json.loads(
        (CONTENT / "ritual_weights.json").read_text(encoding="utf-8"))["entries"]
    for row in rungs + weights:
        top = None if row.get("spiOpen") else row.get("spiMax") or row["spi"]
        for lo, hi in extract_worked_rituals.template_costs(template):
            assert not (lo >= row["spi"] and (top is None or hi <= top)), (
                f"a template's {lo}-{hi} SPI now fits one printed row whole; "
                "the two tables priced one ritual and the templates a class"
            )


@needs_pdf
def test_ritual_eight_names_the_cheapest_tier_a_second_way():
    """Section IV.D prints "Ordinary (common goods, small animals) grants +0";
    Ritual 8 prints "Trivial +0" for the same three values. Two names, one
    tier, and neither is merged -- the way "Eternal Aeon" keeps both of its
    spellings."""
    byn = extract_worked_rituals.load_lines()
    template = " ".join(
        extract_worked_rituals.stream(byn, *extract_worked_rituals.TEMPLATE_RANGE))
    assert extract_worked_rituals.TEMPLATE_TIERS.search(template)
    tiers = json.loads(
        (CONTENT / "ritual_sacrifice.json").read_text(encoding="utf-8"))["entries"]
    assert tiers[0]["tier"] == "Ordinary"
    assert "Trivial" not in json.dumps(tiers)
