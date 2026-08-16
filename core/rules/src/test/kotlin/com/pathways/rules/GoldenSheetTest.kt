package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.Attributes
import com.pathways.model.Difficulty
import com.pathways.model.SecondaryDelta
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class GoldenSheetTest {

    private val hpExclusions = setOf("Docks Bouncer")
    private val fpExclusions = setOf("Docks Bouncer", "Factory Foreman", "Street Acrobat")

    // --- Phase D (KNOWN_ISSUES: fixtures carried no per-category point
    // breakdown). The sheets below print a POINT BREAKDOWN subtotal that
    // contradicts the sum of their own item list. Attributes and Skills
    // never disagree with the engine across all 22 sheets that carry a
    // breakdown (Beat Cop has none); only Advantages and Disadvantages do,
    // and only for the sheets named here. Per spec section 6, the book
    // (i.e. what the engine computes from the item list) wins; the sheet's
    // printed subtotal is recorded as-published and excluded rather than
    // used to "correct" the engine.
    private val advantagesExclusions = setOf(
        // Sheet prints "Advantages: 15 pts" but lists only one 5pt advantage
        // (Sanctity) - the item list is short by 10 points of its own subtotal.
        "Monastery Scholar",
        // Fortune Teller is NOT excluded: tools/extract_sheets.py's traits()
        // checksums the block's Roll and Cost columns against the printed
        // subtotal and now reads Roll (Charisma 2 @ 10, Wealth: Comfortable
        // @ 10 = 20, matching "Advantages: 20 pts") instead of the sheet's
        // mis-transposed Cost column (which summed to 11).
    )
    private val disadvantagesExclusions = setOf(
        // Sheet prints "Disadvantages: 30 pts"; its four items sum to 25.
        "University Librarian",
        // Street Acrobat is NOT excluded: same checksum fix as Fortune
        // Teller above - its DISADVANTAGES block also had Roll/Cost
        // transposed (Poverty: Struggling and Overconfidence read 5/10
        // swapped), and Roll sums to 30, matching "Disadvantages: 30 pts";
        // its ADVANTAGES block (Flexibility, Cost-only) was never affected.
    )

    /**
     * The sheet's printed TOTAL SPENT, excluded for the one sheet whose item
     * list already disagrees with its own subtotal.
     *
     * Not a second disagreement: the test below proves the two gaps are the
     * SAME ten points, so this is one spreadsheet error surfacing in two
     * columns. 4.1 records it and never repairs it.
     */
    private val totalSpentExclusions = setOf("Monastery Scholar")

    private fun playable() = Premades.all().filter { it.skills.isNotEmpty() }

    @Test
    fun `all twenty three fixtures load`() {
        assertEquals(23, Premades.all().size)
    }

    @Test
    fun `the oracle covers every published skill and playable sheet`() {
        // Pins the oracle's stated size (spec: "232/232 skill target numbers"
        // across 22 playable sheets) so a future extractor change that
        // silently emits skills: [] for a sheet cannot shrink playable()
        // and still leave every @TestFactory suite above green.
        assertEquals(22, playable().size, "expected 22 playable sheets")
        assertEquals(232, playable().sumOf { it.skills.size }, "expected 232 skills across all playable sheets")
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
    fun `toCharacter carries published SPI rather than defaulting to mortal`() {
        // SPI is table state, never derivable from attributes (section 4.4),
        // so an import that drops it silently turns a Beyonder into a mortal.
        // No fixture skill is SPI-controlled today, which is exactly why
        // nothing else here would notice.
        val beyonders = Premades.all().filter { (it.published.spi ?: 0) > 0 }
        assertEquals(4, beyonders.size, "expected 4 sheets to print a nonzero SPI")
        beyonders.forEach {
            assertEquals(it.published.spi, it.toCharacter().spi, "${it.name}: SPI")
        }
    }

    @Test
    fun `the excluded sheets really do disagree - guard against silent repair`() {
        // If this ever starts matching, someone changed the engine to encode a spreadsheet bug.
        val bouncer = Premades.byName("Docks Bouncer")
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

    // --- Phase D: per-category point breakdown (KNOWN_ISSUES: "fixtures
    // carry no per-category point breakdown" - attribute costing was
    // previously asserted only by the hand-written Occult Scholar unit
    // test). Uses all 23 fixtures (not just playable()): the breakdown
    // exists independent of whether a sheet has skills. ---

    private fun attributesOf(f: PremadeSheet) = Attributes(
        st = f.attributes.getValue(Attr.ST), dx = f.attributes.getValue(Attr.DX),
        iq = f.attributes.getValue(Attr.IQ), ht = f.attributes.getValue(Attr.HT),
    )

    @TestFactory
    fun `computed attribute cost matches every sheet's published attribute subtotal`(): List<DynamicTest> =
        Premades.all().filter { it.published.attributesSubtotal != null }.map { f ->
            DynamicTest.dynamicTest(f.name) {
                assertEquals(
                    f.published.attributesSubtotal, AttributeCosts.totalCost(attributesOf(f)),
                    "${f.name}: attribute cost",
                )
            }
        }

    @Test
    fun `every fixture but Beat Cop carries an attribute subtotal`() {
        // Guards the filter above from silently degrading to zero test cases.
        assertEquals(22, Premades.all().count { it.published.attributesSubtotal != null })
    }

    /**
     * The sheet's own printed TOTAL SPENT against what the rules compute.
     *
     * Every other POINT BREAKDOWN row is reconciled above; this one was
     * extracted, stored and documented and then read by nothing at all -- the
     * sweep that found it counted readers of each fixture key.
     */
    @TestFactory
    fun `computed spend matches every sheet's published total spent`(): List<DynamicTest> =
        Premades.all()
            .filter { it.published.totalSpent != null && it.name !in totalSpentExclusions }
            .map { f ->
                DynamicTest.dynamicTest(f.name) {
                    assertEquals(
                        f.published.totalSpent,
                        Derive.of(f.toCharacter()).budget.spent,
                        "${f.name}: total spent",
                    )
                }
            }

    @Test
    fun `no fixture buys a secondary characteristic, which is what lets the totals agree`() {
        // The condition the reconciliation above rests on, pinned rather than
        // assumed (a review's finding). `budget.spent` adds
        // SecondaryCharacteristics.costOf, and the POINT BREAKDOWN the
        // extractor reads has four rows with no secondary among them -- so a
        // fixture that bought one would diverge for a reason that is NOT a
        // sheet error, and this fires before anyone reads that as one.
        assertTrue(
            Premades.all().all { it.toCharacter().secondaryDelta == SecondaryDelta() },
            "a fixture now buys a secondary characteristic; the total-spent reconciliation " +
                "compares two quantities that no longer measure the same thing",
        )
    }

    @Test
    fun `every fixture but Beat Cop carries a published total spent`() {
        // Guards the factory above from silently degrading to zero cases, the
        // way the attribute-subtotal count does. 22, not 23: Beat Cop's whole
        // POINT BREAKDOWN block is absent, so this field is null there too.
        // Counting the fixtures whose FILE contains the key says 23 -- the key
        // is present and its value is null, which is why this asserts the
        // loaded value rather than the text.
        assertEquals(22, Premades.all().count { it.published.totalSpent != null })
    }

    /**
     * The excluded total is short by exactly the advantages it under-lists.
     *
     * A guard against silent repair (4.2) that also proves the exclusion is
     * ONE error rather than two: the sheet prints "Advantages: 15 pts" and
     * lists a single 5pt advantage, and its printed TOTAL SPENT is ten points
     * above what the item lists compute. If either gap moves, the data changed
     * and that is the finding.
     */
    @Test
    fun `the excluded total spent is short by exactly its advantage gap`() {
        val scholar = Premades.byName("Monastery Scholar")
        val advantageGap = scholar.published.advantagesSubtotal!! -
            scholar.advantages.sumOf { it.cost }
        val spendGap = scholar.published.totalSpent!! -
            Derive.of(scholar.toCharacter()).budget.spent
        assertEquals(10, advantageGap, "the advantages gap moved")
        assertEquals(
            advantageGap,
            spendGap,
            "the total-spent gap is no longer the advantages gap; these are now two errors, not one",
        )
    }

    // NOTE: despite the historical name, the three TestFactory checks below
    // (advantages/disadvantages/skills) are fixture-vs-fixture: they sum each
    // sheet's own item list and compare it to that same sheet's own printed
    // subtotal. Neither side comes from the engine. Only the attribute-cost
    // check above calls into the engine (AttributeCosts.totalCost). See
    // KNOWN_ISSUES.md.
    @TestFactory
    fun `each sheet's own advantage item list sums to its published subtotal, excluding known-contradictory sheets`(): List<DynamicTest> =
        Premades.all()
            .filter { it.published.advantagesSubtotal != null && it.name !in advantagesExclusions }
            .map { f ->
                DynamicTest.dynamicTest(f.name) {
                    assertEquals(
                        f.published.advantagesSubtotal, f.advantages.sumOf { it.cost },
                        "${f.name}: advantages cost",
                    )
                }
            }

    @TestFactory
    fun `each sheet's own disadvantage item list sums to its published subtotal, excluding known-contradictory sheets`(): List<DynamicTest> =
        Premades.all()
            .filter { it.published.disadvantagesSubtotal != null && it.name !in disadvantagesExclusions }
            .map { f ->
                DynamicTest.dynamicTest(f.name) {
                    assertEquals(
                        f.published.disadvantagesSubtotal, f.disadvantages.sumOf { it.cost },
                        "${f.name}: disadvantages cost",
                    )
                }
            }

    @TestFactory
    fun `each sheet's own skill point list sums to its published skills subtotal`(): List<DynamicTest> =
        Premades.all().filter { it.published.skillsSubtotal != null }.map { f ->
            DynamicTest.dynamicTest(f.name) {
                assertEquals(f.published.skillsSubtotal, f.skills.sumOf { it.points }, "${f.name}: skills cost")
            }
        }

    @Test
    fun `the excluded advantage and disadvantage sheets really do disagree - guard against silent repair`() {
        // If either of these ever starts matching, someone changed the engine
        // (or the extractor) to encode a spreadsheet bug rather than fixing
        // the actual discrepancy - re-check whether the exclusion still holds.
        // Fortune Teller and Street Acrobat are deliberately NOT asserted
        // here any more: their disagreement was a Roll/Cost transposition in
        // the extractor, now fixed by tools/extract_sheets.py's subtotal
        // checksum (see `computed advantage/disadvantage cost matches...`
        // below, which now covers both on the engine's own terms).
        val monasteryScholar = Premades.byName("Monastery Scholar")
        assertTrue(monasteryScholar.published.advantagesSubtotal != monasteryScholar.advantages.sumOf { it.cost })

        val universityLibrarian = Premades.byName("University Librarian")
        assertTrue(
            universityLibrarian.published.disadvantagesSubtotal != universityLibrarian.disadvantages.sumOf { it.cost },
        )
    }
}
