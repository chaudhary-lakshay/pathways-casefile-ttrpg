"""Extract the Glossary of Terms (p. 167-168).

WHY A GLOSSARY IS WORTH EXTRACTING AT ALL
------------------------------------------
It defines no rule this engine did not already implement, so nothing here
reaches the dice. What it is, is the first REFERENCE surface: 25 terms the app
can put in front of a player who is not holding the book. Chapter G read 0.0%
because nothing claimed it, and no percentage was ever going to move until
something did.

THE SHAPE, AND THE FOUR THINGS THAT BREAK A NAIVE PARSE
----------------------------------------------------------
Each entry is `Term: definition`, with the definition wrapping over one or two
further lines. Four details defeat the obvious regex:

  - **One entry continues with a labelled line of its own.** The Pathway
    entry wraps onto "Examples: Fool Pathway, Error Pathway, Darkness
    Pathway." -- a line that starts at the margin, is short, and is followed
    by a colon and a space, so it satisfies every structural property a real
    term has. The book's own rule for telling them apart is printed in the
    intro two lines above: "Terms in italics are game mechanics; bold terms
    are setting concepts." **This PDF is flattened, so no italic or bold
    survives**, and the discriminator the book supplies is unavailable.

    Three substitutes were tried against the data and all are wrong. The
    preceding line ends in a full stop for `Examples` exactly as it does for
    every real term. The definition's shape does not separate them either --
    a bare list is a plausible definition. And sorting does not help, because
    this glossary is not alphabetised (see below).

    So `Examples` is excluded BY NAME, in [NOT_TERMS], with the reason
    recorded: it is not a term this glossary defines, it is a label the book
    uses inside one entry. A named exclusion states what is true; a heuristic
    tuned until this one case passes would state something false about the
    other twenty-five.
  - **One term is punctuated with an em-dash, not a colon**: `SPI --
    Spirituality:`. It still ends in a colon, so it parses, but the term
    carries the dash and that is kept rather than normalised -- the book's
    spelling is the book's.
  - **The closing note is TWO lines, and the second one hides.** 10212 opens
    it and 10213 finishes it -- "Characters (4th Edition)." -- which begins
    with neither the note's own text nor a term, so a parser that SKIPS the
    note one line at a time folds the remainder into whatever entry came
    last. That is not hypothetical: `Will` shipped reading "...and CoR.
    Characters (4th Edition)." No term list can see it, because every term is
    still present and correctly spelled and exactly one definition is longer
    than the page. The note is therefore a STOP, not a skip.
  - **The chapter does not end where the glossary does.** From 10214 the back
    cover runs -- title, blurb, a full table of contents reprint, and the
    credits. The table of contents reprint is the hazard: it lists every
    chapter name in the book, and a parser that walked to the chapter's end
    would read "Chapter 8: Equipment & Starting Wealth" as a glossary term. So
    the range stops at the closing note, and verify() asserts the first line
    beyond it is the one that begins the back matter.

WHAT IS NOT ALPHABETISED, AND STAYS THAT WAY
----------------------------------------------
`Sea of Collective Subconscious` prints after `Spirit Vision`, and `3d6` prints
between `Sea of...` and `Will`. The book's order is not alphabetical and this
does not sort it: an extractor's job is to say what the page says. A screen
that wants A-Z can sort its own copy.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "G"
# The glossary proper: the heading and its intro through the closing note.
# NOT the whole chapter -- see the docstring on the table of contents reprint.
GLOSSARY_RANGE = (10156, 10213)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")

# The furniture actually in range, named rather than re-matched.
# verify()'s conservation check must not reuse [FURNITURE]: the first version
# did, and it compared the parse with itself -- widening that regex dropped a
# line from the output AND from the expected value, so the guard stayed green
# while a definition silently shortened. Measured, not feared.
#
# There is ONE page break inside the range, p.167 -> p.168, falling between
# the second and third lines of the SPI entry. The p.166 -> p.167 break is
# two lines ABOVE the heading and so is not in range -- which the first
# version of this got wrong, and this assertion is what said so.
#
# Keyed by LINE NUMBER, not consumed by text. A text-only ledger drops the
# first line EQUAL to the string it expects, so a continuation line that came
# to read exactly like the page header would be dropped in its place while the
# real header went unnoticed. The line number is what makes each drop exact.
FURNITURE_IN_RANGE = {
    10201: "Powered by GURPS: Pathways — Lord of the Mysteries",
    10202: "Page 168",
}

HEADING = "Glossary of Terms"
CLOSING_NOTE = "This glossary covers only the terms unique to this book."
BACK_COVER = "GURPS: VEILED EPOCH"

# A term starts a line, is short, and is followed by a colon and a space.
#
# The load-bearing condition is the FULL STOP the character class omits, and
# that was measured rather than argued. Add `.` to the class and line 10172,
# "Disadvantages give points back. Starting budget: 70 points.", parses as a
# term named "Disadvantages give points back. Starting budget" -- stealing
# Character Points' continuation line. Add `,` instead and nothing changes.
# Relax the length bound from 58 to 200 and nothing changes either: the
# longest real term is 31 characters, so the bound is slack, not a filter.
# Test names it, so the mutation that disproves the comment stays runnable.
TERM = re.compile(r"^([A-Z0-9][A-Za-z0-9 /()'—-]{0,58}?): (.+)$")

# Lines that match TERM structurally and are not terms. The book distinguishes
# them by typography this flattened PDF does not carry, so they are named. Each
# entry says why, because a bare blocklist is a list of cases someone once saw.
NOT_TERMS = {
    "Examples": "a label inside the Pathway entry, listing three pathways by "
                "name; the glossary defines no term called Examples",
}

# The 25 terms, in the book's order -- which is not alphabetical, see above.
# Named, not counted: verify() asserts this list and so states the query that
# produced it, which a number cannot.
TERMS = [
    "Acting Method",
    "Astral Projection / Astral Body",
    "Backlund",
    "Beyonder",
    "Beyonder Weapon",
    "Body of Heart and Mind",
    "Character Points",
    "CoR",
    "Critical Success / Failure",
    "Digestion",
    "Ether Body",
    "FP (Fatigue Points)",
    "Game Master (GM)",
    "HP (Hit Points)",
    "Loen Kingdom",
    "Losing Control",
    "Margin of Success / Failure",
    "Pathway",
    "Potion",
    "Sequence",
    "SPI — Spirituality",
    "Spirit Vision",
    "Sea of Collective Subconscious",
    "3d6",
    "Will",
]


def load_lines():
    return {row["n"]: row["text"] for row in json.loads(LINES_PATH.read_text("utf-8"))}


def stream(byn, first, last):
    """The glossary's lines, with page furniture dropped.

    A page break falls inside the entry for SPI, between its second and third
    lines, so furniture goes before the entries are walked rather than after.
    """
    out = []
    for n in range(first, last + 1):
        text = byn.get(n, "").strip()
        if not text or FURNITURE.match(text):
            continue
        out.append(text)
    return out


def entries(lines):
    """The `Term: definition` entries, continuation lines folded in."""
    out = []
    for text in lines:
        if text == HEADING:
            continue
        # The closing note is a STOP, not a skip. It wraps onto a second line
        # -- "Characters (4th Edition)." -- which starts with neither the note
        # nor a term, so skipping only the first line folded the second into
        # `Will`, the last entry. Nothing about that is visible in a term
        # list: every term is present and correct, and one definition is
        # longer than the book prints it.
        if text.startswith(CLOSING_NOTE):
            break
        match = TERM.match(text)
        if match and match.group(1).strip() not in NOT_TERMS:
            out.append({"term": match.group(1).strip(), "definition": match.group(2).strip()})
            continue
        # Not a new term: either the intro prose above the first entry, which
        # nothing has to catch, or a wrapped continuation of the entry above.
        if out:
            out[-1]["definition"] += " " + text
    return out


def verify(data, byn):
    ent = data["entries"]
    terms = [e["term"] for e in ent]

    # The set, in the book's order, named rather than counted. A count is a
    # claim nobody can check: this file shipped `== 26` beside an assertion
    # that `Examples` is not a term, and those two cannot both hold -- 25
    # entries with the exclusion, 26 without it, and never a state where
    # verify() passed at all.
    assert terms == TERMS, [t for t in terms if t not in TERMS] or terms
    assert all(e["definition"] for e in ent), [e["term"] for e in ent if not e["definition"]]

    # CONSERVATION, and it is the assertion the term list cannot replace. A
    # continuation line that stops being emitted does not remove a term, it
    # silently SHORTENS the definition it belonged to; a stray line in range
    # silently lengthens its neighbour. Both leave the term list perfect.
    # Rendering the entries back to source form and comparing them with the
    # lines they were built from says nothing was dropped, added, or attached
    # to the wrong entry.
    #
    # Built from the RAW lines, never from [stream] -- see FURNITURE_IN_RANGE.
    expected = []
    seen = {}
    for n in range(GLOSSARY_RANGE[0], GLOSSARY_RANGE[1] + 1):
        text = byn.get(n, "").strip()
        if not text:
            continue
        if FURNITURE_IN_RANGE.get(n) == text:
            seen[n] = text
            continue
        expected.append(text)
    assert seen == FURNITURE_IN_RANGE, (
        f"page furniture moved or changed: expected {FURNITURE_IN_RANGE}, dropped {seen}"
    )

    starts = [i for i, t in enumerate(expected) if TERM.match(t)]
    stops = [i for i, t in enumerate(expected) if t.startswith(CLOSING_NOTE)]
    assert starts, "no term line in range"
    assert stops, "the glossary lost its closing note"
    rendered = " ".join(f"{e['term']}: {e['definition']}" for e in ent)
    assert rendered == " ".join(expected[starts[0]:stops[0]]), (
        "the glossary's text is not conserved"
    )

    by_term = {e["term"]: e["definition"] for e in ent}

    # The excluded label is folded into the entry it belongs to, not dropped.
    # Losing it would silently shorten a definition, which is the failure a
    # blocklist makes easy -- so both halves are asserted: absent as a term,
    # present as text.
    assert "Examples" not in terms, terms
    assert "Examples: Fool Pathway" in by_term["Pathway"], by_term["Pathway"]
    assert by_term["Pathway"].endswith("Darkness Pathway."), by_term["Pathway"]

    # The em-dash spelling, kept rather than normalised.
    spi = next(t for t in terms if t.startswith("SPI"))
    assert spi == "SPI — Spirituality", repr(spi)

    # Wrapped continuations really were folded: each of these ends in a
    # sentence that begins on a later line than its term.
    assert by_term["CoR"].endswith("NPC monster."), by_term["CoR"]
    assert by_term["Ether Body"].endswith("ill."), by_term["Ether Body"]

    # The last entry stops where the book stops it. This one shipped wrong:
    # `Will` ended "...and CoR. Characters (4th Edition)." until the closing
    # note became a stop rather than a skip.
    assert by_term["Will"].endswith("mind control, and CoR."), by_term["Will"]

    # The back matter is NOT in range. The table of contents reprint below it
    # names every chapter in the book, and each of those lines matches the
    # term pattern -- "Chapter 8: Equipment & Starting Wealth" would parse as
    # a glossary term. This is the assertion that the range stops in time.
    assert not any(t.startswith("Chapter ") for t in terms), terms
    assert not any(t.startswith("Appendix ") for t in terms), terms
    after = byn.get(GLOSSARY_RANGE[1] + 1, "").strip()
    assert after == BACK_COVER, (
        f"the glossary's range no longer ends at the back cover, it ends at "
        f"{after!r}; the table of contents reprint may now be in range and "
        f"every chapter name would parse as a term"
    )

    # And the closing note, dropped from the entries but present in the text:
    # it is the sentence that marks where the glossary stops.
    text = "\n".join(stream(byn, *GLOSSARY_RANGE))
    assert CLOSING_NOTE in text, "the glossary lost its closing note"
    assert not any(e["term"].startswith("This glossary") for e in ent), terms

    # Terms this app implements, joined to the objects that implement them.
    # A glossary that stops describing what the engine does is a glossary
    # worth re-reading, so the overlap is asserted rather than assumed.
    for term in ("CoR", "Digestion", "Pathway", "Sequence", "Spirit Vision"):
        assert term in by_term, term


def write(data):
    (OUT / "glossary.json").write_text(
        json.dumps(
            {"sourceLines": {CHAPTER: [list(GLOSSARY_RANGE)]}, **data},
            indent=1,
            ensure_ascii=False,
        )
        + "\n",
        "utf-8",
    )


if __name__ == "__main__":
    byn = load_lines()
    data = {"entries": entries(stream(byn, *GLOSSARY_RANGE))}
    verify(data, byn)
    write(data)
    print(f"wrote {len(data['entries'])} glossary terms", file=sys.stderr)
