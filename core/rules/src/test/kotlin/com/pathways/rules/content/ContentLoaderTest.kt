package com.pathways.rules.content

import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.rules.Fixtures
import com.pathways.rules.SkillLadder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentLoaderTest {

    // ---------------------------------------------------------------
    // Raw-JSON helpers for the required-fields guard (defect 2): these
    // read the file text directly rather than through the Kotlin data
    // classes, because a data class default value (needed so e.g. a
    // range-cost trait's absent `cost` key doesn't explode) would silently
    // paper over a *genuinely* missing key the same way. The guard has to
    // look at the raw JSON object's key set, not the deserialized result.
    // ---------------------------------------------------------------

    private fun rawEntries(resource: String): JsonArray {
        val stream = ContentLoaderTest::class.java.getResourceAsStream("/content/$resource")
            ?: error("content file not found on classpath: $resource")
        val text = stream.bufferedReader().use { it.readText() }
        val obj = Json.parseToJsonElement(text).jsonObject
        return obj.getValue("entries") as JsonArray
    }

    @Test
    fun `every skill entry has all required fields`() {
        val required = setOf("name", "specialization", "attr", "difficulty", "specializable", "notes")
        rawEntries("skills.json").forEachIndexed { i, el ->
            val obj = el.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content
            for (key in required) {
                assertTrue(obj.containsKey(key), "skills.json entry #$i ($name) missing required field '$key'")
            }
        }
    }

    @Test
    fun `every trait entry in advantages and disadvantages has a name, notes, and a cost representation`() {
        for (resource in listOf("advantages.json", "disadvantages.json")) {
            rawEntries(resource).forEachIndexed { i, el ->
                val obj = el.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content
                assertTrue(obj.containsKey("name"), "$resource entry #$i missing 'name'")
                assertTrue(obj.containsKey("notes"), "$resource entry #$i ($name) missing 'notes'")
                val hasCost = obj.containsKey("cost")
                val hasRange = obj.containsKey("costMin") && obj.containsKey("costMax")
                val hasVariable = obj.containsKey("variableCost")
                assertTrue(
                    hasCost || hasRange || hasVariable,
                    "$resource entry #$i ($name) has no cost representation at all " +
                        "(none of cost / costMin+costMax / variableCost present) -- this is defect 2",
                )
            }
        }
    }

    @Test
    fun `TraitDefinition itself refuses to construct with no cost representation`() {
        assertFailsWith<IllegalArgumentException> {
            TraitDefinition(name = "Nothing", notes = "no cost field set at all")
        }
    }

    // ---------------------------------------------------------------
    // Skills: every fixture skill must resolve to a content entry, either
    // exactly (same name + specialization) or, for a book entry marked
    // `specializable` (detected from the "(specify...)" marker, not a
    // hardcoded skill list), by matching on base name alone.
    // ---------------------------------------------------------------

    /**
     * Genuinely absent from the rulebook: verified (build/extract/lines.json)
     * to have zero line-start occurrences anywhere in the 170-page book.
     * "Meditation" appears exactly once, inside the sentence "Replaces
     * Meditation for Beyonder purposes" -- never as a skill-list entry.
     * The 23 published character sheets use these four skills anyway; this
     * is a real book-vs-sheet gap, the same class as the HP/FP spreadsheet
     * quirks already documented in GoldenSheetTest. Named explicitly so a
     * genuine 31st gap still fails this test instead of being silently
     * absorbed by a loose exception rule.
     */
    private val ABSENT_FROM_BOOK_SKILLS = setOf("Meditation", "Forced Entry", "Juggling", "Gesture")

    /**
     * Fixture (name, specialization) pairs where the matching-by-base-name
     * rule does NOT apply -- verified directly against the book text that
     * the content entry is NOT marked `specializable` -- so per the
     * fallback these are reported as conflicts rather than silently
     * resolved:
     *  - Mathematics: book prints "Mathematics (Applied)" as a fixed,
     *    already-specialized entry (no "(specify...)" marker anywhere);
     *    sheets use bare "Mathematics".
     *  - History (Occult): book's "History" entry is bare, with no
     *    "(specify...)" marker on the name or in the notes ("Historical
     *    knowledge") -- unlike Area Knowledge/Hidden Lore/etc., which do
     *    carry that marker. Sheets specialize it anyway.
     *  - Engineer (Steam): the book's actual entry is spelled
     *    "Engineering" (different base name entirely, not just a missing
     *    specialization) and is not marked specializable either.
     */
    private val SKILL_NAME_CONFLICTS: Set<Pair<String, String?>> = setOf(
        "Mathematics" to null,
        "History" to "Occult",
        "Engineer" to "Steam",
    )

    @Test
    fun `every fixture skill resolves to a content entry except the documented book gaps and conflicts`() {
        val byName = ContentLoader.skills().groupBy { it.name }
        val failures = mutableListOf<String>()
        Fixtures.all().forEach { fixture ->
            fixture.skills.forEach { s ->
                if (s.name in ABSENT_FROM_BOOK_SKILLS) return@forEach
                if ((s.name to s.specialization) in SKILL_NAME_CONFLICTS) return@forEach
                val entries = byName[s.name]
                if (entries == null) {
                    failures += "${fixture.name}: '${s.name}' (${s.specialization}) has no content entry at all"
                    return@forEach
                }
                val exact = entries.any { it.specialization == s.specialization }
                val genericSpecializable = entries.any { it.specializable }
                if (!exact && !genericSpecializable) {
                    failures += "${fixture.name}: '${s.name}' (${s.specialization}) " +
                        "has neither an exact match nor a specializable generic entry"
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `Occultism is IQ Average and Alchemy is IQ Very Hard with no untrained default`() {
        val occultism = ContentLoader.skills().first { it.name == "Occultism" }
        assertEquals(ControllingAttribute.fromCode("IQ"), occultism.attr)
        assertEquals(Difficulty.AVERAGE, occultism.difficulty)

        val alchemy = ContentLoader.skills().first { it.name == "Alchemy" }
        assertEquals(ControllingAttribute.fromCode("IQ"), alchemy.attr)
        assertEquals(Difficulty.VERY_HARD, alchemy.difficulty)
        assertNull(SkillLadder.defaultFor(alchemy.difficulty, attributeLevel = 14))
    }

    @Test
    fun `at least one skill loads with the SPI controlling attribute`() {
        val spiSkills = ContentLoader.skills().filter { it.attr == ControllingAttribute.Spi }
        assertTrue(spiSkills.isNotEmpty(), "expected at least one SPI-controlled skill")
        assertTrue(spiSkills.any { it.name == "Spiritual Intuition" })
    }

    // ---------------------------------------------------------------
    // Advantages / disadvantages: every fixture trait must resolve to a
    // book entry (exactly, or by base name when the book entry is a
    // specify-marked or variable/range-cost template -- a range cost is
    // inherently per-character, so a single book row legitimately covers
    // many differently-specialized fixture instances), and its cost must
    // agree with the book (exact cost, or within [costMin, costMax]).
    // Genuine disagreements are reported below rather than bent to match.
    // ---------------------------------------------------------------

    private val SPECIFY = Regex("""^(.*?)\s*\((specify[^)]*|any)\)\s*$""", RegexOption.IGNORE_CASE)
    private val PAREN = Regex("""^(.*?)\s*\(.*\)\s*$""")
    private val TRAILING_LEVEL_NUMBER = Regex("""^(.*?)\s+\d+$""")
    private val DASH_DESCRIPTION = Regex("""^(.*?)\s+[-–—]\s+.+$""", RegexOption.DOT_MATCHES_ALL)
    private val COLON_DESCRIPTION = Regex("""^(.*?):\s+.+$""", RegexOption.DOT_MATCHES_ALL)

    /**
     * Pure spelling/renaming variants between the fixtures and the book,
     * verified by hand to be the same trait (same effect text, and for
     * Poverty/Wealth the same cost on the sheets that use 10 pts):
     *  - "Wierdness Magnet" (fixture typo) / "Weirdness Magnet" (book)
     *  - "Absent-Minded" (fixture) / "Absent-Mindedness" (book)
     *  - "Poverty: Struggling" (fixture) / "Wealth: Struggling" (book)
     */
    private val TRAIT_NAME_ALIASES = mapOf(
        "Wierdness Magnet" to "Weirdness Magnet",
        "Absent-Minded" to "Absent-Mindedness",
        "Poverty: Struggling" to "Wealth: Struggling",
    )

    /**
     * Fixture trait names that are fixture-extraction artifacts: the
     * spreadsheet-reading tool (tools/extract_sheets.py) concatenated the
     * trait's own flavour notes directly onto its name with no separator
     * at all, so no general normalization rule can recover the true name
     * ("Clueless" -- book cost 10, matching the fixture's own cost --
     * confirms this is simply a mangled "Clueless"). Fixing the fixture
     * extractor is out of scope for this phase (only tools/extract_content.py
     * and tools/coverage.py); named here so they don't silently vanish from
     * either the "resolves" or the "conflict" bucket.
     */
    private val KNOWN_FIXTURE_NAME_ARTIFACTS = setOf(
        "Clueless −3 to all social skill rolls; generally miss social cues and subtlety. " +
            "You do\nnot understand subtext, sarcasm, or implication",
        "Clueless −3 to all social skill rolls; generally miss social cues and subtlety. " +
            "You do\nnot understand subtext, sarcasm, or implication.",
    )

    /** Genuinely absent from the book under any spelling -- verified by name search. */
    private val KNOWN_ABSENT_TRAITS = setOf(
        "No Sense of Humour",
        "Pacifism (Cannot Harm Innocents)",
        "Pacifism (Cannot Kill)",
    )

    private data class TraitConflict(val name: String, val fixtureCost: Int, val bookDescription: String)

    private fun candidateNames(name: String): List<String> {
        val aliased = TRAIT_NAME_ALIASES[name] ?: name
        val out = mutableListOf(aliased)
        for (pattern in listOf(PAREN, TRAILING_LEVEL_NUMBER, DASH_DESCRIPTION, COLON_DESCRIPTION)) {
            pattern.matchEntire(aliased)?.let { out += it.groupValues[1].trim() }
        }
        return out
    }

    private fun findBookTrait(entries: List<TraitDefinition>, fixtureName: String): TraitDefinition? {
        val byName = entries.associateBy { it.name }
        val specifyOrRangeBase = buildMap {
            entries.forEach { e ->
                val m = SPECIFY.matchEntire(e.name)
                when {
                    m != null -> put(m.groupValues[1].trim(), e)
                    e.costMin != null || e.variableCost -> put(e.name, e)
                }
            }
        }
        for (candidate in candidateNames(fixtureName)) {
            byName[candidate]?.let { return it }
            val base = PAREN.matchEntire(candidate)?.groupValues?.get(1)?.trim() ?: candidate
            specifyOrRangeBase[base]?.let { return it }
        }
        return null
    }

    private fun checkTraits(
        entries: List<TraitDefinition>,
        fixtureTraitsByName: Map<String, Set<Int>>,
        notFound: MutableSet<String>,
        conflicts: MutableSet<TraitConflict>,
    ) {
        fixtureTraitsByName.forEach { (name, costs) ->
            if (name in KNOWN_FIXTURE_NAME_ARTIFACTS) return@forEach
            val book = findBookTrait(entries, name)
            if (book == null) {
                notFound += name
                return@forEach
            }
            costs.forEach { fixtureCost ->
                val agrees = when {
                    book.cost != null -> book.cost == fixtureCost
                    book.costMin != null -> fixtureCost in book.costMin..book.costMax!!
                    else -> true // variableCost: book states no number at all, nothing to disagree with
                }
                if (!agrees) {
                    val desc = book.cost?.toString() ?: "${book.costMin}-${book.costMax}"
                    conflicts += TraitConflict(name, fixtureCost, "book=$desc")
                }
            }
        }
    }

    @Test
    fun `every fixture advantage and disadvantage resolves to a book entry with agreeing cost, or is a documented conflict`() {
        val advNotFound = mutableSetOf<String>()
        val advConflicts = mutableSetOf<TraitConflict>()
        val disNotFound = mutableSetOf<String>()
        val disConflicts = mutableSetOf<TraitConflict>()

        val advByName = mutableMapOf<String, MutableSet<Int>>()
        val disByName = mutableMapOf<String, MutableSet<Int>>()
        Fixtures.all().forEach { fixture ->
            fixture.advantages.forEach { advByName.getOrPut(it.name) { mutableSetOf() }.add(it.cost) }
            fixture.disadvantages.forEach { disByName.getOrPut(it.name) { mutableSetOf() }.add(it.cost) }
        }

        checkTraits(ContentLoader.advantages(), advByName, advNotFound, advConflicts)
        checkTraits(ContentLoader.disadvantages(), disByName, disNotFound, disConflicts)

        // Genuinely-absent traits (verified: no matching book entry under any spelling).
        assertEquals(emptySet<String>(), advNotFound, "unexpected NOT-FOUND advantages")
        assertEquals(KNOWN_ABSENT_TRAITS, disNotFound, "disadvantage NOT-FOUND set changed")

        // Real cost disagreements between the fixtures and the book -- reported, not bent.
        val expectedAdvConflicts = setOf(
            TraitConflict("Charisma 2", 1, "book=5"),
            TraitConflict("Perfect Balance", 10, "book=20"),
            TraitConflict("Wealth: Comfortable", 10, "book=5"),
        )
        val expectedDisConflicts = setOf(
            TraitConflict("Absent-Minded", 5, "book=15"),
            TraitConflict("Overconfidence", 10, "book=5"),
            TraitConflict("Poverty: Struggling", 5, "book=10"),
        )
        assertEquals(expectedAdvConflicts, advConflicts, "advantage cost-conflict set changed")
        assertEquals(expectedDisConflicts, disConflicts, "disadvantage cost-conflict set changed")
    }

    // ---------------------------------------------------------------
    // Thrust/swing damage table.
    // ---------------------------------------------------------------

    @Test
    fun `thrust swing table has 30 rows with the expected values and plateaus`() {
        val table = ContentLoader.thrustSwing()
        assertEquals(30, table.size)

        assertEquals("1d-2", table.getValue(9).thrust.render())
        assertEquals("1d-1", table.getValue(9).swing.render())

        assertEquals("1d-6", table.getValue(1).thrust.render())

        assertEquals(table.getValue(27).thrust, table.getValue(28).thrust)
        assertEquals(table.getValue(29).swing, table.getValue(30).swing)
    }
}
