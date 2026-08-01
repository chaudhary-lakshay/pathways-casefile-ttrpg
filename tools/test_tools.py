import json, subprocess, sys, pathlib

import pytest

import extract_pdf
import extract_sheets

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "build" / "extract"

# KNOWN_ISSUES: "Python tooling is not reproducible off this machine" --
# extract_pdf.py/extract_sheets.py hardcoded ~/Downloads paths and this
# suite invoked both with check=True, so it failed hard on any machine
# without the source rulebook/spreadsheets (including CI). The source
# paths are now overridable (PATHWAYS_RULEBOOK_PDF / PATHWAYS_SHEETS_DIR
# env vars, or --pdf/--src), and any test that needs the *source* files
# (rather than the already-committed fixtures/content) skips gracefully
# when they are absent, instead of failing.
PDF_PATH = extract_pdf.resolve_pdf_path()
SHEETS_DIR = extract_sheets.resolve_src_dir()

needs_pdf = pytest.mark.skipif(
    not PDF_PATH.exists(),
    reason=f"source rulebook PDF not found at {PDF_PATH} "
           f"(set PATHWAYS_RULEBOOK_PDF or pass --pdf to reproduce off this machine)",
)
needs_sheets = pytest.mark.skipif(
    not (SHEETS_DIR.exists() and any(SHEETS_DIR.glob("*.xlsx"))),
    reason=f"source character sheets not found at {SHEETS_DIR} "
           f"(set PATHWAYS_SHEETS_DIR or pass --src to reproduce off this machine)",
)

@needs_pdf
def test_extract_pdf_produces_pages_and_chapters():
    subprocess.run([sys.executable, str(ROOT / "tools" / "extract_pdf.py")], check=True)

    pages = json.loads((OUT / "pages.json").read_text(encoding="utf-8"))
    assert len(pages) == 170
    assert pages[0]["page"] == 1
    assert "POWERED BY GURPS" in pages[0]["text"]

    chapters = json.loads((OUT / "chapters.json").read_text(encoding="utf-8"))
    ids = [c["id"] for c in chapters]
    assert "3" in ids and "9" in ids and "12" in ids

    ch3 = next(c for c in chapters if c["id"] == "3")
    assert ch3["title"] == "Character Creation"
    assert ch3["startPage"] == 10

    # ranges must be contiguous and non-overlapping
    for a, b in zip(chapters, chapters[1:]):
        assert a["endPage"] < b["startPage"]

FIX = ROOT / "fixtures"

@needs_sheets
def test_extract_sheets_produces_23_fixtures():
    subprocess.run([sys.executable, str(ROOT / "tools" / "extract_sheets.py")], check=True)
    assert len(sorted(FIX.glob("*.json"))) == 23

@needs_sheets
def test_occult_scholar_fixture_is_exact():
    subprocess.run([sys.executable, str(ROOT / "tools" / "extract_sheets.py")], check=True)
    f = json.loads((FIX / "occult_scholar.json").read_text(encoding="utf-8"))

    assert f["attributes"] == {"ST": 9, "DX": 9, "IQ": 10, "HT": 9}
    assert f["published"]["hp"] == 9
    assert f["published"]["will"] == 10
    assert f["published"]["dodge"] == 7
    assert f["published"]["totalSpent"] == 91
    # POINT BREAKDOWN subtotals (KNOWN_ISSUES: fixtures carried no
    # per-category breakdown). 20 (attributes) + 15 (advantages) + 56
    # (skills) = 91 = totalSpent; disadvantages give points back so they
    # are not part of totalSpent, but are still captured here.
    assert f["published"]["attributesSubtotal"] == 20
    assert f["published"]["advantagesSubtotal"] == 15
    assert f["published"]["disadvantagesSubtotal"] == 30
    assert f["published"]["skillsSubtotal"] == 56

    occultism = next(s for s in f["skills"] if s["name"] == "Occultism")
    assert occultism["difficulty"] == "A"
    assert occultism["points"] == 16
    assert occultism["publishedLevel"] == 13

    history = next(s for s in f["skills"] if s["name"] == "History")
    assert history["specialization"] == "Occult"
    assert history["difficulty"] == "H"
    assert history["points"] == 8
    assert history["publishedLevel"] == 11

    assert sum(s["points"] for s in f["skills"]) == 56
    assert sum(a["cost"] for a in f["advantages"]) == 15
    assert sum(d["cost"] for d in f["disadvantages"]) == 30

def test_beat_cop_has_no_point_breakdown():
    # Reads only the already-committed fixture, so this needs neither the
    # source PDF nor the source spreadsheets -- no skip guard required.
    f = json.loads((FIX / "beat_cop.json").read_text(encoding="utf-8"))
    assert f["published"]["bpTotal"] is None
    assert f["published"]["totalSpent"] is None
    assert f["published"]["attributesSubtotal"] is None
    assert f["published"]["advantagesSubtotal"] is None
    assert f["published"]["disadvantagesSubtotal"] is None
    assert f["published"]["skillsSubtotal"] is None

@needs_pdf
def test_coverage_reports_every_chapter():
    subprocess.run([sys.executable, str(ROOT / "tools" / "extract_pdf.py")], check=True)
    subprocess.run([sys.executable, str(ROOT / "tools" / "coverage.py")], capture_output=True, text=True)
    report = (OUT / "coverage.md").read_text(encoding="utf-8")
    assert "| 3 | Character Creation |" in report
    assert "| 9 | Sequence 9 Potion Effects |" in report
    # With no content extracted yet, every chapter is 0% claimed.
    assert "0.0%" in report

@needs_pdf
def test_coverage_intersects_claims_with_mechanical_lines():
    """Verify set arithmetic detects unclaimed lines when count-based arithmetic would fail.

    This is a regression test for Finding 1: the old formula
    unclaimed = max(0, mechanical - claimed_lines) would report 0 (fully covered) when
    claimed_lines >= mechanical, regardless of WHICH lines were claimed. We test the
    pathological case: claim 627 lines from a different chapter under chapter 3's id.
    Old formula would give 0 (false negative). New formula should detect all 459
    mechanical lines are still unclaimed.
    """
    import re as regex
    subprocess.run([sys.executable, str(ROOT / "tools" / "extract_pdf.py")], check=True)
    subprocess.run([sys.executable, str(ROOT / "tools" / "coverage.py")], capture_output=True, text=True)

    lines_data = json.loads((OUT / "lines.json").read_text(encoding="utf-8"))
    chapters = json.loads((OUT / "chapters.json").read_text(encoding="utf-8"))

    # Get chapter 3 and chapter 5 line ranges
    ch3 = next(c for c in chapters if c["id"] == "3")
    ch5 = next(c for c in chapters if c["id"] == "5")

    ch5_lines = [line for line in lines_data if ch5["startPage"] <= line["page"] <= ch5["endPage"]]

    # Pathological claim: all 627 lines from chapter 5, filed under chapter 3's id
    # This exceeds chapter 3's mechanical count (459), triggering the count-based bug
    claim_start = ch5_lines[0]["n"]
    claim_end = ch5_lines[-1]["n"]

    # Track whether content_dir existed before the test
    content_dir = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"
    content_dir_existed = content_dir.exists()

    content_dir.mkdir(parents=True, exist_ok=True)
    temp_file = content_dir / "test_temp.json"
    temp_file.write_text(
        json.dumps({
            "sourceLines": {
                "3": [[claim_start, claim_end]]
            },
            "entries": []
        }),
        encoding="utf-8"
    )

    try:
        # Run coverage with the pathological claim
        subprocess.run(
            [sys.executable, str(ROOT / "tools" / "coverage.py")],
            capture_output=True,
            text=True
        )
        report = (OUT / "coverage.md").read_text(encoding="utf-8")

        # Extract unclaimed count for chapter 3
        match = regex.search(r"\| 3 \| Character Creation \|.*\| (\d+) \|$", report, regex.MULTILINE)
        assert match, "Chapter 3 should be in coverage report"
        unclaimed = int(match.group(1))

        # With new formula: all 459 mechanical lines are unclaimed (claimed span is outside ch3)
        # With old formula: would give max(0, 459-627) = 0 (false negative)
        assert unclaimed > 0, (
            f"Expected unclaimed > 0 for pathological claim, got {unclaimed}. "
            f"Old formula would give 0. Set-based formula should detect unclaimed lines."
        )
    finally:
        # Clean up temporary content file
        if temp_file.exists():
            temp_file.unlink()

        # Remove content directory only if the test created it
        if not content_dir_existed and content_dir.exists() and not list(content_dir.iterdir()):
            content_dir.rmdir()


@needs_pdf
def test_coverage_glob_is_recursive_over_nested_content_dirs():
    """Regression for KNOWN_ISSUES #6: CONTENT.glob("*.json") was
    non-recursive, so a content file's claims silently vanished from
    coverage if it happened to live in a subdirectory under
    core/rules/src/main/resources/content/ instead of directly in it.

    We claim the whole of chapter 3's line range from a file placed in a
    nested subdirectory, and require the reported unclaimed-mechanical
    count for chapter 3 to drop to 0 -- proving the nested file's claim
    was actually picked up (a non-recursive glob would never see it, and
    chapter 3 already has real unclaimed mechanical lines otherwise, per
    the coverage.md fixture committed before content extraction existed).
    """
    subprocess.run([sys.executable, str(ROOT / "tools" / "extract_pdf.py")], check=True)

    lines_data = json.loads((OUT / "lines.json").read_text(encoding="utf-8"))
    chapters = json.loads((OUT / "chapters.json").read_text(encoding="utf-8"))
    ch3 = next(c for c in chapters if c["id"] == "3")
    ch3_lines = [line for line in lines_data if ch3["startPage"] <= line["page"] <= ch3["endPage"]]
    claim_start, claim_end = ch3_lines[0]["n"], ch3_lines[-1]["n"]

    content_dir = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"
    nested_dir = content_dir / "nested_subdir_regression_test"
    nested_dir.mkdir(parents=True, exist_ok=True)
    temp_file = nested_dir / "test_nested_temp.json"
    temp_file.write_text(
        json.dumps({"sourceLines": {"3": [[claim_start, claim_end]]}, "entries": []}),
        encoding="utf-8",
    )
    try:
        subprocess.run(
            [sys.executable, str(ROOT / "tools" / "coverage.py")],
            capture_output=True, text=True,
        )
        report = (OUT / "coverage.md").read_text(encoding="utf-8")
        import re as regex
        match = regex.search(r"\| 3 \| Character Creation \|.*\| (\d+) \|$", report, regex.MULTILINE)
        assert match, "Chapter 3 should be in coverage report"
        unclaimed = int(match.group(1))
        assert unclaimed == 0, (
            f"expected 0 unclaimed mechanical lines for chapter 3 once its whole range is "
            f"claimed by a nested content file, got {unclaimed}. A non-recursive glob would "
            f"silently ignore the nested claim and leave this nonzero."
        )
    finally:
        if temp_file.exists():
            temp_file.unlink()
        if nested_dir.exists() and not list(nested_dir.iterdir()):
            nested_dir.rmdir()
        # regenerate coverage.md from the real (non-synthetic) content files
        # so this test doesn't leave a stale pathological report on disk.
        subprocess.run([sys.executable, str(ROOT / "tools" / "coverage.py")], capture_output=True, text=True)


def test_mechanical_regex_counts_spaced_sign_modifiers():
    """Regression for KNOWN_ISSUES #7: the MECHANICAL regex required no
    space between a sign and its digits, so a real signed modifier printed
    with a space ("Attribute - 4") was not counted as mechanical. That
    understates the denominator, making coverage look BETTER than it
    really is -- the one direction this guard must never err in. Widening
    the pattern to tolerate a space must not stop it matching the
    unspaced/dice/point-cost forms it already handled.
    """
    import coverage as coverage_tool

    assert coverage_tool.MECHANICAL.search("Attribute - 4") is not None
    # still matches every previously-supported shape
    assert coverage_tool.MECHANICAL.search("+1 to all active defenses") is not None
    assert coverage_tool.MECHANICAL.search("-15") is not None
    assert coverage_tool.MECHANICAL.search("15 pts") is not None
    assert coverage_tool.MECHANICAL.search("1d-2") is not None
    assert coverage_tool.MECHANICAL.search("no mechanical content here at all") is None


# --- T1: pathway / sequence extraction (chapters 9-12) ---------------------

import extract_pathways

CONTENT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"


def test_sequence_header_parses_sequence_name_and_pathway():
    assert extract_pathways.parse_header("Sequence 9: Seer · Fool Pathway") == (9, "Seer", "Fool")
    # names carry parentheses and multiple words
    assert extract_pathways.parse_header(
        "Sequence 8: Unwinged Angel (Coldblooded) · Abyss Pathway"
    ) == (8, "Unwinged Angel (Coldblooded)", "Abyss")
    assert extract_pathways.parse_header("Ritualistic Magic/IQ [Very Hard]") is None


def test_stat_rows_pair_an_attribute_with_its_signed_gain():
    stats, prose = extract_pathways.parse_stats(["ST", "+1", "DX", "+2", "Will", "-1"])
    assert stats == [
        {"attr": "ST", "delta": 1.0},
        {"attr": "DX", "delta": 2.0},
        {"attr": "Will", "delta": -1.0},
    ]
    assert prose == ""


def test_fractional_basic_speed_gain_keeps_the_note_on_its_line():
    stats, prose = extract_pathways.parse_stats(
        ["Basic Speed", "+0.25 — reflexes sharpen", "for precise timing"]
    )
    assert stats == [{"attr": "Basic Speed", "delta": 0.25, "note": "— reflexes sharpen"}]
    # the wrapped remainder is not silently dropped
    assert "for precise timing" in prose


def test_advantage_rows_are_kept_as_prose_rather_than_invented_structure():
    """The potion tables interleave attribute rows with advantage rows whose
    name/effect boundary is not recoverable from the flattened PDF text
    ("Sunlight/purification/holy" is a wrapped effect line, "Poison
    Resistance" is a name, and nothing textual separates them). Attributes
    are parsed strictly; everything else is preserved verbatim as prose."""
    stats, prose = extract_pathways.parse_stats(
        ["SPI", "+9", "Eidetic Memory", "continuously grows as the", "potion digests"]
    )
    assert stats == [{"attr": "SPI", "delta": 9.0}]
    assert prose == "Eidetic Memory continuously grows as the potion digests"


def test_skill_rows_parse_name_attribute_difficulty_and_bonus():
    skills, unresolved = extract_pathways.parse_skills(
        ["Ritualistic Magic/IQ [Very Hard]", "+3", "Observation/PER [Average]", "+2"],
        known={"Ritualistic Magic", "Observation"},
    )
    # "Observation/PER" is printed in caps by one block; the attribute is
    # normalised, not treated as a different attribute.
    assert skills == [
        {"name": "Ritualistic Magic", "attr": "IQ", "difficulty": "VH", "bonus": 3},
        {"name": "Observation", "attr": "Per", "difficulty": "A", "bonus": 2},
    ]
    assert unresolved == []


def test_bonus_note_wrapping_into_the_next_row_stays_with_its_own_grant():
    """Harvest Priest prints "+2 (Nature, Life, and Weather domains)" across
    four wrapped lines, so once the columns are flattened the note runs
    straight into the next row's name. The note must stay attached to the
    grant it qualifies, and must not be mistaken for part of the next name."""
    skills, unresolved = extract_pathways.parse_skills(
        ["Ritualistic Magic/IQ [Very Hard]", "+2 (Nature,", "Life, and", "Weather",
         "domains)", "Herb Lore/IQ [Average]", "+3"],
        known={"Ritualistic Magic", "Herb Lore"},
    )
    assert unresolved == []
    assert [s["name"] for s in skills] == ["Ritualistic Magic", "Herb Lore"]
    assert skills[0]["note"] == "(Nature, Life, and Weather domains)"
    assert "note" not in skills[1]


def test_a_grant_naming_no_known_skill_is_reported_not_guessed():
    skills, unresolved = extract_pathways.parse_skills(
        ["Chemistry/IQ [Hard]", "+2", "Inventor!/IQ [Wildcard]", "+2"],
        known={"Chemistry"},
    )
    assert [s["name"] for s in skills] == ["Chemistry"]
    assert unresolved == ["Inventor!/IQ [Wildcard] +2"]


def test_a_block_stops_at_the_next_chapter_heading():
    """A block runs until the next "Sequence N:" header -- but a chapter's
    last blocks are printed on the next chapter's first page, and the next
    chapter opens with intro prose before its own first block. Bounding a
    block only by the next header swallowed that intro: Sequence 9 Lawyer's
    ability text ended with chapter 10's "how to read a pathway entry"
    paragraph, in the committed JSON."""
    lines = [
        {"n": 1, "page": 1, "text": "Sequence 9: Seer · Fool Pathway"},
        {"n": 2, "page": 1, "text": " Ability"},
        {"n": 3, "page": 1, "text": "Spirit Vision: sees ghosts and spectres."},
        {"n": 4, "page": 2, "text": "Chapter 10: Sequence 8 Potion Effects"},
        {"n": 5, "page": 2, "text": "How to Read These Entries: read +N as Attribute + N."},
        {"n": 6, "page": 2, "text": "Sequence 8: Clown · Fool Pathway"},
        {"n": 7, "page": 2, "text": " Ability"},
        {"n": 8, "page": 2, "text": "Clown Agility: impossible acrobatics."},
    ]
    entries, spans, _ = extract_pathways.extract(lines, {"9": (1, 1), "10": (2, 2)})

    assert entries[0]["ability"] == "Spirit Vision: sees ghosts and spectres."
    assert entries[0]["sourceLines"] == [1, 3]
    # the intro is left unclaimed rather than credited to the previous block
    assert spans == {"9": [[1, 3]], "10": [[6, 8]]}


def test_pathways_json_covers_88_blocks_across_22_pathways():
    data = json.loads((CONTENT / "pathways.json").read_text(encoding="utf-8"))
    entries = data["entries"]
    assert len(entries) == 88
    assert {e["sequence"] for e in entries} == {6, 7, 8, 9}
    pathways = {e["pathway"] for e in entries}
    assert len(pathways) == 22
    # every pathway has exactly one block per sequence
    for pathway in pathways:
        seqs = sorted(e["sequence"] for e in entries if e["pathway"] == pathway)
        assert seqs == [6, 7, 8, 9], f"{pathway} has {seqs}"
    assert set(data["sourceLines"]) == {"9", "10", "11", "12"}


def test_every_granted_skill_resolves_against_the_chapter_3_skill_list():
    """Anti-lie guard (spec 7.1): a granted skill that names no real skill is
    an extraction bug. The chapter-3 list is the vocabulary; blocks may only
    reference names it defines."""
    known = {
        e["name"]
        for e in json.loads((CONTENT / "skills.json").read_text(encoding="utf-8"))["entries"]
    }
    entries = json.loads((CONTENT / "pathways.json").read_text(encoding="utf-8"))["entries"]
    unknown = sorted(
        {extract_pathways.base_name(s["name"]) for e in entries for s in e["skills"]}
        - known
        - extract_pathways.SKILLS_NOT_IN_CHAPTER_3
    )
    assert unknown == [], f"granted skills absent from skills.json: {unknown}"


def test_recorded_skill_disagreements_between_chapter_3_and_the_potion_tables():
    """Guard against silent repair: some skills are printed with a different
    difficulty in the potion tables than in the chapter-3 list. The book
    disagrees with itself; the extractor records the disagreement and must
    not normalise either side away."""
    skills = json.loads((CONTENT / "skills.json").read_text(encoding="utf-8"))["entries"]
    ch3 = {}
    for e in skills:
        ch3.setdefault(e["name"], set()).add((e["attr"], e["difficulty"]))
    entries = json.loads((CONTENT / "pathways.json").read_text(encoding="utf-8"))["entries"]

    disagreements = set()
    for e in entries:
        for s in e["skills"]:
            base = extract_pathways.base_name(s["name"])
            if base in ch3 and (s["attr"], s["difficulty"]) not in ch3[base]:
                disagreements.add(base)
    assert disagreements == extract_pathways.DIFFICULTY_DISAGREEMENTS


# --- CoR tables (Ch. 6 p. 45, Ch. 7 p. 68) ---------------------------------

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


# --- Ritual resolution (Ch. 7, p. 67-68) -----------------------------------

import extract_ritual


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
    # The bullet glyph itself must not reach the app.
    assert all(not e["modifier"].startswith("") for e in entries)


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
