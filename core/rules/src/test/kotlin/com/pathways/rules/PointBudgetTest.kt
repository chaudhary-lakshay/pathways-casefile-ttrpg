package com.pathways.rules

import com.pathways.model.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PointBudgetTest {

    private fun adv(n: String, c: Int) = Trait(n, c, TraitKind.ADVANTAGE)
    private fun dis(n: String, c: Int) = Trait(n, c, TraitKind.DISADVANTAGE)
    private fun quirk(n: String) = Trait(n, 1, TraitKind.QUIRK)

    @Test
    fun `Occult Scholar balances exactly as published`() {
        val r = PointBudget.report(
            attributes = Attributes(st = 9, dx = 9, iq = 10, ht = 9),
            secondaryDelta = SecondaryDelta(),
            traits = listOf(
                adv("Eidetic Memory", 5), adv("Language Talent", 10),
                dis("Curious", 5), dis("Unfit", 5),
                dis("Wealth: Poor", 15), dis("Absent-Minded", 5),
            ),
            skills = listOf(
                PurchasedSkill(SkillKey("Occultism", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.AVERAGE), 16),
                PurchasedSkill(SkillKey("Research", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.AVERAGE), 8),
                PurchasedSkill(SkillKey("History", "Occult", ControllingAttribute.Buyable(Attr.IQ), Difficulty.HARD), 8),
                PurchasedSkill(SkillKey("Hidden Lore", "Spirits", ControllingAttribute.Buyable(Attr.IQ), Difficulty.AVERAGE), 4),
                PurchasedSkill(SkillKey("Writing", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.AVERAGE), 4),
                PurchasedSkill(SkillKey("First Aid", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.EASY), 1),
                PurchasedSkill(SkillKey("Diagnosis", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.HARD), 4),
                PurchasedSkill(SkillKey("Area Knowledge", "Academia", ControllingAttribute.Buyable(Attr.IQ), Difficulty.EASY), 1),
                PurchasedSkill(SkillKey("Literature", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.AVERAGE), 4),
                PurchasedSkill(SkillKey("Alchemy", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.VERY_HARD), 4),
                PurchasedSkill(SkillKey("Public Speaking", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.AVERAGE), 2),
            ),
        )
        assertEquals(100, r.budget)     // 70 + 30 disadvantages
        assertEquals(91, r.spent)       // 20 attributes + 15 advantages + 56 skills
        assertEquals(9, r.surplus)
        assertTrue(r.violations.isEmpty())
    }

    @Test
    fun `study points raise a skill and are not billed`() {
        // Ch. 3 p. 32: study points are earned over downtime, outside the budget.
        val key = SkillKey("Knife", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.EASY)
        fun spent(skill: PurchasedSkill) =
            PointBudget.report(Attributes(9, 9, 9, 9), SecondaryDelta(), emptyList(), listOf(skill)).spent
        assertEquals(spent(PurchasedSkill(key, 2)), spent(PurchasedSkill(key, 4, studied = 2)))
    }

    @Test
    fun `disadvantages are capped at forty points`() {
        val r = PointBudget.report(
            Attributes(), SecondaryDelta(), listOf(dis("A", 30), dis("B", 30)), emptyList(),
        )
        assertEquals(110, r.budget)     // 70 + min(60, 40)
        assertTrue(r.violations.any { it.contains("Disadvantages") })
    }

    @Test
    fun `quirks have their own separate five point cap`() {
        val r = PointBudget.report(
            Attributes(), SecondaryDelta(), List(7) { quirk("quirk $it") }, emptyList(),
        )
        assertEquals(75, r.budget)      // 70 + min(7, 5)
        assertTrue(r.violations.any { it.contains("Quirks") })
    }

    @Test
    fun `surplus is legal - a character need not spend everything`() {
        val r = PointBudget.report(Attributes(), SecondaryDelta(), emptyList(), emptyList())
        assertEquals(70, r.budget)
        assertEquals(0, r.spent)
        assertEquals(70, r.surplus)
        assertTrue(r.violations.isEmpty())
    }

    @Test
    fun `overspending is the surplus alone, never a second violation line`() {
        // One fact, one predicate: the builder's budget card reads surplus,
        // and a violation line for it grew the pinned header under a finger.
        val r = PointBudget.report(
            Attributes(iq = 14), SecondaryDelta(), emptyList(), emptyList(),
        )
        assertEquals(100, r.spent)      // five flat levels at 20
        assertEquals(-30, r.surplus)
        assertTrue(r.violations.isEmpty())
    }

    @Test
    fun `secondary characteristic purchases are billed against the budget`() {
        // SecondaryCharacteristics.costOf(SecondaryDelta(hp = 2)) = 2 * COST_HP = 2 * 2 = 4.
        // Attributes() is all-9 baseline, so AttributeCosts.totalCost = 0.
        // No traits or skills, so budget = 70 and the only spend is the secondary delta.
        val r = PointBudget.report(
            Attributes(), SecondaryDelta(hp = 2), emptyList(), emptyList(),
        )
        assertEquals(70, r.budget)
        assertEquals(4, r.spent)        // 0 attributes + 4 secondary (hp+2) + 0 advantages + 0 skills
        assertEquals(66, r.surplus)
        assertTrue(r.violations.isEmpty())
    }
}
