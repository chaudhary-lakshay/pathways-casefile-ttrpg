package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.ModEffect
import com.pathways.model.SkillKey
import com.pathways.model.SourceKind
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The Major Wound's success row: "-4 to all rolls for 1 turn from shock and pain". */
class ShockTest {

    private val tough = listOf(Trait(Shock.IGNORED_BY, 10, TraitKind.ADVANTAGE))
    private val none = emptyList<Trait>()
    private val brawling =
        SkillKey("Brawling", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.EASY)

    @Test
    fun `the penalty is the book's own, on the row that prints it`() {
        assertEquals(-4, Shock.PENALTY)
        val printed = Shock.printed()
        assertEquals("-4 to all rolls for 1 turn from shock and pain", printed)
        assertTrue(printed.contains("${Shock.PENALTY} to all rolls"), printed)
    }

    /**
     * **The scope is a decision, not an accident of which screen was wired.**
     *
     * "All rolls" is taken at its word: a skill roll, a bare attribute roll
     * and an active defence all take it. GURPS 4e exempts active defences
     * from shock; this book prints no exception, and GURPS's conventions are
     * GURPS's (6l).
     */
    @Test
    fun `shock reaches every kind of roll this app makes`() {
        for (kind in RollKind.entries - RollKind.MAJOR_WOUND_HT - RollKind.DAILY_RECOVERY) {
            assertTrue(Shock.appliesTo(kind), "$kind should take the penalty")
            assertEquals(-4, Shock.penaltyFor(kind, shocked = true, traits = none), "$kind")
        }
    }

    /**
     * The one refusal, and it is about WHEN rather than what.
     *
     * The Major Wound's HT roll is what produces the shock. Applying it there
     * would let the state penalise the roll that created it -- and would do so
     * invisibly, since the same dialog rolls and displays both.
     */
    @Test
    fun `the roll that causes the shock does not take it`() {
        assertTrue(!Shock.appliesTo(RollKind.MAJOR_WOUND_HT))
        assertEquals(0, Shock.penaltyFor(RollKind.MAJOR_WOUND_HT, shocked = true, traits = none))
        // Even for a character with no trait to save them.
        assertEquals(0, Shock.penaltyFor(RollKind.MAJOR_WOUND_HT, shocked = true, traits = tough))
    }

    @Test
    fun `nothing is charged unless the shock is in force`() {
        for (kind in RollKind.entries) {
            assertEquals(0, Shock.penaltyFor(kind, shocked = false, traits = none), "$kind")
        }
        assertTrue(Shock.modifiers(shocked = false, traits = none).isEmpty())
    }

    /**
     * High Pain Threshold ignores it, and the trait's own words say so.
     *
     * "Ignore shock penalties from injury" -- the first printed clause of a
     * trait `TraitEffects` recorded as unapplied because "this app models no
     * shock penalty to ignore" (6al). It models one now.
     */
    @Test
    fun `High Pain Threshold ignores the penalty on every kind of roll`() {
        assertTrue(Shock.ignoredBy(tough))
        assertTrue(!Shock.ignoredBy(none))
        for (kind in RollKind.entries) {
            assertEquals(0, Shock.penaltyFor(kind, shocked = true, traits = tough), "$kind")
        }
        assertTrue(Shock.modifiers(shocked = true, traits = tough).isEmpty())
        // The clause really is the book's, not ours.
        val cell = ContentLoader.advantages().first { it.name == Shock.IGNORED_BY }.notes
        assertTrue(cell.contains("Ignore shock penalties from injury"), cell)
    }

    /**
     * **Why the trait suppresses rather than cancelling, with the evidence.**
     *
     * A design review asked for [ModEffect.Cancel] from the trait so the
     * cancellation would show in the resolved contributions. `Cancel` is keyed
     * by [SourceKind], and every state penalty in this app shares
     * `SourceKind.STATE`: a budget of 4 aimed at STATE would also offset a
     * dying character's reeling and a spent one's fatigue. High Pain Threshold
     * ignores shock, not fatigue.
     *
     * This test is the disproof, so the decision cannot be reversed by
     * argument alone: it shows a shocked, reeling character carrying two STATE
     * penalties that one Cancel could not tell apart.
     */
    @Test
    fun `a Cancel keyed on the source kind could not tell shock from fatigue`() {
        val reeling = Conditions.forSkill(
            skill = brawling,
            hp = Pools.hp(2, 10),
            fp = Pools.fp(10, 10),
        )
        assertTrue(reeling.isNotEmpty(), "a character at 2 of 10 HP should be reeling")
        val shock = Shock.modifiers(shocked = true, traits = none)
        val kinds = (reeling + shock).map { it.source.kind }.toSet()
        assertEquals(setOf(SourceKind.STATE), kinds)
        // Both are Flat penalties on the same source kind, so the only thing
        // telling them apart is the id -- which Cancel does not read.
        assertTrue((reeling + shock).all { it.effect is ModEffect.Flat })
    }

    /**
     * The modifier is a STATE, like the pool penalties, not a SITUATIONAL.
     *
     * `Situational` is what the GM says about this turn; shock is what the
     * character is carrying because of a wound. They resolve to different
     * source kinds, which is what a future Cancel budget is measured against
     * -- the distinction 6al had to make for `FromTrait` (Codex).
     */
    @Test
    fun `shock is carried, not declared about the scene`() {
        val mod = Shock.modifiers(shocked = true, traits = none).single()
        assertEquals(SourceKind.STATE, mod.source.kind)
        assertEquals(ModEffect.Flat(-4), mod.effect)
        assertEquals("state:shock", mod.id.value)
    }
}
