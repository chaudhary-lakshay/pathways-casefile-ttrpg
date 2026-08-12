package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Ch. 5's three printed refusals of an active defence, and the one trait that cancels one. */
class ActiveDefensesTest {

    private val reflexes = listOf(Trait("Combat Reflexes", 15, TraitKind.ADVANTAGE))
    private val none = emptyList<Trait>()

    /** Nothing declared is the ordinary case: every defence stands. */
    @Test
    fun `an undeclared character defends normally`() {
        val state = ActiveDefenses.state(emptySet(), none)
        assertNull(state.refused)
        assertNull(state.negated)
        assertTrue(state.defends)
    }

    @Test
    fun `each declared cause refuses the defence with the book's own sentence`() {
        for (cause in DefenseRefusal.entries) {
            val state = ActiveDefenses.state(setOf(cause), none)
            assertEquals(cause, state.refused, "$cause should refuse")
            assertTrue(!state.defends, "$cause should withdraw the defences")
            assertTrue(state.printed!!.isNotEmpty(), "$cause prints nothing")
        }
    }

    /**
     * **Combat Reflexes cancels SURPRISE and nothing else.**
     *
     * Ch. 3 prints "never freeze in surprise" and Ch. 5's surprise rule is
     * the freeze it names -- the trait's own prose pairs them ("prevents
     * freezing on surprise", 2379-2380). The unaware shot is a different
     * cause: the bullet arrives before reaction, which is not a freeze, and
     * no printed clause touches it. Reading the trait as general defence
     * insurance would hand a fighter a rule the book never gave them.
     *
     * A reading, so it is pinned rather than left to be rediscovered.
     */
    @Test
    fun `Combat Reflexes cancels surprise alone`() {
        val surprised = ActiveDefenses.state(setOf(DefenseRefusal.SURPRISE), reflexes)
        assertNull(surprised.refused)
        assertEquals(DefenseRefusal.SURPRISE, surprised.negated)
        assertEquals("Combat Reflexes", surprised.negatedBy)
        assertTrue(surprised.defends)

        for (cause in DefenseRefusal.entries - DefenseRefusal.SURPRISE) {
            val state = ActiveDefenses.state(setOf(cause), reflexes)
            assertEquals(cause, state.refused, "Combat Reflexes must not cancel $cause")
            assertNull(state.negated, "$cause is not a freeze")
        }
    }

    /**
     * A cancelled cause does not shield an uncancelled one declared with it.
     *
     * A surprised character shot from concealment still cannot defend, and
     * the reason shown has to be the shot rather than the surprise the trait
     * already handled.
     */
    @Test
    fun `a surviving cause outranks a cancelled one`() {
        val state = ActiveDefenses.state(
            setOf(DefenseRefusal.SURPRISE, DefenseRefusal.UNAWARE_RANGED),
            reflexes,
        )
        assertEquals(DefenseRefusal.UNAWARE_RANGED, state.refused)
        assertEquals(DefenseRefusal.SURPRISE, state.negated)
        assertTrue(!state.defends)
    }

    /**
     * **Four maneuvers forfeit the next turn's defence, and p. 37 names
     * three.** The sentence granting the defence excepts "an All-Out Attack
     * maneuver"; the maneuver table prints "Cannot defend next turn." on the
     * three All-Out Attacks AND on Charge Attack. The table is the specific
     * printing and wins, which is the call the PCR's critical rules and the
     * ritual CoR ladder already needed.
     *
     * Read from committed content, never a second list here: `maneuvers.json`
     * has carried the sentence since the combat slice.
     */
    @Test
    fun `the forfeiting maneuvers come from the book's own table`() {
        assertEquals(
            listOf(
                "All-Out Attack (Determined)",
                "All-Out Attack (Strong)",
                "All-Out Attack (Double)",
                "Charge Attack",
            ),
            ActiveDefenses.forfeitingManeuvers(),
        )
    }

    /** Every cause the enum names is a rule the book prints, and the reverse. */
    @Test
    fun `every cause is backed by an extracted sentence`() {
        val extracted = ContentLoader.defenseRefusals().map { it.cause }.toSet()
        assertEquals(DefenseRefusal.entries.map { it.name }.toSet(), extracted)
    }

    /**
     * A cause with no printed sentence is refused rather than defaulted.
     *
     * Answering with an empty string would let a content drop present as a
     * refusal nobody can read -- the refusal-over-a-plausible-answer call
     * `Detection.attributeFor` already needed (6ah).
     */
    @Test
    fun `a cause the content does not print is refused`() {
        assertThrows<IllegalArgumentException> { ActiveDefenses.printedFor("NOT_A_CAUSE") }
    }
}
