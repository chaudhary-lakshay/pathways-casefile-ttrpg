"""Extract Sealed Artifacts and their grade table (Ch. 6, p. 47-53, global 3180-3541).

Two files, because they are two tables the book joins by NUMBER:

  sealed_artifact_grades.json  the four-grade classification (p. 47)
  sealed_artifacts.json        the four documented artifacts (p. 49-53)

**Grade is a church ACCESS rule and a danger rating, never a usage
requirement.** The table's own fourth column is "Church Access Rules" and every
cell in it names an institutional RANK -- "Formal Nighthawk members and above",
"Diocesan Bishop or Nighthawk deacon and above". Its third column, "Power
Equivalent (Seq 5-7)", says how strong the ARTIFACT is; read as a requirement it
would lock a Sequence 9 character out of a gauntlet the book says is found on
the body of an out-of-control Beyonder. com.pathways.rules.SealedArtifacts shows
both columns and enforces neither.

The warrant for joining the two tables is that the artifacts confirm the grade
table independently: each header prints its own "Seq N equivalent", and all four
land inside their grade's printed band. verify() checks all four by value -- the
same per-table standard the appendix's matching "Pi" had to meet.

**Two HP costs are parsed and the other three are deliberately not.** The
gauntlet's "Each use of any ability costs 1 HP in addition to SPI" is
unconditional per use, and the candle's Extinguish Memory "deals 1 HP burn
damage to the user" every time it fires. The flagon's 1 HP is conditional on
telling a lie, the candle's Burns True on touching the flame, and the coin's
Time Debt only bites after three uses in a week -- all GM-triggered. A loose
"1 HP" rule would charge all five. The two phrasings are pinned below, because
the app SPENDS them; scraping a number out of rule text at render time would be
a parser nobody tests (see extract_ritual.py's corGained).

Anchors: a grade row starts at a bare "Grade N"; within it the Power Equivalent
cell is the one holding "(Seq", and a line STARTING with "(" cannot open a cell,
so it belongs to the line above -- the unclosed-bracket rule extract_wounds.py
needs for the Vitals multiplier. An ability row is anchored on its SPI cost
cell, a bare "N SPI" optionally "/turn", because three of the four ability
tables sit across a page break and only the cost cell has a fixed shape.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "6"
GRADE_RANGE = (3180, 3210)
ARTIFACT_RANGE = (3344, 3541)
INTRO_RANGE = (3174, 3183)

# This PDF prints list bullets as U+007F. See extract_defenses.py.
BULLET = "\x7f"
FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADER = {
    "Sealed Artifact Grades", "Grade", "Danger", "Power Equivalent",
    "Church Access Rules", "Ability", "SPI Cost", "Effect",
}

GRADE_CELL = re.compile(r"^Grade (\d)$")
SEQ_BAND = re.compile(r"\(Seq (\d+)[–-](\d+)")
SPI_CELL = re.compile(r"^(\d+) SPI(/turn)?$")
ARTIFACT_HEADER = re.compile(
    r"^Sealed Artifact (\d+)[–-](\d+): (.+?) "
    r"\((.+?) Pathway · Grade (\d+) · Seq (\d+) equivalent\)$"
)

# The two HP charges the app applies. Narrow on purpose: see the module docstring.
HP_PER_USE = re.compile(r"Each use of any ability costs (\d+) HP in addition to SPI")
HP_PER_ABILITY = re.compile(r"deals (\d+) HP burn damage to the user")

EXPECTED_GRADES = ["Grade 3", "Grade 2", "Grade 1", "Grade 0"]
EXPECTED_ARTIFACTS = [
    "Gauntlet of the False Baron", "Echoing Coin of the Serpent",
    "Candle of Shared Slumber", "The Peerless Flagon",
]
EXPECTED_ABILITIES = 13
# The sentence that makes a grade a classification rather than a gate.
GRADE_BASIS = "based on danger, power, and sealing method"
# Only one artifact charges HP per use, and only one ability charges it itself.
EXPECTED_HP_PER_USE = {"Gauntlet of the False Baron": 1}
EXPECTED_HP_PER_ABILITY = {"Extinguish Memory": 1}
# Drawbacks that print an HP number the app must NOT charge per use.
UNCHARGED_HP = [
    "the flagon grows hot (1 HP burn to the hand)",
    "takes 1d6 burning damage that ignores mundane armour",
    "the user loses 1 HP permanently",
]


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    """(is_bullet, text) per line, headers and page furniture dropped."""
    out = []
    for n in range(start, end + 1):
        text = byn.get(n)
        if text is None:
            continue
        stripped = text.strip()
        if not stripped or FURNITURE.match(stripped):
            continue
        if stripped.startswith(BULLET):
            out.append((True, stripped.lstrip(BULLET).strip()))
        elif stripped not in HEADER:
            out.append((False, stripped))
    return out


def parse_grades(cells):
    """One row per bare "Grade N"; three cells inside it, split on "(Seq"."""
    starts = [i for i, c in enumerate(cells) if GRADE_CELL.fullmatch(c)]
    if not starts:
        raise ValueError("no grade rows found")
    rows = []
    for i, start in enumerate(starts):
        end = starts[i + 1] if i + 1 < len(starts) else len(cells)
        body = cells[start + 1:end]
        seq = [j for j, c in enumerate(body) if "(Seq" in c]
        if len(seq) != 1:
            raise ValueError(f"{cells[start]!r} has {len(seq)} power cells, want 1")
        at = seq[0]
        # A line opening with "(" is the tail of the line above, never a cell.
        first = at - 1 if body[at].startswith("(") else at
        if first <= 0:
            raise ValueError(f"{cells[start]!r} has no danger cell")
        band = SEQ_BAND.search(" ".join(body[first:at + 1]))
        if band is None:
            raise ValueError(f"{cells[start]!r} prints no sequence band")
        rows.append({
            "grade": int(GRADE_CELL.fullmatch(cells[start]).group(1)),
            "danger": " ".join(body[:first]),
            "powerEquivalent": " ".join(body[first:at + 1]),
            "sequenceLow": int(band.group(1)),
            "sequenceHigh": int(band.group(2)),
            "churchAccess": " ".join(body[at + 1:]),
        })
    return rows


def parse_abilities(cells):
    """One row per SPI cost cell; the name is the line before it."""
    at = [i for i, c in enumerate(cells) if SPI_CELL.fullmatch(c)]
    rows = []
    for i, index in enumerate(at):
        cost = SPI_CELL.fullmatch(cells[index])
        if index == 0:
            raise ValueError(f"{cells[index]!r} has no ability name before it")
        end = at[i + 1] - 1 if i + 1 < len(at) else len(cells)
        effect = " ".join(cells[index + 1:end])
        hp = HP_PER_ABILITY.search(effect)
        rows.append({
            "name": cells[index - 1],
            "spi": int(cost.group(1)),
            "perTurn": cost.group(2) is not None,
            "effect": effect,
            **({"hpCost": int(hp.group(1))} if hp else {}),
        })
    return rows


def parse_artifacts(lines):
    """Split on the "Sealed Artifact N-NN:" headers, then by labelled section."""
    starts = [i for i, (b, t) in enumerate(lines)
              if not b and ARTIFACT_HEADER.match(t)]
    if not starts:
        raise ValueError("no artifact headers found")
    out = []
    for i, start in enumerate(starts):
        end = starts[i + 1] if i + 1 < len(starts) else len(lines)
        head = ARTIFACT_HEADER.match(lines[start][1])
        out.append(_one(head, lines[start + 1:end]))
    return out


def _one(head, body):
    appearance, abilities, drawbacks, sealing = [], [], [], []
    section = "appearance"
    for is_bullet, text in body:
        if text == "Drawbacks:":
            section = "drawbacks"
            continue
        if text.startswith("Sealing Instructions:"):
            section = "sealing"
            sealing.append(text[len("Sealing Instructions:"):].strip())
            continue
        if section == "drawbacks":
            if is_bullet:
                drawbacks.append(text)
            elif drawbacks:
                drawbacks[-1] += " " + text
            continue
        if section == "sealing":
            sealing.append(text)
            continue
        if text.startswith("Appearance:"):
            appearance.append(text[len("Appearance:"):].strip())
        elif SPI_CELL.fullmatch(text) or abilities:
            abilities.append(text)
        else:
            appearance.append(text)
    # The ability table's first NAME lands in appearance above, because nothing
    # marks it: it is the line before the first SPI cell, so take it back.
    if abilities:
        abilities.insert(0, appearance.pop())
    hp = HP_PER_USE.search(" ".join(drawbacks))
    return {
        "code": f"{head.group(1)}-{head.group(2)}",
        "name": head.group(3),
        "pathway": head.group(4),
        "grade": int(head.group(5)),
        "sequenceEquivalent": int(head.group(6)),
        "appearance": " ".join(appearance),
        "abilities": parse_abilities(abilities),
        "drawbacks": drawbacks,
        "sealing": " ".join(sealing),
        **({"hpPerUse": int(hp.group(1))} if hp else {}),
    }


def verify(grades, artifacts, intro):
    got = [f"Grade {g['grade']}" for g in grades]
    if got != EXPECTED_GRADES:
        raise ValueError(f"grades changed: {got} != {EXPECTED_GRADES}")
    names = [a["name"] for a in artifacts]
    if names != EXPECTED_ARTIFACTS:
        raise ValueError(f"artifacts changed: {names} != {EXPECTED_ARTIFACTS}")

    if GRADE_BASIS not in intro:
        raise ValueError(
            "the sentence saying a grade classifies danger rather than gating a "
            f"wielder is gone: {GRADE_BASIS!r}"
        )
    # Every access cell must still name a RANK. That is what makes the column an
    # institutional gate rather than a Sequence one.
    for g in grades:
        if not re.search(r"Nighthawk|Bishop|confidential", g["churchAccess"]):
            raise ValueError(f"Grade {g['grade']} access names no rank: {g}")

    # THE WARRANT. Each artifact prints its own sequence equivalent; every one
    # must land inside its grade's printed band. Four independent confirmations
    # are the only reason the two tables are joined at all.
    bands = {g["grade"]: (g["sequenceLow"], g["sequenceHigh"]) for g in grades}
    for a in artifacts:
        band = bands.get(a["grade"])
        if band is None:
            raise ValueError(f"{a['name']} has an unprinted grade {a['grade']}")
        if not band[0] <= a["sequenceEquivalent"] <= band[1]:
            raise ValueError(
                f"{a['name']} is Seq {a['sequenceEquivalent']} but Grade "
                f"{a['grade']} prints {band} -- the two tables no longer agree, "
                "so joining them is no longer warranted"
            )

    total = sum(len(a["abilities"]) for a in artifacts)
    if total != EXPECTED_ABILITIES:
        raise ValueError(f"expected {EXPECTED_ABILITIES} abilities, got {total}")
    for a in artifacts:
        if not a["abilities"] or not a["drawbacks"] or not a["sealing"]:
            raise ValueError(f"{a['name']} lost a section: {a.keys()}")
        if "Appearance" in a["appearance"] or "Drawbacks" in a["appearance"]:
            raise ValueError(f"{a['name']} ate a label: {a['appearance'][:60]!r}")
        for ability in a["abilities"]:
            if not ability["effect"] or ability["spi"] <= 0:
                raise ValueError(f"{a['name']}/{ability['name']} is empty: {ability}")

    # The two HP charges the app applies, and nothing else.
    per_use = {a["name"]: a["hpPerUse"] for a in artifacts if "hpPerUse" in a}
    if per_use != EXPECTED_HP_PER_USE:
        raise ValueError(f"per-use HP changed: {per_use} != {EXPECTED_HP_PER_USE}")
    per_ability = {
        ab["name"]: ab["hpCost"]
        for a in artifacts for ab in a["abilities"] if "hpCost" in ab
    }
    if per_ability != EXPECTED_HP_PER_ABILITY:
        raise ValueError(
            f"per-ability HP changed: {per_ability} != {EXPECTED_HP_PER_ABILITY}"
        )
    # The drawbacks that print HP the app must NOT charge. If one of these went
    # away the narrow regexes above would stop being a decision.
    prose = " ".join(d for a in artifacts for d in a["drawbacks"])
    for phrase in UNCHARGED_HP:
        if phrase not in prose:
            raise ValueError(f"an uncharged HP drawback is gone: {phrase!r}")


def write(name, entries, spans):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: spans}, "entries": entries}
    (OUT / f"{name}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def main():
    byn = load_lines()
    grades = parse_grades([t for b, t in stream(byn, *GRADE_RANGE) if not b])
    artifacts = parse_artifacts(stream(byn, *ARTIFACT_RANGE))
    intro = " ".join(t for b, t in stream(byn, *INTRO_RANGE))
    verify(grades, artifacts, intro)

    write("sealed_artifact_grades", grades, [list(GRADE_RANGE)])
    write("sealed_artifacts", artifacts, [list(ARTIFACT_RANGE)])

    print(f"sealed_artifact_grades: {len(grades)} rows")
    for g in grades:
        print(f"  Grade {g['grade']}  {g['danger']:<20} "
              f"Seq {g['sequenceLow']}-{g['sequenceHigh']}")
    print(f"sealed_artifacts: {len(artifacts)} artifacts")
    for a in artifacts:
        hp = f", {a['hpPerUse']} HP per use" if "hpPerUse" in a else ""
        print(f"  {a['code']} {a['name']:32} Grade {a['grade']} "
              f"Seq {a['sequenceEquivalent']}  "
              f"{len(a['abilities'])} abilities, {len(a['drawbacks'])} drawbacks{hp}")
        for ab in a["abilities"]:
            turn = "/turn" if ab["perTurn"] else ""
            extra = f" +{ab['hpCost']} HP" if "hpCost" in ab else ""
            print(f"      {ab['name']:24} {ab['spi']} SPI{turn}{extra}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
