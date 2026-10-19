"""Extract the Languages section (Ch. 3, p. 14-15, global 689-767).

TWO PROFICIENCY TABLES THAT PRINT THE SAME THREE ROW NAMES
----------------------------------------------------------
Native / Accented / Broken appear twice, once under Common Languages (6/4/2
pts) and once under Mystical (30/20/10). Nothing in a row tells you which
table it came from -- only the section header above it does. A parser keyed on
the row NAME merges them and prices a mystical Native at 6 points, which is a
fifth of what the book charges. The two tables are therefore read as two
blocks, and verify() fails if either stops having exactly three rows.

The mystical table is also the only one that says anything about rituals, and
that is the half with a caller waiting: RitualResolution.

TWO LANGUAGE LISTS PRINTED IN DIFFERENT STYLES
----------------------------------------------
The common list's notes column is a dash-separated phrase with no full stop
("Loen Kingdom -- the lingua franca of the Northern Continent"). The mystical
one's is two or three prose sentences. So "accumulate until a full stop" reads
one and mis-cuts the other, and there is no single cell shape covering both.

What both share is that a language NAME is short and its note is long, so a row
starts at a short line whose next line is a long one. That is a shape
heuristic, which this repo has been burned by before (the maneuver table, see
CLAUDE.md 6i), so it is pinned hard: EXPECTED_COMMON and EXPECTED_MYSTICAL name
all twelve, and a mis-cut fails the extract instead of shifting every row after
it. The wrap that makes the naive rules fail is "similar" -- seven characters,
a continuation of Balam's note, and short enough to look exactly like a name.

WHAT CHAPTER 3'S TRAIT LIST SAYS ABOUT THIS SECTION
---------------------------------------------------
Unlike Appearance, whose levels the trait lists print zero times, this section
is CROSS-REFERENCED by them: "Language (specify) / varies / ... See the
Languages section for cost tables". That pointer has been sitting in
advantages.json since M0. verify() asserts it is still there, because it is
the only thing connecting a billable trait to these numbers.

Three disagreements between those rows and this section are recorded, never
repaired -- see verify().

WHAT IS DELIBERATELY NOT EXTRACTED
----------------------------------
The Appearance table below it (its own file) and the mundane advantage tables
above. The "Spoken + Written" halving and the Language Talent halving are kept
as the sentences that print them rather than as arithmetic: no caller buys a
language yet, and a cost calculator with no purchaser would be a rule nobody
tests.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "3"
LANGUAGES_RANGE = (689, 767)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
COMMON_HEADER = "Common Languages"
MYSTICAL_HEADER = "Mystical Languages"
TABLE_HEADERS = {
    "Languages", "Language", "Region / Origin", "Origin / Notes",
    "Proficiency", "Cost", "Effect on Skill Rolls",
}
COST_CELL = re.compile(r"^(\d+)\s*pts?$")
# The closing footnote prices traits inline; no effect cell brackets a cost.
FOOTNOTE = re.compile(r"\(\d+\s*pts?\)")

# A name is short and its note is long. Both bounds sit in the wide gap
# between the longest name printed (15, "Jotun (Giant's)") and the shortest
# note (48). "similar" -- Balam's seven-character wrap -- is short too, which
# is why the NEXT line has to be long as well.
NAME_MAX = 20
NOTE_MIN = 40

EXPECTED_COMMON = ["Loen", "Feysac", "Intis", "Balam (Dutan)", "Highland", "Rorsted"]
EXPECTED_MYSTICAL = [
    "Ancient Hermes", "Hermes", "Elvish", "Dragonese",
    "Jotun (Giant's)", "Ancient Feysac",
]
EXPECTED_PROFICIENCIES = ["Native", "Accented", "Broken"]
COMMON_COSTS = [6, 4, 2]
MYSTICAL_COSTS = [30, 20, 10]

# Sentences that must survive, because each carries a rule this extract does
# NOT turn into arithmetic.
HALVING_PHRASE = "Language Talent halves all costs"
SPOKEN_WRITTEN_PHRASE = "Spoken-only or written-only costs half"
MULTILINGUAL_PHRASE = "it does not apply to mystical languages"

# The trait row that points AT this section. Without it these numbers price
# nothing, because no other row in chapter 3 costs a language.
CROSS_REFERENCE = "Language (specify)"

# Chapter 3's trait list and chapter 3's Languages section disagree three
# times. All three are recorded, never repaired (CLAUDE.md 4.1).
DEAD_LANGUAGE_TRAIT = "Dead Language Fluency"
UNPRINTED_LANGUAGE = "Ancient Loen"


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    """Section lines in order, furniture and bare table headers dropped."""
    out = []
    for n in range(start, end + 1):
        text = (byn.get(n) or "").strip()
        if not text or FURNITURE.match(text) or text in TABLE_HEADERS:
            continue
        out.append(text)
    return out


def split_sections(cells):
    """The two language blocks, each ending at its own proficiency table.

    The section headers are the only thing telling a mystical Native from a
    common one, so they are the split rather than a row count.
    """
    try:
        common_at = cells.index(COMMON_HEADER)
        mystical_at = cells.index(MYSTICAL_HEADER)
    except ValueError as exc:
        raise ValueError(f"a Languages section header is gone: {exc}")
    if common_at > mystical_at:
        raise ValueError("the two sections swapped order; the split would mislabel every row")
    return cells[common_at + 1:mystical_at], cells[mystical_at + 1:]


def split_at_proficiencies(block):
    """A block's language rows and its proficiency rows.

    The proficiency table starts at the first row name with a cost cell after
    it -- the table's own structure, rather than a line count that would move
    the moment a note wrapped differently.
    """
    for i in range(len(block) - 1):
        if block[i] in EXPECTED_PROFICIENCIES and COST_CELL.fullmatch(block[i + 1]):
            return block[:i], block[i:]
    raise ValueError("no proficiency row with a cost after it; the table changed")


def is_name_at(cells, i):
    """Whether cells[i] starts a row: a short line whose note line is long."""
    return (
        len(cells[i]) <= NAME_MAX
        and i + 1 < len(cells)
        and len(cells[i + 1]) >= NOTE_MIN
    )


def parse_languages(cells, kind):
    """Rows of name plus note, cut where a short line precedes a long one.

    Leading cells are dropped until the first name, because the Mystical
    section prints three lines of intro prose between its header and its
    table and the last of them ("resonance.") is short enough to look like a
    name on its own. Anchoring on the table's first real row uses the table's
    own structure rather than guessing where the prose stopped -- the same
    move extract_appearance.py makes with its first cost cell. Nothing is
    silently lost: verify() names all twelve languages.
    """
    start = next((i for i in range(len(cells)) if is_name_at(cells, i)), None)
    if start is None:
        raise ValueError(f"{kind}: no language name in {len(cells)} cells")
    rows, i = [], start
    while i < len(cells):
        if not is_name_at(cells, i):
            rows[-1]["notes"] += " " + cells[i]
            i += 1
            continue
        rows.append({"type": kind, "name": cells[i], "notes": cells[i + 1]})
        i += 2
    return rows


def parse_proficiencies(cells, kind):
    """Name, cost, then every line up to the next name as the effect cell."""
    rows, i = [], 0
    while i < len(cells):
        name = cells[i]
        if name not in EXPECTED_PROFICIENCIES:
            raise ValueError(f"{kind}: {name!r} is not a printed proficiency")
        cost = COST_CELL.fullmatch(cells[i + 1])
        if not cost:
            raise ValueError(f"{kind}: {name} has no cost cell, found {cells[i + 1]!r}")
        effect, i = [], i + 2
        while i < len(cells) and cells[i] not in EXPECTED_PROFICIENCIES:
            effect.append(cells[i])
            i += 1
        rows.append({
            "type": kind,
            "proficiency": name,
            "points": int(cost.group(1)),
            "effect": " ".join(effect),
        })
    return rows


def with_ritual_rules(rows):
    """The mystical table's ritual clauses, parsed ONCE rather than at render.

    Same rule as the ritual critical table's CoR (CLAUDE.md 6f): a screen that
    read "Rituals using the language at -2" out of prose at display time would
    be a parser nobody tests. A common row carries neither field, because
    saying nothing about rituals is not the same as permitting them at +0.
    """
    out = []
    for row in rows:
        row = dict(row)
        if row["type"] == "Mystical":
            effect = row["effect"]
            at = re.search(r"Rituals using the language at (-?\d+)", effect)
            if "Cannot perform rituals" in effect:
                row["canPerformRituals"] = False
            elif at:
                # Accented names its penalty and never says it may cast --
                # printing a modifier IS the permission. Requiring the words
                # "can perform" as well left this row unreadable.
                row["canPerformRituals"] = True
                row["ritualModifier"] = int(at.group(1))
            elif "Can perform rituals" in effect:
                row["canPerformRituals"] = True
                row["ritualModifier"] = 0
            else:
                raise ValueError(f"{row['proficiency']} says nothing about rituals: {effect!r}")
        out.append(row)
    return out


def trait_named(name):
    entries = json.loads((OUT / "advantages.json").read_text(encoding="utf-8"))["entries"]
    return next((e for e in entries if e["name"] == name), None)


def verify(languages, proficiencies, notes):
    common = [r["name"] for r in languages if r["type"] == "Common"]
    mystical = [r["name"] for r in languages if r["type"] == "Mystical"]
    if common != EXPECTED_COMMON:
        raise ValueError(f"the common language list changed: {common}")
    if mystical != EXPECTED_MYSTICAL:
        raise ValueError(f"the mystical language list changed: {mystical}")
    for row in languages:
        if len(row["notes"]) < NOTE_MIN:
            raise ValueError(f"{row['name']} has a suspiciously short note: {row['notes']!r}")

    for kind, costs in (("Common", COMMON_COSTS), ("Mystical", MYSTICAL_COSTS)):
        rows = [r for r in proficiencies if r["type"] == kind]
        if [r["proficiency"] for r in rows] != EXPECTED_PROFICIENCIES:
            raise ValueError(f"the {kind} proficiency ladder changed: {rows}")
        if [r["points"] for r in rows] != costs:
            raise ValueError(f"the {kind} costs changed: {[r['points'] for r in rows]}")

    # The whole reason the two tables are read separately. If they ever priced
    # a proficiency the same, one of them was parsed twice.
    by_key = {(r["type"], r["proficiency"]): r["points"] for r in proficiencies}
    for name in EXPECTED_PROFICIENCIES:
        if by_key[("Common", name)] >= by_key[("Mystical", name)]:
            raise ValueError(
                f"{name} is no longer dearer as a mystical language; the two "
                "tables print the same three row names and may have merged"
            )

    # Only the mystical table speaks about rituals. A common row that grew a
    # ritual field would be granting ritual power to everyday speech.
    for row in proficiencies:
        has = "canPerformRituals" in row
        if (row["type"] == "Mystical") != has:
            raise ValueError(f"{row['type']} {row['proficiency']} has the wrong ritual fields: {row}")
    broken = next(r for r in proficiencies if r["type"] == "Mystical" and r["proficiency"] == "Broken")
    if broken["canPerformRituals"] or "ritualModifier" in broken:
        raise ValueError(
            "Broken prints 'Cannot perform rituals in the language', so it "
            "carries no modifier -- a penalty here would imply a roll exists"
        )
    accented = next(r for r in proficiencies if r["type"] == "Mystical" and r["proficiency"] == "Accented")
    if accented["ritualModifier"] != -2:
        raise ValueError(f"Accented no longer costs -2 to rituals: {accented}")

    for phrase in (HALVING_PHRASE, SPOKEN_WRITTEN_PHRASE, MULTILINGUAL_PHRASE):
        if not any(phrase in n for n in notes):
            raise ValueError(f"the note printing {phrase!r} is gone")

    # The pointer that makes these numbers reachable from a billable trait.
    pointer = trait_named(CROSS_REFERENCE)
    if pointer is None or "Languages section" not in pointer["notes"]:
        raise ValueError(
            f"chapter 3's {CROSS_REFERENCE!r} row no longer points at this "
            "section; it is the only trait these cost tables price"
        )

    # Recorded disagreements, all three inside chapter 3 itself.
    multilingual = trait_named("Multilingual")
    if multilingual is None or "common" in multilingual["notes"]:
        raise ValueError(
            "the Multilingual trait row used to say 'two additional languages' "
            "while the section says 'two COMMON languages ... does not apply to "
            "mystical'. If the row now agrees, the disagreement was repaired"
        )
    dead = trait_named(DEAD_LANGUAGE_TRAIT)
    if dead is None or UNPRINTED_LANGUAGE not in dead["notes"]:
        raise ValueError(f"{DEAD_LANGUAGE_TRAIT} no longer names {UNPRINTED_LANGUAGE}")
    if any(r["name"] == UNPRINTED_LANGUAGE for r in languages):
        raise ValueError(
            f"{UNPRINTED_LANGUAGE} is named by a trait but printed by neither "
            "language table; finding it here means the tables changed"
        )


def split_footnote(block):
    """A block's rows and the footnote under it.

    The footnote is found by its own shape -- it prices traits inline,
    "Language Talent (10 pts)", and no effect cell in either table carries a
    cost in brackets. Matching its opening words instead would miss the
    wrapped second line, and leaving it in place made it the tail of Broken's
    effect cell, so the app would have quoted a footnote as a rule about
    speaking badly.
    """
    at = next((i for i, c in enumerate(block) if FOOTNOTE.search(c)), None)
    if at is None:
        return block, ""
    return block[:at], " ".join(block[at:])


def main():
    cells = stream(load_lines(), *LANGUAGES_RANGE)
    # The intro prose wraps across the page break -- "Spoken-only or
    # written-only costs" ends one line and "half." starts the next -- so the
    # rules it prints exist only in the joined text. Everything before the
    # first section header is that intro.
    try:
        intro_ends = cells.index(COMMON_HEADER)
    except ValueError:
        raise ValueError(f"the {COMMON_HEADER!r} header is gone")
    intro = " ".join(cells[:intro_ends])

    common_block, mystical_block = split_sections(cells)
    mystical_block, footnote = split_footnote(mystical_block)
    notes = [n for n in (intro, footnote) if n]

    common_langs, common_profs = split_at_proficiencies(common_block)
    mystical_langs, mystical_profs = split_at_proficiencies(mystical_block)

    languages = (
        parse_languages(common_langs, "Common")
        + parse_languages(mystical_langs, "Mystical")
    )
    proficiencies = with_ritual_rules(
        parse_proficiencies(common_profs, "Common")
        + parse_proficiencies(mystical_profs, "Mystical")
    )
    verify(languages, proficiencies, notes)

    OUT.mkdir(parents=True, exist_ok=True)
    source = {CHAPTER: [list(LANGUAGES_RANGE)]}
    (OUT / "languages.json").write_text(
        json.dumps({"sourceLines": source, "entries": languages},
                   ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    (OUT / "language_proficiency.json").write_text(
        json.dumps({"sourceLines": source, "entries": proficiencies},
                   ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    # The prose rules, committed as the sentences that print them rather than
    # as arithmetic -- same shape as shield_rules.json under the shield table.
    (OUT / "language_notes.json").write_text(
        json.dumps({"sourceLines": source, "entries": [{"text": n} for n in notes]},
                   ensure_ascii=False, indent=1) + "\n", encoding="utf-8")

    print(f"languages: {len(languages)} rows")
    for row in languages:
        print(f"  {row['type']:9} {row['name']:16} {row['notes'][:54]}")
    print(f"proficiency: {len(proficiencies)} rows")
    for row in proficiencies:
        ritual = ""
        if "canPerformRituals" in row:
            ritual = " rituals: " + (
                f"{row['ritualModifier']:+d}" if row["canPerformRituals"] else "no"
            )
        print(f"  {row['type']:9} {row['proficiency']:9} {row['points']:3} pts{ritual}")
    for note in notes:
        print(f"note: {note[:100]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
