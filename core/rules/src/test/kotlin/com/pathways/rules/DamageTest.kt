package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.UnarmedAttack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unarmed damage: the ST table's first reader.
 *
 * The thrust/swing table has been loaded since M0 with no production caller,
 * so these are also the first tests that pin it to a rule rather than to
 * itself.
 */
class DamageTest {

    private fun attack(name: String) =
        ContentLoader.unarmedAttacks().first { it.attack == name }

    @Test
    fun `a punch is thrust minus one`() {
        // ST 10 thrusts 1d-2, so a punch is 1d-3 cr.
        assertEquals("1d-2", Damage.thrust(10).render())
        assertEquals("1d-3 cr", Damage.forAttack(attack("Punch"), st = 10)!!.render())
    }

    @Test
    fun `a kick is full thrust and an uppercut is thrust plus two`() {
        assertEquals("1d-2 cr", Damage.forAttack(attack("Kick"), st = 10)!!.render())
        assertEquals("1d cr", Damage.forAttack(attack("Uppercut"), st = 10)!!.render())
    }

    @Test
    fun `a stronger arm hits harder`() {
        // The modifier is the attack's; the dice come from ST. ST 14 thrusts
        // 1d and ST 20 thrusts 2d-1, so a punch is one less than each.
        assertEquals("1d-1 cr", Damage.forAttack(attack("Punch"), st = 14)!!.render())
        assertEquals("2d-2 cr", Damage.forAttack(attack("Punch"), st = 20)!!.render())
    }

    @Test
    fun `a shove rolls no damage at all`() {
        // "Special": it knocks down. Returning 0 would be a claim the book
        // does not make, and an app that printed "1d-4 cr" for it would lie.
        val shove = attack("Shove")
        assertTrue(!shove.wounds)
        assertNull(Damage.forAttack(shove, st = 10))
    }

    @Test
    fun `boxing's bonus counts levels above DX, not DX itself`() {
        // "+1 at DX+1; +2 at DX+2 (max +2 without Trained by a Master)".
        assertEquals(0, Damage.boxingBonus(dx = 12, boxingLevel = 12))
        assertEquals(1, Damage.boxingBonus(dx = 10, boxingLevel = 11))
        assertEquals(2, Damage.boxingBonus(dx = 10, boxingLevel = 12))
        assertEquals(2, Damage.boxingBonus(dx = 10, boxingLevel = 16))
        // A boxer worse than their own DX is not penalised, only unrewarded.
        assertEquals(0, Damage.boxingBonus(dx = 12, boxingLevel = 9))
    }

    @Test
    fun `the boxing bonus reaches the damage`() {
        assertEquals("1d-2 cr", Damage.forAttack(attack("Jab"), st = 10)!!.render())
        assertEquals("1d cr", Damage.forAttack(attack("Jab"), st = 10, bonus = 2)!!.render())
    }

    @Test
    fun `damage below zero is not floored to one`() {
        // ST 1 thrusts 1d-6, so an elbow is 1d-8. This book prints no
        // minimum-damage rule and inventing one would strengthen the weak.
        assertEquals("1d-8 cr", Damage.forAttack(attack("Elbow"), st = 1)!!.render())
    }

    @Test
    fun `an ST off the end of the table fails loudly`() {
        val ex = assertThrows<IllegalStateException> { Damage.thrust(99) }
        assertTrue(ex.message!!.contains("no row for ST 99"))
    }

    @Test
    fun `a negative damage bonus is refused`() {
        assertThrows<IllegalArgumentException> {
            Damage.forAttack(attack("Jab"), st = 10, bonus = -1)
        }
    }

    @Test
    fun `wrestling has no attack rows, brawling and boxing do`() {
        // The book's own summary: "None (grapple only)". Inventing a row for
        // it would invent damage.
        assertTrue(Damage.attacksFor("Wrestling").isEmpty())
        assertEquals(6, Damage.attacksFor("Brawling").size)
        assertEquals(4, Damage.attacksFor("Boxing").size)
    }

    @Test
    fun `every listed unarmed attack is crushing thrust`() {
        // Damage applies no wounding multiplier, which is only safe while
        // every one of these is cr. If a cut or imp row appears, that is the
        // finding.
        val rows = ContentLoader.unarmedAttacks().filter { it.wounds }
        assertEquals(setOf("thr"), rows.map { it.base }.toSet())
        assertEquals(setOf("cr"), rows.map { it.type }.toSet())
    }

    @Test
    fun `an unreadable base fails rather than guessing`() {
        val bogus = UnarmedAttack(
            skill = "Brawling",
            attack = "Bite",
            rollModifier = 0,
            damage = "kick-1 cr",
            notes = "not in the book",
            base = "kick",
            modifier = -1,
            type = "cr",
        )
        assertThrows<IllegalStateException> { Damage.forAttack(bogus, st = 10) }
    }
}
