package com.pathways.app

import com.pathways.model.BeyonderState
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.Premades
import com.pathways.rules.SelfControl
import com.pathways.rules.SheetComparison
import com.pathways.rules.SheetRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * What a row of the sheet's stat list prints (6fz): the number PLAYED, and
 * the spec's `computed vs asPublished` diff beside it. The diff is the
 * unedited sheet's and a potion is not a disagreement with the book, so the
 * red note never follows the played number.
 */
class SheetLinesTest {

    private val agreed = SheetRow("Brawling", "12", "12")
    private val disputed = SheetRow("HP", "11", "9")

    @Test
    fun `an unmoved row the sheet agrees with prints its number alone`() {
        assertEquals(PlayedLine("Brawling", "12", null, null), playedLine(agreed, "12"))
    }

    @Test
    fun `an unmoved row the sheet disputes keeps the note it always had`() {
        assertEquals(PlayedLine("HP", "11", null, " (sheet: 9)"), playedLine(disputed, "11"))
    }

    @Test
    fun `a moved row the sheet agrees with prints the played number and no note`() {
        // A potion is not a disagreement with the book.
        assertEquals(PlayedLine("Brawling", "16", null, null), playedLine(agreed, "16"))
    }

    @Test
    fun `a moved row the sheet disputes says which number the sheet disputes`() {
        assertEquals(
            PlayedLine("HP", "14", " \u00b7 11 from the sheet's points", " (sheet: 9)"),
            playedLine(disputed, "14"),
        )
    }

    @Test
    fun `a row moved onto the sheet's own figure is still disputed at the computed one`() {
        // Played 9 by coincidence: the dispute is 11 against 9, not settled.
        assertEquals(
            PlayedLine("HP", "9", " \u00b7 11 from the sheet's points", " (sheet: 9)"),
            playedLine(disputed, "9"),
        )
    }

    @Test
    fun `a row the sheet printed nothing for never carries a note`() {
        assertEquals(PlayedLine("Dodge", "8", null, null), playedLine(SheetRow("Dodge", "7", null), "8"))
    }

    @Test
    fun `Docks Bouncer on Twilight Giant 9 reads its played secondaries on Vitals`() {
        val bouncer = Premades.byName("Docks Bouncer")
        val walk = BeyonderState("Twilight Giant", 9, 0)
        val lines = vitalLines(bouncer, SheetComparison.of(bouncer), bouncer.toCharacter().secondariesWith(walk))
        assertEquals(
            listOf(
                "HP" to "14", "FP" to "11", "Will" to "9", "Per" to "9",
                "Basic Speed" to "5.50", "Basic Move" to "5", "Dodge" to "8",
            ),
            lines.map { it.label to it.number },
        )
        assertEquals(
            listOf(" (sheet: 9)", " (sheet: 9)", null, null, " (sheet: 4.00)", null, null),
            lines.map { it.disagreement },
        )
        assertEquals(" \u00b7 4.75 from the sheet's points", lines[4].basis)
    }

    @Test
    fun `an unwalked, unedited sheet reads on Vitals exactly what it always did`() {
        for (sheet in Premades.all()) {
            val rows = SheetComparison.of(sheet)
            val lines = vitalLines(sheet, rows, sheet.toCharacter().secondariesWith(null))
            assertEquals(rows.take(7).map { it.computed }, lines.map { it.number }, sheet.name)
            assertEquals(List(7) { null }, lines.map { it.basis }, sheet.name)
        }
    }

    // A self-control row prints the target its dialog opens on. Until 6ga
    // the row left out the shock and the reeling its dialog rolled with.

    private val bouncer = SelfControl.checksFor(Premades.byName("Docks Bouncer").toCharacter().traits)

    @Test
    fun `with nothing weighing on the roll a self-control row prints the book's number`() {
        assertEquals(mapOf("Overconfidence" to 12, "Bully" to 12), standingTargets(bouncer, will = 9, iq = 9, state = 0))
    }

    @Test
    fun `a reeling character's self-control row takes the -1 its dialog rolls with`() {
        assertEquals(mapOf("Overconfidence" to 11, "Bully" to 11), standingTargets(bouncer, will = 9, iq = 9, state = -1))
    }

    // A disadvantage's price on the Traits tab. A gained one is billed at 0
    // (`SavedEdits.gained`), and read "−0 pts" there (play session 1).

    private val cowardice = Trait("Cowardice", 10, TraitKind.DISADVANTAGE)

    @Test
    fun `a bought disadvantage prints its points with a minus`() {
        assertEquals("−10 pts", drawbackPrice(cowardice, emptyList(), "the potion"))
    }

    @Test
    fun `a disadvantage gained in play says so, never minus nothing`() {
        assertEquals("gained in play", drawbackPrice(cowardice.copy(cost = 0), emptyList(), "the potion"))
    }

    @Test
    fun `a granted disadvantage names what granted it`() {
        assertEquals("from the potion", drawbackPrice(cowardice, listOf(cowardice), "the potion"))
    }
}
