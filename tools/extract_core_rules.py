"""Extract Chapter 2's core rules (p. 7-10): the burn, the contests, the mechanic.

WHY A CHAPTER CALLED "CORE RULES" WAS NEVER READ
--------------------------------------------------
It sat at 0.0% and looked like the safest chapter in the book to skip: every
heading names something M0 already built. `SuccessRoll` is the success roll,
`Difficulty.defaultPenalty` is the skill defaults, `SecondaryCharacteristics`
is the secondary table. Read that way the chapter is a summary of the engine.

It is not. Two of its rules reach a roll and nothing in the app applied
either, and one of them is the general form of a rule two later chapters
implement in scoped copies. This is 6t's lesson in its purest form -- a
chapter at 0.0% disproves nothing, and "I already implemented that" is a
weaker reason to skip a page than a percentage is.

WHAT WAS GOING UNAPPLIED
-------------------------
**Burning FP and SPI** (p. 9). "Spend 1 FP to gain +1 on any roll based on ST,
DX, or HT ... Max +5 per roll." "Spend 1 SPI to gain +1 on any SPI-based or
supernatural skill roll ... There is no cap." Both say "You may use this after
seeing the result", and both end with a sentence nothing enforced: "You cannot
spend both FP and SPI on the same roll."

`Divination.BURN_PER_SPI` (ch 6.5, p. 55) and `RitualResolution.spiFor`'s
`burned` (ch 7, p. 66) are the same rule scoped to those two chapters, and
they AGREE with this one exactly -- 1 SPI, +1, no cap. That agreement is the
warrant for the general rule, the standard 6ad set and 6v is why it must be
per-table. What neither chapter prints is the FP half, the +5 cap, or the
either-or.

**Specific still beats general.** The general burn belongs to the plain skill
roll; a ritual and a divination keep their own, because offering both charges
one point of effort twice -- the scoping the CoR ladder needed (6h), that
keeps ch 13's boons off Phase I preparation (6r), and that withholds Appendix
A's darkness on a ranged shot (6ae).

**Quick Contests** (p. 9) are a complete printed procedure with six worked
rows, and several sections of the handover have recorded a Quick Contest as
"deliberately not modelled" -- Feint (6o), and six more in 6ah. The reason
given each time was that this app holds one sheet, which is still true and is
why nothing here resolves a contest automatically. What was NOT true is that
the book leaves the procedure unstated. It is extracted, and the resolution
rule is arithmetic on two margins that a GM can apply at the table.

A CORRECTION TO 6ad, AND THE BULLETS ARE WHY
----------------------------------------------
6ad adopted Appendix A's degree-of-success ladder partly on the grounds that
"Ch. 2 line 278 says 'Margins matter -- the degree of success or failure often
determines quality of outcome' and tabulates nothing".

Chapter 2 tabulates it. Lines 281-283 print "Success by 0-2: Barely
succeeded", "Success by 3-4: Clean success", "Success by 5+: Exceptional
result" -- the same three bands at the same edges, under different names. The
sentence was read and the bulleted list immediately under it was not, because
**this PDF prints list bullets as U+007F** (6g, and 6ac lost a debugging cycle
to it): a bulleted list can look like nothing at all.

The conclusion survives and is stronger. Appendix A remains the only page that
NAMES "Good Success" and "Excellent Success", so `DegreeOfSuccess`'s row keys
still come from there; and chapter 2 independently prints the same edges,
which is corroboration rather than a competing table. verify() pins the
agreement in both directions -- if either page ever moved a band, that would
be a finding.

WHAT IS DELIBERATELY LEFT
--------------------------
* **Attribute Scale Reference** and **What the Numbers Mean** (p. 8) -- two
  descriptive tables (a score's descriptor, a target's odds). Flavour and
  probability, with no rule to apply.
* **The five-step play loop** (p. 7) -- how to run a session.
* **"Critical Success in Combat: double damage"** is claimed here as part of
  the success-roll group but NOT applied: damage in this app is an expression,
  never a rolled total, so there is nothing to double. The same reason a called
  shot's wounding multiplier is quoted beside the damage rather than multiplied
  into it (6l).

THE EXCLUSION THAT WAS FALSE IN BOTH HALVES
--------------------------------------------
This docstring used to leave the Secondary Characteristics table out, saying
"`SecondaryCharacteristics` implements every formula and ch 3 prints the
costs, which are already extracted. A second span here would double-claim."

Re-run as a query, every clause of that is wrong:

* Chapter 3 prints **no** cost-to-modify for a secondary. Its `pts/level`
  lines are ADVANTAGE rows -- Hard to Kill 2/level, Charisma 5/level, Acute
  Senses 5/level -- which is a different thing wearing the same words. That
  resemblance is what the exclusion was built on.
* "Cost to Modify" occurs **once in the whole book**, at line 357 on page 8.
  Appendix A names the characteristics in its glossary and prices none.
* Nothing extracted them, so there was nothing to double-claim. The claimed
  chapter 2 spans stop at 285 and resume at 387; this table sits at 353-386,
  in the gap.

The consequence was that `SecondaryCharacteristics`' six cost constants had
no tie to any printed line. They are pinned against an EDIT -- change one and
`SecondaryCharacteristicsTest` reddens -- but nothing was pinned against the
BOOK, so a re-extraction that moved a price would have left the engine
billing the old one, and every test would have stayed green. That is 6cg's
direction-of-the-pin with no pin in either direction, and `PointBudget`
charges these to every character ever built.

The table also settles something by printing it: three of its nine rows are
NOT buyable at any price -- Spirituality "Fixed at 0", CoR "Tracked in play;
never bought", Digestion "Tracked through acting". Section 4.4's rule that
SPI can never be an `Attr` gets a second, independent printing here.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "2"
MECHANIC_RANGE = (276, 285)
# The Secondary Characteristics table, heading to last cell. It ends where
# BURN_RANGE begins, and begins after MECHANIC_RANGE ends -- the gap the old
# exclusion said was already claimed.
SECONDARY_RANGE = (353, 386)
BURN_RANGE = (387, 395)
CONTEST_RANGE = (396, 422)
# The Skill Defaults table straddles a page break: its heading and column
# headers are the last three lines of page 9, its body is on page 10, which
# chapters.json gives to Chapter 3. Claimed under "2" anyway, because that is
# where the content is; coverage.py will credit the second span to nobody.
# Seventh instance of that page-granularity gap (6t, 6v, 6ae, 6af, 6ag, 6aj).
DEFAULT_RANGES = ((423, 427), (430, 442))

# This PDF prints list bullets as U+007F. Stripping it is what makes the
# success-roll bullets visible at all -- 6ad read the sentence above them and
# reported the list as absent.
BULLET = "\x7f"
FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")

BAND = re.compile(r"^(Success|Failure) by (\d+)(?:[-–](\d+))?\+?: (.+)$")
CRITICAL = re.compile(r"^(Critical (?:Success|Failure)[^:]*): (.+)$")

BURN_POOLS = {"FP": "physical", "SPI": "mystical"}
BURN_LINE = re.compile(r"^(FP|SPI) -> (\w+) Rolls\. Spend 1 (?:FP|SPI) to gain \+1 on (.+)$")
CAP = re.compile(r"Max \+(\d+) per roll")
NO_CAP = "There is no cap"
EITHER_OR = "You cannot spend both FP and SPI on the same roll."
AFTER_THE_FACT = "You may use this after seeing the result"

CONTEST_HEADERS = {"Situation", "Attacker Rolls", "Defender Rolls"}
TIES_TO_DEFENDER = "Ties always go to the defender."
LARGER_MARGIN = "The winner is the one who succeeds"
RESOLUTION_MARKER = "If both succeed"

# The secondary table's three column headers, and the nine rows it prints.
SECONDARY_HEADING = "Secondary Characteristics"
SECONDARY_HEADERS = {"Characteristic", "Base Formula", "Cost to Modify"}
SECONDARY_INTRO = "Derived from primary attributes"
# The printed name -> the key a consumer joins on. Pinned rather than derived:
# if the book renames a row, that is a finding and not a slug to regenerate.
SECONDARY_KEYS = {
    "HP (Hit Points)": "hp",
    "Will": "will",
    "Perception (Per)": "per",
    "FP (Fatigue Points)": "fp",
    "Spirituality": "spi",
    "CoR": "cor",
    "Basic Speed": "basicSpeed",
    "Basic Move": "basicMove",
    "Digestion %": "digestion",
}
# "±2 pts per level", "±5 pts per 0.25". The sign is U+00B1: the column prices
# BUYING and SELLING at one rate, which is why costOf refunds a negative delta.
SECONDARY_COST = re.compile(r"^±(\d+) pts per (level|0\.25)$")
# The three rows the table itself declares unbuyable, with the words it uses.
SECONDARY_UNBUYABLE = {
    "spi": "Fixed at 0; see Chapter 4",
    "cor": "Tracked in play; never bought",
    "digestion": "Tracked through acting",
}

DEFAULT_ROW = re.compile(r"^(Easy|Average|Hard|Very Hard)$")
DEFAULT_VALUE = re.compile(r"^Attribute - (\d+)$")
NO_DEFAULT = "No default"
DIFFICULTY_CODES = {"Easy": "E", "Average": "A", "Hard": "H", "Very Hard": "VH"}


def load_lines():
    return {row["n"]: row["text"] for row in json.loads(LINES_PATH.read_text("utf-8"))}


def stream(byn, *ranges):
    """The chapter's lines, page furniture and U+007F bullets removed."""
    out = []
    for first, last in ranges:
        for n in range(first, last + 1):
            text = (byn.get(n) or "").replace(BULLET, "").strip()
            if not text or FURNITURE.match(text):
                continue
            out.append((n, text))
    return out


def mechanic(byn):
    """The success roll's own bands, as chapter 2 prints them."""
    rows = []
    for _, text in stream(byn, MECHANIC_RANGE):
        band = BAND.match(text)
        if band:
            kind, low, high, meaning = band.groups()
            row = {"outcome": kind, "low": int(low), "meaning": meaning}
            # "Success by 5+" prints no upper end. Absent rather than a large
            # number, so nothing can compare against a ceiling the book never
            # printed -- the open-cell reading RitualWeight already needed.
            if high:
                row["high"] = int(high)
            rows.append(row)
            continue
        critical = CRITICAL.match(text)
        if critical:
            rows.append({"outcome": critical.group(1), "meaning": critical.group(2)})
    return rows


def burn(byn):
    """Burning FP and SPI to push a roll (p. 9)."""
    lines = stream(byn, BURN_RANGE)
    rows = []
    for index, (_, line) in enumerate(lines):
        hit = BURN_LINE.match(line)
        if not hit:
            continue
        pool, _, scope = hit.groups()
        # The rule wraps onto the next line, and the cap lives there.
        tail = " ".join(t for _, t in lines[index : index + 3])
        # Both scopes wrap mid-bracket -- FP's at "...lifting, etc" and SPI's
        # at "(Ritualistic Magic, Spiritual" / "Perception, ...". Keep
        # consuming while a bracket is open, the same rule the Vitals
        # multiplier (6l) and a shield's wrapped name (6o) already needed;
        # cutting at the line end silently truncates a list of examples.
        step = 1
        while scope.count("(") > scope.count(")") and index + step < len(lines):
            scope = f"{scope} {lines[index + step][1]}"
            step += 1
        # ...and the scope ENDS at that closing bracket, not at the first
        # full stop: SPI's example list contains "Occultism vs. Beyonder
        # phenomena", so splitting on ". " truncates it mid-example. The same
        # "vs." trap extract_fright.py had to be taught (6c).
        row = {
            "pool": pool,
            "kind": BURN_POOLS[pool],
            "perPoint": 1,
            "scope": scope[: scope.rindex(")") + 1].strip(),
        }
        # The cap is on the FP half only. The SPI half says so in words --
        # "There is no cap" -- so it carries no field rather than a huge one,
        # the distinction RitualCritical.corGained draws between printing
        # nothing and printing zero.
        if pool == "FP":
            cap = CAP.search(tail)
            if not cap:
                raise SystemExit("the FP burn lost its +5 cap")
            row["max"] = int(cap.group(1))
        rows.append(row)
    if len(rows) != 2:
        raise SystemExit(f"the book prints two burnable pools, parsed {len(rows)}")
    return rows


def contests(byn):
    """The six worked Quick Contest rows (p. 9)."""
    rows, cells = [], []
    started = False
    for _, text in stream(byn, CONTEST_RANGE):
        if text in CONTEST_HEADERS:
            started = True
            continue
        # The table ends where the resolution paragraph begins, and that
        # paragraph names itself: "If both succeed: ...". Stopping on the
        # MARKER rather than after counting rows is what keeps a wrapped
        # sentence -- "...if the situation" / "allows. If one succeeds..." --
        # from being read as two more cells. A count would have hidden it.
        if text.startswith(RESOLUTION_MARKER):
            break
        if not started:
            continue
        cells.append(text)
        if len(cells) == 3:
            rows.append(
                {"situation": cells[0], "attacker": cells[1], "defender": cells[2]}
            )
            cells = []
    if cells:
        raise SystemExit(f"a contest row is incomplete: {cells}")
    return rows


def defaults(byn):
    """Chapter 2's untrained defaults -- the primary printing."""
    rows, pending = [], None
    for _, text in stream(byn, *DEFAULT_RANGES):
        if DEFAULT_ROW.match(text):
            pending = text
            continue
        if pending is None:
            continue
        value = DEFAULT_VALUE.match(text)
        if value:
            rows.append(
                {
                    "difficulty": pending,
                    "code": DIFFICULTY_CODES[pending],
                    "penalty": -int(value.group(1)),
                }
            )
            pending = None
        elif text.startswith(NO_DEFAULT):
            # Very Hard carries NO penalty rather than a large one: the book
            # says the roll cannot be attempted, which is a refusal and not a
            # number. Same shape as a Broken mystical language (6x).
            rows.append({"difficulty": pending, "code": DIFFICULTY_CODES[pending]})
            pending = None
    return rows


def secondaries(byn):
    """The nine secondary characteristics, priced as the table prices them.

    Three cells per row and no wrapping anywhere in this table, so the rows
    are taken in threes off the stream once the heading, the intro sentence
    and the three column headers are dropped.
    """
    cells = [
        text for _, text in stream(byn, SECONDARY_RANGE)
        if text != SECONDARY_HEADING
        and text not in SECONDARY_HEADERS
        and not text.startswith(SECONDARY_INTRO)
    ]
    if len(cells) % 3:
        raise ValueError(
            f"the secondary table is {len(cells)} cells, not a multiple of "
            "three; a row wrapped or a column moved"
        )

    rows = []
    for name, formula, cost in zip(cells[0::3], cells[1::3], cells[2::3]):
        key = SECONDARY_KEYS.get(name)
        if key is None:
            raise ValueError(
                f"the table prints a characteristic named {name!r}, which no "
                "consumer has a key for. A renamed row is a finding"
            )
        priced = SECONDARY_COST.fullmatch(cost)
        rows.append({
            "key": key,
            "characteristic": name,
            "formula": formula,
            "cost": cost,
            # None where the table refuses a price rather than printing one.
            "pointsPerStep": int(priced.group(1)) if priced else None,
            "step": priced.group(2) if priced else None,
        })
    return rows


def extract():
    byn = load_lines()
    return mechanic(byn), burn(byn), contests(byn), defaults(byn), secondaries(byn)


def verify(bands, pools, rows, defs, secs, byn):
    burn_text = " ".join(t for _, t in stream(byn, BURN_RANGE))
    contest_text = " ".join(t for _, t in stream(byn, CONTEST_RANGE))
    default_text = " ".join(t for _, t in stream(byn, *DEFAULT_RANGES))

    # -- the secondaries --------------------------------------------------
    if [r["key"] for r in secs] != list(SECONDARY_KEYS.values()):
        raise ValueError(
            f"the secondary table's rows moved: {[r['key'] for r in secs]}"
        )
    for key, words in SECONDARY_UNBUYABLE.items():
        row = next(r for r in secs if r["key"] == key)
        if row["pointsPerStep"] is not None:
            raise ValueError(f"{key} is now priced at {row['pointsPerStep']}, and was not")
        if row["cost"] != words:
            raise ValueError(
                f"{key} used to refuse a price with {words!r} and now says "
                f"{row['cost']!r}. Whether it can be bought is a rule"
            )
    # Six priced, three refused, and the table says which is which itself.
    priced = {r["key"]: r["pointsPerStep"] for r in secs if r["pointsPerStep"]}
    if priced != {"hp": 2, "will": 5, "per": 5, "fp": 3, "basicSpeed": 5, "basicMove": 5}:
        raise ValueError(
            f"a secondary characteristic changed price: {priced}. "
            "SecondaryCharacteristics bills every character with these"
        )
    # Basic Speed is the one row priced per quarter rather than per level, and
    # SecondaryDelta.basicSpeedQuarters exists because of it. A parser that
    # dropped the unit would price a whole point of Speed at 5 instead of 20.
    speed = next(r for r in secs if r["key"] == "basicSpeed")
    if speed["step"] != "0.25":
        raise ValueError(f"Basic Speed is now priced per {speed['step']!r}, not per 0.25")
    if any(r["step"] not in (None, "level") for r in secs if r["key"] != "basicSpeed"):
        raise ValueError("a second row is now priced per fraction; only Speed was")
    # The formulas the engine reproduces, checked as printed rather than as
    # remembered -- Will and Per both from IQ, HP from ST, FP from HT.
    formulas = {r["key"]: r["formula"] for r in secs}
    for key, expected in (("hp", "= ST (9)"), ("fp", "= HT (9)"),
                          ("will", "= IQ (9)"), ("per", "= IQ (9)")):
        if formulas[key] != expected:
            raise ValueError(f"{key} is now derived {formulas[key]!r}, not {expected!r}")

    # -- the burn ---------------------------------------------------------
    fp, spi = pools
    assert (fp["pool"], fp["max"]) == ("FP", 5), fp
    assert "max" not in spi, "the SPI burn grew a cap the book does not print"
    assert NO_CAP in burn_text, "the SPI burn lost the sentence saying it has no cap"
    assert AFTER_THE_FACT in burn_text, "the burn stopped being usable after the roll"
    assert EITHER_OR in burn_text, (
        "the either-or sentence is gone; it is the only thing stopping a "
        "caller spending both pools on one roll"
    )
    assert "ST, DX, or HT" in fp["scope"], fp["scope"]
    assert "SPI-based" in spi["scope"], spi["scope"]
    # Both scopes wrap mid-bracket. A truncated cell still parses and still
    # reads plausibly, so the closing bracket is what proves the list of
    # examples survived.
    for row in pools:
        assert row["scope"].count("(") == row["scope"].count(")"), row["scope"]
    assert "Occultism" in spi["scope"], spi["scope"]

    # -- the contests -----------------------------------------------------
    assert len(rows) == 6, f"the book prints six contest rows, parsed {len(rows)}"
    assert rows[0]["situation"] == "Deception vs. detection", rows[0]
    assert TIES_TO_DEFENDER in contest_text, "the tie-break rule is gone"
    assert LARGER_MARGIN in contest_text, "the larger-margin rule is gone"
    # The row that names this app's own caster, so a GM can find it.
    assert any("Ritualistic Magic" in r["attacker"] for r in rows), rows
    for row in rows:
        assert row["attacker"] and row["defender"], row

    # -- the defaults, against Appendix A's restatement --------------------
    assert [d["code"] for d in defs] == ["E", "A", "H", "VH"], defs
    assert [d.get("penalty") for d in defs] == [-4, -5, -6, None], defs
    assert NO_DEFAULT in default_text, "Very Hard stopped refusing the attempt"
    # Appendix A's "Using untrained skill (default) / Attribute -4 to -6" is a
    # RESTATEMENT of this table's two ends (6ae). Both must keep saying so.
    common = json.loads((OUT / "common_modifiers.json").read_text("utf-8"))
    untrained = next(e for e in common["entries"] if e.get("isDefault"))
    assert "-4 to -6" in untrained["printed"], untrained

    # -- the bands, against Appendix A ------------------------------------
    assert bands[0]["outcome"].startswith("Critical Success"), bands[0]
    plain = next(b for b in bands if b.get("low") == 0)
    good = next(b for b in bands if b.get("low") == 3)
    exceptional = next(
        b for b in bands if b.get("low") == 5 and b["outcome"] == "Success"
    )
    assert (plain["high"], good["high"]) == (2, 4), (plain, good)
    assert "high" not in exceptional, "Success by 5+ grew a ceiling"
    ladder = json.loads((OUT / "success_ladder.json").read_text("utf-8"))["entries"]
    edges = sorted({-(e.get("offset") or 0) for e in ladder if e.get("comparison") == "<="})
    assert edges == [0, 3, 5], (
        f"Appendix A's ladder no longer bands at 0/3/5 ({edges}); chapter 2 "
        "prints the same three edges and the two are now in conflict"
    )
    # ...and only the appendix NAMES them. That asymmetry is what makes it a
    # definition rather than a summary (6ad), so it is asserted here too.
    named = {e["result"] for e in ladder}
    assert {"Good Success", "Excellent Success"} <= named, named
    assert not any(
        "Good Success" in b["meaning"] or "Excellent Success" in b["meaning"]
        for b in bands
    ), "chapter 2 now names the bands; 6ad's warrant rested on it not doing so"


def write(bands, pools, rows, defs, secs):
    files = {
        "secondary_costs.json": ([list(SECONDARY_RANGE)], secs),
        "core_mechanic.json": ([list(MECHANIC_RANGE)], bands),
        "effort_burn.json": ([list(BURN_RANGE)], pools),
        "quick_contests.json": ([list(CONTEST_RANGE)], rows),
        "skill_defaults.json": ([list(r) for r in DEFAULT_RANGES], defs),
    }
    for name, (spans, entries) in files.items():
        (OUT / name).write_text(
            json.dumps(
                {"sourceLines": {CHAPTER: spans}, "entries": entries},
                indent=1,
                ensure_ascii=False,
            )
            + "\n",
            "utf-8",
        )


if __name__ == "__main__":
    bands, pools, rows, defs, secs = extract()
    verify(bands, pools, rows, defs, secs, load_lines())
    write(bands, pools, rows, defs, secs)
    print(
        f"wrote {len(bands)} bands, {len(pools)} pools, {len(rows)} contests, "
        f"{len(defs)} defaults, {len(secs)} secondaries",
        file=sys.stderr,
    )
