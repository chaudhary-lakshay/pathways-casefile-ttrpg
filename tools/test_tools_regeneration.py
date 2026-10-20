"""Every generated file reproduces from its extractor, and the line cap. Split out of test_tools.py."""
import json, subprocess, sys, pathlib

import pytest

from testsupport import CONTENT, OUT, ROOT, needs_pdf


# --- 6ed: does a generated file still reproduce from its writer? -------------
#
# Every extractor writes its file and, until this section, nothing compared the
# file to what the extractor would write today. That is how `b1c8c4e` could
# hand-refile `pathways.json` without teaching `extract_pathways.py`: the next
# regeneration silently reverted it, and only reading that diff could see it
# (6ec).

import io, runpy

# Their inputs are the PDF and the spreadsheets, which live outside the repo.
RUN_BY_HAND = {"extract_pdf", "extract_markdown", "extract_sheets"}


def _captured_run(script, written):
    """Run a tool the way `py tools/x.py` does, with every `Path.write_text`
    captured, not written. As a SCRIPT because four extractors have no
    `main()` at all; returns its exit code.

    The tree is left untouched, and a sibling file an extractor reads is still
    the committed one -- which is the question: does the COMMITTED set
    reproduce itself.

    Stdout is a strict cp1252 stream because that is what a redirected stdout
    is on Windows, and a redirect is how every scripted run reaches these.
    """
    with pytest.MonkeyPatch.context() as patch:
        patch.setattr(
            pathlib.Path, "write_text",
            lambda self, data, *args, **kwargs: written.setdefault(self, []).append(data),
        )
        patch.setattr(sys, "stdout", io.TextIOWrapper(io.BytesIO(), encoding="cp1252"))
        patch.setattr(sys, "path", list(sys.path))    # extract_non_standard inserts
        try:
            runpy.run_path(str(script), run_name="__main__")
        except SystemExit as stop:
            return stop.code


@pytest.fixture(scope="module")
def regenerated():
    """Every content extractor, run once against the committed tree."""
    writers, failures = {}, []
    for script in sorted((ROOT / "tools").glob("extract_*.py")):
        tool = script.stem
        if tool in RUN_BY_HAND:
            continue
        written = {}
        try:
            code = _captured_run(script, written)
            if code:
                failures.append(f"{tool}: exit {code}")
        except Exception as error:      # one crash must not hide the other 39
            failures.append(f"{tool}: {type(error).__name__}: {error}")
        for path, texts in written.items():
            if path.parent != CONTENT:
                failures.append(f"{tool} wrote outside content: {path}")
            writers.setdefault(path.name, []).append((tool, texts[-1]))
    return writers, failures


@needs_pdf
def test_every_extractor_finishes_on_a_redirected_stdout(regenerated):
    """An extractor that writes its file and THEN dies reports a failure it
    did not have: `py a.py && py b.py` stops, and a reader who knows that a
    crashed extractor never rewrites its JSON (6dk) concludes the file is
    stale when it is not."""
    _, failures = regenerated
    assert failures == []


@needs_pdf
def test_every_content_file_is_what_exactly_one_extractor_writes_today(regenerated):
    """One comparison, four ways to fail it: a file no extractor writes (a
    hand-authored one), a file two extractors write, a file whose extractor
    would write something else today, and an extractor writing a file that is
    not there. The message maps each offending name to the tools that wrote it.
    """
    writers, _ = regenerated
    committed = {p.name: [p.read_text(encoding="utf-8")] for p in CONTENT.iterdir()}
    written = {name: [text for _, text in runs] for name, runs in writers.items()}
    differing = sorted(
        name for name in committed.keys() | written.keys()
        if committed.get(name) != written.get(name)
    )
    assert {name: [tool for tool, _ in writers.get(name, [])] for name in differing} == {}


@needs_pdf
def test_the_citation_anchor_is_what_coverage_writes_today():
    """`book.txt` is the file every `sourceLines` number is read against, and
    `lines.json` is the same numbering as data. Both must come from ONE walk
    of `pages.json`, or the numbers a person greps and the numbers an
    extractor indexes can part company with nothing to say so."""
    written = {}
    _captured_run(ROOT / "tools" / "coverage.py", written)   # exits 1 by design
    texts = {path.name: runs[-1] for path, runs in written.items()}
    for name in ("lines.json", "book.txt"):
        assert texts.get(name) == (OUT / name).read_text(encoding="utf-8"), name


def test_the_handover_stays_inside_its_own_size_rule():
    # CLAUDE.md's "Handover size and retention" paragraph caps it at 80,000
    # decoded characters. It reached the loader's 150 k limit three times
    # (6eb, 6eh, 2026-10-08) while that rule was prose only.
    size = len((ROOT / "CLAUDE.md").read_text(encoding="utf-8"))
    assert size <= 80_000, (
        f"CLAUDE.md is {size} chars: retire the oldest leads to docs/READING-ORDER.md"
    )


# --- the Natural Recovery rows (Ch. 5, p. 42) ------------------------------
#
# The HP recovery table was refused whole as "clocks". Its first row is also a
# ROLL -- "Natural Recovery (HT roll success)" -- and those three rows are what
# this reads. The other three methods and Regeneration stay out, by name.

import extract_recovery


def test_a_recovery_row_is_three_printed_cells():
    rows = extract_recovery.parse([
        "Natural Recovery (HT roll success)", "1 HP (2 HP w/ Very Rapid Healing)",
        "Per day; rest & food required",
        "Natural Recovery (20–29 HP)", "2× base rate", "Per day; scales proportionally",
    ])
    assert rows == [
        {"method": "Natural Recovery (HT roll success)",
         "recovered": "1 HP (2 HP w/ Very Rapid Healing)",
         "notes": "Per day; rest & food required"},
        {"method": "Natural Recovery (20–29 HP)", "recovered": "2× base rate",
         "notes": "Per day; scales proportionally"},
    ]


def test_a_ragged_recovery_table_is_reported_not_recut():
    """Cells are paired positionally, so a wrapped cell would shift every
    later row into the wrong column and still look like a table."""
    with pytest.raises(ValueError, match="not a multiple of 3"):
        extract_recovery.parse(["Natural Recovery (HT roll success)", "1 HP"])


def test_only_the_natural_recovery_rows_are_taken():
    """First Aid needs wound tracking, Physician and Healing are other rolls,
    and Regeneration is a rate. A widened range must fail, not ship them."""
    with pytest.raises(ValueError, match="methods changed"):
        extract_recovery.verify(extract_recovery.parse([
            "Natural Recovery (HT roll success)", "1 HP", "Per day",
            "First Aid", "1 HP + stops bleeding", "1 minute; once per wound",
        ]))


def test_committed_hp_recovery_matches_the_printed_rows():
    payload = json.loads((CONTENT / "hp_recovery.json").read_text(encoding="utf-8"))
    assert [e["method"] for e in payload["entries"]] == extract_recovery.EXPECTED
    assert payload["sourceLines"] == {"5": [[2927, 2935]]}
    assert "HT roll" in payload["entries"][0]["method"], "the row that makes this a roll"


# The 800-line cap (CLAUDE.md section 7), held as a ratchet. A file over it must
# be listed here; a listed file that drops under it must leave the list, so the
# list only shrinks. docs/SPLIT-PLAN.md is the plan for the rest.
LINE_CAP = 800
OVER_CAP = set()   # empty since split step 10; `{}` would be a dict


def _source_line_counts():
    listed = subprocess.run(
        ["git", "ls-files", "-co", "--exclude-standard", "*.kt", "*.py"],
        cwd=ROOT, capture_output=True, text=True, check=True,
    ).stdout.split()
    assert listed, "git listed no source files"
    return {
        p: len((ROOT / p).read_text(encoding="utf-8").splitlines())
        for p in listed if (ROOT / p).exists()
    }


def test_no_source_file_grows_past_the_line_cap_unlisted():
    counts = _source_line_counts()
    over = {p for p, n in counts.items() if n > LINE_CAP}
    assert over - OVER_CAP == set(), (
        f"over {LINE_CAP} lines and not in OVER_CAP: split it, don't list it: "
        + ", ".join(f"{p} ({counts[p]})" for p in sorted(over - OVER_CAP)))
    assert OVER_CAP - over == set(), (
        "under the cap now, so remove from OVER_CAP: " + ", ".join(sorted(OVER_CAP - over)))
