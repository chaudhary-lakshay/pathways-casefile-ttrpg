"""Extract Chapter 7.6's Spirit Vision guide (p. 77-78).

THE ONLY CHAPTER WITH A HEADING, RULES CONTENT AND NO EXTRACTOR
---------------------------------------------------------------
7.6 was claimed by no content file at all and reported 0.0%. That number was
not always visible: under the narrower MECHANICAL filter it read 100.0%,
because a chapter with no lines the filter can see has an empty denominator
and `coverage.py` reads that as complete. 6db widened the filter, the chapter
dropped to 0.0%, and this extractor is what finally claims it.

Four lettered sections, three shapes:

  A. Astral Projection Colors   8 rows, colour -> emotional meaning
  B. Ether Body Colors          8 rows, body REGION -> colour
  C. Pathway Differences        5 rows, Sequence 9 pathway -> what it sees
  D. Reading Spirit Vision      activation cost, then a 5-row result table

A AND B SHARE A VOCABULARY AND NOT A MEANING
---------------------------------------------
Seven of the eight colours appear in both tables, and they mean unrelated
things: Purple is "Spirituality, madness" in the Astral table and the BRAIN in
the Ether one; Green is "Calm, peace, balance" against the heart. The two also
run in opposite column orders -- A prints colour first, B prints the region
first -- so a shared parser keyed on position would transpose one of them and
a merge keyed on the colour would fuse two unrelated readings. They are kept
as separate lists with separate field names, and verify() asserts the overlap
so that a future reader meets the trap before falling into it.

SECTION C IS EXTRACTED BECAUSE IT DISAGREES, NOT BECAUSE IT ADDS
-----------------------------------------------------------------
Four of C's five rows restate what the pathway's own Sequence 9 block already
prints. The fifth contradicts it: 7.6 gives the Spectator "Enhanced: Read
emotions & thoughts" under the heading Spirit Vision, and the Spectator's own
chapter 9 block prints `Body Language Analysis (1 FP)` -- observation, priced
in FP, with no Spirit Vision in it at all.

The first instinct was to refuse the section under 6da (a summary that says
strictly less than the chapter grants no authority to widen a rule). A design
review corrected that: 6da governs the authority to APPLY, and this is a
disagreement between two printings, which CLAUDE.md 4.1 says to RECORD and 4.2
says to pin with a test so silent repair is impossible. A refusal written in a
KDoc guards nothing -- nothing fails when someone later "corrects" the
Spectator row into agreement. So the rows are extracted, the disagreement is
asserted in verify(), and what stays refused is narrower and lives with the
consumer: section C must not be used to GRANT a Spectator any Spirit Vision.

WHAT THIS DELIBERATELY DOES NOT BUILD
--------------------------------------
No Kotlin object. `SpiritualSenses` is the obvious model and it does not fit:
its `forSkill` keys on a name in `skills.json`, and Spirit Vision is not a
skill there -- it is a pathway ability rolled against Per. Its `rowFor`
matches the literal "Critical Success" where this table prints a bare
"Critical", and returns null for every failure by documented design where this
table prints a Failure row ("Incorrect reading"). Three independent mismatches,
so the grading logic is a real decision rather than a reuse.

That decision, and the question of which screen shows any of this, are
deferred together: a loader with no caller is 6cf's defect, and this slice
would ship one. The JSON stands on its own -- it takes the chapter off 0.0%
and pins a disagreement that nothing was guarding.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "7.6"
CHAPTER_RANGE = (4769, 4855)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")

SECTIONS = {
    "A": "A. Astral Projection Colors",
    "B": "B. Ether Body Colors",
    "C": "C. Pathway Differences",
    "D": "D. Reading Spirit Vision",
}

# Column headers, dropped wherever they appear. "Color" heads a column in BOTH
# tables -- as the value in A and as the key in B -- which is exactly why the
# two are parsed separately rather than by one positional walk.
HEADERS = {"Color", "Meaning", "Body Region", "Pathway (Seq 9)", "Sequence",
           "Spirit Vision Ability", "Roll", "Effect"}

# Section D's result column, in printed order. Unlike chapter 4's four rows
# this set includes a FAILURE, and prints "Critical" bare where every other
# table in the book writes "Critical Success" -- 13 occurrences of the long
# form, none of the short one outside this table.
RESULT_ROWS = ("Success", "Success by 3+", "Success by 5+", "Critical", "Failure")
BY_MARGIN = re.compile(r"^Success by (\d)\+$")

ACTIVATION = re.compile(r"expending (\d+) SPI \((\d+) SPI per minute to maintain\)")
BALANCED_NOTE = "A balanced body appears white."

# The chapter's closing sentence, naming each pathway's form of the ability.
SPECIAL_FORMS = "Special Forms:"
SPECIAL_FORM = re.compile(r"([A-Z][A-Za-z' ]+?) \(([A-Z][A-Za-z ]+?)\)")

# The sentences verify() will not let go quietly.
PER_ROLL = "Make a Perception-based roll"
SPECTATOR_CLAIM = "Enhanced: Read emotions & thoughts"


def load_lines():
    return {row["n"]: row["text"] for row in json.loads(LINES_PATH.read_text("utf-8"))}


def stream(byn, first, last):
    """The chapter's lines, with page furniture dropped.

    A page break falls inside section C, between its header row and its first
    entry, so furniture goes before the rows are walked rather than after --
    the same hazard chapter 4's Perception table has.
    """
    out = []
    for n in range(first, last + 1):
        text = byn.get(n, "").strip()
        if not text or FURNITURE.match(text):
            continue
        out.append((n, text))
    return out


def split_sections(rows):
    """The chapter's lines grouped under the lettered heading above them.

    Three of the four sections open with a single sentence introducing the
    table ("The Astral Projection lies beneath the Ether Body and reveals
    emotional state:"), and those three end in a colon where no table cell in
    this chapter does. That is the whole of what this filter does.

    **It does not remove all of the chapter's prose, and an earlier version
    of this docstring claimed it did.** Section D's intro runs over two lines
    and ends in a full stop, and the chapter's closing Special Forms sentence
    is prose too. Both survive into section D's list on purpose -- `reading`
    needs the intro to find the activation cost, and `special_forms` needs
    the closing sentence. What protects the rows is not this filter but
    `reading`'s own membership check against RESULT_ROWS, and a review
    pointed out that check was relying on iteration order rather than saying
    so: a stray line arriving while a row still wants its effect became that
    effect, silently. `reading` now refuses instead.
    """
    by_letter, current = {}, None
    for _, text in rows:
        letter = next((k for k, v in SECTIONS.items() if text == v), None)
        if letter:
            current = letter
            by_letter[current] = []
            continue
        if current:
            by_letter[current].append(text)
    return {k: [t for t in v if not t.endswith(":")] for k, v in by_letter.items()}


def pairs(lines):
    """Alternating label/value lines, headers removed.

    Both colour tables print one cell per line, so a pair is simply two
    consecutive non-header lines. An odd count means a cell wrapped and the
    caller must not guess -- this raises rather than silently dropping a tail.
    """
    cells = [t for t in lines if t not in HEADERS]
    if len(cells) % 2:
        raise SystemExit(
            f"extract_spirit_vision: {len(cells)} cells do not pair; a cell wrapped"
        )
    return list(zip(cells[0::2], cells[1::2]))


def astral(lines):
    """A: colour -> what it says about the subject's emotional state."""
    return [{"color": c, "meaning": m} for c, m in pairs(lines)]


def ether(lines):
    """B: body region -> its colour. The inverse column order to A."""
    body = [t for t in lines if not t.startswith(BALANCED_NOTE[:20])]
    note = next((t for t in lines if t.startswith(BALANCED_NOTE[:20])), "")
    return [{"region": r, "color": c} for r, c in pairs(body)], note


def pathways(lines):
    """C: the Sequence 9 pathway rows, as printed.

    Four of the five wrap their parenthesised pathway onto its own line, so
    the walk keys on the SEQUENCE cell -- the one line that is always a bare
    integer -- and takes the lines before it as the name.
    """
    out, pending = [], []
    for text in lines:
        if text in HEADERS:
            continue
        if text.isdigit():
            out.append({"name": " ".join(pending), "sequence": int(text), "ability": None})
            pending = []
            continue
        if out and out[-1]["ability"] is None:
            out[-1]["ability"] = text
            continue
        pending.append(text)
    return out


def special_forms(lines):
    """D's closing sentence: each pathway's own name for the ability.

    Kept because it carries a collision worth recording. `Ether Body
    Awareness` is listed here as the Seer's form OF Spirit Vision, and it is
    also a 5-point advantage in chapter 3's `advantages.json` whose own text
    says it works "even without Spirit Vision" -- a trait for characters who
    LACK the ability. Same string, opposite premise, so nothing may join the
    two on the name. verify() pins both printings.
    """
    sentence = " ".join(t for t in lines if SPECIAL_FORMS in t or t.endswith("(Corpse Collector)."))
    if SPECIAL_FORMS not in sentence:
        return []
    return [
        {"form": form.strip(), "pathwayRank": rank.strip()}
        for form, rank in SPECIAL_FORM.findall(sentence.split(SPECIAL_FORMS, 1)[1])
    ]


def reading(lines):
    """D: the activation cost and the five-row interpretation table."""
    prose = " ".join(t for t in lines if t not in HEADERS and t not in RESULT_ROWS)
    cost = ACTIVATION.search(prose)
    if not cost:
        raise SystemExit("extract_spirit_vision: section D lost its activation cost")

    # An effect cell is the ONE line immediately after its result label. The
    # section also carries prose -- its own two-line intro, and the closing
    # Special Forms sentence -- which is why this cannot simply take the next
    # non-header line: a stray line landing while a row still wants its
    # effect would become that effect, and nothing would say so. So a row
    # takes the line that directly follows its label, and a label followed by
    # anything else is a parse this must not guess at.
    cells = [t for t in lines if t not in HEADERS]
    rows = []
    for i, text in enumerate(cells):
        if text not in RESULT_ROWS:
            continue
        if i + 1 >= len(cells) or cells[i + 1] in RESULT_ROWS:
            raise SystemExit(
                f"extract_spirit_vision: result row {text!r} has no effect cell"
            )
        effect = cells[i + 1]
        # The closing Special Forms sentence sits directly after the last
        # effect cell, so a missing final effect does not leave the label
        # bare -- it promotes that sentence into the table. The row count is
        # right, the label is right, and one effect is a paragraph of prose
        # about something else. An effect cell in this table is a short
        # phrase; the sentence names five pathways and ends in a full stop.
        if effect.startswith(SPECIAL_FORMS):
            raise SystemExit(
                f"extract_spirit_vision: result row {text!r} took the Special "
                f"Forms sentence as its effect; an effect cell is missing"
            )
        row = {"result": text, "effect": effect}
        margin = BY_MARGIN.match(text)
        if margin:
            row["margin"] = int(margin.group(1))
        rows.append(row)
    return {
        "activation": int(cost.group(1)),
        "maintenance": int(cost.group(2)),
        "rows": rows,
    }


def extract(byn):
    sections = split_sections(stream(byn, *CHAPTER_RANGE))
    body, note = ether(sections["B"])
    return {
        "astralColors": astral(sections["A"]),
        "etherBody": body,
        "etherBodyNote": note,
        "pathwayDifferences": pathways(sections["C"]),
        "reading": reading(sections["D"]),
        "specialForms": special_forms(sections["D"]),
    }


def verify(data, byn):
    text = "\n".join(t for _, t in stream(byn, *CHAPTER_RANGE))

    # -- A and B: two tables, one vocabulary ------------------------------
    assert len(data["astralColors"]) == 8, len(data["astralColors"])
    assert len(data["etherBody"]) == 8, len(data["etherBody"])
    assert data["astralColors"][0] == {"color": "Red", "meaning": "Passion, excitement, anger"}
    assert data["etherBody"][0] == {"region": "Limbs active", "color": "Red"}

    # The trap, asserted rather than described: seven colours are in both
    # tables and mean unrelated things. Purple is the loudest -- madness in
    # one, the brain in the other -- so a merge on the colour is a defect and
    # this is what fails if anyone attempts one.
    astral_colors = {r["color"] for r in data["astralColors"]}
    ether_colors = {r["color"] for r in data["etherBody"]}
    assert len(astral_colors & ether_colors) == 7, sorted(astral_colors & ether_colors)
    purple_meaning = next(r["meaning"] for r in data["astralColors"] if r["color"] == "Purple")
    purple_region = next(r["region"] for r in data["etherBody"] if r["color"] == "Purple")
    assert purple_meaning == "Spirituality, madness", purple_meaning
    assert purple_region == "Brain", purple_region

    assert data["etherBodyNote"].startswith(BALANCED_NOTE), data["etherBodyNote"]

    # -- C: five rows, and the one that disagrees with chapter 9 ----------
    rows = data["pathwayDifferences"]
    assert len(rows) == 5, [r["name"] for r in rows]
    assert all(r["sequence"] == 9 for r in rows), rows
    assert all(r["ability"] for r in rows), rows

    spectator = next(r for r in rows if r["name"].startswith("Spectator"))
    assert spectator["ability"] == SPECTATOR_CLAIM, spectator

    # 4.1/4.2: the disagreement is pinned so it cannot be silently repaired.
    # 7.6 files the Spectator's power under Spirit Vision; its own Sequence 9
    # block prices Body Language Analysis in FP and never mentions Spirit
    # Vision. If either printing changes, this is what says so.
    blocks = json.loads((OUT / "pathways.json").read_text("utf-8"))["entries"]
    own = next(b for b in blocks if b["name"] == "Spectator" and b["sequence"] == 9)
    assert "Body Language Analysis (1 FP)" in own["ability"], own["ability"][:120]
    assert "Spirit Vision" not in own["ability"], (
        "the Spectator's own block now mentions Spirit Vision; 7.6's summary "
        "and chapter 9 no longer disagree, and the recorded disagreement has "
        "to be read again"
    )
    # The other four do print it, which is what makes the fifth a disagreement
    # rather than a chapter-wide silence.
    for name in ("Seer", "Mystery Pryer", "Sleepless", "Corpse Collector"):
        block = next(b for b in blocks if b["name"] == name and b["sequence"] == 9)
        assert "Spirit Vision" in block["ability"], f"{name} lost its Spirit Vision"

    # -- D: the cost, the roll, and the two rows chapter 4 does not have --
    read = data["reading"]
    assert (read["activation"], read["maintenance"]) == (1, 1), read
    assert PER_ROLL in text, "section D lost the sentence naming the roll"

    results = [r["result"] for r in read["rows"]]
    assert results == list(RESULT_ROWS), results
    assert [r.get("margin") for r in read["rows"]] == [None, 3, 5, None, None], read["rows"]

    # A FAILURE ROW, which chapter 4's two tables do not print and whose
    # absence extract_spirituality.verify() asserts. Any grading written for
    # this table has to answer for it: SpiritualSenses.rowFor returns null on
    # every failure by documented design, so it cannot grade this one.
    failure = next(r for r in read["rows"] if r["result"] == "Failure")
    assert failure["effect"] == "Incorrect reading", failure

    # And the short critical label, which appears nowhere else in the book --
    # every other table writes "Critical Success", the string
    # SpiritualSenses.rowFor matches on.
    assert "Critical" in results and "Critical Success" not in results, results

    # -- the closing sentence, and the name collision in it ---------------
    forms = data["specialForms"]
    assert len(forms) == 5, forms
    assert [f["pathwayRank"] for f in forms] == [
        "Seer", "Mystery Pryer", "Spectator", "Sleepless", "Corpse Collector",
    ], forms

    # `Ether Body Awareness` names two different things and nothing may join
    # them. Here it is the Seer's form OF Spirit Vision; in chapter 3 it is a
    # 5-point advantage that works "even without Spirit Vision" -- for
    # characters who lack the ability entirely. Both printings are asserted,
    # so the collision cannot be resolved by accident in either direction.
    seer_form = next(f["form"] for f in forms if f["pathwayRank"] == "Seer")
    assert seer_form == "Ether Body Awareness", seer_form
    advantages = json.loads((OUT / "advantages.json").read_text("utf-8"))["entries"]
    bought = next(a for a in advantages if a["name"] == "Ether Body Awareness")
    assert bought["cost"] == 5, bought
    assert "even without Spirit Vision" in bought["notes"], (
        "chapter 3's Ether Body Awareness no longer says it works without "
        "Spirit Vision; the collision with 7.6's Special Forms sentence was "
        "recorded on that sentence and has to be read again"
    )


def write(data):
    (OUT / "spirit_vision.json").write_text(
        json.dumps(
            {"sourceLines": {CHAPTER: [list(CHAPTER_RANGE)]}, **data},
            indent=1,
            ensure_ascii=False,
        )
        + "\n",
        "utf-8",
    )


if __name__ == "__main__":
    byn = load_lines()
    data = extract(byn)
    verify(data, byn)
    write(data)
    print(
        f"wrote {len(data['astralColors'])} astral colours, "
        f"{len(data['etherBody'])} ether regions, "
        f"{len(data['pathwayDifferences'])} pathway rows and "
        f"{len(data['reading']['rows'])} result rows",
        file=sys.stderr,
    )
