"""Extract Appendix A's damage-type abbreviations (p. 159, global line 9671).

THE ABBREVIATION SECTION 6t SAID DID NOT EXIST
----------------------------------------------
Chapter 5's Optional Wounding Multipliers table prints "Piercing (small)" and
"Large Piercing" with no abbreviation, so `Wounds.typeMultiplier` refused
"pi-" and "pi+" -- and chapter 8's Bolt-Action Rifle deals "5d pi+", which
made the refusal reachable from real content. The handover doc recorded that
as a disagreement between two chapters with no resolution, and the app told
the player *"chapter 5 prints no wounding multiplier for pi+"*.

Appendix A prints one:

    Wounding: Crush x1 | Cut x1.5 | Impale x2 |
              Pi (small) x0.5 / Pi x1 / Pi+ x1.5 | Burn x1 | Toxic x1

Every multiplier there matches chapter 5's exactly; what the appendix adds is
the LABEL. So this is not a third opinion to weigh -- it is the missing half
of a row chapter 5 printed. The appendix was at 0.0% coverage, which is the
only reason it stayed invisible. Same lesson as chapter 8's weapon table, one
appendix over: check coverage before recording an absence.

WHY A SEPARATE FILE, AND NOT A COLUMN IN wounding_multipliers.json
------------------------------------------------------------------
Writing "pi+" into chapter 5's file would make the committed content claim
chapter 5 prints something it does not. The provenance is the finding: the
multiplier is chapter 5's and the abbreviation is the appendix's. verify()
asserts they AGREE on every row they share, so if a future printing ever made
them disagree that surfaces as a failure rather than as a silently preferred
column.

WHAT IS DELIBERATELY NOT EXTRACTED
----------------------------------
The rest of appendix A, which is a summary of chapters already claimed, and
in one place a summary that DISAGREES with them: its hit-location line reads
"Vitals -3 x3" flat, dropping the "(imp/pi only)" scope chapter 5 prints, and
it lists a "Hand/Foot" location where chapter 5's table has only Hand. A
summary does not overrule the chapter it summarises, so those are recorded
here and left unextracted. Its success ladder (Good Success at Target-3,
Excellent at Target-5) is genuinely new material and a slice of its own.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "A"
WOUNDING_LINE = 9671

# "Pi (small) x0.5", "Pi+ x1.5", "Crush x1". The name runs to the multiplier.
ENTRY = re.compile(r"^(.+?)\s*[×x]\s*([\d.]+)$")
LINE_PREFIX = "Wounding:"

# How the appendix's names map onto chapter 5's printed type names.
TO_CHAPTER_5 = {
    "Crush": "Crushing",
    "Cut": "Cutting",
    "Impale": "Impaling",
    "Pi (small)": "Piercing (small)",
    "Pi": "Piercing",
    "Pi+": "Large Piercing",
    "Burn": "Burning",
    "Toxic": "Toxic",
}

# ONLY the piercing group is read as abbreviations, and the line itself is the
# reason. Elsewhere the appendix writes prose short forms -- "Crush" where
# chapter 5 abbreviates "cr", "Impale" where it says "imp" -- so adopting
# those would invent two spellings for one type. Inside the slash-separated
# piercing group it writes "Pi", which is EXACTLY chapter 5's abbreviation for
# that row, and that agreement is what makes its siblings in the same group
# abbreviations of the same kind rather than more short names.
ABBREVIATED_GROUP = {"Pi (small)", "Pi", "Pi+"}
# Of that group, the ones chapter 5 leaves unlabelled. This file exists for
# exactly these two: "pi" chapter 5 already prints itself.
UNLABELLED_IN_CHAPTER_5 = {"Piercing (small)", "Large Piercing"}


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def parse(text):
    """One entry per name/multiplier pair on the Wounding line."""
    if not text.startswith(LINE_PREFIX):
        raise ValueError(f"line {WOUNDING_LINE} no longer starts {LINE_PREFIX!r}: {text!r}")
    body = text[len(LINE_PREFIX):]
    rows = []
    # The line separates groups with "|" and the piercing group internally
    # with "/", so both are cell boundaries.
    for cell in re.split(r"[|/]", body):
        cell = cell.strip()
        if not cell:
            continue
        m = ENTRY.fullmatch(cell)
        if not m:
            raise ValueError(f"unreadable wounding cell {cell!r}")
        name, multiplier = m.group(1).strip(), float(m.group(2))
        if name not in TO_CHAPTER_5:
            raise ValueError(
                f"the appendix names a damage type this map does not know: {name!r}"
            )
        row = {
            "type": TO_CHAPTER_5[name],
            "multiplier": multiplier,
            "printedAs": name,
        }
        # Only the piercing group carries an abbreviation; see ABBREVIATED_GROUP.
        if name in ABBREVIATED_GROUP:
            row["abbreviation"] = name.lower()
        rows.append(row)
    return rows


def chapter_5_types():
    """Chapter 5's rows: printed name -> (multiplier, abbreviation or None)."""
    path = OUT / "wounding_multipliers.json"
    return {
        e["type"]: (e["multiplier"], e.get("abbreviation"))
        for e in json.loads(path.read_text(encoding="utf-8"))["entries"]
    }


def verify(rows):
    if len(rows) != len(TO_CHAPTER_5):
        raise ValueError(
            f"the appendix lists {len(rows)} damage types, not {len(TO_CHAPTER_5)}"
        )

    chapter5 = chapter_5_types()
    for row in rows:
        if row["type"] not in chapter5:
            raise ValueError(f"chapter 5 prints no row called {row['type']!r}")
        multiplier, abbreviation = chapter5[row["type"]]
        # The whole basis for adopting the label: the numbers already agree.
        if multiplier != row["multiplier"]:
            raise ValueError(
                f"the appendix and chapter 5 disagree about {row['type']}: "
                f"x{row['multiplier']} vs x{multiplier}. That is a finding about "
                "the book, not a column to prefer"
            )
        # Where BOTH label a row, they must spell it the same way. Only the
        # piercing group is read as labels, and "Pi" agreeing with chapter 5's
        # own "pi" is the entire basis for reading its siblings that way.
        if abbreviation and "abbreviation" in row and abbreviation != row["abbreviation"]:
            raise ValueError(
                f"the two spell {row['type']} differently: {row['abbreviation']!r} "
                f"vs chapter 5's {abbreviation!r}. 'Pi' matching chapter 5 is why "
                "this group is read as abbreviations at all"
            )

    if not any(
        "abbreviation" in row and chapter5[row["type"]][1] == row["abbreviation"]
        for row in rows
    ):
        raise ValueError(
            "no row in the piercing group still matches an abbreviation chapter 5 "
            "prints, so there is no longer any evidence these are abbreviations "
            "rather than short names like 'Crush'"
        )

    supplied = {
        row["type"] for row in rows
        if "abbreviation" in row and chapter5[row["type"]][1] is None
    }
    if supplied != UNLABELLED_IN_CHAPTER_5:
        raise ValueError(
            "which rows chapter 5 leaves unlabelled changed; this file exists "
            f"for exactly those.\n  now: {sorted(supplied)}"
            f"\n  was: {sorted(UNLABELLED_IN_CHAPTER_5)}"
        )


def main():
    text = (load_lines().get(WOUNDING_LINE) or "").strip()
    rows = parse(text)
    verify(rows)

    OUT.mkdir(parents=True, exist_ok=True)
    payload = {
        "sourceLines": {CHAPTER: [[WOUNDING_LINE, WOUNDING_LINE]]},
        "entries": rows,
    }
    (OUT / "damage_abbreviations.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )

    chapter5 = chapter_5_types()
    print(f"appendix wounding line: {len(rows)} rows")
    for row in rows:
        label = row.get("abbreviation", "-")
        note = "   <- chapter 5 prints no abbreviation" if (
            "abbreviation" in row and not chapter5[row["type"]][1]
        ) else ""
        print(f"  {row['printedAs']:12} -> {row['type']:18} x{row['multiplier']:<4} "
              f"as {label}{note}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
