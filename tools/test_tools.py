import json, re, subprocess, sys, pathlib

import pytest

import extract_glossary
import extract_spirit_vision
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
    # The seventh field of that block is NOT None, and this enumeration used
    # to stop one short of it. The sheet really does print "Unspent" with 0
    # beside it (Beat Cop.xlsx, row 1, two cells right of the label) while
    # printing no "Budget (BP)" and no "Total spent" at all -- which is why
    # `unspent` is read by value_right_of and the other two by points_after.
    # GoldenSheetTest excludes it from the surplus reconciliation for exactly
    # this reason, so the two suites now agree about the same odd sheet.
    assert f["published"]["unspent"] == 0

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


# --- Skill categories (Ch. 3's Complete Skill List banners) ----------------

import extract_content


def test_every_skill_carries_the_category_banner_it_was_printed_under():
    """The Complete Skill List is printed under fifteen category banners, and
    the extractor discarded them for four milestones -- so a trait cell saying
    "+3 to social skill rolls" named a group the book prints and the app could
    not identify. The "a normaliser can delete a rule" shape that
    strip_self_control_number already had once.

    The banners PARTITION the list: every skill sits under exactly one, so a
    missing category is a parse failure rather than a row to default."""
    skills = json.loads((CONTENT / "skills.json").read_text(encoding="utf-8"))["entries"]
    assert len(skills) == 158
    missing = [e["name"] for e in skills if not e.get("category")]
    assert missing == [], f"skills with no category banner: {missing}"
    assert {e["category"] for e in skills} == extract_content.SKILL_CATEGORIES


def test_the_social_banner_holds_its_sixteen_printed_members():
    """The membership is the whole join: "social skill rolls" is applied to
    exactly the skills under this banner, so a row leaving the set silently
    withdraws a printed bonus and a row joining it silently invents one.

    **Streetwise is under KNOWLEDGE**, which is not incidental -- it is the
    evidence that Charisma's bracketed list ("... Sex Appeal, Streetwise") is
    its own enumeration and NOT a reference to this banner (6bs). If the book
    ever moved it here, that reading would have to be made again."""
    skills = json.loads((CONTENT / "skills.json").read_text(encoding="utf-8"))["entries"]
    by_category = {}
    for e in skills:
        by_category.setdefault(e["category"], set()).add(e["name"])
    assert by_category[extract_content.SOCIAL_SKILLS] == {
        "Fast-Talk", "Intimidation", "Diplomacy", "Public Speaking", "Acting",
        "Leadership", "Detect Lies", "Interrogation", "Observation", "Psychology",
        "Savoir-Faire", "Sex Appeal", "Panhandling", "Disguise", "Forgery", "Holdout",
    }
    assert "Streetwise" in by_category["KNOWLEDGE SKILLS"]


def test_the_book_distinguishes_a_social_skill_roll_from_a_reaction_roll():
    """The warrant for applying "social skill rolls" to the SOCIAL banner, and
    for refusing the bare phrase "social rolls".

    Line 6522 prints both in ONE bracket -- "(-1 to all emotional reaction
    rolls and social skill rolls)" -- so they are two different things in the
    book's own sentence. "social rolls" is never defined, never a banner, and
    the rows using it read like reactions ("first-impression", "people look out
    for you"), while this book prints no reaction roll procedure at all. So
    those rows are refused rather than read as this banner.

    If that bracket ever loses one of its two phrases, the split has to be
    argued again rather than inherited."""
    lines = json.loads((OUT / "lines.json").read_text(encoding="utf-8"))
    text = [(l["text"] if isinstance(l, dict) else l) for l in lines]
    bracket = text[6521]
    assert "reaction rolls" in bracket and "social skill rolls" in bracket, bracket
    # And the banner is the only place the book GROUPS skills as social. The
    # phrase occurs three times in all: line 155 is descriptive prose about
    # what IQ covers, 787 is a language clause, and 1728 is the banner -- so
    # nothing else could be the referent of "social skill rolls".
    grouped = [i + 1 for i, t in enumerate(text) if "social skills" in t.lower()]
    assert grouped == [155, 787, 1728], grouped
    assert text[1727].strip() == "— SOCIAL SKILLS —"


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
    for phrase in extract_defenses.PINNED:
        # The rule ActiveDefenses.acrobaticDodge now applies, both signs of it.
        assert phrase in " ".join(e["modifier"] for e in entries), phrase
    # The bullet glyph itself must not reach the app.
    assert all(not e["modifier"].startswith("") for e in entries)


def test_a_bullet_that_loses_its_sign_fails_verify():
    """The guard must fail on the mutation it exists for: a sign flip inside
    the cell, not merely a bullet going missing. A check on "2 Dodge" would
    pass this flipped text, because it is a substring of "-2 Dodge"."""
    flipped = [{"modifier": "[Free Action] [Acrobatic Dodge] Roll vs. Acrobatics: "
                            "success gives -2 Dodge; failure gives +2 Dodge."}] * 5
    defenses = json.loads((CONTENT / "defenses.json").read_text(encoding="utf-8"))["entries"]
    with pytest.raises(ValueError, match="the rule the app applies is gone"):
        extract_defenses.verify(defenses, flipped)


# --- Refusals of an active defence (Ch. 5, p. 36-40) -----------------------

import extract_defense_refusals as edr


def test_a_refusal_wrapping_across_lines_is_joined_into_one_sentence():
    """UNAWARE_RANGED wraps across three lines and a page break. Read line by
    line the rule is three fragments, none of which states it."""
    byn = {
        10: "Dodging ranged attacks: You may Dodge normally. If you did not",
        11: "Page 40",
        12: "know the shot was coming, you cannot defend at all — the bullet",
        13: "arrives before you can react.",
    }
    got = edr.sentence(byn, 10, 13)
    assert "Page 40" not in got
    assert "you cannot defend at all" in got


def test_a_refusal_that_prints_a_modifier_is_a_mis_cut_sentence():
    """None of the three prints a number to apply instead -- they withdraw the
    defence outright. A signed number means the span caught a neighbouring
    rule, which for two of these is a defence MODIFIER sitting lines away."""
    bad = [{"cause": c, "printed": edr.PINNED[c]} for c in edr.CAUSES]
    bad[0]["printed"] = "Surprised characters defend at -4 and cannot defend on it."
    with pytest.raises(ValueError, match="prints a modifier"):
        edr.verify(bad, [])


def test_charge_attack_forfeits_its_defence_too():
    """p. 37 excepts "an All-Out Attack maneuver"; the maneuver table prints
    "Cannot defend next turn." on Charge Attack as well. Dropping it would
    hand a charging fighter a defence the table denies."""
    entries = [{"cause": c, "printed": edr.PINNED[c]} for c in edr.CAUSES]
    three = [
        {"maneuver": n, "description": edr.FORFEIT_PHRASE}
        for n in sorted(edr.FORFEIT_ROWS - {"Charge Attack"})
    ]
    with pytest.raises(ValueError, match="forfeit a defence changed"):
        edr.verify(entries, three)


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


def test_strong_blows_scope_sentence_is_pinned_whole():
    """Both halves of "Regular melee attacks only" are a reading, and the +2
    is a Kotlin constant. Either drifting has to fail here."""
    entries = json.loads((CONTENT / "extra_effort.json").read_text(encoding="utf-8"))["entries"]
    by_option = {e["option"]: e["effect"] for e in entries}
    assert by_option["Strong Blow"] == extract_combat.PINNED_EFFORT["Strong Blow"]
    # The maneuver row the word "Regular" points at is still printed.
    maneuvers = json.loads((CONTENT / "maneuvers.json").read_text(encoding="utf-8"))["entries"]
    assert "Regular Attack" in [e["maneuver"] for e in maneuvers]
    options = json.loads((CONTENT / "attack_options.json").read_text(encoding="utf-8"))["entries"]
    # Dropping half the scope, and ADDING a clause to it, both have to fail:
    # an appended sentence leaves the old one intact while making the Kotlin
    # scope stale, so a substring check would wave it through (Codex).
    for effect in ("+2 damage.", by_option["Strong Blow"] + " Cannot combine with Rapid Strike."):
        drifted = [dict(e, effect=effect) if e["option"] == "Strong Blow" else e for e in entries]
        with pytest.raises(ValueError):
            extract_combat.verify(maneuvers, drifted, options)


def test_the_stun_penalty_is_pinned_whole_and_corroborated():
    """The -4 is a Kotlin constant and the cell is quoted verbatim on screen.

    Its warrant is a SECOND printing outside chapter 5, which is what makes the
    penalty a property of being stunned rather than of the Do Nothing row it is
    printed on -- and which says "active defenses" plural."""
    maneuvers = json.loads((CONTENT / "maneuvers.json").read_text(encoding="utf-8"))["entries"]
    by_name = {e["maneuver"]: e["description"] for e in maneuvers}
    assert by_name["Do Nothing"] == extract_combat.PINNED_MANEUVERS_EXACT["Do Nothing"]

    effort = json.loads((CONTENT / "extra_effort.json").read_text(encoding="utf-8"))["entries"]
    options = json.loads((CONTENT / "attack_options.json").read_text(encoding="utf-8"))["entries"]
    # Dropping the second clause and appending a third both have to fail: the
    # screen quotes this cell, so either changes what a GM is shown.
    for text in ("If Stunned: -4 to Active Defense.", by_name["Do Nothing"] + " Roll HT to recover."):
        drifted = [dict(e, description=text) if e["maneuver"] == "Do Nothing" else e for e in maneuvers]
        with pytest.raises(ValueError):
            extract_combat.verify(drifted, effort, options)

    byn = extract_combat.load_lines()
    assert extract_combat.stun_corroboration(byn), "nothing outside ch. 5 prints the -4"
    with pytest.raises(ValueError):
        extract_combat.verify(maneuvers, effort, options, {1: "no such rule here"})


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


def test_the_grappling_table_is_cut_on_its_own_roll_column():
    """Maneuver / Roll / Opposed Roll / Notes -- four columns, and the roll
    names a DIFFERENT skill on the last row, so the anchor cannot be one
    skill's name the way the two damage tables' is."""
    rows = extract_unarmed.parse_grappling([
        "Grapple", "Wrestling", "DX or Escape", "Initiate grapple; both roll DX",
        "Takedown", "Wrestling-1", "DX or Wrestling", "Knock down;",
        "target falls prone",
        "Escape", "Escape-2", "DX or Wrestling", "Break free from grapple or pin",
    ], damage="None (grapple only)")
    assert [(r["skill"], r["attack"], r["rollModifier"]) for r in rows] == [
        ("Wrestling", "Grapple", 0),
        ("Wrestling", "Takedown", -1),
        ("Escape", "Escape", -2),
    ]
    assert rows[0]["opposedRoll"] == "DX or Escape"
    assert rows[1]["notes"] == "Knock down; target falls prone"


def test_the_grappling_rows_carry_no_damage_parts():
    """The Wrestling table prints no Damage column at all. Its rows take the
    summary table's own cell for that skill and no parts -- Shove's shape,
    which is already in this file and is what disproves "no damage, no row"."""
    rows = extract_unarmed.parse_grappling(
        ["Pin", "Wrestling-2", "DX or Wrestling", "Immobilize; target cannot act"],
        damage="None (grapple only)",
    )
    assert "base" not in rows[0]
    assert rows[0]["damage"] == "None (grapple only)"


def test_only_the_grapplers_maneuvers_are_offered_as_attacks():
    """Three of the four rows are the GRAPPLER's; Escape-2 is rolled by the
    person being held. This table is keyed by the acting character's skill and
    consumed by an attack picker, so storing Escape here would offer a
    character with that skill an escape from a grapple they are not in. A gap,
    recorded -- not a refusal."""
    entries = json.loads(
        (CONTENT / "unarmed_attacks.json").read_text(encoding="utf-8"))["entries"]
    assert [e["attack"] for e in entries if e["skill"] == "Wrestling"] == [
        "Grapple", "Takedown", "Pin",
    ]
    assert not any(e["skill"] == "Escape" for e in entries)
    assert extract_unarmed.GRAPPLED_ROW == "Escape"


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


def test_the_cross_skill_default_is_read_across_the_page_break_it_wraps_on():
    """"...can attempt the other" ends one line and "at -2." begins the next.

    Neither line alone states the rule -- the same wrap that hid the Flourish
    Bonus one chapter over (6ay).
    """
    penalty, threshold, gated, joined = extract_divination.parse_cross_default([
        "Default: Divination Arts defaults to Ritualistic Magic-2 and vice "
        "versa. A character with one skill at 12+ can attempt the other",
        "at -2.",
    ])
    assert (penalty, threshold, gated) == (-2, 12, -2)
    assert "Default:" in joined

    # A seam with the name and no number reads exactly like no rule at all.
    with pytest.raises(ValueError, match="threshold clause is gone"):
        extract_divination.parse_cross_default([
            "Default: Divination Arts defaults to Ritualistic Magic-2 and vice versa."
        ])


OVERLAP_LINES = [
    "The two skills overlap at the following boundaries:",
    "\x7f Simple divination (yes/no, vague direction, basic insight): "
    "Divination Arts is sufficient. Cost: 1–2 SPI.",
    "\x7f Moderate divination (find a person, glimpse a scene, interpret a "
    "dream): Either skill works. Divination Arts is",
    "faster (minutes vs. ritual setup) but may receive less detail. Cost: 2 SPI.",
    "\x7f Complex divination (long-range, multiple questions, resisting "
    "countermeasures): Requires Ritualistic Magic or a",
    "very high Divination Arts roll (-4 penalty). Cost: 2 SPI.",
]


def test_the_skill_boundary_table_parses_three_scopes():
    """Section I's own table: which skill each scope of question needs.

    Two of the three rows WRAP, and the bullet glyph is the only thing that
    marks where a row starts -- a line-at-a-time read gives Moderate no cost
    and Complex no penalty, which is what a missing rule looks like.
    """
    rows = extract_divination.parse_scope(OVERLAP_LINES)
    assert [r["scope"] for r in rows] == ["Simple", "Moderate", "Complex"]
    assert rows[0]["examples"] == "yes/no, vague direction, basic insight"
    assert rows[0]["guidance"] == "Divination Arts is sufficient."
    assert rows[0]["printedCost"] == "1–2 SPI"
    # The wrapped row keeps both halves of its sentence and its cost.
    assert rows[1]["guidance"].endswith("may receive less detail.")
    assert rows[1]["printedCost"] == "2 SPI"
    # Only the third row prices anything, and it prices the SUBSTITUTION.
    assert [r.get("modifier") for r in rows] == [None, None, -4]
    assert rows[2]["printedModifier"] == "-4"


def test_the_complex_scope_is_not_the_hard_band_restated():
    """The evidence the same-axis refusal died on, guarded.

    That refusal read the bullet's -4 as the Hard band restated, on the only
    evidence that the two numbers agree. They still agree; what disproves it
    is the WORDS. The Hard band's own descriptors are "well-guarded, future,
    abstract" and none of them is in the complex bullet, whose "long-range"
    belongs to the MODERATE band's "distant" at -2 and whose "multiple
    questions" is in no band at all. If a re-extraction ever puts a Hard
    descriptor into this bullet the two really would be one axis, and the
    reading has to be made again rather than inherited.
    """
    rows = extract_divination.parse_scope(OVERLAP_LINES)
    difficulty = [
        {"band": "Trivial — personal, present, simple", "modifier": 2},
        {"band": "Easy — personal, recent, concrete", "modifier": 0},
        {"band": "Moderate — impersonal, distant, vague", "modifier": -2},
        {"band": "Hard — well-guarded, future, abstract", "modifier": -4},
        {"band": "Extreme — divine, fate-bound, cosmic", "modifier": -6},
    ]
    extract_divination.verify_scope(rows, difficulty)

    shared = [dict(r) for r in rows]
    shared[2]["examples"] = "long-range, future, abstract"
    with pytest.raises(ValueError, match="one axis"):
        extract_divination.verify_scope(shared, difficulty)

    # And the rule is only applied while the bullet still prints its number.
    unpriced = [dict(r) for r in rows]
    unpriced[2].pop("modifier")
    with pytest.raises(ValueError, match="no longer prints its penalty"):
        extract_divination.verify_scope(unpriced, difficulty)


def test_committed_divination_scope_claims_the_boundary_table():
    """Six lines that no content file claimed, bracketed on both sides by
    claims -- divination_support.json takes 3559-3560 and 3563-3580."""
    body = json.loads(
        (CONTENT / "divination_scope.json").read_text(encoding="utf-8"))
    assert body["sourceLines"]["6.5"] == [[3553, 3558]]
    assert [e["scope"] for e in body["entries"]] == ["Simple", "Moderate", "Complex"]


def test_the_vice_versa_half_is_refused_from_the_other_chapter():
    """This sentence says the default runs both ways; ch. 7 II p. 61 says
    Ritualistic Magic "has no default and cannot be substituted".

    Pinned from BOTH sides on purpose. The limit lives in a chapter this
    extractor never reads, which is exactly how it stayed unread while 6az
    wired the default symmetrically -- and a reader who re-symmetrises
    Divination.divinationLevelFor will be looking at this file, not that one.
    """
    seam = ("Default: Divination Arts defaults to Ritualistic Magic-2 and vice "
            "versa. A character with one skill at 12+ can attempt the other at -2.")
    extract_divination.verify_cross_default(
        -2, 12, -2, seam,
        "Ritualistic Magic (IQ/Very Hard) - your core skill. "
        "It has no default and cannot be substituted.",
    )
    with pytest.raises(ValueError, match="no longer prints its prohibition"):
        extract_divination.verify_cross_default(
            -2, 12, -2, seam,
            "Ritualistic Magic (IQ/Very Hard) - your core skill.",
        )


def test_committed_divination_content_claims_the_cross_default_sentence():
    """The -2 and the 12 live in Kotlin; the span is claimed so the rule
    stays findable (6af)."""
    body = (CONTENT / "divination_support.json").read_text(encoding="utf-8")
    assert [3559, 3560] in json.loads(body)["sourceLines"]["6.5"]
    assert "default" not in body.lower()


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


import extract_core_rules


def core_entries(name):
    return json.loads((CONTENT / f"{name}.json").read_text(encoding="utf-8"))["entries"]


def test_chapter_two_prints_two_burnable_pools():
    """One capped, one explicitly uncapped."""
    fp, spi = core_entries("effort_burn")
    assert (fp["pool"], fp["kind"], fp["max"]) == ("FP", "physical", 5)
    assert (spi["pool"], spi["kind"]) == ("SPI", "mystical")
    # Absent, not a large number: "There is no cap". A ceiling here would be
    # one this app invented, the distinction RitualCritical.corGained draws
    # between printing nothing and printing zero.
    assert "max" not in spi
    assert fp["perPoint"] == spi["perPoint"] == 1


def test_both_burn_scopes_survive_their_wrap():
    """Both scope cells wrap mid-bracket, and one wraps inside "vs.".

    A truncated cell still parses and still reads plausibly -- "(Ritualistic
    Magic, Spiritual" -- so the closing bracket is what proves the example
    list survived. Splitting on ". " instead cut SPI's list at "Occultism vs.",
    the same trap extract_fright.py had to be taught.
    """
    for row in core_entries("effort_burn"):
        assert row["scope"].count("(") == row["scope"].count(")"), row["scope"]
        assert row["scope"].endswith(")"), row["scope"]
    _, spi = core_entries("effort_burn")
    assert "Occultism vs. Beyonder phenomena" in spi["scope"]


def test_chapter_two_tabulates_the_margin_bands_appendix_a_names():
    """The correction to 6ad.

    That section said chapter 2 "tabulates nothing". It tabulates the same
    three edges -- 0-2, 3-4, 5+ -- under different names. The bulleted list was
    invisible because this PDF prints list bullets as U+007F. Only Appendix A
    NAMES the bands, which is what makes it a definition rather than a
    summary, and that asymmetry is asserted here so a later reading cannot
    quietly erase it.
    """
    success = [b for b in core_entries("core_mechanic") if b["outcome"] == "Success"]
    assert [b["low"] for b in success] == [0, 3, 5]
    assert [b.get("high") for b in success] == [2, 4, None]
    ladder = core_entries("success_ladder")
    edges = sorted({-(e.get("offset") or 0) for e in ladder if e.get("comparison") == "<="})
    assert edges == [0, 3, 5]
    named = {e["result"] for e in ladder}
    assert {"Good Success", "Excellent Success"} <= named
    assert not any("Good Success" in b["meaning"] for b in success)


def test_very_hard_prints_a_refusal_and_not_a_number():
    """"No default -- cannot attempt untrained" is not a large penalty."""
    defs = core_entries("skill_defaults")
    assert [d["code"] for d in defs] == ["E", "A", "H", "VH"]
    assert [d.get("penalty") for d in defs] == [-4, -5, -6, None]
    assert "penalty" not in defs[-1]


def test_the_six_quick_contests_survive_their_trailing_paragraph():
    """The table ends where the resolution paragraph begins, by MARKER.

    A wrapped sentence -- "...if the situation" / "allows. If one succeeds..."
    -- reads as two more cells to anything that counts rows instead.
    """
    rows = core_entries("quick_contests")
    assert len(rows) == 6
    assert rows[0]["situation"] == "Deception vs. detection"
    assert any(r["attacker"] == "Ritualistic Magic" for r in rows)
    assert not any("allows." in r["situation"] for r in rows)


@needs_pdf
def test_the_core_rules_verify_against_the_book():
    bands, pools, rows, defs, secs = extract_core_rules.extract()
    extract_core_rules.verify(
        bands, pools, rows, defs, secs, extract_core_rules.load_lines())


@needs_pdf
def test_a_capped_spi_burn_is_refused():
    """The SPI row says "There is no cap"; growing one is a finding."""
    bands, pools, rows, defs, secs = extract_core_rules.extract()
    pools = [dict(p) for p in pools]
    pools[1]["max"] = 5
    with pytest.raises(AssertionError):
        extract_core_rules.verify(
            bands, pools, rows, defs, secs, extract_core_rules.load_lines())


# --- The Seven Orthodox Gods Blessings (Ch. 3, p. 19) ----------------------

import extract_blessings as ebl


def _blessing_lines():
    """The table's row range, as the book prints it."""
    return {
        1103: "Goddess of Evernight",
        1104: "Dream Lucidity, Concealment. Most divination fails against you.",
        1105: "God of Combat",
        1106: "Herculean strength & catlike reflexes. +3 ST, +3 DX, +3 HT.",
    }


def test_a_wrapped_effect_cell_stays_with_its_deity():
    """A deity name carries no full stop and an effect cell ends with one, so
    a continuation line joins the row above rather than opening a new one.
    Counting two lines per row works today and breaks on the first wrap."""
    byn = dict(_blessing_lines())
    byn[1106] = "Herculean strength & catlike reflexes."
    byn[1107] = "+3 ST, +3 DX, +3 HT."
    rows = ebl.parse(byn)
    assert [r["deity"] for r in rows] == ["Goddess of Evernight", "God of Combat"]
    assert rows[1]["effect"].endswith("+3 ST, +3 DX, +3 HT.")


def test_an_effect_that_lost_its_full_stop_fails_rather_than_shifting_the_table():
    """The loud failure the shape rule buys: an effect read as an eighth deity
    changes the row count, which verify() refuses -- rather than shipping a
    table whose cells have all moved up one."""
    byn = dict(_blessing_lines())
    byn[1106] = "Herculean strength & catlike reflexes, +3 ST, +3 DX, +3 HT"
    rows = ebl.parse(byn)
    assert len(rows) == 3  # the effect became a deity of its own


def test_the_price_has_no_source_if_its_one_sentence_goes():
    """The table prints no cost column at all -- the price is stated once, in
    prose, for all seven rows. That sentence is the only place it exists."""
    byn = ebl.load_lines()
    payload = ebl.build(byn)
    broken = dict(byn)
    broken[1099] = "The Seven Orthodox Churches bless their faithful."
    with pytest.raises(ValueError, match="75-point sentence"):
        ebl.verify(broken, payload, [], [], _knowledge_skills())


def _knowledge_skills():
    return [{"name": "Occultism", "category": ebl.KNOWLEDGE_BANNER}]


def test_the_slash_reading_fails_the_day_the_book_prints_occult_skills():
    """THE WARRANT. "occult/knowledge skills" is read as chapter 3's KNOWLEDGE
    banner because "occult skill" is printed nowhere -- one half of the slash
    names no group at all. If the book ever prints it, the slash may be
    joining two groups and the reading has to be made again."""
    byn = ebl.load_lines()
    payload = ebl.build(byn)
    ebl.verify(byn, payload, [], [], _knowledge_skills())  # green as committed
    widened = dict(byn)
    widened[max(byn) + 1] = "Add +1 to all occult skills."
    with pytest.raises(ValueError, match="occult skill"):
        ebl.verify(widened, payload, [], [], _knowledge_skills())


def test_a_deity_that_is_also_a_catalogue_trait_is_a_second_printing():
    """Disjointness, the check extract_appearance.py already makes: a name in
    both lists would make matching a bare name ambiguous, and would mean this
    table restates rows another file already carries."""
    byn = ebl.load_lines()
    payload = ebl.build(byn)
    clash = [{"name": "Earth Mother"}]
    with pytest.raises(ValueError, match="also a catalogue trait"):
        ebl.verify(byn, payload, clash, [], _knowledge_skills())


def test_the_two_mechanical_cells_are_pinned_exactly():
    """The screen quotes these, and a re-cut cell still reads plausibly."""
    byn = ebl.load_lines()
    payload = ebl.build(byn)
    payload["entries"] = [dict(e) for e in payload["entries"]]
    row = next(e for e in payload["entries"] if e["deity"] == "God of Combat")
    row["effect"] = "Herculean strength & catlike reflexes. +2 ST, +2 DX, +2 HT."
    with pytest.raises(ValueError, match="God of Combat no longer reads"):
        ebl.verify(byn, payload, [], [], _knowledge_skills())


import extract_equipment


def equipment_entries():
    return json.loads((CONTENT / "equipment.json").read_text(encoding="utf-8"))["entries"]


def equipment_named(name):
    return next(e for e in equipment_entries() if e["name"] == name)


def test_the_equipment_table_keeps_its_four_categories_in_order():
    entries = equipment_entries()
    assert len(entries) == 77
    seen = list(dict.fromkeys(e["category"] for e in entries))
    assert seen == extract_equipment.CATEGORIES
    assert all(e["name"] and e["cost"] and e["notes"] for e in entries)


def test_the_notes_cell_is_verbatim_and_no_modifier_is_derived():
    """The bonus is never applied, so nothing parses a number out of the cell.
    A `modifier` field would be a value with no consumer -- and the seed of
    stacking two disguise kits and inverting a penalty aimed at the observer."""
    assert all(set(e) == {"name", "cost", "category", "notes", "skills"}
               for e in equipment_entries())
    chest = equipment_named("Portable medicine chest")
    assert chest["notes"] == "Field hospital; +2 to First Aid & Surgery; 50 doses"


def test_a_mention_is_not_a_signed_clause():
    """"Lock picks (set)" says "Required for Lockpicking skill" and grants
    nothing. A substring matcher would offer it on every Lockpicking roll as
    though it helped; only a SIGNED clause joins."""
    picks = equipment_named("Lock picks (set)")
    assert "Lockpicking" in picks["notes"]
    assert picks["skills"] == []
    # And the row one line below it, which does carry a sign.
    assert equipment_named("Lockpick set (basic)")["skills"] == [
        {"name": "Lockpicking", "specialization": None}
    ]


@needs_pdf
def test_loosening_the_predicate_into_a_mention_fails_verification():
    """The guard is load-bearing: if the matcher ever resolved a mention, the
    lock picks would join Lockpicking and verify() must refuse the run."""
    index = extract_equipment.skill_index()
    tagged = extract_equipment.tag_categories(
        extract_equipment.stream(extract_equipment.load_lines(),
                                 *extract_equipment.EQUIPMENT_RANGE)
    )
    rows = extract_equipment.parse(tagged, index)
    patched = [
        {**r, "skills": [{"name": "Lockpicking", "specialization": None}]}
        if r["name"] == "Lock picks (set)" else r
        for r in rows
    ]
    with pytest.raises(ValueError, match="Lock picks"):
        extract_equipment.verify(patched, index)


def test_one_bonus_may_name_two_skills_and_another_offers_a_choice():
    """The book joins with "&" and chooses with "or", on the same page. Both
    halves of a conjunction join; so does the resolvable half of a choice."""
    assert equipment_named("Portable medicine chest")["skills"] == [
        {"name": "First Aid", "specialization": None},
        {"name": "Surgery", "specialization": None},
    ]
    assert equipment_named("Holy symbol (quality)")["skills"] == [
        {"name": "Ritualistic Magic", "specialization": None},
        {"name": "Religious Ritual", "specialization": None},
    ]
    # "+1 to Fortune-Telling or Psychology" -- only the second half is printed
    # in chapter 3's list, under that spelling.
    assert equipment_named("Crystal ball (glass)")["skills"] == [
        {"name": "Psychology", "specialization": None}
    ]


def test_the_condition_is_trimmed_before_the_target_is_split():
    """"+1 to First Aid on burns & rashes" is one skill, not two. Splitting on
    "&" before trimming the condition would claim a skill called "rashes"."""
    assert equipment_named("Herbal salves (3x)")["skills"] == [
        {"name": "First Aid", "specialization": None}
    ]
    assert extract_equipment.split_targets(
        extract_equipment.trim_condition("First Aid on burns & rashes")
    ) == ["First Aid"]


def test_a_specialization_is_part_of_the_identity():
    """The compass reads "Navigation (Land) +1". A character rolling the bare
    Navigation row is rolling a different skill and must not see it."""
    assert equipment_named("Compass (brass)")["skills"] == [
        {"name": "Navigation", "specialization": "Land"}
    ]
    index = extract_equipment.skill_index()
    assert ("Navigation", "Land") in index and ("Navigation", None) in index


def test_a_bonus_naming_an_instance_of_an_open_row_joins_it():
    """Chapter 3 leaves two of these rows OPEN, and that is what makes an
    instance of them a match rather than a miss.

    "Connoisseur" and "Savoir-Faire" are printed once each with no
    specialization and marked specializable -- a template the player fills in.
    Chapter 8 prices instances of both. A strict key match reads those as
    skills chapter 3 never printed and drops the bonus, which is 6cz's defect
    on the WRITING side of the same join: `Equipment.forSkill` was taught that
    an open row is matched by its instance and this was not.
    """
    index = extract_equipment.skill_index()
    assert index[("Connoisseur", None)] is True
    assert index[("Savoir-Faire", None)] is True
    assert ("Connoisseur", "Books") not in index
    assert ("Savoir-Faire", "High Society") not in index
    # Stored as the book prints it: the bonus is scoped to that instance, so a
    # character with Connoisseur (Antiques) must not be offered it.
    assert equipment_named("Rare book catalogue")["skills"] == [
        {"name": "Connoisseur", "specialization": "Books"}
    ]
    assert equipment_named("Fine clothes (quality)")["skills"] == [
        {"name": "Savoir-Faire", "specialization": "High Society"}
    ]


def test_an_unprinted_specialization_of_an_ENUMERATED_row_joins_nothing():
    """The widening stops where the book stops leaving the row open.

    Chapter 3 prints Navigation three times -- bare, (Sea), (Land) -- and
    marks it NOT specializable, so the list enumerates its own instances and
    "Navigation (Air)" is a skill this book does not have. Reading every
    parenthesis as an instance would invent it.
    """
    index = extract_equipment.skill_index()
    assert index[("Navigation", None)] is False
    assert extract_equipment.joins(("Navigation", "Land"), index)
    assert not extract_equipment.joins(("Navigation", "Air"), index)
    assert extract_equipment.joins(("Connoisseur", "Books"), index)
    # An open row is matched by an instance, never by a name it never printed.
    assert not extract_equipment.joins(("Sommelier", "Wine"), index)


@needs_pdf
def test_every_clause_target_that_joins_nothing_is_recorded():
    """The guard that was missing, and the reason this slice exists.

    verify() checked that no RECORDED exclusion resolved -- and never that
    every unresolved target is recorded. A new clause naming a skill under a
    spelling chapter 3 does not print would be dropped in silence, with the
    row count, the pinned joins and the joiner set all still green. The set
    below is the closed answer, so a new drop has to be read by a human.
    """
    index = extract_equipment.skill_index()
    tagged = extract_equipment.tag_categories(
        extract_equipment.stream(extract_equipment.load_lines(),
                                 *extract_equipment.EQUIPMENT_RANGE)
    )
    rows = extract_equipment.parse(tagged, index)
    unresolved = {
        t for r in rows for t in extract_equipment.clause_targets(r["notes"])
        if not extract_equipment.joins(extract_equipment.as_key(t), index)
    }
    assert unresolved == extract_equipment.RECORDED_UNJOINED
    # Each of the three sets earns its place: a roll the book never prints as
    # a skill, a skill chapter 3 omits, and one it spells differently. Their
    # union is asserted above, so what is left to check is that they are
    # DISJOINT -- a name recorded in two of them is two records of one fact,
    # and the union cannot see that.
    sets = (extract_equipment.UNPRINTED_TARGETS,
            extract_equipment.ABSENT_FROM_CHAPTER_3,
            extract_equipment.HYPHENATED_IN_CHAPTER_8)
    assert sum(len(x) for x in sets) == len(extract_equipment.RECORDED_UNJOINED)
    assert "reaction" in extract_equipment.UNPRINTED_TARGETS
    assert extract_equipment.HYPHENATED_IN_CHAPTER_8 == {"Fortune-Telling"}


@needs_pdf
def test_a_target_that_joins_nothing_and_is_unrecorded_fails_verification():
    """RED-proof for the reverse guard, mutating the direction that FAILS
    silently: a new clause appearing, not an old one going away."""
    index = extract_equipment.skill_index()
    tagged = extract_equipment.tag_categories(
        extract_equipment.stream(extract_equipment.load_lines(),
                                 *extract_equipment.EQUIPMENT_RANGE)
    )
    rows = extract_equipment.parse(tagged, index)
    patched = [
        # A name chapter 3 does not print. Cryptography would have been the
        # obvious mutation and it IS in the skill list, so it resolves and
        # proves nothing -- a mutation has to land where the guard looks.
        {**r, "notes": r["notes"] + "; +1 to Basket Weaving"}
        if r["name"] == "Magnifying glass" else r
        for r in rows
    ]
    with pytest.raises(ValueError, match="Basket Weaving"):
        extract_equipment.verify(patched, index)


def test_four_rolls_are_priced_without_ever_being_printed_as_skills():
    """"spot", "fool officials", "infection-resistance rolls" and "ritual
    effects" are rolls this book charges for and never lists. Guessing one
    into Observation or Search would be authoring content."""
    joined = {s["name"] for e in equipment_entries() for s in e["skills"]}
    assert not (joined & extract_equipment.UNPRINTED_TARGETS)
    for name in ("Trap wire (10 m)", "Dark clothing (set)", "False badge (tin)",
                 "Antiseptic (bottle)", "Holy symbol (wooden)"):
        assert equipment_named(name)["skills"] == []


def test_chapter_8_grants_bonuses_to_two_skills_chapter_3_never_prints():
    """A 4.1 disagreement between two chapters of one book: recorded here,
    never repaired by inventing a skill row for either."""
    index = extract_equipment.skill_index()
    for absent in extract_equipment.ABSENT_FROM_CHAPTER_3:
        assert (absent, None) not in index
    assert "Forced Entry" in equipment_named("Heavy boots (leather)")["notes"]
    assert equipment_named("Heavy boots (leather)")["skills"] == []
    assert "Gather Information" in equipment_named("Press badge")["notes"]
    assert equipment_named("Press badge")["skills"] == []


def test_the_fortune_seam_is_the_books_own_and_is_not_normalised():
    """Chapter 8 spells it "Fortune-Telling"; chapter 3's list prints "Fortune
    Telling". Matching across the hyphen would decide what the book meant on
    resemblance alone (6by), so both decks join nothing."""
    index = extract_equipment.skill_index()
    assert ("Fortune Telling", None) in index
    assert ("Fortune-Telling", None) not in index
    for deck in ("Tarot deck (common)", "Tarot deck (quality)"):
        assert "Fortune-Telling" in equipment_named(deck)["notes"]
        assert equipment_named(deck)["skills"] == []


# --- The Secondary Characteristics cost column (Ch. 2, p. 8-9) -------------


def secondary_rows():
    return core_entries("secondary_costs")


def test_the_book_prices_a_secondary_in_exactly_one_place():
    """`extract_core_rules` used to skip this table because "ch 3 prints the
    costs, which are already extracted". Chapter 3 prints no such thing -- its
    `pts/level` lines are advantage rows -- and "Cost to Modify" occurs once in
    the whole book, on page 8."""
    byn = extract_core_rules.load_lines()
    printing = [n for n, t in byn.items() if "Cost to Modify" in t]
    assert printing == [357], printing
    per_level = [
        n for n, t in byn.items()
        if re.search(r"(pts?|points?) per (level|0\.25)", t, re.I)
    ]
    # All six on pages 8-9, none anywhere else in the book.
    assert per_level and all(353 <= n <= 386 for n in per_level), sorted(per_level)


def test_the_nine_rows_split_six_priced_from_three_refused():
    rows = secondary_rows()
    assert [r["key"] for r in rows] == [
        "hp", "will", "per", "fp", "spi", "cor", "basicSpeed", "basicMove", "digestion",
    ]
    priced = {r["key"]: r["pointsPerStep"] for r in rows if r["pointsPerStep"] is not None}
    assert priced == {
        "hp": 2, "will": 5, "per": 5, "fp": 3, "basicSpeed": 5, "basicMove": 5,
    }


def test_an_unpriced_row_keeps_the_words_that_refuse_it():
    """Absent, not zero. "Fixed at 0" and "never bought" are rules, and a 0
    would read as a free purchase -- the distinction this repo already draws
    between printing nothing and printing zero."""
    by_key = {r["key"]: r for r in secondary_rows()}
    assert by_key["spi"]["cost"] == "Fixed at 0; see Chapter 4"
    assert by_key["cor"]["cost"] == "Tracked in play; never bought"
    assert by_key["digestion"]["cost"] == "Tracked through acting"
    assert all(by_key[k]["pointsPerStep"] is None for k in ("spi", "cor", "digestion"))
    assert all(by_key[k]["step"] is None for k in ("spi", "cor", "digestion"))


def test_basic_speed_is_the_only_row_priced_per_fraction():
    """A parser that dropped the unit would price a whole point of Basic Speed
    at 5 instead of 20, and SecondaryDelta counts quarters because of it."""
    by_key = {r["key"]: r for r in secondary_rows()}
    assert by_key["basicSpeed"]["step"] == "0.25"
    assert all(
        r["step"] in (None, "level")
        for r in secondary_rows() if r["key"] != "basicSpeed"
    )


@needs_pdf
def test_a_renamed_characteristic_is_a_finding_not_a_slug():
    """The printed name -> key map is pinned. A row the book renames must stop
    the run rather than silently produce a key no consumer joins on."""
    byn = dict(extract_core_rules.load_lines())
    byn[358] = "Hit Points"
    with pytest.raises(ValueError, match="no consumer has a key for"):
        extract_core_rules.secondaries(byn)


@needs_pdf
def test_a_moved_price_fails_verification():
    """These six numbers bill every character PointBudget reports on."""
    bands, pools, rows, defs, secs = extract_core_rules.extract()
    secs = [dict(s) for s in secs]
    secs[0]["pointsPerStep"] = 4
    with pytest.raises(ValueError, match="changed price"):
        extract_core_rules.verify(
            bands, pools, rows, defs, secs, extract_core_rules.load_lines())


@needs_pdf
def test_pricing_a_row_the_table_refuses_fails_verification():
    bands, pools, rows, defs, secs = extract_core_rules.extract()
    secs = [dict(s) for s in secs]
    spi = next(s for s in secs if s["key"] == "spi")
    spi["pointsPerStep"] = 5
    with pytest.raises(ValueError, match="is now priced"):
        extract_core_rules.verify(
            bands, pools, rows, defs, secs, extract_core_rules.load_lines())


# Chapter 5's own rows, as the book prints them on lines 2826 and 2830.
CHAPTER_5_MAJOR_WOUND = {
    "Failure": "Stunned for 1d6 seconds (roll HT each turn to recover early)",
    "Fail by 5+": "Knocked down and unconscious for 1d hours",
}
APPENDIX_MAJOR_WOUND = (
    "Major Wound: Hit > ½ HP -> HT or stunned 1d6 sec | Fail by 5+ -> down 15 min"
)


def test_the_appendix_summary_is_cut_into_the_clauses_it_summarises():
    """Two clauses, each a condition and an outcome, on one line."""
    summary = extract_appendix.parse_major_wound(APPENDIX_MAJOR_WOUND)
    assert summary == {
        "Hit > ½ HP": "HT or stunned 1d6 sec",
        "Fail by 5+": "down 15 min",
    }


def test_the_appendix_and_chapter_five_really_do_disagree_on_the_worst_row():
    """A guard against silent repair, in the 4.2 shape.

    Chapter 5's own table says a Major Wound failed by 5+ leaves you "Knocked
    down and unconscious for 1d hours". Appendix A's summary of that same
    table says "down 15 min". Chapter 5 governs -- a summary may define but
    not overrule -- so this is RECORDED, never repaired, and the recording is
    only honest while both printings still say what it claims.
    """
    summary = extract_appendix.parse_major_wound(APPENDIX_MAJOR_WOUND)
    extract_appendix.verify_major_wound(summary, CHAPTER_5_MAJOR_WOUND)

    # The exclusion is stale the day they agree, and a stale exclusion is how
    # a repair goes unnoticed.
    with pytest.raises(ValueError, match="no longer disagree"):
        extract_appendix.verify_major_wound(
            summary, {**CHAPTER_5_MAJOR_WOUND, "Fail by 5+": "down 15 min"})

    # And the clause they DO agree on is checked in the same pass, because
    # "already claimed" was never the same question as "agrees".
    moved = dict(summary)
    moved["Hit > ½ HP"] = "HT or stunned 2d6 sec"
    with pytest.raises(ValueError, match="agreed with"):
        extract_appendix.verify_major_wound(moved, CHAPTER_5_MAJOR_WOUND)


def test_the_agreement_check_reads_both_printings_not_just_the_summary():
    """A one-way check has a blind side (6dh), and this one had it.

    The agreement clause was compared against the appendix's wording alone,
    so chapter 5's own row could drift to anything at all while the guard
    kept passing. Found by review, not by the tests above.
    """
    summary = extract_appendix.parse_major_wound(APPENDIX_MAJOR_WOUND)
    drifted = {**CHAPTER_5_MAJOR_WOUND, "Failure": "Knocked out for 2d6 seconds"}
    with pytest.raises(ValueError, match="chapter 5 now reads"):
        extract_appendix.verify_major_wound(summary, drifted)


def test_the_recorded_disagreement_names_both_printings():
    """A prose-only record is invisible to every sweep this repo runs (6cj).

    The two hit-location disagreements this file found were written into a
    docstring, and the Major Wound one was missed for six slices while being
    printed to the player on the damage screen.
    """
    both = extract_appendix.SUMMARY_DISAGREEMENTS["Fail by 5+"]
    assert both == ("down 15 min", "Knocked down and unconscious for 1d hours")
    byn = {r["n"]: r["text"] for r in
           json.loads((ROOT / "build" / "extract" / "lines.json")
                      .read_text(encoding="utf-8"))}
    assert byn[9672].strip() == APPENDIX_MAJOR_WOUND
    # Every chapter-5 string the tests above feed the guard, tied to the line
    # the book prints it on. Without this the guard would be checking the
    # appendix against strings typed HERE -- a value asserted on the wrong
    # side of the thing that produces it, which is the hole 6dk shipped once.
    for row, n in extract_appendix.SUMMARISES.values():
        assert byn[n].strip() == CHAPTER_5_MAJOR_WOUND[row], (row, n)
    assert CHAPTER_5_MAJOR_WOUND["Fail by 5+"] == both[1]


# --- Appendix A's facing line (Ch. A, p. 159) ------------------------------


def test_the_appendix_prints_the_facing_penalty_a_second_time():
    """`ActiveDefenses`' KDoc used to say the facing rule "has no second
    printing anywhere in the book -- checked, not assumed". This is that
    second printing. It is pinned because the KDoc now cites it: an argument
    resting on a sentence needs the sentence guarded, or the correction
    becomes the next stale claim."""
    byn = {r["n"]: r["text"] for r in
           json.loads((ROOT / "build" / "extract" / "lines.json")
                      .read_text(encoding="utf-8"))}
    assert byn[9667].strip() == "Direction Penalties: Side -2 | Rear -4"
    # Its neighbour enumerates all three defences, which is what made the line
    # look like it could widen chapter 5's singular.
    assert byn[9666].strip().startswith("Active Defenses: Dodge =")
    assert "Parry" in byn[9666] and "Block" in byn[9666]


def test_the_appendix_line_names_no_defence_so_it_widens_nothing():
    """The whole refusal now rests on ONE property of line 9667: it carries no
    noun. To widen chapter 5's singular "to defense", a second printing has to
    say something the first did not, and this one says strictly less. If the
    line ever acquires "defense" or "defenses", that reason has expired."""
    byn = {r["n"]: r["text"] for r in
           json.loads((ROOT / "build" / "extract" / "lines.json")
                      .read_text(encoding="utf-8"))}
    assert not re.search(r"defen[cs]e", byn[9667], re.I), byn[9667]
    # And chapter 5's own two bullets still print the singular they are
    # refused on. Both halves have to hold for the refusal to stand.
    for n in (2480, 2481):
        assert re.search(r"to defense\b", byn[n], re.I), byn[n]
        assert not re.search(r"to defenses\b", byn[n], re.I), byn[n]


@needs_pdf
def test_mechanical_filter_sees_a_cost_priced_in_a_bare_pool():
    """A cost is not always signed, and the filter used to miss the ones
    that are not.

    coverage.py admitted "5 pts" from the start -- a bare integer followed
    by the unit a rule reads -- and admitted no other pool. So the book's
    ability costs were read as flavour. These are real book lines, quoted
    by number rather than invented, and the assertion that matters is the
    SECOND one in each pair: the old pattern did not match them. Without
    that half this test would pass against the narrow filter it exists to
    replace.
    """
    import coverage as coverage_tool

    # "The old filter" is derived by removing the widening alternative from
    # the pattern that actually ships, never retyped. A hand-copied duplicate
    # stops representing the old filter the moment anyone edits one of the
    # four shared alternatives, and then the negative assertions below are
    # made against a pattern nobody runs.
    widening = r"|\b\d+\s*(?:SPI|HP|FP|CoR|DR)\b"
    live = coverage_tool.MECHANICAL.pattern
    assert live.endswith(widening), (
        "the pool alternative is no longer the last one in MECHANICAL, so "
        f"slicing it off no longer yields the pre-change filter: {live!r}"
    )
    narrow = re.compile(live[: -len(widening)])
    byn = {l["n"]: l["text"] for l in json.loads((OUT / "lines.json").read_text(encoding="utf-8"))}

    for n, quote in [
        (4762, "4 HP"),
        (4763, "1 SPI"),
        (4840, "1 SPI"),
    ]:
        text = byn[n]
        assert quote in text, f"line {n} no longer prints {quote!r}: {text!r}"
        assert coverage_tool.MECHANICAL.search(text), f"line {n} is still invisible"
        assert not narrow.search(text), (
            f"line {n} was already visible to the narrow filter, so it cannot "
            "be evidence for widening it"
        )

    # The condition is the unit, not the digit. A number followed by
    # anything that is not a pool the rules read stays flavour.
    assert not coverage_tool.MECHANICAL.search("It is around 5 cm in length.")
    assert not coverage_tool.MECHANICAL.search("The ritual takes 20 minutes to prepare.")


@needs_pdf
def test_spirit_vision_chapter_reports_a_completion_it_earned():
    """The blind spot moved the denominator, not just the miss count.

    A line the filter cannot see is excluded from BOTH sides of the ratio,
    and main() reads an empty denominator as "no mechanical lines to claim --
    chapter is complete". So chapter 7.6 reported 100.0% while being claimed
    by no content file whatsoever. 6db widened the filter and it fell to
    0.0%, which is what it had always been.

    **It reads 100.0% again now, and this time it is earned**:
    `extract_spirit_vision.py` claims the chapter. That makes the earned and
    the unearned completion indistinguishable by percentage alone, which is
    precisely the hazard this test was written against -- so the percentage
    is no longer what it rests on. It asserts the thing the number was
    standing in for: that the chapter's priced line is CLAIMED. A completion
    nobody earned fails here; a completion somebody extracted passes.

    The assertion order is the lesson. Checking only that line 4840 matches
    the filter would assert the precondition and not the consequence, and an
    off-by-one in the page range would leave the chapter claiming it is
    finished with every precondition still true.
    """
    import coverage as coverage_tool

    byn = {l["n"]: l["text"] for l in json.loads((OUT / "lines.json").read_text(encoding="utf-8"))}
    activation = byn[4840]
    assert "1 SPI" in activation, f"line 4840 no longer prices Spirit Vision: {activation!r}"
    assert coverage_tool.MECHANICAL.search(activation)

    claimed = set()
    content = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"
    for f in content.rglob("*.json"):
        data = json.loads(f.read_text(encoding="utf-8"))
        for spans in (data.get("sourceLines") or {}).values():
            for a, b in spans:
                claimed.update(range(a, b + 1))
    assert 4840 in claimed, (
        "Spirit Vision's activation cost is claimed by no content file. The "
        "chapter reports 100.0% anyway once its visible lines go, which is "
        "the empty-denominator branch reading invisibility as completion -- "
        "the defect this test exists for."
    )

    subprocess.run(
        [sys.executable, str(ROOT / "tools" / "coverage.py")],
        capture_output=True, text=True,
    )
    report = (OUT / "coverage.md").read_text(encoding="utf-8")
    row = next(
        (l for l in report.splitlines() if l.startswith("| 7.6 |")),
        None,
    )
    assert row, f"chapter 7.6 is missing from the coverage report:\n{report}"
    pct = re.search(r"\|\s*([\d.]+)%\s*\|", row)
    assert pct, f"no percentage in chapter 7.6's row: {row!r}"
    assert float(pct.group(1)) == 100.0, (
        "chapter 7.6 is claimed by extract_spirit_vision.py, so it must "
        f"report the completion it earned: {row!r}"
    )


# --- The fatigue row's silence about rounding (Ch. 5, p. 42) ----------------


def test_the_fatigue_row_prints_no_rounding_direction():
    """`Pools.halvedForFatigue` rounds UP and its KDoc says the direction is
    this app's choice because the row prints none. That is an argument resting
    on a sentence, so the sentence is pinned. If line 2852 ever acquires a
    direction, the choice stops being a choice and the KDoc has expired."""
    byn = {r["n"]: r["text"] for r in
           json.loads((ROOT / "build" / "extract" / "lines.json")
                      .read_text(encoding="utf-8"))}
    assert byn[2852].strip() == "-1 to all rolls; Move and Dodge halved"
    assert byn[2851].strip() == "At 1/3 FP or below"
    assert not re.search(r"round(ed)?\s+(up|down)", byn[2852], re.I), byn[2852]


def test_the_book_does_print_a_direction_where_it_cares():
    """The other half of the argument: the row's silence means something only
    because this book DOES print directions elsewhere. Two of them, in both
    directions, so there is no house convention to fall back on."""
    byn = {r["n"]: r["text"] for r in
           json.loads((ROOT / "build" / "extract" / "lines.json")
                      .read_text(encoding="utf-8"))}
    assert re.search(r"rounded down", byn[2450], re.I), byn[2450]
    assert re.search(r"round up", byn[6050], re.I), byn[6050]


def test_chapter_boundaries_fall_on_the_heading_not_the_page():
    """A chapter partition by PAGE puts 57 mechanical lines in the wrong chapter.

    `chapters.json` carries a startPage per chapter, so `coverage.py` used to
    assign every line on that page to the chapter -- including the lines above
    the heading, which belong to the chapter before it. That is invisible for
    most chapters (a page break usually falls between them) and wrong wherever
    the book starts a chapter mid-page.

    Appendix A is the case that proves it. Its heading is on p. 159, Appendix
    B's on p. 160, so the page partition gave the WHOLE of p. 160 to B. But
    lines 9707-9747 -- the Spiritual Skills table, the CoR reference, the SPI
    recovery row and the entire Common Modifiers table -- print on p. 160 and
    belong to A. `common_modifiers.json` claims them under "A", correctly, and
    the claim landed in a bucket holding none of those lines while chapter B
    was charged for lines nothing had promised to extract.

    So the reading that looks obvious from the coverage table -- "six content
    files declare the wrong chapter id" -- is backwards. Every one of the six
    declared ids is right when measured against the heading. The partition was
    the defect, and a fix that "corrected" the declarations would have moved
    Appendix A's own quick-reference tables into the political appendix.
    """
    lines = json.loads((ROOT / "build" / "extract" / "lines.json")
                       .read_text(encoding="utf-8"))
    byn = {r["n"]: r["text"] for r in lines}

    # The heading lines the partition must respect, read from the book itself.
    assert byn[9631].strip().startswith("Appendix A:")
    assert byn[9748].strip().startswith("Appendix B:")

    # Between them lies content that is Appendix A's, on Appendix B's page.
    pageof = {r["n"]: r["page"] for r in lines}
    assert pageof[9745] == 160, "the Common Modifiers table prints on p.160"
    assert pageof[9631] == 159, "but Appendix A's heading is on p.159"
    assert byn[9745].strip() == "+2 (if they succeed first)"

    # And the content file that claims them names A, which the heading agrees
    # with and the page range does not.
    claim = json.loads((ROOT / "core" / "rules" / "src" / "main" / "resources"
                        / "content" / "common_modifiers.json")
                       .read_text(encoding="utf-8"))
    assert claim["sourceLines"] == {"A": [[9725, 9745]]}
    assert 9631 < 9725 and 9745 < 9748, "the claim sits inside A's heading span"


@needs_pdf
def test_coverage_credits_a_claim_that_spans_a_mid_page_boundary():
    """The regression this guards: Appendix A's own tables scored against B.

    Run over the real content, Appendix B must not be charged for the
    twenty-one lines `common_modifiers.json` extracted, and Appendix A must be
    credited for them. Under the page partition B read 0.0% with 12 unclaimed
    mechanical lines that were every one of them already extracted.
    """
    import re as regex
    subprocess.run([sys.executable, str(ROOT / "tools" / "coverage.py")],
                   capture_output=True, text=True)
    report = (OUT / "coverage.md").read_text(encoding="utf-8")

    row = regex.search(r"^\| B \| The Political World \|[^|]*\|\s*([\d.]+)%\s*\|\s*(\d+) \|$",
                       report, regex.MULTILINE)
    assert row, "Appendix B should be in the coverage report"
    unclaimed = int(row.group(2))
    # Appendix B genuinely holds no extracted table of its own; the lines it
    # was being charged for belong to A. Charging it none of them is the fix.
    assert unclaimed == 0, (
        f"Appendix B has {unclaimed} unclaimed mechanical lines; under a "
        f"heading-precise partition it should have none -- the lines it was "
        f"charged for are Appendix A's Common Modifiers table."
    )


def test_no_claim_crosses_a_chapter_heading():
    """Every extracted span sits inside the chapter it declares.

    This is the check that would have caught the partition defect from the
    other side, and it found five spans the coverage table never flagged.
    Four were two-line page headers. The fifth was fifty lines: `pathways.json`
    filed Arbiter and Lawyer under chapter 10, and both are SEQUENCE 9 entries
    whose rows happen to print on p.97 above chapter 10's heading. The page
    partition and the declaration were wrong in the same direction, so they
    agreed with each other and the error was invisible to a report comparing
    them.

    A misfiled claim does not lose the line -- it is still claimed by SOMETHING,
    so no total moves. It moves the credit to the wrong chapter, which is
    exactly the failure that makes a per-chapter percentage untrustworthy.
    """
    import sys as _sys
    _sys.path.insert(0, str(ROOT / "tools"))
    import coverage as coverage_tool

    lines = json.loads((ROOT / "build" / "extract" / "lines.json")
                       .read_text(encoding="utf-8"))
    chapters = json.loads((ROOT / "build" / "extract" / "chapters.json")
                          .read_text(encoding="utf-8"))
    heads = coverage_tool.heading_lines(lines, chapters)
    ordered = sorted(chapters, key=lambda c: heads[c["id"]])
    bounds = {
        c["id"]: (heads[c["id"]],
                  heads[ordered[i + 1]["id"]] - 1 if i + 1 < len(ordered)
                  else lines[-1]["n"])
        for i, c in enumerate(ordered)
    }

    content = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"
    offenders = []
    for f in sorted(content.rglob("*.json")):
        data = json.loads(f.read_text(encoding="utf-8"))
        for cid, spans in (data.get("sourceLines") or {}).items():
            lo, hi = bounds[cid]
            for span in spans:
                if span[0] < lo or span[1] > hi:
                    offenders.append(f"{f.name} claims {span} under {cid} (which is {lo}-{hi})")
    assert not offenders, "claims crossing a chapter heading:\n  " + "\n  ".join(offenders)


def test_the_glossary_is_the_one_heading_the_book_does_not_number():
    """Why `heading_lines` names "G" explicitly instead of falling back.

    Every other section heads with "Chapter N:" or "Appendix X:". The glossary
    prints "Glossary of Terms" bare -- "G" is this project's id for it, not the
    book's. A general "try the title if the prefix misses" fallback would cover
    it AND would silently cover the next heading an extractor mangles, which is
    the failure the SystemExit exists to make loud. So the exception is named,
    and this test is what makes the naming checkable.
    """
    byn = {r["n"]: r["text"] for r in
           json.loads((ROOT / "build" / "extract" / "lines.json")
                      .read_text(encoding="utf-8"))}
    assert byn[10156].strip() == "Glossary of Terms"
    assert not byn[10156].strip().startswith("Appendix")
    # And the form every other section does use, for contrast.
    assert byn[9840].strip().startswith("Appendix C:")
    assert byn[2995].strip().startswith("Chapter 6:")


def _glossary_lines():
    return {r["n"]: r["text"] for r in
            json.loads((OUT / "lines.json").read_text(encoding="utf-8"))}


def test_the_examples_exclusion_rests_on_typography_this_pdf_destroyed():
    """Why one line is excluded BY NAME rather than by a rule.

    `extract_glossary.NOT_TERMS` drops "Examples", and the reason is that the
    book's own discriminator is unavailable: line 10157 prints "Terms in
    italics are game mechanics; bold terms are setting concepts", and this
    PDF is flattened, so neither weight survives extraction. The exclusion is
    therefore a claim about the EXTRACTION, not about the book -- and it
    expires the day a re-extraction preserves formatting.

    Both halves are pinned so that day is loud: the sentence that names the
    missing discriminator, and the line it would have discriminated.
    """
    byn = _glossary_lines()
    assert "Terms in italics are game mechanics" in byn[10157]
    assert byn[10158].strip() == "bold terms are setting concepts."
    # The line that satisfies every structural property a term has.
    assert byn[10193].strip() == "Examples: Fool Pathway, Error Pathway, Darkness Pathway."
    assert extract_glossary.TERM.match(byn[10193].strip()), (
        "Examples no longer parses as a term, so NOT_TERMS may be dead weight"
    )


def test_the_full_stop_is_what_keeps_prose_out_of_the_glossary_terms():
    """The load-bearing character in TERM, measured rather than asserted.

    TERM's comment used to credit the length bound. It does not: relax the
    bound and nothing moves. What keeps a mid-sentence colon out is the full
    stop the character class omits -- line 10172 ends
    "...Starting budget: 70 points." and would parse as a term named for the
    prose in front of the colon, stealing Character Points' continuation.

    A comment is not a pin (this repo has paid for that), so the mutation
    that disproves it runs here.
    """
    byn = _glossary_lines()
    lines = extract_glossary.stream(byn, *extract_glossary.GLOSSARY_RANGE)
    base = [e["term"] for e in extract_glossary.entries(lines)]
    assert base == extract_glossary.TERMS

    original = extract_glossary.TERM
    try:
        extract_glossary.TERM = re.compile(r"^([A-Z0-9][A-Za-z0-9 /()'.—-]{0,58}?): (.+)$")
        widened = [e["term"] for e in extract_glossary.entries(lines)]
        assert "Disadvantages give points back. Starting budget" in widened, widened

        # And the bound the comment used to credit, which is slack: the
        # longest real term is 31 characters against a limit of 58.
        extract_glossary.TERM = re.compile(r"^([A-Z0-9][A-Za-z0-9 /()'—-]{0,200}?): (.+)$")
        assert [e["term"] for e in extract_glossary.entries(lines)] == base
    finally:
        extract_glossary.TERM = original
    assert max(len(t) for t in extract_glossary.TERMS) < 58


def test_chapter_G_reads_100_percent_off_a_denominator_of_three_lines():
    """What "G 100.0%" does and does not say.

    `glossary.json` moved chapter G from 0.0% to 100.0%, and quoting that
    without its denominator would repeat 6db exactly. G's MECHANICAL filter
    matches THREE lines in the whole chapter -- the HP, Spirit Vision and 3d6
    entries, which are the only ones carrying a number the filter recognises.
    The other 55 lines of glossary are prose, and the 39 lines of back matter
    below the closing note (blurb, credits, and a full table-of-contents
    reprint) are prose too, so nothing there can ever be counted as missing.

    The percentage is therefore true and nearly weightless: it says three
    lines are claimed, not that four pages were read. The claim `sourceLines`
    makes -- 10156-10213 -- is the honest statement of what was read, and it
    deliberately stops before the back matter.
    """
    byn = _glossary_lines()
    import coverage as coverage_tool

    in_glossary = [n for n in range(10156, 10214) if byn.get(n, "").strip()]
    mechanical = [n for n in in_glossary if coverage_tool.MECHANICAL.search(byn[n])]
    assert mechanical == [10185, 10203, 10208], mechanical
    assert len(in_glossary) == 58, len(in_glossary)

    # The back matter: in the chapter's page range, claimed by nothing, and
    # invisible to the metric because not one line of it is mechanical.
    back = [n for n in range(10214, 10253) if byn.get(n, "").strip()]
    assert back, "the back matter moved; G's page range no longer runs past 10213"
    assert not [n for n in back if coverage_tool.MECHANICAL.search(byn[n])]
    assert extract_glossary.GLOSSARY_RANGE[1] < back[0]


def test_the_glossary_conservation_check_reads_the_raw_lines():
    """The guard for the guard: verify() must not re-derive its own input.

    Conservation is what catches a continuation line that stops being
    emitted -- the term list cannot, because the definition it belonged to
    just gets shorter and every term is still present. The first version of
    that check built its expected value with `stream()`, so widening
    FURNITURE dropped the line from the output AND from the expectation and
    it stayed green. It now reads the raw lines, and this is the mutation
    that says so.
    """
    byn = _glossary_lines()
    data = {"entries": extract_glossary.entries(
        extract_glossary.stream(byn, *extract_glossary.GLOSSARY_RANGE))}
    extract_glossary.verify(data, byn)

    original = extract_glossary.FURNITURE
    try:
        # "the faster you digest..." is Acting Method's continuation (10160).
        extract_glossary.FURNITURE = re.compile(
            r"^(Powered by GURPS.*|Page \d+|the faster you digest.*)$")
        starved = {"entries": extract_glossary.entries(
            extract_glossary.stream(byn, *extract_glossary.GLOSSARY_RANGE))}
        assert [e["term"] for e in starved["entries"]] == extract_glossary.TERMS, (
            "the dropped line must leave the term list intact -- that is the "
            "whole reason conservation exists"
        )
        with pytest.raises(AssertionError, match="not conserved"):
            extract_glossary.verify(starved, byn)
    finally:
        extract_glossary.FURNITURE = original


@needs_pdf
def test_the_pages_column_reports_the_range_actually_measured():
    """The Pages column is derived from the same boundaries as the percentage.

    A review finding: the column used to print `chapters.json`'s declared
    startPage-endPage while the percentage beside it was measured over the
    heading-derived line span. Those disagree for precisely the chapters this
    partition exists to fix -- Appendix A is declared 159-159 and its content
    runs onto p.160 -- so a correct number sat next to a page range it had not
    been measured over.

    Overlapping ranges between neighbours are the POINT, not a defect: a page
    holding the end of one chapter and the start of the next is reported by
    both, which is what the book does.
    """
    import re as regex
    subprocess.run([sys.executable, str(ROOT / "tools" / "coverage.py")],
                   capture_output=True, text=True)
    report = (OUT / "coverage.md").read_text(encoding="utf-8")

    row = regex.search(r"^\| A \| Quick Reference Tables \| (\d+)-(\d+) \|",
                       report, regex.MULTILINE)
    assert row, "Appendix A should be in the coverage report"
    assert (int(row.group(1)), int(row.group(2))) == (159, 160), (
        f"Appendix A measured over pages {row.group(1)}-{row.group(2)}; its "
        f"content runs onto p.160 and the column must say so"
    )

    # And chapters.json still declares the narrower range, which is what makes
    # this a real difference rather than a restatement.
    chapters = json.loads((ROOT / "build" / "extract" / "chapters.json")
                          .read_text(encoding="utf-8"))
    appendix_a = next(c for c in chapters if c["id"] == "A")
    assert appendix_a["startPage"] == 159 and appendix_a["endPage"] == 159


def test_spirit_vision_keeps_its_two_colour_tables_apart():
    """Chapter 7.6 prints two colour tables that share a vocabulary.

    Seven of the eight colours appear in both, meaning unrelated things: the
    Astral table reads Purple as "Spirituality, madness", the Ether table
    puts Purple on the BRAIN. The column orders are inverted too -- A prints
    the colour first, B prints the region first. Either fact alone makes a
    merge or a shared positional parser a defect, so both are pinned here
    rather than only in the extractor, which runs on demand.
    """
    d = json.loads((ROOT / "core" / "rules" / "src" / "main" / "resources"
                    / "content" / "spirit_vision.json").read_text(encoding="utf-8"))
    astral = {r["color"]: r["meaning"] for r in d["astralColors"]}
    ether = {r["color"]: r["region"] for r in d["etherBody"]}
    assert len(astral) == 8 and len(ether) == 8
    assert len(set(astral) & set(ether)) == 7, sorted(set(astral) & set(ether))
    assert astral["Purple"] == "Spirituality, madness"
    assert ether["Purple"] == "Brain"
    # The field names are the guard against a positional merge: each table
    # names its own key, so neither can be read as the other by accident.
    assert set(d["astralColors"][0]) == {"color", "meaning"}
    assert set(d["etherBody"][0]) == {"region", "color"}


def test_spirit_vision_prints_rows_chapter_four_does_not():
    """Why 7.6's result table cannot be graded by `SpiritualSenses.rowFor`.

    Chapter 4's two sense tables print four rows, all successes, and
    `extract_spirituality.verify()` asserts the absence of a failure row.
    7.6 prints five, including a Failure -- and labels its critical row
    "Critical" where every other table in the book writes "Critical Success",
    which is the literal `rowFor` matches on. Feeding this table to that
    function would drop the failure and mis-grade the critical, silently.
    """
    d = json.loads((ROOT / "core" / "rules" / "src" / "main" / "resources"
                    / "content" / "spirit_vision.json").read_text(encoding="utf-8"))
    rows = d["reading"]["rows"]
    assert [r["result"] for r in rows] == [
        "Success", "Success by 3+", "Success by 5+", "Critical", "Failure",
    ]
    assert next(r["effect"] for r in rows if r["result"] == "Failure") == "Incorrect reading"

    # The short label really is unique to this table: the long form is what
    # the rest of the book uses, so this is a difference in the source and
    # not a normalisation this extractor chose.
    book = (ROOT / "build" / "extract" / "book.txt").read_text(encoding="utf-8", errors="replace")
    assert book.count("Critical Success") > 10
    assert "\nCritical\n" not in book.replace("Critical Success", "")


def test_spirit_vision_records_the_spectator_disagreement():
    """4.1/4.2: the summary and the chapter disagree, and that is recorded.

    Chapter 7.6 files the Spectator's Sequence 9 power under Spirit Vision as
    "Enhanced: Read emotions & thoughts". The Spectator's own chapter 9 block
    prints `Body Language Analysis (1 FP)` -- an observation power, priced in
    FP, that never mentions Spirit Vision. The other four rows of that table
    agree with their blocks, which is what makes this one a disagreement
    rather than a chapter that simply says less.

    This is the guard against silent repair. It fails if someone "corrects"
    either printing into agreement with the other.
    """
    content = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"
    d = json.loads((content / "spirit_vision.json").read_text(encoding="utf-8"))
    blocks = json.loads((content / "pathways.json").read_text(encoding="utf-8"))["entries"]

    row = next(r for r in d["pathwayDifferences"] if r["name"].startswith("Spectator"))
    assert row["ability"] == "Enhanced: Read emotions & thoughts"

    own = next(b for b in blocks if b["name"] == "Spectator" and b["sequence"] == 9)
    assert "Body Language Analysis (1 FP)" in own["ability"]
    assert "Spirit Vision" not in own["ability"], (
        "the Spectator's block now claims Spirit Vision; the disagreement 7.6 "
        "records has been repaired away and the reading must be made again"
    )

    for name in ("Seer", "Mystery Pryer", "Sleepless", "Corpse Collector"):
        block = next(b for b in blocks if b["name"] == name and b["sequence"] == 9)
        assert "Spirit Vision" in block["ability"], name


def test_the_crippling_paragraph_is_invisible_to_the_coverage_filter():
    """The rule no coverage number has ever reported missing.

    Lines 2785-2787 govern the entire `Special Effect on Hit` column -- what
    crippling IS, what it costs, and how it ends -- and they sit in a THREE
    LINE seam between two claimed spans: `hit_locations.json` stops at 2784
    and `wounding_multipliers.json` starts at 2789.

    That alone would be 6ba's gap. What makes it worse is that the paragraph
    matches NEITHER the old MECHANICAL filter nor the widened one, so it was
    never in any denominator: its numbers are a fraction glyph, a bare "1",
    and "12+" -- and the filter's signed-number rule wants the sign FIRST.

    Both halves are pinned. If a future filter learns to see these lines the
    assertion fails loudly, which is the right time to re-read this test.
    """
    import coverage as coverage_tool
    byn = _glossary_lines()

    assert byn[2785].strip().startswith("Crippling: A limb or extremity struck")
    assert "First Aid (skill 12+) removes permanent" in byn[2786]
    assert byn[2787].strip() == "damage risk."
    for n in (2785, 2786, 2787):
        assert not coverage_tool.MECHANICAL.search(byn[n]), (
            f"line {n} is now visible to the coverage filter; it was invisible "
            f"to both the narrow and the widened form, which is why nothing "
            f"ever reported it unclaimed"
        )


def test_every_hit_location_threshold_is_read_from_its_own_cell():
    """The five cells that print a damage threshold, and the four that do not.

    The `Special Effect on Hit` column had no reader at all -- not in
    production, not in a test -- while carrying thresholds the damage screen
    already holds both sides of. These are the cells `parse_thresholds` reads,
    named rather than counted, so a cell changing its wording fails here
    instead of silently dropping an effect.

    **Arm and Leg print two numbers and only one of them is the rule.** The
    governing threshold is "> 1/2 HP of limb"; "~ HP/3" is the BOOK's own
    approximation of that in character HP, tilde and all. Storing one divisor
    without saying so would let a test bless the approximation as the rule,
    so `approximate` travels with it.
    """
    content = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"
    rows = json.loads((content / "hit_locations.json").read_text(encoding="utf-8"))["entries"]
    by_target = {r["target"]: r for r in rows}

    assert by_target["Arm"]["thresholds"] == [
        {"outcome": "Drop held item", "minDamage": 3},
        {"outcome": "Crippled", "hpDivisor": 3, "approximate": True},
    ]
    assert by_target["Leg"]["thresholds"] == [
        {"outcome": "Knock prone", "minDamage": 3},
        {"outcome": "Crippled", "hpDivisor": 3, "approximate": True},
    ]
    # The Hand drops the item on ANY hit -- its cell prints no damage
    # condition where Arm and Leg both print ">= 3".
    assert by_target["Hand"]["thresholds"] == [
        {"outcome": "Drop held item"},
        {"outcome": "Crippled", "hpDivisor": 6},
    ]
    assert by_target["Neck"]["thresholds"] == [{"outcome": "Choke possible", "hpDivisor": 2}]
    assert by_target["Eye"]["thresholds"] == [{"outcome": "Blind", "hpDivisor": 10}]

    # And the four rows whose cells print no damage threshold at all. Each is
    # refused for its own reason, recorded in production Kotlin; none of them
    # may acquire a threshold here by resemblance.
    for target in ("Torso", "Face", "Skull", "Vitals"):
        assert by_target[target].get("thresholds", []) == [], target

    # The prose stays verbatim beside the structure, because it says things
    # the structure does not -- "unusable until healed", "bypasses head DR".
    assert all(r["effect"] for r in rows)


def test_an_outcome_name_is_closed_at_a_word_boundary():
    """Why OUTCOMES holds anchored regexes and not a prefix test.

    A bare `startswith` reads "Blindness or disfigurement at > HP/10" -- the
    Face cell's wording one re-extraction away -- as the Eye's definite
    "Blind", prices it, fires it, and leaves the raise-guard silent because it
    had found a name. That is the worst failure this parser has available: not
    a crash, not a dropped clause, but a confident wrong effect.

    The same closure separates "Knock prone" from "Knockdown", which share
    five letters and are the only two outcomes in the column that do.
    """
    assert extract_wounds.parse_thresholds("Blind at > HP/10", "Eye") == [
        {"outcome": "Blind", "hpDivisor": 10}
    ]
    with pytest.raises(ValueError, match="cannot name"):
        extract_wounds.parse_thresholds("Blindness or disfigurement at > HP/10", "Face")

    assert extract_wounds.parse_thresholds("Knock prone if damage >= 3", "Leg") == [
        {"outcome": "Knock prone", "minDamage": 3}
    ]
    # Knockdown names no threshold and must stay silent -- Shock records why.
    assert extract_wounds.parse_thresholds("Knockdown at -10", "Skull") == []


def test_the_hit_location_table_now_claims_the_rule_that_governs_it():
    """The seam, closed -- and the span that closes it stops where it should.

    2788 is the NEXT table's heading ("Optional -- Wounding Multipliers") and
    2789 is where `wounding_multipliers.json` begins, so a span that ran one
    line further would claim a heading it does not read and collide with its
    neighbour.
    """
    content = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"
    spans = json.loads((content / "hit_locations.json").read_text(encoding="utf-8"))["sourceLines"]["5"]
    claimed = {n for lo, hi in spans for n in range(lo, hi + 1)}
    assert {2785, 2786, 2787} <= claimed, sorted(claimed)[-6:]
    assert 2788 not in claimed, "the span now claims the next table's heading"

    others = json.loads((content / "wounding_multipliers.json").read_text(encoding="utf-8"))
    for lo, hi in others["sourceLines"]["5"]:
        assert not (claimed & set(range(lo, hi + 1))), (lo, hi)


def test_spirit_vision_section_c_is_a_swap_not_a_one_sided_error():
    """The other direction of the sweep, which nothing had run.

    `test_spirit_vision_records_the_spectator_disagreement` asks, of each row
    in 7.6's section C, whether the pathway's own block agrees. It cannot see
    the opposite question: is there a pathway whose block GRANTS Spirit Vision
    at Sequence 9 and which section C omits? There is -- Moon's Apothecary,
    whose block prints "Spirit Vision - Ether Body".

    So the table is not four-right-and-one-wrong. It is the right SIZE and the
    wrong MEMBERSHIP: five rows against five Sequence 9 grants, with Visionary
    listed and not granted, and Moon granted and not listed. A swap.

    Both halves are asserted, because a repair in either direction would
    otherwise pass: dropping the Visionary row leaves Moon still missing, and
    adding Moon leaves Visionary still wrong.
    """
    content = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"
    sv = json.loads((content / "spirit_vision.json").read_text(encoding="utf-8"))
    blocks = json.loads((content / "pathways.json").read_text(encoding="utf-8"))["entries"]

    granted = {b["pathway"] for b in blocks
               if b["sequence"] == 9 and extract_spirit_vision.GRANT.search(b["ability"] or "")}
    assert granted == {"Fool", "Darkness", "Death", "Hermit", "Moon"}, sorted(granted)

    listed = {r["name"].split("(")[1].rstrip(")").strip()
              for r in sv["pathwayDifferences"] if "(" in r["name"]}
    assert listed == {"Fool", "Hermit", "Visionary", "Darkness", "Death"}, sorted(listed)

    assert listed - granted == {"Visionary"}, "7.6 no longer lists a pathway that grants none"
    assert granted - listed == {"Moon"}, "7.6 no longer omits a pathway that grants it"
    assert len(listed) == len(granted), "the swap has become an imbalance; re-read section C"


def test_the_spirit_vision_grant_predicate_separates_three_classes():
    """Why the gate reads a LABEL and not the words "Spirit Vision".

    21 of 88 pathway blocks name Spirit Vision and they do not mean one thing.
    Three classes, each verified against the book's own prose:

      GRANT     the ability named as a label -- "Spirit Vision:",
                "Spirit Vision - Limited Form:", "Eyes of Mystery Prying (".
      UPGRADE   the same label inside `reinforcedAbilities`, which improves a
                grant an earlier rung already made.
      NEITHER   a DEFENCE against someone else's Spirit Vision (the Werewolf
                reveals only black hair; the Hypnotist's illusion "works even
                against Spirit Vision"), or a mention of what OTHER pathways
                can do (the Monster sees what "Pathways with strong Spirit
                Vision abilities cannot").

    A bare substring match grants the Werewolf, the Hypnotist and the Monster
    an ability the book gives them no part of. The label is what separates
    them, and this is the test that says the classification still holds.
    """
    content = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"
    blocks = json.loads((content / "pathways.json").read_text(encoding="utf-8"))["entries"]

    grants, upgrades, neither = set(), set(), set()
    for b in blocks:
        ability, reinforced = b["ability"] or "", b.get("reinforcedAbilities") or ""
        key = (b["pathway"], b["sequence"])
        if extract_spirit_vision.GRANT.search(ability):
            grants.add(key)
        elif extract_spirit_vision.GRANT.search(reinforced):
            upgrades.add(key)
        elif "Spirit Vision" in ability or "Spirit Vision" in reinforced:
            neither.add(key)

    assert grants == {
        ("Fool", 9), ("Darkness", 9), ("Death", 9), ("Hermit", 9), ("Moon", 9),
        ("Death", 8), ("Mother", 8), ("Mother", 7), ("Red Priest", 8),
    }, sorted(grants)
    assert neither == {
        ("Wheel of Fortune", 9), ("Chained", 7), ("Visionary", 6),
        ("Darkness", 6), ("Red Priest", 7),
    }, sorted(neither)
    assert len(grants) + len(upgrades) + len(neither) == 21

    # The invariant the gate rests on: an UPGRADE never stands alone. Every
    # one sits on a pathway that granted the ability at a HIGHER sequence
    # number, which the cumulative walk has already passed through. That is
    # why the gate reads grants only -- checking `reinforcedAbilities` too
    # would be a branch no data can reach, trusted later as though it meant
    # something.
    earliest = {}
    for pathway, sequence in grants:
        earliest[pathway] = max(earliest.get(pathway, -1), sequence)
    for pathway, sequence in upgrades:
        assert pathway in earliest, f"{pathway} upgrades Spirit Vision it was never granted"
        assert earliest[pathway] > sequence, (pathway, sequence, earliest[pathway])


def test_ether_body_awareness_is_two_different_things():
    """One name, two printings, opposite premises — and nothing may join them.

    Chapter 7.6's closing sentence lists `Ether Body Awareness` as the Seer's
    Special Form OF Spirit Vision. Chapter 3 prices `Ether Body Awareness` at
    5 points in `advantages.json` and defines it as knowing when something
    targets your soul **"even without Spirit Vision"** — a trait bought by
    characters who do not have the ability at all.

    A resolver joining the two on the name would hand every Seer a 5-point
    advantage they never bought, or gate a bought advantage behind a pathway.
    Both printings are pinned so the collision cannot be resolved by accident
    in either direction.
    """
    content = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"
    sv = json.loads((content / "spirit_vision.json").read_text(encoding="utf-8"))
    advantages = json.loads((content / "advantages.json").read_text(encoding="utf-8"))["entries"]

    seer = next(f for f in sv["specialForms"] if f["pathwayRank"] == "Seer")
    assert seer["form"] == "Ether Body Awareness"

    bought = next(a for a in advantages if a["name"] == "Ether Body Awareness")
    assert bought["cost"] == 5
    assert "even without Spirit Vision" in bought["notes"], (
        "the advantage no longer disclaims Spirit Vision; the two printings "
        "may no longer be distinct and the reading must be made again"
    )

    # Five forms, one per Sequence 9 rank, matching section C's five rows.
    assert [f["pathwayRank"] for f in sv["specialForms"]] == [
        r["name"].split(" (")[0] for r in sv["pathwayDifferences"]
    ]


def test_markdown_repair_restores_arrows_and_bullets():
    """markitdown's glyph mapping for this PDF is worse than pypdf's.

    It emits a bare `fi` ligature where the book prints an arrow, and
    `(cid:127)` where it prints a bullet. Both survive every byte-count and
    line-count completeness check -- "Status 1 fi +1" reads as ordinary text --
    so `extract_markdown.repair` fixes them and `main()` refuses to write a
    file where either shape remains. Measured on v6.9d: 18 arrows, 261 bullets.
    """
    import extract_markdown

    # Mid-line, the shape the first lookahead already caught.
    assert extract_markdown.repair("Status 1 fi +1 reaction") == "Status 1 \u2192 +1 reaction"
    # End of line: the arrow the first lookahead MISSED, because it required a
    # trailing space or paren and a wrapped line offers neither.
    assert extract_markdown.repair("soft liquor (wine, etc.) fi\nHT") == "soft liquor (wine, etc.) \u2192\nHT"
    assert extract_markdown.repair("(cid:127) FP fi 1 per 10 minutes") == "\u2022 FP \u2192 1 per 10 minutes"

    # The ligature inside a word is a real "fi", not an arrow, and must survive.
    assert extract_markdown.repair("20% efficiency") == "20% efficiency"
    assert extract_markdown.repair("classified") == "classified"


def test_markdown_rendering_loses_nothing_that_book_txt_has():
    """book.md is a SECOND rendering, never a replacement for book.txt.

    book.txt's global line numbers are this repo's citation anchor (coverage.py
    compares line sets; the tests above pin book lines by number), so the two
    files must both exist and book.md must not be missing content book.txt has.
    """
    md = OUT / "book.md"
    if not md.exists():
        pytest.skip("book.md not built; run tools/extract_markdown.py")

    normalise = lambda s: re.sub(r"\W+", "", s).lower()
    body = normalise(md.read_text(encoding="utf-8"))
    lines = [
        re.sub(r"^\s*\d+:\s?", "", line)
        for line in (OUT / "book.txt").read_text(encoding="utf-8").splitlines()
    ]
    missing = [l for l in lines if len(normalise(l)) >= 30 and normalise(l) not in body]
    assert missing == [], f"{len(missing)} book.txt lines absent from book.md"

    # The gain that justifies the second rendering: pdfplumber recovers ruled
    # tables as pipe rows where pypdf flattens them into prose.
    body_text = md.read_text(encoding="utf-8")
    rows = sum(1 for l in body_text.splitlines() if l.startswith("|"))
    assert rows > 1000, f"only {rows} table rows; the table recovery regressed"

    # normalise() above strips every non-word character, so it is BLIND to the
    # failure this file exists to prevent: markitdown substituting a glyph
    # rather than dropping a line. "Status 1 fi +1" survives the containment
    # check untouched. These three counts are the part that can see it.
    book_txt = (OUT / "book.txt").read_text(encoding="utf-8")
    assert body_text.count("→") == book_txt.count("→"), (
        "book.md and book.txt disagree on arrow count; either the repair "
        "missed one or it substituted a real standalone 'fi'"
    )
    assert "(cid:" not in body_text, "unmapped glyphs survived into book.md"
    assert body_text.count("•") > 200, "the bullet repair regressed"


@needs_pdf
def test_chapter_4_governs_spi_recovery_and_the_appendix_says_less():
    """Which printing of the SPI recovery rule the recorded refusal names.

    `Study.kt`'s KDoc cited *"Appendix A's SPI recovery"* as one of the two
    rules this app leaves unmodelled for want of a clock. Appendix A is the
    QUICK REFERENCE, and its row (9718-9724) is three cells: Rest, 1 SPI per
    hour, and the requirement. Chapter 4 p.34 prints the rule itself and
    carries two clauses the summary drops -- an exclusivity sentence and an
    interruption clause -- so the refusal named the summary and never
    mentioned the printing that governs. Claim status was never the same
    question as which printing governs (6dl), and neither is being handy to
    quote.

    Pinned so the reason recorded in `Pools.UNMODELLED` cannot drift off the
    sentence it rests on. RED-proved by editing 2340's text in `lines.json`.
    """
    byn = {l["n"]: l["text"] for l in json.loads((OUT / "lines.json").read_text(encoding="utf-8"))}

    rule = " ".join(byn[n] for n in range(2338, 2342))
    assert "recovers 1 SPI" in rule and "per hour" in rule, (
        f"p.34 no longer prints the recovery rate: {rule!r}"
    )
    assert "the standard and only recovery rate" in rule, (
        f"p.34's exclusivity clause is gone: {rule!r}"
    )
    assert "interrupts rest and halts recovery" in rule, (
        f"p.34's interruption clause is gone: {rule!r}"
    )

    summary = " ".join(byn[n] for n in range(9718, 9725))
    assert "1 SPI per hour" in summary, (
        f"Appendix A no longer prints the same rate: {summary!r}"
    )
    # A negative pin on two words, not on a meaning: a reworded summary
    # carrying the same two clauses would pass this. That is the reach a
    # substring check has, stated rather than assumed -- what it does catch
    # is the extraction moving the row or the book gaining the sentence.
    assert "only" not in summary and "halts" not in summary, (
        "Appendix A now carries a clause chapter 4 was cited for; the "
        f"refusal may name either printing: {summary!r}"
    )


@needs_pdf
def test_the_bard_restores_spi_outside_rest():
    """The narrower ability p.34's "only" does not reach.

    p.88's Sequence 9 Bard picks one Singing buff, and option (d) is
    *"Spiritual Recovery - allies recover 1 SPI at the song's end"*, upgraded
    at p.100 to two. That is not a second ORDINARY recovery rate competing
    with p.34's -- it is an active pathway ability with its own scope, and
    the parenthetical proves the scoping is deliberate: the Bard is excluded
    from their own effect.

    **This test pins the ability, never the priority.** Reading it as
    specific-beating-general -- the reading `Pools.UNMODELLED` records, on
    the model of the Potion Consumption Roll against `SuccessRoll` -- is this
    repo's; the book states no priority between the two printings, and no
    assertion below proves one.

    Recorded rather than repaired, and pinned here because `Pools.UNMODELLED`
    names it: a reader who greps "only recovery rate" must find the exception
    beside it, not conclude the book contradicts itself.
    """
    byn = {l["n"]: l["text"] for l in json.loads((OUT / "lines.json").read_text(encoding="utf-8"))}

    song = " ".join(byn[n] for n in range(5600, 5602))
    assert "Spiritual Recovery" in song, f"p.88's buff list lost the option: {song!r}"
    assert "recover 1 SPI at the song" in song, f"the buff no longer restores SPI: {song!r}"
    assert "Bard cannot benefit from this effect" in song, (
        f"the exclusion that makes this an ability rather than a rate is gone: {song!r}"
    )
    assert "restores 2 SPI instead of 1" in byn[6402], (
        f"p.100's upgrade no longer doubles it: {byn[6402]!r}"
    )


@needs_pdf
def test_chapter_4_prices_spirit_vision_a_second_time_and_says_less():
    """The other unclaimed line on p. 34, and why it disputes nothing.

    Ch. 7.6 line 4840 prints *"Activate by expending 1 SPI (1 SPI per minute
    to maintain)"* and `SpiritVisionDialog` bills the activation. Ch. 4's
    skill overview prints the maintenance rate ALONE. A second printing
    widens a rule only by saying something the first did not, so this one is
    recorded and not applied -- but the claim rests on the word `Activate`
    being absent from it, which is a fact about the book and can move.
    """
    byn = {l["n"]: l["text"] for l in json.loads((OUT / "lines.json").read_text(encoding="utf-8"))}

    overview = " ".join(byn[n] for n in range(2309, 2311))
    assert "Cost: 1 SPI per" in overview and "minute" in overview, (
        f"p.34 no longer prices Spirit Vision: {overview!r}"
    )
    assert "Activate" not in overview, (
        "p.34 now names an activation charge, so it is no longer a printing "
        f"that says strictly less than 7.6's: {overview!r}"
    )
    assert "Activate by expending 1 SPI" in byn[4840], (
        f"7.6's governing printing moved: {byn[4840]!r}"
    )
