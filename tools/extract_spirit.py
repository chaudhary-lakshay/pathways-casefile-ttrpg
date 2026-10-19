"""Extract the Spirit Body Damage rules (Ch. 5, p. 42, global 2815-2821).

v6.9d printed five labelled groups of bullets here: the target CHOSE a pool
(HP, FP or SPI) before the roll, wounding multipliers applied to it, physical
DR did not protect, and recovery followed each pool's rate. v6.9i deletes all
of it. What is left is four sentences-to-a-line-end, with no labels and no
bullets:

  - spirit body damage pierces DR, physical and spiritual alike;
  - each point gives -1 to the victim's NEXT roll, one roll of any kind, then
    the penalty clears;
  - some forms also add CoR, in the amount the dealing ability states;
  - other forms carry none.

So there is no pool to choose any more: the damage is a penalty, not a loss
of HP, FP or SPI. A paragraph is the unit, because the book prints no finer
one: lines are joined until one ends a sentence at its line end.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "5"
SPIRIT_RANGE = (2815, 2821)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
TITLE = "Spirit Body Damage"
ENDS_SENTENCE = re.compile(r"\.$")

# Phrases the Kotlin side reads these rules as meaning. If one goes, the app is
# stating a rule the book stopped printing.
PINNED = [
    "pierces Damage Resistance (DR) — physical and spiritual DR provide no protection",
    "-1 per point of damage",
    "The penalty applies to a single roll",
    "and then clears",
    "Some forms of spirit damage also add Corruption (CoR)",
]
# The v6.9d rule this file used to carry. Its return would be a finding.
GONE = ["chooses which of three pools", "One pool per instance"]


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def parse(byn):
    """One rule per printed paragraph."""
    out, current = [], []
    for n in range(SPIRIT_RANGE[0], SPIRIT_RANGE[1] + 1):
        text = (byn.get(n) or "").strip()
        if not text or FURNITURE.match(text):
            continue
        if text == TITLE:
            continue
        current.append(text)
        if ENDS_SENTENCE.search(text):
            out.append({"section": TITLE, "rule": " ".join(current)})
            current = []
    if current:
        raise ValueError(f"the section ends mid-sentence: {current}")
    return out


def verify(rules, byn):
    if (byn.get(SPIRIT_RANGE[0]) or "").strip() != TITLE:
        raise ValueError(f"line {SPIRIT_RANGE[0]} is not the {TITLE!r} heading")
    if len(rules) != 4:
        raise ValueError(f"expected four rules, got {len(rules)}")
    body = " ".join(r["rule"] for r in rules)
    for phrase in PINNED:
        if phrase not in body:
            raise ValueError(f"the rule the app states is gone: {phrase!r}")
    for phrase in GONE:
        if phrase in body:
            raise ValueError(f"the v6.9d pool rule is back: {phrase!r}")


def write(name, entries, spans):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: spans}, "entries": entries}
    (OUT / f"{name}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def main():
    byn = load_lines()
    rules = parse(byn)
    verify(rules, byn)
    write("spirit_damage", rules, [list(SPIRIT_RANGE)])

    # The echo below quotes the book, and the book prints an arrow. A redirected
    # stdout on Windows is cp1252, which has none -- so this died AFTER writing.
    sys.stdout.reconfigure(encoding="utf-8")
    print(f"spirit_damage: {len(rules)} rules")
    for rule in rules:
        print(f"  [{rule['section']}] {rule['rule'][:78]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
