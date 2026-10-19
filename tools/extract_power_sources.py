"""Extract Ch. 9's Full-ritual time table and pin Sec. IV's power sources
(p. 61, global 3972-4007).

ONE AXIS NOW, NOT TWO
---------------------
v6.9d printed SPI cost twice -- a three-row weight column and a four-row
complexity ladder that disagreed at both ends -- and its six worked rituals
sided with the ladder, so the two were kept as separate axes. v6.9i deleted
the ladder and the worked rituals. The weight table now prints four rows
(Light / Moderate / Heavy / Major) with SPI cost and base time, and this
chapter's Rushed / Extended table keys on the SAME four names with the SAME
base times. So `ritual_complexity.json` is the pacing half of the weight
axis: base time, rushed, extended. SPI cost is the weight's alone.
verify() pins the agreement of the names and base times; the day they part,
they are two axes again.

SEC. IV IS SENTENCES, SO ITS NUMBERS STAY IN KOTLIN
----------------------------------------------------
  HP Sacrifice      "2 HP = 1 SPI toward ritual cost. HP sacrifice does not
                    grant a roll bonus -- it only provides spiritual fuel."
  Participants      "Each assistant beyond the first gives +1 to the ritual
                    roll (max +3)."
  Sacrifice         "ranges from -40 to +40 depending on quality and domain
                    relevance at GM's discretion." The v6.9d tiers
                    (Ordinary/Meaningful/Precious) are gone, so
                    ritual_sacrifice.json is gone: one GM range is a number
                    in a sentence, the split every other one here takes.
  Invocation        "+1 to +5 to the ritual roll (GM discretion ...). Orthodox
                    deities grant +2 when the request aligns with their
                    domain and the caster is in good standing."
  Suspension        "suspending costs -2 to effective skill."

"BEYOND THE FIRST" LOST ITS EVIDENCE
------------------------------------
v6.9d's Ritual 3 ("Two trained assistants ... +2") was the only thing that
read "beyond the first" as beyond the CASTER. v6.9i deleted it. The sentence
is pinned as printed; which participant "the first" names is a reading the
Kotlin side records, not this file.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "9"
COMPLEXITY_RANGE = (3972, 3991)
PROSE_RANGE = (3992, 4007)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
HEADERS = {"Complexity", "Base Time", "Rushed", "Extended"}
NAMES = ("Light", "Moderate", "Heavy", "Major")
MINUS = "-−"
ROLL_CELL = re.compile(rf"^([+{MINUS}]\d+) to roll$")

PINNED = {
    "hpPerSpi": (re.compile(r"(\d+) HP = (\d+) SPI toward ritual cost"), (2, 1)),
    "participants": (re.compile(r"beyond the first gives \+(\d+) to the ritual roll \(max \+(\d+)\)"), (1, 3)),
    "sacrifice": (re.compile(rf"ranges from ([{MINUS}]\d+) to \+(\d+)"), (-40, 40)),
    "invocation": (re.compile(r"Adds \+(\d+) to \+(\d+) to the ritual roll"), (1, 5)),
    "orthodox": (re.compile(r"Orthodox deities grant \+(\d+)"), (2,)),
    "suspension": (re.compile(rf"suspending costs ([{MINUS}]\d+) to effective skill"), (-2,)),
}
HP_NO_BONUS = "HP sacrifice does not grant a roll bonus"
DECLARATION = "it cannot be changed once begun"
GOOD_STANDING = "the caster is in good standing"


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    out = []
    for n in range(start, end + 1):
        text = (byn.get(n) or "").strip()
        if not text or FURNITURE.match(text) or text in HEADERS:
            continue
        out.append(text)
    return out


def signed(text):
    return int(text.replace("−", "-"))


def parse_complexity(cells):
    rows = []
    for i, name in enumerate(cells):
        if name not in NAMES:
            continue
        time, rushed, extended = cells[i + 1:i + 4]
        r, e = ROLL_CELL.match(rushed), ROLL_CELL.match(extended)
        if not r or not e:
            raise ValueError(f"the {name!r} row has no Rushed/Extended pair")
        rows.append({"complexity": name, "baseTime": time,
                     "rushed": signed(r.group(1)), "extended": signed(e.group(1))})
    return rows


def read_numbers(prose):
    out = {}
    for key, (pattern, want) in PINNED.items():
        found = pattern.search(prose)
        if not found:
            raise ValueError(f"the {key} sentence no longer prints its number")
        got = tuple(signed(g) for g in found.groups())
        if got != want:
            raise ValueError(f"{key} reads {got}, not {want}")
        out[key] = got
    return out


def verify(rows, prose, weights):
    if [r["complexity"] for r in rows] != list(NAMES):
        raise ValueError(f"the complexity names changed: {rows}")
    if [r["rushed"] for r in rows] != [-3, -3, -4, -5]:
        raise ValueError(f"the Rushed column changed: {rows}")
    if [r["extended"] for r in rows] != [1, 1, 2, 2]:
        raise ValueError(f"the Extended column changed: {rows}")
    by_weight = {w["weight"]: w["baseTime"] for w in weights}
    for row in rows:
        if by_weight.get(row["complexity"]) != row["baseTime"]:
            raise ValueError(
                f"{row['complexity']}: base time {row['baseTime']!r} here and "
                f"{by_weight.get(row['complexity'])!r} in the weight table -- two axes again"
            )
    for phrase in (HP_NO_BONUS, DECLARATION, GOOD_STANDING):
        if phrase not in prose:
            raise ValueError(f"the sentence {phrase!r} is gone")


def main():
    byn = load_lines()
    rows = parse_complexity(stream(byn, *COMPLEXITY_RANGE))
    prose = " ".join(stream(byn, *PROSE_RANGE))
    numbers = read_numbers(prose)
    weights = json.loads((OUT / "ritual_weights.json").read_text(encoding="utf-8"))["entries"]
    verify(rows, prose, weights)
    (OUT / "ritual_complexity.json").write_text(
        json.dumps({"sourceLines": {CHAPTER: [list(COMPLEXITY_RANGE), list(PROSE_RANGE)]},
                    "entries": rows}, ensure_ascii=False, indent=1) + "\n",
        encoding="utf-8")
    for row in rows:
        print(f"  {row['complexity']:9} {row['baseTime']:7} rushed {row['rushed']:+d} "
              f"extended {row['extended']:+d}")
    print(f"in Kotlin: {numbers}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
