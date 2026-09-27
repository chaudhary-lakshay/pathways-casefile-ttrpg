package com.pathways.app

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.SkillKey
import com.pathways.model.Weapon
import com.pathways.rules.DefenseBoost
import com.pathways.rules.Weapons
import com.pathways.rules.Facing
import com.pathways.rules.Shields
import com.pathways.rules.UnarmedStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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

    // Chapter 8 arms these three by the skill NAME it prints in the table.
    private val broadswordKey =
        SkillKey("Broadsword", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.AVERAGE)
    private val revolverKey =
        SkillKey("Guns", "Revolver", ControllingAttribute.Buyable(Attr.DX), Difficulty.EASY)

    /**
     * p. 36: "Each can be used once per round unless stated otherwise", and
     * the line above it: "Each type may be used multiple times per round, but
     * Parry accumulates a -4 cumulative penalty after the first". Both name a
     * round, and every value they govern is now local to this one screen --
     * which is why the boundary needs no global state, and why the reason
     * recorded in 6bf/6co (a latch on one control would claim a boundary the
     * other controls cannot honour) has stopped being true.
     *
     * The membership assertion is the guard: a value added to [RoundState] is
     * a value a round boundary CLEARS, so `stunned` must never appear here.
     * The book gives stun its own clock -- "Stunned for 1d6 seconds (roll HT
     * each turn to recover early)" (line 2826) -- so a round boundary is not
     * what ends it. The shield carried, the facing and what is coming in are
     * not per-round facts at all, which is a different reason again.
     */
    @Test
    fun `a round boundary clears these values and no others`() {
        val cleared = RoundState::class.java.declaredFields
            .map { it.name }
            .filterNot { it.contains("$") }
            .toSet()
        assertEquals(
            setOf("declared", "acrobatic", "dived", "retreated", "priorParries", "boost"),
            cleared,
        )
    }

    @Test
    fun `a new round restores every per-round value to its default`() {
        val spent = RoundState(
            declared = setOf(com.pathways.rules.DefenseRefusal.SURPRISE),
            dived = true,
            retreated = true,
            priorParries = 3,
            boost = DefenseBoost.entries.first() to "All-Out Defense (Increased)",
        )
        assertEquals(RoundState(), spent.newRound())
    }

    @Test
    fun `a fresh round has nothing to end, and any one value ends that`() {
        assertFalse(RoundState().isSpent())
        assertTrue(RoundState(dived = true).isSpent())
        assertTrue(RoundState(priorParries = 1).isSpent())
        assertTrue(RoundState(declared = setOf(com.pathways.rules.DefenseRefusal.SURPRISE)).isSpent())
    }

    @Test
    fun `a carried weapon parries at half its skill plus three`() {
        val parries = armedParries(
            weapons = listOf(Weapons.named("Broadsword")),
            skillLevels = mapOf(broadswordKey to 13),
        )
        assertEquals(listOf("Broadsword" to 9), parries) // floor(13/2) + 3
    }

    @Test
    fun `a weapon that cannot parry is not listed at all`() {
        // Chapter 8's Range column prints metres for a firearm, and nothing
        // in this book is both fired and parried. Refusing is not parrying
        // at +0.
        val parries = armedParries(
            weapons = listOf(Weapons.named("Revolver (.38 cal)")),
            skillLevels = mapOf(revolverKey to 12),
        )
        assertTrue(parries.isEmpty())
    }

    @Test
    fun `a carried weapon whose skill the character lacks is skipped`() {
        // The formula is skill/2 + 3, and there is no skill to halve. The
        // weapon stays CARRIED -- a borrowed broadsword is still in hand.
        val parries = armedParries(
            weapons = listOf(Weapons.named("Broadsword")),
            skillLevels = emptyMap(),
        )
        assertTrue(parries.isEmpty())
    }

    @Test
    fun `two carried weapons give two parries`() {
        val parries = armedParries(
            weapons = listOf(Weapons.named("Broadsword"), Weapons.named("Combat Knife")),
            skillLevels = mapOf(broadswordKey to 12, knifeKey to 11),
        )
        assertEquals(listOf("Broadsword" to 9, "Combat Knife" to 8), parries)
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
     * The shield's DB is withheld from behind, on every row that shows one.
     *
     * The Dodge and Parry rows read it through `shielded()` and the Block row
     * builds its arrow by hand, so this is the one answer both consume. A
     * design review caught that gating only the shared helper would have left
     * the bonus standing on Block — reachable by any character with the Shield
     * skill and a shield Readied, not an edge case.
     *
     * **Null here is not "no shield Readied."** At the rear a carried shield
     * also answers null, and only the carried value tells the two apart, which
     * is why the Block row's own note branches on `carried` rather than on
     * this.
     */
    @Test
    fun `the shield's DB is withheld from behind and offered from the front and side`() {
        val shield = Shields.named("Medium")
        assertEquals(shield, shieldForDb(shield, Facing.FRONT))
        assertEquals(shield, shieldForDb(shield, Facing.SIDE))
        assertNull(shieldForDb(shield, Facing.REAR))
        // Nothing Readied answers null from every direction, which is the
        // other way to reach the same branch.
        for (facing in Facing.entries) {
            assertNull(shieldForDb(null, facing))
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
