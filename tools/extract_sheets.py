"""Convert the 23 published .xlsx character sheets into JSON fixtures.

These are FIXTURES, not content. Where a sheet disagrees with the
rulebook the sheet is wrong (spec section 6); we record what it printed
so the engine can show a computed-vs-published diff.

Reads the CharSheetP1 tab only. ThrustTable is content (Task 10), and
Summons is byte-identical boilerplate across all 23 files with no
character data at all.
"""
import glob, json, os, pathlib, re

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = pathlib.Path(os.path.expanduser("~/Downloads/LOTM TTRPG Sheets"))
OUT = ROOT / "fixtures"

DIFFICULTIES = {"E", "A", "H", "VH"}


def slug(name: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


def num(v):
    """Cell -> int when integral, float when not, None when blank."""
    if v is None or v == "":
        return None
    try:
        f = float(v)
    except (TypeError, ValueError):
        return None
    return int(f) if f == int(f) else f


def find(grid, label):
    """Row/col of the first cell equal to `label`, or None."""
    for r, row in enumerate(grid):
        for c, cell in enumerate(row):
            if isinstance(cell, str) and cell.strip() == label:
                return r, c
    return None


def parse_skill(raw: str):
    """'History (Occult)/IQ' -> ('History', 'Occult', 'IQ')."""
    name_part, _, attr = raw.rpartition("/")
    m = re.match(r"^(.*?)\s*\((.*)\)\s*$", name_part.strip())
    if m:
        return m.group(1).strip(), m.group(2).strip(), attr.strip()
    return name_part.strip(), None, attr.strip()


def extract(path: pathlib.Path) -> dict:
    from openpyxl import load_workbook

    ws = load_workbook(str(path), data_only=True)["CharSheetP1"]
    grid = [list(r) for r in ws.iter_rows(values_only=True)]

    def value_right_of(label, offset=1):
        pos = find(grid, label)
        if pos is None:
            return None
        r, c = pos
        return num(grid[r][c + offset]) if c + offset < len(grid[r]) else None

    attributes = {a: value_right_of(a) for a in ("ST", "DX", "IQ", "HT")}

    published = {
        "hp":         value_right_of("HP"),
        "fp":         value_right_of("FP"),
        "will":       value_right_of("Will"),
        "per":        value_right_of("Per"),
        "spi":        value_right_of("SPI"),
        "cor":        value_right_of("CoR"),
        "basicSpeed": value_right_of("BASE SPD"),
        "basicMove":  value_right_of("BASE MOVE"),
        "dodge":      value_right_of("Dodge"),
        "bpTotal":    None,
        "totalSpent": None,
        "unspent":    value_right_of("Unspent", 2),
    }

    # "Budget (BP)" and "Total spent" print as strings like "100 pts".
    for label, key in (("Budget (BP)", "bpTotal"), ("Total spent", "totalSpent")):
        pos = find(grid, label)
        if pos:
            r, c = pos
            for cell in grid[r][c + 1:]:
                if isinstance(cell, str):
                    m = re.search(r"(\d+)\s*pts", cell)
                    if m:
                        published[key] = int(m.group(1))
                        break

    def traits(header):
        """Parse an ADVANTAGES/DISADVANTAGES block.

        Each block has its own two-row header ("ADVANTAGES AND PERKS" /
        "Name ... Roll Cost") immediately above the data rows. The cost
        column position is read off THAT header row rather than assumed,
        because some sheets also populate the "Roll" column for these
        entries (e.g. Fortune Teller's "Charisma 2", Street Acrobat's
        disadvantages) -- scanning for "the first number in the row"
        then grabs Roll instead of Cost. Locating "Cost" by its header
        label and always reading that fixed column is correct for every
        sheet, since the column position itself does not drift.
        """
        pos = find(grid, header)
        if pos is None:
            return []
        r0, c0 = pos
        header_row = grid[r0 + 1] if r0 + 1 < len(grid) else []
        cost_col = None
        for i, cell in enumerate(header_row[c0:]):
            if isinstance(cell, str) and cell.strip() == "Cost":
                cost_col = c0 + i
                break
        out = []
        for row in grid[r0 + 2:]:                    # +2 skips the sub-header row
            name = row[c0] if c0 < len(row) else None
            if not isinstance(name, str) or not name.strip():
                break
            if name.startswith("*GURPS"):
                break
            if cost_col is not None and cost_col < len(row):
                cost = num(row[cost_col])
            else:
                cost = None
            out.append({"name": name.strip(), "cost": cost or 0})
        return out

    skills = []
    pos = find(grid, "SKILLS & TECHNIQUE")
    if pos:
        r0, c0 = pos
        for row in grid[r0 + 2:]:
            cell = row[c0] if c0 < len(row) else None
            if not isinstance(cell, str) or not cell.strip():
                break
            text = cell.strip()
            if "/" not in text:                      # category header, e.g. "Social & Utility (56 pts):"
                continue
            rest = [x for x in row[c0 + 1:] if x is not None and x != ""]
            diff = next((str(x).strip() for x in rest if str(x).strip() in DIFFICULTIES), None)
            nums = [num(x) for x in rest if num(x) is not None]
            if diff is None or len(nums) < 2:
                continue
            name, spec, attr = parse_skill(text)
            skills.append({
                "name": name, "specialization": spec, "attr": attr,
                "difficulty": diff, "points": nums[1], "publishedLevel": nums[0],
            })

    return {
        "name": path.stem,
        "attributes": attributes,
        "published": published,
        "advantages": traits("ADVANTAGES AND PERKS"),
        "disadvantages": traits("DISADVANTAGES AND QUIRKS"),
        "skills": skills,
    }


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    n = 0
    for f in sorted(glob.glob(str(SRC / "*.xlsx"))):
        p = pathlib.Path(f)
        if p.name.startswith("~$"):        # Excel lock file
            continue
        data = extract(p)
        (OUT / f"{slug(p.stem)}.json").write_text(
            json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
        n += 1
    print(f"{n} fixtures -> {OUT}")


if __name__ == "__main__":
    main()
