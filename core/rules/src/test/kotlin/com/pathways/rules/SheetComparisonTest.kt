package com.pathways.rules

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * [SheetComparison] is the main-source home of the `computed vs asPublished`
 * diff that previously existed only inside `GoldenSheetTest` (spec section 8).
 * The rules of section 4.1 still hold here: where a sheet disagrees with the
 * book, the comparison REPORTS the disagreement. It never repairs it.
 */
class SheetComparisonTest {

    private val secondaryLabels =
        listOf("HP", "FP", "Will", "Per", "Basic Speed", "Basic Move", "Dodge")

    @Test
    fun `every premade reports all seven secondaries`() {
        Premades.all().forEach { sheet ->
            val labels = SheetComparison.of(sheet).map { it.label }
            secondaryLabels.forEach { assertTrue(it in labels, "${sheet.name}: missing $it row") }
        }
    }

    @Test
    fun `secondaries come first, then one row per purchased skill`() {
        Premades.all().forEach { sheet ->
            val rows = SheetComparison.of(sheet)
            assertEquals(secondaryLabels, rows.take(secondaryLabels.size).map { it.label }, sheet.name)
            assertEquals(
                secondaryLabels.size + sheet.skills.size, rows.size,
                "${sheet.name}: expected one row per secondary plus one per skill",
            )
        }
    }

    @Test
    fun `every published skill level is reported as a match`() {
        Premades.all().filter { it.skills.isNotEmpty() }.forEach { sheet ->
            SheetComparison.of(sheet).drop(secondaryLabels.size).forEach { row ->
                assertTrue(
                    row.matches,
                    "${sheet.name}: ${row.label} computed ${row.computed}, published ${row.published}",
                )
            }
        }
    }

    @Test
    fun `Docks Bouncer reports its HP disagreement instead of repairing it`() {
        val hp = SheetComparison.of(Premades.byName("Docks Bouncer")).first { it.label == "HP" }
        assertFalse(
            hp.matches,
            "Docks Bouncer HP now matches; a recorded oracle disagreement was silently repaired",
        )
    }

    @Test
    fun `most sheets floor Basic Speed and the book does not`() {
        val offenders = Premades.all().count { sheet ->
            !SheetComparison.of(sheet).first { it.label == "Basic Speed" }.matches
        }
        assertTrue(offenders >= 19, "expected at least 19 sheets to disagree on Basic Speed, got $offenders")
    }

    @Test
    fun `a row the sheet printed no value for counts as matching`() {
        assertTrue(SheetRow(label = "Dodge", computed = "7", published = null).matches)
    }
}
