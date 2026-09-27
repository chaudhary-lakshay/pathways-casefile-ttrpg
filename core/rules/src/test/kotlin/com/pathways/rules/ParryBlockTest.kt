package com.pathways.rules

import com.pathways.model.*
import com.pathways.rules.content.ContentLoader
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ParryBlockTest {

    private fun weapon(name: String, mod: Int?) =
        Weapon(name, SkillKey(name, null, ControllingAttribute.Buyable(Attr.DX), Difficulty.AVERAGE), mod)

    @Test
    fun `armed parry halves the skill and rounds down`() {
        assertEquals(10, ParryBlock.parryFor(weapon("Rapier", 0), 14))   // 7 + 3
        assertEquals(10, ParryBlock.parryFor(weapon("Rapier", 0), 15))   // still 7 + 3
        assertEquals(11, ParryBlock.parryFor(weapon("Rapier", 0), 16))   // 8 + 3
    }

    @Test
    fun `the weapon modifier applies after the halving`() {
        assertEquals(9, ParryBlock.parryFor(weapon("Sap", -1), 14))
        assertEquals(8, ParryBlock.parryFor(weapon("Scalpel", -2), 14))
    }

    @Test
    fun `a weapon that cannot parry returns null`() {
        assertNull(ParryBlock.parryFor(weapon("Brass knuckles", null), 14))
    }

    @Test
    fun `Wrestling applies its penalty inside the halving`() {
        // (14 - 3) / 2 + 3 = 8, NOT (14 / 2 + 3) - 3 = 7
        assertEquals(8, ParryBlock.unarmedParry(UnarmedStyle.WRESTLING, 14))
        assertEquals(10, ParryBlock.unarmedParry(UnarmedStyle.BRAWLING, 14))
        assertEquals(10, ParryBlock.unarmedParry(UnarmedStyle.BOXING, 14))
    }

    /**
     * **A bare hand turning a knife is three points worse, and Boxing is the
     * one exemption.**
     *
     * "Brawling parries weapons at -3. Boxing uses Boxing / 2 + 3 and ignores
     * that penalty." (p. 37). The skill section agrees independently --
     * "Unlike Brawling, Boxing parries weapons without penalty" (p. 38) --
     * and that second printing is what licenses reading the exemption as
     * Boxing's alone rather than as loose phrasing.
     *
     * Wrestling is named by NEITHER exemption. Its own -3 is a different rule
     * in a different place: inside the halving, always, weapon or not. So a
     * wrestler parrying a knife takes both, which is what makes the two
     * penalties separate functions.
     */
    @Test
    fun `only Boxing ignores the penalty for parrying a weapon`() {
        assertEquals(0, ParryBlock.versusWeaponPenalty(UnarmedStyle.BOXING))
        assertEquals(-3, ParryBlock.versusWeaponPenalty(UnarmedStyle.BRAWLING))
        assertEquals(-3, ParryBlock.versusWeaponPenalty(UnarmedStyle.WRESTLING))
    }

    @Test
    fun `the two parry penalties compose rather than replacing each other`() {
        // A SECOND parry, of a weapon, bare-handed: -4 and -3, not -4 or -3.
        val second = ParryBlock.parryPenalty(priorParries = 1)
        val weapon = ParryBlock.versusWeaponPenalty(UnarmedStyle.BRAWLING)
        assertEquals(-7, second + weapon)
        // Wrestling's own -3 is inside the halving and is NOT one of these:
        // at skill 14 the base is 8, so the same pair leaves 1, not 4.
        assertEquals(1, ParryBlock.unarmedParry(UnarmedStyle.WRESTLING, 14) + second + weapon)
    }

    /**
     * "Cannot parry ranged attacks." -- a refusal, not a penalty.
     *
     * All three unarmed styles, because the row scopes itself to the hands
     * and Wrestling's section says the same from the other side ("parry MELEE
     * attacks with their hands", p. 38). An armed parry is deliberately not
     * covered: chapter 8 gives no melee weapon a ranged defence rule and
     * extending this sentence to weapons would author one.
     */
    @Test
    fun `no unarmed style parries a ranged attack`() {
        for (style in UnarmedStyle.entries) {
            assertTrue(!ParryBlock.canParryRanged(style), "$style must not parry a shot")
        }
    }

    @Test
    fun `the three rules the unarmed row prints are still printed`() {
        // Numbers inside prose live in Kotlin (6i, 6y, 6z); this pins the
        // cell they were read from, and the second printing that licensed the
        // Boxing exemption.
        val notes = ContentLoader.defenses().single { it.defense == "Parry (Unarmed)" }.notes
        assertTrue(notes.contains("Brawling parries weapons at -3"), notes)
        assertTrue(notes.contains("ignores that penalty"), notes)
        assertTrue(notes.contains("Cannot parry ranged attacks"), notes)
    }

    @Test
    fun `block halves the shield skill and adds the shield's DB`() {
        assertEquals(11, ParryBlock.blockFor(shieldSkillLevel = 14, shieldDb = 1))
    }

    @Test
    fun `a character has one parry per readied weapon, not one parry`() {
        val parries = listOf(weapon("Sap", -1), weapon("Brass knuckles", null))
            .map { ParryBlock.parryFor(it, 12) }
        assertEquals(listOf(8, null), parries)
    }

    @Test
    fun `parry floors rather than truncates below zero`() {
        // When skill < 3 in Wrestling, the numerator (skillLevel - 3) goes negative.
        // Math.floorDiv(-1, 2) = -1 (floors toward negative infinity)
        // But plain / in Kotlin truncates toward zero: -1 / 2 = 0
        // Working: skill 2 WRESTLING: (2 - 3) / 2 + 3 = (-1) / 2 + 3 = -1 + 3 = 2 (with floorDiv)
        assertEquals(2, ParryBlock.unarmedParry(UnarmedStyle.WRESTLING, 2))
    }

    @Test
    fun `the first parry of a round is free and each one after costs a tier`() {
        // "Parry accumulates a -4 cumulative penalty AFTER THE FIRST", so the
        // opening parry is unmodified -- reading it as -4 per parry would
        // penalise a fighter who parried once and never again.
        assertEquals(0, ParryBlock.parryPenalty(0))
        assertEquals(-4, ParryBlock.parryPenalty(1))
        assertEquals(-8, ParryBlock.parryPenalty(2))
        assertEquals(-12, ParryBlock.parryPenalty(3))
    }

    @Test
    fun `the two softer tiers exist and nothing in chapter 8 can reach them`() {
        // The book prints -2 for fencing weapons and -1 for a rapier; chapter
        // 8's nine weapons are five firearms, a knife, a truncheon, a hand axe
        // and a broadsword. Printed rules with nothing to reach them, the
        // `Guns (Pistol)` shape (6t) -- modelled so a GM's smallsword is not
        // silently charged the broadsword's penalty.
        assertEquals(-2, ParryBlock.parryPenalty(1, ParryClass.FENCING))
        assertEquals(-1, ParryBlock.parryPenalty(1, ParryClass.RAPIER))
        assertEquals(-4, ParryBlock.parryPenalty(1, ParryClass.ORDINARY))
        val parryable = Weapons.all().filter { Weapons.canParry(it) }.map { it.name }
        assertTrue(parryable.isNotEmpty())
        assertTrue(
            parryable.none { it.contains("rapier", true) || it.contains("fencing", true) },
            "chapter 8 now prints a fencing weapon: $parryable",
        )
    }

    @Test
    fun `a negative count of earlier parries is refused, not clamped`() {
        // A caller holding -1 has a bug in its round tracking, and answering
        // with a BONUS would hide it behind a plausible number.
        assertThrows<IllegalArgumentException> { ParryBlock.parryPenalty(-1) }
    }

    @Test
    fun `the sentences the Kotlin numbers were read from are still printed`() {
        // The tiers live in Kotlin because they are numbers inside a prose
        // notes cell -- the split the maneuver table's +4/-4 (6i) and the +3
        // support cap (6y) take. This pins the cell they came from, so the
        // committed content and the constants cannot drift apart in silence.
        val defenses = ContentLoader.defenses()
        val armed = defenses.single { it.defense == "Parry (Armed)" }.notes
        assertTrue(
            armed.contains("Cumulative -4 per additional parry (-2 fencing, -1 rapier)"),
            "the parry accumulation sentence has moved: $armed",
        )
        // Block is a LIMIT, not a penalty, and that is why parryPenalty takes
        // no defence parameter.
        assertTrue(
            defenses.single { it.defense == "Block" }.notes.contains("Once per round"),
        )
        assertEquals(1, ParryBlock.BLOCKS_PER_ROUND)
        // ...and Dodge takes neither, which is what makes the scope parries.
        assertTrue(
            defenses.single { it.defense == "Dodge" }.notes.contains("May be used repeatedly"),
        )
    }

    /**
     * The SHEET's weapon row, which is the only thing [ParryBlock.parryFor]
     * answers for.
     *
     * These three cases lived in `DefenseDialogTest` until the defence
     * dialog moved onto chapter 8's catalogue, where the book prints no
     * per-weapon modifier. They move here rather than dying with that
     * screen's shape: the function is the sheet path, kept for a published
     * sheet that prints a weapon row, and a formula with no test is a claim.
     */
    @Test
    fun `a sheet weapon parries at half its skill plus three, plus its own modifier`() {
        val sword = SkillKey("Shortsword", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.AVERAGE)
        assertEquals(9, ParryBlock.parryFor(Weapon("Cane sword", sword, parryMod = 0), 13))
        assertEquals(10, ParryBlock.parryFor(Weapon("Fencing blade", sword, parryMod = 1), 13))
        // A dash on the sheet is not a +0: that weapon cannot parry at all.
        assertNull(ParryBlock.parryFor(Weapon("Revolver", sword, parryMod = null), 13))
    }
}
