package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.Attributes
import com.pathways.model.BoonState
import com.pathways.model.Character
import com.pathways.model.MAX_SECONDARY_DELTA_MAGNITUDE
import com.pathways.model.SecondaryDelta
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.SequenceBlock
import com.pathways.rules.content.StatGain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The highest score the builder may sell, so that every potion still fits on
 * top of it. A potion ADDS to what was bought, and the digested score is held
 * to the same bounds as the bought one -- ST 30 by [Attributes], +-100 steps
 * by [SecondaryDelta], and ST 30 again by the thrust/swing table.
 */
class PurchaseCeilingsTest {

    private val blocks = ContentLoader.pathways()

    /** Every boon a character can hold, and none: a boon sits on a potion walk. */
    private val boons: List<BoonState?> =
        listOf<BoonState?>(null) + NonStandardPathways.all().map { BoonState(NonStandardPathways.patronOf(it.pathway), it.sequence) }

    private fun atCeilings() = Character(
        name = "ceilings",
        attributes = Attributes(
            st = PurchaseCeilings.attribute(Attr.ST, emptyList()),
            dx = PurchaseCeilings.attribute(Attr.DX, emptyList()),
            iq = PurchaseCeilings.attribute(Attr.IQ, emptyList()),
            ht = PurchaseCeilings.attribute(Attr.HT, emptyList()),
        ),
        secondaryDelta = ContentLoader.secondaryCosts().fold(SecondaryDelta()) { d, row ->
            SecondaryCharacteristics.withSteps(d, row.key, PurchaseCeilings.secondarySteps(row.key))
        },
    )

    /**
     * Whether every block digests on [c]. A failure counts only when it is a
     * bound on the digested SUM -- [Attributes] or [SecondaryDelta] -- so a
     * future, unrelated `require` on the way cannot pass for a tight ceiling.
     */
    private fun digestsEverywhere(c: Character): Boolean = blocks.all { b ->
        runCatching { boons.forEach { PotionDigestion.digestThrough(c.copy(boon = it), b.pathway, b.sequence, blocks) } }
            .onFailure { e ->
                val bound = e is IllegalArgumentException &&
                    e.message.orEmpty().let { "must be between" in it || "magnitude too large" in it }
                if (!bound) throw e
            }
            .isSuccess
    }

    @Test
    fun `the ceilings are the bounds less the largest walk`() {
        // Twilight Giant is ST +11 and HT +5 by Sequence 6; Demoness and Moon
        // DX +4, White Tower IQ +4; Moon Basic Speed +1 (four quarters); Will,
        // Per and Basic Move +2 at most. No potion grants HP or FP.
        // And the largest boon walk on top, which is held beside a potion's:
        // Patriarch ST +2, Sublunary Eye DX +3 and Basic Speed +2, Monarch of
        // Decay HT +4, three pathways IQ +1, Eternal Aeon Will +2.
        assertEquals(
            mapOf(Attr.ST to 17, Attr.DX to 23, Attr.IQ to 25, Attr.HT to 21),
            Attr.entries.associateWith { PurchaseCeilings.attribute(it, emptyList()) },
        )
        assertEquals(
            mapOf("hp" to 100, "fp" to 100, "will" to 96, "per" to 98, "basicSpeed" to 88, "basicMove" to 98),
            ContentLoader.secondaryCosts().associate { it.key to PurchaseCeilings.secondarySteps(it.key) }
                .filterKeys { it in setOf("hp", "fp", "will", "per", "basicSpeed", "basicMove") },
        )
    }

    @Test
    fun `every potion digests on a character bought to every ceiling at once`() {
        val c = atCeilings()
        for (b in blocks) for (boon in boons) {
            val digested = PotionDigestion.digestThrough(c.copy(boon = boon), b.pathway, b.sequence, blocks)
            // The second reader of ST: damage rolls off the digested score.
            Damage.thrust(digested.effectiveAttributes.st)
        }
    }

    @Test
    fun `each ceiling is the highest that holds, not merely one that does`() {
        // Checked by digesting, not by the derivation: one step over any
        // ceiling that a potion reaches throws on some block.
        val c = atCeilings()
        for (attr in Attr.entries) {
            val over = c.copy(attributes = c.attributes.raised(attr))
            assertTrue(!digestsEverywhere(over), "$attr one over its ceiling still digests everywhere")
        }
        // Every row some potion raises, read from the ceilings, so a future
        // HP grant is checked here without editing this list.
        val raised = ContentLoader.secondaryCosts().map { it.key }
            .filter { PurchaseCeilings.secondarySteps(it) < MAX_SECONDARY_DELTA_MAGNITUDE }
        assertEquals(setOf("will", "per", "basicSpeed", "basicMove"), raised.toSet())
        for (key in raised) {
            val d = c.secondaryDelta
            val over = c.copy(secondaryDelta = SecondaryCharacteristics.withSteps(d, key, SecondaryCharacteristics.stepsOf(d, key) + 1))
            assertTrue(!digestsEverywhere(over), "$key one over its ceiling still digests everywhere")
        }
    }

    @Test
    fun `no premade prints an attribute above its ceiling`() {
        // An attribute left as printed is never held to a ceiling, which is
        // safe only while every sheet sits under it (the highest is 11).
        for (sheet in Premades.all()) {
            val a = sheet.toCharacter().attributes
            for (attr in Attr.entries) {
                assertTrue(a[attr] <= PurchaseCeilings.attribute(attr, emptyList()), "${sheet.name} prints $attr ${a[attr]}")
            }
        }
    }

    @Test
    fun `a walk is measured at its peak, not where it ends`() {
        // No standard walk lowers a stat it raised, so the catalogue cannot
        // tell these apart: digestion builds the score at EVERY Sequence, and
        // the ST 3 at Sequence 9 must fit even if Sequence 8 takes one back.
        val walk = listOf(
            SequenceBlock(9, "Up", "Synthetic", stats = listOf(StatGain("ST", 3.0))),
            SequenceBlock(8, "Down", "Synthetic", stats = listOf(StatGain("ST", -1.0))),
        )
        assertEquals(3, PotionDigestion.largestGrants(walk).attributes[Attr.ST])
    }

    private fun Attributes.raised(attr: Attr) = when (attr) {
        Attr.ST -> copy(st = st + 1)
        Attr.DX -> copy(dx = dx + 1)
        Attr.IQ -> copy(iq = iq + 1)
        Attr.HT -> copy(ht = ht + 1)
    }
}
