package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.ModEffect
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Ch. 3's advantages, where their printed numbers reach a roll. */
class TraitEffectsTest {

    private fun advantage(name: String) = Trait(name, 5, TraitKind.ADVANTAGE)
    private fun skill(attr: ControllingAttribute) =
        SkillKey("Whatever", null, attr, Difficulty.AVERAGE)

    private val per = skill(ControllingAttribute.Per)
    private val ht = skill(ControllingAttribute.Buyable(Attr.HT))
    private val iq = skill(ControllingAttribute.Buyable(Attr.IQ))

    /**
     * **The sentences these numbers were read from, against the copy
     * production reads.** They live in Kotlin because each is a value inside
     * a prose cell, and committing one as a column would make the book look
     * like it printed one -- the split the maneuver table's +4/-4 (6i) and
     * the orthodox -2 (6z) already take. So the pin has to be here.
     */
    @Test
    fun `every applied number is still the one its row prints`() {
        val byName = ContentLoader.advantages().associateBy { it.name }
        assertTrue(
            byName.getValue(TraitEffects.ALERTNESS).notes
                .startsWith("+${TraitEffects.ALERTNESS_PER_LEVEL} per level to all Per rolls"),
            byName.getValue(TraitEffects.ALERTNESS).notes,
        )
        assertTrue(
            byName.getValue(TraitEffects.FIT).notes
                .startsWith("+${TraitEffects.FIT_BONUS} to all HT rolls"),
            byName.getValue(TraitEffects.FIT).notes,
        )
        assertTrue(
            byName.getValue(TraitEffects.COMBAT_REFLEXES).notes
                .startsWith("+${TraitEffects.COMBAT_REFLEXES_BONUS} to all active defenses"),
            byName.getValue(TraitEffects.COMBAT_REFLEXES).notes,
        )
        // Alertness is the levelled one; the other two are not.
        assertTrue(byName.getValue(TraitEffects.ALERTNESS).perLevel)
        assertTrue(!byName.getValue(TraitEffects.FIT).perLevel)
    }

    // ---- the level in the name -------------------------------------------

    /**
     * A sheet prints "Alertness 1"; the catalogue prints "Alertness" and a
     * flag. An unlevelled trait counts as ONE level of itself, never zero --
     * zero would silently cancel its own bonus.
     */
    @Test
    fun `a level printed in the name is read, and its absence means one`() {
        assertEquals(1, TraitEffects.levelOf(listOf(advantage("Alertness 1")), "Alertness"))
        assertEquals(3, TraitEffects.levelOf(listOf(advantage("Alertness 3")), "Alertness"))
        assertEquals(1, TraitEffects.levelOf(listOf(advantage("Fit")), "Fit"))
        assertNull(TraitEffects.levelOf(listOf(advantage("Fit")), "Alertness"))
    }

    /**
     * A name whose last word is not a number keeps it. "Poverty: Struggling"
     * is on ten premades, and reading its tail as a level would invent one.
     */
    @Test
    fun `a trailing word that is not a number is part of the name`() {
        val poverty = Trait("Poverty: Struggling", 10, TraitKind.DISADVANTAGE)
        assertEquals(1, TraitEffects.levelOf(listOf(poverty), "Poverty: Struggling"))
        assertNull(TraitEffects.levelOf(listOf(poverty), "Poverty"))
    }

    // ---- reaching a roll --------------------------------------------------

    /**
     * **Alertness reaches a Per roll and nothing else.** It is on eight of
     * the 23 premades and reached no roll at all until this slice -- the
     * defect class 6h fixed for the trackers. Scoped by controlling
     * attribute, the same test that keeps Spiritual Attrition on SPI rolls.
     */
    @Test
    fun `Alertness lifts a Per roll by its level`() {
        val traits = listOf(advantage("Alertness 2"))
        val mods = TraitEffects.modifiers(traits, per)
        assertEquals(1, mods.size)
        assertEquals(ModEffect.Flat(2), mods.single().effect)
        // ...and touches neither of the other two roll classes.
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(traits, ht))
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(traits, iq))
    }

    @Test
    fun `Fit lifts an HT roll and not a Per one`() {
        val traits = listOf(advantage("Fit"))
        assertEquals(ModEffect.Flat(1), TraitEffects.modifiers(traits, ht).single().effect)
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(traits, per))
    }

    @Test
    fun `a character with neither gets nothing`() {
        val traits = listOf(advantage("Overconfidence"), advantage("Stubborn"))
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(traits, per))
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(traits, ht))
    }

    /**
     * **"+1 to all active defenses" means ALL of them.** Block is the only
     * defence whose formula names a modifier, which makes Block-only the easy
     * wrong reading -- and it would understate a Dodge by one. The same trap
     * a shield's DB already had to avoid (6o).
     */
    @Test
    fun `Combat Reflexes lifts every active defence, not just Block`() {
        val traits = listOf(advantage("Combat Reflexes"))
        assertEquals(1, TraitEffects.defenseBonus(traits))
        assertEquals(0, TraitEffects.defenseBonus(listOf(advantage("Fit"))))
        // It is a defence bonus and NOT a skill bonus: the same character
        // gets nothing on a Per or HT roll from it.
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(traits, per))
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(traits, ht))
    }

    // ---- printed, real, and reaching nothing -------------------------------

    /**
     * **Shown with the reason, never silently dropped.** A GM who reads "this
     * app models no shock penalty to ignore" can apply the trait themselves;
     * one who sees nothing assumes it was counted. The call the Sealed
     * Artifact grade table (6af) and a technique's availability (6ai) needed.
     */
    @Test
    fun `an unapplied trait is reported with the book's own words`() {
        val notes = TraitEffects.reported(
            listOf(advantage("High Pain Threshold"), advantage("Hard to Kill 2")),
        )
        assertEquals(listOf("High Pain Threshold", "Hard to Kill 2"), notes.map { it.trait })
        assertTrue(notes[0].printed.contains("shock penalties"), notes[0].printed)
        assertTrue(notes[0].reason.contains("no shock penalty"), notes[0].reason)
        // The levelled one still finds its catalogue row through the level.
        assertTrue(notes[1].printed.contains("HT rolls to avoid death"), notes[1].printed)
    }

    /** An applied trait is not also reported: it would read as unhandled. */
    @Test
    fun `the applied traits are absent from the reported list`() {
        val applied = listOf(
            advantage("Alertness 1"),
            advantage("Fit"),
            advantage("Combat Reflexes"),
        )
        assertEquals(emptyList<TraitNote>(), TraitEffects.reported(applied))
    }

    /**
     * **An advantage is what a character IS, not what the GM says this turn.**
     * `ModifierSource.FromTrait` and `Layer.ADVANTAGE` have existed since M0
     * with no production caller, and this is the first -- so tagging these as
     * `Situational` would have put a permanent trait in the same `SourceKind`
     * bucket a Cancel budget is measured against. Nothing can reach that
     * today, which is exactly why it is pinned now (caught in review).
     */
    @Test
    fun `a trait bonus is sourced as a trait, not as a circumstance`() {
        val mod = TraitEffects.modifiers(listOf(advantage("Alertness 1")), per).single()
        assertEquals(
            com.pathways.model.ModifierSource.FromTrait("Alertness", advantage = true),
            mod.source,
        )
        assertEquals(com.pathways.model.SourceKind.ADVANTAGE, mod.source.kind)
        assertEquals(com.pathways.model.Layer.ADVANTAGE, mod.layer)
    }

    /**
     * Every trait this object names is one the book actually prints. A typo
     * would make a row unreachable in silence -- and `reported` resolves its
     * text through the catalogue, so a name that misses matches nothing.
     */
    @Test
    fun `every trait named here is one the book prints`() {
        val printed = ContentLoader.advantages().map { it.name }.toSet()
        assertEquals(emptySet<String>(), TraitEffects.known() - printed)
    }
}
