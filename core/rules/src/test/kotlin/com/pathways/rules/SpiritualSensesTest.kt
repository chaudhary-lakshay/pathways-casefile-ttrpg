package com.pathways.rules

import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.Outcome
import com.pathways.model.SkillKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Ch. 4's two spiritual senses (p. 33-34) -- the tables their skills roll on. */
class SpiritualSensesTest {

    private val intuition = SpiritualSenses.named("Spiritual Intuition")
    private val perception = SpiritualSenses.named("Spiritual Perception")

    @Test
    fun `the book prints two, in order, and they differ in difficulty`() {
        assertEquals(
            listOf("Spiritual Intuition", "Spiritual Perception"),
            SpiritualSenses.all().map { it.skill },
        )
        assertEquals(listOf("SPI", "SPI"), SpiritualSenses.all().map { it.attr })
        assertEquals("H", intuition.difficulty)
        assertEquals("A", perception.difficulty)
    }

    /**
     * The join to a sheet. Every other skill a character owns prints no table
     * of its own and reads [DegreeOfSuccess] instead.
     */
    @Test
    fun `only these two skills have a table`() {
        assertNotNull(SpiritualSenses.forSkill(key("Spiritual Intuition")))
        assertNotNull(SpiritualSenses.forSkill(key("Spiritual Perception")))
        assertNull(SpiritualSenses.forSkill(key("Occultism")))
        assertThrows<IllegalArgumentException> { SpiritualSenses.named("Spirit Vision") }
    }

    // ---- outcome first, then the last matching margin --------------------

    /**
     * **A critical is read by its outcome, never its margin.** A rolled 3
     * against a target of 5 is a critical success by the always-rule and has
     * a margin of only 2, so a margin-first read reports the book's best row
     * as an ordinary success -- the slip `FrightCheck`, `RitualResolution`
     * and `Wounds` each made once.
     */
    @Test
    fun `a critical success takes the critical row whatever its margin`() {
        val row = SpiritualSenses.rowFor(intuition, Outcome.CRITICAL_SUCCESS, margin = 2)
        assertEquals("Critical Success", row?.result)
        assertEquals("Brief mental vision of the exact danger", row?.effect)
    }

    /**
     * **The success bands overlap, so the LAST match wins.** A margin of 6
     * satisfies "Success", "Success by 3+" and "Success by 5+" at once, and
     * the book prints them least- to most-demanding. Taking the first match
     * would report every success as vague and leave the two deeper rows
     * loaded, tested and unreachable.
     */
    @Test
    fun `the deepest band a margin reaches is the answer`() {
        assertEquals("Success", SpiritualSenses.rowFor(intuition, Outcome.SUCCESS, 0)?.result)
        assertEquals("Success", SpiritualSenses.rowFor(intuition, Outcome.SUCCESS, 2)?.result)
        assertEquals(
            "Success by 3+",
            SpiritualSenses.rowFor(intuition, Outcome.SUCCESS, 3)?.result,
        )
        assertEquals(
            "Success by 3+",
            SpiritualSenses.rowFor(intuition, Outcome.SUCCESS, 4)?.result,
        )
        assertEquals(
            "Success by 5+",
            SpiritualSenses.rowFor(intuition, Outcome.SUCCESS, 5)?.result,
        )
        assertEquals(
            "Success by 5+",
            SpiritualSenses.rowFor(intuition, Outcome.SUCCESS, 6)?.result,
        )
    }

    /**
     * **Neither table prints a failure row**, so a failed roll gets nothing
     * rather than an invented "you sense no danger". The distinction
     * `RitualCritical.corGained` draws between printing nothing and printing
     * zero, one table over.
     */
    @Test
    fun `a failure lands on no row at all`() {
        SpiritualSenses.all().forEach { sense ->
            assertTrue(sense.rows.none { it.result.contains("Failure") }, sense.skill)
            assertNull(SpiritualSenses.rowFor(sense, Outcome.FAILURE, margin = -1))
            assertNull(SpiritualSenses.rowFor(sense, Outcome.CRITICAL_FAILURE, margin = -8))
            assertNull(SpiritualSenses.rowFor(sense, outcome = null, margin = 0))
        }
    }

    /**
     * Both tables print the SAME four band edges and DIFFERENT effects. That
     * is the whole reason this is not `DegreeOfSuccess`: the edges are
     * Appendix A's Good (-3) and Excellent (-5) exactly, and reusing the
     * general ladder would silently merge two tables the book prints apart.
     */
    @Test
    fun `the bands match Appendix A's edges and the effects do not`() {
        assertEquals(3, DegreeOfSuccess.marginFor(SuccessDegree.GOOD))
        assertEquals(5, DegreeOfSuccess.marginFor(SuccessDegree.EXCELLENT))
        assertEquals(listOf(null, 3, 5, null), intuition.rows.map { it.margin })
        assertEquals(intuition.rows.map { it.margin }, perception.rows.map { it.margin })
        val theirs = perception.rows.map { it.effect }
        assertTrue(
            intuition.rows.map { it.effect }.none { it in theirs },
            "the two tables now share an effect; they are printed as two for a reason",
        )
    }

    // ---- the circumstances ------------------------------------------------

    @Test
    fun `each sense prints its own circumstances`() {
        assertEquals(
            listOf("meditative state", "distracted/in combat", "magically concealed"),
            intuition.modifiers.map { it.situation },
        )
        assertEquals(
            listOf("graveyard/ritual site", "industrial/polluted area", "entity hiding", "Seer"),
            perception.modifiers.map { it.situation },
        )
        assertEquals(
            6,
            perception.modifiers.single { it.situation == SpiritualSenses.SEER }.modifier,
        )
    }

    @Test
    fun `the chosen circumstances reach a roll as modifiers`() {
        val chosen = intuition.modifiers.filter { it.modifier != 0 }
        assertEquals(-4, SpiritualSenses.modifierTotal(chosen))
        assertEquals(3, SpiritualSenses.modifiers(intuition, chosen).size)
    }

    /** A sense has one meditative state; naming it twice is not two of them. */
    @Test
    fun `naming a circumstance twice pays once`() {
        val twice = listOf(intuition.modifiers[0], intuition.modifiers[0])
        assertEquals(2, SpiritualSenses.modifierTotal(twice))
        assertEquals(1, SpiritualSenses.modifiers(intuition, twice).size)
    }

    @Test
    fun `a circumstance the chapter never printed is refused`() {
        val invented = com.pathways.rules.content.SenseModifier("holding a lucky coin", 2)
        assertThrows<IllegalArgumentException> {
            SpiritualSenses.modifiers(intuition, listOf(invented))
        }
    }

    /**
     * **The other sense's rows are refused too.** A graveyard helps you notice
     * a spirit, not foresee an ambush, and "+6 Seer" is Perception's alone --
     * so validating against every printed row rather than against THIS
     * sense's would let the strongest bonus in the chapter land on the wrong
     * skill. Nothing can reach it from the dialog, which offers one sense's
     * own rows; the permissive path is removed so the next caller cannot find
     * it, the call `Wounds.multiplierFor` already had to make (6l).
     */
    @Test
    fun `one sense's circumstances are refused on the other's roll`() {
        val seer = perception.modifiers.single { it.situation == SpiritualSenses.SEER }
        val thrown = assertThrows<IllegalArgumentException> {
            SpiritualSenses.modifiers(intuition, listOf(seer))
        }
        assertTrue(thrown.message!!.contains("Spiritual Intuition"), thrown.message)
        // ...and it is genuinely a printed row, just not this skill's.
        assertEquals(1, SpiritualSenses.modifiers(perception, listOf(seer)).size)
    }

    // ---- the overlap with Appendix A -------------------------------------

    /**
     * **Specific beats general.** Chapter 4 prices "distracted/in combat" at
     * -2; Appendix A prices "Distracted or hurried" and "Very adverse
     * conditions (combat, extreme weather)" separately. Taking both charges
     * one distraction twice -- the scoping that keeps chapter 5's darkness off
     * the general adverse row (6ae). Refused rather than silently dropped, so
     * a caller cannot believe the general row was counted.
     */
    @Test
    fun `chapter 4's own price supersedes Appendix A's`() {
        val distracted = intuition.modifiers.single { it.situation == "distracted/in combat" }
        val displaced = SpiritualSenses.displacedCommonModifiers(listOf(distracted))
        assertTrue(displaced.any { it.contains("Distracted", ignoreCase = true) }, "$displaced")
        assertTrue(displaced.any { it.contains("combat", ignoreCase = true) }, "$displaced")

        val general = CommonModifiers.all().first { it.situation in displaced }
        val thrown = assertThrows<IllegalArgumentException> {
            SpiritualSenses.modifiers(intuition, listOf(distracted), listOf(general))
        }
        assertTrue(thrown.message!!.contains("never both"), thrown.message)
    }

    /** Nothing else displaces anything: the guard is one row's, not the table's. */
    @Test
    fun `the other circumstances leave Appendix A alone`() {
        val others = intuition.modifiers.filterNot { it.situation == "distracted/in combat" }
        assertEquals(
            emptySet<String>(),
            SpiritualSenses.displacedCommonModifiers(others + perception.modifiers),
        )
        val general = CommonModifiers.offered().first()
        assertEquals(
            others.size,
            SpiritualSenses.modifiers(intuition, others, listOf(general)).size,
        )
    }

    private fun key(name: String) =
        SkillKey(name, null, ControllingAttribute.Spi, Difficulty.HARD)
}
