package com.pathways.rules

import com.pathways.model.Layer
import com.pathways.model.ModEffect
import com.pathways.model.ModTarget
import com.pathways.model.Modifier
import com.pathways.model.ModifierId
import com.pathways.model.ModifierSource
import com.pathways.rules.content.CommonModifier
import com.pathways.rules.content.ContentLoader

/**
 * Appendix A's Common Modifiers (p. 159) -- the fourth modifier source.
 *
 * [Conditions] is what the character is carrying, [Attacks] is what the player
 * chose this turn, [Ranged] is the distance and the scene. This one is what
 * the GM says the situation is: good light, poor tools, a helping hand.
 *
 * **It is scoped to the plain skill roll and deliberately not offered on a
 * ranged attack.** "Adverse conditions (dark, noisy, dangerous) -4" and
 * chapter 5's "Darkness or heavy fog -3 to -9" are the same circumstance
 * priced twice, once generally and once specifically. Offering both charges
 * darkness twice -- specific beats general, the call the ritual CoR ladder
 * already needed (6h) and that keeps chapter 13's boons off Phase I
 * preparation (6r).
 */
object CommonModifiers {

    /** Every printed row, including the one that is not a modifier. */
    fun all(): List<CommonModifier> = ContentLoader.commonModifiers()

    /**
     * The rows that can actually be applied to a roll.
     *
     * "Using untrained skill (default)" is excluded, because it is not a
     * modifier: it replaces the roll with an attribute default, which
     * [SkillDefaults] answers per skill and [SkillLadder.defaultFor] per
     * difficulty. Its printed range is exactly `Difficulty.defaultPenalty`'s
     * two ends, which is how the table itself says so. Offering it would let a GM subtract it from a SKILL and
     * stack it on a default already taken.
     */
    fun offered(): List<CommonModifier> = all().filterNot { it.isDefault }

    fun named(situation: String): CommonModifier =
        all().firstOrNull { it.situation == situation }
            ?: error("the book prints no common modifier called '$situation'")

    /**
     * The value a row contributes, with [chosen] standing in for a GM's pick
     * inside a printed range.
     *
     * Refuses the untrained row outright rather than answering zero: a caller
     * given 0 would believe the rule had been priced, and the book's rule is
     * that the roll changes to an attribute default instead.
     */
    fun valueFor(row: CommonModifier, chosen: Int? = null): Int {
        require(!row.isDefault) {
            "'${row.situation}' is not a modifier -- it prints '${row.printed}', which " +
                "replaces the roll with an attribute default. Ask SkillDefaults."
        }
        val printed = row.modifier
            ?: error("'${row.situation}' prints no modifier")
        if (chosen == null) return printed
        // The gentle end is what the row offers, so a chosen value may only
        // run further from zero, and only as far as the book prints.
        val limit = row.otherEnd
        when {
            limit != null -> require(chosen in minOf(printed, limit)..maxOf(printed, limit)) {
                "'${row.situation}' prints ${row.printed}; $chosen is outside it"
            }
            row.isOpenEnded -> require(
                if (printed < 0) chosen <= printed else chosen >= printed
            ) {
                "'${row.situation}' prints ${row.printed}; $chosen is on the wrong side of it"
            }
            else -> require(chosen == printed) {
                "'${row.situation}' prints exactly ${row.printed}, not $chosen"
            }
        }
        return chosen
    }

    /**
     * The chosen circumstances as modifiers, ready for [SkillRoll].
     *
     * [gmValue] carries a pick inside a printed range, keyed by situation.
     * A value naming a row that was not chosen is refused rather than
     * ignored -- the stale-state shape the boon dialog was fixed for.
     */
    fun modifiers(
        chosen: List<CommonModifier> = emptyList(),
        gmValue: Map<String, Int> = emptyMap(),
    ): List<Modifier> {
        val names = chosen.map { it.situation }.toSet()
        val unmatched = gmValue.keys - names
        require(unmatched.isEmpty()) {
            "a value was given for ${unmatched.joinToString()}, which was not chosen"
        }
        return chosen.mapNotNull { row ->
            situational(row, valueFor(row, gmValue[row.situation]))
        }
    }

    private fun situational(row: CommonModifier, value: Int): Modifier? {
        // "Routine conditions" really does print +0. It is a choice that adds
        // nothing, so it reaches no ledger -- the same as any other zero.
        if (value == 0) return null
        return Modifier(
            id = ModifierId("common:${row.situation.lowercase()}"),
            source = ModifierSource.Situational(row.situation),
            // Retargeted onto the skill being rolled by SkillRoll.
            target = ModTarget.AllRolls,
            effect = ModEffect.Flat(value),
            layer = Layer.SITUATIONAL,
        )
    }
}
