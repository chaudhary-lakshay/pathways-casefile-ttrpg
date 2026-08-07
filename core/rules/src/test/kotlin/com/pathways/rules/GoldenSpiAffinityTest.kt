package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Chapter 4 against chapter 9 (p. 33 vs p. 85-96).
 *
 * The SPI Attribute section makes four checkable claims about what a Sequence
 * 9 potion grants -- five named pathways receive "significantly more", others
 * a moderate "+2 to +6", reality-leaning ones a "baseline +1", and the
 * Prisoner (Chained) exactly 0. `pathways.json` has held those numbers since
 * M3 and nothing had ever compared them. This marks that homework, the way
 * `GoldenRitualTest` marks Section VIII's worked rituals and
 * `GoldenSheetTest` marks the 23 published sheets.
 *
 * **A PARTIAL ORACLE, like both of those.** Where a claim disagreed with the
 * data, the disagreement would be RECORDED in a named set and neither side
 * repaired (4.1). Today there is nothing to record: every claim reconciles
 * exactly, which is a rarer outcome here than a disagreement and is the whole
 * reason the affinity paragraph can be trusted as a description of the data.
 */
class GoldenSpiAffinityTest {

    private val tiers = ContentLoader.spiAffinity()

    /**
     * What chapter 9 actually grants at Sequence 9, by pathway.
     *
     * `delta` is a Double, and `toInt()` truncates toward zero where this
     * book floors -- the defect class 4.3 exists for, and the one
     * `PotionDigestion.wholeSteps` already guards. Every SPI grant is a whole
     * number today, so the guard costs nothing and fails loudly on the first
     * fractional one rather than silently comparing a truncated value.
     */
    private val granted: Map<String, Int> = ContentLoader.pathways()
        .filter { it.sequence == 9 }
        .associate { block ->
            val delta = block.stats.filter { it.attr == "SPI" }.sumOf { it.delta }
            check(delta == Math.floor(delta)) {
                "${block.pathway} grants a fractional $delta SPI; truncating it " +
                    "would compare a number the book never printed"
            }
            block.pathway to delta.toInt()
        }

    private fun tier(name: String) = tiers.single { it.tier == name }

    @Test
    fun `the chapter prints four tiers and chapter 9 prints 22 pathways`() {
        assertEquals(
            listOf("high", "moderate", "baseline", "constrained"),
            tiers.map { it.tier },
        )
        assertEquals(22, granted.size)
    }

    /**
     * "Pathways confirmed to have high spirituality ... receive significantly
     * more SPI than other pathways."
     *
     * Checked as a STRICT separation rather than by a threshold the book
     * never prints: the five named pathways are the five highest, and the
     * lowest of them still beats the highest of everyone else. A threshold
     * would be a number this app invented, and it would keep passing after
     * the gap closed.
     */
    @Test
    fun `the five named pathways really do receive significantly more`() {
        val named = tier("high").pathways
        assertEquals(
            listOf("Fool", "Hanged Man", "Darkness", "Hermit", "Wheel of Fortune"),
            named,
        )
        val lowestNamed = named.minOf { granted.getValue(it) }
        val highestOther = granted.filterKeys { it !in named }.values.max()
        assertTrue(
            lowestNamed > highestOther,
            "lowest named pathway grants $lowestNamed, highest other grants $highestOther",
        )
        val topFive = granted.entries.sortedByDescending { it.value }.take(5).map { it.key }
        assertEquals(named.toSet(), topFive.toSet())
    }

    /**
     * "Reality-leaning pathways receive a baseline +1 SPI" and the moderate
     * band "+2 to +6". Every pathway that is neither named nor the Prisoner
     * falls in exactly one of them, and BOTH are populated -- a test that only
     * checked the union would still pass if one band emptied.
     */
    @Test
    fun `every other pathway lands in one of the two printed bands`() {
        val named = tier("high").pathways.toSet() + tier("constrained").pathways
        val rest = granted.filterKeys { it !in named }
        val moderate = tier("moderate")
        val baseline = tier("baseline")
        val inModerate = rest.filterValues { it in moderate.low!!..moderate.high!! }
        val inBaseline = rest.filterValues { it == baseline.low }

        val unbanded = rest.keys - inModerate.keys - inBaseline.keys
        assertEquals(emptySet<String>(), unbanded, "pathways in neither printed band")
        assertTrue(inModerate.isNotEmpty(), "the moderate band emptied")
        assertTrue(inBaseline.isNotEmpty(), "the baseline band emptied")
        assertEquals(1, baseline.low)
        assertEquals(2 to 6, moderate.low to moderate.high)
    }

    /**
     * "The Prisoner (Chained) is a unique exception ... granting 0 SPI at
     * Sequence 9."
     *
     * Unique is asserted as well as zero: it is the only pathway the chapter
     * exempts, and it must stay the only one at 0, or the exception has
     * quietly become a band.
     */
    @Test
    fun `the Prisoner is the only pathway granted no spirituality`() {
        assertEquals(listOf("Chained"), tier("constrained").pathways)
        assertEquals(0, tier("constrained").low)
        assertEquals(listOf("Chained"), granted.filterValues { it == 0 }.keys.toList())
    }

    /**
     * Every tier keeps the sentence it was read from. A claim whose printed
     * words are gone is a claim nobody can check again -- the reason 6ab has
     * `GoldenRitualTest` assert the book's own counts rather than recall them.
     */
    @Test
    fun `every tier keeps the words it was read from`() {
        tiers.forEach { assertTrue(it.printed.isNotBlank(), it.tier) }
        assertTrue(tier("constrained").printed.contains("actively constrained"))
        assertTrue(tier("high").printed.contains("significantly more"))
    }
}
