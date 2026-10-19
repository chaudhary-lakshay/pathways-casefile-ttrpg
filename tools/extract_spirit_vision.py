"""Extract the Spirit Vision guide (end of Ch. 5, p. 46-47, global 3080-3139).

v6.9i made Spirit Vision a UNIVERSAL ability. Ch. 4 (2364-2374): "It is not
tied to any single pathway, sequence, or potion ... Cost: 1 SPI per
activation, regardless of duration." It is bought as the 10-point Spirit
Vision advantage, or taught in play to a Beyonder with 4+ total SPI, and is
then "recorded on the character sheet exactly as if it had been bought". So
no potion block grants it any more and this file carries no `grants`.

Three lettered sections, two shapes:

  A. Astral Projection Colors   8 rows, colour -> emotional meaning
  B. Ether Body Colors          8 rows, body REGION -> colour
  C. Pathway Differences        3 rows, Sequence 9 pathway -> refinement

v6.9d's Section D (a Per roll and a five-row reading table) is gone: the
guide prints no roll at all now. What the app rolls is decided on the
Kotlin side from ch. 4's text, not from here.

A AND B SHARE A VOCABULARY AND NOT A MEANING
---------------------------------------------
Seven of the eight colours appear in both tables and mean unrelated things:
Purple is "Spirituality, madness" in A and the BRAIN in B. The two also run in
opposite column orders. They are kept as separate lists with separate field
names, and verify() asserts the overlap so a future merge on the colour fails.

C IS A REFINEMENT, NOT A GATE
------------------------------
"Pathway and Sequence do not grant access to it, but they do shape how a
Beyonder's Spirit Vision manifests." So C's rows are display: what a holder
of that pathway sees differently. Nothing may read them to GRANT the ability.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "5"
CHAPTER_RANGE = (3080, 3139)
# Ch. 4's universal-ability paragraph: the cost and how it is acquired.
UNIVERSAL_CHAPTER = "4"
UNIVERSAL_RANGE = (2364, 2384)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
SECTIONS = {
    "A": "A. Astral Projection Colors",
    "B": "B. Ether Body Colors",
    "C": "C. Pathway Differences",
}
HEADERS = {"Color", "Meaning", "Body Region", "Pathway (Seq 9)", "Spirit Vision Refinement"}
BALANCED_NOTE = "A balanced body appears white."
PATHWAY_ROW = re.compile(r"^(.+?) \((\w[\w ]*)\)$")
EXPECTED_PATHWAYS = ["Mystery Pryer (Hermit)", "Sleepless (Darkness)", "Corpse Collector (Death)"]

PINNED = [
    "Cost: 1 SPI per activation, regardless of duration.",
    "the Spirit Vision advantage (10 character",
    "minimum of 4 total",
    "Pathway and Sequence do not grant access to it",
]


def load_lines():
    return {row["n"]: row["text"] for row in json.loads(LINES_PATH.read_text("utf-8"))}


def stream(byn, first, last):
    out = []
    for n in range(first, last + 1):
        text = byn.get(n, "").strip()
        if not text or FURNITURE.match(text):
            continue
        out.append(text)
    return out


def split_sections(rows):
    by_letter, current = {}, None
    for text in rows:
        letter = next((k for k, v in SECTIONS.items() if text == v), None)
        if letter:
            current = letter
            by_letter[current] = []
            continue
        if current:
            by_letter[current].append(text)
    return by_letter


def pairs(lines):
    cells = [t for t in lines if t not in HEADERS and not t.endswith(":")]
    if len(cells) % 2:
        raise SystemExit(f"extract_spirit_vision: {len(cells)} cells do not pair; a cell wrapped")
    return list(zip(cells[0::2], cells[1::2]))


def pathways(lines):
    """C: a row starts at "Name (Pathway)"; its refinement runs to the next."""
    start = next(i for i, t in enumerate(lines) if t == "manifests:") + 1
    rows = []
    for text in lines[start:]:
        if text in HEADERS:
            continue
        if PATHWAY_ROW.match(text) and len(text) < 40:
            rows.append({"name": text, "sequence": 9, "ability": ""})
        elif rows:
            rows[-1]["ability"] = f"{rows[-1]['ability']} {text}".strip()
    return rows


def extract(byn):
    sections = split_sections(stream(byn, *CHAPTER_RANGE))
    ether_lines = [t for t in sections["B"] if not t.startswith(BALANCED_NOTE[:20])]
    note = next(t for t in sections["B"] if t.startswith(BALANCED_NOTE[:20]))
    return {
        "astralColors": [{"color": c, "meaning": m} for c, m in pairs(sections["A"])],
        "etherBody": [{"region": r, "color": c} for r, c in pairs(ether_lines)],
        "etherBodyNote": note,
        "pathwayDifferences": pathways(sections["C"]),
        "activation": {"spi": 1, "perActivation": True},
    }


def verify(data, byn):
    assert len(data["astralColors"]) == 8, data["astralColors"]
    assert len(data["etherBody"]) == 8, data["etherBody"]
    astral = {r["color"] for r in data["astralColors"]}
    ether = {r["color"] for r in data["etherBody"]}
    assert len(astral & ether) == 7, sorted(astral & ether)
    assert next(r["meaning"] for r in data["astralColors"] if r["color"] == "Purple") == "Spirituality, madness"
    assert next(r["region"] for r in data["etherBody"] if r["color"] == "Purple") == "Brain"

    rows = data["pathwayDifferences"]
    assert [r["name"] for r in rows] == EXPECTED_PATHWAYS, rows
    assert all(r["ability"] for r in rows), rows

    text = " ".join(stream(byn, *UNIVERSAL_RANGE) + stream(byn, *CHAPTER_RANGE))
    for phrase in PINNED:
        assert phrase in text, f"the guide no longer prints {phrase!r}"

    # No potion grants it. One block still LABELS it: the Mystery Pryer's Eyes
    # of Mystery Prying reads "Active use -- expend 1 SPI and roll SPI -- to
    # activate Spirit Vision: detect ...". That is the refinement Section C
    # describes ("built on Spirit Vision"), priced by its own block, and not
    # a grant of the universal ability. Recorded so a second one is a finding.
    grant = re.compile(r"\bSpirit Vision\s*(?::|\(|—)")
    blocks = json.loads((OUT / "pathways.json").read_text("utf-8"))["entries"]
    granting = [(b["pathway"], b["sequence"]) for b in blocks if grant.search(b.get("ability") or "")]
    assert granting == [("Hermit", 9)], f"blocks labelling Spirit Vision changed: {granting}"


def main():
    byn = load_lines()
    data = extract(byn)
    verify(data, byn)
    payload = {
        "sourceLines": {
            UNIVERSAL_CHAPTER: [list(UNIVERSAL_RANGE)],
            CHAPTER: [list(CHAPTER_RANGE)],
        },
        **data,
    }
    (OUT / "spirit_vision.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"spirit_vision: {len(data['astralColors'])} astral, {len(data['etherBody'])} ether, "
          f"{len(data['pathwayDifferences'])} pathway rows")
    return 0


if __name__ == "__main__":
    sys.exit(main())
