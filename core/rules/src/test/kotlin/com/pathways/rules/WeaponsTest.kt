package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.SkillKey
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WeaponsTest {

    private fun gun(specialization: String) = SkillKey(
        name = "Guns",
        specialization = specialization,
        attr = ControllingAttribute.Buyable(Attr.DX),
        difficulty = Difficulty.EASY,
    )

    private val knifeSkill = SkillKey(
        name = "Knife",
        attr = ControllingAttribute.Buyable(Attr.DX),
        difficulty = Difficulty.EASY,
    )

    @Test
    fun `the book prints nine weapons`() {
        // The handover doc said four times over that it printed none. It does;
        // the table was in chapter 8, which nothing had read.
        assertEquals(9, Weapons.all().size)
        assertEquals("Revolver (.38 cal)", Weapons.all().first().name)
        assertEquals("Broadsword", Weapons.all().last().name)
    }

    @Test
    fun `a melee weapon rolls off the ST table and a firearm does not`() {
        val axe = Weapons.named("Hand Axe")
        assertEquals(
            Damage.swing(10).copy(modifier = 1).render(),
            Weapons.damageFor(axe, axe.damage.single(), st = 10).dice.render(),
        )
        assertEquals(
            Damage.swing(14).copy(modifier = Damage.swing(14).modifier + 1).render(),
            Weapons.damageFor(axe, axe.damage.single(), st = 14).dice.render(),
        )

        val revolver = Weapons.named("Revolver (.38 cal)")
        val mode = revolver.damage.single()
        // A bullet does not care how strong the hand holding the gun is.
        assertEquals("2d", Weapons.damageFor(revolver, mode, st = 8).dice.render())
        assertEquals("2d", Weapons.damageFor(revolver, mode, st = 20).dice.render())
    }

    @Test
    fun `the two melee weapons that cut or thrust keep both modes`() {
        // The damage cell wraps: "sw-1 cut / thr" then "imp". Reading the
        // first mode only would drop half of what the knife can do.
        val knife = Weapons.named("Combat Knife")
        assertEquals(listOf("sw-1 cut", "thr imp"), knife.damage.map { it.printed })
        assertEquals(listOf("cut", "imp"), knife.damage.map { it.type })
        assertEquals(2, Weapons.named("Broadsword").damage.size)
    }

    @Test
    fun `All-Out Attack Strong's plus two reaches a weapon`() {
        // The book scopes Determined ("+4 to hit melee, +1 to hit ranged") and
        // deliberately does not scope Strong ("+2 to damage"), so it lands on
        // whatever is swung or fired.
        val axe = Weapons.named("Hand Axe")
        val plain = Weapons.damageFor(axe, axe.damage.single(), st = 10)
        val strong = Weapons.damageFor(axe, axe.damage.single(), st = 10, bonus = 2)
        assertEquals(plain.dice.modifier + 2, strong.dice.modifier)
    }

    @Test
    fun `a firearm cannot parry and a melee weapon parries at half skill plus three`() {
        // The table prints no parry column, so the only signal it gives is the
        // Range cell: a reach letter is a weapon in the hand, metres are a gun.
        assertTrue(Weapons.canParry(Weapons.named("Broadsword")))
        assertFalse(Weapons.canParry(Weapons.named("Bolt-Action Rifle")))
        assertEquals(9, Weapons.parryFor(Weapons.named("Broadsword"), skillLevel = 12))
        assertNull(Weapons.parryFor(Weapons.named("Bolt-Action Rifle"), skillLevel = 12))
    }

    @Test
    fun `parry floors the halving rather than truncating it`() {
        // Math.floorDiv, never "/" -- the defect class that recurred three
        // times in this codebase.
        assertEquals(8, Weapons.parryFor(Weapons.named("Broadsword"), skillLevel = 11))
        assertEquals(1, Weapons.parryFor(Weapons.named("Broadsword"), skillLevel = -3))
    }

    @Test
    fun `a Guns specialization matches its own weapons and no others`() {
        // Resolving by name alone would arm a revolver shooter with a rifle:
        // the sheets print "Guns (Revolver)" and the table names the same.
        assertEquals(
            listOf("Revolver (.38 cal)", "Revolver (.44 cal)"),
            Weapons.forSkill(gun("Revolver")).map { it.name },
        )
        assertEquals(
            listOf("Bolt-Action Rifle", "Hunting Rifle"),
            Weapons.forSkill(gun("Rifle")).map { it.name },
        )
        assertEquals(listOf("Combat Knife"), Weapons.forSkill(knifeSkill).map { it.name })
        assertTrue(Weapons.forSkill(gun("Pistol")).isEmpty())
    }

    @Test
    fun `every weapon names a skill the book actually prints`() {
        val printed = ContentLoader.skills()
            .flatMap { listOfNotNull(it.name, it.specialization?.let { s -> "${it.name} ($s)" }) }
            .toSet()
        Weapons.all().forEach {
            assertTrue(it.skill in printed, "${it.name} names an unprinted skill '${it.skill}'")
        }
    }

    @Test
    fun `the rifle's damage type is priced by the appendix, not by chapter 5`() {
        // CORRECTION. This test used to assert the lookup THREW, on the
        // reading that chapter 5's silence was the whole book's. Appendix A
        // prints "Pi+ x1.5" (p. 159) and sat at 0.0% coverage, so nothing had
        // read it -- the same failure chapter 8's weapon table produced, one
        // appendix over. Chapter 5 is still unrepaired: it supplies the
        // multiplier, the appendix supplies the label.
        val rifle = Weapons.named("Bolt-Action Rifle")
        assertEquals("pi+", rifle.damage.single().type)
        assertEquals(1.5, Wounds.typeMultiplier("pi+"))
        assertTrue(Weapons.hasWoundingMultiplier(rifle.damage.single()))
        assertTrue(Weapons.hasWoundingMultiplier(Weapons.named("Hunting Rifle").damage.single()))
        // And the number is chapter 5's own, printed against a row it left
        // unlabelled. Adopting the label adds no multiplier to the book.
        assertEquals(
            1.5,
            Wounds.damageTypes().single { it.type == "Large Piercing" }.multiplier,
        )
    }

    @Test
    fun `the two chapters disagree about which rifle is large piercing`() {
        // Chapter 5's Large Piercing row gives "Hunting rifles" as its example
        // while chapter 8 prints the Hunting Rifle as plain pi and gives pi+
        // to the Bolt-Action. Neither is corrected.
        assertEquals("pi", Weapons.named("Hunting Rifle").damage.single().type)
        assertTrue(
            Wounds.damageTypes().single { it.type == "Large Piercing" }
                .examples.contains("Hunting rifles"),
        )
    }

    @Test
    fun `an unknown weapon is refused rather than defaulted`() {
        assertThrows<IllegalArgumentException> { Weapons.named("Steam-Pressure Gun") }
    }

    @Test
    fun `only a firearm carries a range in metres`() {
        assertEquals(150, Weapons.named("Revolver (.38 cal)").halfRange)
        assertEquals(1800, Weapons.named("Revolver (.38 cal)").maxRange)
        // "C/1" is reach, not one metre: bandFor would call it point blank and
        // apply a distance penalty to a sword.
        assertNull(Weapons.named("Combat Knife").halfRange)
        assertEquals("C/1", Weapons.named("Combat Knife").range)
    }

    @Test
    fun `Aim is worth the weapon's Acc, which the book now prints`() {
        assertEquals(4, Weapons.named("Bolt-Action Rifle").acc)
        assertEquals(0, Weapons.named("Broadsword").acc)
    }
}
