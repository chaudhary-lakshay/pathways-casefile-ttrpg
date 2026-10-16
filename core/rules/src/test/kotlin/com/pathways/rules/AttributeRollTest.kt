package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.model.TraitKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The bare Will and Per rolls the Vitals rows open (play session 4). */
class AttributeRollTest {

    private fun held(vararg names: String) = names.map { Trait(it, 0, TraitKind.ADVANTAGE) }

    /**
     * Every term that reaches a bare roll elsewhere reaches this one: shock
     * and the trackers are "-N to all rolls", the declared states and the
     * GM's figure are entered, and a declared clause is the trait's own.
     */
    @Test
    fun `a Will roll takes every all-rolls term and its declared clauses`() {
        val target = AttributeRoll.targetFor(
            AttributeRoll.Kind.WILL, level = 12, traits = emptyList(),
            shock = -4, hurt = -1, others = -2 + 3,
        )
        assertEquals(8, target)
    }

    /** Alertness is "+1 per level to all Per rolls", and this is one; Will takes none. */
    @Test
    fun `Alertness lifts a Per roll and not a Will roll`() {
        val alert = held("Alertness 2")
        assertEquals(12, AttributeRoll.targetFor(AttributeRoll.Kind.PER, 10, alert))
        assertEquals(10, AttributeRoll.targetFor(AttributeRoll.Kind.WILL, 10, alert))
        assertTrue(AttributeRoll.partsFor(AttributeRoll.Kind.PER, 10, alert).any { "Alertness +2" in it })
    }

    /**
     * Will offers the Will-roll clauses (Guilt's -1 in an echo of the act);
     * Per offers the sense rows and Light Sleeper's "Perception rolls while
     * sleeping at +4", whose plainest roll, the one to wake, this now is.
     */
    @Test
    fun `each roll offers its own declared clauses`() {
        val traits = held("Guilt", "Light Sleeper", "Acute Vision 2")
        val will = AttributeRoll.clauses(AttributeRoll.Kind.WILL, traits).map { it.trait }
        val per = AttributeRoll.clauses(AttributeRoll.Kind.PER, traits).map { it.trait }
        assertEquals(listOf("Guilt"), will)
        assertTrue("Light Sleeper" in per && "Acute Vision" in per, "$per")
        assertTrue("Guilt" !in per)
    }

    @Test
    fun `the parts name what moved the target`() {
        val parts = AttributeRoll.partsFor(AttributeRoll.Kind.WILL, 11, emptyList(), shock = -4, hurt = -1)
        assertEquals(listOf("Will 11", "shock -4", "hurt or tired -1"), parts)
    }
}
