package com.pathways.rules

import com.pathways.model.BeyonderState
import com.pathways.model.BoonState
import com.pathways.model.Character
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.SequenceBlock
import com.pathways.rules.content.StatGain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What a granted boon gives (Ch. 13-15). Until this slice a successful prayer
 * printed "granted" and stored nothing, so none of this could be asked.
 */
class BoonGrantsTest {

    private val potions = ContentLoader.pathways()

    private fun mortal(boon: BoonState?) = Character(name = "supplicant", boon = boon)

    private fun level(levels: Map<com.pathways.model.SkillKey, Int>, name: String) =
        levels.entries.single { it.key.name == name }.value

    @Test
    fun `a mortal granted a boon plays its gains and pays nothing for them`() {
        // Ch. 13: a boon "functions similarly to a Beyonder potion"; ch. 15:
        // "The same rules for potion-granted skills and attributes ... apply".
        val c = mortal(BoonState("Chaos Mist", 9))
        val walked = PotionDigestion.of(c, potions)!!
        assertEquals(10, walked.effectiveAttributes.iq)
        assertEquals(5, walked.effectiveSpi)
        // "your IQ + 4", on the IQ the boon itself raised.
        assertEquals(14, level(walked.skillLevels, "Diplomacy"))
        assertEquals(Derive.of(c).budget, walked.budget)
    }

    @Test
    fun `a boon's sequences are cumulative, as a potion's are`() {
        val walked = PotionDigestion.of(mortal(BoonState("Chaos Mist", 8)), potions)!!
        assertEquals(11, walked.effectiveSpi)
        // p. 86: the Sequence 8 +3 stacks on the level Sequence 9 granted.
        assertEquals(17, level(walked.skillLevels, "Diplomacy"))
    }

    @Test
    fun `a rung printed with nothing under it grants what is printed`() {
        // The owner's ruling, 2026-10-13: three pathways print a Sequence 8
        // and no Sequence 9, and the 8 is grantable alone.
        val walked = PotionDigestion.of(mortal(BoonState("Second Law", 8)), potions)!!
        assertEquals(1, walked.effectiveSpi)
        assertEquals(13, level(walked.skillLevels, "Law"))
    }

    @Test
    fun `the two spellings of Eternal Aeon are one pathway`() {
        // The owner's ruling, the same day: ch. 14's "(Inevitability)" and
        // ch. 15's bare name are walked as one ladder.
        assertFalse("Eternal Aeon" in NonStandardPathways.patrons())
        assertTrue("Eternal Aeon (Inevitability)" in NonStandardPathways.patrons())
        assertEquals(NonStandardPathways.pathways().size - 1, NonStandardPathways.patrons().size)
        val chain = NonStandardPathways.chainFor(BoonState("Eternal Aeon (Inevitability)", 8))
        assertEquals(listOf("Dancer", "Alms Monk"), chain.map { it.name })
        val walked = PotionDigestion.of(mortal(BoonState("Eternal Aeon (Inevitability)", 8)), potions)!!
        assertEquals(9, walked.effectiveSpi)
        assertEquals(11, walked.effectiveAttributes.dx)
    }

    @Test
    fun `a boon sits on top of a potion walk`() {
        // The owner's ruling: anyone may hold one, alongside potions.
        val both = Character(name = "both", beyonder = BeyonderState("Fool", 9), boon = BoonState("Chaos Mist", 9))
        val potionOnly = PotionDigestion.of(both.copy(boon = null), potions)!!
        val walked = PotionDigestion.of(both, potions)!!
        assertEquals(potionOnly.effectiveSpi + 5, walked.effectiveSpi)
        assertEquals(potionOnly.effectiveAttributes.iq + 1, walked.effectiveAttributes.iq)
        // The browsed-potion path the pathway screen takes carries it too.
        assertEquals(walked, PotionDigestion.digestThrough(both, "Fool", 9, potions))
    }

    @Test
    fun `potions are digested before the boon - a reading, pinned`() {
        // A granted level is frozen at the attributes of its moment, and the
        // save stores no order. Potions first: the Dancer's "DX + 3" reads
        // the DX the potion already raised. Boon first would give 14.
        val potion = listOf(SequenceBlock(9, "Up", "Synthetic", stats = listOf(StatGain("DX", 2.0))))
        val c = Character(
            name = "ordered",
            beyonder = BeyonderState("Synthetic", 9),
            boon = BoonState("Eternal Aeon (Inevitability)", 9),
        )
        val walked = PotionDigestion.of(c, potion)!!
        assertEquals(13, walked.effectiveAttributes.dx)
        assertEquals(16, level(walked.skillLevels, "Acrobatics"))
    }

    @Test
    fun `a pathway the book no longer prints grants nothing and throws nothing`() {
        // A save is convenience: a stale name must not kill the sheet.
        assertEquals(emptyList<SequenceBlock>(), NonStandardPathways.chainFor(BoonState("Unprinted", 9)))
        assertNull(PotionDigestion.of(mortal(BoonState("Unprinted", 9)), potions))
    }

    @Test
    fun `the next rung is the next one printed, on the patron already served`() {
        fun next(held: BoonState?, patron: String) = NonStandardPathways.nextRung(held, patron)
        assertEquals(BoonState("Everlasting", 9), next(null, "Everlasting"))
        // No Sequence 9 is printed for Second Law, so its first rung is 8.
        assertEquals(BoonState("Second Law", 8), next(null, "Second Law"))
        assertEquals(BoonState("Everlasting", 8), next(BoonState("Everlasting", 9), "Everlasting"))
        // Nothing further is recorded, and a second patron is never offered:
        // each Outer Deity "is the sole source of all boons on that path".
        assertNull(next(BoonState("Everlasting", 8), "Everlasting"))
        assertNull(next(BoonState("Everlasting", 9), "Chaos Mist"))
        // Across the two spellings.
        assertEquals(
            BoonState("Eternal Aeon (Inevitability)", 8),
            next(BoonState("Eternal Aeon (Inevitability)", 9), "Eternal Aeon (Inevitability)"),
        )
    }

    @Test
    fun `no boon lowers a score, so a grant needs no floor check`() {
        // BeyonderSession.grantBoon skips the floors on this. A negative
        // delta arriving in the content reddens here first.
        assertTrue(NonStandardPathways.all().all { b -> b.stats.all { it.delta > 0 } })
    }
}
