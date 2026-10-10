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
    fun `spent FP reaches an HP roll, because the book scopes it to rolls`() {
        // "-1 to all rolls per 1 FP below 0" (p. 41) names rolls, not a pool.
        // These two are gated on an HP threshold and take it anyway; scoping
        // it to HP would add a qualifier the book does not print.
        val hp = Pools.hp(0, 10)
        val spent = Pools.fp(-8, 12)
        // -9, not -8: the reeling -1 is in force at 0 HP as well, and the two
        // penalties are separate rules that both name "all rolls".
        assertEquals(-9, Conditions.penaltyFor(RollKind.ATTRIBUTE, hp, spent))
        assertEquals(
            4,
            VitalRolls.targetFor(VitalCheck.CONSCIOUSNESS, 12, emptyList(), poolPenalty = -8),
        )
    }

    @Test
    fun `the two offered rolls are told apart by the row that prints them`() {
        // Below -HP both are due and a Hard to Kill carrier sees two different
        // targets; without the row's own name the player cannot tell which
        // number belongs to which rule.
        assertEquals("0 HP", VitalRolls.rowFor(VitalCheck.CONSCIOUSNESS))
        assertEquals("-HP", VitalRolls.rowFor(VitalCheck.SURVIVAL))
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
        assertEquals(printed, TraitAttributeRolls.htRollNames())
    }

    @Test
    fun `the refusal that said there is no death roll is gone`() {
        // The reason expires in the commit that ships the roll (6ba, 6bl).
        assertFalse("Hard to Kill" in TraitEffects.unappliedNames())
        val stale = TraitEffects.unappliedNames().filter { name ->
            TraitReport.reported(listOf(trait(name))).any {
                it.reason.contains("models no death roll")
            }
        }
        assertEquals(emptyList<String>(), stale, "a refusal citing a roll this app now makes")
    }

    /**
     * The fourth trait that names one of these rolls, and the first to name it
     * WITHOUT the words "HT roll".
     *
     * `Hard to Subdue` is printed immediately below `Hard to Kill`, at the same
     * price and the same shape — "+1/level to remain conscious when reduced to
     * 0 or negative HP" — and it quotes the 0 HP row's own words back at it
     * ("roll each turn to remain conscious"). It reached nothing until 6dt,
     * because every census that would have found it filtered on the phrase
     * "HT roll", which this cell never uses.
     *
     * **CONSCIOUSNESS only, at every HP where that check is due.** The clause
     * names "negative HP" and the survival roll happens there, but the survival
     * row's own words are "or die" — two rolls being due at once does not merge
     * their scopes, and widening a clause that names its own condition is the
     * move 6ck refused.
     */
    @Test
    fun `Hard to Subdue lifts the consciousness roll and not the survival roll`() {
        val subdue = listOf(trait("Hard to Subdue 2"))
        assertEquals(2, VitalRolls.bonusFor(VitalCheck.CONSCIOUSNESS, subdue))
        assertEquals(0, VitalRolls.bonusFor(VitalCheck.SURVIVAL, subdue))
        // Its mirror runs the other way, which is what makes the pair a test
        // rather than two assertions about one trait.
        val kill = listOf(trait("Hard to Kill 2"))
        assertEquals(0, VitalRolls.bonusFor(VitalCheck.CONSCIOUSNESS, kill))
        assertEquals(2, VitalRolls.bonusFor(VitalCheck.SURVIVAL, kill))
    }

    @Test
    fun `Hard to Subdue is per level, and the book prints it no cap`() {
        assertEquals(1, VitalRolls.bonusFor(VitalCheck.CONSCIOUSNESS, listOf(trait("Hard to Subdue"))))
        assertEquals(4, VitalRolls.bonusFor(VitalCheck.CONSCIOUSNESS, listOf(trait("Hard to Subdue 4"))))
        // Hard to Kill's cell caps itself at 1-5 levels and this one prints no
        // cap at all, so inventing one would be app-authored (4.1). Asserted
        // ABOVE the sibling's ceiling, because a check that the catalogue text
        // lacks the word "levels" is a claim about the data: a mistaken cap of
        // 5 in production satisfies it and every level below it (Codex).
        assertEquals(6, VitalRolls.bonusFor(VitalCheck.CONSCIOUSNESS, listOf(trait("Hard to Subdue 6"))))
        val row = ContentLoader.advantages().first { it.name == TraitEffects.HARD_TO_SUBDUE }
        assertFalse(row.notes.contains("levels"), row.notes)
    }

    /**
     * The census's own corpus, which could not see two traits it applies.
     *
     * `TraitEffects.known()` says it is "every trait this object knows about,
     * applied or not", and it is what the catalogue guard uses as its handled
     * set. It read a list of collections, so the traits applied by a bespoke
     * single-trait function belonged to none — `Hard to Kill` and `High Pain
     * Threshold` were both applied, both reported nothing, and both absent.
     * Third instance of the failure that function's own KDoc records twice.
     */
    @Test
    fun `every trait a bespoke bonus applies is one the census can see`() {
        val known = TraitEffects.known()
        for (name in listOf(TraitEffects.HARD_TO_KILL, TraitEffects.HARD_TO_SUBDUE,
                            TraitEffects.HIGH_PAIN_THRESHOLD)) {
            assertTrue(name in known, "$name is applied and invisible to the catalogue guard")
        }
    }
}
