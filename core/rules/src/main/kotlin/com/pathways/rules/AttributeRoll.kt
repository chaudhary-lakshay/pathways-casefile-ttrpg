package com.pathways.rules

import com.pathways.model.Trait

/**
 * A bare Will or Per roll, opened from its row on the Vitals tab.
 *
 * Play session 4 found the book asking for these with nowhere to roll them:
 * a Sealed Artifact's daily Will roll, Light Sleeper's Per roll to wake. The
 * terms are the ones every other bare roll already takes -- shock and the
 * trackers are "-N to all rolls" -- plus Alertness on Per, "+1 per level to
 * all Per rolls", as Detection and Spirit Vision take it (6fe). What the GM
 * declares (a clause, a state, a figure) comes in as [others], named by the
 * screen that took it.
 *
 * ST, DX, IQ and HT are not offered: they have no row on the Vitals tab,
 * which is where the owner placed this (2026-10-16).
 */
object AttributeRoll {

    /**
     * [INTUITION] and [PERCEPTION] are ch. 4's universal abilities, rolled
     * against current SPI ([SpiritualSenses]); [code] labels the base.
     */
    enum class Kind(val label: String, val code: String) {
        WILL("Will", "Will"),
        PER("Per", "Per"),
        INTUITION("Spiritual Intuition", "SPI"),
        PERCEPTION("Spiritual Perception", "SPI"),
        ;

        val isSense: Boolean get() = code == "SPI"
    }

    /** The clauses a GM may declare on this roll: never summed unasked. */
    fun clauses(kind: Kind, traits: List<Trait>): List<TraitClause> = when (kind) {
        Kind.WILL -> TraitClauses.willClauses(traits)
        Kind.PER -> TraitClauses.senseClauses(traits) + TraitClauses.perRollClauses(traits)
        Kind.INTUITION, Kind.PERCEPTION -> emptyList()
    }

    /**
     * [spi] reaches a sense alone: Spiritual Attrition is "-3 to all
     * SPI-based skill rolls and Beyonder ability checks", and a sense is a
     * Beyonder ability.
     */
    fun targetFor(
        kind: Kind,
        level: Int,
        traits: List<Trait>,
        shock: Int = 0,
        hurt: Int = 0,
        others: Int = 0,
        spi: PoolStatus? = null,
    ): Int = level + alertness(kind, traits) + shock + hurt + others + attrition(kind, spi)

    /** What moved the target before the GM's terms, one part per source. */
    fun partsFor(
        kind: Kind,
        level: Int,
        traits: List<Trait>,
        shock: Int = 0,
        hurt: Int = 0,
        spi: PoolStatus? = null,
    ): List<String> =
        buildList {
            add("${kind.code} $level")
            alertness(kind, traits).takeIf { it != 0 }?.let { add("Alertness +$it") }
            if (shock != 0) add("shock $shock")
            if (hurt != 0) add("hurt or tired $hurt")
            attrition(kind, spi).takeIf { it != 0 }?.let { add("${spi!!.effect.substringBefore(':')} $it") }
        }

    private fun attrition(kind: Kind, spi: PoolStatus?): Int =
        if (kind.isSense) spi?.takeIf { it.scope == PenaltyScope.SPI_ROLLS }?.penalty ?: 0 else 0

    private fun alertness(kind: Kind, traits: List<Trait>): Int =
        if (kind == Kind.PER) TraitAttributeRolls.alertnessBonus(traits) else 0
}
