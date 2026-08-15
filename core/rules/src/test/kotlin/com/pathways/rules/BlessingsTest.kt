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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Seven Orthodox Gods Blessings (Ch. 3, p. 19).
 *
 * Three slices deferred this table and a fourth extracted it, on the strength
 * of the deferral's own reason having expired: 6bc left it because no premade
 * prints a blessing and traits were not editable, and 6ca/6cc made traits
 * purchasable. What is pinned here is the ONE join the table supports, both
 * halves of the evidence for it, and the six rows that reach nothing.
 */
class BlessingsTest {

    private fun blessing(deity: String) = Trait(deity, 75, TraitKind.ADVANTAGE)

    private fun skill(name: String, attr: ControllingAttribute = ControllingAttribute.Buyable(Attr.IQ)) =
        SkillKey(name, null, attr, Difficulty.AVERAGE)

    /**
     * **The warrant, in the half Kotlin can check.**
     *
     * `extract_blessings.verify()` owns the other half -- that "occult skill"
     * is printed nowhere in this book, so the slash in "occult/knowledge
     * skills" cannot be joining two groups. This is the consequence of that
     * inside the content: every occult skill the book prints is already under
     * the KNOWLEDGE banner, so reading the phrase as that banner loses
     * nothing, and there is no second group for "occult" to name.
     *
     * Astrology is asserted OUTSIDE it deliberately. It is the one
     * occult-flavoured skill the book files elsewhere (SURVIVAL & OTHER), and
     * it is the whole reason the wider reading is refused: including it would
     * be my judgement about what counts as occult, not the book's.
     */
    @Test
    fun `every occult skill the book prints is under the KNOWLEDGE banner`() {
        val byName = ContentLoader.skills().associateBy { it.name }
        for (occult in listOf("Occultism", "Hidden Lore", "Thaumatology", "Theology", "Alchemy")) {
            assertEquals(
                TraitEffects.KNOWLEDGE_SKILLS,
                byName.getValue(occult).category,
                "$occult is no longer a KNOWLEDGE skill, so the reading of " +
                    "\"occult/knowledge skills\" has to be made again",
            )
        }
        assertEquals(
            "SURVIVAL & OTHER",
            byName.getValue("Astrology").category,
            "Astrology moved, which is the counter-example the narrow reading rests on",
        )
    }

    /** The applied join: +5 on a KNOWLEDGE skill and nothing anywhere else. */
    @Test
    fun `the God of Knowledge lifts a KNOWLEDGE skill and no other category`() {
        val held = listOf(blessing(TraitEffects.GOD_OF_KNOWLEDGE))
        val occultism = TraitEffects.modifiers(held, skill("Occultism")).single()
        assertEquals(ModEffect.Flat(5), occultism.effect)

        // A social skill and a combat skill, on the same character.
        assertTrue(TraitEffects.modifiers(held, skill("Diplomacy")).isEmpty())
        assertTrue(
            TraitEffects.modifiers(
                held,
                skill("Brawling", ControllingAttribute.Buyable(Attr.DX)),
            ).isEmpty(),
        )
        // ...and Astrology, which is the refusal of the WIDER reading on a
        // real skill rather than in a comment.
        assertTrue(
            TraitEffects.modifiers(held, skill("Astrology")).isEmpty(),
            "the bonus reached a skill outside the banner the cell names",
        )
    }

    /**
     * **Every row the book prints is applied or reported, and none is both.**
     *
     * A blessing whose cell reaches nothing and says nothing is worse than one
     * the app never offered: the sheet then shows the cell as understood
     * (6bf). Six of the seven land here.
     */
    @Test
    fun `six blessings are reported with a reason and the seventh is applied`() {
        val deities = ContentLoader.blessings().map { it.deity }
        assertEquals(7, deities.size)

        val applied = TraitEffects.categoryBonusNames()
        val reported = TraitEffects.unappliedNames()
        for (deity in deities) {
            assertTrue(
                deity in applied || deity in reported,
                "$deity reaches nothing and says nothing",
            )
            assertFalse(deity in applied && deity in reported, "$deity is applied and disclaimed")
        }
        assertEquals(1, deities.count { it in applied })

        // The reasons are real text, and each is reachable from a sheet.
        for (deity in deities.filter { it in reported }) {
            val note = TraitEffects.reported(listOf(blessing(deity))).single()
            assertEquals(deity, note.trait)
            assertTrue(note.reason.isNotBlank())
            assertTrue(note.printed.isNotBlank(), "$deity renders no printed cell")
        }
    }

    /**
     * **God of Combat's refusal is about this app's shape, not the book's
     * silence, and says so.**
     *
     * The number is printed, real, and unambiguous. What is missing is that a
     * granted attribute is never written into a billable field (M3) and no
     * trait re-derives one -- only a potion does. A reason claiming the book
     * prints nothing would be the fresh excuse 6bo caught one commit from
     * shipping.
     */
    @Test
    fun `the God of Combat names the plumbing rather than a missing rule`() {
        val held = listOf(blessing(TraitEffects.GOD_OF_COMBAT))
        val note = TraitEffects.reported(held).single()
        assertTrue(note.printed.contains("+3 ST, +3 DX, +3 HT"), "the cell is quoted whole")
        assertTrue(
            note.reason.contains("attribute"),
            "the reason has to name what is missing: ${note.reason}",
        )
        // And the modifier path really does leave it alone, on every attribute
        // the cell names -- a report beside a live bonus would be the
        // screen-disagrees-with-itself defect (6ai).
        for (attr in listOf(Attr.ST, Attr.DX, Attr.HT)) {
            assertTrue(
                TraitEffects.modifiers(
                    held,
                    skill("Whatever", ControllingAttribute.Buyable(attr)),
                ).isEmpty(),
            )
        }
    }

    /**
     * All seven are buyable at the one price the book prints for them.
     *
     * The price is stated once in prose, for all seven rows, and the table
     * prints no cost column -- which is exactly why `extract_content.py`'s
     * cost-line parser has never been able to see this table at all.
     */
    @Test
    fun `every blessing is offered at the shared printed price`() {
        val offered = PurchasableTraits.offered().associateBy { it.name }
        for (row in ContentLoader.blessings()) {
            val bought = offered[row.deity]
            assertNotNull(bought, "${row.deity} is printed and cannot be bought")
            assertEquals(ContentLoader.blessingCost(), bought!!.cost)
            assertTrue(bought.advantage, "a blessing costs points, so it bills as an advantage")
            assertEquals(row.effect, bought.notes)
        }
        assertEquals(75, ContentLoader.blessingCost())
    }

    /**
     * **Two different blessings are allowed and a second copy of one is not.**
     *
     * Nothing in the book forbids holding two -- the requirement is "a
     * confirmed believer in the corresponding Church", which is per blessing,
     * and the prohibition sweep over the table's own lines finds no exclusion.
     * Refusing would author a rule, the opposite call to [Wealth]'s exclusion,
     * where the book does say a character has one economic status.
     *
     * The same blessing twice is still refused, for the reason every trait is:
     * [PointBudget] bills the second copy and [TraitEffects] reads one
     * canonical match, so it would charge 75 points and change nothing.
     */
    @Test
    fun `a second church is allowed and a second copy of one church is not`() {
        val combat = PurchasableTraits.named(TraitEffects.GOD_OF_COMBAT)!!
        val knowledge = PurchasableTraits.named(TraitEffects.GOD_OF_KNOWLEDGE)!!
        assertTrue(PurchasableTraits.canBuy(knowledge, listOf(blessing(TraitEffects.GOD_OF_COMBAT))))
        assertFalse(PurchasableTraits.canBuy(combat, listOf(blessing(TraitEffects.GOD_OF_COMBAT))))
    }

    /**
     * The requirement is committed and shown, never enforced.
     *
     * This app models no faith and no reconciliation, so whether a character
     * is a confirmed believer is the GM's to say -- the call the Sealed
     * Artifact grade table's Power Equivalent column needed (6af). A check
     * that would be wrong is worse than a sentence.
     */
    @Test
    fun `the printed requirement is available and gates nothing`() {
        val requirement = PurchasableTraits.blessingRequirement()
        assertTrue(requirement.contains("confirmed believer"))
        assertTrue(requirement.contains("suspends blessings"))
        // An unbeliever can still buy one: there is no faith to fail.
        assertTrue(PurchasableTraits.canBuy(PurchasableTraits.named("Earth Mother")!!, emptyList()))
    }
}
