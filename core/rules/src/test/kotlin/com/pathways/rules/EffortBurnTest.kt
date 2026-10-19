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
import org.junit.jupiter.api.assertThrows

/**
 * Ch. 2's burn (v6.9i, book lines 459-468): FP or SPI spent BEFORE a roll,
 * +1 per point, no cap, and only in combat unless the character is Very Fit.
 */
class EffortBurnTest {

    private fun key(attr: ControllingAttribute, name: String = "Whatever") =
        SkillKey(name, null, attr, Difficulty.AVERAGE)

    private fun held(vararg names: String) = names.map { Trait(it, 0, TraitKind.ADVANTAGE) }

    @Test
    fun `the book prints two pools at one point each and no cap`() {
        assertEquals(listOf("FP", "SPI"), EffortBurn.all().map { it.pool })
        // v6.9d's "Max +5 per roll" is gone: a large spend buys its size.
        assertEquals(12, EffortBurn.bonusFor(BurnKind.PHYSICAL, 12))
        assertEquals(40, EffortBurn.bonusFor(BurnKind.MYSTICAL, 40))
        assertThrows<IllegalArgumentException> { EffortBurn.bonusFor(BurnKind.PHYSICAL, -1) }
    }

    /**
     * ST, DX and HT are the FP row's own list; SPI-based rolls are the SPI
     * row's. IQ, Will and Per appear in neither, so they get NULL rather than
     * a default.
     */
    @Test
    fun `the mental attributes fall in the gap and are refused`() {
        for (attr in listOf(Attr.ST, Attr.DX, Attr.HT)) {
            assertEquals(BurnKind.PHYSICAL, EffortBurn.kindFor(key(ControllingAttribute.Buyable(attr))))
        }
        assertEquals(BurnKind.MYSTICAL, EffortBurn.kindFor(key(ControllingAttribute.Spi)))
        assertNull(EffortBurn.kindFor(key(ControllingAttribute.Buyable(Attr.IQ))))
        assertNull(EffortBurn.kindFor(key(ControllingAttribute.Will)))
        assertNull(EffortBurn.kindFor(key(ControllingAttribute.Per)))
    }

    /**
     * "Supernatural skill" is read off ch. 3's "Spiritual Skills in Detail"
     * (2404-2410), which lists exactly two: Ritualistic Magic and Divination
     * Arts. Both are IQ skills, so the SPI-based half alone reached neither.
     */
    @Test
    fun `the two spiritual skills take the mystical burn`() {
        assertEquals(setOf(Conditions.RITUAL_SKILL, "Divination Arts"), EffortBurn.SUPERNATURAL_SKILLS)
        for (name in EffortBurn.SUPERNATURAL_SKILLS) {
            assertEquals(BurnKind.MYSTICAL, EffortBurn.kindFor(key(ControllingAttribute.Buyable(Attr.IQ), name)))
        }
        assertEquals(
            "SPIRITUAL SKILLS",
            ContentLoader.skills().single { it.name == "Divination Arts" }.category,
        )
    }

    /**
     * v6.9d named "Occultism vs. Beyonder phenomena" in the SPI row and the
     * app reported it to the GM. v6.9i prints no such example, so Occultism
     * is an ordinary IQ skill and takes no burn.
     */
    @Test
    fun `Occultism is no longer named and takes no burn`() {
        val occultism = key(ControllingAttribute.Buyable(Attr.IQ), "Occultism")
        assertNull(EffortBurn.kindFor(occultism))
        assertTrue("Occultism" !in EffortBurn.pool(BurnKind.MYSTICAL).scope)
    }

    /**
     * "You may only burn FP during combat if you have Fit or Very Fit; you may
     * only burn FP out of combat if you have Very Fit." No Fit: no FP burn.
     */
    @Test
    fun `FP burn needs Fit in combat and Very Fit outside it`() {
        assertNull(EffortBurn.whenAllowed(BurnKind.PHYSICAL, held()))
        assertEquals(BurnWhen.IN_COMBAT, EffortBurn.whenAllowed(BurnKind.PHYSICAL, held("Fit")))
        assertEquals(BurnWhen.ALWAYS, EffortBurn.whenAllowed(BurnKind.PHYSICAL, held("Very Fit")))
        assertEquals(BurnWhen.ALWAYS, EffortBurn.whenAllowed(BurnKind.PHYSICAL, held("Fit", "Very Fit")))
    }

    /** "You may only burn SPI during combat." No trait is asked for. */
    @Test
    fun `SPI burn is combat only for everyone`() {
        assertEquals(BurnWhen.IN_COMBAT, EffortBurn.whenAllowed(BurnKind.MYSTICAL, held()))
        assertEquals(BurnWhen.IN_COMBAT, EffortBurn.whenAllowed(BurnKind.MYSTICAL, held("Very Fit")))
    }

    /**
     * "You must declare this before the roll." The burn is a modifier on the
     * target the dice are read against, so it reaches the ledger like any
     * other line, and zero points adds no line.
     */
    @Test
    fun `a declared burn is a flat modifier on the roll`() {
        assertNull(EffortBurn.modifier(BurnKind.PHYSICAL, 0))
        val mod = EffortBurn.modifier(BurnKind.PHYSICAL, 3)!!
        assertEquals(ModEffect.Flat(3), mod.effect)
        assertEquals(EffortBurn.idFor(BurnKind.PHYSICAL), mod.id)
        assertEquals("Burned FP", mod.source.label())
        assertEquals("Burned SPI", EffortBurn.modifier(BurnKind.MYSTICAL, 2)!!.source.label())
    }

    @Test
    fun `the burn raises the target before the dice are thrown`() {
        val dx = key(ControllingAttribute.Buyable(Attr.DX))
        val target = SkillRoll.targetFor(
            label = "Stealth",
            base = 10,
            key = dx,
            mods = listOfNotNull(EffortBurn.modifier(BurnKind.PHYSICAL, 4)),
        )
        assertEquals(14, target.target)
    }

    /**
     * "You cannot spend both FP and SPI on the same roll." Refused rather
     * than silently preferring one.
     */
    @Test
    fun `both pools on one roll is refused`() {
        EffortBurn.requireOnePool(fp = 3, spi = 0)
        EffortBurn.requireOnePool(fp = 0, spi = 3)
        EffortBurn.requireOnePool(fp = 0, spi = 0)
        val thrown = assertThrows<IllegalArgumentException> {
            EffortBurn.requireOnePool(fp = 1, spi = 1)
        }
        assertTrue(thrown.message!!.contains("not both"), thrown.message)
    }

    @Test
    fun `chapter 10's own burn rate matches this one`() {
        assertEquals(Divination.BURN_PER_SPI, EffortBurn.pool(BurnKind.MYSTICAL).perPoint)
    }
}
