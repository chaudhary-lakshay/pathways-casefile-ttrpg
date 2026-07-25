import json, subprocess, sys, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "build" / "extract"

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

def test_extract_sheets_produces_23_fixtures():
    subprocess.run([sys.executable, str(ROOT / "tools" / "extract_sheets.py")], check=True)
    assert len(sorted(FIX.glob("*.json"))) == 23

def test_occult_scholar_fixture_is_exact():
    subprocess.run([sys.executable, str(ROOT / "tools" / "extract_sheets.py")], check=True)
    f = json.loads((FIX / "occult_scholar.json").read_text(encoding="utf-8"))

    assert f["attributes"] == {"ST": 9, "DX": 9, "IQ": 10, "HT": 9}
    assert f["published"]["hp"] == 9
    assert f["published"]["will"] == 10
    assert f["published"]["dodge"] == 7
    assert f["published"]["totalSpent"] == 91

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
    f = json.loads((FIX / "beat_cop.json").read_text(encoding="utf-8"))
    assert f["published"]["bpTotal"] is None
    assert f["published"]["totalSpent"] is None

def test_coverage_reports_every_chapter():
    subprocess.run([sys.executable, str(ROOT / "tools" / "extract_pdf.py")], check=True)
    subprocess.run([sys.executable, str(ROOT / "tools" / "coverage.py")], capture_output=True, text=True)
    report = (OUT / "coverage.md").read_text(encoding="utf-8")
    assert "| 3 | Character Creation |" in report
    assert "| 9 | Sequence 9 Potion Effects |" in report
    # With no content extracted yet, every chapter is 0% claimed.
    assert "0.0%" in report

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
