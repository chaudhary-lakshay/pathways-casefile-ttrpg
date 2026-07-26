"""Extract skills, advantages, disadvantages and the thrust/swing table into
committed JSON content for :core:rules.

Source shapes (verified against build/extract/lines.json, not assumed):

Skills (Ch. 3, "Complete Skill List by Category", global lines 1664-2246):
    Guns (Pistol)
     DX/Easy
    Derringers, semi-automatic pistols
Three-line records: name / " ATTR/Difficulty" / notes (notes may wrap).
Category banners look like "— COMBAT SKILLS —"; the header trio
(" Skill"/" Attr / Difficulty"/" Notes") appears once, right after the
section title. Trailing appendix content (Study Points optional rule,
Character Creation Summary, global lines 2181-2246) is a different table
shape entirely and is NOT part of the skill list; it is deliberately
excluded and shows up as unclaimed in coverage.py. The single "Inventor!"
entry uses a "Wildcard" difficulty that the closed Difficulty enum
(Easy/Average/Hard/Very Hard) cannot represent, so it is also excluded
(and reported, not silently invented).

Advantages (Ch. 3, "Step 2: Advantages", global lines 530-1118) and
Disadvantages ("Step 3: Disadvantages", 1119-1569) share the same
name / cost / effect triple shape, with category banners of the form
"Advantage"/"Cost"/"Effect" (or "Effect / When It Triggers") appearing as
a 3-line header directly under a banner title. Two non-standard subtables
(Languages, lines 633-712, and Appearance, lines 714-744) use different
column headers entirely (Language/Region, Level/Reaction Modifier) and no
skill in any of the 23 fixtures references them, so they are excluded by
exact line range rather than forced through the Name/Cost/Effect parser.
The "Seven Orthodox Gods Blessings" table (1098-1118) has no per-row cost
line at all (cost is stated once in prose, "each blessing costs 75
points") and is excluded the same way, by virtue of never producing a
cost-line match.

Some disadvantages (e.g. "Obsession", "Code of Honor", "Sense of Duty",
"Social Stigma", "Secret", "Blood Feud") print a variable point cost as a
range ("-5 to -10") rather than a single value -- the specific point cost
is chosen per-character within that range, so there is no single "the"
cost to report. Those entries carry costMin/costMax (both positive
magnitudes, consistent with the fixed-cost convention below) instead of
cost. Every entry has EITHER cost XOR (costMin AND costMax); never
neither -- a record with no cost representation at all is a hard parse
failure (see the assertion in extract_traits), not a silently-omitted
field.

Names almost always fit on one line, but wrap when long: "Church
Organisation" / "Informant" is a single advantage split across two lines
by the PDF's column width. These are rare (verified by dumping every
block of 3+ lines between cost lines and checking each one by hand) and
are listed explicitly in ADV_NAME_OVERRIDES / DIS_NAME_OVERRIDES below,
keyed by the line number of the record's cost line.

Record-boundary note (defect fixed here): the layout is
    Name
     Cost-or-Attr/Difficulty
    Notes (may wrap onto multiple lines, continuing right up to the next
    record's Name line)
so for any given cost/attr-diff line, the text immediately BEFORE it
(back to the previous cost/attr-diff line) is the CURRENT record's name,
but the text immediately AFTER it (up to the NEXT record's name) is also
the CURRENT record's notes -- not the next record's. The previous version
of this script computed that trailing span but attributed it to the
following record, shifting every entry's notes back by one position. The
fix below builds one block per cost line (name only) and then explicitly
assigns each block's leading "pre-name" text as the *previous* record's
notes, with the true last record's trailing text (to the end of the
extraction range) captured separately.
"""
import json, pathlib, re

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

FURNITURE = re.compile(r"^Powered by GURPS: Pathways|^Page \d+$")
WRAPPER_TITLE = re.compile(
    r"^(Step \d+:.*|Mundane Advantages|Supernatural Advantages"
    r"|Mundane Disadvantages|Supernatural Disadvantages)$"
)

# Languages (633-712) and Appearance (714-744) use non-standard table
# shapes (Language/Region columns; Level/Reaction Modifier columns) with
# no fixture ever referencing an entry from either. The "Seven Orthodox
# Gods Blessings" table (1098-1118) has no per-row cost line -- cost is
# stated once in prose ("each blessing costs 75 points") -- and uses a
# Deity/Effect shape instead of Name/Cost/Effect. All three are excluded
# by exact range rather than forced through the general parser.
ADV_EXCLUDE_RANGES = [(633, 744), (1098, 1118)]

DIFFICULTY_WORDS = {"Easy": "E", "Average": "A", "Hard": "H", "Very Hard": "VH"}


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def clean_stream(byn, start, end, exclude_ranges=()):
    out = []
    for n in range(start, end + 1):
        t = byn.get(n)
        if t is None:
            continue
        if FURNITURE.match(t.strip()):
            continue
        if any(a <= n <= b for a, b in exclude_ranges):
            continue
        out.append((n, t))
    return out


def strip_wrappers_and_banners(stream, header_word):
    """Remove banner titles + their 3-line header trio, and supercategory
    wrapper titles + their lead-in prose (which always run up to the next
    banner with zero cost lines in between -- verified against the data,
    not assumed)."""
    result = []
    i = 0
    n_items = len(stream)
    while i < n_items:
        n, t = stream[i]
        tstrip = t.strip()
        if i + 1 < n_items and stream[i + 1][1].strip() == header_word:
            i += 4  # banner title + 3-line header trio
            continue
        if WRAPPER_TITLE.match(tstrip):
            j = i + 1
            while j < n_items:
                if j + 1 < n_items and stream[j + 1][1].strip() == header_word:
                    break
                j += 1
            i = j
            continue
        result.append((n, t))
        i += 1
    return result


def cost_line_adv(t):
    t2 = t.strip()
    if t2.lower() == "varies":
        return True
    if re.match(r"^[+]?\d+\s+or\s+\d+\s*pts?(/level)?$", t2, re.IGNORECASE):
        return True
    return bool(re.match(r"^[+]?\d+(\s*[–—-]\s*\d+)?\s*pts?(/level)?$", t2, re.IGNORECASE))


def cost_line_dis(t):
    t2 = t.strip()
    return bool(re.match(r"^-\d+(\s*(to|–|—)\s*-?\d+)?\s*(pts?)?$", t2, re.IGNORECASE))


# Verified by hand against every block of 3+ lines between two cost lines
# in the advantages/disadvantages ranges (see module docstring): these are
# the only records whose printed name wraps across more than one line.
# Keyed by the global line number of the record's cost line.
ADV_NAME_OVERRIDES = {
    570: [568, 569],   # "Church Organisation" / "Informant"
    762: [760, 761],   # "Connections: Church Lay" / "Staff"
    767: [765, 766],   # "Connections: Press" / "Credentials"
}
DIS_NAME_OVERRIDES = {
    1415: [1413, 1414],  # "Compulsive Behavior" / "(Indulge Evil Desires) SC 6"
    1458: [1456, 1457],  # "Pathway Resonance" / "(Hostile)"
    1483: [1481, 1482],  # "Compulsion: Collection" / "(pathway)"
    1492: [1490, 1491],  # "Compulsion: Preserve the" / "Dead"
}

SELF_CONTROL_SUFFIX = re.compile(r"\s*\(\d+\)$")  # e.g. "Curious (12)" -> "Curious"


def strip_self_control_number(name: str) -> str:
    return SELF_CONTROL_SUFFIX.sub("", name).strip()


def parse_adv_cost(t):
    t2 = t.strip()
    per_level = "/level" in t2.lower()
    body = t2.lower().replace("/level", "").strip()
    if body == "varies":
        return {"variableCost": True, "perLevel": per_level}
    body = t2.replace("/level", "").replace("pts", "").replace("pt", "").strip()
    m = re.match(r"^\+?(\d+)\s+or\s+(\d+)$", body, re.IGNORECASE)
    if m:
        return {"costMin": int(m.group(1)), "costMax": int(m.group(2)), "perLevel": per_level}
    m = re.match(r"^\+?(\d+)\s*[–—-]\s*(\d+)$", body)
    if m:
        return {"costMin": int(m.group(1)), "costMax": int(m.group(2)), "perLevel": per_level}
    m = re.match(r"^\+?(\d+)$", body)
    if m:
        return {"cost": int(m.group(1)), "perLevel": per_level}
    raise ValueError(f"unparsed advantage cost: {t!r}")


def parse_dis_cost(t):
    body = t.strip().replace("pts", "").replace("pt", "").strip()
    m = re.match(r"^-(\d+)\s*(?:to|–|—)\s*-?(\d+)$", body, re.IGNORECASE)
    if m:
        lo, hi = int(m.group(1)), int(m.group(2))
        return {"costMin": min(lo, hi), "costMax": max(lo, hi)}
    m = re.match(r"^-(\d+)$", body)
    if m:
        return {"cost": int(m.group(1))}
    raise ValueError(f"unparsed disadvantage cost: {t!r}")


def _require_cost_shape(entry, cost_n):
    has_single = "cost" in entry
    has_range = "costMin" in entry and "costMax" in entry
    if not has_single and not has_range and not entry.get("variableCost"):
        raise ValueError(
            f"record ending at cost line {cost_n} has no cost representation "
            f"at all (name={entry.get('name')!r}); refusing to write a silent gap"
        )


def extract_traits(byn, start, end, header_word, cost_fn, cost_parser, overrides, exclude_ranges=()):
    stream = clean_stream(byn, start, end, exclude_ranges)
    stream = strip_wrappers_and_banners(stream, header_word)
    cost_idx = [i for i, (n, t) in enumerate(stream) if cost_fn(t)]

    # Pass 1: one block per cost line, containing ONLY that record's name
    # (the last line(s) before its cost line) and the "pre-name" text that
    # precedes it, which actually belongs to the PREVIOUS record's notes
    # (see docstring). We deliberately do not attach pre_name_lines to the
    # record being built here.
    blocks = []
    prev_end = -1
    for ci in cost_idx:
        block = stream[prev_end + 1:ci]
        if not block:
            raise ValueError(f"no name found before cost line {stream[ci][0]}")
        cost_n, cost_t = stream[ci]
        override = overrides.get(cost_n)
        if override:
            name_lines = [(n, t) for n, t in block if n in override]
            pre_name_lines = [(n, t) for n, t in block if n not in override]
        else:
            name_lines = [block[-1]]
            pre_name_lines = block[:-1]
        blocks.append({
            "name_lines": name_lines,
            "pre_name_lines": pre_name_lines,  # notes for the PREVIOUS record
            "cost_n": cost_n,
            "cost_t": cost_t,
        })
        prev_end = ci

    # Trailing text after the last cost line belongs to the last record's
    # own notes (it has no "next name line" to be mistaken for).
    trailing_notes_lines = stream[prev_end + 1:]

    entries = []
    spans = []
    for i, b in enumerate(blocks):
        name = strip_self_control_number(" ".join(t.strip() for _, t in b["name_lines"]))
        cost_fields = cost_parser(b["cost_t"])
        notes_lines = blocks[i + 1]["pre_name_lines"] if i + 1 < len(blocks) else trailing_notes_lines
        notes = " ".join(t.strip() for _, t in notes_lines)

        entry = {"name": name, **cost_fields, "notes": notes}
        _require_cost_shape(entry, b["cost_n"])
        entries.append(entry)

        first_line = b["name_lines"][0][0]
        last_line = notes_lines[-1][0] if notes_lines else b["cost_n"]
        spans.append([first_line, last_line])

    spans.sort()
    merged = []
    for a, bb in spans:
        if merged and a <= merged[-1][1] + 1:
            merged[-1][1] = max(merged[-1][1], bb)
        else:
            merged.append([a, bb])

    return entries, merged


SKILL_ATTR_DIFF = re.compile(
    r"^(ST|DX|IQ|HT|Will|Per|SPI)/(Easy|Average|Hard|Very Hard|Wildcard)$"
)
SKILL_BANNER = re.compile(r"^— .+ —$")

SKILL_HEADER_WORD = "Skill"
SKILL_SECTION_END = 2181  # "Study Points (Optional Rule)" appendix begins here;
                           # different table shape, no fixture references it.


def clean_skill_stream(byn, start, end):
    out = []
    for n in range(start, end + 1):
        t = byn.get(n)
        if t is None:
            continue
        if FURNITURE.match(t.strip()):
            continue
        out.append((n, t))
    return out


def strip_skill_structure(stream):
    result = []
    i = 0
    n_items = len(stream)
    while i < n_items:
        n, t = stream[i]
        tstrip = t.strip()
        if tstrip == "Complete Skill List by Category":
            i += 4  # title + " Skill" / " Attr / Difficulty" / " Notes"
            continue
        if SKILL_BANNER.match(tstrip):
            i += 1  # category banner only; no per-category header repeats
            continue
        result.append((n, t))
        i += 1
    return result


SPECIFY_PAREN = re.compile(r"\s*\((specify[^)]*|any)\)$", re.IGNORECASE)


def parse_skill_name(raw: str):
    """'Guns (Pistol)' -> ('Guns', 'Pistol'); 'Law (specify)' -> ('Law', None)."""
    m = SPECIFY_PAREN.search(raw)
    if m:
        return raw[: m.start()].strip(), None
    m = re.match(r"^(.*?)\s*\((.*)\)\s*$", raw.strip())
    if m:
        return m.group(1).strip(), m.group(2).strip()
    return raw.strip(), None


def is_specializable(raw_name: str, notes: str) -> bool:
    """A skill is specializable when the book marks it with a
    "(specify...)" style suffix -- most often at the end of the notes
    ("Specific region (specify)" for Area Knowledge, "Specific secret
    knowledge (specify type)" for Hidden Lore), but occasionally directly
    on the name line itself ("Law (specify)", "Connoisseur (specify)").
    Detected from the text in either place, not from a hardcoded list of
    skill names."""
    return bool(SPECIFY_PAREN.search(raw_name.strip()) or SPECIFY_PAREN.search(notes.strip()))


def extract_skills(byn):
    stream = clean_skill_stream(byn, 1664, SKILL_SECTION_END - 1)
    stream = strip_skill_structure(stream)
    cost_idx = [i for i, (n, t) in enumerate(stream) if SKILL_ATTR_DIFF.match(t.strip())]

    # Same record-boundary fix as extract_traits: build one block per
    # attr/difficulty line (name only), then assign each block's
    # "pre-name" text to the PREVIOUS record's notes.
    blocks = []
    prev_end = -1
    for ci in cost_idx:
        block = stream[prev_end + 1:ci]
        if not block:
            raise ValueError(f"no name found before skill attr/diff line {stream[ci][0]}")
        name_lines = [block[-1]]
        pre_name_lines = block[:-1]
        cost_n, cost_t = stream[ci]
        blocks.append({
            "name_lines": name_lines,
            "pre_name_lines": pre_name_lines,
            "cost_n": cost_n,
            "cost_t": cost_t,
        })
        prev_end = ci

    trailing_notes_lines = stream[prev_end + 1:]

    entries = []
    spans = []
    skipped = []
    for i, b in enumerate(blocks):
        cost_n, cost_t = b["cost_n"], b["cost_t"]
        attr_code, diff_word = cost_t.strip().split("/")
        raw_name = b["name_lines"][0][1].strip()
        notes_lines = blocks[i + 1]["pre_name_lines"] if i + 1 < len(blocks) else trailing_notes_lines

        if diff_word == "Wildcard":
            # Difficulty is a closed Easy/Average/Hard/Very Hard enum;
            # "Inventor!" (IQ/Wildcard) has no representation and is
            # skipped rather than forced into a wrong bucket.
            skipped.append((cost_n, raw_name, "Wildcard difficulty has no Difficulty enum value"))
            continue

        name, spec = parse_skill_name(raw_name)
        notes = " ".join(t.strip() for _, t in notes_lines)
        first_line = b["name_lines"][0][0]
        last_line = notes_lines[-1][0] if notes_lines else cost_n
        entries.append({
            "name": name,
            "specialization": spec,
            "attr": attr_code,
            "difficulty": DIFFICULTY_WORDS[diff_word],
            "specializable": is_specializable(raw_name, notes),
            "notes": notes,
        })
        spans.append([first_line, last_line])

    spans.sort()
    merged = []
    for a, bb in spans:
        if merged and a <= merged[-1][1] + 1:
            merged[-1][1] = max(merged[-1][1], bb)
        else:
            merged.append([a, bb])

    return entries, merged, skipped


def write_content_file(name, entries, spans):
    OUT.mkdir(parents=True, exist_ok=True)
    payload = {
        "sourceLines": {"3": spans} if spans else {},
        "entries": entries,
    }
    (OUT / f"{name}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def extract_thrust_swing():
    from openpyxl import load_workbook
    import glob, os

    src_dir = pathlib.Path(os.path.expanduser("~/Downloads/LOTM TTRPG Sheets"))
    fixtures = sorted(glob.glob(str(src_dir / "*.xlsx")))
    fixtures = [f for f in fixtures if not pathlib.Path(f).name.startswith("~$")]
    if not fixtures:
        raise SystemExit("no source .xlsx sheets found to read ThrustTable from")

    # ThrustTable is byte-identical across all 23 fixtures (per Task 10
    # brief); read the first one but verify against a second to be sure
    # rather than trusting that claim blindly.
    def read_table(path):
        ws = load_workbook(str(path), data_only=True)["ThrustTable"]
        grid = [list(r) for r in ws.iter_rows(values_only=True)]
        rows = []
        for row in grid:
            if len(row) < 4:
                continue
            st, thrust, swing = row[1], row[2], row[3]
            if not isinstance(st, (int, float)):
                continue
            rows.append({"st": int(st), "thrust": str(thrust).strip(), "swing": str(swing).strip()})
        return rows

    first = read_table(fixtures[0])
    second = read_table(fixtures[-1])
    if first != second:
        raise ValueError(
            f"ThrustTable differs between {fixtures[0]} and {fixtures[-1]}; "
            "not byte-identical as assumed, need per-row reconciliation"
        )
    if len(first) != 30:
        raise ValueError(f"expected 30 ST rows, got {len(first)}")
    return first


def main():
    byn = load_lines()

    adv_entries, adv_spans = extract_traits(
        byn, 530, 1118, "Advantage", cost_line_adv, parse_adv_cost,
        ADV_NAME_OVERRIDES, ADV_EXCLUDE_RANGES,
    )
    dis_entries, dis_spans = extract_traits(
        byn, 1119, 1569, "Disadvantage", cost_line_dis, parse_dis_cost,
        DIS_NAME_OVERRIDES,
    )
    skill_entries, skill_spans, skipped_skills = extract_skills(byn)
    thrust_swing_rows = extract_thrust_swing()

    write_content_file("advantages", adv_entries, adv_spans)
    write_content_file("disadvantages", dis_entries, dis_spans)
    write_content_file("skills", skill_entries, skill_spans)
    write_content_file("thrust_swing", thrust_swing_rows, [])

    print(f"advantages: {len(adv_entries)} entries, spans={adv_spans}")
    print(f"disadvantages: {len(dis_entries)} entries, spans={dis_spans}")
    print(f"skills: {len(skill_entries)} entries, spans={skill_spans}")
    if skipped_skills:
        print(f"skills skipped ({len(skipped_skills)}):")
        for n, name, reason in skipped_skills:
            print(f"  line {n}: {name!r} -- {reason}")
    print(f"thrust_swing: {len(thrust_swing_rows)} rows")


if __name__ == "__main__":
    main()
