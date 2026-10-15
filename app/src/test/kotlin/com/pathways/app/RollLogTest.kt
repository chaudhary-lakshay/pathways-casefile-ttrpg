package com.pathways.app

import com.pathways.rules.SeededRandom
import com.pathways.rules.SuccessRoll
import com.pathways.rules.threeDSix
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The roll log: every throw, labelled, newest first, per character. */
class RollLogTest {

    @Test
    fun `a throw is logged with its character, label, dice and target, newest first`() {
        val log = RollLog(SeededRandom(1))
        val first = SuccessRoll.against(11, log.dice("Private Investigator", "Observation"))
        log.dice("Private Investigator", "Fright").threeDSix()
        val rows = log.of("Private Investigator")
        assertEquals(listOf("Fright", "Observation"), rows.map { it.label })
        assertEquals(listOf(null, 11), rows.map { it.target })
        assertEquals(first.total, rows[1].faces.sum())
    }

    @Test
    fun `each character reads only its own throws`() {
        val log = RollLog(SeededRandom(2))
        log.dice("Beat Cop", "Brawling").threeDSix()
        log.dice("Journalist", "Writing").threeDSix()
        assertEquals(listOf("Brawling"), log.of("Beat Cop").map { it.label })
    }

    @Test
    fun `the oldest throw falls off past the cap`() {
        val log = RollLog(SeededRandom(3), cap = 2)
        listOf("a", "b", "c").forEach { log.dice("X", it).threeDSix() }
        assertEquals(listOf("c", "b"), log.of("X").map { it.label })
    }

    @Test
    fun `one character's throws never push out another's`() {
        val log = RollLog(SeededRandom(5), cap = 2)
        log.dice("Beat Cop", "Brawling").threeDSix()
        listOf("a", "b", "c").forEach { log.dice("Journalist", it).threeDSix() }
        assertEquals(listOf("Brawling"), log.of("Beat Cop").map { it.label })
        assertEquals(listOf("c", "b"), log.of("Journalist").map { it.label })
    }

    @Test
    fun `forgetting a character clears its throws and no one else's`() {
        val log = RollLog(SeededRandom(4))
        log.dice("Beat Cop", "Brawling").threeDSix()
        log.dice("Journalist", "Writing").threeDSix()
        log.forget("Beat Cop")
        assertEquals(emptyList<LoggedThrow>(), log.of("Beat Cop"))
        assertEquals(1, log.of("Journalist").size)
    }

    @Test
    fun `a row reads its dice, total and target`() {
        assertEquals("4 + 5 + 6 = 15 vs 11", LoggedThrow("X", "Fright", listOf(4, 5, 6), 11).line())
        assertEquals("2 + 3 + 1 = 6", LoggedThrow("X", "Fright", listOf(2, 3, 1), null).line())
    }
}
