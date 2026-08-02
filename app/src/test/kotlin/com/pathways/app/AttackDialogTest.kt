package com.pathways.app

import com.pathways.rules.Weapons
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AttackDialogTest {

    @Test
    fun `a firearm is fired at range and a melee weapon is not`() {
        // REGRESSION (Codex): picking a Guns skill armed the character with a
        // revolver but left the plan in reach, so an immediate roll took
        // All-Out Determined's MELEE +4 instead of its ranged +1 and the
        // distance and Aim rows never appeared at all.
        assertTrue(Armed(Weapons.named("Revolver (.38 cal)")).isFired)
        assertTrue(Armed(Weapons.named("Bolt-Action Rifle")).isFired)
        assertFalse(Armed(Weapons.named("Broadsword")).isFired)
        assertFalse(Armed(Weapons.named("Combat Knife")).isFired)
    }

    @Test
    fun `an empty hand is not a ranged attack`() {
        // The nullable receiver is the whole point: putting a weapon down has
        // to answer this question too, and this book prints no thrown weapon.
        val empty: Armed? = null
        assertFalse(empty.isFired)
    }

    @Test
    fun `only a fired weapon lends its Acc to an Aim`() {
        // REGRESSION (Codex): the Aim chips were gated on merely HOLDING a
        // weapon, and the "At range" toggle stays the player's -- so a Hand
        // Axe, which prints Acc 1, could be aimed for +1, or +3 across three
        // turns. Aiming is something you do with a gun.
        assertEquals(4, Armed(Weapons.named("Bolt-Action Rifle")).aimAcc)
        assertEquals(2, Armed(Weapons.named("Revolver (.38 cal)")).aimAcc)
        assertNull(Armed(Weapons.named("Hand Axe")).aimAcc)
        assertNull(Armed(Weapons.named("Combat Knife")).aimAcc)
        assertNull((null as Armed?).aimAcc)
    }

    @Test
    fun `the hand axe really does print an Acc, which is why the gate matters`() {
        // If it were 0 the defect would have been invisible: Ranged.modifiers
        // drops a zero rather than adding one.
        assertEquals(1, Weapons.named("Hand Axe").acc)
    }

    @Test
    fun `a weapon opens on the first attack the book prints`() {
        // The Combat Knife cuts before it thrusts, matching how an unarmed
        // skill opens on its first listed attack rather than on nothing.
        val knife = Armed(Weapons.named("Combat Knife"))
        assertEquals("sw-1 cut", knife.mode.printed)
        assertEquals(2, knife.weapon.damage.size)
    }
}
