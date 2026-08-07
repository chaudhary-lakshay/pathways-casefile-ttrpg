"""Extract Chapter 4's spiritual sense tables and its SPI affinity claims (p. 33-34).

WHY THIS CHAPTER WAS UNREAD FOR FOUR MILESTONES
------------------------------------------------
Chapter 4 sat at 0.0% coverage while the whole app rolled on the attribute it
defines. `coverage.py` counts a line as mechanical when it holds dice, a
signed number or a point cost, and these two tables are four rows of
`Success by 3+ / <prose>` -- no numbers at all. Only the two `Modifiers:`
lines register, so the instrument reported "3 unclaimed lines" for a chapter
holding two complete skill resolution tables.

That is the FIFTH distinct way this instrument has been wrong (CLAUDE.md 6t,
6v, 6ae, 6af) and the second time bare tables were the reason. A percentage is
a floor and an argument, never a receipt.

WHAT WAS GOING UNAPPLIED
-------------------------
`Spiritual Intuition` (SPI/Hard) and `Spiritual Perception` (SPI/Average) are
both in `skills.json` and both purchasable in the builder, so a character
could already buy one, tap it and roll -- and get a bare success or failure,
while the book prints a four-row degree table and three or four circumstance
modifiers for each. Same class as the trackers reaching no roll (6h) and the
Soul Debt printed and not charged (6m).

NOT Appendix A's LADDER, AND THE EDGES MAKE THAT TEMPTING
-----------------------------------------------------------
Both tables band at "Success by 3+" and "Success by 5+", which are exactly
`DegreeOfSuccess`'s Good (Target-3) and Excellent (Target-5) edges. Reusing
that object would look tidy and would be the silent merge 4.1 forbids: the
EFFECT text is per-skill -- Intuition's 5+ is "Specific premonition (ambush,
poison, betrayal)", Perception's is "Identify type of entity" -- and 6ad
already pins that a chapter's own table beats the general ladder. The shared
edges are a coincidence worth recording, never a warrant for merging.

NEITHER TABLE PRINTS A FAILURE ROW
------------------------------------
Four rows each, all successes. So a failed sense roll gets NO row rather than
an invented one, and neither table overrides the critical rules -- nothing is
printed to override them with, so `SuccessRoll`'s 3-4/17-18 stand. That is the
FrightCheck reading (6c), not the PCR's: specific beats general only where the
specific rule actually speaks. verify() asserts the absence, because an
invented "Failure -- you sense nothing" row would look perfectly plausible.

THE OVERLAP WITH APPENDIX A
-----------------------------
Intuition prints "-2 distracted/in combat". Appendix A prints "Distracted or
hurried -2" and "Very adverse conditions (combat, extreme weather) -6 or
worse". Those are the same circumstances priced twice, generally and
specifically, and the words are verbatim in both -- so offering both charges
one distraction twice. Specific beats general, the scoping that keeps chapter
5's darkness off the general adverse row (6ae) and chapter 13's boons off
Phase I preparation (6r). verify() pins that the overlap is still really
there, because the day it stops being there is the day the scoping stops being
needed.

Deliberately NOT extended to Appendix A's "Adverse conditions (dark, noisy,
dangerous) -4": nothing in that cell is a word chapter 4 prints, and refusing
it would be reasoning from a resemblance rather than from the text.

"+6 SEER" IS A RANK, NOT A PATHWAY
------------------------------------
Perception's last modifier is "+6 Seer". Seer is the Fool pathway's SEQUENCE 9
NAME, not the pathway -- a Fool at Sequence 8 is a Clown. So it is offered as
a chip quoting the book rather than applied automatically to anyone on the
Fool pathway, which would be authoring a rule the book did not print. Same
call as the Sealed Artifact grade table's Power Equivalent column (6af): show
it, do not gate on it.

THE SECOND FILE: A CROSS-CHAPTER ORACLE
-----------------------------------------
The SPI Attribute section makes four checkable claims about chapter 9's potion
tables -- five named pathways receive "significantly more", others "+2 to +6",
reality-leaning ones a "baseline +1", and the Prisoner (Chained) exactly 0.
`pathways.json` has held those numbers since M3 and nothing had ever compared
them. That is the chapter grading another chapter's data, so it is extracted
as data and marked by `GoldenSpiAffinityTest`, the way Section VIII's worked
rituals are (6ab). Committed rather than hard-coded into the test for the
reason 6ab had to learn twice: a decision that outlives its evidence is how
the weapon-table claim survived four sections.

WHAT IS DELIBERATELY LEFT
--------------------------
* **Spirit Vision's aura table** -- already claimed under Chapter 7.6, which
  prints the fuller version. A second span here would double-claim a summary.
* **Its Sequence scaling** ("SPI typically 0-8 / 6-14 / 12+") -- descriptive
  bands for the GM, with no roll to attach them to.
* **Spirituality Recovery** -- "1 SPI per hour of rest". This is its THIRD
  printing (here, p. 35, and Appendix A, which 6ad also left), and nothing in
  this app holds a clock. Still reported, still not modelled.
* **Blocks 4 and 5** -- Ritualistic Magic and Divination Arts are one-line
  cross-references to chapters 7 and 6.5, both fully modelled already.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "4"
AFFINITY_RANGE = (2268, 2276)
SENSES_RANGE = (2278, 2307)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")

# The Roll Result column is a CLOSED SET, which is what makes these tables
# parseable at all: a line is a result label exactly when it is one of these
# four, so the effect cell is simply everything until the next one. The same
# anchor the maneuver table's five-token middle column provides (6i).
RESULT_ROWS = ("Success", "Success by 3+", "Success by 5+", "Critical Success")
HEADERS = {"Roll Result", "Effect"}

HEADING = re.compile(r"^(\d)\. (.+?) \((\w+)/(Hard|Average|Very Hard)\)$")
DIFFICULTIES = {"Hard": "H", "Average": "A", "Very Hard": "VH"}
BY_MARGIN = re.compile(r"^Success by (\d)\+$")

MODIFIER_LINE = re.compile(r"^Modifiers: (.+)$")
MODIFIER = re.compile(r"^([+-]\d+) (.+)$")

# ---- the sentences verify() will not let go quietly -----------------------

SECRET_ROLL = "The GM rolls this secretly"
SEER_MODIFIER = "+6 Seer"
# Appendix A's two rows that price the same circumstance as Intuition's own.
# Kept as the words rather than as row names so the check is against what the
# book says, not against how this repo keyed it.
OVERLAP_WORDS = ("distracted", "combat")

HIGH_AFFINITY = ["Fool", "Hanged Man", "Darkness", "Hermit", "Wheel of Fortune"]
NAMED_PATHWAY = re.compile(r"([A-Z][\w']+(?: [A-Z][\w']+)*) \(([A-Z][\w ]+)\)")
MODERATE_BAND = re.compile(r"moderate\s+bonuses \(\+(\d) to \+(\d)\)")
BASELINE = re.compile(r"baseline \+(\d) SPI")
CONSTRAINED = re.compile(r"granting (\d) SPI at Sequence 9")


def load_lines():
    return {row["n"]: row["text"] for row in json.loads(LINES_PATH.read_text("utf-8"))}


def stream(byn, first, last):
    """The chapter's lines with page furniture dropped.

    A page break falls INSIDE Spiritual Perception's table, between its header
    and its first row, so furniture has to go before the rows are walked
    rather than after.
    """
    out = []
    for n in range(first, last + 1):
        text = byn.get(n, "").strip()
        if not text or FURNITURE.match(text):
            continue
        out.append((n, text))
    return out


def senses(byn):
    """The numbered skill blocks that actually print a resolution table."""
    rows = stream(byn, *SENSES_RANGE)
    blocks, current = [], None
    for _, text in rows:
        heading = HEADING.match(text)
        if heading:
            _, name, attr, difficulty = heading.groups()
            current = {
                "skill": name,
                "attr": attr,
                "difficulty": DIFFICULTIES[difficulty],
                "rows": [],
                "modifiers": [],
            }
            blocks.append(current)
            continue
        if current is None or text in HEADERS:
            continue
        modifiers = MODIFIER_LINE.match(text)
        if modifiers:
            for part in modifiers.group(1).split(";"):
                hit = MODIFIER.match(part.strip())
                if not hit:
                    raise SystemExit(f"unparsed modifier: {part!r}")
                current["modifiers"].append(
                    {"situation": hit.group(2), "modifier": int(hit.group(1))}
                )
            continue
        if text in RESULT_ROWS:
            row = {"result": text}
            margin = BY_MARGIN.match(text)
            # Only the two banded rows carry a margin. "Success" and "Critical
            # Success" carry none rather than a zero -- the distinction
            # RitualCritical.corGained draws between charging nothing and
            # charging zero, and here it is what stops a critical being read
            # as a margin-0 success.
            if margin:
                row["margin"] = int(margin.group(1))
            current["rows"].append(row)
            continue
        if current["rows"] and "effect" not in current["rows"][-1]:
            current["rows"][-1]["effect"] = text
    # Blocks 4 and 5 are cross-references with no table; blocks with no rows
    # are dropped rather than shipped empty.
    return [b for b in blocks if b["rows"]]


def affinity(byn):
    """Chapter 4's claims about chapter 9's Sequence 9 SPI grants."""
    text = " ".join(t for _, t in stream(byn, *AFFINITY_RANGE))
    named = [pathway for _, pathway in NAMED_PATHWAY.findall(text)]
    high = [p for p in named if p in HIGH_AFFINITY]
    band = MODERATE_BAND.search(text)
    baseline = BASELINE.search(text)
    constrained = CONSTRAINED.search(text)
    if not (band and baseline and constrained):
        raise SystemExit("the SPI affinity paragraph no longer prints its bands")
    return [
        {
            "tier": "high",
            "pathways": high,
            "printed": "receive significantly more SPI than other pathways",
        },
        {
            "tier": "moderate",
            "low": int(band.group(1)),
            "high": int(band.group(2)),
            "printed": "Pathways with strong spiritual abilities at Sequence 9 "
            "receive moderate bonuses",
        },
        {
            "tier": "baseline",
            "low": int(baseline.group(1)),
            "high": int(baseline.group(1)),
            "printed": "Reality-leaning pathways receive a baseline +1 SPI",
        },
        {
            "tier": "constrained",
            "pathways": ["Chained"],
            "low": int(constrained.group(1)),
            "high": int(constrained.group(1)),
            "printed": "its spirituality is actively constrained by the potion itself",
        },
    ]


def extract():
    byn = load_lines()
    return senses(byn), affinity(byn)


def verify(blocks, tiers, byn):
    text = "\n".join(t for _, t in stream(byn, *SENSES_RANGE))

    # -- the two tables ---------------------------------------------------
    assert len(blocks) == 2, f"chapter 4 prints two sense tables, parsed {len(blocks)}"
    assert [b["skill"] for b in blocks] == [
        "Spiritual Intuition",
        "Spiritual Perception",
    ], [b["skill"] for b in blocks]
    assert [b["attr"] for b in blocks] == ["SPI", "SPI"]
    assert [b["difficulty"] for b in blocks] == ["H", "A"], "the two differ in difficulty"

    for block in blocks:
        results = [r["result"] for r in block["rows"]]
        assert results == list(RESULT_ROWS), f"{block['skill']}: {results}"
        assert all(r.get("effect") for r in block["rows"]), block["skill"]
        # NO FAILURE ROW. Both tables print successes only, so a failed roll
        # gets nothing rather than an invented consolation row -- and nothing
        # here overrides SuccessRoll's critical rules, because there is
        # nothing printed to override them with.
        assert not any(
            "Failure" in r["result"] for r in block["rows"]
        ), f"{block['skill']} grew a failure row; the reading has to be made again"
        assert [r.get("margin") for r in block["rows"]] == [None, 3, 5, None], block["skill"]

    intuition, perception = blocks
    assert [(m["modifier"], m["situation"]) for m in intuition["modifiers"]] == [
        (2, "meditative state"),
        (-2, "distracted/in combat"),
        (-4, "magically concealed"),
    ], intuition["modifiers"]
    assert [(m["modifier"], m["situation"]) for m in perception["modifiers"]] == [
        (2, "graveyard/ritual site"),
        (-2, "industrial/polluted area"),
        (-4, "entity hiding"),
        (6, "Seer"),
    ], perception["modifiers"]

    # The pathway-named bonus, and the fact that it names a SEQUENCE 9 RANK
    # rather than a pathway -- which is why it is offered and not applied.
    assert SEER_MODIFIER in text, "Perception lost its Seer bonus"
    assert "Fool" not in text, (
        "the sense tables now name a pathway; the Seer bonus named a Sequence 9 "
        "rank, and the decision to offer rather than apply it rests on that"
    )
    assert SECRET_ROLL in text, "Perception lost the sentence handing its roll to the GM"

    # -- the overlap with Appendix A --------------------------------------
    common = json.loads((OUT / "common_modifiers.json").read_text("utf-8"))
    situations = " ".join(e["situation"] for e in common["entries"]).lower()
    intuition_words = intuition["modifiers"][1]["situation"].lower()
    for word in OVERLAP_WORDS:
        assert word in situations, f"Appendix A no longer prices '{word}'"
        assert word in intuition_words, f"chapter 4 no longer prices '{word}'"

    # -- the affinity claims, against chapter 9's own data ----------------
    assert [t["tier"] for t in tiers] == ["high", "moderate", "baseline", "constrained"]
    high = next(t for t in tiers if t["tier"] == "high")
    assert high["pathways"] == HIGH_AFFINITY, high["pathways"]
    moderate = next(t for t in tiers if t["tier"] == "moderate")
    assert (moderate["low"], moderate["high"]) == (2, 6), moderate
    assert next(t for t in tiers if t["tier"] == "baseline")["low"] == 1
    assert next(t for t in tiers if t["tier"] == "constrained")["low"] == 0

    pathways = json.loads((OUT / "pathways.json").read_text("utf-8"))
    grants = {}
    for block in pathways["entries"]:
        if block["sequence"] != 9:
            continue
        grants[block["pathway"]] = int(
            sum(s["delta"] for s in block["stats"] if s["attr"] == "SPI")
        )
    assert grants, "pathways.json stopped recording Sequence 9 SPI"
    top = min(grants[p] for p in HIGH_AFFINITY)
    rest = max(v for p, v in grants.items() if p not in HIGH_AFFINITY)
    assert top > rest, (
        f"the five named pathways no longer receive significantly more SPI: "
        f"lowest named {top}, highest other {rest}"
    )
    assert grants["Chained"] == 0, f"the Prisoner now grants {grants['Chained']} SPI"
    # Every remaining pathway falls in one of the two printed bands. This is
    # the claim most likely to break first, so it names the offender.
    for pathway, spi in sorted(grants.items()):
        if pathway in HIGH_AFFINITY or pathway == "Chained":
            continue
        assert spi == 1 or 2 <= spi <= 6, f"{pathway} grants {spi} SPI, outside both bands"


def write(blocks, tiers):
    (OUT / "spiritual_senses.json").write_text(
        json.dumps(
            {"sourceLines": {CHAPTER: [list(SENSES_RANGE)]}, "entries": blocks},
            indent=1,
            ensure_ascii=False,
        )
        + "\n",
        "utf-8",
    )
    (OUT / "spi_affinity.json").write_text(
        json.dumps(
            {"sourceLines": {CHAPTER: [list(AFFINITY_RANGE)]}, "entries": tiers},
            indent=1,
            ensure_ascii=False,
        )
        + "\n",
        "utf-8",
    )


if __name__ == "__main__":
    blocks, tiers = extract()
    verify(blocks, tiers, load_lines())
    write(blocks, tiers)
    print(f"wrote {len(blocks)} sense tables and {len(tiers)} affinity tiers", file=sys.stderr)
