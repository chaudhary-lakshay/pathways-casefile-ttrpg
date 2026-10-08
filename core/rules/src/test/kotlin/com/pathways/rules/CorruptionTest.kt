package com.pathways.rules

import com.pathways.model.Attributes
import com.pathways.model.Character
import com.pathways.model.SecondaryDelta
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CorruptionTest {

    private fun mortal(iq: Int = 10, traits: List<Trait> = emptyList()) = Character(
        name = "test",
        attributes = Attributes(iq = iq),
        traits = traits,
    )

    @Test
    fun `maximum corruption is the character's Will`() {
        // p. 45: "Maximum CoR equals the character's Will score."
        assertEquals(10, Corruption.maxFor(mortal(iq = 10)))
        assertEquals(13, Corruption.maxFor(mortal(iq = 13)))
    }

    @Test
    fun `maximum corruption follows bought Will, not IQ`() {
        val willed = mortal().copy(secondaryDelta = SecondaryDelta(will = 4))
        assertEquals(14, Corruption.maxFor(willed))
    }

    @Test
    fun `Spiritual Fortitude raises the maximum by three`() {
        // Ch. 3 advantage, p. 18: "Maximum CoR equal Will + 3 rather than just Will."
        val warded = mortal(traits = listOf(Trait("Spiritual Fortitude", 5, TraitKind.ADVANTAGE)))
        assertEquals(13, Corruption.maxFor(warded))
    }

    @Test
    fun `a potion that raises Will raises the ceiling with it`() {
        // Sequence 9 Arbiter (Justiciar) grants Will +1; capping a Beyonder
        // at their mortal Will would lose them the headroom the potion paid
        // for. The Abyss Criminal's Will -1 is the same rule downward.
        val blocks = ContentLoader.pathways()
        val mortalMax = Corruption.maxFor(mortal())
        fun maxAfter(pathway: String): Int {
            val digested = PotionDigestion.digestThrough(
                mortal(), pathway, PotionDigestion.STARTING_SEQUENCE, blocks,
            )
            return Corruption.maxFor(digested.secondaries.will, mortal().traits)
        }
        assertEquals(mortalMax + 1, maxAfter("Justiciar"))
        assertEquals(mortalMax - 1, maxAfter("Abyss"))
    }

    @Test
    fun `a character at maximum corruption is lost`() {
        // p. 45: "At maximum CoR: The character is permanently lost."
        assertFalse(Corruption.isLostAt(9, max = 10))
        assertTrue(Corruption.isLostAt(10, max = 10))
        assertTrue(Corruption.isLostAt(11, max = 10))
    }

    @Test
    fun `a gain that reaches the maximum ends the character`() {
        assertTrue(Corruption.isLostAfter(cor = 0, delta = 10, max = 10))
        assertFalse(Corruption.isLostAfter(cor = 0, delta = 9, max = 10))
        assertTrue(Corruption.isLostAfter(cor = 5, delta = 50, max = 10))
        // No gain at all, under a ceiling a potion has just lowered onto the
        // total: the Will-lowering advancement, which costs nothing and ends
        // the character anyway.
        assertTrue(Corruption.isLostAfter(cor = 9, delta = 0, max = 9))
        assertFalse(Corruption.isLostAfter(cor = 8, delta = 0, max = 9))
    }

    @Test
    fun `corruption is never negative and never counted past the maximum`() {
        assertEquals(0, Corruption.clamp(-3, max = 10))
        assertEquals(10, Corruption.clamp(14, max = 10))
        assertEquals(7, Corruption.clamp(7, max = 10))
    }

    @Test
    fun `ritual thresholds are only those reached`() {
        assertEquals(emptyList<Int>(), Corruption.ritualThresholdsReached(2).map { it.cor })
        assertEquals(listOf(3), Corruption.ritualThresholdsReached(3).map { it.cor })
        assertEquals(listOf(3, 5), Corruption.ritualThresholdsReached(7).map { it.cor })
        assertEquals(listOf(3, 5, 8, 10), Corruption.ritualThresholdsReached(99).map { it.cor })
    }

    @Test
    fun `spiritual instability penalises ritual rolls only`() {
        // p. 68 files the 3/5/8/10 ladder under "Corruption from Ritual Work"
        // and calls it "additional ritual-specific thresholds". The design
        // spec's section 5 paraphrases it as a general CoR ladder; the book
        // wins (CLAUDE.md 4.1), so the penalty is named for rituals and no
        // general "penaltyFor(cor)" exists to be misapplied to a combat roll.
        assertEquals(0, Corruption.ritualPenalty(2))
        assertEquals(-1, Corruption.ritualPenalty(3))
        assertEquals(-1, Corruption.ritualPenalty(10))
    }

    @Test
    fun `the ritual penalty row still says what the constant claims`() {
        // Guard against silent repair: -1 is a constant here, read off the
        // 3 CoR row's prose. If the content stops saying so, the data changed
        // and that is the finding.
        val row = Corruption.ritualThresholdsReached(3).single()
        assertTrue("-1" in row.effect, "the 3 CoR row no longer states a -1: ${row.effect}")
    }

    @Test
    fun `the advancement floor in the content matches what the PCR charges`() {
        // `PotionConsumption.attempt` no longer states its own 10: it reads
        // this row through `earlyAdvancementCor`, so what is guarded here is
        // the DATA (4.2). If the row stops printing 10 this fails, the content
        // change is the finding, and the charge has already moved with it.
        val row = Corruption.gained().single { it.action.startsWith("Advancing sequence") }
        assertEquals(10, row.minimum)
        assertEquals(null, row.maximum) // "10+" prints no ceiling
        assertEquals(row.minimum, PotionConsumption.earlyAdvancementCor())
    }

    @Test
    fun `a maximum below one is a bad call, not a lost character`() {
        assertThrows<IllegalArgumentException> { Corruption.clamp(0, max = 0) }
        assertThrows<IllegalArgumentException> { Corruption.isLostAt(0, max = -1) }
    }
}
