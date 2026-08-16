"""Extract Chapter 8's Common Equipment table (p. 81-83, global 5062-5310).

WHAT THIS FILE IS FOR, AND WHAT IT REFUSES TO BE
------------------------------------------------
The table prints ~77 items, and 40-odd of them carry a mechanical clause in
the Notes column ("+1 to First Aid; 10 uses before restock", "-1 to
Lockpicking", "DR 1 on torso"). None of it reaches a roll: this app records
no possession, so nothing can say whether a character HAS the magnifying
glass.

So the notes cell is committed VERBATIM and nothing derives a modifier from
it. The only structured output is [skills], which exists to answer one
question -- "which of these rows should a Search roll show the player?" --
and is an index for discovery, never a rule. A structured `modifier` field
would be a number with no consumer, and the seed of every defect the design
review found: stacking two disguise kits, inverting a penalty aimed at the
observer, and telling a player that a row without a chip grants nothing.

A SIGNED CLAUSE, NOT A MENTION
------------------------------
"Lock picks (set)" says "Required for Lockpicking skill" and grants nothing.
A substring scan for a skill name would surface it on every Lockpicking roll
as though it helped. So a row joins a skill only through a SIGNED clause
whose target resolves, in the book's two printed forms:

    +1 to Search for fine details        (sign first)
    Navigation (Land) +1 when exploring  (skill first)

verify() pins the lock picks as resolving to nothing, so that predicate
cannot quietly loosen into a mention.

THE TARGET IS TRIMMED, THEN SPLIT -- IN THAT ORDER
--------------------------------------------------
A target runs to the end of its clause and carries the book's condition with
it ("Search for fine details"). CONDITION_SEPARATORS cut that away. Only
THEN is the remainder split on " & " and " or ", because the book joins two
skills to one bonus ("+2 to First Aid & Surgery") and offers a choice between
two ("+1 to Fortune-Telling or Psychology"). Splitting first would leave
"+1 to First Aid on burns & rashes" claiming a skill called "rashes".

Splitting is a claim about which rows to SHOW, never about what to add. Both
skills see the medicine chest; the player reads the printed sentence and the
GM decides.

FOUR TARGETS THE BOOK NAMES AND CHAPTER 3 DOES NOT
--------------------------------------------------
"spot", "fool officials", "infection-resistance rolls" and "ritual effects"
are rolls this book prices without printing them as skills. They resolve to
nothing and that is the honest answer, not a gap -- inventing a row for
"spot" (Observation? Search?) would be authoring content. UNPRINTED_TARGETS
pins them so a future matcher cannot guess one into existence.

"Forced Entry" and "Gather Information" are different: chapter 8 grants
bonuses to skills chapter 3's list never prints. CLAUDE.md 4.1 governs --
recorded, never repaired -- and ABSENT_FROM_CHAPTER_3 pins them.

THE FORTUNE SEAM IS THE BOOK'S, NOT THE PARSER'S
-------------------------------------------------
Chapter 8 spells it "Fortune-Telling" (three clauses: both tarot decks and
the crystal ball). Chapter 3's skill list prints "Fortune Telling", and
skills.json holds that. So the tarot decks join nothing, and the crystal
ball joins only Psychology, the other half of its "or".

That is a disagreement inside one book, not an extraction artefact, and 6by's
rule applies: evidence per spelling, never a normaliser. A hyphen-insensitive
match here would silently decide the book meant one skill, on no evidence
beyond resemblance.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "8"
EQUIPMENT_RANGE = (5062, 5310)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADING = "Common Equipment"
INTRO_PREFIX = "The following table lists common equipment"
INTRO_WRAP_PREFIX = "markets; remote villages"
COLUMN_HEADERS = {"Item", "Cost", "Notes"}

# The four subheadings the table groups its rows under, in printed order.
CATEGORIES = [
    "General Tools", "Medical & Herbal", "Clothing & Protective",
    "Specialized Equipment",
]

# Every price form this table prints: "3s", "2d", "£1", "£1 10s", "5s-£5"
# (an en dash), "5Gp / 3 pts". The last is a row priced in EITHER money or
# character points, which chapter 8's licence note calls equivalent.
COST_CELL = re.compile(
    r"^(?:£\d+(?:\s+\d+s)?|\d+s(?:–£\d+)?|\d+d|\d+Gp\s*/\s*\d+\s*pts)$"
)

# "+1 to Search for fine details" -- the clause runs to a semicolon or the
# end of the cell, and the condition is trimmed off the target below.
CLAUSE_SIGN_FIRST = re.compile(r"([+-]\d+)\s+to\s+([^;]+)")
# "Navigation (Land) +1 when exploring" -- capitalised words only, so
# "Stay awake for +2 hrs" cannot offer "for" as a skill.
CLAUSE_SKILL_FIRST = re.compile(
    r"\b([A-Z][A-Za-z'/-]*(?:\s+[A-Z][A-Za-z'/-]*)*(?:\s+\([A-Za-z ]+\))?)\s+([+-]\d+)\b"
)
# A parenthesised target names a specialization: "Navigation (Land)".
SPECIALIZED = re.compile(r"^(.+?)\s*\(([^)]+)\)$")

# What the book appends to a target to scope it. Cut at the FIRST of these.
CONDITION_SEPARATORS = (
    " when ", " for ", " in ", " on ", " without ", " vs", " at ", " from ",
    " if ",
)
TARGET_JOINERS = (" & ", " or ")

# Rolls this book prices without printing them as skills anywhere.
UNPRINTED_TARGETS = {
    "spot", "fool officials", "infection-resistance rolls", "ritual effects",
}
# Skills chapter 8 grants bonuses to that chapter 3's list never prints.
# A 4.1 disagreement: recorded here, never resolved by inventing a row.
ABSENT_FROM_CHAPTER_3 = {"Forced Entry", "Gather Information"}

# Counted from the printed table: 27 general tools, 15 medical, 11 clothing,
# 24 specialized. Exact rather than a floor -- see verify().
EXPECTED_ROWS = 77

# Every clause in the table that joins two skills to one bonus, or offers a
# choice between them. Pinned as a CLOSED set, because "&" and "or" are also
# ordinary English: splitting on them is safe only while every occurrence is
# the book's two-skill shorthand. A fourth one is a sentence a human must
# read before the splitter is allowed to have it.
# Two of them join, one offers a choice, and the fourth is PROSE that the
# condition-trim disarms before the splitter sees it -- which is the whole
# reason trimming happens first. This set was hand-written with three
# members and the guard found the fourth on its first run.
JOINED_CLAUSES = {
    # Genuine: one bonus, two skills.
    "Field hospital; +2 to First Aid & Surgery; 50 doses",
    "Spiritual focus; +1 to Ritualistic Magic & Religious Ritual",
    # Genuine: a choice between two.
    "Fortune-telling prop; +1 to Fortune-Telling or Psychology",
    # Prose. "burns & rashes" is the condition, cut at " on " before any
    # split, so this resolves to First Aid alone and must stay that way.
    "+1 to First Aid on burns & rashes",
}

# Rows whose join is pinned, because each one is a different way the matcher
# could go wrong. Values are (name, specialization) pairs.
PINNED_JOINS = {
    "First aid kit": [("First Aid", None)],
    "Scalpel set": [("Surgery", None)],
    "Magnifying glass": [("Search", None)],
    "Compass (brass)": [("Navigation", "Land")],
    "Portable medicine chest": [("First Aid", None), ("Surgery", None)],
    "Crystal ball (glass)": [("Psychology", None)],
    # A mention with no signed clause. This row is the predicate's guard.
    "Lock picks (set)": [],
    # Chapter 8 hyphenates; chapter 3 does not. The book's own seam.
    "Tarot deck (common)": [],
    "Tarot deck (quality)": [],
    # Chapter 3 prints neither skill.
    "Heavy boots (leather)": [],
    "Press badge": [],
}


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    """Table cells in order, with the heading, intro and furniture dropped."""
    out = []
    for n in range(start, end + 1):
        text = (byn.get(n) or "").strip()
        if not text or FURNITURE.match(text) or text in COLUMN_HEADERS:
            continue
        if text == HEADING or text.startswith(INTRO_PREFIX):
            continue
        # The intro's second line, which wraps and so cannot be prefixed.
        if text.startswith(INTRO_WRAP_PREFIX):
            continue
        out.append(text)
    return out


def tag_categories(cells):
    """(category, cell) for each table cell; the subheadings themselves go."""
    tagged, current = [], None
    for cell in cells:
        if cell in CATEGORIES:
            current = cell
            continue
        if current is None:
            raise ValueError(f"a row cell {cell!r} appears before any category")
        tagged.append((current, cell))
    return tagged


def skill_index():
    """Every (name, specialization) chapter 3's skill list prints."""
    path = OUT / "skills.json"
    entries = json.loads(path.read_text(encoding="utf-8"))["entries"]
    return {(e["name"], e.get("specialization")) for e in entries}


def trim_condition(target):
    """Cut the book's scoping clause off a target, at the earliest separator."""
    cut = len(target)
    for sep in CONDITION_SEPARATORS:
        found = target.find(sep)
        if found != -1:
            cut = min(cut, found)
    return target[:cut].strip().rstrip(".,")


def split_targets(target):
    """One bonus may name two skills ("First Aid & Surgery") or offer a choice."""
    parts = [target]
    for joiner in TARGET_JOINERS:
        parts = [p for part in parts for p in part.split(joiner)]
    return [p.strip() for p in parts if p.strip()]


def as_key(target):
    """"Navigation (Land)" -> ("Navigation", "Land"); otherwise no spec."""
    m = SPECIALIZED.fullmatch(target)
    return (m.group(1).strip(), m.group(2).strip()) if m else (target, None)


def clause_targets(notes):
    """Every signed clause's target, trimmed and split, in printed order."""
    found = []
    for _, raw in CLAUSE_SIGN_FIRST.findall(notes):
        found.extend(split_targets(trim_condition(raw)))
    for raw, _ in CLAUSE_SKILL_FIRST.findall(notes):
        found.extend(split_targets(trim_condition(raw)))
    return found


def resolve_skills(notes, index):
    """The clause targets chapter 3's list actually prints. Order preserved."""
    out = []
    for target in clause_targets(notes):
        key = as_key(target)
        already = [(s["name"], s["specialization"]) for s in out]
        if key in index and key not in already:
            out.append({"name": key[0], "specialization": key[1]})
    return out


def parse(tagged, index):
    """One row per cost cell: the cell before it names it, the cells after it
    are its note, joined -- which is why a wrapped note needs no rule."""
    cells = [c for _, c in tagged]
    cats = [c for c, _ in tagged]
    starts = [i for i, c in enumerate(cells) if COST_CELL.fullmatch(c)]
    if not starts:
        raise ValueError("no cost cell found; the table's shape changed")

    rows = []
    for i, start in enumerate(starts):
        end = starts[i + 1] - 1 if i + 1 < len(starts) else len(cells)
        if start == 0 or COST_CELL.fullmatch(cells[start - 1]):
            raise ValueError(f"the cost cell {cells[start]!r} has no item before it")
        notes = " ".join(cells[start + 1:end])
        rows.append({
            "name": cells[start - 1],
            "cost": cells[start],
            "category": cats[start],
            "notes": notes,
            "skills": resolve_skills(notes, index),
        })
    return rows


def verify(rows, index):
    # EXACT, not a floor. A lower bound cannot see the failure that matters:
    # a note cell whose text happens to fullmatch a price form would be read
    # as a new row's cost, stealing the rest of the real row's note and
    # fabricating a row whose "name" is that note's last cell. That ADDS a
    # row, so only an exact count catches it.
    if len(rows) != EXPECTED_ROWS:
        raise ValueError(
            f"the equipment table is {len(rows)} rows and was {EXPECTED_ROWS}. "
            "Either the book changed, or a note cell was read as a price"
        )

    for row in rows:
        for column in ("name", "cost", "notes"):
            if not row[column]:
                raise ValueError(f"{row['name']!r} has an empty {column}: {row}")

    seen = list(dict.fromkeys(r["category"] for r in rows))
    if seen != CATEGORIES:
        raise ValueError(f"the table's categories changed:\n  now: {seen}\n  was: {CATEGORIES}")

    by_name = {r["name"]: r for r in rows}
    for name, expected in PINNED_JOINS.items():
        row = by_name.get(name)
        if row is None:
            raise ValueError(f"the row {name!r} is gone from the table")
        got = [(s["name"], s["specialization"]) for s in row["skills"]]
        if got != expected:
            raise ValueError(
                f"{name!r} now joins {got} and used to join {expected}. That is "
                "a change in the book or in the matcher, and either is a finding"
            )

    # The splitter is only safe while every "&"/"or" is the book's two-skill
    # shorthand rather than prose. If a new clause joins with either word, a
    # half of it could resolve to a real skill the sentence never meant.
    joined_clauses = {
        r["notes"] for r in rows
        if any(j in r["notes"] for j in TARGET_JOINERS) and r["skills"]
    }
    if joined_clauses != JOINED_CLAUSES:
        raise ValueError(
            "the set of clauses joining two skills changed; read the new one "
            "before trusting the split.\n"
            f"  now: {sorted(joined_clauses)}\n"
            f"  was: {sorted(JOINED_CLAUSES)}"
        )

    # No target the book prices without printing may be guessed into a skill.
    joined = {s["name"] for r in rows for s in r["skills"]}
    guessed = joined & (UNPRINTED_TARGETS | ABSENT_FROM_CHAPTER_3)
    if guessed:
        raise ValueError(
            f"{sorted(guessed)} resolved to a skill. The book prices these "
            "rolls without printing them in chapter 3's list, so a match here "
            "is a row someone invented"
        )
    for absent in ABSENT_FROM_CHAPTER_3:
        if (absent, None) in index:
            raise ValueError(
                f"chapter 3 now prints {absent!r}, so chapter 8's bonus is no "
                "longer a disagreement and this exclusion is stale"
            )

    # The seam is the book's: chapter 8 hyphenates, chapter 3 does not.
    if ("Fortune Telling", None) not in index:
        raise ValueError("chapter 3 no longer prints 'Fortune Telling'")
    if ("Fortune-Telling", None) in index:
        raise ValueError(
            "chapter 3 now prints the hyphenated spelling too, so the tarot "
            "rows are no longer a recorded disagreement"
        )


def main():
    index = skill_index()
    tagged = tag_categories(stream(load_lines(), *EQUIPMENT_RANGE))
    rows = parse(tagged, index)
    verify(rows, index)

    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: [list(EQUIPMENT_RANGE)]}, "entries": rows}
    (OUT / "equipment.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )

    joined = [r for r in rows if r["skills"]]
    print(f"equipment: {len(rows)} rows, {len(joined)} joining a printed skill")
    for row in joined:
        names = ", ".join(
            s["name"] + (f" ({s['specialization']})" if s["specialization"] else "")
            for s in row["skills"]
        )
        print(f"  {row['name']:32} {row['cost']:<14} {names}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
