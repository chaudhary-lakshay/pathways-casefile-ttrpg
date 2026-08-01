"""Extract the Active Defenses table and its modifiers (Ch. 5, p. 36-37).

DEFENSES (global lines 2458-2478) is a three-column table -- Defense, Formula,
Notes -- flattened into wrapped lines. Two things make it awkward:

  * the row label itself wraps: "Parry" then "(Unarmed)" on the next line;
  * so does the formula: "Basic Speed (drop" then "fractions) + 3".

So labels are a KNOWN SET rather than a shape. Guessing "any short line" would
take "(Unarmed)" for a new row and shift the rest of the table by one, which
is precisely the failure this file exists to avoid. The set is asserted in
verify(), so a change to the book's own headings fails loudly instead of
silently re-cutting the rows.

The formulas are NOT parsed into arithmetic. com.pathways.rules.ParryBlock
already implements them and is tested against the book; a second copy that
drifted would be worse than none. What ships here is what the GM reads: the
printed formula and the notes beside it ("Cumulative -4 per additional
parry", "Once per round").

MODIFIERS (global lines 2480-2485) are the bullet list under the table. Two
of them wrap, and one ("Acrobatic Dodge") carries both a bonus and a penalty,
so they ship as prose rather than as signed numbers -- there is no single
figure to extract from "success gives +2 Dodge; failure gives -2 Dodge".
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "5"
DEFENSE_RANGE = (2458, 2478)
MODIFIER_RANGE = (2480, 2485)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
# This PDF prints its list bullets as U+007F, which survives text extraction.
BULLET = "•● "
HEADER = {"Defense", "Formula", "Notes"}
# The book's own row headings, in the order it prints them. "Parry" opens the
# unarmed row and "(Unarmed)" continues that same label.
LABELS = ("Dodge", "Parry (Armed)", "Parry", "Block")
CONTINUATION = "(Unarmed)"

EXPECTED = ["Dodge", "Parry (Armed)", "Parry (Unarmed)", "Block"]


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    out = []
    for n in range(start, end + 1):
        text = byn.get(n)
        if text is None:
            continue
        stripped = text.strip().lstrip(BULLET).strip()
        if not stripped or FURNITURE.match(stripped) or stripped in HEADER:
            continue
        out.append(stripped)
    return out


def parse_defenses(lines):
    """Rows of Defense / Formula / Notes, with wrapped labels rejoined.

    Within a row the formula runs until a chunk ends it and the rest is the
    note. There is no column geometry to lean on -- this PDF reports x=0 for
    every glyph -- so the split is by sentence shape, and verify() checks the
    result rather than trusting it.
    """
    rows = []
    for line in lines:
        if line == CONTINUATION and rows:
            rows[-1]["defense"] = f"{rows[-1]['defense']} {line}"
            continue
        if line in LABELS:
            rows.append({"defense": line, "parts": []})
            continue
        if not rows:
            raise ValueError(f"text before any defense label: {line!r}")
        rows[-1]["parts"].append(line)

    out = []
    for row in rows:
        parts = row["parts"]
        if not parts:
            raise ValueError(f"defense {row['defense']!r} has no formula at all")
        formula, notes, in_formula = [], [], True
        for part in parts:
            if in_formula:
                formula.append(part)
                # "Basic Speed (drop" continues; "fractions) + 3" and
                # "Weapon Skill / 2 + 3" end the formula.
                if not part.endswith("(drop") and re.search(r"\+ 3$|^\S.*\d$", part):
                    in_formula = False
            else:
                notes.append(part)
        out.append({
            "defense": row["defense"],
            "formula": " ".join(formula),
            "notes": " ".join(notes),
        })
    return out


def parse_modifiers(lines):
    """The bullet list under the table, each bullet rejoined onto one line."""
    bullets = []
    for line in lines:
        # A new bullet starts with a bracketed tag or an "Attacked from"
        # clause; anything else is the wrapped tail of the previous one.
        if line.startswith("[") or line.startswith("Attacked from"):
            bullets.append(line)
        elif bullets:
            bullets[-1] = f"{bullets[-1]} {line}"
        else:
            raise ValueError(f"modifier text before any bullet: {line!r}")
    return [{"modifier": b} for b in bullets]


def verify(defenses, modifiers):
    names = [d["defense"] for d in defenses]
    if names != EXPECTED:
        raise ValueError(f"defense rows changed: {names} != {EXPECTED}")
    for row in defenses:
        if not row["formula"]:
            raise ValueError(f"{row['defense']} lost its formula")
    # ParryBlock implements skill/2 + 3 for the armed parry and for block; if
    # the book stops printing that, the code is what needs revisiting.
    for name in ("Parry (Armed)", "Block"):
        row = next(d for d in defenses if d["defense"] == name)
        if "2 + 3" not in row["formula"]:
            raise ValueError(f"{name} no longer reads as skill/2 + 3: {row['formula']!r}")
    if len(modifiers) != 5:
        raise ValueError(f"expected 5 defense modifiers, got {len(modifiers)}")


def write(name, entries, spans):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: spans}, "entries": entries}
    (OUT / f"{name}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def main():
    byn = load_lines()
    defenses = parse_defenses(stream(byn, *DEFENSE_RANGE))
    modifiers = parse_modifiers(stream(byn, *MODIFIER_RANGE))
    verify(defenses, modifiers)

    write("defenses", defenses, [list(DEFENSE_RANGE)])
    write("defense_modifiers", modifiers, [list(MODIFIER_RANGE)])

    print(f"defenses: {[d['defense'] for d in defenses]}")
    print(f"defense_modifiers: {len(modifiers)} bullets")
    return 0


if __name__ == "__main__":
    sys.exit(main())
