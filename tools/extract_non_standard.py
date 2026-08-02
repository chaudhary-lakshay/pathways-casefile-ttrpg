"""Extract the 17 non-standard Sequence blocks (Ch. 14-15) into committed JSON.

Same block shape as the 88 standard ones, so the parsing is
extract_pathways.extract() verbatim -- the header regex already matches
"Sequence 9: Dancer · Eternal Aeon (Inevitability) Pathway", and advantages
and drawbacks stay prose here for exactly the reason they do there.

WHY A SEPARATE FILE AND NOT MORE ROWS IN pathways.json
------------------------------------------------------
Because they are not potions. Chapter 14 says it in its own second
paragraph: "Unlike standard Beyonders who consume potions brewed from
Beyonder Characteristics, followers of non-standard pathways advance through
boons -- power granted by an Outer Deity in response to prayer, sacrifice,
and ritual." Dropped into pathways.json they would immediately become
drinkable: PotionDigestion.of walks whatever is there, and canDrink would
offer a Sequence 9 Dancer potion that does not exist.

THE LADDER HAS HOLES, AND THAT IS THE DATA
------------------------------------------
Chapter 14 documents Sequence 9 for ten pathways; chapter 15 documents
Sequence 8 for seven. They do not line up. Three of the Sequence 8 entries
belong to pathways whose Sequence 9 the book never prints, which is what the
intro warns about -- "most pathways are only known at this sequence" -- and
is a second reason these cannot go in pathways.json, where every pathway is
verified to carry all four of 6/7/8/9.

A FOURTH GAP IS A NAMING DISAGREEMENT, NOT A MISSING BLOCK
----------------------------------------------------------
Chapter 14 prints "Eternal Aeon (Inevitability) Pathway" and chapter 15
prints "Eternal Aeon Pathway". Recorded, never repaired: the two strings
stay as printed, so the computed gap list contains "Eternal Aeon" and the
constant below says why.
"""
import json, pathlib, sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import extract_pathways as standard

ROOT = pathlib.Path(__file__).resolve().parent.parent
CHAPTERS_PATH = ROOT / "build" / "extract" / "chapters.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER_IDS = ("14", "15")

EXPECTED_BLOCKS = 17
# Ten Sequence 9 entries in chapter 14, seven Sequence 8 in chapter 15.
EXPECTED_BY_SEQUENCE = {9: 10, 8: 7}

# Pathways whose Sequence 8 is printed but whose Sequence 9 is not. The first
# three are real gaps the chapter intro warns about. "Eternal Aeon" is not: it
# is chapter 15's shorter spelling of chapter 14's "Eternal Aeon
# (Inevitability)", and the two are left disagreeing rather than merged.
SEQUENCE_8_WITHOUT_9 = {"Second Law", "Sublunary Eye", "Eternal Edict", "Eternal Aeon"}
PATHWAY_NAME_DISAGREEMENT = ("Eternal Aeon (Inevitability)", "Eternal Aeon")

# Three blocks print an " Ability" heading with nothing under it -- Villain at
# global line 9177, Astronomy Aficionado at 9322, Secretary at 9370. In each
# case the very next line is a page break or the following block's header, so
# the section is empty in the BOOK and nothing was lost in the parse. All 88
# standard blocks carry an ability and so do the other 14 here, which is why
# these are named rather than tolerated.
ABILITY_MISSING = {("Villain", 9), ("Astronomy Aficionado", 9), ("Secretary", 8)}


def chapter_ranges():
    chapters = json.loads(CHAPTERS_PATH.read_text(encoding="utf-8"))
    return {c["id"]: (c["startPage"], c["endPage"]) for c in chapters if c["id"] in CHAPTER_IDS}


def verify(entries):
    if len(entries) != EXPECTED_BLOCKS:
        raise ValueError(f"expected {EXPECTED_BLOCKS} blocks, got {len(entries)}")

    counts = {}
    for entry in entries:
        counts[entry["sequence"]] = counts.get(entry["sequence"], 0) + 1
    if counts != EXPECTED_BY_SEQUENCE:
        raise ValueError(f"sequence spread changed: {counts} != {EXPECTED_BY_SEQUENCE}")

    silent = {(e["name"], e["sequence"]) for e in entries if not e["ability"]}
    if silent != ABILITY_MISSING:
        raise ValueError(
            "which blocks print no ability changed; that is the finding.\n"
            f"  now: {sorted(silent)}\n  was: {sorted(ABILITY_MISSING)}"
        )

    # A pathway/sequence pair must be unique, or a lookup silently picks one.
    pairs = [(e["pathway"], e["sequence"]) for e in entries]
    if len(set(pairs)) != len(pairs):
        raise ValueError(f"a pathway prints the same sequence twice: {sorted(pairs)}")

    with_nine = {e["pathway"] for e in entries if e["sequence"] == 9}
    gaps = {e["pathway"] for e in entries if e["sequence"] == 8} - with_nine
    if gaps != SEQUENCE_8_WITHOUT_9:
        raise ValueError(
            "which pathways print a Sequence 8 without a Sequence 9 changed; "
            f"that is the finding.\n  now: {sorted(gaps)}\n  was: {sorted(SEQUENCE_8_WITHOUT_9)}"
        )

    printed = {e["pathway"] for e in entries}
    for spelling in PATHWAY_NAME_DISAGREEMENT:
        if spelling not in printed:
            raise ValueError(
                f"the book no longer prints {spelling!r}; the two chapters may have been "
                "reconciled, which is a finding, not a repair to copy"
            )


def not_also_a_potion(entries):
    """No non-standard pathway may appear in pathways.json.

    Chapter 14's pathways advance by boon, and PotionDigestion walks whatever
    pathways.json holds. One name in both files makes a boon-granted path
    drinkable.
    """
    path = OUT / "pathways.json"
    if not path.exists():
        return
    potions = {e["pathway"] for e in json.loads(path.read_text(encoding="utf-8"))["entries"]}
    overlap = {e["pathway"] for e in entries} & potions
    if overlap:
        raise ValueError(f"these pathways are in both files and would be drinkable: {overlap}")


def main():
    entries, spans, unparsed = standard.extract(standard.load_lines(), chapter_ranges())
    verify(entries)
    not_also_a_potion(entries)

    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": spans, "entries": entries}
    (OUT / "non_standard_pathways.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )

    print(f"non_standard_pathways: {len(entries)} blocks, "
          f"{sum(len(e['skills']) for e in entries)} skill grants, "
          f"{sum(len(e['stats']) for e in entries)} attribute gains")
    for cid in CHAPTER_IDS:
        claimed = sum(b - a + 1 for a, b in spans.get(cid, []))
        print(f"  ch{cid}: {claimed} lines claimed")
    if unparsed:
        print(f"unresolved skill grants ({len(unparsed)}):")
        for name, line in unparsed:
            print(f"  {name}: {line!r}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
