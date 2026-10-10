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

    2788 is the NEXT table's heading ("Optional -- Wounding Multipliers") and
    2789 is where `wounding_multipliers.json` begins, so a span that ran one
    line further would claim a heading it does not read and collide with its
    neighbour.
    """
    content = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"
    spans = json.loads((content / "hit_locations.json").read_text(encoding="utf-8"))["sourceLines"]["5"]
    claimed = {n for lo, hi in spans for n in range(lo, hi + 1)}
    assert {2785, 2786, 2787} <= claimed, sorted(claimed)[-6:]
    assert 2788 not in claimed, "the span now claims the next table's heading"

    others = json.loads((content / "wounding_multipliers.json").read_text(encoding="utf-8"))
    for lo, hi in others["sourceLines"]["5"]:
        assert not (claimed & set(range(lo, hi + 1))), (lo, hi)


def test_spirit_vision_section_c_is_a_swap_not_a_one_sided_error():
    """The other direction of the sweep, which nothing had run.

    `test_spirit_vision_records_the_spectator_disagreement` asks, of each row
    in 7.6's section C, whether the pathway's own block agrees. It cannot see
    the opposite question: is there a pathway whose block GRANTS Spirit Vision
    at Sequence 9 and which section C omits? There is -- Moon's Apothecary,
    whose block prints "Spirit Vision - Ether Body".

    So the table is not four-right-and-one-wrong. It is the right SIZE and the
    wrong MEMBERSHIP: five rows against five Sequence 9 grants, with Visionary
    listed and not granted, and Moon granted and not listed. A swap.

    Both halves are asserted, because a repair in either direction would
    otherwise pass: dropping the Visionary row leaves Moon still missing, and
    adding Moon leaves Visionary still wrong.
    """
    content = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"
    sv = json.loads((content / "spirit_vision.json").read_text(encoding="utf-8"))
    blocks = json.loads((content / "pathways.json").read_text(encoding="utf-8"))["entries"]

    granted = {b["pathway"] for b in blocks
               if b["sequence"] == 9 and extract_spirit_vision.GRANT.search(b["ability"] or "")}
    assert granted == {"Fool", "Darkness", "Death", "Hermit", "Moon"}, sorted(granted)

    listed = {r["name"].split("(")[1].rstrip(")").strip()
              for r in sv["pathwayDifferences"] if "(" in r["name"]}
    assert listed == {"Fool", "Hermit", "Visionary", "Darkness", "Death"}, sorted(listed)

    assert listed - granted == {"Visionary"}, "7.6 no longer lists a pathway that grants none"
    assert granted - listed == {"Moon"}, "7.6 no longer omits a pathway that grants it"
    assert len(listed) == len(granted), "the swap has become an imbalance; re-read section C"


def test_the_spirit_vision_grant_predicate_separates_three_classes():
    """Why the gate reads a LABEL and not the words "Spirit Vision".

    21 of 88 pathway blocks name Spirit Vision and they do not mean one thing.
    Three classes, each verified against the book's own prose:

      GRANT     the ability named as a label -- "Spirit Vision:",
                "Spirit Vision - Limited Form:", "Eyes of Mystery Prying (".
      UPGRADE   the same label inside `reinforcedAbilities`, which improves a
                grant an earlier rung already made.
      NEITHER   a DEFENCE against someone else's Spirit Vision (the Werewolf
                reveals only black hair; the Hypnotist's illusion "works even
                against Spirit Vision"), or a mention of what OTHER pathways
                can do (the Monster sees what "Pathways with strong Spirit
                Vision abilities cannot").

    A bare substring match grants the Werewolf, the Hypnotist and the Monster
    an ability the book gives them no part of. The label is what separates
    them, and this is the test that says the classification still holds.
    """
    content = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"
    blocks = json.loads((content / "pathways.json").read_text(encoding="utf-8"))["entries"]

    grants, upgrades, neither = set(), set(), set()
    for b in blocks:
        ability, reinforced = b["ability"] or "", b.get("reinforcedAbilities") or ""
        key = (b["pathway"], b["sequence"])
        if extract_spirit_vision.GRANT.search(ability):
            grants.add(key)
        elif extract_spirit_vision.GRANT.search(reinforced):
            upgrades.add(key)
        elif "Spirit Vision" in ability or "Spirit Vision" in reinforced:
            neither.add(key)

    assert grants == {
        ("Fool", 9), ("Darkness", 9), ("Death", 9), ("Hermit", 9), ("Moon", 9),
        ("Death", 8), ("Mother", 8), ("Mother", 7), ("Red Priest", 8),
    }, sorted(grants)
    assert neither == {
        ("Wheel of Fortune", 9), ("Chained", 7), ("Visionary", 6),
        ("Darkness", 6), ("Red Priest", 7),
    }, sorted(neither)
    assert len(grants) + len(upgrades) + len(neither) == 21

    # The invariant the gate rests on: an UPGRADE never stands alone. Every
    # one sits on a pathway that granted the ability at a HIGHER sequence
    # number, which the cumulative walk has already passed through. That is
    # why the gate reads grants only -- checking `reinforcedAbilities` too
    # would be a branch no data can reach, trusted later as though it meant
    # something.
    earliest = {}
    for pathway, sequence in grants:
        earliest[pathway] = max(earliest.get(pathway, -1), sequence)
    for pathway, sequence in upgrades:
        assert pathway in earliest, f"{pathway} upgrades Spirit Vision it was never granted"
        assert earliest[pathway] > sequence, (pathway, sequence, earliest[pathway])


def test_ether_body_awareness_is_two_different_things():
    """One name, two printings, opposite premises — and nothing may join them.

    Chapter 7.6's closing sentence lists `Ether Body Awareness` as the Seer's
    Special Form OF Spirit Vision. Chapter 3 prices `Ether Body Awareness` at
    5 points in `advantages.json` and defines it as knowing when something
    targets your soul **"even without Spirit Vision"** — a trait bought by
    characters who do not have the ability at all.

    A resolver joining the two on the name would hand every Seer a 5-point
    advantage they never bought, or gate a bought advantage behind a pathway.
    Both printings are pinned so the collision cannot be resolved by accident
    in either direction.
    """
    content = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"
    sv = json.loads((content / "spirit_vision.json").read_text(encoding="utf-8"))
    advantages = json.loads((content / "advantages.json").read_text(encoding="utf-8"))["entries"]

    seer = next(f for f in sv["specialForms"] if f["pathwayRank"] == "Seer")
    assert seer["form"] == "Ether Body Awareness"

    bought = next(a for a in advantages if a["name"] == "Ether Body Awareness")
    assert bought["cost"] == 5
    assert "even without Spirit Vision" in bought["notes"], (
        "the advantage no longer disclaims Spirit Vision; the two printings "
        "may no longer be distinct and the reading must be made again"
    )

    # Five forms, one per Sequence 9 rank, matching section C's five rows.
    assert [f["pathwayRank"] for f in sv["specialForms"]] == [
        r["name"].split(" (")[0] for r in sv["pathwayDifferences"]
    ]


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
    assert body_text.count("•") > 200, "the bullet repair regressed"


@needs_pdf
def test_chapter_4_governs_spi_recovery_and_the_appendix_says_less():
    """Which printing of the SPI recovery rule the recorded refusal names.

    `Study.kt`'s KDoc cited *"Appendix A's SPI recovery"* as one of the two
    rules this app leaves unmodelled for want of a clock. Appendix A is the
    QUICK REFERENCE, and its row (9718-9724) is three cells: Rest, 1 SPI per
    hour, and the requirement. Chapter 4 p.34 prints the rule itself and
    carries two clauses the summary drops -- an exclusivity sentence and an
    interruption clause -- so the refusal named the summary and never
    mentioned the printing that governs. Claim status was never the same
    question as which printing governs (6dl), and neither is being handy to
    quote.

    Pinned so the reason recorded in `Pools.UNMODELLED` cannot drift off the
    sentence it rests on. RED-proved by editing 2340's text in `lines.json`.
    """
    byn = {l["n"]: l["text"] for l in json.loads((OUT / "lines.json").read_text(encoding="utf-8"))}

    rule = " ".join(byn[n] for n in range(2338, 2342))
    assert "recovers 1 SPI" in rule and "per hour" in rule, (
        f"p.34 no longer prints the recovery rate: {rule!r}"
    )
    assert "the standard and only recovery rate" in rule, (
        f"p.34's exclusivity clause is gone: {rule!r}"
    )
    assert "interrupts rest and halts recovery" in rule, (
        f"p.34's interruption clause is gone: {rule!r}"
    )

    summary = " ".join(byn[n] for n in range(9718, 9725))
    assert "1 SPI per hour" in summary, (
        f"Appendix A no longer prints the same rate: {summary!r}"
    )
    # A negative pin on two words, not on a meaning: a reworded summary
    # carrying the same two clauses would pass this. That is the reach a
    # substring check has, stated rather than assumed -- what it does catch
    # is the extraction moving the row or the book gaining the sentence.
    assert "only" not in summary and "halts" not in summary, (
        "Appendix A now carries a clause chapter 4 was cited for; the "
        f"refusal may name either printing: {summary!r}"
    )


@needs_pdf
def test_the_bard_restores_spi_outside_rest():
    """The narrower ability p.34's "only" does not reach.

    p.88's Sequence 9 Bard picks one Singing buff, and option (d) is
    *"Spiritual Recovery - allies recover 1 SPI at the song's end"*, upgraded
    at p.100 to two. That is not a second ORDINARY recovery rate competing
    with p.34's -- it is an active pathway ability with its own scope, and
    the parenthetical proves the scoping is deliberate: the Bard is excluded
    from their own effect.

    **This test pins the ability, never the priority.** Reading it as
    specific-beating-general -- the reading `Pools.UNMODELLED` records, on
    the model of the Potion Consumption Roll against `SuccessRoll` -- is this
    repo's; the book states no priority between the two printings, and no
    assertion below proves one.

    Recorded rather than repaired, and pinned here because `Pools.UNMODELLED`
    names it: a reader who greps "only recovery rate" must find the exception
    beside it, not conclude the book contradicts itself.
    """
    byn = {l["n"]: l["text"] for l in json.loads((OUT / "lines.json").read_text(encoding="utf-8"))}

    song = " ".join(byn[n] for n in range(5600, 5602))
    assert "Spiritual Recovery" in song, f"p.88's buff list lost the option: {song!r}"
    assert "recover 1 SPI at the song" in song, f"the buff no longer restores SPI: {song!r}"
    assert "Bard cannot benefit from this effect" in song, (
        f"the exclusion that makes this an ability rather than a rate is gone: {song!r}"
    )
    assert "restores 2 SPI instead of 1" in byn[6402], (
        f"p.100's upgrade no longer doubles it: {byn[6402]!r}"
    )


@needs_pdf
def test_chapter_4_prices_spirit_vision_a_second_time_and_says_less():
    """The other unclaimed line on p. 34, and why it disputes nothing.

    Ch. 7.6 line 4840 prints *"Activate by expending 1 SPI (1 SPI per minute
    to maintain)"* and `SpiritVisionDialog` bills the activation. Ch. 4's
    skill overview prints the maintenance rate ALONE. A second printing
    widens a rule only by saying something the first did not, so this one is
    recorded and not applied -- but the claim rests on the word `Activate`
    being absent from it, which is a fact about the book and can move.
    """
    byn = {l["n"]: l["text"] for l in json.loads((OUT / "lines.json").read_text(encoding="utf-8"))}

    overview = " ".join(byn[n] for n in range(2309, 2311))
    assert "Cost: 1 SPI per" in overview and "minute" in overview, (
        f"p.34 no longer prices Spirit Vision: {overview!r}"
    )
    assert "Activate" not in overview, (
        "p.34 now names an activation charge, so it is no longer a printing "
        f"that says strictly less than 7.6's: {overview!r}"
    )
    assert "Activate by expending 1 SPI" in byn[4840], (
        f"7.6's governing printing moved: {byn[4840]!r}"
    )


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

    Eight sites defer to the GURPS Basic Set. 246 is the book's own framing
    ("Standard GURPS rules apply unless this book explicitly overrides them")
    and prices nothing. Of the SEVEN that carry a rule, five sit inside a
    `pathways.json` Sequence block's `ability` prose -- the field
    `PotionDigestion.UNPARSED` already records as unbuilt work -- and the two
    that do not are claimed by a content file and ruled on already:

      * 2723 is `spirit_damage.json`'s threshold list. Its `HP <= 0` row
        points at rules chapter 5 prints for itself, and `VitalRolls`
        applies those (6de).
      * 3653-3654 is `divination_methods.json`. Its specialisation
        technique is REFUSED in `Divination.UNAPPLIED` (6cs).

    So there is no unruled GURPS deferral outside that prose, and none
    inside it that the prose's own record does not already cover.

    Fails if a re-extraction moves a line, or if an eighth deferral appears
    in prose no Sequence block claims -- either of which reopens the verdict.
    """
    byn, sites = _gurps_deferrals()
    assert sites == [246, 2723, 3653, 6553, 7348, 8030, 8059, 8062], sites
    assert "unless this book explicitly overrides them" in byn[246], (
        f"246 is no longer the book-level framing: {byn[246]!r}"
    )
    assert "B229" in byn[3654], f"the wrapped citation moved: {byn[3654]!r}"

    entries = json.loads((CONTENT / "pathways.json").read_text(encoding="utf-8"))["entries"]
    def claimant(n):
        return [e for e in entries if e["sourceLines"][0] <= n <= e["sourceLines"][1]]

    for n in (6553, 7348, 8030, 8059, 8062):
        owners = claimant(n)
        assert len(owners) == 1, f"line {n} claimed by {[e['name'] for e in owners]}"
        prose = owners[0]["ability"] + " " + owners[0].get("reinforcedAbilities", "")
        assert byn[n].strip()[:40] in prose, (
            f"line {n} is inside {owners[0]['name']}'s span but not in its prose"
        )
    # The two outside it are claimed, and by the files whose objects rule on
    # them -- named here so "already ruled on" is checkable, not asserted.
    for n, f, chapter in ((2723, "spirit_damage.json", "5"),
                          (3653, "divination_methods.json", "6.5")):
        assert claimant(n) == [], f"line {n} is not Sequence block prose"
        spans = json.loads((CONTENT / f).read_text(encoding="utf-8"))["sourceLines"][chapter]
        assert any(a <= n <= b for a, b in spans), f"{f} no longer claims line {n}"


def test_shaken_is_one_of_two_deferred_terms_the_book_prints_once():
    """The claim thirteen slices made about `Shaken`, run as a query.

    `Shaken` is deferred as a bare term: 8030 prints the ROLL ("must roll HT
    or be Shaken ... for 1 turn") and never what failing it does. The reading
    inherited from 6dk onward was that this makes it a special case worth its
    own recorded refusal. It does not. Of the five deferrals in ability
    prose, THREE name a term this book prints elsewhere and this app models
    -- `grappled` (the Wrestling table, 6do) and `mentally Stunned` twice
    (chapter 5's Major Wound row, `StunRecovery`) -- and TWO name a term
    printed exactly once in the whole book: `Shaken` (8030) and `suffocates`
    (7348). A refusal recorded against `Shaken` alone would be a blocklist of
    one, with its twin one chapter away.

    A case-insensitive search for the first returns THREE, and the two extra
    are ordinary English -- "shaken awake" (6521) and "being damaged, shaken,
    or a loud noise" (8602). Both counts are asserted, so a re-extraction
    that turns one of those into the game term reddens rather than quietly
    widening the reason.
    """
    byn, _ = _gurps_deferrals()
    def printed(term):
        return [n for n, t in byn.items() if re.search(term, t)]

    assert printed("Shaken") == [8030]
    assert printed("suffocat") == [7348]
    assert len(printed("grappl")) > 1 and len(printed("Stunned")) > 1, (
        "the two glossed terms are no longer printed outside their deferral"
    )
    assert [n for n, t in byn.items() if "shaken" in t.lower()] == [6521, 8030, 8602]
    assert "shaken awake" in byn[6521]
    assert "damaged, shaken, or a loud noise" in byn[8602]
