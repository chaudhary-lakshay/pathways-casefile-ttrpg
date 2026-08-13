"""Extract the Maneuvers table and the two option lists (Ch. 5, p. 36-37).

Three ranges, three shapes:

  MANEUVERS (global lines 2384-2437) is a three-column table -- Maneuver,
  Movement, Description -- flattened into wrapped lines. Its header wraps too
  ("Movemen" / "t"), which is why HEADER carries both halves.

  There is no column geometry to cut on: this PDF reports x=0 for every glyph.
  What IS reliable is that the middle column is a CLOSED SET of five tokens
  ("Full Move", "Step", "Step*", "None", em dash), so a line is a maneuver
  NAME exactly when the line after it is one of those. Everything else is
  description continuation. Guessing "short line = name" would take "(max
  ACC+2). Canceled by..." for a row of its own; the set is asserted in
  verify(), so a change to the book's own movement column fails loudly instead
  of silently re-cutting the table.

  EXTRA EFFORT (2438-2442) and ATTACK OPTIONS (2443-2451) are both bullet
  lists -- this PDF prints its bullets as U+007F -- so they share bullets().
  Extra effort bullets fit one line each; attack option bullets wrap.

What is and is NOT extracted as a number, following the defenses slice: the FP
COST is extracted, because the app charges it against the FP tracker and a
cost that is only printed is a tracker that lies. The to-hit modifiers inside
the prose (+4 determined, -4 charge, -6 rapid strike, -2 per -1 deceptive) are
NOT: com.pathways.rules.Attacks implements them and is tested against the
book, and a second copy in JSON would be free to drift. verify() pins the
sentences those numbers are read from, so the book changing under the code is
still a loud failure.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "5"
MANEUVER_RANGE = (2384, 2437)
EXTRA_EFFORT_RANGE = (2438, 2442)
ATTACK_OPTION_RANGE = (2443, 2451)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
# This PDF prints its list bullets as U+007F, which survives text extraction.
BULLET = "\x7f•● "
# "Movemen" / "t" is one wrapped heading; both halves have to be dropped.
HEADER = {
    "Maneuvers (Choose One Per Turn)", "Maneuver", "Movemen", "t", "Description",
    "Extra Effort Options (Costs FP)", "Attack Options",
}
# The middle column, exactly as the book prints it. "Step*" marks the footnote
# on Concentrate; the em dash is Wait, which moves not at all.
MOVEMENTS = {"Full Move", "Step", "Step*", "None", "—"}

FP_COST = re.compile(r"Pay (\d+) FP")

EXPECTED_MANEUVERS = [
    "Move", "Ready", "Aim (Ranged)", "Do Nothing", "Concentrate", "Wait",
    "Regular Attack",
    "All-Out Attack (Determined)", "All-Out Attack (Strong)", "All-Out Attack (Double)",
    "Charge Attack",
    "All-Out Defense (Increased)", "All-Out Defense (Double)",
    "Feint",
]
EXPECTED_EFFORT = [
    "Giant Step", "Strong Blow", "Heroic Charge",
    "Feverish Defense — Variant Rule",
]
EXPECTED_OPTIONS = ["Rapid Strike", "Deceptive Attack"]

# The sentences com.pathways.rules.Attacks reads its constants from. Pinned
# here so the book changing a number fails the extractor rather than leaving
# the Kotlin quietly wrong.
PINNED = {
    "All-Out Attack (Determined)": "+4 to hit melee, +1 to hit ranged",
    "Charge Attack": "-4 to hit",
}
PINNED_OPTIONS = {
    "Rapid Strike": "-6 to skill",
    "Deceptive Attack": "-2 penalty to your melee attack roll for every -1",
}
# Strong Blow's +2 is a com.pathways.rules.Attacks constant, and both halves
# of its scope are a reading argued from this exact sentence: "Regular" names
# the maneuver row printed two lines above the bullet list, and "melee" keeps
# it out of reach of a shot. Pinned whole, so the book restating either half
# differently fails here rather than leaving the Kotlin quietly wrong.
PINNED_EFFORT = {
    "Strong Blow": "+2 damage. Regular melee attacks only.",
}
# The stun penalty com.pathways.rules.ActiveDefenses reads, and the screen
# quotes this cell verbatim. EXACT for the same reason as PINNED_EFFORT: the
# cell holds two rules -- the -4, and an FP recovery this app has no clock for
# -- so a clause dropped or appended changes what a GM is shown.
PINNED_MANEUVERS_EXACT = {
    "Do Nothing": "If Stunned: -4 to Active Defense. Otherwise rest to recover FP.",
    # The Acc rows are in ranged_modifiers.json; the sentence that CANCELS
    # them is only here, on the maneuver. Exact, because com.pathways.rules
    # .Ranged quotes this cell as the reason it withdraws them, and because
    # "max ACC+2" is the half that has to keep agreeing with that table.
    "Aim (Ranged)": "Add weapon's ACC to your next ranged Attack. "
    "+1 per additional Aim (max ACC+2). Canceled by any Active Defense or injury.",
    # com.pathways.rules.Concentration reads the -3 from here and the screen
    # quotes the cell. Exact, because the CONDITION is the rule: drop "if
    # injured or defending" and an unconditional Will-3 taxes every act of
    # focus the book gates behind this maneuver.
    "Concentrate": "Channel powers or skills requiring focus. "
    "Roll Will-3 if injured or defending.",
}
# Ch. 11 prints the same -4 where no maneuver is in sight, on an ability that
# leaves its target "mentally Stunned for 1 turn". That corroboration is the
# entire warrant for two readings: the penalty belongs to the STATE rather
# than to the Do Nothing row it is printed on, and it is plural -- "active
# defenses" -- so it reaches every row rather than Dodge alone.
STUN_CORROBORATION = "-4 to active defenses"


def stun_corroboration(byn):
    """The lines outside chapter 5 that print the same penalty."""
    return [n for n, t in byn.items() if STUN_CORROBORATION in t and n > MANEUVER_RANGE[1]]


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def stream(byn, start, end):
    """(is_bullet, text) per line, furniture and headers dropped.

    The bullet flag is kept rather than thrown away with the marker: it is the
    only unambiguous way to tell a new bullet from a wrapped continuation, and
    both option lists wrap. Guessing by shape -- "short capitalised text before
    a colon" -- also matches "Rapid Strike (apply to each attack separately)"
    tails and the intro sentence above the list.
    """
    out = []
    for n in range(start, end + 1):
        text = byn.get(n)
        if text is None:
            continue
        stripped = text.strip()
        bulleted = stripped[:1] in BULLET
        stripped = stripped.lstrip(BULLET).strip()
        if not stripped or FURNITURE.match(stripped) or stripped in HEADER:
            continue
        out.append((bulleted, stripped))
    return out


def texts(stream_out):
    """Just the text of a [stream], for ranges with no bullets in them."""
    return [text for _, text in stream_out]


def parse_maneuvers(lines):
    """Rows of Maneuver / Movement / Description, descriptions rejoined.

    A line is a name exactly when the NEXT line is a movement token -- see the
    module docstring for why that beats any shape heuristic here.
    """
    rows = []
    skip = False
    for i, line in enumerate(lines):
        if skip:
            skip = False
            continue
        if i + 1 < len(lines) and lines[i + 1] in MOVEMENTS:
            rows.append({"maneuver": line, "movement": lines[i + 1], "parts": []})
            skip = True
            continue
        if not rows:
            raise ValueError(f"description text before any maneuver: {line!r}")
        rows[-1]["parts"].append(line)

    out = []
    for row in rows:
        if not row["parts"]:
            raise ValueError(f"maneuver {row['maneuver']!r} has no description")
        description = " ".join(row["parts"])
        entry = {
            "maneuver": row["maneuver"],
            "movement": row["movement"],
            "description": description,
        }
        cost = FP_COST.search(description)
        if cost:
            entry["fp"] = int(cost.group(1))
        out.append(entry)
    return out


def bullets(lines):
    """Rejoin wrapped bullets. Prose before the first bullet is intro text.

    The Attack Options list opens with three lines explaining what an attack
    option is; they are claimed by this range so coverage.py sees them, and
    dropped here because they are not rules.
    """
    out = []
    for bulleted, text in lines:
        if bulleted:
            out.append(text)
        elif out:
            out[-1] = f"{out[-1]} {text}"
    return out


def parse_extra_effort(lines):
    """[Free Action] [Giant Step] Pay 1 FP: <effect> -> name, fp, effect.

    The [Free Action] tag is on all four and carries no per-row information,
    so it is checked here rather than shipped as a field repeated four times.
    """
    out = []
    for bullet in bullets(lines):
        tags = re.findall(r"\[([^\]]+)\]", bullet)
        if len(tags) != 2:
            raise ValueError(f"expected two bracket tags, got {tags} in {bullet!r}")
        if tags[0] != "Free Action":
            raise ValueError(f"extra effort {tags[1]!r} is no longer a free action: {tags[0]!r}")
        rest = bullet.split("]", 2)[-1].strip()
        cost = FP_COST.search(rest)
        if not cost:
            raise ValueError(f"extra effort {tags[1]!r} prints no FP cost: {rest!r}")
        effect = rest.split(":", 1)[1].strip() if ":" in rest else rest
        out.append({"option": tags[1], "fp": int(cost.group(1)), "effect": effect})
    return out


def parse_attack_options(lines):
    """"Rapid Strike: <rule>" -> name, rule."""
    out = []
    for bullet in bullets(lines):
        if ":" not in bullet:
            raise ValueError(f"attack option prints no name: {bullet!r}")
        name, rule = bullet.split(":", 1)
        out.append({"option": name.strip(), "rule": rule.strip()})
    return out


def verify(maneuvers, effort, options, byn=None):
    names = [m["maneuver"] for m in maneuvers]
    if names != EXPECTED_MANEUVERS:
        raise ValueError(f"maneuver rows changed: {names} != {EXPECTED_MANEUVERS}")
    for row in maneuvers:
        if row["movement"] not in MOVEMENTS:
            raise ValueError(f"{row['maneuver']} has movement {row['movement']!r}")
    # Every All-Out maneuver costs FP and nothing else does; a cost that
    # stopped being parsed would silently stop being charged.
    charged = {m["maneuver"] for m in maneuvers if "fp" in m}
    expected_charged = {n for n in EXPECTED_MANEUVERS if n.startswith("All-Out")}
    if charged != expected_charged:
        raise ValueError(f"FP-charging maneuvers changed: {sorted(charged)}")

    by_name = {m["maneuver"]: m["description"] for m in maneuvers}
    for name, phrase in PINNED.items():
        if phrase not in by_name[name]:
            raise ValueError(f"{name} no longer reads {phrase!r}: {by_name[name]!r}")
    for name, phrase in PINNED_MANEUVERS_EXACT.items():
        if by_name[name] != phrase:
            raise ValueError(f"{name} no longer reads {phrase!r}: {by_name[name]!r}")
    # Checked against the whole book, not this chapter: the corroboration is
    # in Ch. 11 and it is what licenses reading the -4 as a property of the
    # state rather than of the Do Nothing row.
    if byn is not None and not stun_corroboration(byn):
        raise ValueError(f"nothing outside ch. 5 still prints {STUN_CORROBORATION!r}")

    got = [e["option"] for e in effort]
    if got != EXPECTED_EFFORT:
        raise ValueError(f"extra effort options changed: {got} != {EXPECTED_EFFORT}")
    # EXACT, where PINNED and PINNED_OPTIONS are substring checks: those cells
    # are several sentences of prose around one number, while an extra-effort
    # effect cell IS the whole rule. A sentence APPENDED to it -- "Cannot
    # combine with Rapid Strike" -- would leave the substring intact and the
    # Kotlin scope stale, which is the drift this pin exists to catch (Codex).
    by_effort = {e["option"]: e["effect"] for e in effort}
    for name, phrase in PINNED_EFFORT.items():
        if by_effort[name] != phrase:
            raise ValueError(f"{name} no longer reads {phrase!r}: {by_effort[name]!r}")

    got = [o["option"] for o in options]
    if got != EXPECTED_OPTIONS:
        raise ValueError(f"attack options changed: {got} != {EXPECTED_OPTIONS}")
    by_option = {o["option"]: o["rule"] for o in options}
    for name, phrase in PINNED_OPTIONS.items():
        if phrase not in by_option[name]:
            raise ValueError(f"{name} no longer reads {phrase!r}: {by_option[name]!r}")


def write(name, entries, spans):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {"sourceLines": {CHAPTER: spans}, "entries": entries}
    (OUT / f"{name}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def main():
    byn = load_lines()
    maneuvers = parse_maneuvers(texts(stream(byn, *MANEUVER_RANGE)))
    effort = parse_extra_effort(stream(byn, *EXTRA_EFFORT_RANGE))
    options = parse_attack_options(stream(byn, *ATTACK_OPTION_RANGE))
    verify(maneuvers, effort, options, byn)

    write("maneuvers", maneuvers, [list(MANEUVER_RANGE)])
    write("extra_effort", effort, [list(EXTRA_EFFORT_RANGE)])
    write("attack_options", options, [list(ATTACK_OPTION_RANGE)])

    print(f"maneuvers: {len(maneuvers)} rows, {sum('fp' in m for m in maneuvers)} charging FP")
    print(f"extra_effort: {[e['option'] for e in effort]}")
    print(f"attack_options: {[o['option'] for o in options]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
