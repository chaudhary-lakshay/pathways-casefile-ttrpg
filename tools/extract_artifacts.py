"""Extract the Sealed Artifact grade table (Ch. 6, p. 49, global 3298-3334).

v6.9d also printed four documented artifacts (the gauntlet, the coin, the
candle and the flagon, p. 49-53) with their abilities, SPI costs and HP
charges, and this tool wrote them to sealed_artifacts.json. v6.9i deletes the
whole "Unique Sealed Artifacts" section: p. 49 goes straight from the grade
table to Charms. So only the grades are left to extract, and the artifacts
file is gone with its source.

**Grade is a church ACCESS rule and a danger rating, never a usage
requirement.** The table's own fourth column is "Church Access Rules" and every
cell in it names an institutional RANK -- "Formal Nighthawk members and above",
"Diocesan Bishop or Nighthawk deacon and above". Its third column, "Power
Equivalent (Seq 5-7)", says how strong the ARTIFACT is; read as a requirement it
would lock a Sequence 9 character out of an artifact the book says arises from
the death of an out-of-control Beyonder. com.pathways.rules.SealedArtifacts
shows both columns and enforces neither.

Anchors: a grade row starts at a bare "Grade N"; within it the Power Equivalent
cell is the one holding "(Seq", and a line STARTING with "(" cannot open a cell,
so it belongs to the line above -- the unclosed-bracket rule extract_wounds.py
needs for the Vitals multiplier.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "6"
GRADE_RANGE = (3304, 3334)
INTRO_RANGE = (3298, 3307)

# This PDF prints list bullets as U+007F. See extract_defenses.py.
BULLET = "\x7f"
FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADER = {
    "Sealed Artifact Grades", "Grade", "Danger", "Power Equivalent",
    "Church Access Rules",
}

GRADE_CELL = re.compile(r"^Grade (\d)$")
SEQ_BAND = re.compile(r"\(Seq (\d+)[–-](\d+)")
EXPECTED_GRADES = ["Grade 3", "Grade 2", "Grade 1", "Grade 0"]
# The sentence that makes a grade a classification rather than a gate.
GRADE_BASIS = "based on danger, power, and sealing method"

def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    """(is_bullet, text) per line, headers and page furniture dropped."""
    out = []
    for n in range(start, end + 1):
        text = byn.get(n)
        if text is None:
            continue
        stripped = text.strip()
        if not stripped or FURNITURE.match(stripped):
            continue
        if stripped.startswith(BULLET):
            out.append((True, stripped.lstrip(BULLET).strip()))
        elif stripped not in HEADER:
            out.append((False, stripped))
    return out


def parse_grades(cells):
    """One row per bare "Grade N"; three cells inside it, split on "(Seq"."""
    starts = [i for i, c in enumerate(cells) if GRADE_CELL.fullmatch(c)]
    if not starts:
        raise ValueError("no grade rows found")
    rows = []
    for i, start in enumerate(starts):
        end = starts[i + 1] if i + 1 < len(starts) else len(cells)
        body = cells[start + 1:end]
        seq = [j for j, c in enumerate(body) if "(Seq" in c]
        if len(seq) != 1:
            raise ValueError(f"{cells[start]!r} has {len(seq)} power cells, want 1")
        at = seq[0]
        # A line opening with "(" is the tail of the line above, never a cell.
        first = at - 1 if body[at].startswith("(") else at
        if first <= 0:
            raise ValueError(f"{cells[start]!r} has no danger cell")
        band = SEQ_BAND.search(" ".join(body[first:at + 1]))
        if band is None:
            raise ValueError(f"{cells[start]!r} prints no sequence band")
        rows.append({
            "grade": int(GRADE_CELL.fullmatch(cells[start]).group(1)),
            "danger": " ".join(body[:first]),
            "powerEquivalent": " ".join(body[first:at + 1]),
            "sequenceLow": int(band.group(1)),
            "sequenceHigh": int(band.group(2)),
            "churchAccess": " ".join(body[at + 1:]),
        })
    return rows


def verify(grades, intro):
    got = [f"Grade {g['grade']}" for g in grades]
    if got != EXPECTED_GRADES:
        raise ValueError(f"grades changed: {got} != {EXPECTED_GRADES}")
    if GRADE_BASIS not in intro:
        raise ValueError(
            "the sentence saying a grade classifies danger rather than gating a "
            f"wielder is gone: {GRADE_BASIS!r}"
        )
    # Every access cell must still name a RANK. That is what makes the column an
    # institutional gate rather than a Sequence one.
    for g in grades:
        if not re.search(r"Nighthawk|Bishop|confidential", g["churchAccess"]):
            raise ValueError(f"Grade {g['grade']} access names no rank: {g}")


def write(name, entries, spans):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: spans}, "entries": entries}
    (OUT / f"{name}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def main():
    byn = load_lines()
    grades = parse_grades([t for b, t in stream(byn, *GRADE_RANGE) if not b])
    intro = " ".join(t for b, t in stream(byn, *INTRO_RANGE))
    verify(grades, intro)

    write("sealed_artifact_grades", grades, [list(GRADE_RANGE)])

    print(f"sealed_artifact_grades: {len(grades)} rows")
    for g in grades:
        print(f"  Grade {g['grade']}  {g['danger']:<20} "
              f"Seq {g['sequenceLow']}-{g['sequenceHigh']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
