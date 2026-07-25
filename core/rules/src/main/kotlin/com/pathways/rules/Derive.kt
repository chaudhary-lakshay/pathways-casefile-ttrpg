package com.pathways.rules

import com.pathways.model.Character
import com.pathways.model.SkillKey

data class DerivedCharacter(
    val secondaries: Secondaries,
    val budget: BudgetReport,
    val skillLevels: Map<SkillKey, Int>,
)

/** The single public entry point of the engine. Pure: it never mutates its input. */
object Derive {
    fun of(c: Character): DerivedCharacter = DerivedCharacter(
        secondaries = SecondaryCharacteristics.of(c.attributes, c.secondaryDelta),
        budget = PointBudget.report(c.attributes, c.secondaryDelta, c.traits, c.skills),
        skillLevels = c.skills.associate { s ->
            s.key to SkillLadder.levelFor(s.points, s.key.difficulty, c.attributes[s.key.attr])
        },
    )
}
