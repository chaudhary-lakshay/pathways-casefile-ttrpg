package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.RitualExampleModifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The book against the engine (Ch. 7, Section VIII, p. 69-72).
 *
 * Seven worked rituals, each printing every modifier it applied, the net, the
 * effective skill and the SPI cost, all for one caster at Ritualistic Magic-14.
 * That is the chapter grading its own homework, and this test marks it.
 *
 * **A PARTIAL ORACLE, exactly like `GoldenSheetTest`.** Where a worked example
 * disagrees with a printed table, the disagreement is RECORDED in a named set.
 * The table is never repaired from the example, nor the example from the
 * table. Three such disagreements are pinned below, and each is a finding.
 */
class GoldenRitualTest {

    private val rituals = ContentLoader.ritualExamples()

    /**
     * Rows whose value comes from NO table this book prints.
     *
     * Duration is the bulk of them -- "One month duration -1", "Indefinite
     * duration -3", "Single-use object (not permanent) +1" -- and chapter 7
     * tabulates duration nowhere. They are the GM's, and naming them is how
     * the engine's coverage of a real ritual stays honest: it reproduces the
     * printed part and no more.
     */
    private val untabulated = setOf(
        "One month duration (longer than one week)",
        "Indefinite duration",
        "Single-use object (not permanent)",
        "Both parties willing",
    )

    @Test
    fun `the book's own arithmetic holds in every worked ritual`() {
        assertEquals(7, rituals.size)
        rituals.forEach { ritual ->
            assertEquals(
                ritual.net,
                ritual.modifiers.sumOf { it.value },
                "ritual ${ritual.number}'s rows do not sum to its printed net",
            )
            assertEquals(
                ritual.effectiveSkill,
                ritual.casterSkill + ritual.net,
                "ritual ${ritual.number}'s effective skill is not caster + net",
            )
        }
    }

    /**
     * Every "true name" row is the Quick Ritual's Target Link table, and all
     * six land on a printed cell -- including the two that take a range's end
     * rather than its middle.
     */
    @Test
    fun `every target-link row matches the printed link table`() {
        val links = RitualMode.links()
        val checked = rituals.flatMap { ritual ->
            ritual.modifiers.filter { it.source.contains("true name", ignoreCase = true) }
                .map { ritual to it }
        }
        assertEquals(6, checked.size, "the worked rituals print six link rows")
        checked.forEach { (ritual, row) ->
            val fits = links.any { row.value == it.modifier || row.value == it.best }
            assertTrue(fits) {
                "ritual ${ritual.number}'s '${row.source}' is ${row.value}, " +
                    "which no Target Link cell prints"
            }
        }
    }

    /**
     * "Proper dedicated space +0" is Adequate; "Long-used sanctified space +2"
     * is Good's better end. Both are Phase I preparation rows.
     */
    @Test
    fun `every space row matches the preparation table`() {
        val preparations = RitualResolution.preparations()
        val spaces = rituals.flatMap { ritual ->
            ritual.modifiers.filter { it.source.contains("space", ignoreCase = true) }
                .map { ritual to it }
        }
        assertTrue(spaces.isNotEmpty())
        spaces.forEach { (ritual, row) ->
            val fits = preparations.any { row.value == it.modifier || row.value == it.best }
            assertTrue(fits) {
                "ritual ${ritual.number}'s '${row.source}' is ${row.value} and " +
                    "no preparation quality prints it"
            }
        }
    }

    /** Materials and offerings are External Sacrifice tiers: all three are +2. */
    @Test
    fun `every materials row matches a sacrifice tier`() {
        val tiers = PowerSources.sacrificeTiers()
        val materials = rituals.flatMap { ritual ->
            ritual.modifiers.filter {
                it.source.contains("materials", ignoreCase = true) ||
                    it.source.contains("offerings", ignoreCase = true)
            }.map { ritual to it }
        }
        assertEquals(3, materials.size)
        materials.forEach { (ritual, row) ->
            assertTrue(tiers.any { it.modifier == row.value }) {
                "ritual ${ritual.number}'s '${row.source}' is ${row.value} and " +
                    "no sacrifice tier prints it"
            }
        }
    }

    /**
     * "Two trained assistants +2" is the whole evidence for reading "each
     * assistant beyond the first" as counting from the CASTER. Both rituals
     * that use assistants print the same +2.
     */
    @Test
    fun `every assistants row matches the participant bonus`() {
        val assisted = rituals.flatMap { ritual ->
            ritual.modifiers.filter { it.source.contains("assistants", ignoreCase = true) }
                .map { ritual to it }
        }
        assertEquals(2, assisted.size)
        assisted.forEach { (_, row) -> assertEquals(PowerSources.participantBonus(2), row.value) }
    }

    /**
     * **`RitualComplexity`'s join has one counter-example, and it is here.**
     * That row pairs the Time table's four names with the SPI ladder's four
     * rungs by position, so one rung is meant to give both a ritual's base
     * time and its cost.
     *
     * Ritual 6 does not obey it. "3 hours (extended)" from "triple the base
     * time" makes its base one hour -- the Heavy row -- and its extended bonus
     * is +2, also Heavy's. Its SPI is 6, which is the MODERATE rung; Heavy's is
     * 7-10. So one worked ritual is Heavy by time and Moderate by cost.
     *
     * Recorded, not repaired. Splitting the row into two independent axes on
     * the strength of one example would be authoring content, and the other
     * six are consistent. But the join is now known to be a READING rather
     * than a fact, and this test is where a later slice finds that out.
     */
    @Test
    fun `one worked ritual is a different rung by time than by cost`() {
        val fabrication = rituals.single { it.number == 6 }
        val extended = fabrication.modifiers.single {
            it.source.contains("Extended", ignoreCase = true)
        }
        assertEquals(2, extended.value)

        val byCost = PowerSources.complexities().single { rung ->
            fabrication.spi >= rung.spi && fabrication.spi <= (rung.spiMax ?: rung.spi)
        }
        assertEquals("Moderate", byCost.complexity)
        // Moderate's extended is +1 and the ritual printed +2 -- the Heavy
        // row's, matching its one-hour base time tripled to three.
        assertEquals(1, PowerSources.pacingModifier(byCost, RitualPacing.EXTENDED))
        assertEquals(
            2,
            PowerSources.pacingModifier(
                PowerSources.complexityNamed("Heavy"), RitualPacing.EXTENDED,
            ),
        )
    }

    /**
     * Ritual 7 DOES match the rung its cost puts it on, which is what keeps
     * the join a reasonable reading rather than a broken one.
     */
    @Test
    fun `the other extended ritual matches the rung its cost puts it on`() {
        val binding = rituals.single { it.number == 7 }
        val byCost = PowerSources.complexities().single { rung ->
            binding.spi >= rung.spi && binding.spi <= (rung.spiMax ?: rung.spi)
        }
        assertEquals("Heavy", byCost.complexity)
        assertEquals(
            binding.modifiers.single { it.source.contains("Extended") }.value,
            PowerSources.pacingModifier(byCost, RitualPacing.EXTENDED),
        )
    }

    /**
     * Three rituals print a base difficulty the weight table cannot express.
     * Curse, Oath-Sealing and Fabrication are all Moderate categories and all
     * print -3 where the table prints -2, so the table is a summary the worked
     * examples grade inside. Asserted so a later "fix" to either cannot erase
     * the conflict.
     */
    @Test
    fun `three worked rituals grade inside the Moderate band`() {
        val printed = RitualResolution.weights().map { it.modifier }.toSet()
        val graded = rituals.filter { it.baseDifficulty !in printed }
        assertEquals(listOf("Curse", "Oath-Sealing", "Fabrication"), graded.map { it.category })
        graded.forEach { assertEquals(-3, it.baseDifficulty) }
        assertTrue(-3 !in printed) {
            "the weight table prints a -3 now; these three stopped being a disagreement"
        }
    }

    /**
     * Every costed ritual fits the complexity ladder and three do not fit the
     * weight table's cost column. That asymmetry is the entire evidence for
     * charging fuel from the ladder (CLAUDE.md 6aa), so it is asserted here as
     * well as in the extractor -- the decision outlives the parse.
     */
    @Test
    fun `all seven fit the complexity ladder and three do not fit the weight column`() {
        val rungs = PowerSources.complexities()
        rituals.forEach { ritual ->
            assertTrue(
                rungs.any { rung ->
                    ritual.spi >= rung.spi &&
                        (rung.spiOpen || ritual.spi <= (rung.spiMax ?: rung.spi))
                },
            ) { "ritual ${ritual.number} costs ${ritual.spi} SPI, off the ladder" }
        }

        val outside = rituals.filter { ritual ->
            val weight = RitualResolution.weights().firstOrNull {
                it.categories.contains(ritual.category, ignoreCase = true)
            } ?: return@filter false
            val top = if (weight.spiOpen) null else weight.spiMax ?: weight.spi
            ritual.spi < weight.spi || (top != null && ritual.spi > top)
        }
        assertEquals(listOf(2, 3, 4), outside.map { it.number })
    }

    /**
     * What the engine genuinely cannot reproduce, named rather than skipped.
     * Duration is nearly all of it, and chapter 7 tabulates duration nowhere.
     */
    @Test
    fun `the rows no printed table covers are exactly the ones named`() {
        val covered = { row: RitualExampleModifier ->
            listOf(
                "true name", "space", "materials", "offerings", "assistants",
                "Extended", "base difficulty",
            ).any { row.source.contains(it, ignoreCase = true) }
        }
        val loose = rituals.flatMap { it.modifiers }.filterNot(covered).map { it.source }
        assertEquals(untabulated, loose.toSet())
    }
}
