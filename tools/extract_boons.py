"""Extract boon granting (Ch. 13, p. 144-145) into committed JSON.

Not a table anywhere: this chapter is bullets, and its numbers sit inside the
bullet prose. Two things come out of it.

THE FIXED PARAMETERS (global lines 8933-8939). The book pins this ritual:

    Ritual Complexity: Heavy (1 hour base time)
    Effect Weight: Moderate (-2 difficulty, 4-6 SPI required)

Those are two different axes and only the second reaches the dice. Complexity
is TIME; the weight is what Chapter 7 rolls against. A reader who took "Heavy"
as the ritual weight would roll at -4 and be charged 8+ SPI, which is the
whole reason both are extracted rather than one of them.

THE MODIFIER GROUPS (global lines 8942-8950). Two of the three bullets list
options with a modifier in brackets after each, so they are parsed into rows:
sacrifice quality and ritual quality. The third -- boon level -- is not a list
at all. It prints three examples and then a RULE ("doubling the penalty with
each sequence"), so it ships as the sentence, and the ladder itself lives in
Boons.penaltyForSequence, where the reading can be tested. Extracting three
rows would have meant inventing Sequence 6.

Requirements (8926-8931) and consequences (8954-8962) are prose the GM reads;
they wrap onto continuation lines and are rejoined on the bullet, not by
counting lines.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "15"
SKILL_RANGE = (8164, 8165)
PARAMETER_RANGE = (8167, 8168)
REQUIREMENT_RANGE = (8157, 8162)
# Runs across the page break; the two furniture lines between are stripped, so
# the boon-level bullet rejoins with its tail on p. 145.
MODIFIER_RANGE = (8173, 8181)
CONSEQUENCE_RANGE = (8185, 8193)

BULLET = "\x7f"
FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
MINUS = "-−–—"

# "rolls Ritualistic Magic/IQ (Very Hard)"
SKILL = re.compile(r"rolls ([A-Z][A-Za-z ]+?)/([A-Z]{2}) \(([A-Za-z ]+?)\)")
# "Ritual Complexity: Heavy (1 hour base time)"
COMPLEXITY = re.compile(r"^Ritual Complexity:\s*(\w+)\s*\((.+)\)$")
# "Effect Weight: Moderate (-2 difficulty, 4-6 SPI required)"
WEIGHT = re.compile(
    rf"^Effect Weight:\s*(\w+)\s*\(([+{MINUS}]?\d+) difficulty,"
    rf"\s*(\d+)[{MINUS}](\d+) SPI required\)$"
)
# "A perfunctory sacrifice (-2 to -4)", "standard preparation (+0)". The
# bracketed modifier is the anchor; the label is what runs back to the last
# comma. No printed label contains one.
OPTION = re.compile(
    rf"([^,()]+?)\s*\(([+{MINUS}]\d+)(?:\s*to\s*([+{MINUS}]\d+))?\)"
)
CONNECTOR = re.compile(r"^(?:or|and)\s+", re.IGNORECASE)
# The bullet that is a rule rather than a list.
BOON_LEVEL = "Boon level"


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {line["n"]: line["text"] for line in data}


def normalise(value):
    return re.sub(f"[{MINUS}]", "-", value)


def bullets(byn, start, end):
    """The bullets in a range, each rejoined with its continuation lines."""
    out = []
    for n in range(start, end + 1):
        text = (byn.get(n) or "").strip()
        if not text or FURNITURE.match(text):
            continue
        if text.startswith(BULLET):
            out.append(text.lstrip(BULLET).strip())
        elif out:
            out[-1] = f"{out[-1]} {text}".strip()
        else:
            raise ValueError(f"line {n} continues a bullet that never started: {text!r}")
    if not out:
        raise ValueError(f"no bullets in {start}-{end}")
    return out


def plain(byn, start, end):
    """A range of prose, joined into one string with any bullets stripped."""
    parts = []
    for n in range(start, end + 1):
        text = (byn.get(n) or "").strip().lstrip(BULLET).strip()
        if text and not FURNITURE.match(text):
            parts.append(text)
    return " ".join(parts)


def parse_options(group, rest):
    """The bracketed options inside one modifier bullet."""
    rows = []
    for match in OPTION.finditer(rest):
        label = CONNECTOR.sub("", match.group(1).strip()).strip()
        if not label:
            raise ValueError(f"{group!r} prints a modifier with no label: {match.group(0)!r}")
        row = {
            "group": group,
            "label": label,
            "modifier": int(normalise(match.group(2))),
            "printed": normalise(match.group(0)[match.group(0).index("(") :]).strip("()"),
        }
        if match.group(3):
            # "+1 to +3" -- a range the GM picks inside, so both ends are kept
            # and `modifier` is the end nearer zero.
            row["best"] = int(normalise(match.group(3)))
        rows.append(row)
    if not rows:
        raise ValueError(f"{group!r} prints no options")
    return rows


def parse_modifiers(lines):
    """The modifier bullets: option rows, plus the one that is a rule."""
    rows, rule = [], None
    for bullet in lines:
        if ":" not in bullet:
            raise ValueError(f"modifier bullet has no group label: {bullet!r}")
        group, rest = (part.strip() for part in bullet.split(":", 1))
        if group == BOON_LEVEL:
            # Three examples and then "and so on, doubling the penalty with
            # each sequence" -- a rule, not a row set. Kept whole; the ladder
            # is implemented (and tested) in Kotlin.
            rule = rest
            continue
        rows.extend(parse_options(group, rest))
    if rule is None:
        raise ValueError(f"no {BOON_LEVEL!r} bullet found")
    return rows, rule


def parse_parameters(byn):
    complexity = weight = None
    for n in range(PARAMETER_RANGE[0], PARAMETER_RANGE[1] + 1):
        line = normalise((byn.get(n) or "").strip().lstrip(BULLET).strip())
        if COMPLEXITY.match(line):
            complexity = COMPLEXITY.match(line).groups()
        elif WEIGHT.match(line):
            weight = WEIGHT.match(line).groups()
    if complexity is None or weight is None:
        raise ValueError("the fixed parameters are unreadable")

    skill = SKILL.search(plain(byn, *SKILL_RANGE))
    if skill is None:
        raise ValueError("the chapter no longer names the skill it rolls")
    return {
        "skill": skill.group(1),
        "attribute": skill.group(2),
        "difficulty": skill.group(3),
        "complexity": complexity[0],
        "complexityTime": complexity[1],
        "weight": weight[0],
        "weightModifier": int(weight[1]),
        "spi": int(weight[2]),
        "spiMax": int(weight[3]),
    }


def verify(granting, modifiers):
    if granting["skill"] != "Ritualistic Magic" or granting["difficulty"] != "Very Hard":
        raise ValueError(f"the boon roll changed: {granting}")
    # Complexity and weight are DIFFERENT axes. If they ever read the same,
    # the parse has collapsed one onto the other and the roll goes wrong by 2.
    if granting["complexity"] != "Heavy" or granting["weight"] != "Moderate":
        raise ValueError(f"the fixed parameters changed: {granting}")
    if granting["weightModifier"] != -2 or (granting["spi"], granting["spiMax"]) != (4, 6):
        raise ValueError(f"the effect weight changed: {granting}")

    groups = {}
    for row in modifiers:
        groups.setdefault(row["group"], []).append(row)
    if sorted(groups) != ["Ritual quality", "Sacrifice quality"]:
        raise ValueError(f"modifier groups changed: {sorted(groups)}")
    sacrifice = [(r["modifier"], r.get("best")) for r in groups["Sacrifice quality"]]
    if sacrifice != [(-2, -4), (0, None), (1, 3), (4, 6)]:
        raise ValueError(f"sacrifice modifiers changed: {sacrifice}")
    quality = [(r["modifier"], r.get("best")) for r in groups["Ritual quality"]]
    if quality != [(-3, None), (0, None), (1, 3), (4, None)]:
        raise ValueError(f"ritual quality modifiers changed: {quality}")

    # The ladder lives in Kotlin, so the sentence it was read from is pinned
    # here: three printed examples and the word that makes it a doubling.
    rule = normalise(granting["boonLevelRule"])
    for printed in ("Sequence 9 equivalent (+0)", "Sequence 8 (-2)", "Sequence 7 (-4)"):
        if printed not in rule:
            raise ValueError(f"the boon level rule no longer prints {printed!r}: {rule!r}")
    if "doubling the penalty with each sequence" not in rule:
        raise ValueError(f"the boon level ladder is no longer a doubling: {rule!r}")

    if len(granting["requirements"]) != 3:
        raise ValueError(f"expected three requirements: {granting['requirements']}")
    if len(granting["consequences"]) != 4:
        raise ValueError(f"expected four consequences: {granting['consequences']}")
    # The refusal row sends the GM back to Chapter 7 rather than printing its
    # own effects, the same way the divination critical row does.
    refusal = [c for c in granting["consequences"] if "Critical Failure Table" in c]
    if len(refusal) != 1:
        raise ValueError("no consequence points at the Critical Failure Table any more")


def write(name, entries, spans):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: spans}, "entries": entries}
    (OUT / f"{name}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def main():
    byn = load_lines()
    granting = parse_parameters(byn)
    modifiers, rule = parse_modifiers(bullets(byn, *MODIFIER_RANGE))
    granting["boonLevelRule"] = rule
    granting["requirements"] = bullets(byn, *REQUIREMENT_RANGE)
    granting["consequences"] = bullets(byn, *CONSEQUENCE_RANGE)
    verify(granting, modifiers)

    write(
        "boon_granting",
        [granting],
        [list(SKILL_RANGE), list(PARAMETER_RANGE),
         list(REQUIREMENT_RANGE), list(CONSEQUENCE_RANGE)],
    )
    write("boon_modifiers", modifiers, [list(MODIFIER_RANGE)])

    print(f"boon_granting: {granting['complexity']} time, {granting['weight']} weight "
          f"({granting['weightModifier']}, {granting['spi']}-{granting['spiMax']} SPI)")
    print(f"boon_modifiers: {[(m['group'], m['printed']) for m in modifiers]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
