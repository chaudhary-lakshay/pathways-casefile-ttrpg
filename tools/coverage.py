"""Report how much of each chapter has been claimed by extracted content.

A content file may declare which source lines it consumed:

    { "sourceLines": { "3": [[1717, 2100]] }, "entries": [...] }

Everything not claimed is counted as unclaimed. A line is "mechanical" if
it contains a die expression, a signed modifier, a point cost, or an
attribute offset — prose without those is flavour and may stay unclaimed.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "build" / "extract"
CONTENT = ROOT / "core" / "rules" / "src" / "main" / "resources" / "content"

MECHANICAL = re.compile(r"\d+d[-+]?\d*|[+-]\d+\b|\d+\s*pts?\b|\bAttr[+-]\d+")


def main() -> int:
    pages = json.loads((OUT / "pages.json").read_text(encoding="utf-8"))
    chapters = json.loads((OUT / "chapters.json").read_text(encoding="utf-8"))

    claimed: dict[str, list[tuple[int, int]]] = {}
    if CONTENT.exists():
        for f in CONTENT.glob("*.json"):
            data = json.loads(f.read_text(encoding="utf-8"))
            for cid, spans in (data.get("sourceLines") or {}).items():
                claimed.setdefault(cid, []).extend(tuple(s) for s in spans)

    lines = ["# Extraction coverage", "",
             "| Ch | Title | Pages | Claimed | Unclaimed mechanical lines |",
             "|---|---|---|---|---|"]
    failed = False

    for ch in chapters:
        body = [p for p in pages if ch["startPage"] <= p["page"] <= ch["endPage"]]
        total = sum(len(p["text"].splitlines()) for p in body)
        spans = claimed.get(ch["id"], [])
        claimed_lines = sum(b - a + 1 for a, b in spans)
        pct = (claimed_lines / total * 100) if total else 0.0

        mechanical = sum(
            1
            for p in body
            for line in p["text"].splitlines()
            if MECHANICAL.search(line)
        )
        unclaimed = max(0, mechanical - claimed_lines)
        if pct > 0 and unclaimed > 0:
            failed = True

        lines.append(f"| {ch['id']} | {ch['title']} | "
                     f"{ch['startPage']}-{ch['endPage']} | {pct:.1f}% | {unclaimed} |")

    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "coverage.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
