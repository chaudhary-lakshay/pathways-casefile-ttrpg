"""Extract Chapter 4's two universal spiritual abilities and its SPI affinity claims (p. 35-36).

v6.9i TURNED THE TWO SENSES FROM SKILLS INTO UNIVERSAL ABILITIES
-----------------------------------------------------------------
v6.9d printed Spiritual Intuition (SPI/Hard) and Spiritual Perception
(SPI/Average) as skills, each with a four-row degree table and its own
modifiers. v6.9i prints them as "a universal ability available to any
Beyonder, like Spirit Vision", both with ONE roll and no table:

    Roll: Roll 3d6. The difficulty class is based on however much current SPI
    you have -- if you have 1 SPI, you need a critical roll (3-4); if you have
    9 SPI, you need 9 or lower; and so on.

So the target IS current SPI, and the "1 SPI needs a critical" clause is not
a separate rule: at target 1 only SuccessRoll's 3-4 auto-success can land.
verify() pins both examples, because they are the evidence for that reading.
The degree tables, the modifiers, the +6 Seer and the Appendix A overlap are
gone with the tables that printed them.

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
* **Spirit Vision** -- its universal-ability paragraph (2364-2384) is claimed
  by spirit_vision.json beside the fuller guide at the end of chapter 5.
* **Spirituality Recovery** -- "1 SPI per hour" of rest, which chapter 7
  (Rest) now owns.
* **Ritualistic Magic and Divination Arts** -- one-line cross-references to
  chapters 9 and 8.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "4"
AFFINITY_RANGE = (2355, 2363)
SENSES_RANGE = (2385, 2403)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")

ABILITY_HEADING = re.compile(r"^Universal Beyonder Ability — (.+)$")
ABILITIES = ["Spiritual Intuition", "Spiritual Perception"]
ROLL = "Roll: Roll 3d6. The difficulty class is based on however much current SPI you have"
EXAMPLES = ("if you have 1 SPI, you need a critical roll (3–4)", "if you have 9 SPI, you need 9 or lower")

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
    """The two universal-ability paragraphs, each with its roll sentence."""
    blocks, current = [], None
    for _, text in stream(byn, *SENSES_RANGE):
        heading = ABILITY_HEADING.match(text)
        if heading:
            current = {"ability": heading.group(1), "text": ""}
            blocks.append(current)
        elif current is not None:
            current["text"] = f"{current['text']} {text}".strip()
    return blocks


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
    assert [b["ability"] for b in blocks] == ABILITIES, [b["ability"] for b in blocks]
    for block in blocks:
        assert ROLL in block["text"], f"{block['ability']} no longer rolls against current SPI"
        for example in EXAMPLES:
            assert example in block["text"], f"{block['ability']} lost {example!r}"

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
    print(f"wrote {len(blocks)} universal abilities and {len(tiers)} affinity tiers", file=sys.stderr)
