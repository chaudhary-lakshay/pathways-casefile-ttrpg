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

WHAT IS DELIBERATELY NOT EXTRACTED, AND WHY THAT REASON WAS NOT ENOUGH
-----------------------------------------------------------------------
The rest of appendix A is "a summary of chapters already claimed". That
sentence used to carry the whole decision, and it does TWO jobs at once: the
lines restate claimed content, THEREFORE nothing is lost. Both halves have now
failed, separately.

The first failed at line 9667 (below): a line that is not a summary of a
claimed chapter at all.

The second fails at line 9672, and it is the worse failure because the reason
stays TRUE while becoming irrelevant:

    Major Wound: Hit > 1/2 HP -> HT or stunned 1d6 sec | Fail by 5+ -> down 15 min

That IS a summary of a claimed chapter -- major_wound.json holds chapter 5's
own table -- and it DISAGREES with it. Chapter 5's third row reads "Knocked
down and unconscious for 1d hours"; the summary says 15 minutes. Claim status
was never the same question as agreement, and only the second one matters.

It went unread for six slices while being printed to the player: DamageDialog
renders `"${row.result} - ${row.effect}"` on every Major Wound, so the app has
been showing one of the two printings with nothing recording that the other
exists. 6da read this page one line at a time and called this line a
restatement.

So the method is now a GUARD rather than a sentence. verify_major_wound()
compares the summary with the rows it summarises, clause by clause, and
SUMMARY_DISAGREEMENTS names both printings of the one that differs -- in the
4.2 shape, so the day they agree is the day the extraction fails rather than
the day a repair goes unnoticed. Chapter 5 governs throughout: a summary may
define but not overrule, which is line 9667's own principle in the other
direction, and major_wound.json is left untouched.

Two more disagreements on this page are recorded in PROSE only, which no sweep
over this repo can read (6cj): the hit-location line reads "Vitals -3 x3" flat,
dropping the "(imp/pi only)" scope chapter 5 prints, and it lists a "Hand/Foot"
location where chapter 5's table has only Hand. Both are naming and scope
differences rather than a rule with a different number, so they are left as
prose deliberately -- but that is now a stated choice rather than the only
option. Its success ladder (Good Success at Target-3, Excellent at Target-5) is
genuinely new material and a slice of its own.

THE ONE LINE THAT IS NOT A SUMMARY OF A CLAIMED CHAPTER
--------------------------------------------------------
Line 9667, `Direction Penalties: Side -2 | Rear -4`, summarises the facing
rule -- which chapter 5 prints and which NOTHING in this repo extracts,
because `ActiveDefenses` deliberately refuses it. So "a summary of chapters
already claimed" is true of the other 31 unclaimed mechanical lines on this
page and false of this one.

That mattered once. `ActiveDefenses`' KDoc justified the refusal with "the
facing rule has no second printing anywhere in the book -- checked, not
assumed", and a sweep over the 59 content files kept agreeing, because the
second printing is on this page and this file skipped it. Two careful
exclusions, each correct about its own corpus, between them hid one line.

It is STILL not extracted, and the reason is now the right one rather than
the absent one: the line carries no noun -- neither "defense" nor "defenses"
-- so it cannot widen chapter 5's singular, and a summary may define but not
overrule. `test_tools.py` pins its wording and its nounlessness, because the
KDoc now cites it as evidence.
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


# Line 9672, the appendix's one-line summary of chapter 5's Major Wound table.
MAJOR_WOUND_LINE = 9672
MAJOR_WOUND_PREFIX = "Major Wound:"
MAJOR_WOUND_ARROW = "->"

# Which chapter-5 row each clause of that summary is a summary OF, and the
# GLOBAL LINE that row is printed on. Read from the book rather than from
# major_wound.json: this is a question about two PRINTINGS, the book is the
# authority for both, and reading another extractor's output would make the
# answer depend on which extractor ran last (Codex).
SUMMARISES = {
    "Hit > ½ HP": ("Failure", 2826),
    "Fail by 5+": ("Fail by 5+", 2830),
}
# The words the summary keeps when it agrees. Checked rather than compared
# whole: the appendix abbreviates ("HT or stunned 1d6 sec" against "Stunned
# for 1d6 seconds (roll HT each turn to recover early)"), and an equality test
# would report every abbreviation as a disagreement.
# Three dicts keyed on the same clauses, and they must stay in step: every
# SUMMARISES key belongs to exactly one of AGREES_ON and SUMMARY_DISAGREEMENTS.
# verify_major_wound says so rather than leaving a KeyError to say it. Two
# clauses and one entry each is thin, and deliberately not generalised -- a
# fourth clause needs a reading of what it summarises, not a wider structure.
AGREES_ON = {
    "Hit > ½ HP": ("stunned", "1d6"),
}
# And where it does not agree, both printings, so a repair cannot be silent.
# Chapter 5 governs: a summary may define but not overrule (line 9667's
# refusal rests on the same principle in the other direction).
SUMMARY_DISAGREEMENTS = {
    "Fail by 5+": ("down 15 min", "Knocked down and unconscious for 1d hours"),
}


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def parse_major_wound(text):
    """The summary line cut into condition -> outcome, one pair per cell."""
    if not text.startswith(MAJOR_WOUND_PREFIX):
        raise ValueError(
            f"line {MAJOR_WOUND_LINE} no longer starts {MAJOR_WOUND_PREFIX!r}: {text!r}"
        )
    out = {}
    for cell in text[len(MAJOR_WOUND_PREFIX):].split("|"):
        if MAJOR_WOUND_ARROW not in cell:
            raise ValueError(f"no {MAJOR_WOUND_ARROW!r} in summary cell {cell!r}")
        condition, outcome = cell.split(MAJOR_WOUND_ARROW, 1)
        out[condition.strip()] = outcome.strip()
    return out


def verify_major_wound(summary, chapter5):
    """The summary against the table it summarises, clause by clause.

    THE METHOD THIS FIXES. This file's own reason for leaving the page alone
    was "a summary of chapters already claimed", which does two jobs at once:
    the lines restate claimed content, THEREFORE nothing is lost. 6da killed
    the first job for line 9667. Line 9672 kills the second -- it IS a summary
    of claimed content and it DISAGREES with it, so "already claimed" is true
    and irrelevant. Claim status was never the same question as agreement, and
    this checks the one that matters.

    [chapter5] maps a row name to the chapter-5 LINE that prints it. Both
    sides of every comparison are the book's own text, so nothing here depends
    on another extractor having run.

    The two hit-location disagreements above are recorded in prose, which no
    sweep over this repo can read (6cj). These are a named set instead.
    """
    for clause, outcome in summary.items():
        summarised = SUMMARISES.get(clause)
        if summarised is None:
            raise ValueError(
                f"the summary grew a clause nothing has read: {clause!r} -> {outcome!r}"
            )
        row_name, _ = summarised
        printed = chapter5.get(row_name)
        if not printed:
            raise ValueError(f"chapter 5 no longer prints a {row_name!r} row")

        disagreement = SUMMARY_DISAGREEMENTS.get(clause)
        if disagreement is None:
            if clause not in AGREES_ON:
                raise ValueError(
                    f"{clause!r} is in SUMMARISES and in neither AGREES_ON nor "
                    "SUMMARY_DISAGREEMENTS, so nothing says what agreeing means "
                    "for it"
                )
            # BOTH sides. Checking only the appendix's wording lets chapter 5's
            # row drift under a guard that keeps passing -- the one-way check
            # with a blind side (6dh), found by review.
            for side, text in (("the appendix", outcome), ("chapter 5", printed)):
                missing = [w for w in AGREES_ON[clause] if w.lower() not in text.lower()]
                if missing:
                    raise ValueError(
                        f"the summary's {clause!r} clause used to have agreed with "
                        f"chapter 5's {row_name!r} row on {AGREES_ON[clause]}; "
                        f"{side} now reads {text!r} and drops {missing}"
                    )
            continue

        appendix_says, chapter_says = disagreement
        # Equality FIRST. Both checks fire on a repair, and this is the one
        # that names what happened: a caller who edited chapter 5's row to
        # match the summary has repaired a disagreement 4.1 says to record.
        if outcome == printed:
            raise ValueError(
                f"the appendix and chapter 5 no longer disagree on {clause!r}. "
                "The exclusion is stale, and a stale exclusion is how a repair "
                "goes unnoticed"
            )
        if outcome != appendix_says or printed != chapter_says:
            raise ValueError(
                f"the recorded disagreement on {clause!r} has moved: the appendix "
                f"reads {outcome!r} against {appendix_says!r}, and chapter 5 reads "
                f"{printed!r} against {chapter_says!r}"
            )


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
    byn = load_lines()
    text = (byn.get(WOUNDING_LINE) or "").strip()
    rows = parse(text)
    verify(rows)

    # Nothing is written for line 9672 and nothing should be -- chapter 5's
    # table governs and is already extracted. What runs here is the check the
    # old reason skipped: the summary is compared with the chapter it
    # summarises, so a repair of the recorded disagreement, or a drift in
    # either printing, fails the extraction instead of passing unread.
    summary = parse_major_wound((byn.get(MAJOR_WOUND_LINE) or "").strip())
    verify_major_wound(
        summary,
        {row: (byn.get(n) or "").strip() for row, n in SUMMARISES.values()},
    )

    OUT.mkdir(parents=True, exist_ok=True)
    payload = {
        "sourceLines": {CHAPTER: [[WOUNDING_LINE, WOUNDING_LINE]]},
        "entries": rows,
    }
    (OUT / "damage_abbreviations.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )

    chapter5 = chapter_5_types()
    for clause, (appendix_says, chapter_says) in SUMMARY_DISAGREEMENTS.items():
        print(f"recorded disagreement, {clause}: appendix {appendix_says!r} vs "
              f"chapter 5 {chapter_says!r} (chapter 5 governs)")
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
