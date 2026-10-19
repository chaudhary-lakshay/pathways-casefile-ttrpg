"""Re-price an extracted character sheet from rulebook v6.9d to v6.9i.

The owner's ruling 3 (2026-10-19): the 23 published sheets are RE-PRICED to
v6.9i so they reconcile, rather than kept and recorded as disagreements.

Every printed figure moves by the DIFFERENCE between what it counts at the
v6.9d price and at the v6.9i price; nothing is re-summed. A sheet that got
its own arithmetic wrong under v6.9d (Monastery Scholar, University
Librarian) keeps that error, and the guard tests that pin it keep passing.

What moved between the two books, for anything these sheets hold:
  - attributes: v6.9d doubled each level from a 10 (ST, HT) or 20 (DX, IQ)
    first step; v6.9i prices "a flat rate per level" (book 353-366), read
    from attribute_costs.json.
  - Charity: 10 -> 15, and its cell, which the sheet quotes glued to the
    name, now reads "Must roll vs. 12 or help ...".
  - Acute Vision: 2 -> 5.
The v6.9d figures are constants here because v6.9d's content is no longer
on disk; the v6.9i figures are read from the content files, never from the
Kotlin engine. A trait is re-priced only where the sheet printed its v6.9d
price: a cost the sheet already had wrong stays wrong.

`extract_sheets.py` applies this after its Roll/Cost checksum, which needs
the as-printed subtotals.
"""
import copy, json, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
CONTENT = ROOT / "core/rules/src/main/resources/content"

# v6.9d's price for each row whose price v6.9i changed and a sheet holds.
V69D_PRICES = {"Charity": 10, "Acute Vision": 2}
# v6.9d's first-level step per attribute; each later level doubled it.
V69D_FIRST_STEP = {"ST": 10, "DX": 20, "IQ": 20, "HT": 10}
BASE_ATTRIBUTE = 9
MAX_DISADVANTAGES = 40
SEPARATOR = " - "


def catalogue() -> dict:
    """The v6.9i advantage and disadvantage rows, keyed by lower-case name."""
    rows = {}
    for f in ("advantages", "disadvantages"):
        for r in json.loads((CONTENT / f"{f}.json").read_text(encoding="utf-8"))["entries"]:
            rows[r["name"].lower()] = r
    return rows


def flat_rates() -> dict:
    data = json.loads((CONTENT / "attribute_costs.json").read_text(encoding="utf-8"))
    return {r["attr"]: r["costPerLevel"] for r in data["entries"]}


def v69d_attribute_cost(attr: str, level: int) -> int:
    return sum(V69D_FIRST_STEP[attr] << i for i in range(level - BASE_ATTRIBUTE))


def head(name: str) -> str:
    """The row name before a glued cell: "Charity - Cannot ..." -> "Charity"."""
    return name.split(SEPARATOR, 1)[0].strip()


def _capped(disadvantages) -> int:
    return min(sum(t["cost"] for t in disadvantages), MAX_DISADVANTAGES)


def _shift(published: dict, key: str, delta: int) -> None:
    if delta and published.get(key) is not None:
        published[key] += delta


def reprice(sheet: dict) -> dict:
    out = copy.deepcopy(sheet)
    published = out["published"]
    rates = flat_rates()
    rows = catalogue()

    attribute_delta = sum(
        rates[a] * (level - BASE_ATTRIBUTE) - v69d_attribute_cost(a, level)
        for a, level in out["attributes"].items()
    )
    _shift(published, "attributesSubtotal", attribute_delta)

    trait_delta = {}
    for kind in ("advantages", "disadvantages"):
        delta = 0
        for t in out[kind]:
            name = head(t["name"])
            row = rows.get(name.lower())
            if row is None or V69D_PRICES.get(name) != t["cost"]:
                continue
            delta += row["cost"] - t["cost"]
            t["cost"] = row["cost"]
            if SEPARATOR in t["name"]:
                t["name"] = f"{name}{SEPARATOR}{row['notes']}"
        trait_delta[kind] = delta
    _shift(published, "advantagesSubtotal", trait_delta["advantages"])
    _shift(published, "disadvantagesSubtotal", trait_delta["disadvantages"])

    spent = attribute_delta + trait_delta["advantages"]
    budget = _capped(out["disadvantages"]) - _capped(sheet["disadvantages"])
    _shift(published, "totalSpent", spent)
    _shift(published, "bpTotal", budget)
    # Beat Cop prints Unspent with neither a budget nor a total beside it.
    if published.get("totalSpent") is not None:
        _shift(published, "unspent", budget - spent)
    return out
