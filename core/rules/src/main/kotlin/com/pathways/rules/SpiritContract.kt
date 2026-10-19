package com.pathways.rules

import com.pathways.model.BeyonderState
import com.pathways.model.Outcome
import com.pathways.model.RollResult
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.ContractOutcome
import com.pathways.rules.content.RitualContract

/** The contract penalty: -Sequence, and each extra 1d6 rolled for a contract held. */
data class ContractPenalty(val sequence: Int, val dice: List<Int>) {
    val total: Int get() = -sequence - dice.sum()
}

/**
 * Ch. 9 VI's Spirit World Creature Contract (v6.9i 4078-4122): a focused
 * Ritualistic Magic roll whose "base penalty equals your Sequence number",
 * less "an extra 1d6" per creature already under contract, for 3 SPI.
 * "Maximum contracts: 4 (8 for Scrolls Professor)": Hermit 6, held from
 * there down because sequences are cumulative.
 */
object SpiritContract {

    private const val SCROLLS_PATHWAY = "Hermit"
    private const val SCROLLS_SEQUENCE = 6

    fun contract(): RitualContract = ContentLoader.ritualContract().single()

    fun spi(): Int = contract().spi

    fun outcomes(): List<ContractOutcome> = contract().outcomes

    fun maxContracts(state: BeyonderState?): Int =
        if (state?.pathway == SCROLLS_PATHWAY && state.sequence <= SCROLLS_SEQUENCE) {
            contract().maxContractsScrollsProfessor
        } else {
            contract().maxContracts
        }

    /**
     * Ritualistic Magic at [skill], the CoR ladder every ritual takes, the
     * [penalty], and ch. 9's SPI burn, +1 a point before the roll.
     */
    fun targetFor(skill: Int, cor: Int, penalty: ContractPenalty, burned: Int): Int {
        require(burned >= 0) { "a burn cannot be negative (was $burned)" }
        return skill + Corruption.ritualPenalty(cor) + penalty.total + burned
    }

    /** Rolls the [held] extra d6 through [source], reported to the roll log, and refuses past [max]. */
    fun penaltyFor(sequence: Int, held: Int, max: Int, source: RandomSource): ContractPenalty {
        require(held in 0 until max) { "$held contracts held; the most is $max, so no other can be made" }
        val dice = List(held) { source.roll(6) }
        if (dice.isNotEmpty()) source.thrown(dice, null)
        return ContractPenalty(sequence, dice)
    }

    /**
     * The demands row [roll] earns. A critical success below the target
     * (3 or 4 against a target under it) is READ as "Success by 0 (exact)":
     * the table prints no row for a success with a negative margin.
     */
    fun outcomeFor(roll: RollResult): ContractOutcome {
        val rows = outcomes()
        return when (roll.outcome) {
            Outcome.CRITICAL_FAILURE -> rows.single { it.critical }
            Outcome.FAILURE -> rows.single { it.failure && !it.critical }
            else -> {
                val margin = maxOf(roll.margin, 0)
                rows.filter { !it.failure }.single { row ->
                    margin >= row.from!! && (row.to == null || margin <= row.to)
                }
            }
        }
    }
}
