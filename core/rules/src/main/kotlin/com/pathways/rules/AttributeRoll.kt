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

    enum class Kind(val label: String) { WILL("Will"), PER("Per") }

    /** The clauses a GM may declare on this roll: never summed unasked. */
    fun clauses(kind: Kind, traits: List<Trait>): List<TraitClause> = when (kind) {
        Kind.WILL -> TraitClauses.willClauses(traits)
        Kind.PER -> TraitClauses.senseClauses(traits) + TraitClauses.perRollClauses(traits)
    }

    fun targetFor(kind: Kind, level: Int, traits: List<Trait>, shock: Int = 0, hurt: Int = 0, others: Int = 0): Int =
        level + alertness(kind, traits) + shock + hurt + others

    /** What moved the target before the GM's terms, one part per source. */
    fun partsFor(kind: Kind, level: Int, traits: List<Trait>, shock: Int = 0, hurt: Int = 0): List<String> =
        buildList {
            add("${kind.label} $level")
            alertness(kind, traits).takeIf { it != 0 }?.let { add("Alertness +$it") }
            if (shock != 0) add("shock $shock")
            if (hurt != 0) add("hurt or tired $hurt")
        }

    private fun alertness(kind: Kind, traits: List<Trait>): Int =
        if (kind == Kind.PER) TraitAttributeRolls.alertnessBonus(traits) else 0
}
