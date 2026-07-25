package com.pathways.rules

import com.pathways.model.Attributes
import com.pathways.model.Character
import com.pathways.model.ControllingAttribute
import com.pathways.model.SkillKey

data class DerivedCharacter(
    val secondaries: Secondaries,
    val budget: BudgetReport,
    val skillLevels: Map<SkillKey, Int>,
)

/** The single public entry point of the engine. Pure: it never mutates its input. */
object Derive {
    fun of(c: Character): DerivedCharacter {
        val secondaries = SecondaryCharacteristics.of(c.attributes, c.secondaryDelta)
        return DerivedCharacter(
            secondaries = secondaries,
            budget = PointBudget.report(c.attributes, c.secondaryDelta, c.traits, c.skills),
            skillLevels = c.skills.associate { s ->
                s.key to SkillLadder.levelFor(
                    s.points,
                    s.key.difficulty,
                    controllingLevel(s.key.attr, c.attributes, secondaries, c.spi),
                )
            },
        )
    }

    /**
     * Resolves a skill's controlling attribute to an actual level by reading
     * through the character's DERIVED state. Will and Per come from
     * [Secondaries] (iq + delta), not raw [Attributes], so a character who
     * buys Will up correctly raises Will-controlled skills. SPI comes from
     * the character directly since it is never derivable from attributes.
     */
    fun controllingLevel(
        attr: ControllingAttribute,
        attributes: Attributes,
        secondaries: Secondaries,
        spi: Int,
    ): Int = when (attr) {
        is ControllingAttribute.Buyable -> attributes[attr.attr]
        ControllingAttribute.Will -> secondaries.will
        ControllingAttribute.Per -> secondaries.per
        ControllingAttribute.Spi -> spi
    }
}
