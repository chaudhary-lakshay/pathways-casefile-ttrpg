package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.model.TraitKind
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

    // ---- buying a language --------------------------------------------------

    private fun buy(language: String, proficiency: String, form: String = Languages.BOTH_FORMS) =
        LanguagePurchase(language, proficiency, form)

    private fun holding(vararg names: String) = names.map { Trait(it, 0, TraitKind.ADVANTAGE) }

    private fun costOf(purchase: LanguagePurchase, held: List<Trait> = emptyList()): Int? =
        Languages.owned(listOf(purchase), held).single().second?.cost

    /** "Points listed are for Spoken + Written at the given level" (p. 13). */
    @Test
    fun `a language costs what its own table prints`() {
        assertEquals(listOf(6, 4, 2), listOf("Native", "Accented", "Broken").map { costOf(buy("Loen", it)) })
        assertEquals(listOf(30, 20, 10), listOf("Native", "Accented", "Broken").map { costOf(buy("Hermes", it)) })
    }

    /** "Spoken-only or written-only costs half." Every printed price is even. */
    @Test
    fun `one form costs half`() {
        for (form in listOf(Languages.SPOKEN_ONLY, Languages.WRITTEN_ONLY)) {
            assertEquals(3, costOf(buy("Feysac", "Native", form)), form)
            assertEquals(1, costOf(buy("Feysac", "Broken", form)), form)
            assertEquals(15, costOf(buy("Elvish", "Native", form)), form)
            assertEquals(5, costOf(buy("Elvish", "Broken", form)), form)
        }
    }

    /**
     * "Language Talent halves all costs (rounded up)." The form's half is
     * always whole, so the rounding the book prints is the only one there is:
     * half of 3 is 2, and half of 15 is 8.
     */
    @Test
    fun `Language Talent halves after the form, rounded up`() {
        val talent = holding(Languages.TALENT)
        assertEquals(listOf(3, 2, 1), listOf("Native", "Accented", "Broken").map { costOf(buy("Loen", it), talent) })
        assertEquals(15, costOf(buy("Hermes", "Native"), talent))
        val spoken = Languages.SPOKEN_ONLY
        assertEquals(
            listOf(2, 1, 1),
            listOf("Native", "Accented", "Broken").map { costOf(buy("Loen", it, spoken), talent) },
        )
        assertEquals(
            listOf(8, 5, 3),
            listOf("Native", "Accented", "Broken").map { costOf(buy("Hermes", it, spoken), talent) },
        )
        // At the cost the book prints for it, as Occult Scholar's sheet does.
        assertEquals(3, costOf(buy("Loen", "Native"), listOf(Trait("Language Talent", 10, TraitKind.ADVANTAGE))))
    }

    /**
     * "Multilingual (3 pts) grants fluency in two common languages without
     * point cost -- it does not apply to mystical languages" (p. 14). Fluent
     * is the Native row's own word, and the table prices Spoken + Written.
     */
    @Test
    fun `Multilingual frees the first two fluent common languages and nothing else`() {
        val bought = listOf(
            buy("Hermes", "Native"),
            buy("Loen", "Native"),
            buy("Feysac", "Accented"),
            buy("Intis", "Native", Languages.SPOKEN_ONLY),
            buy("Highland", "Native"),
            buy("Rorsted", "Native"),
        )
        val with = Languages.owned(bought, holding(Languages.MULTILINGUAL)).map { it.second!! }
        assertEquals(listOf(30, 0, 4, 3, 0, 6), with.map { it.cost })
        assertEquals(listOf("Loen", "Highland"), with.filter { it.isFree }.map { it.purchase.language })
        val without = Languages.owned(bought, emptyList()).map { it.second!! }
        assertEquals(listOf(30, 6, 4, 3, 6, 6), without.map { it.cost })
        assertTrue(without.none { it.isFree })
    }

    @Test
    fun `a purchase the book does not print is dropped, and a language is owned once`() {
        assertNull(costOf(buy("Klingon", "Native")))
        assertNull(costOf(buy("Loen", "Fluent")))
        assertNull(costOf(buy("Loen", "Native", "Sung")))
        val twice = Languages.owned(listOf(buy("Loen", "Broken"), buy("Loen", "Native")), emptyList())
        assertEquals(2, twice.first().second!!.cost, "the first purchase stands")
        assertNull(twice.last().second, "and the second bills nothing")
    }

    /**
     * The composed name has to resolve to the book's own row, or the trait is
     * an identity nothing matches. Two printed languages carry brackets of
     * their own -- "Balam (Dutan)", "Jotun (Giant's)" -- and the placeholder
     * matcher split on the LAST bracket (a design review's finding).
     */
    @Test
    fun `every language composes to a trait the catalogue resolves`() {
        for (language in Languages.all()) {
            for (form in Languages.forms()) {
                val owned = Languages.owned(listOf(buy(language.name, "Accented", form)), emptyList())
                    .single().second!!
                val trait = owned.toTrait()
                assertEquals("Language (specify)", TraitEffects.catalogueName(trait.name), trait.name)
                assertEquals(TraitKind.ADVANTAGE, trait.kind)
                assertEquals(owned.cost, trait.cost)
                assertTrue(language.name in trait.name && "Accented" in trait.name, trait.name)
            }
        }
        assertEquals(
            "Language (Hermes, Native)",
            Languages.owned(listOf(buy("Hermes", "Native")), emptyList()).single().second!!.toTrait().name,
        )
        assertEquals(
            "Language (Balam (Dutan), Broken, written only)",
            Languages.owned(listOf(buy("Balam (Dutan)", "Broken", Languages.WRITTEN_ONLY)), emptyList())
                .single().second!!.toTrait().name,
        )
    }

    /**
     * The builder prints one line per language and it has to reconcile: the
     * table's price, and each thing that moved it. Authored here, so the
     * screen types no number of its own.
     */
    @Test
    fun `the cost line says what moved the price`() {
        fun line(purchase: LanguagePurchase, vararg held: String) =
            Languages.owned(listOf(purchase), holding(*held)).single().second!!.costLine
        assertEquals("20 pts", line(buy("Hermes", "Accented")))
        assertEquals("1 pt (2 listed; half for one form)", line(buy("Loen", "Broken", Languages.SPOKEN_ONLY)))
        assertEquals(
            "8 pts (30 listed; half for one form; halved by Language Talent, rounded up)",
            line(buy("Hermes", "Native", Languages.WRITTEN_ONLY), Languages.TALENT),
        )
        assertEquals(
            "free with Multilingual (6 listed)",
            line(buy("Loen", "Native"), Languages.MULTILINGUAL),
        )
        // Multilingual is what freed it, so Language Talent is not also named.
        assertEquals(
            "free with Multilingual (6 listed)",
            line(buy("Loen", "Native"), Languages.MULTILINGUAL, Languages.TALENT),
        )
    }

    /** "Fluent" is the Native row's own first word, in both tables. */
    @Test
    fun `the row Multilingual frees is the one the book calls fluent`() {
        for (type in listOf(Languages.COMMON, Languages.MYSTICAL)) {
            assertTrue(Languages.proficiency(type, Languages.FLUENT).effect.startsWith("Fluent."))
            assertEquals(1, Languages.proficiencies(type).count { it.effect.startsWith("Fluent.") })
        }
    }

    /**
     * The two traits this object reads are APPLIED, so the trait census has
     * to see them. `TraitEffects.known()` reads collections, and a trait
     * applied from another file belongs to none: the failure
     * `VitalRollsTest` records three times, a fourth time.
     */
    @Test
    fun `every trait the language prices read is one the census can see`() {
        assertEquals(setOf(Languages.TALENT, Languages.MULTILINGUAL), Languages.PRICING_TRAITS)
        assertEquals(emptySet<String>(), Languages.PRICING_TRAITS - TraitEffects.known())
        // Multilingual's cell prints a second clause, and it reaches nothing.
        val multilingual = TraitEffects.reported(holding(Languages.MULTILINGUAL))
        assertTrue(multilingual.any { "language-dependent" in it.printed }, multilingual.toString())
        // Language Talent's one clause is applied whole: nothing to report.
        assertEquals(emptyList<TraitNote>(), TraitEffects.reported(holding(Languages.TALENT)))
    }

    /** Owned, billed, and still on the sheet: the penalties reach no roll. */
    @Test
    fun `a bought language is reported on the sheet with what is not applied`() {
        val trait = Languages.owned(listOf(buy("Hermes", "Accented")), emptyList()).single().second!!.toTrait()
        val notes = TraitEffects.reported(listOf(trait))
        assertTrue(notes.any { "language-dependent" in it.reason }, notes.toString())
        assertEquals(20, PointBudget.report(
            com.pathways.model.Attributes(9, 9, 9, 9), com.pathways.model.SecondaryDelta(), listOf(trait), emptyList(),
        ).spent - PointBudget.report(
            com.pathways.model.Attributes(9, 9, 9, 9), com.pathways.model.SecondaryDelta(), emptyList(), emptyList(),
        ).spent)
    }
}
