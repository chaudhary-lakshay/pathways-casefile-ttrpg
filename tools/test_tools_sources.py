"""The PDF and sheet extractors, coverage, pathways and the content loader. Split out of test_tools.py."""
import json, subprocess, sys

import pytest

import extract_pools

from testsupport import CONTENT, OUT, ROOT, needs_pdf, needs_sheets


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
    # EVERY chapter, which is what the name says. Until 6ee this ended with
    # `assert "0.0%" in report` under a comment that had been false since M1
    # ("with no content extracted yet, every chapter is 0% claimed") -- and
    # "100.0%" contains "0.0%", so it could not fail either.
    chapters = json.loads((OUT / "chapters.json").read_text(encoding="utf-8"))
    missing = [c["id"] for c in chapters if f"| {c['id']} | {c['title']} |" not in report]
    assert len(chapters) == 22 and missing == []

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


def test_a_stat_note_claims_its_lowercase_continuation():
    """A wrapped line is the middle of a sentence, so it starts lowercase.
    Measured over all 105 blocks this parser sees -- the 88 potion blocks and
    the 17 non-standard ones, because `extract_non_standard.py` calls the same
    function and a count of 88 would exclude the rows that could contradict
    it: 62 lines follow a stat gain line inside the Stat / Advantage section,
    60 are advantage NAMES and start with a capital, and the 2 that do not are
    both a note cut at its wrap."""
    stats, prose = extract_pathways.parse_stats(
        ["Basic Speed", "+0.25 — reflexes sharpen", "for precise timing"]
    )
    assert stats == [
        {
            "attr": "Basic Speed",
            "delta": 0.25,
            "note": "— reflexes sharpen for precise timing",
        }
    ]
    # and it LEAVES the prose: the wrap was never an advantage row
    assert prose == ""


def test_a_line_above_a_mid_page_chapter_heading_stays_with_the_chapter_before():
    """b1c8c4e taught coverage.py that a chapter starts at its HEADING, not at
    the top of the page the heading sits on, and hand-refiled pathways.json's
    spans to match. This extractor is the other site answering that same
    question and was never taught, so re-running it silently reverted the fix.
    Line 6167 is chapter 10's page furniture at the top of p. 97, above
    chapter 10's heading at 6217, and it belongs to chapter 9."""
    heading_of = {"9": 5416, "10": 6217, "11": 7043, "12": 8098}
    assert extract_pathways.chapter_at(6166, heading_of) == "9"
    assert extract_pathways.chapter_at(6167, heading_of) == "9"
    assert extract_pathways.chapter_at(6216, heading_of) == "9"
    assert extract_pathways.chapter_at(6217, heading_of) == "10"
    # before the first heading in scope: not this extractor's business
    assert extract_pathways.chapter_at(5415, heading_of) is None


def test_a_wrapped_parenthetical_is_not_cut_at_the_line_break():
    stats, prose = extract_pathways.parse_stats(
        ["IQ", "+1 (knowledge retention", "and learning)",
         "Eidetic Memory", "+5 to remember things"]
    )
    assert stats == [
        {"attr": "IQ", "delta": 1.0, "note": "(knowledge retention and learning)"}
    ]
    assert prose == "Eidetic Memory +5 to remember things"


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


def test_an_unresolved_grant_is_stored_on_its_block_verbatim():
    # Recorded, not guessed -- and not dropped: before this the three rows
    # reached no screen, so a Reader never saw their +3 (6gk).
    blocks = json.loads((CONTENT / "pathways.json").read_text(encoding="utf-8"))["entries"]
    stored = {(b["name"], line) for b in blocks for line in b.get("unresolvedSkills", [])}
    assert stored == {
        ("Reader", "Any one knowledge skill (player's choice)/IQ [Average] +3"),
        ("Savant", "Inventor!/IQ [Wildcard] +2"),
        ("Potions Professor", "Poison/IQ [Hard] +3"),
    }
    assert {line for _, line in stored} == extract_pathways.UNRESOLVED_GRANTS


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
        # ON PAGE 2, above chapter 10's heading: the ability text runs onto
        # the next chapter's first page, which is what lines 6167, 7041 and
        # 8096 do in the book. Bucketing by page files it under 10; it is 9's.
        {"n": 3, "page": 2, "text": "Spirit Vision: sees ghosts and spectres."},
        {"n": 4, "page": 2, "text": "Chapter 10: Sequence 8 Potion Effects"},
        {"n": 5, "page": 2, "text": "How to Read These Entries: read +N as Attribute + N."},
        {"n": 6, "page": 2, "text": "Sequence 8: Clown · Fool Pathway"},
        {"n": 7, "page": 2, "text": " Ability"},
        {"n": 8, "page": 2, "text": "Clown Agility: impossible acrobatics."},
    ]
    entries, spans, _ = extract_pathways.extract(
        lines, {"9": (1, 1), "10": (2, 2)}, {"9": 1, "10": 4}
    )

    assert entries[0]["ability"] == "Spirit Vision: sees ghosts and spectres."
    assert entries[0]["sourceLines"] == [1, 3]
    # the intro is left unclaimed rather than credited to the previous block,
    # and line 3 is claimed under 9 although it sits on chapter 10's PAGE
    assert spans == {"9": [[1, 3]], "10": [[6, 8]]}


def test_every_committed_span_agrees_with_the_heading_partition():
    """b1c8c4e taught `coverage.py` that a chapter starts at its HEADING and
    hand-refiled three content files. Nothing then checked that the
    extractors WRITING those files agree, and two of them did not: one
    bucketed by page and one had the chapter as a constant. A grep for
    `chapters.json` cannot see the constant, so this asks the data instead --
    every chapter key in every committed file, against the one partition.

    It is also the only thing that would catch `extract_pools.main()` being
    reverted to a hardcoded key: `chapter_spans` is unit-tested, and nothing
    else tests that `main` calls it."""
    heading_of = extract_pathways.heading_of_chapters(extract_pathways.load_lines())

    def disagreements(name, data):
        out = []
        for cid, spans in (data.get("sourceLines") or {}).items():
            for a, b in spans:
                for n in (a, b):
                    got = extract_pathways.chapter_at(n, heading_of)
                    if got != cid:
                        out.append(f"{name}: [{a}, {b}] filed under {cid!r}, "
                                   f"line {n} is {got!r}")
        return out

    # the checker can fire: this is pool_thresholds.json exactly as it stood
    # before b1c8c4e, and what `extract_pools` wrote again until 6ec
    assert disagreements("control", {"sourceLines": {"5": [[2346, 2356]]}})

    found = []
    for path in sorted(CONTENT.glob("*.json")):
        found += disagreements(path.name, json.loads(path.read_text(encoding="utf-8")))
    assert found == []


def test_the_two_pool_tables_are_filed_under_the_chapters_that_print_them():
    """`extract_pools` hardcoded CHAPTER = "5" and filed BOTH tables under
    Combat. The SPI ladder prints in chapter 4 (Spirituality, heading at line
    2247) and only the HP one is chapter 5's (heading 2361), so b1c8c4e had to
    refile lines 2346-2356 by hand -- and a re-run put them back. The chapter
    a line belongs to is asked of the headings now, at this site too."""
    spans = extract_pools.chapter_spans({"3": 443, "4": 2247, "5": 2361, "6": 2995})
    assert spans == {"4": [[2346, 2356]], "5": [[2697, 2708]]}
    # a range no heading precedes must raise: a None key writes JSON "null",
    # which is a chapter nobody can claim and nothing would report
    with pytest.raises(ValueError, match="no chapter heading precedes"):
        extract_pools.chapter_spans({"9": 9999})


def test_no_pathway_string_is_cut_mid_parenthetical():
    """A gain cell cut at its wrap leaves an unclosed "(" on the stat note and
    strands the ")" on whatever field swallowed the remainder. That is one
    sentence arriving as two wrong ones, and it is checkable without knowing
    which sentence: a stored string this extractor built should balance its
    own brackets. Read off the COMMITTED files, not from entries built in
    memory, because a crashed extractor leaves its JSON intact.

    What this does NOT catch is a parser regression on its own: breaking
    `parse_stats` leaves the committed file green until someone regenerates.
    That half belongs to test_a_stat_note_claims_its_lowercase_continuation,
    which is a unit test of the parser. This one covers the other half -- a
    wrap this fixture does not contain, arriving in a future re-extraction."""
    for name in ("pathways", "non_standard_pathways"):
        data = json.loads((CONTENT / f"{name}.json").read_text(encoding="utf-8"))
        for entry in data["entries"]:
            for field, value in entry.items():
                for text in (value if isinstance(value, list) else [value]):
                    for key, inner in (text.items() if isinstance(text, dict) else [(field, text)]):
                        if isinstance(inner, str) and inner.count("(") != inner.count(")"):
                            raise AssertionError(
                                f"{name}: {entry['pathway']} Seq {entry['sequence']} "
                                f"{key} has unbalanced brackets: {inner!r}"
                            )


def test_no_pathway_prose_field_opens_mid_sentence():
    """The advantages and drawbacks blobs are collected from whatever the stat
    parser did not claim, so a note's lost remainder lands at the FRONT of
    them. A prose field opening lowercase is that remainder."""
    for name in ("pathways", "non_standard_pathways"):
        data = json.loads((CONTENT / f"{name}.json").read_text(encoding="utf-8"))
        for entry in data["entries"]:
            for field in ("advantages", "drawbacks"):
                text = entry.get(field, "")
                assert not text[:1].islower(), (
                    f"{name}: {entry['pathway']} Seq {entry['sequence']} "
                    f"{field} opens mid-sentence: {text[:60]!r}"
                )


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
