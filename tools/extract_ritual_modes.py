"""Extract the Quick Ritual's two modifiers (Ch. 9 III.A, p. 60, global 3958-3959).

v6.9i prints them as one prose line, not two tables:

    Preparation Quality: Excellent +2, Adequate +0, Poor/None -2 to -4.
    Target Link: Perfect (name + possession) +4, Strong (name or possession)
    +2 to +3, Weak (alias, secondhand) +0 to -1, None -4.

THE BOOK PRINTS TWO PREPARATION LADDERS AND THEY DISAGREE
----------------------------------------------------------
Quick: Excellent +2. Full (Phase I, `ritual_preparation.json`): Excellent +3.
Neither is repaired. They are two modes, the book says so, and which one is in
play is the GM's declaration (6y).

AND THE LINK IS COUNTED ONCE IN EACH MODE, DIFFERENTLY
-------------------------------------------------------
Quick mode prices the link on its own, +4 to -4. Full mode folds it IN:
Phase I reads "Declare target entity, timing, materials, space, sacrifice, and
target link. GM determines Preparation Modifier" -- the link is one of the
declarations a single modifier is drawn from. So target_link belongs to the
QUICK ritual only; offering it in Full mode pays a perfect link twice.
verify() pins Phase I's sentence, because it is the whole evidence.

WHAT v6.9i DELETED
------------------
The Supporting Skills table and its +3 cap are gone, so ritual_support.json
is gone with them. The no-default sentence survives as Sec. I's one line,
"Ritualistic Magic (IQ/Very Hard) -- no default, cannot be substituted."
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "9"
NO_DEFAULT_RANGE = (3896, 3896)
QUICK_MODIFIERS_RANGE = (3958, 3959)
PHASE_ONE_RANGE = (3963, 3964)

NO_DEFAULT_SENTENCE = "no default, cannot be substituted"
LINK_FOLDED = "sacrifice, and target link. GM determines Preparation Modifier"

MINUS = "-−"
VALUE = rf"([+{MINUS}]\d+)(?:\s+to\s+([+{MINUS}]\d+))?"
PREPARATION_LINE = re.compile(r"Preparation Quality:\s*(.+?)\.\s*Target Link:")
LINK_LINE = re.compile(r"Target Link:\s*(.+?)\.$")
ROW = re.compile(rf"([A-Z][A-Za-z/ ]*?)\s*(?:\(([^)]+)\))?\s+{VALUE}(?:,|$)")

EXPECTED_QUICK = ["Excellent", "Adequate", "Poor/None"]
EXPECTED_LINKS = ["Perfect", "Strong", "Weak", "None"]
FULL_PREPARATION = OUT / "ritual_preparation.json"


def load_lines():
    data = json.loads(LINES_PATH.read_text(encoding="utf-8"))
    return {l["n"]: l["text"] for l in data}


def joined(byn, start, end):
    return " ".join((byn.get(n) or "").strip() for n in range(start, end + 1))


def signed(text):
    return int(text.replace("−", "-"))


def parse_rows(text, key, expected):
    rows = []
    for name, looks_like, near, far in ROW.findall(text.strip()):
        row = {key: name.strip(), "modifier": signed(near),
               "printed": near + (f" to {far}" if far else "")}
        if far:
            row["best"] = signed(far)
        if looks_like:
            row["looksLike"] = looks_like
        rows.append(row)
    got = [r[key] for r in rows]
    if got != expected:
        raise ValueError(f"the {key} ladder changed: {got} != {expected}")
    return rows


def verify(quick, links, no_default, phase_one):
    if NO_DEFAULT_SENTENCE not in no_default:
        raise ValueError(f"Sec. I no longer prints {NO_DEFAULT_SENTENCE!r}")
    if LINK_FOLDED not in phase_one:
        raise ValueError(
            "Phase I no longer folds the target link into its Preparation "
            "Modifier; nothing then stops a Full ritual counting the link twice"
        )
    full = {r["quality"]: r["modifier"]
            for r in json.loads(FULL_PREPARATION.read_text(encoding="utf-8"))["entries"]}
    quick_by_name = {r["quality"]: r["modifier"] for r in quick}
    if quick_by_name["Excellent"] == full["Excellent"]:
        raise ValueError("the two preparation ladders now agree on Excellent")
    if quick_by_name["Adequate"] != full["Adequate"]:
        raise ValueError("the two ladders used to agree that Adequate is +0")
    if [r["modifier"] for r in links] != [4, 2, 0, -4]:
        raise ValueError(f"target link values changed: {links}")
    if [r.get("best") for r in links] != [None, 3, -1, None]:
        raise ValueError(f"target link ranges changed: {links}")
    if [r.get("best") for r in quick] != [None, None, -4]:
        raise ValueError(f"quick preparation ranges changed: {quick}")


def main():
    byn = load_lines()
    text = joined(byn, *QUICK_MODIFIERS_RANGE)
    quick = parse_rows(PREPARATION_LINE.search(text).group(1), "quality", EXPECTED_QUICK)
    links = parse_rows(LINK_LINE.search(text).group(1), "link", EXPECTED_LINKS)
    verify(quick, links, joined(byn, *NO_DEFAULT_RANGE), joined(byn, *PHASE_ONE_RANGE))

    def write(name, rows, *spans):
        payload = {"sourceLines": {CHAPTER: [list(s) for s in spans]}, "entries": rows}
        (OUT / name).write_text(
            json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")

    write("ritual_quick_preparation.json", quick, NO_DEFAULT_RANGE, QUICK_MODIFIERS_RANGE)
    write("ritual_target_link.json", links, QUICK_MODIFIERS_RANGE)
    print(f"quick preparation: {[(r['quality'], r['printed']) for r in quick]}")
    print(f"target link: {[(r['link'], r['printed'], r.get('looksLike')) for r in links]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
