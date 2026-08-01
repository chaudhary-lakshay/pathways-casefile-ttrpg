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
        // added to every roll the way reeling and fatigue are.
        if (spi != null && skill.attr == ControllingAttribute.Spi) {
            penalty("spi", spi.penalty, spi.level)?.let(::add)
        }
        if (skill.name.equals(RITUAL_SKILL, ignoreCase = true)) {
            penalty("cor", Corruption.ritualPenalty(cor), "$cor CoR")?.let(::add)
        }
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
