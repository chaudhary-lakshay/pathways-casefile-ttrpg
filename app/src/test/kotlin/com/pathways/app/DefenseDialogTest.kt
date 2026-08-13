package com.pathways.app

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.SkillKey
import com.pathways.model.Weapon
import com.pathways.rules.DefenseBoost
import com.pathways.rules.UnarmedStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pairing weapons with the skills that wield them.
 *
 * No premade carries a weapon, so none of this is on screen yet. It exists
 * because the model does carry them, and a dialog that silently skipped an
 * armed character's parry would be wrong the first time one appeared (Codex).
 */
class DefenseDialogTest {

    private val swordKey = SkillKey("Shortsword", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.AVERAGE)
    private val knifeKey = SkillKey("Knife", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.EASY)

    @Test
    fun `a weapon parries at half its skill plus three`() {
        val parries = armedParries(
            weapons = listOf(Weapon("Cane sword", swordKey, parryMod = 0)),
            skillLevels = mapOf(swordKey to 13),
        )
        assertEquals(listOf("Cane sword" to 9), parries) // floor(13/2) + 3 + 0
    }

    @Test
    fun `a weapon's own modifier moves its parry`() {
        val parries = armedParries(
            weapons = listOf(Weapon("Fencing blade", swordKey, parryMod = 1)),
            skillLevels = mapOf(swordKey to 13),
        )
        assertEquals(listOf("Fencing blade" to 10), parries)
    }

    @Test
    fun `a weapon that cannot parry is not listed at all`() {
        // The sheets print a dash there, which is not the same as +0.
        val parries = armedParries(
            weapons = listOf(Weapon("Revolver", knifeKey, parryMod = null)),
            skillLevels = mapOf(knifeKey to 12),
        )
        assertTrue(parries.isEmpty())
    }

    @Test
    fun `a weapon whose skill the character lacks is skipped`() {
        // The formula is skill/2 + 3, and there is no skill to halve.
        val parries = armedParries(
            weapons = listOf(Weapon("Borrowed sabre", swordKey, parryMod = 0)),
            skillLevels = emptyMap(),
        )
        assertTrue(parries.isEmpty())
    }

    @Test
    fun `two weapons give two parries`() {
        val parries = armedParries(
            weapons = listOf(
                Weapon("Shortsword", swordKey, parryMod = 0),
                Weapon("Knife", knifeKey, parryMod = 0),
            ),
            skillLevels = mapOf(swordKey to 12, knifeKey to 11),
        )
        assertEquals(listOf("Shortsword" to 9, "Knife" to 8), parries)
    }

    /**
     * **"+2 to ONE defence" reaches the named row and nothing else.**
     *
     * Combat Reflexes says "+1 to ALL active defenses" and a shield's DB
     * "adds to all active defenses", so both go through one function every
     * row calls. These two rules deliberately do not: applied the same way,
     * 1 FP would buy +2 on Dodge, every Parry and Block at once.
     *
     * A reading, so it is pinned rather than left to be rediscovered by
     * whoever next edits the rows that render it.
     */
    @Test
    fun `a bought boost lands on the named row alone`() {
        val bought = DefenseBoost.FEVERISH to "Parry (Brawling)"
        assertEquals(2, boostOn("Parry (Brawling)", bought))
        assertEquals(0, boostOn("Dodge", bought))
        assertEquals(0, boostOn("Parry (Wrestling)", bought))
        assertEquals(0, boostOn("Block", bought))
        // Nothing bought is the ordinary case and moves no row at all.
        for (row in listOf("Dodge", "Parry (Brawling)", "Block")) {
            assertEquals(0, boostOn(row, null))
        }
    }

    /**
     * The rows a boost may be spent on are exactly the ones rendered.
     *
     * A parry withdrawn against a shot must not be purchasable either, or a
     * GM could pay FP for a defence the book has already refused.
     */
    @Test
    fun `only the defences on screen can be paid for`() {
        val armed = listOf("Shortsword" to 9)
        val unarmed = listOf(Triple("Brawling", UnarmedStyle.BRAWLING, 9))
        assertEquals(
            listOf("Dodge", "Parry (Shortsword)", "Parry (Brawling)", "Block"),
            defenceLabels(dodge = true, armed, unarmed, block = true),
        )
        // Against a shot the unarmed rows are withdrawn upstream, so they are
        // not offered here -- and a character with no shield skill has no
        // Block row to buy.
        assertEquals(
            listOf("Dodge", "Parry (Shortsword)"),
            defenceLabels(dodge = true, armed, emptyList(), block = false),
        )
    }
}
