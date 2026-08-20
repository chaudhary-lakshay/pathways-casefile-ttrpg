package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The two HT rolls chapter 5's HP table prints, and who they reach.
 *
 * `Pools.hp` has computed `rollsToStayConscious` and `rollsToSurvive` since
 * M0 and nothing read either: the sheet printed the sentence and no screen
 * could roll it. What is under test is the scope, because the three HT-roll
 * traits answer differently and a test asserting one total would not notice.
 */
class VitalRollsTest {

    private fun trait(name: String) = Trait(name, 0, TraitKind.ADVANTAGE)

    private val fit = listOf(trait("Fit"))
    private val veryFit = listOf(trait("Very Fit"))
    private val unfit = listOf(trait("Unfit"))
    private val hardToKill = listOf(trait("Hard to Kill 2"))
    private val painThreshold = listOf(trait("High Pain Threshold"))

    @Test
    fun `the table asks for a roll only where it prints one`() {
        assertEquals(emptyList<VitalCheck>(), VitalRolls.due(Pools.hp(10, 10)))
        // Reeling prints a penalty and no roll.
        assertEquals(emptyList<VitalCheck>(), VitalRolls.due(Pools.hp(3, 10)))
        assertEquals(listOf(VitalCheck.CONSCIOUSNESS), VitalRolls.due(Pools.hp(0, 10)))
    }

    @Test
    fun `below minus HP the book asks for both, and Pools already said so`() {
        // "Roll vs. HT or die; must roll vs. HT each turn while below this
        // threshold" does not withdraw "roll each turn to remain conscious"
        // one row above it, and Pools sets both flags. Reporting one would be
        // a reading this slice does not have to make.
        val dying = Pools.hp(-10, 10)
        assertTrue(dying.rollsToStayConscious && dying.rollsToSurvive)
        assertEquals(listOf(VitalCheck.CONSCIOUSNESS, VitalCheck.SURVIVAL), VitalRolls.due(dying))
    }

    @Test
    fun `at minus five times HP there is no roll to offer`() {
        // "Dead - no roll required". A screen offering one here would invent
        // a chance the book withholds.
        assertEquals(emptyList<VitalCheck>(), VitalRolls.due(Pools.hp(-50, 10)))
    }

    @Test
    fun `Hard to Kill reaches the death roll and not the consciousness roll`() {
        // "+1/level to HT rolls to avoid death". Dying starts at -HP; the row
        // above it is where consciousness is lost, which is not death.
        assertEquals(2, VitalRolls.bonusFor(VitalCheck.SURVIVAL, hardToKill))
        assertEquals(0, VitalRolls.bonusFor(VitalCheck.CONSCIOUSNESS, hardToKill))
    }

    @Test
    fun `an all-HT-rolls trait reaches both, signed`() {
        for (check in VitalCheck.entries) {
            assertEquals(1, VitalRolls.bonusFor(check, fit), "Fit on $check")
            assertEquals(2, VitalRolls.bonusFor(check, veryFit), "Very Fit on $check")
            assertEquals(-1, VitalRolls.bonusFor(check, unfit), "Unfit on $check")
            assertEquals(0, VitalRolls.bonusFor(check, painThreshold), "High Pain Threshold on $check")
        }
    }

    @Test
    fun `the two rules compose on the roll they share`() {
        // A Fit character with Hard to Kill 2 rolls to survive at +3 and to
        // stay conscious at +1. One total would hide which rule did what.
        val both = fit + hardToKill
        assertEquals(3, VitalRolls.bonusFor(VitalCheck.SURVIVAL, both))
        assertEquals(1, VitalRolls.bonusFor(VitalCheck.CONSCIOUSNESS, both))
    }

    @Test
    fun `the target takes what the trackers cost, like every other bare roll`() {
        // A character at 0 HP is reeling by definition, and nothing here
        // creates that state mid-roll -- the argument that keeps shock off
        // RollKind.MAJOR_WOUND_HT does not exist for these two.
        val hp = Pools.hp(0, 10)
        val penalty = Conditions.penaltyFor(RollKind.ATTRIBUTE, hp, Pools.fp(12, 12))
        assertEquals(-1, penalty)
        // Three targets, because +1 and -1 cancel: an assertion using both at
        // once passes just as happily when NEITHER is applied, which is the
        // vacuity this repo has now shipped five times.
        val bare = VitalRolls.targetFor(VitalCheck.CONSCIOUSNESS, 12, emptyList(), penalty)
        val fitted = VitalRolls.targetFor(VitalCheck.CONSCIOUSNESS, 12, fit, penalty)
        val rested = VitalRolls.targetFor(VitalCheck.CONSCIOUSNESS, 12, fit, poolPenalty = 0)
        assertEquals(11, bare, "HT 12 reeling")
        assertEquals(12, fitted, "HT 12 reeling, Fit")
        assertEquals(13, rested, "HT 12 unhurt, Fit")
    }

    @Test
    fun `each check quotes the row it comes from`() {
        assertTrue(VitalRolls.printed(VitalCheck.CONSCIOUSNESS).contains("fall unconscious"))
        assertTrue(VitalRolls.printed(VitalCheck.SURVIVAL).contains("die"))
        // The repetition is the book's and the GM's: this app holds no clock,
        // so a screen offering one roll must say the book asks for one a turn.
        assertTrue(VitalRolls.printed(VitalCheck.CONSCIOUSNESS).contains("each turn"))
        assertTrue(VitalRolls.printed(VitalCheck.SURVIVAL).contains("each turn"))
    }

    /**
     * The map's contract as a TEST rather than a sentence (6bu).
     *
     * `HT_ROLL_TRAITS`' KDoc claims it holds "every trait whose cell says 'to
     * all HT rolls'", and `htRollNames()` cannot check that: it returns the
     * map's own keys, so it asserts the data rather than the failure. Reading
     * the catalogue instead is what finds a row nobody added.
     */
    @Test
    fun `every catalogue row saying all HT rolls is in the map`() {
        val printed = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .filter { it.notes.contains("to all HT rolls", ignoreCase = true) }
            .map { it.name }
            .toSet()
        assertTrue(printed.isNotEmpty(), "the phrase should occur at all")
        assertEquals(printed, TraitEffects.htRollNames())
    }

    @Test
    fun `the refusal that said there is no death roll is gone`() {
        // The reason expires in the commit that ships the roll (6ba, 6bl).
        assertFalse("Hard to Kill" in TraitEffects.unappliedNames())
        val stale = TraitEffects.unappliedNames().filter { name ->
            TraitEffects.reported(listOf(trait(name))).any {
                it.reason.contains("models no death roll")
            }
        }
        assertEquals(emptyList<String>(), stale, "a refusal citing a roll this app now makes")
    }
}
