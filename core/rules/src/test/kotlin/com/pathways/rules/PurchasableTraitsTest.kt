package com.pathways.rules

import com.pathways.model.Attributes
import com.pathways.model.SecondaryDelta
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.TraitDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Buying a trait in play: which rows, at what price, and which are withheld. */
class PurchasableTraitsTest {

    @Test
    fun `the trait this whole slice exists for is buyable`() {
        // No premade carries Ether Body Damage and no potion grants a trait,
        // so before this there was no route to it at all -- the "unreachable
        // feature looks exactly like a missing one" shape (6bv).
        val row = PurchasableTraits.named(TraitEffects.ETHER_BODY_DAMAGE)
        assertNotNull(row)
        assertEquals(10, row!!.cost)
        assertFalse(row.advantage, "it is a disadvantage, and the budget refunds it")
        assertEquals(TraitKind.DISADVANTAGE, row.toTrait().kind)
        assertEquals(10, row.toTrait().cost, "Trait carries a magnitude; the kind is the sign")
    }

    /**
     * **Every printed row is offered, instantiable, or explained -- and the
     * three are disjoint.** The total is not asserted as a NUMBER: a count in
     * prose is a finding (6as), and one in a test goes stale the first time
     * the catalogue grows. Disjointness is the half a reviewer caught: a row
     * reported as buyable AND as unsupported is an API contradicting itself,
     * and a screen rendering both lists would show it twice.
     */
    @Test
    fun `every printed row is either offered, instantiable, or explained`() {
        val printed = printedRows().map { it.name }.toSortedSet()
        val groups = listOf(
            PurchasableTraits.offered().map { it.name },
            PurchasableTraits.instantiable().map { it.name },
            PurchasableTraits.unoffered().flatMap { it.names },
        )
        assertEquals(printed, groups.flatten().toSortedSet(), "a printed row is unaccounted for")
        assertEquals(
            groups.sumOf { it.size },
            groups.flatten().distinct().size,
            "a row is reported in two groups at once",
        )
    }

    /**
     * Every trait row the book prints, blessings included.
     *
     * The blessings are a third list because the book prices them once in
     * prose for all seven rather than in a cost column, so a guard reading the
     * two catalogues alone reports them as unaccounted for -- which is exactly
     * what both of these tests did the moment they became buyable.
     */
    private fun printedRows(): List<TraitDefinition> =
        ContentLoader.advantages() + ContentLoader.disadvantages() +
            ContentLoader.blessings().map { it.asTrait(ContentLoader.blessingCost()) }

    @Test
    fun `only a row printing a single cost is offered`() {
        val byName = printedRows().associateBy { it.name }
        for (row in PurchasableTraits.offered()) {
            assertEquals(
                byName.getValue(row.name).cost,
                row.cost,
                "${row.name} is offered at a price the book does not print",
            )
        }
    }

    /**
     * **Economic status is one choice and [Wealth] owns it.** A general picker
     * offering "Wealth: Poor" would let a character hold two statuses, or
     * fight the wealth row for the same points -- and one slice ago that
     * double count was live (6bx). The exclusion is here rather than in the
     * screen so a hand-edited save cannot get round it either.
     */
    @Test
    fun `wealth is never offered as an ordinary trait`() {
        assertTrue(
            PurchasableTraits.offered().none { it.name.startsWith(Wealth.TRAIT_PREFIX) },
            "the trait picker offers an economic status",
        )
        assertNull(PurchasableTraits.named("Wealth: Poor"))
        // ...and the four rows are NAMED rather than silently missing.
        assertTrue(
            PurchasableTraits.unoffered().flatMap { it.names }
                .any { it.startsWith(Wealth.TRAIT_PREFIX) },
            "the wealth rows vanished without being explained",
        )
    }

    /**
     * A row the book prices per instance is no longer WITHHELD -- it has a
     * licensed purchase path -- and what is left here is only what nothing can
     * price: a row printing no bounds, and economic status, which is one
     * choice owned by [Wealth].
     */
    @Test
    fun `only a row nothing can price is withheld, and it says why`() {
        val withheld = PurchasableTraits.unoffered().flatMap { it.names }
        assertTrue("Reputation" in withheld, "no printed bounds")
        assertTrue("Language (specify)" in withheld, "priced by a table of its own")
        // These moved to instantiable() in this slice.
        assertFalse("Sense of Duty" in withheld)
        assertFalse("Secret" in withheld)
        assertFalse("Duty (specify)" in withheld)
        for (group in PurchasableTraits.unoffered()) {
            assertTrue(group.reason.isNotBlank(), "a withheld group with no reason")
            assertTrue(group.names.isNotEmpty(), "a reason with nothing under it")
        }
    }

    /**
     * **A duplicate charges points and changes nothing.** [PointBudget] bills
     * every row it is handed, while [TraitEffects] reads one canonical match
     * per trait -- so a second Fit costs 5 points and lifts no roll further.
     */
    @Test
    fun `a trait the character already holds cannot be bought again`() {
        val fit = PurchasableTraits.named("Fit")!!
        assertTrue(PurchasableTraits.canBuy(fit, emptyList()))
        assertFalse(PurchasableTraits.canBuy(fit, listOf(Trait("Fit", 5, TraitKind.ADVANTAGE))))
    }

    /**
     * **The sheet's own spelling counts as holding it.** Two premades print
     * "Clueless" with the book's whole effect cell glued to the name, and
     * offering them a second Clueless because the strings differ would be the
     * two-matchers failure this repo has produced six times. Resolved through
     * the one matcher production already uses.
     */
    @Test
    fun `a glued sheet spelling still blocks a second copy`() {
        val clueless = PurchasableTraits.named("Clueless")!!
        val printed = Premades.all()
            .flatMap { it.toCharacter().traits }
            .first { it.name.startsWith("Clueless") && it.name.length > "Clueless".length }
        assertFalse(
            PurchasableTraits.canBuy(clueless, listOf(printed)),
            "a glued spelling of the same trait let it be bought twice",
        )
    }

    @Test
    fun `a name the book no longer prints resolves to nothing`() {
        assertNull(PurchasableTraits.named("Gumption"))
    }

    /**
     * **The budget bills a bought trait exactly as it bills a printed one**,
     * which is why this feature needed no billing code: an advantage spends
     * and a disadvantage refunds, both through the magnitude and kind
     * [PurchasableTrait.toTrait] produces.
     */
    @Test
    fun `buying an advantage spends and buying a drawback refunds`() {
        val ether = PurchasableTraits.named(TraitEffects.ETHER_BODY_DAMAGE)!!.toTrait()
        val fit = PurchasableTraits.named("Fit")!!.toTrait()
        fun report(traits: List<Trait>) =
            PointBudget.report(Attributes(), SecondaryDelta(), traits, emptyList())

        assertEquals(PointBudget.STARTING_TOTAL + 10, report(listOf(ether)).budget)
        assertEquals(0, report(listOf(ether)).spent)
        assertEquals(5, report(listOf(fit)).spent)
    }

    // ---- the rows the book prices per instance ----------------------------

    /**
     * **A printed range and a printed "(specify)" are one thing in two
     * notations** -- the book asking for an instance -- which is what a design
     * review established and what makes these one feature rather than two.
     * Every row carrying an interval is here, whichever notation it uses.
     */
    @Test
    fun `every row printing an interval is instantiable, whichever notation it uses`() {
        val names = PurchasableTraits.instantiable().map { it.name }
        assertTrue("Phobia (specify)" in names, "the placeholder notation")
        assertTrue("Sense of Duty" in names, "the range notation")
        assertTrue(PurchasableTraits.instantiableNamed("Phobia (specify)")!!.needsInstance)
        assertFalse(PurchasableTraits.instantiableNamed("Sense of Duty")!!.needsInstance)
        // ...and nothing the book prices outright is in here: those are bought
        // at the one cost the book prints.
        assertNull(PurchasableTraits.instantiableNamed(TraitEffects.ETHER_BODY_DAMAGE))
    }

    /**
     * **An unbounded row is not an interval with no ceiling.** "Reputation"
     * and "Language (specify)" price themselves by a procedure this app does
     * not hold, and taking whatever number a player typed would make the SAVE
     * authoritative over the book -- the inversion every other stored choice
     * in this codebase avoids by storing the choice and re-reading the price.
     */
    @Test
    fun `a row printing no bounds is not instantiable`() {
        assertNull(PurchasableTraits.instantiableNamed("Reputation"))
        assertNull(PurchasableTraits.instantiableNamed("Language (specify)"))
        assertNull(PurchasableTraits.instanced(InstancedPurchase("Reputation", cost = 5)))
    }

    /**
     * The three composition rules, and the third is the load-bearing one.
     *
     * A "(specify)" row takes the instance and an enumerating row takes the
     * variant, because [TraitNames.catalogueName] already resolves both
     * spellings. **Anything else composes the book's own bare name**: "Sense
     * of Duty (Patients)" resolves to nothing here, so composing it would hand
     * the engine an identity it cannot match and the trait would silently do
     * nothing.
     */
    @Test
    fun `the name is composed from the parts, and a bare row stays bare`() {
        val phobia = PurchasableTraits.instanced(
            InstancedPurchase("Phobia (specify)", instance = "Cities", cost = 10),
        )
        assertEquals("Phobia (Cities)", phobia!!.name)
        assertEquals("Phobia (specify)", TraitNames.catalogueName(phobia.name))

        val shy = PurchasableTraits.instanced(
            InstancedPurchase("Shyness", variant = "Mild", cost = 5),
        )
        assertEquals("Shyness (Mild)", shy!!.name)
        assertEquals("Shyness", TraitNames.catalogueName(shy.name))

        val duty = PurchasableTraits.instanced(
            InstancedPurchase("Sense of Duty", instance = "Patients", cost = 10),
        )
        assertEquals("Sense of Duty", duty!!.name, "the instance is display, not identity")
        assertEquals("Sense of Duty", TraitNames.catalogueName(duty.name))
    }

    /**
     * The book's interval is the whole of what it licenses, at both ends.
     * A "(specify)" row with nothing specified is refused rather than composed
     * into the placeholder itself, which would put the word "(specify)" on a
     * character sheet.
     */
    @Test
    fun `a purchase the book does not license is refused`() {
        fun secret(cost: Int) =
            PurchasableTraits.instanced(InstancedPurchase("Secret", instance = "A body", cost = cost))
        assertNull(secret(4))
        assertNull(secret(31))
        assertNotNull(secret(5))
        assertNotNull(secret(30))
        assertNull(PurchasableTraits.instanced(InstancedPurchase("Phobia (specify)", cost = 10)))
        assertNull(
            PurchasableTraits.instanced(
                InstancedPurchase("Phobia (specify)", instance = "  ", cost = 10),
            ),
        )
        // A cell that prices its own options prices them exactly, and a row
        // that enumerates cannot be bought without naming one.
        assertNull(
            PurchasableTraits.instanced(
                InstancedPurchase("Shyness", variant = "Mild", cost = 10),
            ),
        )
        assertNull(PurchasableTraits.instanced(InstancedPurchase("Shyness", cost = 7)))
        assertNull(
            PurchasableTraits.instanced(
                InstancedPurchase("Shyness", variant = "Crippling", cost = 5),
            ),
        )
    }

    /**
     * **A bare price settles nothing**, which is the same rule the placeholder
     * states out loud. The two notations are one request, so an interval needs
     * an instance exactly as "(specify)" does -- an unnamed Secret is a cost
     * with no dangerous hidden truth behind it, and the price is supposed to
     * follow from what the thing is. A cell that enumerates its own options is
     * the exception: there the VARIANT settles it and prose adds nothing.
     */
    @Test
    fun `an interval needs an instance, exactly as a placeholder does`() {
        assertNull(PurchasableTraits.instanced(InstancedPurchase("Secret", cost = 10)))
        assertNull(PurchasableTraits.instanced(InstancedPurchase("Obsession", cost = 5)))
        assertNotNull(
            PurchasableTraits.instanced(
                InstancedPurchase("Obsession", instance = "The lost manuscript", cost = 5),
            ),
        )
        // Shyness enumerates, so its variant is what settles it.
        assertNotNull(
            PurchasableTraits.instanced(InstancedPurchase("Shyness", variant = "Mild", cost = 5)),
        )
    }

    /**
     * **And a variant purchase takes no instance.** The instance would be
     * dropped from the composed name and kept in the key, so two identical
     * "Shyness (Mild)" purchases carrying different prose would both survive
     * the de-dupe and bill twice -- one identity for the engine, another for
     * the key, which is the divergence this shape exists to avoid (Codex).
     */
    /**
     * **A variant is a severity, not a second thing.** Shyness is Mild or
     * Severe and a character cannot be both, so an enumerating row admits ONE
     * -- including against a spelling the sheet already prints. Every other
     * row may repeat, because a second vow really is a second vow (Codex).
     */
    @Test
    fun `an enumerating row admits one, and every other row may repeat`() {
        val shyness = PurchasableTraits.instantiableNamed("Shyness")!!
        val vow = PurchasableTraits.instantiableNamed("Vow (specify)")!!
        val mild = listOf(Trait("Shyness (Mild)", 5, TraitKind.DISADVANTAGE))
        assertTrue(PurchasableTraits.canInstantiate(shyness, emptyList()))
        assertFalse(PurchasableTraits.canInstantiate(shyness, mild), "Mild and Severe at once")
        assertTrue(
            PurchasableTraits.canInstantiate(vow, listOf(Trait("Vow (Poverty)", 10, TraitKind.DISADVANTAGE))),
            "a second vow is a second vow",
        )
        // ...and two severities saved at once collapse to one, because the
        // variant is deliberately not part of what identifies a purchase.
        val both = PurchasableTraits.instancedTraits(
            listOf(
                InstancedPurchase("Shyness", variant = "Mild", cost = 5),
                InstancedPurchase("Shyness", variant = "Severe", cost = 10),
            ),
        )
        assertEquals(listOf("Shyness (Mild)"), both.map { it.name })
    }

    @Test
    fun `a variant purchase carrying prose is refused rather than trimmed`() {
        assertNull(
            PurchasableTraits.instanced(
                InstancedPurchase("Shyness", variant = "Mild", instance = "Crowds", cost = 5),
            ),
        )
        val two = PurchasableTraits.instancedTraits(
            listOf(
                InstancedPurchase("Shyness", variant = "Mild", instance = "Crowds", cost = 5),
                InstancedPurchase("Shyness", variant = "Mild", instance = "Parties", cost = 5),
            ),
        )
        assertTrue(two.isEmpty(), "two Shyness (Mild) purchases would have billed twice")
    }

    /**
     * **An unlicensed entry must not consume the slot.** A save can carry a
     * stale row -- a cost the book no longer prints, an instance since
     * cleared -- and if the invalid entry claimed the key first, the corrected
     * purchase after it would be dropped as a duplicate and the character
     * would silently lose a trait they had paid for (Codex). Validate, then
     * dedupe.
     */
    @Test
    fun `a stale entry does not suppress the corrected one behind it`() {
        val traits = PurchasableTraits.instancedTraits(
            listOf(
                InstancedPurchase("Secret", instance = "A body", cost = 99),
                InstancedPurchase("Secret", instance = "A body", cost = 10),
            ),
        )
        assertEquals(1, traits.size)
        assertEquals(10, traits.single().cost, "the licensed purchase survived")
    }

    /**
     * **The book's own cast holds one instantiable row twice.** Monastery
     * Scholar carries "Vow (Partial Silence)" and "Vow (Vegetarian)", so a
     * row-keyed refusal would forbid a published character. What repeats is
     * refused instead: the same row, variant and instance is one purchase.
     */
    @Test
    fun `two instances of one row are both kept and an exact repeat is not`() {
        val two = PurchasableTraits.instancedTraits(
            listOf(
                InstancedPurchase("Vow (specify)", instance = "Partial Silence", cost = 5),
                InstancedPurchase("Vow (specify)", instance = "Vegetarian", cost = 5),
            ),
        )
        assertEquals(listOf("Vow (Partial Silence)", "Vow (Vegetarian)"), two.map { it.name })

        val repeated = PurchasableTraits.instancedTraits(
            listOf(
                InstancedPurchase("Sense of Duty", instance = "Patients", cost = 10),
                InstancedPurchase("Sense of Duty", instance = "Patients", cost = 10),
            ),
        )
        assertEquals(1, repeated.size, "the same row, variant and instance is one purchase")

        // Two bare purchases of one row differ only by instance, and by Trait
        // alone they are identical -- which is why the key is not the name.
        val bare = PurchasableTraits.instancedTraits(
            listOf(
                InstancedPurchase("Sense of Duty", instance = "Patients", cost = 10),
                InstancedPurchase("Sense of Duty", instance = "Coworkers", cost = 5),
            ),
        )
        assertEquals(2, bare.size)
        assertEquals(listOf("Sense of Duty", "Sense of Duty"), bare.map { it.name })
    }

    /**
     * **Two fears are two declarations**, and it falls out of the composition
     * rather than needing a rule: a fright clause is keyed by the DISPLAYED
     * name plus the printed clause, so "Phobia (Cities)" and "Phobia
     * (Spiders)" are separately declarable and separately applied. A key on
     * the catalogue row would collapse them, so one tick would move both and
     * neither could be declared alone -- wrong in both directions.
     */
    @Test
    fun `two phobias are two clauses, each declarable on its own`() {
        val traits = PurchasableTraits.instancedTraits(
            listOf(
                InstancedPurchase("Phobia (specify)", instance = "Cities", cost = 10),
                InstancedPurchase("Phobia (specify)", instance = "Spiders", cost = 10),
            ),
        )
        val clauses = TraitClauses.frightClauses(traits)
        assertEquals(2, clauses.size)
        assertEquals(2, clauses.map { it.key }.distinct().size, "one key each")
        val cities = clauses.single { it.shown == "Phobia (Cities)" }
        assertEquals(-2, TraitClauses.clauseBonus(clauses, setOf(cities.key)).total)
        assertEquals(
            -4,
            TraitClauses.clauseBonus(clauses, clauses.map { it.key }.toSet()).total,
            "both fears in play cost both penalties",
        )
    }

    /**
     * A bought instance reaches the rolls its row prints. Obsession's target
     * is a flat number in the book's own NAME ("Obsession (12)"), stripped
     * into the catalogue by the extractor (6am); Compulsion: Collection prints
     * Will-2 in its effect cell. Both rows print a cost RANGE, so neither was
     * reachable at all before this.
     */
    @Test
    fun `a bought instance reaches the self-control roll its row prints`() {
        val traits = PurchasableTraits.instancedTraits(
            listOf(
                InstancedPurchase("Obsession", instance = "The lost manuscript", cost = 5),
                InstancedPurchase(
                    "Compulsion: Collection (pathway)",
                    instance = "Mirrors",
                    cost = 5,
                ),
            ),
        )
        val checks = SelfControl.checksFor(traits)
        assertEquals(2, checks.size)
    }

    /**
     * The builder told a player the language row "prices itself by a
     * procedure this app has not read". [Languages.proficiencies] has held
     * that table since 6x. The reason that is true is the plain one: buying
     * a language is not built. A first rewording blamed two halvings the
     * book never combines in an example, and book 638-639 states both.
     */
    @Test
    fun `the language row is not withheld for a table this app holds`() {
        val language = PurchasableTraits.unoffered().single { "Language (specify)" in it.names }
        assertFalse("has not read" in language.reason, language.reason)
        // It is sold, by the builder's own Languages row, and says where.
        assertTrue("Languages row" in language.reason, language.reason)
        assertFalse("not built" in language.reason, language.reason)
        // Reputation prints no table at all, and is not filed under this one.
        assertEquals(listOf("Language (specify)"), language.names)
        val reputation = PurchasableTraits.unoffered().single { "Reputation" in it.names }
        // "varies", and nothing else: the book prints no procedure, so there
        // is none for this app to have left unread.
        assertFalse("has not read" in reputation.reason, reputation.reason)
        assertTrue("\"varies\"" in reputation.reason, reputation.reason)
    }
}
