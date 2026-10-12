package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.Attributes
import com.pathways.model.Trait

/**
 * A blessing's attributes as PLAYED (6hf): God of Combat's "+3 ST, +3 DX,
 * +3 HT" (book 1109-1110).
 *
 * Never written into `Character.attributes`, which the budget bills: the
 * 75-point trait paid for the +3 (M3's boundary, which only potions crossed
 * before). Written by hand as [TraitGrants] is, and pinned against the
 * extracted cell both ways.
 */
object Blessings {
    private val ATTRIBUTE_GAINS: Map<String, Map<Attr, Int>> = mapOf(
        TraitEffects.GOD_OF_COMBAT to mapOf(Attr.ST to 3, Attr.DX to 3, Attr.HT to 3),
    )

    /** The table, for its census. */
    fun attributeGainTable(): Map<String, Map<Attr, Int>> = ATTRIBUTE_GAINS

    /** What the blessings named in [held] add to [attr]. */
    fun gainTo(attr: Attr, held: Collection<String>): Int =
        held.distinct().sumOf { ATTRIBUTE_GAINS[it]?.get(attr) ?: 0 }

    /**
     * [attributes] as played before any potion: plus every blessing [traits]
     * hold. What `Derive.of` and `PotionDigestion` both start from.
     */
    fun played(attributes: Attributes, traits: List<Trait>): Attributes {
        val held = traits.map { it.name }
        return Attributes(
            st = attributes.st + gainTo(Attr.ST, held),
            dx = attributes.dx + gainTo(Attr.DX, held),
            iq = attributes.iq + gainTo(Attr.IQ, held),
            ht = attributes.ht + gainTo(Attr.HT, held),
        )
    }
}
