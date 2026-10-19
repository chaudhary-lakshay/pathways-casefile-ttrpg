package com.pathways.rules

import com.pathways.model.BeyonderState
import com.pathways.model.DiceExpr
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
 * [corGained] is the book's "1d6 CoR" for advancing without full digestion
 * (v6.9i line 3188-3189), rolled once the potion integrates; 0 on a failure
 * or after full digestion, when no die is thrown.
 */
data class PcrOutcome(
    val roll: RollResult,
    val target: Int,
    val digestion: Int,
    val corGained: Int,
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
 * The Potion Consumption Roll: `3d6 <= 10 + Digestion Modifier - CoR`.
 *
 * "This is a raw 3d6 roll -- no stat or skill applies. Only the drinker's
 * digestion of their current potion matters" (line 3200-3201) -- and then,
 * new in v6.9i, "A Beyonder's current CoR also imposes a disadvantage on the
 * PCR: -1 per point of CoR" (3203-3204). Those two are the only inputs,
 * which is why this takes a [BeyonderState] and a CoR, not a Character.
 *
 * **No other carried state reaches it, and that is the same sentence.**
 * Reeling, FP below 0, shock and the five declared trait states each print
 * "to all rolls" (6gs), and this roll takes none of them: "only ... matters"
 * is the specific rule and "all rolls" the general one, the call
 * [PcrOutcome.integrated] already makes for the critical rules. CoR is not
 * an exception to that reading but its proof: the PCR's own section names
 * it, so it is specific. A grep of every content file for a modifier that
 * NAMES this roll (6gt has the query) found one, the Beast Tamer's "+4 to
 * rolls involving drinking potions", and it is for a tamed animal.
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

    /**
     * The table's effective target less [cor], one per point (line 3203).
     *
     * The table's "always fails" / "always succeeds" notes describe CoR 0;
     * past 18 nothing can fail and below 3 nothing can succeed either way.
     */
    fun targetFor(digestion: Int, cor: Int, rows: List<PcrRow> = ContentLoader.potionConsumption()): Int {
        require(cor >= 0) { "CoR cannot be negative; was $cor" }
        return rowFor(digestion, rows).effectiveTarget - cor
    }

    /**
     * Drink the next potion. [state] is the CURRENT Sequence -- the one being
     * digested -- because that is what the roll is made against.
     *
     * The 1d6 CoR is rolled only once the potion integrates: a failure ends
     * the character as a Rampager, so a die thrown there would price nothing.
     * Reported through [RandomSource.thrown] like every other die (6ht).
     */
    fun attempt(
        state: BeyonderState,
        cor: Int,
        source: RandomSource,
        rows: List<PcrRow> = ContentLoader.potionConsumption(),
    ): PcrOutcome {
        val target = targetFor(state.digestion, cor, rows)
        val roll = SuccessRoll.against(target, source)
        val dice = corDiceFor(state)
        val gained = if (roll.total <= target && dice != null) {
            val faces = List(dice.count) { source.roll(dice.sides) }
            source.thrown(faces, null)
            faces.sum() + dice.modifier
        } else {
            0
        }
        return PcrOutcome(roll = roll, target = target, digestion = state.digestion, corGained = gained)
    }

    /**
     * The CoR die advancing from [state] rolls on a successful PCR: the
     * book's before full digestion, none at 100%.
     *
     * A function of the state and not of the roll, so the screen can print it
     * beside the Drink button and [attempt] can roll the same die.
     */
    fun corDiceFor(state: BeyonderState): DiceExpr? =
        if (state.isFullyDigested) null else earlyAdvancementDice()

    /**
     * The CoR die for advancing before full digestion, read from the row
     * that prints it rather than restated here: "Advancing sequence without
     * full digestion -- 1d6 CoR" (line 3188-3189).
     */
    fun earlyAdvancementDice(): DiceExpr =
        ContentLoader.corGained().firstOrNull { it.action.startsWith(ADVANCING_EARLY) }?.dice
            ?.let(DiceExpr::parse)
            ?: error("cor_gained.json has no rolled row starting '$ADVANCING_EARLY'")

    /** The opening of the row [earlyAdvancementDice] reads. */
    const val ADVANCING_EARLY = "Advancing sequence"
}
