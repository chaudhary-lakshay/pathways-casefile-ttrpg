"""Extract the Seven Orthodox Gods Blessings (Ch. 3, p. 19, lines 1098-1118).

Seven deities, one blessing each, 75 points each, plus a printed requirement.
The table is a genuine SEAM: advantages.json's last span ends at 1097 and
disadvantages.json's first begins at 1131, so these 21 lines were claimed by
nobody -- the sweep direction 6ay opened and 6ba widened.

**Why this was not extracted before, and why the reason expired.** 6bc read
this page and deliberately left it, because no premade prints a blessing and
traits were not editable, so a blessings.json would have been a content file
read by nothing -- the defect 6ai, 6al and 6am each found in turn. 6ca then
made traits purchasable and 6cc widened the picker to the instantiated rows,
so the consumer now exists. A deferral is a claim about the DATA and expires
like any other verdict (6au, 6av, 6ca).

**The cost is stated once, in prose, for all seven rows**, so it is stored
once as costEach rather than repeated as a per-row column. A column would
make the book look like it printed one; the table's own two headers are
Deity and Blessing Effect and nothing else. This is also exactly why
extract_content.py's cost-line parser cannot see this table at all, which is
the mechanism by which it has always been excluded (see its docstring).

**Row shape.** A deity name carries no full stop and an effect cell ends with
one, so a line without a '.' opens a row and everything after it up to the
next such line is that row's effect. That accumulates a wrapped effect cell
correctly, and an effect whose FIRST line lost its full stop would be read as
an eighth deity -- which verify() fails on, rather than shipping a mis-cut
table. Counting two lines per row works today and breaks on the first wrap,
the trap extract_shields.py already had to avoid.

What is NOT decided here: which of the seven effects the engine can apply.
That is a join between this table and chapter 3's skill banners, and lives in
Kotlin where both are in scope. verify() pins the evidence for the one join
that exists -- see OCCULT_ABSENT / KNOWLEDGE_PHRASE below -- because that
grep IS the warrant and a later reading must be made again if it changes.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "3"
SPAN = (1098, 1118)

# The rows themselves, between the two-line column header and the requirement.
ROWS = (1103, 1116)

PROSE = (1099, 1100)
HEADER = (1101, 1102)
REQUIREMENT = (1117, 1118)

COST_EACH = 75

# The sentence the cost is read from. Stored once because the book prints it
# once, for all seven rows at the same price.
COST_PHRASE = "Each blessing costs 75 points"

# The requirement's two halves. It is a rule the GM applies -- this app models
# no faith and no reconciliation -- so it is committed and shown, never
# enforced: the call the Sealed Artifact grade table already needed (6af).
REQUIREMENT_PHRASES = ("confirmed believer", "suspends blessings")

# The two effect cells that print a number the engine could reach. Pinned
# exactly, because the screen quotes them and a re-cut cell still reads
# plausibly (6bi).
PINNED_EFFECTS = {
    "God of Combat": "+3 ST, +3 DX, +3 HT.",
    "God of Knowledge and Wisdom": "+5 to all occult/knowledge skills.",
}

# The whole warrant for reading "occult/knowledge skills" as chapter 3's
# KNOWLEDGE banner. "occult skills" is printed NOWHERE in this book, so that
# half of the slash names no group at all; "knowledge skills" is printed, and
# one of its occurrences IS the banner. The day either changes, the reading
# has to be made again rather than inherited -- the per-table standard 6ad
# set, and 6v is why it can never be per-source.
OCCULT_ABSENT = "occult skill"
KNOWLEDGE_PHRASE = "knowledge skill"
KNOWLEDGE_BANNER = "KNOWLEDGE SKILLS"

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def joined(byn, start, end):
    parts = []
    for n in range(start, end + 1):
        text = (byn.get(n) or "").strip()
        if not text or FURNITURE.match(text):
            continue
        parts.append(text)
    if not parts:
        raise ValueError(f"lines {start}-{end} are empty")
    return " ".join(parts)


def parse(byn):
    """Seven deity/effect rows, accumulating a wrapped effect cell."""
    entries = []
    for n in range(ROWS[0], ROWS[1] + 1):
        text = (byn.get(n) or "").strip()
        if not text or FURNITURE.match(text):
            continue
        if "." not in text:
            entries.append({"deity": text, "effect": ""})
            continue
        if not entries:
            raise ValueError(f"line {n} is an effect with no deity above it: {text!r}")
        row = entries[-1]
        row["effect"] = (row["effect"] + " " + text).strip()
    return entries


def verify(byn, payload, advantages, disadvantages, skills):
    entries = payload["entries"]
    if len(entries) != 7:
        raise ValueError(
            f"the book prints seven blessings, parsed {len(entries)}: "
            f"{[e['deity'] for e in entries]}"
        )

    names = [e["deity"] for e in entries]
    if len(set(names)) != len(names):
        raise ValueError(f"deity names are not unique: {names}")
    for row in entries:
        if not row["effect"]:
            raise ValueError(f"{row['deity']} parsed with no effect cell")
        if not row["effect"].endswith("."):
            raise ValueError(
                f"{row['deity']}'s effect does not end in a full stop, so the "
                f"row shape has changed: {row['effect']!r}"
            )

    for deity, phrase in PINNED_EFFECTS.items():
        row = next((e for e in entries if e["deity"] == deity), None)
        if row is None:
            raise ValueError(f"{deity} is no longer printed")
        if phrase not in row["effect"]:
            raise ValueError(f"{deity} no longer reads {phrase!r}: {row['effect']!r}")

    # The price, and the sentence it was read from. The table prints no cost
    # column, so if this sentence goes the price has no source at all.
    prose = joined(byn, *PROSE)
    if COST_PHRASE not in prose:
        raise ValueError(f"the 75-point sentence changed: {prose!r}")
    if payload["costEach"] != COST_EACH:
        raise ValueError(f"costEach is not {COST_EACH}")

    for phrase in REQUIREMENT_PHRASES:
        if phrase not in payload["requirement"]:
            raise ValueError(f"the requirement no longer says {phrase!r}")

    # The header is what says these two columns are Deity and Effect and that
    # there is no third. A cost column appearing is the shape changing.
    header = joined(byn, *HEADER)
    if "Deity" not in header or "Blessing Effect" not in header:
        raise ValueError(f"the column header changed: {header!r}")
    if re.search(r"cost|point", header, re.I):
        raise ValueError(f"the table now prints a cost column: {header!r}")

    # THE WARRANT for the one join production makes. Checked over the whole
    # book, not over this table.
    body = [t for t in byn.values() if t]
    occult = [t for t in body if OCCULT_ABSENT in t.lower()]
    if occult:
        raise ValueError(
            "the book now prints 'occult skill(s)', so the slash in "
            f"'occult/knowledge skills' may name two groups: {occult[:3]}"
        )
    knowledge = [t for t in body if KNOWLEDGE_PHRASE in t.lower()]
    if len(knowledge) < 2:
        raise ValueError(f"'knowledge skill' is no longer printed twice: {knowledge}")
    if not any(KNOWLEDGE_BANNER in t for t in body):
        raise ValueError("chapter 3 no longer prints a KNOWLEDGE SKILLS banner")
    if not any(s.get("category") == KNOWLEDGE_BANNER for s in skills):
        raise ValueError("no skill carries the KNOWLEDGE SKILLS category")

    # Disjointness, the same check extract_appearance.py makes: a deity named
    # in the trait lists too would make matching a bare name ambiguous, and
    # would mean this table is a second printing rather than its own.
    printed = {t["name"] for t in advantages} | {t["name"] for t in disadvantages}
    clash = printed & set(names)
    if clash:
        raise ValueError(f"a deity is also a catalogue trait: {sorted(clash)}")


def write(payload):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "blessings.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def build(byn):
    return {
        "sourceLines": {CHAPTER: [list(SPAN)]},
        "costEach": COST_EACH,
        "requirement": joined(byn, *REQUIREMENT),
        "entries": parse(byn),
    }


def main():
    byn = load_lines()
    payload = build(byn)
    read = lambda name: json.loads((OUT / name).read_text(encoding="utf-8"))["entries"]
    verify(byn, payload, read("advantages.json"), read("disadvantages.json"), read("skills.json"))
    write(payload)
    print(f"blessings.json: {len(payload['entries'])} blessings at {COST_EACH} pts each")
    return 0


if __name__ == "__main__":
    sys.exit(main())
