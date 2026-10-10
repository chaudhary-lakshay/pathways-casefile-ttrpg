package com.pathways.rules

import com.pathways.rules.content.ContentLoader
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
    fun `the secondary rows of a played character keep the labels and take its numbers`() {
        // Docks Bouncer on Twilight Giant 9: ST +3, DX +2, HT +1.
        val bouncer = Premades.byName("Docks Bouncer")
        val played = PotionDigestion
            .digestThrough(bouncer.toCharacter(), "Twilight Giant", 9, ContentLoader.pathways())
            .secondaries
        val rows = SheetComparison.secondaryRows(played, bouncer.published)
        assertEquals(secondaryLabels, rows.map { it.label })
        assertEquals(listOf("14", "11", "9", "9", "5.50", "5", "8"), rows.map { it.computed })
    }

    @Test
    fun `the Will row is Will and the Per row is Per`() {
        // Every sheet prints Will equal to Per, so no fixture can tell the two
        // rows apart: a Will row reading Per passed all 1937 tests (6fz).
        // Abyss 9 is Will -1, Per +2 on a sheet that prints 10 and 10.
        val dealer = Premades.byName("Antiquarian Dealer")
        val played = PotionDigestion
            .digestThrough(dealer.toCharacter(), "Abyss", 9, ContentLoader.pathways())
            .secondaries
        val rows = SheetComparison.secondaryRows(played, dealer.published).associate { it.label to it.computed }
        assertEquals("9" to "12", rows.getValue("Will") to rows.getValue("Per"))
    }

    @Test
    fun `the diff is the unedited sheet's - Docks Bouncer differs on three of sixteen`() {
        val rows = SheetComparison.of(Premades.byName("Docks Bouncer"))
        assertEquals(3 to 16, rows.count { !it.matches } to rows.size)
        assertEquals(listOf("11", "10", "9", "9", "4.75", "4", "7"), rows.take(7).map { it.computed })
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
