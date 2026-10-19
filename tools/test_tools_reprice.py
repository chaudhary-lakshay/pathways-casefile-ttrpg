"""reprice_sheets: the 23 sheets moved from rulebook v6.9d prices to v6.9i (owner's ruling 3)."""
import copy, json

import reprice_sheets
from testsupport import ROOT


def sheet(attributes, advantages=(), disadvantages=(), **published):
    base = dict(bpTotal=100, totalSpent=90, unspent=10, attributesSubtotal=None,
                advantagesSubtotal=None, disadvantagesSubtotal=None, skillsSubtotal=None)
    base.update(published)
    return {
        "name": "Test",
        "attributes": dict(zip(("ST", "DX", "IQ", "HT"), attributes)),
        "published": base,
        "advantages": [{"name": n, "cost": c} for n, c in advantages],
        "disadvantages": [{"name": n, "cost": c} for n, c in disadvantages],
        "skills": [],
    }


def test_attributes_move_by_the_difference_between_the_two_tables():
    # v6.9d doubled: HT 11 = 10 + 20 = 30, IQ 12 = 20 + 40 + 80 = 140.
    # v6.9i is flat (book 353-366): HT 11 = 40, IQ 12 = 60. Delta +10 - 80.
    out = reprice_sheets.reprice(sheet((9, 9, 12, 11), attributesSubtotal=170, totalSpent=190, unspent=10))
    assert out["published"]["attributesSubtotal"] == 100
    assert out["published"]["totalSpent"] == 120
    assert out["published"]["unspent"] == 80


def test_a_sheet_arithmetic_error_survives_the_move():
    # Printed 175 where v6.9d says 170: the 5 stays, only the delta moves.
    out = reprice_sheets.reprice(sheet((9, 9, 12, 11), attributesSubtotal=175))
    assert out["published"]["attributesSubtotal"] == 105


def test_a_trait_at_its_v69d_price_takes_the_v69i_price():
    out = reprice_sheets.reprice(sheet((9, 9, 9, 9), advantages=[("Acute Vision", 2)], advantagesSubtotal=2))
    assert out["advantages"] == [{"name": "Acute Vision", "cost": 5}]
    assert out["published"]["advantagesSubtotal"] == 5
    assert out["published"]["totalSpent"] == 93
    assert out["published"]["unspent"] == 7


def test_a_trait_the_sheet_already_mispriced_is_left_alone():
    out = reprice_sheets.reprice(sheet((9, 9, 9, 9), advantages=[("Acute Vision", 3)]))
    assert out["advantages"] == [{"name": "Acute Vision", "cost": 3}]
    assert out["published"]["totalSpent"] == 90


def test_charity_takes_its_new_price_and_cell_and_raises_the_budget_under_the_cap():
    old_cell = ("Cannot ignore genuine need. Must roll vs. Will to avoid helping anyone\n"
                "who asks for assistance you can reasonably provide. May be exploited.")
    out = reprice_sheets.reprice(sheet(
        (9, 9, 9, 9),
        disadvantages=[(f"Charity - {old_cell}", 10), ("Vow (Poverty)", 10)],
        disadvantagesSubtotal=20, bpTotal=90, unspent=0,
    ))
    charity = out["disadvantages"][0]
    assert charity["cost"] == 15
    cell = reprice_sheets.catalogue()["charity"]["notes"]
    assert charity["name"] == f"Charity - {cell}"
    assert "Must roll vs. 12" in charity["name"]
    assert out["published"]["disadvantagesSubtotal"] == 25
    assert out["published"]["bpTotal"] == 95
    assert out["published"]["unspent"] == 5


def test_the_budget_moves_only_below_the_40_point_cap():
    out = reprice_sheets.reprice(sheet(
        (9, 9, 9, 9),
        disadvantages=[("Charity", 10), ("Duty (PMC)", 30)],
        bpTotal=110, unspent=20,
    ))
    assert out["published"]["bpTotal"] == 110
    assert out["published"]["unspent"] == 20


def test_reprice_is_pure_and_absent_figures_stay_absent():
    s = sheet((9, 9, 12, 9), bpTotal=None, totalSpent=None, unspent=0)
    before = copy.deepcopy(s)
    out = reprice_sheets.reprice(s)
    assert s == before
    assert out["published"]["totalSpent"] is None
    assert out["published"]["attributesSubtotal"] is None
    # Beat Cop prints Unspent 0 beside no budget and no total: left as printed.
    assert out["published"]["unspent"] == 0


def test_every_committed_fixture_is_priced_for_v69i():
    # The committed fixtures are the extractor's output, repriced: applying
    # the reprice again finds nothing left at a v6.9d price.
    for path in sorted((ROOT / "fixtures").glob("*.json")):
        f = json.loads(path.read_text(encoding="utf-8"))
        if "published" not in f:
            continue
        for kind in ("advantages", "disadvantages"):
            for t in f[kind]:
                head = reprice_sheets.head(t["name"])
                assert reprice_sheets.V69D_PRICES.get(head) != t["cost"], (path.name, t)
