package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The Languages section (Ch. 3, p. 13-14).
 *
 * Two tables print the same three row names at different prices, and only one
 * of them says anything about rituals. Nearly every test here is about not
 * letting those two collapse into one.
 */
class LanguagesTest {

    private fun mystical(name: String) = Languages.proficiency(Languages.MYSTICAL, name)
    private fun common(name: String) = Languages.proficiency(Languages.COMMON, name)

    @Test
    fun `six common languages and six mystical ones`() {
        assertEquals(
            listOf("Loen", "Feysac", "Intis", "Balam (Dutan)", "Highland", "Rorsted"),
            Languages.common().map { it.name },
        )
        assertEquals(
            listOf(
                "Ancient Hermes", "Hermes", "Elvish", "Dragonese",
                "Jotun (Giant's)", "Ancient Feysac",
            ),
            Languages.mystical().map { it.name },
        )
    }

    @Test
    fun `the same three proficiencies are priced twice, and never the same`() {
        // The whole hazard of this section: nothing inside a row says which
        // table it came from. Keyed on the name alone, a mystical Native costs
        // 6 points instead of 30 -- a fifth of the book's price.
        assertEquals(listOf(6, 4, 2), Languages.proficiencies(Languages.COMMON).map { it.points })
        assertEquals(listOf(30, 20, 10), Languages.proficiencies(Languages.MYSTICAL).map { it.points })
        assertEquals(6, common("Native").points)
        assertEquals(30, mystical("Native").points)
    }

    @Test
    fun `only the mystical table speaks about rituals`() {
        // A common row says nothing, which is not the same as permitting a
        // ritual at no penalty -- the distinction the ritual critical table
        // already draws between charging no corruption and charging zero.
        Languages.proficiencies(Languages.COMMON).forEach {
            assertNull(it.canPerformRituals, "${it.proficiency} is a common language")
            assertNull(it.ritualModifier)
        }
        assertTrue(Languages.proficiencies(Languages.MYSTICAL).all { it.canPerformRituals != null })
    }

    @Test
    fun `a mystical Accented caster rolls at minus two`() {
        assertEquals(-2, Languages.ritualModifier(mystical("Accented")))
        assertEquals(0, Languages.ritualModifier(mystical("Native")))
        assertEquals(0, Languages.ritualModifier(null), "an unstated language changes nothing")
    }

    @Test
    fun `Broken cannot cast at all, and is refused rather than penalised`() {
        // "Cannot perform rituals in the language." A modifier here would
        // imply a roll exists to modify, and a caster who cannot cast is not
        // a caster at -4.
        val broken = mystical("Broken")
        assertFalse(broken.canPerformRituals!!)
        assertNull(broken.ritualModifier, "no penalty is printed, because there is no roll")
        val ex = assertThrows<IllegalArgumentException> { Languages.ritualModifier(broken) }
        assertTrue(ex.message!!.contains("Cannot perform rituals"))
    }

    @Test
    fun `a common language is refused as a ritual language`() {
        // The book grants ritual power to mystical languages only. A common
        // row makes no claim either way, so reading it as +0 would let a
        // ritual be cast in everyday Loen on the strength of the book's
        // silence.
        assertThrows<IllegalArgumentException> { Languages.ritualModifier(common("Native")) }
    }

    @Test
    fun `an unprinted proficiency or type is refused`() {
        assertThrows<IllegalArgumentException> { Languages.proficiency(Languages.MYSTICAL, "Fluent") }
        assertThrows<IllegalArgumentException> { Languages.proficiency("Divine", "Native") }
    }

    @Test
    fun `the two halving rules are carried as sentences, not as arithmetic`() {
        // No caller buys a language yet -- the builder edits attributes,
        // skills, wealth and looks. A cost calculator with no purchaser would
        // be a rule nobody tests, and the book prints two halvings that
        // interact ("Spoken-only ... costs half", "Language Talent halves all
        // costs (rounded up)") without ever combining them in an example.
        val notes = Languages.notes()
        assertTrue(notes.any { it.contains("Spoken-only or written-only costs half") })
        assertTrue(notes.any { it.contains("Language Talent halves all costs") })
    }

    @Test
    fun `chapter 3's trait list points at this section and disagrees with it twice`() {
        // RECORDED, never repaired. Unlike Appearance, whose levels the trait
        // lists print zero times, "Language (specify)" cross-references these
        // tables -- and two neighbouring rows contradict them.
        val advantages = ContentLoader.advantages().associateBy { it.name }

        assertTrue(
            advantages.getValue("Language (specify)").notes.contains("Languages section"),
            "the pointer that makes these numbers reachable from a billable trait",
        )
        // The section says "two COMMON languages ... it does not apply to
        // mystical languages"; the trait row drops the restriction entirely.
        assertFalse(advantages.getValue("Multilingual").notes.contains("common"))
        // And this row names a language neither table prints.
        assertTrue(advantages.getValue("Dead Language Fluency").notes.contains("Ancient Loen"))
        assertTrue(Languages.all().none { it.name == "Ancient Loen" })
    }
}
