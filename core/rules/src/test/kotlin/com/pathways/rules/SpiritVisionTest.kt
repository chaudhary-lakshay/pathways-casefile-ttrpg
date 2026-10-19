package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ch. 4 and ch. 5's "Spirit Vision -- A Complete Guide": "Cost: 1 SPI per
 * activation, regardless of duration", held through the 10-point advantage
 * (or learned in play and "recorded on the character sheet exactly as if it
 * had been bought"). No roll: v6.9i prints none.
 */
class SpiritVisionTest {

    @Test
    fun `activation costs one SPI`() {
        assertEquals(1, SpiritVision.activation())
    }

    /** "Pathway and Sequence do not grant access to it." */
    @Test
    fun `held through the advantage and nothing else`() {
        assertTrue(SpiritVision.isHeldBy(listOf(Trait(SpiritVision.TRAIT, 10, TraitKind.ADVANTAGE))))
        assertFalse(SpiritVision.isHeldBy(emptyList()))
        assertTrue(ContentLoader.advantages().any { it.name == SpiritVision.TRAIT })
        // A production reader by name, so the Traits tab must not say UNREAD.
        assertTrue(SpiritVision.TRAIT in TraitEffects.known())
    }

    @Test
    fun `both colour tables load and are kept apart`() {
        assertEquals(8, SpiritVision.astralColors().size)
        assertEquals(8, SpiritVision.etherBody().size)
        assertEquals("Spirituality, madness", SpiritVision.astralColors().single { it.color == "Purple" }.meaning)
        assertEquals("Purple", SpiritVision.etherBody().single { it.region == "Brain" }.color)
    }

    @Test
    fun `the pathway refinements are printed as reference`() {
        assertEquals(
            listOf("Mystery Pryer (Hermit)", "Sleepless (Darkness)", "Corpse Collector (Death)"),
            SpiritVision.pathwayDifferences().map { it.name },
        )
    }
}
