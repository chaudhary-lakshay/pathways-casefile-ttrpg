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
