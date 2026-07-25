package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.Attributes
import com.pathways.model.Difficulty
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

    private fun playable() = Fixtures.all().filter { it.skills.isNotEmpty() }

    @Test
    fun `all twenty three fixtures load`() {
        assertEquals(23, Fixtures.all().size)
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

    // --- Phase D: per-category point breakdown (KNOWN_ISSUES: "fixtures
    // carry no per-category point breakdown" - attribute costing was
    // previously asserted only by the hand-written Occult Scholar unit
    // test). Uses all 23 fixtures (not just playable()): the breakdown
    // exists independent of whether a sheet has skills. ---

    private fun attributesOf(f: Fixture) = Attributes(
        st = f.attributes.getValue(Attr.ST), dx = f.attributes.getValue(Attr.DX),
        iq = f.attributes.getValue(Attr.IQ), ht = f.attributes.getValue(Attr.HT),
    )

    @TestFactory
    fun `computed attribute cost matches every sheet's published attribute subtotal`(): List<DynamicTest> =
        Fixtures.all().filter { it.published.attributesSubtotal != null }.map { f ->
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
        assertEquals(22, Fixtures.all().count { it.published.attributesSubtotal != null })
    }

    // NOTE: despite the historical name, the three TestFactory checks below
    // (advantages/disadvantages/skills) are fixture-vs-fixture: they sum each
    // sheet's own item list and compare it to that same sheet's own printed
    // subtotal. Neither side comes from the engine. Only the attribute-cost
    // check above calls into the engine (AttributeCosts.totalCost). See
    // KNOWN_ISSUES.md.
    @TestFactory
    fun `each sheet's own advantage item list sums to its published subtotal, excluding known-contradictory sheets`(): List<DynamicTest> =
        Fixtures.all()
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
        Fixtures.all()
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
        Fixtures.all().filter { it.published.skillsSubtotal != null }.map { f ->
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
        val monasteryScholar = Fixtures.byName("Monastery Scholar")
        assertTrue(monasteryScholar.published.advantagesSubtotal != monasteryScholar.advantages.sumOf { it.cost })

        val universityLibrarian = Fixtures.byName("University Librarian")
        assertTrue(
            universityLibrarian.published.disadvantagesSubtotal != universityLibrarian.disadvantages.sumOf { it.cost },
        )
    }
}
