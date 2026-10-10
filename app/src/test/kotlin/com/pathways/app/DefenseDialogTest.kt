package com.pathways.app

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.model.Weapon
import com.pathways.rules.DefenseBoost
import com.pathways.rules.Weapons
import com.pathways.rules.Facing
import com.pathways.rules.RandomSource
import com.pathways.rules.Shields
import com.pathways.rules.SkillDefaults
import com.pathways.rules.TraitEffects
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
            mastery = null,
            dx = 10,
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
            mastery = null,
            dx = 10,
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
            mastery = null,
            dx = 10,
        )
        assertTrue(parries.isEmpty())
    }

    /**
     * A master's weapon is "treated as known at effective DX" (6ey): DX is
     * the Weapon Skill operand, so the broadsword parries untrained and is no
     * longer named as carried without its skill. A firearm still parries at
     * nothing, mastery or not.
     */
    @Test
    fun `a master's carried weapon parries at DX without the skill`() {
        val carried = listOf(Weapons.named("Broadsword"), Weapons.named("Revolver (.38 cal)"))
        val warrior = SkillDefaults.WeaponMastery.WARRIOR
        assertEquals(
            listOf("Broadsword" to 9), // floor(12/2) + 3
            armedParries(carried, emptyMap(), mastery = warrior, dx = 12),
        )
        assertEquals(
            listOf("Broadsword" to 9),
            armedParries(carried, mapOf(broadswordKey to 10), mastery = warrior, dx = 12),
        )
        assertTrue(unskilledParriers(carried, emptyMap(), mastery = warrior, dx = 12).isEmpty())
    }

    /**
     * The builder's carried line is the third screen that prints a weapon's
     * parry (the diff review's MEDIUM): it said "no parry" for a Warrior's
     * broadsword that Defend parried at 9.
     */
    @Test
    fun `the builder prints the parry Defend rolls, mastery included`() {
        val broadsword = Weapons.named("Broadsword")
        val warrior = SkillDefaults.WeaponMastery.WARRIOR
        assertEquals("Broadsword 12 — parry 9", carriedLine(broadsword, emptyMap(), warrior, dx = 12))
        assertEquals("Broadsword 12 — parry 9", carriedLine(broadsword, mapOf(broadswordKey to 10), warrior, dx = 12))
        assertEquals(
            "Broadsword — not a skill this character has, so no parry",
            carriedLine(broadsword, emptyMap(), mastery = null, dx = 12),
        )
    }

    @Test
    fun `two carried weapons give two parries`() {
        val parries = armedParries(
            weapons = listOf(Weapons.named("Broadsword"), Weapons.named("Combat Knife")),
            skillLevels = mapOf(broadswordKey to 12, knifeKey to 11),
            mastery = null,
            dx = 10,
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

    private fun dxSkill(name: String, difficulty: Difficulty) =
        SkillKey(name, null, ControllingAttribute.Buyable(Attr.DX), difficulty)

    private val brawlingKey = dxSkill("Brawling", Difficulty.EASY)
    private val acrobaticsKey = dxSkill("Acrobatics", Difficulty.HARD)

    /**
     * p. 37's Parry (Unarmed) formula cell: "Brawling or DX ÷ 2 + 3". A sheet
     * with no unarmed skill was shown no parry row at all, and a note calling
     * that a gap in defaults. DX is an operand the cell prints.
     */
    @Test
    fun `a sheet with no unarmed skill parries bare-handed at DX`() {
        assertEquals(
            listOf(Triple(BARE_HANDS, UnarmedStyle.BARE_HANDS, 8)),
            unarmedParries(skillLevels = emptyMap(), dx = 10),
        )
    }

    @Test
    fun `a brawler at or above DX is not shown the DX row as well`() {
        assertEquals(
            listOf(Triple("Brawling", UnarmedStyle.BRAWLING, 9)),
            unarmedParries(mapOf(brawlingKey to 12), dx = 10),
        )
    }

    /**
     * "Or", not "only without Brawling". A potion's frozen grant can sit under
     * a DX that rose later, and the cell then offers the better operand.
     */
    @Test
    fun `a Brawling below DX leaves the DX row standing beside it`() {
        assertEquals(
            listOf(
                Triple("Brawling", UnarmedStyle.BRAWLING, 8),
                Triple(BARE_HANDS, UnarmedStyle.BARE_HANDS, 9),
            ),
            unarmedParries(mapOf(brawlingKey to 11), dx = 12),
        )
    }

    /**
     * The cell names Brawling as the alternative to DX, and no other style,
     * so a boxer or a wrestler keeps the DX row whatever their own style
     * parries at. Boxing's row differs from it in what a weapon costs.
     * Wrestling's can coincide, as it does here at 8 and 8, and the row
     * stays: withholding it would read "or DX" as "or DX unless you wrestle".
     */
    @Test
    fun `Boxing and Wrestling do not withdraw the DX row`() {
        val rows = unarmedParries(
            mapOf(dxSkill("Boxing", Difficulty.AVERAGE) to 14, dxSkill("Wrestling", Difficulty.AVERAGE) to 14),
            dx = 10,
        )
        assertEquals(
            listOf(
                Triple("Boxing", UnarmedStyle.BOXING, 10),
                Triple("Wrestling", UnarmedStyle.WRESTLING, 8),
                Triple(BARE_HANDS, UnarmedStyle.BARE_HANDS, 8),
            ),
            rows,
        )
    }

    /** The row is one of the rows, so a boost can be bought on it by its label. */
    @Test
    fun `the DX row can be paid for like any other parry`() {
        assertEquals(
            listOf("Dodge", "Parry ($BARE_HANDS)"),
            defenceLabels(dodge = true, emptyList(), unarmedParries(emptyMap(), dx = 10), block = false),
        )
    }

    /**
     * p. 37: "Roll vs. Acrobatics", and p. 10: "You can attempt most skills
     * even without training". The level is the one `SkillDefaults` already
     * composed, Versatile's +1 inside it; this screen must not rebuild DX-6.
     */
    @Test
    fun `an Acrobatic Dodge without the skill rolls the default already composed`() {
        val untrained = listOf(
            SkillDefaults.Untrained(dxSkill("Stealth", Difficulty.AVERAGE), 5, "DX-5"),
            SkillDefaults.Untrained(acrobaticsKey, 5, "DX-6, +1 Versatile"),
        )
        val skill = acrobaticsFor(emptyMap(), untrained)
        assertEquals(AcrobaticSkill(acrobaticsKey, 5, "DX-6, +1 Versatile"), skill)
        assertEquals("Acrobatics (untrained, DX-6, +1 Versatile)", skill?.label)
    }

    @Test
    fun `a trained Acrobatics is rolled as itself, never as a default`() {
        val skill = acrobaticsFor(
            mapOf(acrobaticsKey to 12),
            listOf(SkillDefaults.Untrained(acrobaticsKey, 4, "DX-6")),
        )
        assertEquals(AcrobaticSkill(acrobaticsKey, 12, null), skill)
        assertEquals("Acrobatics", skill?.label)
    }

    private val perfectBalance = Trait(TraitEffects.PERFECT_BALANCE, 15, TraitKind.ADVANTAGE)

    /**
     * The diff review's HIGH. The Skills tab rolls a skill with what the
     * trackers cost AND what the character is (`TraitEffects.modifiers`);
     * this dialog passed the first alone. Pickpocket -- DX 10, Perfect
     * Balance, no Acrobatics -- rolled the default at 4 here and at 6 there.
     * Latent on the trained path before 6ev, and reachable on a stock premade
     * the moment a sheet without the skill was offered the roll.
     */
    @Test
    fun `an Acrobatic Dodge takes the trait bonus the Skills tab gives the same roll`() {
        val untrained = AcrobaticSkill(acrobaticsKey, 4, "DX-6")
        val standing = acrobaticTarget(untrained, { emptyList() }, listOf(perfectBalance))
        assertEquals(4, standing.baseTarget)
        assertEquals(6, standing.target)
        assertEquals(4, acrobaticTarget(untrained, { emptyList() }, emptyList()).target)
    }

    /** The trackers' half still arrives: the ledger is added to, not replaced. */
    @Test
    fun `an Acrobatic Dodge still takes what the ledger passes in`() {
        val untrained = AcrobaticSkill(acrobaticsKey, 4, "DX-6")
        val ledger = { key: SkillKey -> TraitEffects.modifiers(listOf(perfectBalance), key) }
        assertEquals(6, acrobaticTarget(untrained, ledger, emptyList()).target)
    }

    @Test
    fun `no Acrobatics and no default leaves nothing to roll`() {
        assertNull(acrobaticsFor(emptyMap(), emptyList()))
    }

    /**
     * The gap this slice leaves, as a list the note can name: a carried
     * weapon that can parry and has no skill behind it. Line 2468 prints
     * "Weapon Skill ÷ 2 + 3" and no second operand, where the unarmed row
     * prints DX. A firearm is not one of them: it parries at nothing.
     */
    @Test
    fun `a parrying weapon carried without its skill is named, a firearm is not`() {
        val carried = listOf(
            Weapons.named("Broadsword"),
            Weapons.named("Combat Knife"),
            Weapons.named("Revolver (.38 cal)"),
        )
        assertEquals(listOf("Broadsword"), unskilledParriers(carried, mapOf(knifeKey to 11), mastery = null, dx = 10))
        assertTrue(unskilledParriers(carried, mapOf(knifeKey to 11, broadswordKey to 9), mastery = null, dx = 10).isEmpty())
    }
}
