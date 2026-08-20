package com.pathways.rules

import com.pathways.model.ControllingAttribute
import com.pathways.model.Layer
import com.pathways.model.ModEffect
import com.pathways.model.ModTarget
import com.pathways.model.Modifier
import com.pathways.model.ModifierId
import com.pathways.model.ModifierSource
import com.pathways.model.SkillKey

/**
 * The penalties a character is carrying right now, as modifiers.
 *
 * This is what turns the trackers into rolls. [Pools] and [Corruption] already
 * say what a state costs; until something fed those numbers into a roll, the
 * sheet cheerfully rolled a dying character at full skill.
 *
 * **Built per skill, not per character.** Two of the book's penalties are
 * scoped: Spiritual Attrition is "-3 to all SPI-based skill rolls and
 * Beyonder ability checks" (p. 35), and the ritual CoR ladder is "-1 to all
 * ritual rolls" (p. 68). [ModTarget.RollClass] cannot express either yet --
 * [SkillKey] carries no tag to match on -- so the scope is applied here, by
 * asking each penalty whether it touches THIS skill. A modifier list built
 * once per character would have to lie about one of them.
 *
 * Everything is [Layer.STATE_THRESHOLD] and [ModifierSource.StateThreshold]:
 * these are threshold states, not equipment or advantages, and tagging them
 * correctly is what lets a future Cancel effect ("ignore fatigue penalties")
 * find them.
 */
object Conditions {

    /** The skill every ritual is rolled against (Ch. 7). */
    const val RITUAL_SKILL = "Ritualistic Magic"

    /**
     * The modifiers that apply to a roll of [skill].
     *
     * [spi] is null for a mortal, who has no SPI pool at all (p. 41). [cor] is
     * the running corruption total; its penalty reaches ritual rolls only.
     */
    fun forSkill(
        skill: SkillKey,
        hp: PoolStatus,
        fp: PoolStatus,
        spi: PoolStatus? = null,
        cor: Int = 0,
    ): List<Modifier> = buildList {
        penalty("hp", hp.penalty, hp.level)?.let(::add)
        penalty("fp", fp.penalty, fp.level)?.let(::add)
        // Scoped: SPI-controlled skills only, which is why this is not simply
        // added to every roll the way reeling and fatigue are -- here for a
        // skill, and through penaltyFor for the three kinds this ledger cannot
        // reach.
        if (spi != null && skill.attr == ControllingAttribute.Spi) {
            penalty("spi", spi.penalty, spi.level)?.let(::add)
        }
        if (skill.name.equals(RITUAL_SKILL, ignoreCase = true)) {
            penalty("cor", Corruption.ritualPenalty(cor), "$cor CoR")?.let(::add)
        }
    }

    /**
     * The same two penalties as an Int, for the rolls that never see a [SkillKey].
     *
     * p. 40's "Reeling: -1 to all rolls" and p. 42's "-1 to all rolls" are
     * unqualified, and [Shock] already reads that exact phrase at its word for
     * every [RollKind] -- so a defence value, a bare attribute roll and the
     * Major Wound's own HT roll are inside "all". Every kind gets the same
     * answer here because nothing excludes one: shock's single exclusion is
     * about WHEN ([RollKind.MAJOR_WOUND_HT] is the roll that CREATES it), and
     * a character was already reeling before the blow landed.
     *
     * [spi] and [cor] are deliberately absent. Spiritual Attrition prints "all
     * SPI-based SKILL rolls" (p. 35) and the ritual ladder "all ritual rolls"
     * (p. 68); both name a scope, and a defence value has no skill to match it
     * against. Widening either here would author a rule the book scopes.
     *
     * @throws IllegalArgumentException for [RollKind.SKILL], which takes these
     * through [forSkill] as ledger modifiers. The invariant is one delivery
     * path per roll kind; a caller taking both would charge a reeling
     * character twice, and no other number on the screen would disagree.
     */
    fun penaltyFor(kind: RollKind, hp: PoolStatus, fp: PoolStatus): Int {
        require(kind != RollKind.SKILL) {
            "a skill roll takes reeling and fatigue through forSkill's ledger, not this number"
        }
        return hp.penalty + fp.penalty
    }

    private fun penalty(id: String, value: Int, tracker: String): Modifier? {
        if (value == 0) return null
        return Modifier(
            id = ModifierId("state:$id"),
            source = ModifierSource.StateThreshold(tracker),
            // Retargeted onto the skill being rolled by SkillRoll.of; the
            // scoping decision has already been made above.
            target = ModTarget.AllRolls,
            effect = ModEffect.Flat(value),
            layer = Layer.STATE_THRESHOLD,
        )
    }
}
