"""Extract Chapter 7: Rest (p. 52-54, global 3465-3529), new in v6.9i.

One file, `rest.json`, in five parts:

  fpRecovery     Sec. I's two recovery rows (rest 1 FP / 10 min, sleep all)
  fpThresholds   Sec. I's two threshold rows. v6.9d printed the same two rows
                 in ch. 5's recovery table; `Pools` already applies both, and
                 this is now where they are printed.
  spi            Sec. II: 1 SPI per hour of rest; a Cogitation roll
                 (Will/Hard) doubles it to 2 for that rest.
  rests          Sec. III-IV: Short (3 h, one focused-task roll) and Long
                 (9 h, all FP, the better of two rolls, and an acting
                 session toward digestion).
  restrictions   Sec. V's four labelled rules, shown and never enforced: the
                 party decision and the GM's veto are table matters, and this
                 app holds no clock to count "one rest per day" against.

THE HEALING BULLETS DISAGREE WITH CHAPTER 5
--------------------------------------------
Both rests print "natural healing occurs at normal rates (1 HP per 8 hours of
rest)". Chapter 5's recovery table prints Natural Recovery as "1 HP ... Per
day; rest & food required", on an HT roll. Ch. 7 itself says "HP recovery
remains in Chapter 5 (Combat)", so ch. 5 is the authority and the ch. 7 rate
is recorded, not applied. verify() pins both sides (CLAUDE.md 4.1).
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINES_PATH = ROOT / "build" / "extract" / "lines.json"
OUT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

CHAPTER = "7"
REST_RANGE = (3465, 3529)
FP_TABLE = (3474, 3491)
SPI_SECTION = (3492, 3500)
SHORT_SECTION = (3501, 3509)
LONG_SECTION = (3510, 3519)
RULES_SECTION = (3520, 3529)

FURNITURE = re.compile(r"^(Powered by GURPS.*|Page \d+)$")
BULLET = "\x7f"
HEADERS = {"Method", "FP Recovered", "Time Required"}
FP_METHODS = ["Rest (sitting, no strenuous activity)", "Sleep"]
FP_THRESHOLDS = ["At 0 FP", "At 1/3 FP or below"]
DURATION = re.compile(r"^Duration: (\d+) hours\.")
LABEL = re.compile(r"^([A-Z][A-Za-z ]+): (.+)$")
RULE_LABELS = ["Group Decision", "GM Discretion", "Interrupted Rest", "One Rest Per Day"]

PINNED = {
    "spiPerHour": "recovers 1 SPI\nper hour",
    "cogitation": "Cogitation roll (Will/Hard) during a rest period. On success, SPI recovery is doubled\n(2 SPI per hour)",
    "hpStaysInCh5": "HP recovery remains in Chapter 5 (Combat).",
    "longRestDigestion": "a long rest counts as a full acting session toward digestion",
    "betterOfTwo": "may roll twice for a single focused task and take the better result",
    "shortOnce": "may roll once for a single focused task",
    "ch7Healing": "1 HP per 8 hours of rest",
}


def load_lines():
    return {row["n"]: row["text"] for row in json.loads(LINES_PATH.read_text("utf-8"))}


def stream(byn, first, last):
    out = []
    for n in range(first, last + 1):
        text = (byn.get(n) or "").strip()
        if text and not FURNITURE.match(text):
            out.append(text)
    return out


def fp_table(cells):
    cells = [c for c in cells if c not in HEADERS]
    names = FP_METHODS + FP_THRESHOLDS
    starts = [i for i, c in enumerate(cells) if c in names]
    rows = []
    for pos, i in enumerate(starts):
        end = starts[pos + 1] if pos + 1 < len(starts) else len(cells)
        body = cells[i + 1:end]
        # The 0-FP effect cell wraps ("... also costs 1" / "HP"), so the last
        # cell is the time and everything between is the effect.
        rows.append({"name": cells[i], "effect": " ".join(body[:-1]), "time": body[-1]})
    return rows


def bullets(cells):
    out, current = [], None
    for cell in cells:
        if cell.startswith(BULLET):
            current = cell.lstrip(BULLET).strip()
            out.append(current)
        elif out:
            out[-1] = f"{out[-1]} {cell}"
    return [dict(zip(("label", "text"), LABEL.match(b).groups())) for b in out]


def rest(cells, name, focused_rolls, all_fp, digestion):
    hours = DURATION.match(cells[1])
    if not hours:
        raise ValueError(f"{name} prints no duration: {cells[1]!r}")
    intro = []
    for cell in cells[1:]:
        if cell.startswith(BULLET):
            break
        intro.append(cell)
    row = {"name": name, "hours": int(hours.group(1)), "text": " ".join(intro),
           "focusedTaskRolls": focused_rolls, "allFp": all_fp,
           "bullets": bullets(cells[len(intro) + 1:])}
    if digestion:
        row["countsAsActingSession"] = True
    return row


def restrictions(cells):
    rows = []
    for cell in cells[1:]:
        hit = LABEL.match(cell)
        if hit and hit.group(1) in RULE_LABELS:
            rows.append({"label": hit.group(1), "text": hit.group(2)})
        elif rows:
            rows[-1]["text"] = f"{rows[-1]['text']} {cell}"
    return rows


def verify(data, text, ch5):
    if [r["name"] for r in data["fpRecovery"]] != FP_METHODS:
        raise ValueError(f"FP recovery rows changed: {data['fpRecovery']}")
    if [r["name"] for r in data["fpThresholds"]] != FP_THRESHOLDS:
        raise ValueError(f"FP threshold rows changed: {data['fpThresholds']}")
    if data["fpThresholds"][1]["effect"] != "-1 to all rolls; Move and Dodge halved":
        raise ValueError("the 1/3 FP row no longer prints what Pools applies")
    if [(r["name"], r["hours"]) for r in data["rests"]] != [("Short Rest", 3), ("Long Rest", 9)]:
        raise ValueError(f"rest durations changed: {data['rests']}")
    if [r["label"] for r in data["restrictions"]] != RULE_LABELS:
        raise ValueError(f"rest restrictions changed: {data['restrictions']}")
    for key, phrase in PINNED.items():
        if phrase.replace("\n", " ") not in text:
            raise ValueError(f"ch. 7 no longer prints {key}: {phrase!r}")
    if "Per day; rest & food required" not in ch5:
        raise ValueError("ch. 5 no longer prices natural recovery per day; the recorded "
                         "disagreement with ch. 7's 1 HP per 8 hours has to be re-read")


def main():
    byn = load_lines()
    text = " ".join(stream(byn, *REST_RANGE))
    fp = fp_table(stream(byn, *FP_TABLE))
    data = {
        "fpRecovery": fp[:2],
        "fpThresholds": fp[2:],
        "spi": {"perHour": 1, "cogitationPerHour": 2, "cogitationSkill": "Cogitation"},
        "rests": [
            rest(stream(byn, *SHORT_SECTION), "Short Rest", 1, False, False),
            rest(stream(byn, *LONG_SECTION), "Long Rest", 2, True, True),
        ],
        "restrictions": restrictions(stream(byn, *RULES_SECTION)),
    }
    ch5 = json.loads((OUT / "hp_recovery.json").read_text("utf-8"))
    verify(data, text, json.dumps(ch5, ensure_ascii=False))
    (OUT / "rest.json").write_text(
        json.dumps({"sourceLines": {CHAPTER: [list(REST_RANGE)]}, **data},
                   indent=1, ensure_ascii=False) + "\n", "utf-8")
    print(f"rest: {[(r['name'], r['hours']) for r in data['rests']]}, "
          f"{len(data['restrictions'])} restrictions")
    return 0


if __name__ == "__main__":
    sys.exit(main())
