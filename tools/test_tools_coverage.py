"""Chapter boundaries, the coverage filter and the glossary. Split out of test_tools.py."""
import json, re, subprocess, sys

import pytest

import extract_glossary

from testsupport import OUT, ROOT, needs_pdf


@needs_pdf
def test_chapter_boundaries_fall_on_the_heading_not_the_page():
    """A chapter partition by PAGE puts 57 mechanical lines in the wrong chapter.

    `chapters.json` carries a startPage per chapter, so `coverage.py` used to
    assign every line on that page to the chapter -- including the lines above
    the heading, which belong to the chapter before it. That is invisible for
    most chapters (a page break usually falls between them) and wrong wherever
    the book starts a chapter mid-page.

    Appendix A is the case that proves it. Its heading is on p. 146 (v6.9d:
    159), Appendix B's on p. 147, so the page partition gave the WHOLE of p. 147
    to B. But the lines -- the Spiritual Skills table, the CoR reference, the SPI
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
    assert byn[8890].strip().startswith("Appendix A:")
    assert byn[9003].strip().startswith("Appendix B:")

    # Between them lies content that is Appendix A's, on Appendix B's page.
    pageof = {r["n"]: r["page"] for r in lines}
    assert pageof[9000] == 147, "the Common Modifiers table prints on p.147"
    assert pageof[8890] == 146, "but Appendix A's heading is on p.146"
    assert pageof[9003] == 147, "and Appendix B's heading shares p.147"
    assert byn[9000].strip() == "+2 (if they succeed first)"

    # And the content file that claims them names A, which the heading agrees
    # with and the page range does not.
    claim = json.loads((ROOT / "core" / "rules" / "src" / "main" / "resources"
                        / "content" / "common_modifiers.json")
                       .read_text(encoding="utf-8"))
    assert claim["sourceLines"] == {"A": [[8980, 9000]]}
    assert 8890 < 8980 and 9000 < 9003, "the claim sits inside A's heading span"


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


@needs_pdf
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


@needs_pdf
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
    assert byn[9411].strip() == "Glossary of Terms"
    assert not byn[9411].strip().startswith("Appendix")
    # And the form every other section does use, for contrast.
    assert byn[9095].strip().startswith("Appendix C:")
    assert byn[3140].strip().startswith("Chapter 6:")


def _glossary_lines():
    return {r["n"]: r["text"] for r in
            json.loads((OUT / "lines.json").read_text(encoding="utf-8"))}


@needs_pdf
def test_the_examples_exclusion_rests_on_typography_this_pdf_destroyed():
    """Why one line is excluded BY NAME rather than by a rule.

    `extract_glossary.NOT_TERMS` drops "Examples", and the reason is that the
    book's own discriminator is unavailable: line 9412 prints "Terms in
    italics are game mechanics; bold terms are setting concepts", and this
    PDF is flattened, so neither weight survives extraction. The exclusion is
    therefore a claim about the EXTRACTION, not about the book -- and it
    expires the day a re-extraction preserves formatting.

    Both halves are pinned so that day is loud: the sentence that names the
    missing discriminator, and the line it would have discriminated.
    """
    byn = _glossary_lines()
    assert "Terms in italics are game mechanics" in byn[9412]
    assert byn[9413].strip() == "bold terms are setting concepts."
    # The line that satisfies every structural property a term has.
    assert byn[9448].strip() == "Examples: Fool Pathway, Error Pathway, Darkness Pathway."
    assert extract_glossary.TERM.match(byn[9448].strip()), (
        "Examples no longer parses as a term, so NOT_TERMS may be dead weight"
    )


@needs_pdf
def test_the_full_stop_is_what_keeps_prose_out_of_the_glossary_terms():
    """The load-bearing character in TERM, measured rather than asserted.

    TERM's comment used to credit the length bound. It does not: relax the
    bound and nothing moves. What keeps a mid-sentence colon out is the full
    stop the character class omits -- line 9427 ends
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


@needs_pdf
def test_chapter_G_reads_100_percent_off_a_denominator_of_seven_lines():
    """What "G 100.0%" does and does not say.

    G's MECHANICAL filter matches SEVEN lines in the whole chapter -- HP,
    Spirit Vision's cost, the two universal-ability rolls, and 3d6. The rest
    of the glossary is prose, and the back matter below it is prose too, so
    nothing there can ever be counted as missing. The percentage is true and
    nearly weightless: it says seven lines are claimed, not that the chapter
    was read. `sourceLines` (9411-9474) is the honest statement of what was
    read, and it deliberately stops before the back matter.
    """
    byn = _glossary_lines()
    import coverage as coverage_tool

    in_glossary = [n for n in range(9411, 9475) if byn.get(n, "").strip()]
    mechanical = [n for n in in_glossary if coverage_tool.MECHANICAL.search(byn[n])]
    assert mechanical == [9440, 9460, 9461, 9462, 9464, 9465, 9469], mechanical
    assert len(in_glossary) == 64, len(in_glossary)

    back = [n for n in range(9475, 9512) if byn.get(n, "").strip()]
    assert back, "the back matter moved; G's page range no longer runs past 9474"
    assert not [n for n in back if coverage_tool.MECHANICAL.search(byn[n])]
    assert extract_glossary.GLOSSARY_RANGE[1] < back[0]


@needs_pdf
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
        # "the faster you digest..." is Acting Method's continuation (9415).
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
    partition exists to fix -- Appendix A is declared 146-146 and its content
    runs onto p.147 -- so a correct number sat next to a page range it had not
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
    assert (int(row.group(1)), int(row.group(2))) == (146, 147), (
        f"Appendix A measured over pages {row.group(1)}-{row.group(2)}; its "
        f"content runs onto p.147 and the column must say so"
    )

    # And chapters.json still declares the narrower range, which is what makes
    # this a real difference rather than a restatement.
    chapters = json.loads((ROOT / "build" / "extract" / "chapters.json")
                          .read_text(encoding="utf-8"))
    appendix_a = next(c for c in chapters if c["id"] == "A")
    assert appendix_a["startPage"] == 146 and appendix_a["endPage"] == 146


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


@needs_pdf
def test_spirit_vision_prints_no_roll_table_and_no_spectator_row():
    """v6.9d's guide had a five-row reading table (with the book's only bare
    "Critical" label) and a Section C row for the Spectator that disagreed
    with the Spectator's own block. v6.9i prints neither: no roll, and three
    Section C rows. Both old records went with their source."""
    d = json.loads((ROOT / "core" / "rules" / "src" / "main" / "resources"
                    / "content" / "spirit_vision.json").read_text(encoding="utf-8"))
    assert "reading" not in d
    assert not any(r["name"].startswith(("Spectator", "Seer")) for r in d["pathwayDifferences"])
    book = (ROOT / "build" / "extract" / "book.txt").read_text(encoding="utf-8", errors="replace")
    assert re.findall(r"(?m)^(\d+): Critical$", book) == []


@needs_pdf
def test_the_crippling_paragraph_is_invisible_to_the_coverage_filter():
    """The rule no coverage number has ever reported missing.

    Lines 2869-2871 (v6.9d: 2785-2787) govern the entire `Special Effect on Hit` column -- what
    crippling IS, what it costs, and how it ends -- and they sit in a THREE
    LINE seam between two claimed spans. `hit_locations.json` has claimed it
    since 6di, and it is still invisible to the filter.

    That alone would be 6ba's gap. What makes it worse is that the paragraph
    matches NEITHER the old MECHANICAL filter nor the widened one, so it was
    never in any denominator: its numbers are a fraction glyph, a bare "1",
    and "12+" -- and the filter's signed-number rule wants the sign FIRST.

    Both halves are pinned. If a future filter learns to see these lines the
    assertion fails loudly, which is the right time to re-read this test.
    """
    import coverage as coverage_tool
    byn = _glossary_lines()

    assert byn[2869].strip().startswith("Crippling: A limb or extremity struck")
    assert "First Aid (skill 12+) removes permanent" in byn[2870]
    assert byn[2871].strip() == "damage risk."
    for n in (2869, 2870, 2871):
        assert not coverage_tool.MECHANICAL.search(byn[n]), (
            f"line {n} is now visible to the coverage filter; it was invisible "
            f"to both the narrow and the widened form, which is why nothing "
            f"ever reported it unclaimed"
        )
