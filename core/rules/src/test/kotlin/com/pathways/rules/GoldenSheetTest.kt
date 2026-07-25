package com.pathways.rules

import com.pathways.model.Difficulty
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class GoldenSheetTest {

    private val hpExclusions = setOf("Docks Bouncer")
    private val fpExclusions = setOf("Docks Bouncer", "Factory Foreman", "Street Acrobat")

    private fun playable() = Fixtures.all().filter { it.skills.isNotEmpty() }

    @Test
    fun `all twenty three fixtures load`() {
        assertEquals(23, Fixtures.all().size)
    }

    @TestFactory
    fun `every published skill level is reproduced`(): List<DynamicTest> =
        playable().map { f ->
            DynamicTest.dynamicTest(f.name) {
                f.skills.forEach { s ->
                    val attrLevel = f.attributeLevelFor(s)
                    val computed = SkillLadder.levelFor(
                        points = s.points,
                        difficulty = Difficulty.fromCode(s.difficulty),
                        attributeLevel = attrLevel,
                    )
                    assertEquals(
                        s.publishedLevel, computed,
                        "${f.name}: ${s.name} ${s.difficulty} ${s.points}pt off ${s.attr} $attrLevel",
                    )
                }
            }
        }

    @TestFactory
    fun `derived secondaries match on the trusted field set`(): List<DynamicTest> =
        playable().map { f ->
            DynamicTest.dynamicTest(f.name) {
                val d = Derive.of(f.toCharacter())
                assertEquals(f.published.will, d.secondaries.will, "${f.name}: Will")
                assertEquals(f.published.per, d.secondaries.per, "${f.name}: Per")
                assertEquals(f.published.basicMove, d.secondaries.basicMove, "${f.name}: Basic Move")
                assertEquals(f.published.dodge, d.secondaries.dodge, "${f.name}: Dodge")
                if (f.name !in hpExclusions) {
                    assertEquals(f.published.hp, d.secondaries.maxHp, "${f.name}: HP")
                }
                if (f.name !in fpExclusions) {
                    assertEquals(f.published.fp, d.secondaries.maxFp, "${f.name}: FP")
                }
            }
        }

    @Test
    fun `the excluded sheets really do disagree - guard against silent repair`() {
        // If this ever starts matching, someone changed the engine to encode a spreadsheet bug.
        val bouncer = Fixtures.byName("Docks Bouncer")
        val d = Derive.of(bouncer.toCharacter())
        assertTrue(
            bouncer.published.hp != d.secondaries.maxHp,
            "Docks Bouncer HP now matches; re-check whether the exclusion is still warranted",
        )
    }

    @Test
    fun `most sheets floor Basic Speed and the book does not`() {
        val offenders = playable().count { f ->
            val d = Derive.of(f.toCharacter())
            f.published.basicSpeed != d.secondaries.basicSpeedQuarters / 4.0
        }
        assertTrue(offenders >= 19, "expected at least 19 sheets to disagree on Basic Speed, got $offenders")
    }

    @TestFactory
    fun `point budgets balance where the sheet recorded one`(): List<DynamicTest> =
        playable().filter { it.published.bpTotal != null }.map { f ->
            DynamicTest.dynamicTest(f.name) {
                val r = Derive.of(f.toCharacter()).budget
                assertTrue(r.spent <= r.budget, "${f.name}: spent ${r.spent} exceeds budget ${r.budget}")
            }
        }
}
