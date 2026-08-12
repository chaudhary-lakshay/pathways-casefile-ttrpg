"""Extract the three rules that REFUSE an active defence (Ch. 5, p. 36-40).

Chapter 5 prints three separate reasons a character gets no defence at all,
and none of them is in a table -- each is one sentence inside a paragraph, in
three different sections a page or more apart:

  SURPRISE (2377) sits in the turn-order section.
  ALL_OUT_ATTACK (2453) is a parenthetical inside the sentence that GRANTS
    the defence, directly above the defences table.
  UNAWARE_RANGED (2686-2688) is in the ranged-attack section, and wraps
    across three lines.

All three were UNCLAIMED by any content file when this was written -- they
fall in the seams between extracted tables, which is the sweep direction 6ay
opened and 6ba widened. A rule nobody can find again is a rule that goes
unapplied, so they are claimed here rather than quoted from Kotlin.

What is NOT here, deliberately:

  The MANEUVERS that forfeit a defence. maneuvers.json has carried
  "Cannot defend next turn." on four rows since the combat slice -- the three
  All-Out Attacks and Charge Attack -- so the list is already committed
  content, and a second copy here would be free to drift. verify() asserts
  the phrase is still on exactly those four rows, because that set is what
  production reads to decide which declaration is even offerable.

  Combat Reflexes negating SURPRISE. That is a join between this chapter and
  chapter 3's trait catalogue, and lives in Kotlin where both are in scope.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "5"

# One span per rule. Each is the whole sentence and nothing else: the lines
# either side belong to rules other files already claim or deliberately leave.
SPANS = [(2377, 2377), (2453, 2453), (2686, 2688)]

# The cause each span defines, in the order the spans are listed. The KEY is
# what com.pathways.rules.DefenseRefusal names; the book prints no key, so
# these are ours and the sentence is the book's.
CAUSES = ["SURPRISE", "ALL_OUT_ATTACK", "UNAWARE_RANGED"]

# The phrase that has to survive in each, so a re-parse that quietly widens or
# truncates a sentence fails instead of shipping a rule that reads differently.
PINNED = {
    "SURPRISE": "cannot defend on it",
    "ALL_OUT_ATTACK": "unless you took an All-Out Attack maneuver",
    "UNAWARE_RANGED": "you cannot defend at all",
}

# maneuvers.json's own sentence, and the rows that must carry it.
FORFEIT_PHRASE = "Cannot defend next turn."
FORFEIT_ROWS = {
    "All-Out Attack (Determined)",
    "All-Out Attack (Strong)",
    "All-Out Attack (Double)",
    "Charge Attack",
}

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def sentence(byn, start, end):
    """The span joined into one line, page furniture dropped.

    UNAWARE_RANGED wraps mid-sentence across a page break, so the halves are
    joined rather than read line by line -- the same join
    extract_languages needed for "costs" / "half.".
    """
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
    if len(SPANS) != len(CAUSES):
        raise ValueError("every span defines exactly one cause")
    return [
        {"cause": cause, "printed": sentence(byn, start, end)}
        for cause, (start, end) in zip(CAUSES, SPANS)
    ]


def verify(entries, maneuvers):
    got = [e["cause"] for e in entries]
    if got != CAUSES:
        raise ValueError(f"causes changed: {got} != {CAUSES}")
    for row in entries:
        phrase = PINNED[row["cause"]]
        if phrase not in row["printed"]:
            raise ValueError(f"{row['cause']} no longer reads {phrase!r}: {row['printed']!r}")

    # A refusal is not a modifier: none of these prints a number to apply
    # instead. A signed number appearing here would mean the sentence was
    # re-cut onto a neighbouring rule.
    for row in entries:
        if re.search(r"[-+−]\d", row["printed"]):
            raise ValueError(f"{row['cause']} prints a modifier, not a refusal: {row['printed']!r}")

    # The maneuver half is committed content, not re-extracted. This is the
    # whole evidence that Charge Attack forfeits too -- p. 37's parenthetical
    # names only the All-Out Attacks, and the table names four rows.
    forfeit = {m["maneuver"] for m in maneuvers if FORFEIT_PHRASE in m["description"]}
    if forfeit != FORFEIT_ROWS:
        raise ValueError(f"maneuvers that forfeit a defence changed: {sorted(forfeit)}")


def write(entries):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: [list(s) for s in SPANS]}, "entries": entries}
    (OUT / "defense_refusals.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def main():
    byn = load_lines()
    entries = parse(byn)
    maneuvers = json.loads((OUT / "maneuvers.json").read_text(encoding="utf-8"))["entries"]
    verify(entries, maneuvers)
    write(entries)
    print(f"defense_refusals.json: {len(entries)} rules")
    return 0


if __name__ == "__main__":
    sys.exit(main())
