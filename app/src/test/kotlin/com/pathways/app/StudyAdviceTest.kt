package com.pathways.app

import com.pathways.model.ControllingAttribute.Buyable
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.Blessings
import com.pathways.rules.Derive
import com.pathways.rules.TraitEffects
import com.pathways.rules.Premades
import com.pathways.rules.Study
import com.pathways.rules.StudyMethod
import com.pathways.rules.StudyRefusal
import com.pathways.rules.StudyRefusedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The builder's study tag (6gm): a refusal is tagged by its cause, with its
 * sentence kept for the open row, and a plan says its price and the level it buys.
 * Every bought skill on every premade, by both roads.
 */
class StudyAdviceTest {

    @Test
    fun `a refusal is tagged and keeps its sentence, and a plan is priced`() {
        var refused = 0
        var planned = 0
        for (sheet in Premades.all()) {
            val character = sheet.toCharacter()
            val derived = Derive.of(character)
            for (skill in character.skills) for (method in StudyMethod.entries) {
                val advice = studyAdvice(skill, character, derived, method)
                val attributeLevel = Derive.controllingLevel(
                    skill.key.attr, character.attributes, derived.secondaries, character.spi,
                )
                val plan = Study.plan(
                    points = skill.points,
                    difficulty = skill.key.difficulty,
                    attributeLevel = attributeLevel,
                    method = method,
                    attr = skill.key.attr,
                )
                plan.fold(
                    onSuccess = {
                        planned++
                        assertNull(advice.reason, "${sheet.name} ${skill.key.display}")
                        assertTrue("${it.pointsNeeded} CP" in advice.tag && "→ ${it.toLevel}" in advice.tag, advice.tag)
                    },
                    onFailure = {
                        refused++
                        val refusal = it as StudyRefusedException
                        // The CAUSE, so a player sees which refusal the road
                        // chips can lift and which one a level settles.
                        val expected = when (refusal.refusal) {
                            StudyRefusal.AT_CAP -> "at cap ${Study.capFor(attributeLevel)}"
                            StudyRefusal.NOT_POSSIBLE -> "not by self-study"
                            StudyRefusal.NOT_BY_POINTS -> "not by points"
                            StudyRefusal.POTION_GRANTED -> "potion skill"
                            StudyRefusal.PAST_GRID -> "past the grid"
                        }
                        assertEquals(expected, advice.tag, "${sheet.name} ${skill.key.display}")
                        assertEquals(refusal.reason, advice.reason)
                    },
                )
            }
        }
        // Neither branch may be vacuous.
        assertTrue(refused > 0 && planned > 0, "refused $refused, planned $planned")
    }

    /**
     * The row prints the level a blessed attribute gives (`Derive.of`, 6hf),
     * so the rung study buys is read from the same attribute: one higher than
     * the level beside it, never three lower. The expectation is the row's
     * own number, not the advice's arithmetic.
     */
    @Test
    fun `a blessed holder's study tag climbs from the level the row prints`() {
        var checked = 0
        for (sheet in Premades.all()) {
            val mortal = sheet.toCharacter()
            val character = mortal.copy(
                traits = mortal.traits + Trait(TraitEffects.GOD_OF_COMBAT, 75, TraitKind.ADVANTAGE),
            )
            val derived = Derive.of(character)
            for (skill in character.skills) {
                val attr = (skill.key.attr as? Buyable)?.attr ?: continue
                if (Blessings.gainTo(attr, listOf(TraitEffects.GOD_OF_COMBAT)) == 0) continue
                val tag = studyAdvice(skill, character, derived, StudyMethod.TUTOR).tag
                val to = tag.substringAfter("→ ", "").toIntOrNull() ?: continue
                checked++
                assertEquals(derived.skillLevels.getValue(skill.key) + 1, to, "${sheet.name} ${skill.key.display}: $tag")
            }
        }
        assertTrue(checked > 0, "no blessed skill had a plan")
    }
}
