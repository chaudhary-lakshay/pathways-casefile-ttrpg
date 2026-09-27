"""Extract the 88 Sequence stat blocks (T1) from chapters 9-12 into committed
JSON content for :core:rules.

Source shape (verified against build/extract/lines.json, not assumed). Each
block opens with a header line and is followed by up to five sections, each
introduced by a header line that the PDF emits with a leading space:

    Sequence 9: Seer · Fool Pathway
     Stat / Advantage        <- section header, followed by " Gain"
     Gain
    SPI
    +9
    Eidetic Memory
    continuously grows as the
    ...
     Skill                   <- followed by " Change"
     Change
    Ritualistic Magic/IQ [Very Hard]
    +3
     Ability
    Spirit Vision: By expending 1 SPI, ...

Section headers seen across all 88 blocks, with counts:
    " Stat / Advantage" 88, " Gain" 88, " Ability" 88,
    " Skill" 76, " Change" 76, " Reinforce" 56, " Drawbacks" 11
" Reinforce" is the PDF's split rendering of "Reinforced Abilities" -- the
remainder arrives on the next line as "d Abilities" (no leading space).

WHAT IS STRUCTURED AND WHAT IS NOT
----------------------------------
Attribute rows and skill rows are parsed strictly. Advantage and drawback
rows are NOT: their name/effect boundary is not recoverable from the
flattened text. The PDF prints them as a two-column table, the columns are
concatenated in reading order, and no textual signal separates a wrapped
effect line from the next row's name -- "Sunlight/purification/holy" is a
continuation, "Poison Resistance" is a name, and both are capitalised
single-clause lines. pypdf offers no rescue: this PDF's text matrices are
degenerate (every glyph reports x=0), so column geometry is unavailable.

That claim is about an ADVANTAGE row's boundary and it still stands. An
ATTRIBUTE row's gain cell does have a signal, because a wrapped line is the
middle of a sentence and opens lowercase where the next row's name is a
proper noun. Measured over every block this parser sees -- the 88 potion
blocks AND the 17 non-standard ones `extract_non_standard.py` runs it on,
which a count of 88 would have left out: 62 lines follow a stat gain line in
this section, 60 open with a capital and are advantage names, and the 2 that
do not are the gain cell's own remainder. Before that was read, the
remainder was swept into `advantages`, so ONE sentence arrived split across
two fields -- White Tower Seq 9 stored `"(knowledge retention"` and ended its
advantages `"...near-perfect recall and learning)"`, and Demoness Seq 9
stored `"-- reflexes sharpen"` and OPENED its advantages `"for precise
timing ..."`. Note the counter-example above is the other direction: a
capitalised continuation is left where it falls, which costs nothing this
parser was getting right.

Rather than guess a boundary, those rows are preserved verbatim as prose in
`advantages` / `drawbacks`. Nothing is dropped; it is presented as the book
prints it, and the GM adjudicates it, which is what a drawback like "Roll
Will when suppressing evil desires" requires anyway.

ponytail: prose, not per-row structure. Structure it only if the engine ever
needs to auto-apply potion-granted advantages -- which needs a curated name
list per block, roughly 90 rows of hand work, and buys nothing until
advantages have mechanical effects in :core:rules.

The chapter intro pages (the "How to Read These Entries" prose and its
point-ladder examples) are deliberately unclaimed: they are instructions to
the reader, not content. They show up as unclaimed mechanical lines in
coverage.md, which is the honest reading.
"""
import json, pathlib, re, sys

import coverage

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
CHAPTERS_PATH = ROOT / "build" / "extract" / "chapters.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER_IDS = ("9", "10", "11", "12")

HEADER = re.compile(r"^Sequence (\d+): (.+?) · (.+?) Pathway$")
CHAPTER_HEADING = re.compile(r"^Chapter \d+: ")
FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
GAIN = re.compile(r"^([+−-])\s*(\d+(?:\.\d+)?)\s*(.*)$")

# A skill grant, anchored on its "/ATTR [Difficulty] +N" tail. The name is
# whatever precedes the slash -- see resolve_name for why it cannot simply
# be captured here.
GRANT = re.compile(
    r"/(ST|DX|IQ|HT|Will|Per|SPI)\s*\[(Easy|Average|Hard|Very Hard|Wildcard)\]\s*([+−-]?\d+)",
    re.IGNORECASE,
)
ATTR_CASE = {a.upper(): a for a in ("ST", "DX", "IQ", "HT", "Will", "Per", "SPI")}
TRAILING_SPECIALIZATION = re.compile(r"\s*\(([^()]*)\)$")

DIFFICULTY_WORDS = {"Easy": "E", "Average": "A", "Hard": "H", "Very Hard": "VH"}

# The attributes and secondary characteristics a potion row may raise. A row
# whose label is not one of these is an advantage (or drawback) and is kept
# as prose -- see the module docstring.
ATTRIBUTES = ("ST", "DX", "IQ", "HT", "Will", "Per", "SPI", "HP", "FP", "Basic Speed", "Basic Move")

SECTIONS = {
    " Stat / Advantage": "stats",
    " Skill": "skills",
    " Drawbacks": "drawbacks",
    " Ability": "ability",
    " Reinforce": "reinforcedAbilities",
}
# Continuation halves of a section header, carrying no content of their own.
SECTION_HEADER_TAILS = (" Gain", " Change", "d Abilities")

# Grants whose name matches nothing in the chapter-3 skill list. Each is a
# book inconsistency, recorded rather than invented into skills.json or
# quietly dropped:
#   Reader (Seq 9, White Tower) prints a player-choice placeholder, not a
#     skill name.
#   Savant (Seq 9, Paragon) grants the one "Wildcard" entry that the closed
#     Difficulty enum cannot represent -- chapter 3 excludes it too.
#   Potions Professor (Seq 6, Moon) grants "Poison"; chapter 3 defines
#     Poisons nowhere, only Chemistry, Herb Lore and Physician.
UNRESOLVED_GRANTS = {
    "Any one knowledge skill (player's choice)/IQ [Average] +3",
    "Inventor!/IQ [Wildcard] +2",
    "Poison/IQ [Hard] +3",
}

# Base skill names granted by a potion that chapter 3 never defines. Kept
# separate from UNRESOLVED_GRANTS: these parse cleanly, they just name a
# skill the rest of the book does not have.
SKILLS_NOT_IN_CHAPTER_3 = set()

# Skills whose controlling attribute or difficulty differs between the
# chapter-3 list and the potion tables. The book disagrees with itself.
# Recorded, never normalised -- see CLAUDE.md 4.1.
DIFFICULTY_DISAGREEMENTS = {
    "Herb Lore",        # ch3 IQ/Very Hard, potion tables IQ/Average
    "Herbal Medicine",  # ch3 IQ/Very Hard, potion tables IQ/Hard
    "Jeweler",          # ch3 IQ/Hard,      potion tables IQ/Average
    "Knife",            # ch3 DX/Easy,      potion tables DX/Average
    "Leatherworking",   # ch3 DX/Easy,      potion tables DX/Average
}


def parse_header(text):
    """(sequence, name, pathway) for a block header line, else None."""
    m = HEADER.match(text.strip())
    if not m:
        return None
    return int(m.group(1)), m.group(2).strip(), m.group(3).strip()


def _signed(text):
    m = GAIN.match(text.strip())
    if not m:
        return None
    sign = -1.0 if m.group(1) in "-−" else 1.0
    return sign * float(m.group(2)), m.group(3).strip()


def parse_stats(lines):
    """(stat rows, leftover prose) for a " Stat / Advantage" section.

    An attribute row is a line naming an attribute followed by a line that
    opens with a signed number. Everything else is advantage prose.
    """
    stats, prose = [], []
    i = 0
    while i < len(lines):
        label = lines[i].strip()
        gain = _signed(lines[i + 1]) if i + 1 < len(lines) else None
        if label in ATTRIBUTES and gain is not None:
            delta, note = gain
            i += 2
            # The gain cell wraps, and a wrapped line is the middle of a
            # sentence, so it opens lowercase; the next row's name is a
            # proper noun and does not. That is the one textual signal this
            # table gives, and it is measured rather than assumed: of the 62
            # lines that follow a stat gain line across all 105 blocks this
            # parser sees (88 potion + 17 non-standard), 60 are advantage
            # names and open with a capital, and the 2 that do not are this
            # cell's own remainder (White Tower Seq 9's
            # "and learning)" and Demoness Seq 9's "for precise timing").
            # Without this the remainder was swept into `prose`, so one
            # sentence arrived split across a stat note and the advantages
            # blob -- see test_a_stat_note_claims_its_lowercase_continuation.
            while i < len(lines) and lines[i].strip()[:1].islower():
                note = f"{note} {lines[i].strip()}".strip()
                i += 1
            row = {"attr": label, "delta": delta}
            if note:
                row["note"] = note
            stats.append(row)
            continue
        if label:
            prose.append(label)
        i += 1
    return stats, " ".join(prose)


def skill_vocabulary():
    """Every skill name chapter 3 defines, plus its "Name (Specialization)"
    forms. This is the vocabulary a potion table may draw on."""
    path = OUT / "skills.json"
    if not path.exists():
        return set()
    names = set()
    for e in json.loads(path.read_text(encoding="utf-8"))["entries"]:
        names.add(e["name"])
        if e.get("specialization"):
            names.add(f"{e['name']} ({e['specialization']})")
    return names


def resolve_name(segment, known):
    """The skill name at the end of `segment`, and the note preceding it.

    The PDF's bonus cell carries free text ("+2 (Nature, Life, and Weather
    domains)") that flows into the next row's name once the columns are
    flattened, so a grant's name cannot be captured positionally -- there is
    no delimiter between the previous row's note and this row's name. It can
    be recognised, though: the name is the longest chapter-3 skill name that
    the segment ends with, optionally carrying a specialization the skill
    list does not enumerate ("Survival (any terrain)").

    Returns (name, note); name is None when no known skill name fits, which
    is reported rather than guessed at.
    """
    seg = segment.strip()
    for candidate in (seg, TRAILING_SPECIALIZATION.sub("", seg)):
        best = max((k for k in known if candidate.endswith(k)), key=len, default=None)
        if best is None:
            continue
        name = seg[len(candidate) - len(best):].strip()
        return name, seg[: len(candidate) - len(best)].strip()
    return None, seg


def parse_skills(lines, known=None):
    """(skill grants, unresolved name segments) for a " Skill" section.

    Lines are joined before parsing: the PDF wraps both names and bonus
    notes mid-cell, so line boundaries carry no meaning here.
    """
    known = skill_vocabulary() if known is None else known
    text = " ".join(t.strip() for t in lines if t.strip())

    skills, unresolved = [], []
    cursor = 0
    for m in GRANT.finditer(text):
        name, note = resolve_name(text[cursor:m.start()], known)
        cursor = m.end()
        difficulty = m.group(2).title().replace("Very hard", "Very Hard")
        if name is None or difficulty not in DIFFICULTY_WORDS:
            unresolved.append(f"{note or name}{m.group(0)}".strip())
            continue
        if note and skills:
            skills[-1]["note"] = (skills[-1].get("note", "") + " " + note).strip()
        skills.append({
            "name": name,
            "attr": ATTR_CASE[m.group(1).upper()],
            "difficulty": DIFFICULTY_WORDS[difficulty],
            "bonus": int(m.group(3).replace("−", "-")),
        })

    tail = text[cursor:].strip()
    if tail and skills:
        skills[-1]["note"] = (skills[-1].get("note", "") + " " + tail).strip()
    return skills, unresolved


def split_sections(lines):
    """Group a block's lines by section name, dropping section headers."""
    sections = {}
    current = None
    for text in lines:
        if text in SECTIONS:
            current = SECTIONS[text]
            sections.setdefault(current, [])
            continue
        if text in SECTION_HEADER_TAILS:
            continue
        if current is not None:
            sections[current].append(text)
    return sections


def load_lines():
    return json.loads(LINES_PATH.read_text(encoding="utf-8"))


def chapter_ranges():
    chapters = json.loads(CHAPTERS_PATH.read_text(encoding="utf-8"))
    return {c["id"]: (c["startPage"], c["endPage"]) for c in chapters if c["id"] in CHAPTER_IDS}


def heading_of_chapters(all_lines):
    """Where each chapter's heading sits, as a global line number.

    Asked of `coverage.heading_lines` rather than restated here: the chapter
    a line belongs to is ONE question, and answering it twice is what this
    function exists to stop. It raises rather than falling back to the page
    range when a heading cannot be found, which is deliberate there.
    """
    chapters = json.loads(CHAPTERS_PATH.read_text(encoding="utf-8"))
    return coverage.heading_lines(all_lines, chapters)


def chapter_at(n, heading_of):
    """The chapter a global line belongs to: the LAST chapter whose heading
    precedes it, or None above them all.

    A chapter starts at its HEADING, not at the top of the page the heading
    sits on. The book opens a chapter mid-page, so the page furniture above
    that heading still belongs to the chapter before it. `coverage.py` was
    taught this in b1c8c4e ("partition chapters at the heading, not the
    page") and pathways.json's spans were hand-refiled to match -- but THIS
    extractor, the other site answering the same question, kept bucketing by
    `chapters.json` page ranges. So a re-run silently re-filed those three
    spans back to the defect, and only reading the diff could see it.
    """
    started = [(line, cid) for cid, line in heading_of.items() if line <= n]
    return max(started)[1] if started else None


def extract(all_lines, ranges, heading_of):
    """Walk chapters 9-12 and return (entries, spans, unparsed)."""
    in_scope = [
        l for l in all_lines
        if any(a <= l["page"] <= b for a, b in ranges.values())
    ]
    # The page range says which lines this extractor WALKS; the heading says
    # which chapter each one is CLAIMED under. They are different questions
    # and the page range is the wrong answer to the second -- see chapter_at.
    chapter_of = {l["n"]: chapter_at(l["n"], heading_of) for l in in_scope}

    starts = [l for l in in_scope if parse_header(l["text"])]
    known = skill_vocabulary()
    entries, spans, unparsed = [], {}, []

    # A chapter's last blocks run onto the next chapter's first page (chapter
    # 9 ends on page 97, which is chapter 10), so the page a block starts on
    # does not bound it. The next chapter *heading* does: everything from
    # there to that chapter's first "Sequence N:" header is intro prose, and
    # without this a block swallows it -- Sequence 9 Lawyer's ability text
    # ended with chapter 10's "how to read a pathway entry" paragraph.
    headings = [l["n"] for l in in_scope if CHAPTER_HEADING.match(l["text"].strip())]

    for index, start in enumerate(starts):
        end = starts[index + 1]["n"] - 1 if index + 1 < len(starts) else in_scope[-1]["n"]
        next_heading = next((n for n in headings if n > start["n"]), None)
        if next_heading is not None:
            end = min(end, next_heading - 1)
        sequence, name, pathway = parse_header(start["text"])
        body = [
            l["text"] for l in in_scope
            if start["n"] < l["n"] <= end and not FURNITURE.match(l["text"].strip())
        ]
        sections = split_sections(body)

        stats, advantages = parse_stats(sections.get("stats", []))
        skills, block_unparsed = parse_skills(sections.get("skills", []), known)
        for line in block_unparsed:
            unparsed.append((name, line))

        entries.append({
            "sequence": sequence,
            "name": name,
            "pathway": pathway,
            "stats": stats,
            "skills": skills,
            "advantages": advantages,
            "drawbacks": " ".join(t.strip() for t in sections.get("drawbacks", []) if t.strip()),
            "ability": " ".join(t.strip() for t in sections.get("ability", []) if t.strip()),
            "reinforcedAbilities": " ".join(
                t.strip() for t in sections.get("reinforcedAbilities", []) if t.strip()
            ),
            "sourceLines": [start["n"], end],
        })

        # A block that straddles a page break can straddle a chapter break
        # (chapter 9 runs onto page 97, which is chapter 10). Claim each
        # line under the chapter whose pages actually contain it, or
        # coverage.py credits the claim to the wrong chapter and reports
        # those lines as unclaimed.
        for n in range(start["n"], end + 1):
            cid = chapter_of.get(n)
            if cid is None:
                continue
            block = spans.setdefault(cid, [])
            if block and block[-1][1] == n - 1:
                block[-1][1] = n
            else:
                block.append([n, n])

    return entries, spans, unparsed


def verify(entries, unparsed=()):
    """Fail loudly on any shape the rest of the pipeline assumes."""
    seen = {line for _, line in unparsed}
    if seen != UNRESOLVED_GRANTS:
        raise ValueError(
            "unresolved skill grants changed; the data moved, which is the finding.\n"
            f"  new: {sorted(seen - UNRESOLVED_GRANTS)}\n"
            f"  gone: {sorted(UNRESOLVED_GRANTS - seen)}"
        )
    if len(entries) != 88:
        raise ValueError(f"expected 88 sequence blocks, got {len(entries)}")
    by_pathway = {}
    for e in entries:
        by_pathway.setdefault(e["pathway"], []).append(e["sequence"])
    if len(by_pathway) != 22:
        raise ValueError(f"expected 22 pathways, got {len(by_pathway)}: {sorted(by_pathway)}")
    for pathway, seqs in sorted(by_pathway.items()):
        if sorted(seqs) != [6, 7, 8, 9]:
            raise ValueError(f"{pathway} has sequences {sorted(seqs)}, expected [6, 7, 8, 9]")
    for e in entries:
        if not e["ability"]:
            raise ValueError(f"{e['name']} (Seq {e['sequence']}) has no ability text")


def base_name(name):
    """"Guns (Pistol)" -> "Guns". Potion tables specialize skills more
    finely than the chapter-3 list enumerates ("Survival (any terrain)"),
    so the vocabulary check is against the base name."""
    return TRAILING_SPECIALIZATION.sub("", name).strip()


def compare_to_skill_list(entries):
    """(base names absent from chapter 3, names whose attr/difficulty disagrees)."""
    path = OUT / "skills.json"
    if not path.exists():
        return set(), set()
    ch3 = {}
    for e in json.loads(path.read_text(encoding="utf-8"))["entries"]:
        ch3.setdefault(e["name"], set()).add((e["attr"], e["difficulty"]))
    missing, disagreeing = set(), set()
    for entry in entries:
        for s in entry["skills"]:
            base = base_name(s["name"])
            if base not in ch3:
                missing.add(base)
            elif (s["attr"], s["difficulty"]) not in ch3[base]:
                disagreeing.add(base)
    return missing, disagreeing


def main():
    all_lines = load_lines()
    entries, spans, unparsed = extract(
        all_lines, chapter_ranges(), heading_of_chapters(all_lines)
    )
    verify(entries, unparsed)

    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": spans, "entries": entries}
    (OUT / "pathways.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )

    print(f"pathways: {len(entries)} blocks, "
          f"{sum(len(e['skills']) for e in entries)} skill grants, "
          f"{sum(len(e['stats']) for e in entries)} attribute gains")
    for cid in CHAPTER_IDS:
        claimed = sum(b - a + 1 for a, b in spans.get(cid, []))
        print(f"  ch{cid}: {claimed} lines claimed")

    if unparsed:
        print(f"unresolved skill grants ({len(unparsed)}):")
        for name, line in unparsed:
            print(f"  {name}: {line!r}")

    missing, disagreeing = compare_to_skill_list(entries)
    if missing != SKILLS_NOT_IN_CHAPTER_3:
        print(f"NOTICE granted skills absent from skills.json: {sorted(missing)}")
    if disagreeing != DIFFICULTY_DISAGREEMENTS:
        print(f"NOTICE skills whose difficulty disagrees with chapter 3: {sorted(disagreeing)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
