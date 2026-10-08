package com.pathways.rules

import com.pathways.model.*

data class BudgetReport(
    val budget: Int,
    val spent: Int,
    val violations: List<String>,
) {
    val surplus: Int get() = budget - spent
}

/**
 * Ch. 3, p. 11 and p. 19.
 *
 * Budget = 70 + capped disadvantages (max 40) + capped quirks (max 5).
 * Spending must be <= budget, never == budget: surplus is normal and
 * several published sheets leave points unspent.
 *
 * **Nothing this app ships produces a quirk, and the term is kept anyway.**
 * Not "unreachable": [report] takes any `List<Trait>`, so a caller or a
 * deserialized character can hand it one, and the branch below handles that
 * correctly (a review corrected the stronger wording). The book prints
 * the rule -- "Up to 5 ADDITIONAL points of quirks (minor personality traits
 * at -1 pt each) are allowed", lines 1121-1122, where `additional` is what
 * puts them beside the -40 rather than inside it -- but nothing produces one.
 * Checked against three corpora, not one (6de's verdict failed for naming a
 * single one): no Kotlin under `src/main` constructs [TraitKind.QUIRK], none
 * of the 23 fixtures carries a trait of that kind, and no row of
 * `disadvantages.json` costs 1, so [PurchasableTraits] cannot offer one
 * either. `extract_sheets.py` reads the sheets' own "DISADVANTAGES AND
 * QUIRKS" block whole and files all of it as [TraitKind.DISADVANTAGE] -- a
 * block NAME, not evidence any sheet holds a quirk; none does.
 *
 * That is why the term stays: the book prints the rule, the engine answers it
 * correctly for any caller who supplies one, and deleting it would drop a
 * printed rule to tidy away a branch the bundled data happens not to reach.
 *
 * **A sold secondary characteristic is not counted against the -40.** A
 * reading, not a quotation: the book states the cap of "disadvantages" (line
 * 1120, and "Take Disadvantages ... up to -40 pts worth", 455-456), which it
 * goes on to list, and prices each secondary "+-N pts per level" (358-383)
 * with no cap of its own. Nothing printed files a sold level under either
 * heading, so a refund lowers [BudgetReport.spent] and leaves the budget
 * alone. GURPS Basic counts it; that is not this book. What a sale can
 * return is bounded by [ScoreFloors] alone.
 */
object PointBudget {

    const val STARTING_TOTAL = 70
    const val MAX_DISADVANTAGES = 40
    const val MAX_QUIRKS = 5

    fun report(
        attributes: Attributes,
        secondaryDelta: SecondaryDelta,
        traits: List<Trait>,
        skills: List<PurchasedSkill>,
    ): BudgetReport {
        fun sumOf(kind: TraitKind) = traits.filter { it.kind == kind }.sumOf { it.cost }

        val rawDisadvantages = sumOf(TraitKind.DISADVANTAGE)
        val rawQuirks = sumOf(TraitKind.QUIRK)
        val budget = STARTING_TOTAL +
            minOf(rawDisadvantages, MAX_DISADVANTAGES) +
            minOf(rawQuirks, MAX_QUIRKS)

        val spent = AttributeCosts.totalCost(attributes) +
            SecondaryCharacteristics.costOf(secondaryDelta) +
            sumOf(TraitKind.ADVANTAGE) +
            skills.sumOf { it.points }

        val violations = buildList {
            if (rawDisadvantages > MAX_DISADVANTAGES) {
                add("Disadvantages total $rawDisadvantages, capped at $MAX_DISADVANTAGES")
            }
            if (rawQuirks > MAX_QUIRKS) {
                add("Quirks total $rawQuirks, capped at $MAX_QUIRKS")
            }
            if (spent > budget) add("Overspent: $spent of $budget")
        }

        return BudgetReport(budget, spent, violations)
    }
}
