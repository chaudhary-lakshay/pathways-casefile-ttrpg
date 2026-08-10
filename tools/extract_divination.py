"""Extract the Divination Arts resolution tables (Ch. 6.5, global 3563-3736).

Five tables, and each needs a different anchor because each has a different
closed column:

  * SUPPORTING SKILLS (3563-3579) and MODIFIER SOURCES (3690-3707) both put a
    signed value in the second column, so the anchor is the sign -- the rule
    extract_ranged.py already uses. One modifier cell carries no number at all
    ("See Step 1 table") and is flagged rather than parsed to zero, the same
    way the two Aim rows are.
  * QUESTION DIFFICULTY (3662-3684) is Band / Modifier / Example. The band
    wraps ("Trivial - personal, present," / "simple") and the example follows
    the modifier, so the modifier's leading sign cuts the row.
  * METHODS & TOOLS (3583-3652) is five columns and the only closed one is the
    METHOD NAME, so that is the anchor -- the same decision the maneuvers table
    forced. Two cells fight a positional read: "Dream" / "Interpretation" wraps
    the NAME, and the Dream row's time cell is split MID-WORD as "Overnig" /
    "ht", which no cell-shaped rule can see. Joining the time cell without a
    separator and checking it against the times this book prints is what
    catches it.
  * CLARITY (3711-3736) is a closed set of six results, five named by margin
    and one named by OUTCOME ("Critical Failure"). That is the same two-doors
    shape as the ritual failure table, and com.pathways.rules.Divination reads
    the outcome first for the reason recorded there.

The sentences the Kotlin side turns into numbers are pinned by verify(): the
supporting-skill cap, the specialisation bonus, and the burn-SPI rule.

THE CROSS-SKILL DEFAULT (3559-3560), IN THE SEAM ABOVE THE FIRST TABLE
-----------------------------------------------------------------------
    Default: Divination Arts defaults to Ritualistic Magic-2 and vice versa.
    A character with one skill at 12+ can attempt the other at -2.

Two lines directly above SUPPORT_RANGE's 3563, wrapping across a page break
("...attempt the other" / "at -2."), and unclaimed until this slice -- the
seam sweep from CLAUDE.md 6ay found it. It is a DEFAULT, not a modifier: it
says what a caster who lacks one skill rolls AGAINST, which is why nothing in
the modifier table covers it and why a per-roll chip is the wrong home.

It is a SPECIFIC default and it beats the general one. Divination Arts is
SPI/Hard, so Difficulty.defaultPenalty answers -6 for a character who has
neither; this sentence prints -2 for one who has the sibling skill. Same
shape as 6aw's melee summary, where Brawling prints DX-2 against the ladder's
DX-4 -- except that there SkillLadder.defaultFor has no production caller and
nothing could reach the wrong answer. Here it is REACHABLE: the app gates its
Divine button on having the skill at all, and 25 potion blocks grant
Ritualistic Magic against 5 that grant Divination Arts.

"12+" is a threshold on the SOURCE skill, not on the result, and it is read
as a gate rather than a rounding: a caster at 11 gets no default at all. Read
as advisory it would hand every apprentice the cheap default the sentence
conditions.

WHY THE -4 ONE LINE ABOVE IT IS NOT A MODIFIER
------------------------------------------------
3557-3558: "Complex divination ... Requires Ritualistic Magic or a very high
Divination Arts roll (-4 penalty)." It reads like a sixth modifier row and it
is not: it is prose for the SAME axis the Question Difficulty table already
prices, whose "Hard -- well-guarded, future, abstract" row is exactly -4 and
whose Extreme row is "-6 or worse". Offering it beside that table charges one
hard question twice -- the scoping that keeps Appendix A's adverse conditions
off a shot already priced by chapter 5's darkness row (6ae). verify() pins
that the difficulty table still prints a -4 row, because that agreement is
the whole reason this line is read as a restatement rather than a rule.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "6.5"
# The cross-skill default, in the seam above the first table. Two lines,
# because the sentence wraps across a page break.
CROSS_DEFAULT_RANGE = (3559, 3560)
# The overlap bullets above it, read only to prove the -4 is the difficulty
# table restated rather than a modifier of its own.
OVERLAP_RANGE = (3553, 3558)
SUPPORT_RANGE = (3563, 3580)
METHOD_RANGE = (3583, 3654)
DIFFICULTY_RANGE = (3662, 3684)
MODIFIER_RANGE = (3690, 3708)
CLARITY_RANGE = (3711, 3736)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADER = {
    "Supporting Skill", "Bonus", "Notes", "Method", "Best For", "Time", "SPI",
    "Question Difficulty", "Modifier", "Example", "Modifier Source",
    "Roll Result", "Clarity", "GM Gives",
    "II. Methods & Tools", "Supporting Skills",
}

SIGNED_CELL = re.compile(r"^[+-]")
SIGNED = re.compile(r"[+-]\d+")
# "+0 to +2", "-1 to -2": the second number is the other end of a GM's range.
RANGE_END = re.compile(r"to\s*([+-]\d+)")
CROSS_REFERENCE = "See Step 1 table"
PER_REPEAT = "per repeat"

# The eight methods, which is the only closed column in that table.
METHODS = [
    "Pendulum", "Coin", "Dowsing Rods", "Dream Interpretation",
    "Mirror Scrying", "Crystal Ball", "Tarot / Cards", "Bone Oracle",
]
# Every time this book prints. "Overnight" arrives split mid-word.
TIMES = {"30 sec", "1 min", "5 min", "10 min", "15 min", "Overnight"}
SPI_CELL = re.compile(r"^(\d+)(?:\s*[-–—]\s*(\d+))?$")

CLARITY_RESULTS = [
    "Success by 5+", "Success by 3–4", "Success by 0–2",
    "Failure by 1–4", "Failure by 5+", "Critical Failure",
]
CRITICAL = "Critical Failure"
# The margin a Result cell prints, so the number travels ON the row rather
# than being copied into Kotlin -- the shape MajorWound.failBy, RitualFailure
# .from/.to and SpiritualSense.margin already have. A band's lower bound is
# all it needs: they overlap by design and the deepest match wins.
CLARITY_BAND = re.compile(r"^(Success|Failure) by (\d+)")
# (isFailure, margin) for the five rows named by margin, in printed order.
EXPECTED_CLARITY_BANDS = [(False, 5), (False, 3), (False, 0), (True, 1), (True, 5)]

EXPECTED_SUPPORT = ["Occultism", "Hidden Lore (relevant)", "Psychology", "Cogitation"]
EXPECTED_DIFFICULTY = 5

# "Divination Arts defaults to Ritualistic Magic-2 and vice versa." The
# penalty is captured, never assumed from the "-2" one clause later: the
# sentence prints two -2s and they are the same number by coincidence of
# drafting, not by a rule saying so.
CROSS_DEFAULT = re.compile(
    r"Divination Arts defaults to Ritualistic Magic\s*([+-]\d+)\s*and vice versa",
    re.IGNORECASE,
)
# "A character with one skill at 12+ can attempt the other at -2."
CROSS_THRESHOLD = re.compile(
    r"one skill at\s*(\d+)\+\s*can attempt the other at\s*([+-]\d+)", re.IGNORECASE)
# The complex-divination bullet, whose -4 is the difficulty table restated.
COMPLEX_PENALTY = re.compile(r"very high Divination Arts roll\s*\(([+-]\d+) penalty\)")
# Ch. 7 II p. 61, in a chapter this module does not otherwise read. It is the
# ONLY thing limiting the sentence above to one direction, and that limit is
# invisible from here -- which is exactly how it went unread until the seam
# sweep (CLAUDE.md 6ba). Pinned on this side too, because a reader who
# re-symmetrises Divination.divinationLevelFor will be looking at this file.
# Claimed by ritual_support.json under chapter 7, not re-claimed here.
RITUAL_NO_DEFAULT_RANGE = (4007, 4008)
RITUAL_NO_DEFAULT = "It has no default and cannot be substituted"
PINNED = [
    "cumulative but cap at +2 total from supporting skills",
    "gaining +1 when using it",
    "1 SPI = +1, no cap",
]


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    out = []
    for n in range(start, end + 1):
        text = byn.get(n)
        if text is None:
            continue
        stripped = text.strip()
        if not stripped or FURNITURE.match(stripped) or stripped in HEADER:
            continue
        out.append(stripped)
    return out


def pair_on_sign(lines):
    """(label, value) per row, cut on the value cell's leading sign."""
    rows = []
    for i, line in enumerate(lines):
        if not SIGNED_CELL.match(line) and line != CROSS_REFERENCE:
            continue
        if i == 0:
            raise ValueError(f"value {line!r} has no label before it")
        rows.append((lines[i - 1], line))
    return rows


def parse_support(lines):
    out = []
    for skill, cell in pair_on_sign(lines):
        bonus = SIGNED.search(cell)
        if not bonus:
            raise ValueError(f"{skill!r} prints no bonus: {cell!r}")
        out.append({"skill": skill, "bonus": int(bonus.group())})
    return out


def parse_modifiers(lines):
    out = []
    for source, cell in pair_on_sign(lines):
        entry = {"source": source, "printed": cell}
        if cell == CROSS_REFERENCE:
            # Not a number: it is the difficulty table, which the caller
            # already has. Applying zero here would drop the whole of Step 1.
            entry["crossReference"] = True
            out.append(entry)
            continue
        value = SIGNED.search(cell)
        if not value:
            raise ValueError(f"{source!r} prints no modifier: {cell!r}")
        entry["modifier"] = int(value.group())
        other = RANGE_END.search(cell)
        if other:
            entry["best"] = int(other.group(1))
        if PER_REPEAT in cell:
            entry["perRepeat"] = True
        out.append(entry)
    return out


def parse_difficulty(lines):
    """Band / Modifier / Example, cut on the modifier's leading sign."""
    out = []
    starts = [i for i, line in enumerate(lines) if SIGNED_CELL.match(line)]
    for pos, i in enumerate(starts):
        band = " ".join(lines[(0 if pos == 0 else starts[pos - 1] + 2):i])
        if not band:
            raise ValueError(f"modifier {lines[i]!r} has no band before it")
        if i + 1 >= len(lines):
            raise ValueError(f"band {band!r} prints no example")
        value = SIGNED.search(lines[i])
        entry = {
            "band": band,
            "modifier": int(value.group()),
            "printedModifier": lines[i],
            "example": lines[i + 1],
        }
        if "worse" in lines[i]:
            # "-6 or worse" is open-ended, like the "100+ meters" range band.
            entry["openEnded"] = True
        out.append(entry)
    return out


def join_time(parts):
    """The time cell, whether it arrived whole or split mid-word."""
    for joiner in ("", " "):
        candidate = joiner.join(parts).strip()
        if candidate in TIMES:
            return candidate
    raise ValueError(f"unreadable time cell: {parts!r}")


def join_time_safe(parts):
    try:
        return join_time(parts)
    except ValueError:
        return None


def parse_methods(lines):
    """One row per method name; the name is the only closed column here."""
    starts = []
    for i, line in enumerate(lines):
        if line in METHODS:
            starts.append((i, i, line))
        elif i + 1 < len(lines) and f"{line} {lines[i + 1]}" in METHODS:
            starts.append((i, i + 1, f"{line} {lines[i + 1]}"))
    if [name for _, _, name in starts] != METHODS:
        raise ValueError(f"methods changed: {[n for _, _, n in starts]} != {METHODS}")

    out = []
    for pos, (first, last, name) in enumerate(starts):
        end = starts[pos + 1][0] if pos + 1 < len(starts) else len(lines)
        cells = lines[last + 1:end]
        spi_at = next((j for j, c in enumerate(cells) if SPI_CELL.fullmatch(c)), None)
        if spi_at is None:
            raise ValueError(f"{name} prints no SPI cost")
        spi = SPI_CELL.fullmatch(cells[spi_at])
        # Everything between the "Best For" prose and the SPI cell is the time,
        # however many lines the typesetter cut it into.
        time_at = spi_at - 1
        while time_at > 0 and join_time_safe(cells[time_at:spi_at]) is None:
            time_at -= 1
        entry = {
            "method": name,
            "bestFor": " ".join(cells[:time_at]),
            "time": join_time(cells[time_at:spi_at]),
            "spi": int(spi.group(1)),
            "notes": " ".join(cells[spi_at + 1:]),
        }
        if spi.group(2):
            entry["spiMax"] = int(spi.group(2))
        out.append(entry)
    return out


def parse_clarity(lines):
    """Result / Clarity / GM Gives, cut on the closed Result column."""
    starts = [i for i, line in enumerate(lines) if line in CLARITY_RESULTS]
    if [lines[i] for i in starts] != CLARITY_RESULTS:
        raise ValueError(f"clarity rows changed: {[lines[i] for i in starts]}")
    out = []
    for pos, i in enumerate(starts):
        end = starts[pos + 1] if pos + 1 < len(starts) else len(lines)
        if i + 2 > end:
            raise ValueError(f"{lines[i]!r} has no clarity cell")
        entry = {
            "result": lines[i],
            "clarity": lines[i + 1],
            "effect": " ".join(lines[i + 2:end]),
        }
        if lines[i] == CRITICAL:
            # Named by OUTCOME where the other five are named by margin, so it
            # carries no margin at all -- printing no number is not printing 0.
            entry["isCritical"] = True
        else:
            band = CLARITY_BAND.match(lines[i])
            if not band:
                raise ValueError(f"{lines[i]!r} names neither an outcome nor a margin")
            entry["margin"] = int(band.group(2))
            if band.group(1) == "Failure":
                entry["isFailure"] = True
        out.append(entry)
    return out


def parse_cross_default(cells):
    """The cross-skill default's penalty and its source-skill threshold.

    The cells are joined first because the sentence WRAPS across a page break
    -- "can attempt the other" ends one line and "at -2." begins the next --
    so a line-at-a-time scan finds a rule with no number, which reads exactly
    like the rule being absent. The same shape as the Flourish Bonus (6ay).
    """
    joined = " ".join(cells)
    default = CROSS_DEFAULT.search(joined)
    if not default:
        raise ValueError(f"no cross-skill default sentence; the seam reads {joined!r}")
    gated = CROSS_THRESHOLD.search(joined)
    if not gated:
        raise ValueError(f"the '12+' threshold clause is gone; the seam reads {joined!r}")
    return int(default.group(1)), int(gated.group(1)), int(gated.group(2)), joined


def verify_cross_default(penalty, threshold, gated_penalty, seam, overlap,
                         difficulty, ritual_section=None):
    """The default, its gate, why the -4 beside it is not a modifier, and
    which of the sentence's two directions the other chapter forbids."""
    if ritual_section is not None and RITUAL_NO_DEFAULT not in ritual_section:
        raise ValueError(
            "ch. 7 II no longer prints its prohibition. This sentence's 'and "
            "vice versa' half is refused ONLY because p. 61 says Ritualistic "
            "Magic 'has no default and cannot be substituted'; without it the "
            "default is symmetric again and Divination.ritualLevelFor is wrong"
        )

    if penalty != -2:
        raise ValueError(f"the cross-skill default used to be -2; the page prints {penalty:+d}")
    # The sentence states the penalty twice. They agree today, and if they
    # ever stop the reading has to be made again rather than inherited --
    # the standard the appendix's matching "Pi" had to meet (6v).
    if gated_penalty != penalty:
        raise ValueError(
            f"the sentence's two printings of the default disagree ({penalty:+d} "
            f"defaulting, {gated_penalty:+d} gated). They used to agree, which is "
            "the only evidence they are one rule"
        )
    if threshold != 12:
        raise ValueError(f"the source-skill threshold used to be 12+; it prints {threshold}")

    # The whole reason this is a DEFAULT and not a per-roll modifier: it is
    # printed above the tables, not in the modifier list.
    if "Default:" not in seam:
        raise ValueError(f"the seam no longer labels itself a default: {seam!r}")

    # And the -4 one line above, which is the difficulty table restated. If
    # that table ever stops printing a -4 row, this line becomes a rule of its
    # own and the refusal to apply it has to be re-argued.
    complex_penalty = COMPLEX_PENALTY.search(" ".join(overlap))
    if not complex_penalty:
        raise ValueError(
            "the complex-divination bullet no longer prints its penalty. The "
            "refusal to apply it is only a decision while the number is there"
        )
    printed = [d["modifier"] for d in difficulty]
    if int(complex_penalty.group(1)) not in printed:
        raise ValueError(
            f"the complex-divination bullet prints {complex_penalty.group(1)} and the "
            f"Question Difficulty table prints {printed}. They used to agree, which is "
            "the only evidence that bullet restates the table rather than adding a "
            "sixth modifier -- applying both would charge one hard question twice"
        )


def verify(support, methods, difficulty, modifiers, clarity, prose):
    got = [s["skill"] for s in support]
    if got != EXPECTED_SUPPORT:
        raise ValueError(f"supporting skills changed: {got} != {EXPECTED_SUPPORT}")
    if any(s["bonus"] != 1 for s in support):
        raise ValueError("a supporting skill stopped being worth +1")
    if len(difficulty) != EXPECTED_DIFFICULTY:
        raise ValueError(f"expected {EXPECTED_DIFFICULTY} difficulty bands, got {len(difficulty)}")
    if sum(1 for d in difficulty if d.get("openEnded")) != 1:
        raise ValueError("exactly one difficulty band reads 'or worse'; that changed")
    if sum(1 for m in modifiers if m.get("crossReference")) != 1:
        raise ValueError("the 'See Step 1 table' row is the only one with no number")
    if sum(1 for c in clarity if c.get("isCritical")) != 1:
        raise ValueError("exactly one clarity row is named by outcome, not margin")
    # The join the app was missing: the margin that SELECTS a row now comes
    # from the row's own printed cell, so this pins the parse of that cell.
    bands = [(bool(c.get("isFailure")), c["margin"])
             for c in clarity if not c.get("isCritical")]
    if bands != EXPECTED_CLARITY_BANDS:
        raise ValueError(f"clarity bands changed: {bands} != {EXPECTED_CLARITY_BANDS}")
    if any("margin" in c for c in clarity if c.get("isCritical")):
        raise ValueError("the critical row is named by outcome and must print no margin")
    if len(methods) != len(METHODS):
        raise ValueError(f"expected {len(METHODS)} methods, got {len(methods)}")

    dream = next(m for m in methods if m["method"] == "Dream Interpretation")
    if dream["time"] != "Overnight":
        raise ValueError(f"the mid-word time split is unhandled: {dream['time']!r}")
    rods = next(m for m in methods if m["method"] == "Dowsing Rods")
    if rods.get("spiMax") != 2:
        raise ValueError("Dowsing Rods used to cost 1-2 SPI; the range is gone")

    body = " ".join(prose)
    for phrase in PINNED:
        if phrase not in body:
            raise ValueError(f"the rule the app applies is gone: {phrase!r}")


def write(name, entries, spans):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: spans}, "entries": entries}
    (OUT / f"{name}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def main():
    byn = load_lines()
    support_lines = stream(byn, *SUPPORT_RANGE)
    method_lines = stream(byn, *METHOD_RANGE)
    modifier_lines = stream(byn, *MODIFIER_RANGE)

    support = parse_support(support_lines)
    methods = parse_methods(method_lines)
    difficulty = parse_difficulty(stream(byn, *DIFFICULTY_RANGE))
    modifiers = parse_modifiers(modifier_lines)
    clarity = parse_clarity(stream(byn, *CLARITY_RANGE))
    verify(support, methods, difficulty, modifiers, clarity,
           support_lines + method_lines + modifier_lines)

    # The seam above the first table. Nothing is written for it -- the -2 and
    # the 12 live in Kotlin, the split every number-inside-a-sentence takes --
    # but the span is claimed below, so the rule stays findable (6af).
    penalty, threshold, gated, seam = parse_cross_default(
        stream(byn, *CROSS_DEFAULT_RANGE))
    verify_cross_default(penalty, threshold, gated, seam,
                         stream(byn, *OVERLAP_RANGE), difficulty,
                         " ".join(stream(byn, *RITUAL_NO_DEFAULT_RANGE)))

    write("divination_support", support, [list(CROSS_DEFAULT_RANGE), list(SUPPORT_RANGE)])
    write("divination_methods", methods, [list(METHOD_RANGE)])
    write("divination_difficulty", difficulty, [list(DIFFICULTY_RANGE)])
    write("divination_modifiers", modifiers, [list(MODIFIER_RANGE)])
    write("divination_clarity", clarity, [list(CLARITY_RANGE)])

    print(f"divination_difficulty: {len(difficulty)} bands")
    for row in difficulty:
        print(f"  {row['modifier']:+d}  {row['band'][:56]}")
    print(f"divination_methods: {len(methods)} methods")
    for row in methods:
        cost = f"{row['spi']}-{row['spiMax']}" if "spiMax" in row else str(row["spi"])
        print(f"  {row['method']:22} {row['time']:10} {cost} SPI")
    print(f"divination_modifiers: {len(modifiers)} rows")
    print(f"divination_clarity: {len(clarity)} rows")
    print(f"divination_support: {len(support)} skills")
    print(f"cross-skill default: {penalty:+d} from the sibling skill at {threshold}+ "
          "(lives in Divination)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
