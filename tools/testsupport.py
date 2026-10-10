"""Paths and skip marks every tools test reads, split out of test_tools.py."""
import pathlib

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

CONTENT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"
