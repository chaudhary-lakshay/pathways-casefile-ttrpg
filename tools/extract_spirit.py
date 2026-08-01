"""Extract the Spirit Body Damage rules (Ch. 5, p. 40-41, global 2709-2737).

Not a table -- five labelled groups of bullets. A line is a bullet because the
PDF says so: this book prints list bullets as U+007F, which stream() carries as
a flag rather than guessing from shape (the same lesson extract_combat.py
learned). A non-bullet line is a GROUP HEADING when it ends in a colon, and a
CONTINUATION of the bullet above it otherwise -- but only when that bullet does
not already end in sentence punctuation. Two bullets wrap mid-sentence:

    "... (e.g. skull" / "x4, vitals x3) stack with wounding multipliers ..."
    "... reduce by the penalty, then apply to the" / "chosen pool."

and the section's last line is a standalone note ("Non-Beyonders (SPI 0): Can
only choose HP or FP"). Joining by position would glue that note onto the
recovery bullet above it; joining on the SENTENCE tells the two apart, which is
the rule extract_fright.py already uses on the effects table.

The "Threshold Penalties by Pool" group is deliberately NOT emitted. Every one
of its five lines is already modelled: the HP and SPI rows are in
pool_thresholds.json and the FP line is one of the two prose sources
com.pathways.rules.Pools quotes for a pool the book prints no table for. A
second copy would be a rule with two homes and no way to tell which one an app
was reading. verify() asserts the group is still THERE in the source, so
dropping it stays a decision rather than a parse that quietly stopped working.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "5"
SPIRIT_RANGE = (2709, 2737)

# This PDF prints list bullets as U+007F. See extract_defenses.py.
BULLET = "\x7f"
FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
TITLE = "Spirit Body Damage"
# A bullet that ends here is finished; anything else wraps onto the next line.
ENDS_SENTENCE = re.compile(r"[.:]$")

DROPPED = "Threshold Penalties by Pool"
# The book's own labels, not tidied ones: "Recovery follows each pool's normal
# rate" is what it prints, and shortening it here would mean the app displays a
# heading the rulebook does not have.
EXPECTED_SECTIONS = [
    TITLE, "Choosing a Pool", "Damage Application", "Damage Expression",
    "Recovery follows each pool's normal rate", "Non-Beyonders (SPI 0)",
]
# Phrases the Kotlin side reads these rules as meaning. If one goes, the app is
# stating a rule the book stopped printing.
PINNED = [
    "the target chooses which of three pools it reduces: HP, FP, or SPI",
    "One pool per instance",
    "Physical DR does not protect",
    "Wounding multipliers apply normally to the chosen pool",
    "have no spirituality to sacrifice",
]


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    """(is_bullet, text) per line, with the PDF's own bullet character stripped."""
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
        else:
            out.append((False, stripped))
    return out


def parse(lines):
    """Grouped rules, in printed order. See the module docstring for the cuts."""
    out = []
    section = TITLE
    for is_bullet, text in lines:
        if text == TITLE:
            continue
        # An unfinished rule takes the next line whatever that line looks like:
        # a wrap can land on something that would otherwise read as a heading.
        wraps = bool(out) and not ENDS_SENTENCE.search(out[-1]["rule"])
        if wraps and not is_bullet:
            out[-1]["rule"] += " " + text
            continue
        if is_bullet:
            out.append({"section": section, "rule": text})
            continue
        if text.endswith(":"):
            # "Damage Expression: Spirit body damage is given either as:" is a
            # label with its own sentence attached; the label is the part
            # before the first colon.
            section = text.rstrip(":").split(":")[0].strip()
            continue
        # A standalone line. One carries its own label ("Non-Beyonders (SPI
        # 0): ..."), which opens a section of exactly one rule; the rest is the
        # section's own prose.
        if ":" in text:
            section = text.split(":")[0].strip()
        out.append({"section": section, "rule": text})
    # Modelled elsewhere; see the module docstring. Dropped AFTER parsing so
    # its bullets still anchor the sections around them.
    return [rule for rule in out if rule["section"] != DROPPED]


def verify(rules, raw):
    sections = list(dict.fromkeys(r["section"] for r in rules))
    if sections != EXPECTED_SECTIONS:
        raise ValueError(f"sections changed: {sections} != {EXPECTED_SECTIONS}")
    if DROPPED in sections:
        raise ValueError(f"{DROPPED!r} was emitted; it is modelled in pool_thresholds.json")
    # ... but it must still BE there, so dropping it stays a decision.
    if not any(text.startswith(DROPPED) for _, text in raw):
        raise ValueError(f"{DROPPED!r} is gone from the source; the drop is now silent")

    body = " ".join(r["rule"] for r in rules)
    for phrase in PINNED:
        if phrase not in body:
            raise ValueError(f"the rule the app states is gone: {phrase!r}")
    unfinished = [r["rule"] for r in rules if not ENDS_SENTENCE.search(r["rule"])]
    if unfinished:
        raise ValueError(f"these rules end mid-sentence, so a wrap was missed: {unfinished}")


def write(name, entries, spans):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: spans}, "entries": entries}
    (OUT / f"{name}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def main():
    raw = stream(load_lines(), *SPIRIT_RANGE)
    rules = parse(raw)
    verify(rules, raw)
    write("spirit_damage", rules, [list(SPIRIT_RANGE)])

    print(f"spirit_damage: {len(rules)} rules")
    for rule in rules:
        print(f"  [{rule['section']}] {rule['rule'][:78]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
