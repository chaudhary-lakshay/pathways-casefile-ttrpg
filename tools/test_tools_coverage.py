"""Chapter boundaries, the coverage filter and the glossary. Split out of test_tools.py."""
import json, re, subprocess, sys

import pytest

import extract_glossary

from testsupport import OUT, ROOT, needs_pdf


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
    #
    # Until 6ed this asserted `"\nCritical\n" not in book`, which could not
    # fire: every line of book.txt opens with its own number. It also read
    # backwards -- the bare label IS printed, exactly once, and that one line
    # is this table's.
    book = (ROOT / "build" / "extract" / "book.txt").read_text(encoding="utf-8", errors="replace")
    assert book.count("Critical Success") > 10
    bare = [int(n) for n in re.findall(r"(?m)^(\d+): Critical$", book)]
    assert bare == [4850]
    (first, last), = d["sourceLines"]["7.6"]
    assert first <= bare[0] <= last


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
