"""Spirit Vision, hit locations, markdown and the GURPS deferrals. Split out of test_tools.py."""
import json, re

import pytest

import extract_spirit_vision
import extract_wounds

from testsupport import CONTENT, OUT, ROOT, needs_pdf


def test_every_hit_location_threshold_is_read_from_its_own_cell():
    """The five cells that print a damage threshold, and the four that do not.

    The `Special Effect on Hit` column had no reader at all -- not in
    production, not in a test -- while carrying thresholds the damage screen
    already holds both sides of. These are the cells `parse_thresholds` reads,
    named rather than counted, so a cell changing its wording fails here
    instead of silently dropping an effect.

    **Arm and Leg print two numbers and only one of them is the rule.** The
    governing threshold is "> 1/2 HP of limb"; "~ HP/3" is the BOOK's own
    approximation of that in character HP, tilde and all. Storing one divisor
    without saying so would let a test bless the approximation as the rule,
    so `approximate` travels with it.
    """
    content = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"
    rows = json.loads((content / "hit_locations.json").read_text(encoding="utf-8"))["entries"]
    by_target = {r["target"]: r for r in rows}

    assert by_target["Arm"]["thresholds"] == [
        {"outcome": "Drop held item", "minDamage": 3},
        {"outcome": "Crippled", "hpDivisor": 3, "approximate": True},
    ]
    assert by_target["Leg"]["thresholds"] == [
        {"outcome": "Knock prone", "minDamage": 3},
        {"outcome": "Crippled", "hpDivisor": 3, "approximate": True},
    ]
    # The Hand drops the item on ANY hit -- its cell prints no damage
    # condition where Arm and Leg both print ">= 3".
    assert by_target["Hand"]["thresholds"] == [
        {"outcome": "Drop held item"},
        {"outcome": "Crippled", "hpDivisor": 6},
    ]
    assert by_target["Neck"]["thresholds"] == [{"outcome": "Choke possible", "hpDivisor": 2}]
    assert by_target["Eye"]["thresholds"] == [{"outcome": "Blind", "hpDivisor": 10}]

    # And the four rows whose cells print no damage threshold at all. Each is
    # refused for its own reason, recorded in production Kotlin; none of them
    # may acquire a threshold here by resemblance.
    for target in ("Torso", "Face", "Skull", "Vitals"):
        assert by_target[target].get("thresholds", []) == [], target

    # The prose stays verbatim beside the structure, because it says things
    # the structure does not -- "unusable until healed", "bypasses head DR".
    assert all(r["effect"] for r in rows)


def test_an_outcome_name_is_closed_at_a_word_boundary():
    """Why OUTCOMES holds anchored regexes and not a prefix test.

    A bare `startswith` reads "Blindness or disfigurement at > HP/10" -- the
    Face cell's wording one re-extraction away -- as the Eye's definite
    "Blind", prices it, fires it, and leaves the raise-guard silent because it
    had found a name. That is the worst failure this parser has available: not
    a crash, not a dropped clause, but a confident wrong effect.

    The same closure separates "Knock prone" from "Knockdown", which share
    five letters and are the only two outcomes in the column that do.
    """
    assert extract_wounds.parse_thresholds("Blind at > HP/10", "Eye") == [
        {"outcome": "Blind", "hpDivisor": 10}
    ]
    with pytest.raises(ValueError, match="cannot name"):
        extract_wounds.parse_thresholds("Blindness or disfigurement at > HP/10", "Face")

    assert extract_wounds.parse_thresholds("Knock prone if damage >= 3", "Leg") == [
        {"outcome": "Knock prone", "minDamage": 3}
    ]
    # Knockdown names no threshold and must stay silent -- Shock records why.
    assert extract_wounds.parse_thresholds("Knockdown at -10", "Skull") == []


def test_the_hit_location_table_now_claims_the_rule_that_governs_it():
    """The seam, closed -- and the span that closes it stops where it should.

    2872 is the NEXT table's heading ("Optional -- Wounding Multipliers") and
    2873 is where `wounding_multipliers.json` begins, so a span that ran one
    line further would claim a heading it does not read and collide with its
    neighbour.
    """
    content = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"
    spans = json.loads((content / "hit_locations.json").read_text(encoding="utf-8"))["sourceLines"]["5"]
    claimed = {n for lo, hi in spans for n in range(lo, hi + 1)}
    assert {2869, 2870, 2871} <= claimed, sorted(claimed)[-6:]
    assert 2872 not in claimed, "the span now claims the next table's heading"

    others = json.loads((content / "wounding_multipliers.json").read_text(encoding="utf-8"))
    for lo, hi in others["sourceLines"]["5"]:
        assert not (claimed & set(range(lo, hi + 1))), (lo, hi)


def test_spirit_vision_is_universal_and_section_c_grants_nothing():
    """v6.9i made Spirit Vision a universal ability: "It is not tied to any
    single pathway, sequence, or potion." v6.9d's Section C was a swap (it
    listed Visionary, which granted nothing, and omitted Moon, which did);
    that question died with the grants. Section C is display now -- "Pathway
    and Sequence do not grant access to it" -- and names three pathways."""
    content = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"
    sv = json.loads((content / "spirit_vision.json").read_text(encoding="utf-8"))
    assert "grants" not in sv
    listed = [r["name"] for r in sv["pathwayDifferences"]]
    assert listed == extract_spirit_vision.EXPECTED_PATHWAYS

    byn = {l["n"]: l["text"] for l in json.loads((OUT / "lines.json").read_text(encoding="utf-8"))}
    universal = " ".join(byn[n] for n in range(*extract_spirit_vision.UNIVERSAL_RANGE))
    assert "It is not tied to any single pathway, sequence, or potion" in universal
    guide = " ".join(byn[n] for n in range(3126, 3128))
    assert "Pathway and Sequence do not grant access to it" in guide, guide


def test_ether_body_awareness_and_spirit_vision_are_two_advantages():
    """One name used to be two things: ch. 3's 5-point Ether Body Awareness,
    bought "even without Spirit Vision", and v6.9d's Seer Special Form of
    Spirit Vision. v6.9i deleted the Special Forms and prices Spirit Vision
    itself as a 10-point advantage, so the two are distinct catalogue rows and
    the disclaimer still keeps them apart."""
    content = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"
    sv = json.loads((content / "spirit_vision.json").read_text(encoding="utf-8"))
    advantages = {a["name"]: a for a in
                  json.loads((content / "advantages.json").read_text(encoding="utf-8"))["entries"]}
    assert "specialForms" not in sv
    assert advantages["Ether Body Awareness"]["cost"] == 5
    assert "even without Spirit Vision" in advantages["Ether Body Awareness"]["notes"]
    assert advantages["Spirit Vision"]["cost"] == 10


def test_markdown_repair_restores_arrows_and_bullets():
    """markitdown's glyph mapping for this PDF is worse than pypdf's.

    It emits a bare `fi` ligature where the book prints an arrow, and
    `(cid:127)` where it prints a bullet. Both survive every byte-count and
    line-count completeness check -- "Status 1 fi +1" reads as ordinary text --
    so `extract_markdown.repair` fixes them and `main()` refuses to write a
    file where either shape remains. Measured on v6.9d: 18 arrows, 261 bullets.
    """
    import extract_markdown

    # Mid-line, the shape the first lookahead already caught.
    assert extract_markdown.repair("Status 1 fi +1 reaction") == "Status 1 \u2192 +1 reaction"
    # End of line: the arrow the first lookahead MISSED, because it required a
    # trailing space or paren and a wrapped line offers neither.
    assert extract_markdown.repair("soft liquor (wine, etc.) fi\nHT") == "soft liquor (wine, etc.) \u2192\nHT"
    assert extract_markdown.repair("(cid:127) FP fi 1 per 10 minutes") == "\u2022 FP \u2192 1 per 10 minutes"

    # The ligature inside a word is a real "fi", not an arrow, and must survive.
    assert extract_markdown.repair("20% efficiency") == "20% efficiency"
    assert extract_markdown.repair("classified") == "classified"


def test_markdown_rendering_loses_nothing_that_book_txt_has():
    """book.md is a SECOND rendering, never a replacement for book.txt.

    book.txt's global line numbers are this repo's citation anchor (coverage.py
    compares line sets; the tests above pin book lines by number), so the two
    files must both exist and book.md must not be missing content book.txt has.
    """
    md = OUT / "book.md"
    if not md.exists():
        pytest.skip("book.md not built; run tools/extract_markdown.py")

    normalise = lambda s: re.sub(r"\W+", "", s).lower()
    body = normalise(md.read_text(encoding="utf-8"))
    lines = [
        re.sub(r"^\s*\d+:\s?", "", line)
        for line in (OUT / "book.txt").read_text(encoding="utf-8").splitlines()
    ]
    missing = [l for l in lines if len(normalise(l)) >= 30 and normalise(l) not in body]
    assert missing == [], f"{len(missing)} book.txt lines absent from book.md"

    # The gain that justifies the second rendering: pdfplumber recovers ruled
    # tables as pipe rows where pypdf flattens them into prose.
    body_text = md.read_text(encoding="utf-8")
    rows = sum(1 for l in body_text.splitlines() if l.startswith("|"))
    assert rows > 1000, f"only {rows} table rows; the table recovery regressed"

    # normalise() above strips every non-word character, so it is BLIND to the
    # failure this file exists to prevent: markitdown substituting a glyph
    # rather than dropping a line. "Status 1 fi +1" survives the containment
    # check untouched. These three counts are the part that can see it.
    book_txt = (OUT / "book.txt").read_text(encoding="utf-8")
    assert body_text.count("→") == book_txt.count("→"), (
        "book.md and book.txt disagree on arrow count; either the repair "
        "missed one or it substituted a real standalone 'fi'"
    )
    assert "(cid:" not in body_text, "unmapped glyphs survived into book.md"
    # v6.9d printed 261 bullets and v6.9i 175, so the floor is book.txt's own.
    assert body_text.count("•") >= book_txt.count("\x7f"), "the bullet repair regressed"


@needs_pdf
def test_chapter_7_governs_spi_recovery_and_the_appendix_says_less():
    """Which printing of the SPI recovery rule governs.

    v6.9i moved the rule to Chapter 7: Rest, Sec. II (3495-3500). Ch. 4
    (2417-2421) now points there, and Appendix A (8973-8979) is three cells.
    Ch. 7 carries the interruption clause the summary drops, and adds the
    Cogitation roll that doubles the rate. v6.9d's "the standard and only
    recovery rate" is now "the standard recovery rate": the exclusivity
    word is gone.
    """
    byn = {l["n"]: l["text"] for l in json.loads((OUT / "lines.json").read_text(encoding="utf-8"))}

    rule = " ".join(byn[n] for n in range(3495, 3501))
    assert "recovers 1 SPI" in rule and "per hour" in rule, rule
    assert "This is the standard recovery rate" in rule, rule
    assert "only recovery rate" not in rule, rule
    assert "interrupts rest and halts" in rule and "recovery" in rule, rule
    assert "SPI recovery is doubled" in rule, rule

    pointer = " ".join(byn[n] for n in range(2417, 2421))
    assert "see Chapter 7: Rest, Section II" in pointer, pointer

    summary = " ".join(byn[n] for n in range(8973, 8980))
    assert "1 SPI per hour" in summary, summary
    assert "halts" not in summary and "Cogitation" not in summary, summary


@needs_pdf
def test_the_bard_restores_spi_outside_rest():
    """An active pathway ability with its own scope, not a second rest rate:
    the Bard is excluded from their own effect. This test pins the ability,
    never a priority between the two printings."""
    byn = {l["n"]: l["text"] for l in json.loads((OUT / "lines.json").read_text(encoding="utf-8"))}

    song = " ".join(byn[n] for n in range(4911, 4913))
    assert "Spiritual Recovery" in song, f"the buff list lost the option: {song!r}"
    assert "recover 1 SPI at the song" in song, f"the buff no longer restores SPI: {song!r}"
    assert "Bard cannot benefit from this effect" in song, song
    assert "restores 2 SPI instead of 1" in byn[5693], byn[5693]


@needs_pdf
def test_spirit_vision_costs_one_spi_per_activation_in_every_printing():
    """v6.9d priced Spirit Vision twice and differently (activation in 7.6,
    maintenance per minute in ch. 4). v6.9i prints one rule: ch. 4's "1 SPI
    per activation, regardless of duration", ch. 3's advantage "Activate by
    ...", and the guide's "Activate by expending 1 SPI"."""
    byn = {l["n"]: l["text"] for l in json.loads((OUT / "lines.json").read_text(encoding="utf-8"))}
    universal = " ".join(byn[n] for n in range(*extract_spirit_vision.UNIVERSAL_RANGE))
    assert "Cost: 1 SPI per activation, regardless of duration." in universal
    assert "Activate by expending 1 SPI" in byn[3082], byn[3082]
    assert not any("per minute to maintain" in t for t in byn.values())


# 6dr ran the "places the book defers to a GURPS rule it does not reprint"
# census by hand and reported six sites. These two tests re-derive it as a
# QUERY and key it to what claims each line, because the verdict on `Shaken`
# -- deferred from 6dk through 6dw -- rests on both facts.
GURPS_DEFERRAL = re.compile(r"\bp\.\s*B\d+|\b(?:per|per normal|Standard) GURPS\b")


def _gurps_deferrals():
    """Every line that defers to the GURPS Basic Set, keyed by where it starts.

    Scanned over each line JOINED to the one after it, because a citation
    wraps: 3653 ends "(see GURPS Techniques, p." and 3654 opens "B229)". A
    line-at-a-time search returns seven sites and silently drops that one --
    which is how this test's first version was written, and it is 6ba's rule
    (a rule spanning a boundary is invisible to a scan that stops at it).
    """
    byn = {l["n"]: l["text"] for l in json.loads((OUT / "lines.json").read_text(encoding="utf-8"))}
    sites = []
    for n in sorted(byn):
        joined = byn[n] + " " + byn.get(n + 1, "")
        # A match starting inside line n belongs to n, wherever it ends.
        if any(m.start() < len(byn[n]) for m in GURPS_DEFERRAL.finditer(joined)):
            sites.append(n)
    return byn, sites


def test_the_gurps_deferrals_outside_the_ability_prose_are_all_ruled_on():
    """Where the class sits, which is what settles 6dw's question.

    Seven sites defer to the GURPS Basic Set. 246 is the book's own framing
    ("Standard GURPS rules apply unless this book explicitly overrides them")
    and prices nothing. Of the SIX that carry a rule, five sit inside a
    `pathways.json` Sequence block's `ability` prose -- the field
    `PotionDigestion.UNPARSED` already records as unbuilt work -- and the one
    that does not is claimed by a content file and ruled on already:

      * 3619-3620 is `divination_methods.json`. Its specialisation
        technique is REFUSED in `Divination.UNAPPLIED` (6cs).

    v6.9d's eighth, spirit damage's "HP <= 0: Standard GURPS death/dying
    rules", went with v6.9i's rewrite of that table.

    So there is no unruled GURPS deferral outside that prose, and none
    inside it that the prose's own record does not already cover.

    Fails if a re-extraction moves a line, or if an eighth deferral appears
    in prose no Sequence block claims -- either of which reopens the verdict.
    """
    byn, sites = _gurps_deferrals()
    assert sites == [246, 3619, 5835, 6606, 7267, 7296, 7299], sites
    assert "unless this book explicitly overrides them" in byn[246], (
        f"246 is no longer the book-level framing: {byn[246]!r}"
    )
    assert "B229" in byn[3620], f"the wrapped citation moved: {byn[3620]!r}"

    entries = json.loads((CONTENT / "pathways.json").read_text(encoding="utf-8"))["entries"]
    def claimant(n):
        return [e for e in entries if e["sourceLines"][0] <= n <= e["sourceLines"][1]]

    for n in (5835, 6606, 7267, 7296, 7299):
        owners = claimant(n)
        assert len(owners) == 1, f"line {n} claimed by {[e['name'] for e in owners]}"
        prose = owners[0]["ability"] + " " + owners[0].get("reinforcedAbilities", "")
        assert byn[n].strip()[:40] in prose, (
            f"line {n} is inside {owners[0]['name']}'s span but not in its prose"
        )
    # The two outside it are claimed, and by the files whose objects rule on
    # them -- named here so "already ruled on" is checkable, not asserted.
    for n, f, chapter in ((3619, "divination_methods.json", "8"),):
        assert claimant(n) == [], f"line {n} is not Sequence block prose"
        spans = json.loads((CONTENT / f).read_text(encoding="utf-8"))["sourceLines"][chapter]
        assert any(a <= n <= b for a, b in spans), f"{f} no longer claims line {n}"


def test_shaken_is_one_of_two_deferred_terms_the_book_prints_once():
    """The claim thirteen slices made about `Shaken`, run as a query.

    `Shaken` is deferred as a bare term: 7267 prints the ROLL ("must roll HT
    or be Shaken ... for 1 turn") and never what failing it does. The reading
    inherited from 6dk onward was that this makes it a special case worth its
    own recorded refusal. It does not. Of the five deferrals in ability
    prose, THREE name a term this book prints elsewhere and this app models
    -- `grappled` (the Wrestling table, 6do) and `mentally Stunned` twice
    (chapter 5's Major Wound row, `StunRecovery`) -- and TWO name a term
    printed exactly once in the whole book: `Shaken` (7267) and `suffocates`
    (6606). A refusal recorded against `Shaken` alone would be a blocklist of
    one, with its twin one chapter away.

    A case-insensitive search for the first returns THREE, and the two extra
    are ordinary English -- "shaken awake" (5805) and "being damaged, shaken,
    or a loud noise" (7833). Both counts are asserted, so a re-extraction
    that turns one of those into the game term reddens rather than quietly
    widening the reason.
    """
    byn, _ = _gurps_deferrals()
    def printed(term):
        return [n for n, t in byn.items() if re.search(term, t)]

    assert printed("Shaken") == [7267]
    assert printed("suffocat") == [6606]
    assert len(printed("grappl")) > 1 and len(printed("Stunned")) > 1, (
        "the two glossed terms are no longer printed outside their deferral"
    )
    assert [n for n, t in byn.items() if "shaken" in t.lower()] == [5805, 7267, 7833]
    assert "shaken awake" in byn[5805]
    assert "damaged, shaken, or a loud noise" in byn[7833]


@pytest.mark.skipif(not (OUT / "book.txt").exists(), reason="build/extract/book.txt not extracted")
def test_chapter_2_summary_and_chapter_5_disagree_on_four_defence_rules():
    """v6.9i's ch. 2 "Parry & Block Calculations" against ch. 5's table.

    The app follows ch. 5, the combat chapter (owner's ruling 6 for the
    unarmed parry; the same chapter for the rest). Each disagreement is
    pinned on BOTH sides, so a corrected book reddens here rather than the
    app quietly following a summary that changed (4.1: recorded, not repaired).
    """
    book = (OUT / "book.txt").read_text(encoding="utf-8")
    shields = {r["type"]: r["db"] for r in json.loads((CONTENT / "shields.json").read_text(encoding="utf-8"))["entries"]}
    # The unarmed parry's operand.
    assert "Unarmed Parry: Brawling or Wrestling ÷ 2 + 3" in book
    assert "Brawling or DX ÷ 2 + 3" in book
    # Shield DB: ch. 2 prints two sizes, ch. 5's table three.
    assert "Light shields (buckler, target) give +1 DB; Heavy shields (kite, tower) give +2 DB" in book
    assert shields["Heavy (tower, riot)"] == 3
    # Block: until your next turn against once per round.
    assert "cannot block again until your next turn" in book
    assert "Once per round. Add shield's DB to your Block score." in book
    # Extra parries: ch. 5 adds a rapier step ch. 2 does not print.
    assert "(-2 for fencing weapons like rapier, shortsword, or spear)" in book
    assert "Cumulative -4 per additional parry (-2 fencing, -1" in book
