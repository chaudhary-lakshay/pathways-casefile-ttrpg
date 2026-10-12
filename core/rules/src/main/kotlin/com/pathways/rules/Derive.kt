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
    /**
     * The mortal's derivation: secondaries and skill levels from the PLAYED
     * attributes ([Blessings.played], 6hf), the budget from the bought ones.
     */
    fun of(c: Character): DerivedCharacter {
        val played = Blessings.played(c.attributes, c.traits)
        val secondaries = SecondaryCharacteristics.of(played, c.secondaryDelta)
        return DerivedCharacter(
            secondaries = secondaries,
            budget = PointBudget.report(c.attributes, c.secondaryDelta, c.traits, c.skills),
            skillLevels = c.skills.associate { s ->
                s.key to SkillLadder.levelFor(
                    s.points,
                    s.key.difficulty,
                    controllingLevel(s.key.attr, played, secondaries, c.spi),
                )
            },
        )
    }

    /**
     * ST, DX, IQ and HT as played: [walked]'s, after any potion, or the
     * blessed purchase for a mortal. The one answer every screen's fallback
     * reads (6hf), so a blessing cannot reach one and miss the next.
     */
    fun attributes(c: Character, walked: DigestedCharacter?): Attributes =
        walked?.effectiveAttributes ?: Blessings.played(c.attributes, c.traits)

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
