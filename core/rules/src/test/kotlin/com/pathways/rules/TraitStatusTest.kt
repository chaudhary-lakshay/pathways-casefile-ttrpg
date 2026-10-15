package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.TraitReport.Applied
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What one word on a collapsed Traits row says of a held trait. The tab
 * shows the status and hides the cell until asked, so the status must never
 * read as counted where the notes say otherwise (6gl).
 */
class TraitStatusTest {

    private fun statusOf(name: String, kind: TraitKind = TraitKind.ADVANTAGE) =
        TraitReport.applied(TraitReport.held(listOf(Trait(name, 5, kind)), kind).single())

    @Test
    fun `each kind of held row gets the status its notes say`() {
        assertEquals(Applied.APPLIED, statusOf("Alertness"))
        // "+1 to all HT rolls" applied, "recover FP at twice the normal rate" not.
        assertEquals(Applied.PARTLY, statusOf("Fit"))
        assertEquals(Applied.ROLL_ONLY, statusOf("Overconfidence", TraitKind.DISADVANTAGE))
        assertEquals(Applied.GM, statusOf("Legal Enforcement Powers"))
        assertEquals(Applied.GM, statusOf("Voice"))
        // 6am's recorded spelling: no row, so nothing is claimed of it.
        assertEquals(Applied.NOT_IN_BOOK, statusOf("Absent-Minded", TraitKind.DISADVANTAGE))
    }

    /**
     * A quirk a Fright Check hands out is named by the GM (6hv), so it never
     * matches a catalogue row, and play session 3 saw it tagged "Unmatched
     * name". The book does define the kind: "minor personality traits at -1
     * pt each" (line 1122). That is its cell, and the GM plays it.
     */
    @Test
    fun `a quirk gained in play is the GM's to play, never an unmatched name`() {
        val row = TraitReport.held(listOf(Trait("Avoids old mirrors", 0, TraitKind.QUIRK)), TraitKind.QUIRK).single()
        assertEquals("minor personality traits", row.cell)
        assertEquals(Applied.GM, TraitReport.applied(row))
    }

    /**
     * Properties, not a second copy of the rule: a row with anything left
     * unapplied never reads as applied, a row with no catalogue entry claims
     * nothing, and "roll only" is said only where the roll is offered.
     */
    @Test
    fun `no reachable row reads as applied while a note says otherwise`() {
        val rows = reachableHeldTraits().map { TraitReport.held(listOf(it), it.kind).single() }
        for (row in rows) {
            val status = TraitReport.applied(row)
            assertEquals(row.cell == null, status == Applied.NOT_IN_BOOK, row.shown)
            assertEquals(row.notes.isEmpty() && row.cell != null, status == Applied.APPLIED, row.shown)
            if (status == Applied.ROLL_ONLY) {
                assertTrue(SelfControl.checksFor(listOf(row.trait)).isNotEmpty(), row.shown)
            }
        }
        assertEquals(Applied.entries.toSet(), rows.map(TraitReport::applied).toSet())
    }
}
