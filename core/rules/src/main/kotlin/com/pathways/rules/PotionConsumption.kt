package com.pathways.rules

import com.pathways.model.BeyonderState
import com.pathways.model.Outcome
import com.pathways.model.RollResult
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.PcrRow

/**
 * The result of drinking the next Sequence's potion (Ch. 6, p. 45-46).
 *
 * The book gives exactly two outcomes and no middle: on a success the potion
 * integrates, on a failure "the character is lost -- they transform into an
 * NPC Rampager immediately, permanently under the GM's control". [lost] is
 * therefore the end of that character as a player character, which is why it
 * is stated here rather than left for a caller to infer from a margin.
 *
 * [minimumCorGained] is the book's "10+ CoR" for advancing without full
 * digestion (p. 45). The floor is the book's; the "+" is the GM's, so only
 * the floor is computed.
 */
data class PcrOutcome(
    val roll: RollResult,
    val target: Int,
    val digestion: Int,
    val minimumCorGained: Int,
) {
    /**
     * Whether the potion took, decided by the raw total against the target.
     *
     * NOT by [RollResult.outcome]: the generic success roll applies GURPS
     * critical rules, where 3-4 always succeed and 17-18 always fail whatever
     * the target. The PCR table overrides both ends -- it annotates the 0%
     * row "1 -- always fails" and the 100% row "19 -- always succeeds", which
     * only holds if a 3 loses at target 1 and an 18 survives at target 19.
     * The specific rule wins over the general one; [roll] is still carried
     * whole so a table can see what was thrown and rule otherwise.
     */
    val integrated: Boolean get() = roll.total <= target

    val lost: Boolean get() = !integrated
}

/**
 * The Potion Consumption Roll: `3d6 <= 10 + Digestion Modifier`.
 *
 * "This is a raw 3d6 roll -- no stat or skill applies. Only the drinker's
 * digestion of their current potion matters" (p. 45). Nothing about the
 * character is an input beyond that percentage, which is why this takes a
 * [BeyonderState] and not a Character.
 */
object PotionConsumption {

    /**
     * The table row governing [digestion].
     *
     * The book tabulates deciles only. A partly-digested potion between two
     * rows uses the lower one: you do not get the better bracket until you
     * have reached it. The book never contemplates 47%, so this is a reading,
     * not a quotation -- but rounding up would hand out a bonus the table
     * never grants.
     */
    fun rowFor(digestion: Int, rows: List<PcrRow> = ContentLoader.potionConsumption()): PcrRow {
        require(digestion in 0..100) { "digestion is a percentage; was $digestion" }
        return rows.filter { it.digestion <= digestion }.maxByOrNull { it.digestion }
            ?: error("no Potion Consumption Roll row at or below $digestion%")
    }

    /** The effective target as the book prints it, not as it recomputes. */
    fun targetFor(digestion: Int, rows: List<PcrRow> = ContentLoader.potionConsumption()): Int =
        rowFor(digestion, rows).effectiveTarget

    /**
     * Drink the next potion. [state] is the CURRENT Sequence -- the one being
     * digested -- because that is what the roll is made against.
     */
    fun attempt(
        state: BeyonderState,
        source: RandomSource,
        rows: List<PcrRow> = ContentLoader.potionConsumption(),
    ): PcrOutcome {
        val target = targetFor(state.digestion, rows)
        return PcrOutcome(
            roll = SuccessRoll.against(target, source),
            target = target,
            digestion = state.digestion,
            minimumCorGained = if (state.isFullyDigested) 0 else earlyAdvancementCor(),
        )
    }

    /**
     * The CoR floor for advancing before full digestion, read from the row
     * that prints it rather than restated here.
     *
     * p. 45: "Advancing sequence without full digestion: 10+ CoR". The floor
     * is the row's minimum; the "+" is an open ceiling the row records as a
     * null maximum, and the GM sets the rest.
     */
    fun earlyAdvancementCor(): Int =
        ContentLoader.corGained().firstOrNull { it.action.startsWith(ADVANCING_EARLY) }?.minimum
            ?: error("cor_gained.json has no priced row starting '$ADVANCING_EARLY'")

    /** The opening of the row [earlyAdvancementCor] reads. */
    const val ADVANCING_EARLY = "Advancing sequence"
}
