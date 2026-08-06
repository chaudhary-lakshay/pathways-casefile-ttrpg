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


# --- Shields (Ch. 5, p. 37) -------------------------------------------------

import extract_shields


def test_a_wrapped_shield_name_is_walked_back_on_its_brackets():
    """"Light (buckler," / "target)" is one cell. Counting two lines per name
    would break the first time the book prints one that fits on one line."""
    cells = ["Light (buckler,", "target)", "+1", "3", "15", "2 kg", "£2", "worn"]
    assert extract_shields.name_before(cells, 2) == "Light (buckler, target)"
    # A name that does not wrap is taken whole, not glued to the row above.
    assert extract_shields.name_before(["Tower", "+3"], 1) == "Tower"


def test_a_note_holding_a_signed_number_does_not_start_a_row():
    """"-1 to Block vs. ranged" and "Move -1 while Readied" both sit in Notes
    cells. Only the bare signed integer is the DB column."""
    rows = extract_shields.parse([
        "Light (buckler,", "target)", "+1", "3", "15", "2 kg", "£2",
        "Worn on forearm; frees hand; -1 to Block vs. ranged",
    ])
    assert len(rows) == 1
    assert rows[0]["db"] == 1 and rows[0]["coverDr"] == 3 and rows[0]["hp"] == 15
    assert rows[0]["notes"].endswith("-1 to Block vs. ranged")


def test_a_bullet_takes_the_lines_that_wrap_out_of_it():
    rules = extract_shields.join_bullets([
        (False, "table row that is not a bullet"),
        (True, "Shield as cover: treat it as having Cover"),
        (False, "DR equal to its listed value × 1.5 for this purpose."),
        (True, "Shield Bash: damage = thr-2 cr for light."),
    ])
    assert len(rules) == 2
    assert rules[0].endswith("for this purpose.")


def test_committed_shields_are_the_four_the_book_prints():
    entries = json.loads((CONTENT / "shields.json").read_text(encoding="utf-8"))["entries"]
    assert [e["type"] for e in entries] == extract_shields.EXPECTED_TYPES
    # Weight and Cost stay text: the improvised row prints "varies" and a dash.
    improvised = entries[3]
    assert improvised["weight"] == "varies" and not improvised["cost"].isdigit()


# --- Spirit body damage (Ch. 5, p. 40-41) -----------------------------------

import extract_spirit


def test_a_bullet_that_wraps_is_rejoined_on_its_sentence():
    """"(e.g. skull" ends nothing, so the next line belongs to it. Cutting by
    position would leave half a rule and start the next one mid-word."""
    rules = extract_spirit.parse([
        (False, "Damage Application:"),
        (True, "Wounding multipliers apply normally to the chosen pool (e.g. skull"),
        (False, "×4, vitals ×3) stack with wounding multipliers when applied to HP."),
    ])
    assert len(rules) == 1
    assert rules[0]["rule"].endswith("when applied to HP.")
    assert rules[0]["section"] == "Damage Application"


def test_a_finished_bullet_does_not_swallow_the_note_after_it():
    """The section's last line is a standalone note, and the recovery bullet
    above it ends in a full stop. Joining by position would glue them."""
    rules = extract_spirit.parse([
        (False, "Recovery follows each pool's normal rate:"),
        (True, "CoR from Soul Debt are permanent and require special rituals to remove."),
        (False, "Non-Beyonders (SPI 0): Can only choose HP or FP — no spirituality to sacrifice."),
    ])
    assert len(rules) == 2
    assert rules[1]["section"] == "Non-Beyonders (SPI 0)"


def test_the_threshold_group_is_dropped_but_still_anchors_the_section_after_it():
    """Its five lines live in pool_thresholds.json; a second copy would be a
    rule with two homes. Dropping it must not shift what follows."""
    rules = extract_spirit.parse([
        (False, "Threshold Penalties by Pool:"),
        (True, "HP ≤ 0: Standard GURPS death/dying rules."),
        (False, "Damage Expression: Spirit body damage is given either as:"),
        (True, "Flat dice: roll damage, then choose a pool."),
    ])
    assert [r["section"] for r in rules] == ["Damage Expression"]


def test_committed_spirit_rules_keep_the_phrases_the_app_states():
    entries = json.loads((CONTENT / "spirit_damage.json").read_text(encoding="utf-8"))["entries"]
    assert list(dict.fromkeys(e["section"] for e in entries)) == extract_spirit.EXPECTED_SECTIONS
    body = " ".join(e["rule"] for e in entries)
    for phrase in extract_spirit.PINNED:
        assert phrase in body
    assert extract_spirit.DROPPED not in {e["section"] for e in entries}


import extract_boons


def test_a_modifier_bullet_splits_into_its_bracketed_options():
    rows = extract_boons.parse_options(
        "Sacrifice quality",
        "A perfunctory sacrifice (-2 to -4), an appropriate sacrifice (+0), a "
        "meaningful sacrifice (+1 to +3), or a great sacrifice that aligns "
        "perfectly with the Great One's domain (+4 to +6).",
    )
    assert [r["modifier"] for r in rows] == [-2, 0, 1, 4]
    assert [r.get("best") for r in rows] == [-4, None, 3, 6]
    # The connector is dropped; the rest of the label is the book's own text,
    # commas and all -- and no printed label contains one, which is why the
    # comma can be the boundary.
    assert rows[3]["label"] == "a great sacrifice that aligns perfectly with the Great One's domain"


def test_the_boon_level_bullet_is_kept_whole_because_it_is_a_rule():
    """Three examples and then "doubling the penalty with each sequence".
    Parsed into rows it would stop at Sequence 7 and the ladder would have to
    be guessed; kept whole, the ladder is implemented and tested in Kotlin."""
    rows, rule = extract_boons.parse_modifiers([
        "Ritual quality: Sloppy or rushed (-3), standard preparation (+0).",
        "Boon level: Sequence 9 equivalent (+0), Sequence 8 (-2), Sequence 7 "
        "(-4), and so on, doubling the penalty with each sequence.",
    ])
    assert {r["group"] for r in rows} == {"Ritual quality"}
    assert "doubling the penalty with each sequence" in rule


def test_a_modifier_bullet_with_no_options_is_a_finding_not_an_empty_group():
    with pytest.raises(ValueError):
        extract_boons.parse_options("Sacrifice quality", "some prose with no brackets")


def test_a_bullet_continuation_rejoins_across_the_page_break():
    byn = {
        1: "\x7f Boon level: Sequence 9 equivalent (+0), and so on — see Sacrifice",
        2: "Powered by GURPS: Pathways — Lord of the Mysteries",
        3: "Page 145",
        4: "quality modifiers above.",
    }
    assert extract_boons.bullets(byn, 1, 4) == [
        "Boon level: Sequence 9 equivalent (+0), and so on — see Sacrifice "
        "quality modifiers above."
    ]


def test_the_committed_boon_ritual_keeps_time_and_weight_apart():
    """Heavy is how long it takes; Moderate is what it rolls at. Collapsing
    them rolls at -4 and charges 8+ SPI for a 4-6 SPI ritual."""
    entry = json.loads((CONTENT / "boon_granting.json").read_text(encoding="utf-8"))["entries"][0]
    assert entry["complexity"] == "Heavy"
    assert entry["weight"] == "Moderate"
    assert (entry["weightModifier"], entry["spi"], entry["spiMax"]) == (-2, 4, 6)
    assert entry["skill"] == "Ritualistic Magic"
    # Both printings of the Moderate cost must agree with Chapter 7's own.
    weights = json.loads((CONTENT / "ritual_weights.json").read_text(encoding="utf-8"))["entries"]
    moderate = next(w for w in weights if w["weight"] == "Moderate")
    assert (moderate["spi"], moderate["spiMax"]) == (entry["spi"], entry["spiMax"])


def test_the_committed_boon_modifiers_are_two_groups_of_four():
    entries = json.loads((CONTENT / "boon_modifiers.json").read_text(encoding="utf-8"))["entries"]
    groups = {}
    for row in entries:
        groups.setdefault(row["group"], []).append(row)
    assert sorted(groups) == ["Ritual quality", "Sacrifice quality"]
    assert all(len(rows) == 4 for rows in groups.values())


import extract_non_standard


def test_the_non_standard_blocks_are_not_in_the_potion_file():
    """Chapter 14's pathways advance by boon. PotionDigestion walks whatever
    pathways.json holds, so one name in both files makes a boon-granted path
    drinkable -- the reason these are a separate file at all."""
    granted = json.loads(
        (CONTENT / "non_standard_pathways.json").read_text(encoding="utf-8")
    )["entries"]
    potions = json.loads((CONTENT / "pathways.json").read_text(encoding="utf-8"))["entries"]
    assert not {e["pathway"] for e in granted} & {e["pathway"] for e in potions}


def test_the_non_standard_ladder_has_the_holes_the_intro_warns_about():
    entries = json.loads(
        (CONTENT / "non_standard_pathways.json").read_text(encoding="utf-8")
    )["entries"]
    assert len(entries) == extract_non_standard.EXPECTED_BLOCKS
    with_nine = {e["pathway"] for e in entries if e["sequence"] == 9}
    gaps = {e["pathway"] for e in entries if e["sequence"] == 8} - with_nine
    assert gaps == extract_non_standard.SEQUENCE_8_WITHOUT_9


def test_both_spellings_of_one_pathway_survive_the_extraction():
    """Ch. 14 prints "Eternal Aeon (Inevitability)", ch. 15 "Eternal Aeon".
    Recorded, never repaired -- which is why the gap list above has four
    entries and only three are real gaps."""
    entries = json.loads(
        (CONTENT / "non_standard_pathways.json").read_text(encoding="utf-8")
    )["entries"]
    printed = {e["pathway"] for e in entries}
    for spelling in extract_non_standard.PATHWAY_NAME_DISAGREEMENT:
        assert spelling in printed


def test_the_three_silent_ability_sections_stay_named():
    """An " Ability" heading with nothing under it is what the BOOK prints in
    three blocks; in each the next line is a page break or the next header."""
    entries = json.loads(
        (CONTENT / "non_standard_pathways.json").read_text(encoding="utf-8")
    )["entries"]
    silent = {(e["name"], e["sequence"]) for e in entries if not e["ability"]}
    assert silent == extract_non_standard.ABILITY_MISSING


def test_a_block_that_grew_an_ability_would_fail_verification():
    entries = [
        {"name": "Villain", "sequence": 9, "pathway": "Mother Goddess of Depravity",
         "ability": "something the book does not print"},
    ]
    with pytest.raises(ValueError):
        extract_non_standard.verify(entries)


import extract_weapons


def test_the_book_does_print_a_weapon_table():
    """Four sections of the handover doc said it did not, and three features
    were deferred on that premise. It is in chapter 8, which coverage.py
    reported at 0.0% because nothing had ever read it."""
    entries = json.loads((CONTENT / "weapons.json").read_text(encoding="utf-8"))["entries"]
    assert [e["name"] for e in entries] == extract_weapons.EXPECTED_WEAPONS
    assert all(e["skill"] and e["cost"] for e in entries)


def test_a_wrapped_damage_cell_keeps_both_attacks():
    """The Combat Knife prints "sw-1 cut / thr" then "imp" on the next line.
    Reading the first mode only drops half of what it can do."""
    entries = json.loads((CONTENT / "weapons.json").read_text(encoding="utf-8"))["entries"]
    knife = next(e for e in entries if e["name"] == "Combat Knife")
    assert [m["printed"] for m in knife["damage"]] == ["sw-1 cut", "thr imp"]
    assert [m["type"] for m in knife["damage"]] == ["cut", "imp"]
    assert knife["damage"][0]["base"] == "sw" and knife["damage"][0]["modifier"] == -1


def test_only_a_firearm_carries_a_range_in_metres():
    """"C" and "C/1" are reach. Parsed as distances, Ranged.bandFor would put
    a sword one metre away and apply a range penalty to it."""
    entries = json.loads((CONTENT / "weapons.json").read_text(encoding="utf-8"))["entries"]
    revolver = next(e for e in entries if e["name"] == "Revolver (.38 cal)")
    assert (revolver["halfRange"], revolver["maxRange"]) == (150, 1800)
    for melee in ("Combat Knife", "Broadsword", "Truncheon/Baton", "Hand Axe"):
        row = next(e for e in entries if e["name"] == melee)
        assert row["halfRange"] is None and row["maxRange"] is None


def test_chapter_8_prints_a_damage_type_chapter_5_cannot_price():
    """The Bolt-Action Rifle deals "pi+"; chapter 5's wounding table never
    abbreviates its Large Piercing row. Recorded, never repaired -- adding the
    abbreviation would be editing a chapter that does not print it."""
    entries = json.loads((CONTENT / "weapons.json").read_text(encoding="utf-8"))["entries"]
    assert extract_weapons.orphan_types(entries) == extract_weapons.ORPHAN_TYPES
    rifle = next(e for e in entries if e["name"] == "Bolt-Action Rifle")
    assert rifle["damage"][0]["type"] == "pi+"
    # And the same table gives "Hunting rifles" as its Large Piercing example
    # while chapter 8 prints that gun as plain pi. Both disagreements stand.
    assert next(e for e in entries if e["name"] == "Hunting Rifle")["damage"][0]["type"] == "pi"


def test_a_reconciled_rifle_would_fail_verification():
    """If a future extraction quietly agreed the two chapters, that is a
    finding about the data, not a repair to copy forward."""
    entries = json.loads((CONTENT / "weapons.json").read_text(encoding="utf-8"))["entries"]
    patched = [
        {**e, "damage": [{**e["damage"][0], "type": "pi+"}]}
        if e["name"] == "Hunting Rifle" else e
        for e in entries
    ]
    with pytest.raises(ValueError):
        extract_weapons.verify(patched)


def test_the_shotgun_note_attaches_to_the_shotgun():
    entries = json.loads((CONTENT / "weapons.json").read_text(encoding="utf-8"))["entries"]
    shotgun = next(e for e in entries if e["name"] == "Double-Barrel Shotgun")
    assert "pellets" in shotgun["notes"]
    assert all(not e["notes"] for e in entries if e["name"] != "Double-Barrel Shotgun")


def test_the_longer_aim_adds_two_on_top_of_the_weapons_acc():
    """"+ weapon's Acc +2 (max Acc +2 total)". Read as the modifier itself,
    +2 would make the longer aim WORSE than the short one for every weapon
    with Acc 3 or better."""
    entries = json.loads(
        (CONTENT / "ranged_modifiers.json").read_text(encoding="utf-8")
    )["entries"]
    aim = [e for e in entries if e.get("weaponAcc")]
    assert len(aim) == 2
    assert sorted(e.get("accBonus", 0) for e in aim) == [0, 2]
    assert all("modifier" not in e for e in aim)


import extract_wealth


def wealth_entries():
    return json.loads((CONTENT / "wealth.json").read_text(encoding="utf-8"))["entries"]


def wealth_note():
    _, note = extract_wealth.split_note(
        extract_wealth.stream(extract_wealth.load_lines(), *extract_wealth.WEALTH_RANGE)
    )
    return note


def test_the_wealth_ladder_climbs_on_every_column():
    entries = wealth_entries()
    assert [e["status"] for e in entries] == extract_wealth.EXPECTED_STATUSES
    assert [e["points"] for e in entries] == [-25, -15, -10, 0, 5, 15, 25]
    assert [e["startingMoney"] for e in entries] == [
        "£0", "5 soli", "15 soli", "£1", "£5", "£50", "£100",
    ]
    assert all(e["lifestyle"] for e in entries)


def test_the_two_kinds_of_minus_are_both_read():
    """The point costs are ASCII-signed ("-25 pts") and the reaction column is
    U+2212 (" -2"). A parser that knows only one silently drops half the
    negative numbers on this page."""
    broke = next(e for e in wealth_entries() if e["status"] == "Dead Broke")
    assert broke["points"] == -25 and broke["reactionModifier"] == -2


def test_the_reaction_ladder_is_not_symmetric_around_average():
    """The paragraph says "+1 per Status level above Average ... and -1 per
    level below". Above Average the column agrees exactly. Below it, it does
    not: Struggling is one level down and prints 0, and the paragraph's own
    example puts Poor -- two levels down -- at -1."""
    by_status = {e["status"]: e for e in wealth_entries()}
    assert [by_status[s]["reactionModifier"] for s in
            ("Comfortable", "Wealthy", "Very Wealthy")] == [1, 2, 3]
    assert by_status["Struggling"]["reactionModifier"] == 0
    assert by_status["Poor"]["reactionModifier"] == -1


@needs_pdf
def test_a_straightened_ladder_would_fail_verification():
    """Giving Struggling the -1 the paragraph implies is a repair, not a fix.
    The printed column is the authority (CLAUDE.md 4.1)."""
    patched = [
        {**e, "reactionModifier": -1} if e["status"] == "Struggling" else e
        for e in wealth_entries()
    ]
    with pytest.raises(ValueError):
        extract_wealth.verify(patched, wealth_note())


def test_three_levels_are_printed_only_in_chapter_8():
    """Chapter 3's advantage and disadvantage lists carry four wealth levels.
    Average, Wealthy and Very Wealthy are not among them, so nothing could
    give a character the two levels above Comfortable until this table."""
    shared = extract_wealth.chapter_3_levels()
    assert shared == extract_wealth.CHAPTER_3_LEVELS
    only_here = {e["status"] for e in wealth_entries()} - set(shared)
    assert only_here == extract_wealth.ONLY_IN_CHAPTER_8


def test_the_two_chapters_agree_where_they_overlap():
    """Both price Poor at 15 points of disadvantage. If they ever stopped
    agreeing that would be a finding about the book, not a column to pick."""
    by_status = {e["status"]: e["points"] for e in wealth_entries()}
    for status, points in extract_wealth.CHAPTER_3_LEVELS.items():
        assert by_status[status] == points


@needs_pdf
def test_a_chapter_that_repriced_a_shared_level_would_fail_verification():
    patched = [
        {**e, "points": -20} if e["status"] == "Poor" else e for e in wealth_entries()
    ]
    with pytest.raises(ValueError):
        extract_wealth.verify(patched, wealth_note())


import extract_appendix


def appendix_entries():
    return json.loads(
        (CONTENT / "damage_abbreviations.json").read_text(encoding="utf-8")
    )["entries"]


def test_the_appendix_prints_the_abbreviation_chapter_5_omits():
    """Four doc sections, and two tests, recorded "this book prints no
    abbreviation for pi+". Appendix A prints it, at 0.0% coverage — the same
    way chapter 8's weapon table stayed invisible for four milestones."""
    by_type = {e["type"]: e for e in appendix_entries()}
    assert by_type["Large Piercing"]["abbreviation"] == "pi+"
    assert by_type["Large Piercing"]["multiplier"] == 1.5
    assert by_type["Piercing (small)"]["abbreviation"] == "pi (small)"


def test_the_appendix_adds_a_label_and_never_a_number():
    """The basis for adopting the label: every multiplier it repeats already
    matches chapter 5, so nothing new is claimed about the book."""
    chapter5 = extract_appendix.chapter_5_types()
    for row in appendix_entries():
        assert chapter5[row["type"]][0] == row["multiplier"]


def test_only_the_piercing_group_is_read_as_abbreviations():
    """The appendix writes "Crush" where chapter 5 abbreviates "cr", so its
    other cells are short names. Adopting them would give one damage type two
    spellings. "Pi" matching chapter 5's own "pi" is the whole evidence that
    this group is different."""
    labelled = {e["printedAs"] for e in appendix_entries() if e.get("abbreviation")}
    assert labelled == extract_appendix.ABBREVIATED_GROUP
    unlabelled = {e["printedAs"] for e in appendix_entries() if not e.get("abbreviation")}
    assert unlabelled == {"Crush", "Cut", "Impale", "Burn", "Toxic"}


def test_a_disagreeing_multiplier_would_fail_verification():
    """If the appendix ever priced a row differently from chapter 5, that is a
    finding about the book, not a column to prefer."""
    patched = [
        {**e, "multiplier": 2.5} if e["type"] == "Large Piercing" else e
        for e in appendix_entries()
    ]
    with pytest.raises(ValueError):
        extract_appendix.verify(patched)


def test_losing_the_matching_pi_would_fail_verification():
    """"Pi" agreeing with chapter 5 is the only evidence these are labels and
    not short names. Without it the group has no standing."""
    patched = [e for e in appendix_entries() if e["type"] != "Piercing"]
    with pytest.raises(ValueError):
        extract_appendix.verify(patched)


import extract_appearance


def appearance_entries():
    return json.loads((CONTENT / "appearance.json").read_text(encoding="utf-8"))["entries"]


def appearance_note():
    _, note = extract_appearance.stream(
        extract_appearance.load_lines(), *extract_appearance.APPEARANCE_RANGE
    )
    return note


def test_the_appearance_ladder_runs_best_to_worst():
    entries = appearance_entries()
    assert [e["level"] for e in entries] == extract_appearance.EXPECTED_LEVELS
    assert [e["points"] for e in entries] == [1, 4, 8, -4, -8, -16, -20, -24]
    assert [e["reactionModifier"] for e in entries] == [1, 2, 3, -1, -2, -4, -5, -6]


def test_the_reaction_column_is_not_derivable_from_the_cost():
    """If it were, the second column would be redundant and reading it would be
    arithmetic rather than extraction. It is not: the good half doubles its
    cost per step while its modifier climbs by one, and the bad half skips -12
    entirely, so -8 pts gives -2 and -16 gives -4."""
    by_level = {e["level"]: e for e in appearance_entries()}
    assert (by_level["Attractive"]["points"], by_level["Attractive"]["reactionModifier"]) == (1, 1)
    assert (by_level["Ugly"]["points"], by_level["Ugly"]["reactionModifier"]) == (-8, -2)
    assert (by_level["Hideous"]["points"], by_level["Hideous"]["reactionModifier"]) == (-16, -4)
    assert not any(-12 == e["points"] for e in appearance_entries())


def test_chapter_3_prints_none_of_these_levels():
    """Starting Wealth was HALF printed in chapter 3's trait lists, which is
    what made "Wealth: Poor" the spelling to match on. Appearance is printed
    there zero times, so the trait carries the level's own name and no prefix.
    The one row whose name says "Appearance" is a potion's doing."""
    named = extract_appearance.appearance_named_traits()
    assert named == [extract_appearance.NOT_AN_APPEARANCE_LEVEL]
    assert extract_appearance.NOT_AN_APPEARANCE_LEVEL not in {
        e["level"] for e in appearance_entries()
    }


def test_no_level_is_free_so_ordinary_looks_are_not_a_row():
    """Wealth prints Average at 0 pts. This table's cheapest row is Attractive
    at 1 point, so ordinary looks are the absence of a trait, not a level."""
    assert all(e["points"] != 0 for e in appearance_entries())


@needs_pdf
def test_a_level_that_crossed_the_two_columns_would_fail_verification():
    """A level that costs points and does not help -- or refunds them and does
    not hurt -- means a row was read out of order."""
    patched = [
        {**e, "reactionModifier": -1} if e["level"] == "Attractive" else e
        for e in appearance_entries()
    ]
    with pytest.raises(ValueError):
        extract_appearance.verify(patched, appearance_note())


@needs_pdf
def test_folding_in_unsettling_appearance_would_fail_verification():
    """4 pts, -1 to all reaction rolls, and "a common consequence of Abyss and
    Chained Pathway potions" -- suffered in play, not chosen at creation.
    Pricing it as this table's ninth row would sell a disfigurement."""
    patched = appearance_entries() + [
        {"level": extract_appearance.NOT_AN_APPEARANCE_LEVEL,
         "points": -4, "reactionModifier": -1}
    ]
    with pytest.raises(ValueError):
        extract_appearance.verify(patched, appearance_note())


@needs_pdf
def test_the_note_disclaiming_the_gurps_costs_must_survive():
    """"Appearance point costs have been adjusted for the Pathways setting and
    differ from standard GURPS values." Every number here is this book's, so a
    reviewer reaching for GURPS 4e (Attractive at 4 pts, not 1) would be
    correcting the book with its own source material."""
    assert extract_appearance.NOTE_PHRASE in appearance_note()
    with pytest.raises(ValueError):
        extract_appearance.verify(appearance_entries(), "some other sentence")


import extract_languages


def language_entries():
    return json.loads((CONTENT / "languages.json").read_text(encoding="utf-8"))["entries"]


def proficiency_entries():
    return json.loads(
        (CONTENT / "language_proficiency.json").read_text(encoding="utf-8")
    )["entries"]


def language_notes():
    return [
        e["text"] for e in
        json.loads((CONTENT / "language_notes.json").read_text(encoding="utf-8"))["entries"]
    ]


def test_twelve_languages_in_two_lists():
    entries = language_entries()
    assert [e["name"] for e in entries if e["type"] == "Common"] == extract_languages.EXPECTED_COMMON
    assert [e["name"] for e in entries if e["type"] == "Mystical"] == extract_languages.EXPECTED_MYSTICAL


def test_the_same_three_rows_are_priced_twice():
    """Native / Accented / Broken appear under both tables at 6/4/2 and
    30/20/10. Nothing inside a row says which table it came from -- only the
    header above it does -- so a parser keyed on the name alone prices a
    mystical Native at a fifth of the book's cost."""
    by_key = {(e["type"], e["proficiency"]): e["points"] for e in proficiency_entries()}
    assert [by_key[("Common", p)] for p in extract_languages.EXPECTED_PROFICIENCIES] == [6, 4, 2]
    assert [by_key[("Mystical", p)] for p in extract_languages.EXPECTED_PROFICIENCIES] == [30, 20, 10]


@needs_pdf
def test_a_merged_proficiency_table_would_fail_verification():
    patched = [
        {**e, "points": 6} if e["type"] == "Mystical" and e["proficiency"] == "Native" else e
        for e in proficiency_entries()
    ]
    with pytest.raises(ValueError):
        extract_languages.verify(language_entries(), patched, language_notes())


def test_only_the_mystical_table_speaks_about_rituals():
    """A common row carries neither ritual field. Saying nothing is not the
    same as permitting a ritual at no penalty -- the distinction the ritual
    critical table already draws about corruption."""
    for row in proficiency_entries():
        assert ("canPerformRituals" in row) == (row["type"] == "Mystical")
    mystical = {e["proficiency"]: e for e in proficiency_entries() if e["type"] == "Mystical"}
    assert mystical["Native"]["ritualModifier"] == 0
    assert mystical["Accented"]["ritualModifier"] == -2
    assert mystical["Broken"]["canPerformRituals"] is False
    assert "ritualModifier" not in mystical["Broken"]


def test_the_accented_row_names_a_penalty_without_granting_permission():
    """"Functional but imperfect. -2 to language-dependent skill rolls.
    Rituals using the language at -2." It never says the caster MAY cast, so
    requiring those words as well made this row unreadable -- printing a
    ritual modifier is the permission."""
    accented = next(
        e for e in proficiency_entries()
        if e["type"] == "Mystical" and e["proficiency"] == "Accented"
    )
    assert "Can perform rituals" not in accented["effect"]
    assert accented["canPerformRituals"] is True


def test_a_seven_character_wrap_is_not_a_thirteenth_language():
    """Balam's note wraps onto "similar" -- short enough to look exactly like
    a language name to the short-line rule that cuts these rows. It has to
    land inside the note it belongs to."""
    balam = next(e for e in language_entries() if e["name"] == "Balam (Dutan)")
    assert balam["notes"].endswith("structurally similar")
    assert all(e["name"] != "similar" for e in language_entries())


def test_the_intro_rule_survives_the_page_break():
    """"Spoken-only or written-only costs" ends one line and "half." starts
    the next, with a page footer between them. Matched per line, the rule
    simply is not there."""
    assert any(extract_languages.SPOKEN_WRITTEN_PHRASE in n for n in language_notes())
    assert any(extract_languages.HALVING_PHRASE in n for n in language_notes())


def test_the_footnote_is_not_part_of_the_broken_row():
    """Left in the stream it became the tail of Broken's effect cell, so the
    app would have quoted a trait footnote as a rule about speaking badly.
    It is found by its own shape: it prices traits inline, "(10 pts)"."""
    broken = next(
        e for e in proficiency_entries()
        if e["type"] == "Mystical" and e["proficiency"] == "Broken"
    )
    assert "Language Talent" not in broken["effect"]
    assert broken["effect"].endswith("Cannot perform rituals in the language.")
    assert any("Language Talent (10 pts)" in n for n in language_notes())


def test_chapter_3_points_at_this_section_and_then_disagrees_with_it():
    """Unlike Appearance, whose levels the trait lists print zero times, this
    section is cross-referenced by "Language (specify)". Two neighbouring rows
    then contradict it, and both are recorded rather than repaired."""
    pointer = extract_languages.trait_named(extract_languages.CROSS_REFERENCE)
    assert "Languages section" in pointer["notes"]

    # The section says "two COMMON languages ... does not apply to mystical";
    # the trait row says "two additional languages" and drops the restriction.
    assert "common" not in extract_languages.trait_named("Multilingual")["notes"]
    # And this row names a language neither table prints.
    dead = extract_languages.trait_named(extract_languages.DEAD_LANGUAGE_TRAIT)
    assert extract_languages.UNPRINTED_LANGUAGE in dead["notes"]
    assert all(
        e["name"] != extract_languages.UNPRINTED_LANGUAGE for e in language_entries()
    )


@needs_pdf
def test_a_repaired_multilingual_row_would_fail_verification():
    """If someone "fixed" the trait row to agree with the section, the
    disagreement would vanish silently. verify() fails instead."""
    original = extract_languages.trait_named
    try:
        extract_languages.trait_named = lambda name: (
            {"name": name, "notes": "Fluent in two additional common languages."}
            if name == "Multilingual" else original(name)
        )
        with pytest.raises(ValueError):
            extract_languages.verify(
                language_entries(), proficiency_entries(), language_notes()
            )
    finally:
        extract_languages.trait_named = original


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
