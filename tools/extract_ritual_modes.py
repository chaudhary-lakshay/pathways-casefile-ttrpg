"""Extract Ch. 7's Section II and the Quick Ritual's modifiers (p. 61-62).

THE BOOK PRINTS TWO PREPARATION TABLES AND THEY DISAGREE
---------------------------------------------------------
Section III offers two ways to resolve a ritual, and each prints its own
preparation table:

  Quick Ritual (p. 62)   Excellent +2, Adequate +0, Poor/None -2 to -4
  Full Ritual  (p. 63)   Excellent +3, Good +1 to +2, Adequate +0,
                         Poor -1 to -2, None -4 or worse

`ritual_preparation.json` is the SECOND of those, extracted in M4's ritual
slice, and the app has been applying it to every ritual ever since -- so a
Quick Ritual prepared excellently has been rolling at +3 where its own table
says +2. Neither table is repaired. They are two modes, the book says so, and
which one is in play is the GM's declaration.

AND THE LINK IS COUNTED ONCE IN EACH MODE, DIFFERENTLY
-------------------------------------------------------
Quick mode says "Sum the following two modifiers: 1. Preparation Quality
2. Target Link", so the link is its own +4 to -4.

Full mode folds it IN: p. 63 lists "Link to target: None / Weak / Strong /
Perfect" among the declarations that "feed into a single Preparation
Modifier", and its rows say so in their own descriptions -- Excellent is
"perfect link", Good "strong link", Adequate "basic link", Poor "weak link",
None "no link".

So target_link belongs to the QUICK table only. Offering it in full mode pays
a perfect link twice, +8 on a 3d6 roll. verify() asserts the full table's rows
still name their links, because that phrasing is the whole evidence.

THE SUPPORTING SKILLS ARE MODE-INDEPENDENT AND CAPPED
------------------------------------------------------
Section II's five skills each grant +1 and the sentence under them caps the
total at +3 -- so a scholar with all five gets +3, not +5. The cap is a
sentence rather than a column, and it is extracted as a number because
Divination already has this exact shape (`Divination.supportBonus`).

WHAT IS DELIBERATELY NOT EXTRACTED
----------------------------------
Section IV (power sources, HP sacrifice, participants, catalysts, and the
Entity Response Table) and the Time & Complexity table. Both are real
mechanics with real modifiers, and both are their own slice: this one is
about which modifiers a ROLL takes, and those are about what fuels it and who
answers. The full 12-step canon procedure is reference prose the book itself
labels as texture.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "7"
SUPPORT_RANGE = (4009, 4028)
QUICK_PREPARATION_RANGE = (4055, 4064)
TARGET_LINK_RANGE = (4065, 4075)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADERS = {
    "Supporting Skill", "Bonus", "Notes",
    "Condition", "Modifier", "Link to Target",
    "1. Preparation Quality", "2. Target Link",
}
BONUS_CELL = re.compile(r"^\+(\d+)$")
# "+2", "+0", "-4", "-2 to -4", "+2 to +3", "+0 to -1".
MODIFIER_CELL = re.compile(r"^([+-]\d+)(?:\s+to\s+([+-]\d+))?$")
# "Excellent — sanctified space, ..." / "Perfect — true name + ..."
QUALITY_CELL = re.compile(r"^(.+?)\s+[—-]\s+(.+)$")

CAP_SENTENCE = "cumulative but cap at +3 total"
SUPPORT_CAP = 3

EXPECTED_SUPPORT = [
    "Occultism", "Thaumatology", "Hidden Lore (relevant domain)",
    "Research", "Professional Skill (Astrology etc)",
]
EXPECTED_QUICK = ["Excellent", "Adequate", "Poor / None"]
EXPECTED_LINKS = ["Perfect", "Strong", "Weak", "None"]

# The Full Ritual's own table, already extracted. The two disagree on
# Excellent and that disagreement is the point.
FULL_PREPARATION = OUT / "ritual_preparation.json"
# Every link word the full table's rows use, which is what proves the link is
# folded into its preparation modifier rather than added beside it.
LINKS_NAMED_BY_FULL_TABLE = {
    "perfect link", "strong link", "basic link", "weak link", "no link",
}


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    """Table cells in order, furniture and headers dropped."""
    out = []
    for n in range(start, end + 1):
        text = (byn.get(n) or "").strip()
        if not text or FURNITURE.match(text) or text in HEADERS:
            continue
        out.append(text)
    return out


def parse_support(cells):
    """Skill, bonus, notes -- with the cap sentence split off the end.

    A note wraps onto as many lines as it needs, so a row ends where the next
    NAME is: a line whose following line is a bonus cell. Counting three cells
    per row works until a note wraps, which the last row's does.
    """
    cap_at = next((i for i, c in enumerate(cells) if CAP_SENTENCE in c), None)
    if cap_at is None:
        raise ValueError(f"the cap sentence is gone; expected {CAP_SENTENCE!r}")
    cap_text, cells = cells[cap_at], cells[:cap_at]

    rows, i = [], 0
    while i < len(cells):
        name = cells[i]
        bonus = BONUS_CELL.fullmatch(cells[i + 1]) if i + 1 < len(cells) else None
        if not bonus:
            raise ValueError(f"{name!r} has no bonus cell after it")
        notes, i = [], i + 2
        while i < len(cells) and not (
            i + 1 < len(cells) and BONUS_CELL.fullmatch(cells[i + 1])
        ):
            notes.append(cells[i])
            i += 1
        rows.append({
            "skill": name,
            "bonus": int(bonus.group(1)),
            "notes": " ".join(notes),
        })
    return rows, cap_text


def parse_modifier_rows(cells, key, expected):
    """"Name — what it looks like" followed by a modifier cell.

    The name cell wraps (the quick table's Excellent row runs onto a second
    line), so cells are accumulated until a modifier cell is met rather than
    counted -- the same rule the shield table's names needed.
    """
    rows, pending = [], []
    for cell in cells:
        modifier = MODIFIER_CELL.fullmatch(cell)
        if not modifier:
            pending.append(cell)
            continue
        if not pending:
            raise ValueError(f"a modifier {cell!r} with no row above it")
        described = QUALITY_CELL.fullmatch(" ".join(pending))
        if not described:
            raise ValueError(f"{' '.join(pending)!r} is not 'Name — description'")
        near, far = modifier.group(1), modifier.group(2)
        row = {
            key: described.group(1).strip(),
            "modifier": int(near),
            "printed": cell,
            "looksLike": described.group(2).strip(),
        }
        if far is not None:
            row["best"] = int(far)
        rows.append(row)
        pending = []
    if pending:
        raise ValueError(f"trailing cells with no modifier: {pending!r}")
    got = [r[key] for r in rows]
    if got != expected:
        raise ValueError(f"the {key} ladder changed: {got} != {expected}")
    return rows


def full_preparation():
    return json.loads(FULL_PREPARATION.read_text(encoding="utf-8"))["entries"]


def verify(support, cap, quick, links):
    got = [r["skill"] for r in support]
    if got != EXPECTED_SUPPORT:
        raise ValueError(f"the supporting skill list changed: {got}")
    if any(r["bonus"] != 1 for r in support):
        raise ValueError("a supporting skill no longer grants +1; the cap maths would change")
    if sum(r["bonus"] for r in support) <= SUPPORT_CAP:
        raise ValueError(
            "the supporting skills no longer total more than the cap, so the "
            "cap does nothing -- it exists because five skills grant five points"
        )
    if CAP_SENTENCE not in cap:
        raise ValueError(f"the cap sentence changed: {cap!r}")

    # The finding: the two modes price the same preparation differently.
    full = {r["quality"]: r["modifier"] for r in full_preparation()}
    quick_by_name = {r["quality"]: r["modifier"] for r in quick}
    if quick_by_name["Excellent"] == full["Excellent"]:
        raise ValueError(
            "the two preparation tables now agree on Excellent. They printed "
            f"{quick_by_name['Excellent']} (quick) and {full['Excellent']} "
            "(full), and the app applied the full one to every ritual -- if "
            "they agree, this whole distinction was repaired away"
        )
    if quick_by_name["Adequate"] != full["Adequate"]:
        raise ValueError(
            "the two tables used to agree that Adequate is +0, which is what "
            "makes Excellent's disagreement a real difference rather than two "
            "unrelated ladders"
        )

    # And the evidence that the link belongs to quick mode only.
    described = " ".join(r["looksLike"].lower() for r in full_preparation())
    named = {phrase for phrase in LINKS_NAMED_BY_FULL_TABLE if phrase in described}
    if named != LINKS_NAMED_BY_FULL_TABLE:
        raise ValueError(
            "the Full Ritual's preparation rows no longer name their links "
            f"(missing {LINKS_NAMED_BY_FULL_TABLE - named}). That phrasing is "
            "the only evidence the link is folded INTO that modifier, and "
            "without it nothing stops a full ritual counting a perfect link twice"
        )

    # A link is worth more than any single preparation step, which is why
    # double-counting it matters enough to scope.
    if max(r["modifier"] for r in links) <= max(r["modifier"] for r in quick):
        raise ValueError("the target link no longer outweighs preparation; check the parse")


def main():
    byn = load_lines()
    support, cap = parse_support(stream(byn, *SUPPORT_RANGE))
    quick = parse_modifier_rows(
        stream(byn, *QUICK_PREPARATION_RANGE), "quality", EXPECTED_QUICK)
    links = parse_modifier_rows(
        stream(byn, *TARGET_LINK_RANGE), "link", EXPECTED_LINKS)
    verify(support, cap, quick, links)

    OUT.mkdir(parents=True, exist_ok=True)

    def write(name, rows, lines):
        payload = {"sourceLines": {CHAPTER: [list(lines)]}, "entries": rows}
        (OUT / name).write_text(
            json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")

    # The +3 cap is a number inside a sentence, so it lives in Kotlin and this
    # verify() pins the sentence it was read from -- the same split the
    # maneuver table's +4/-4/-6/-2 already take (CLAUDE.md 6i). Committing it
    # as a column would make it look like the book printed one.
    write("ritual_support.json", support, SUPPORT_RANGE)
    write("ritual_quick_preparation.json", quick, QUICK_PREPARATION_RANGE)
    write("ritual_target_link.json", links, TARGET_LINK_RANGE)

    print(f"support: {len(support)} skills, cap +{SUPPORT_CAP}")
    for row in support:
        print(f"  {row['skill']:34} +{row['bonus']}  {row['notes'][:44]}")
    print(f"quick preparation: {len(quick)} rows")
    for row in quick:
        print(f"  {row['quality']:14} {row['printed']:10} {row['looksLike'][:44]}")
    print(f"target link: {len(links)} rows")
    for row in links:
        print(f"  {row['link']:14} {row['printed']:10} {row['looksLike'][:44]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
