package com.pathways.app

import com.pathways.model.Attr
import com.pathways.model.Attributes
import com.pathways.model.BeyonderState
import com.pathways.rules.Premades
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Who the gallery calls a Beyonder (6ft). No sheet prints a pathway, so the
 * answer is the save's: a character is a Beyonder once walked onto one.
 */
class PremadeGalleryTest {

    private val people = Premades.all().filterNot { "Blank" in it.name }
    private val error = BeyonderState("Error", 9, 0)
    private val saved = mapOf(
        "Journalist" to SavedSession(beyonder = error),
        // Opened and edited, never walked.
        "Church Healer" to SavedSession(),
    )

    @Test
    fun `a walked character is a Beyonder whatever SPI its sheet prints`() {
        assertEquals(error, walkOf(Premades.byName("Journalist"), saved))
        assertNull(walkOf(Premades.byName("Church Healer"), saved))
    }

    @Test
    fun `a sheet that prints SPI 9 and has drunk nothing is a mortal`() {
        // Barmaid Brawler prints SPI 9 and no pathway; the old tab read that
        // cell and filed her under Beyonders.
        assertEquals(9, Premades.byName("Barmaid Brawler").published.spi)
        assertNull(walkOf(Premades.byName("Barmaid Brawler"), saved))
    }

    @Test
    fun `the tabs hold the walked and the rest, and nobody twice`() {
        assertEquals(listOf("Journalist"), rosterOf(Roster.BEYONDER, people, saved).map { it.name })
        assertEquals(people.size - 1, rosterOf(Roster.MORTAL, people, saved).size)
        assertEquals(people, rosterOf(Roster.ALL, people, saved))
    }

    @Test
    fun `with nothing saved every character is a mortal`() {
        assertEquals(emptyList<String>(), rosterOf(Roster.BEYONDER, people, emptyMap()).map { it.name })
    }

    // What a row prints (6fw). Until then every number was the sheet's, and
    // the tap led to a character who rolled different ones.

    @Test
    fun `a mortal's bought ST is the row's`() {
        val church = Premades.byName("Church Healer")
        val row = playedRow(church, SavedSession(edits = SavedEdits(st = 12)))
        assertEquals(Attributes(st = 12, dx = 9, iq = 11, ht = 9), row.attributes)
    }

    @Test
    fun `a walked row prints the attributes its potion raised`() {
        // Twilight Giant 9: ST +3, DX +2, HT +1 on a sheet printing 11/9/9/10.
        val bouncer = Premades.byName("Docks Bouncer")
        val row = playedRow(bouncer, SavedSession(beyonder = BeyonderState("Twilight Giant", 9, 0)))
        assertEquals(Attributes(st = 14, dx = 11, iq = 9, ht = 11), row.attributes)
    }

    @Test
    fun `a walked row counts the skills its potion granted`() {
        // Journalist prints 11. Error 9 grants five; Observation is one the
        // sheet already holds, so four are new.
        val row = playedRow(Premades.byName("Journalist"), SavedSession(beyonder = error))
        assertEquals(15, row.skills)
    }

    @Test
    fun `with nothing saved every row is its sheet as printed`() {
        for (sheet in people) {
            val row = playedRow(sheet, null)
            val printed = listOf(Attr.ST, Attr.DX, Attr.IQ, Attr.HT).map { sheet.attributes.getValue(it) }
            assertEquals(printed, row.attributes.run { listOf(st, dx, iq, ht) }, sheet.name)
            assertEquals(sheet.skills.size, row.skills, sheet.name)
        }
    }

    @Test
    fun `a save outside the bounds reads on the row what its sheet opens to`() {
        // ST bought to the model's 30 before the ceilings existed, and Will
        // sold to the bound under Abyss 9's Will -1: digesting it as saved
        // throws twice over. A session repairs both on opening; the row
        // reads the same repair and writes nothing.
        val dealer = Premades.byName("Antiquarian Dealer")
        val abyss = BeyonderState("Abyss", 9, 0)
        val saved = SavedSession(
            beyonder = abyss,
            edits = SavedEdits(st = 30, secondaries = mapOf("will" to -100)),
        )
        assertThrows(IllegalArgumentException::class.java) {
            saved.edits.applyTo(dealer.toCharacter()).digestedWith(abyss)
        }

        val row = playedRow(dealer, saved)

        // The purchase ceiling for ST is 19; Abyss 9 adds 2.
        assertEquals(21, row.attributes.st)
        val opened = BeyonderSession(saved, dealer)
        assertEquals(opened.characterFor(dealer).attributesWith(abyss), row.attributes)
    }

    @Test
    fun `a walk the catalogue does not hold costs one row its numbers, not the launch screen`() {
        val church = Premades.byName("Church Healer")
        val nowhere = SavedSession(beyonder = BeyonderState("Nowhere", 9, 0), edits = SavedEdits(st = 12))
        assertThrows(IllegalArgumentException::class.java) { playedRow(church, nowhere) }
        assertEquals(playedRow(church, null), rowFor(church, nowhere))
    }

    // The chips (6fx): the last thing on the row read from the sheet.

    @Test
    fun `a wealth picked in the builder takes the sheet's wealth off the chips`() {
        // Fortune Teller prints Charisma 2, Wealth: Comfortable, then Secret.
        val teller = Premades.byName("Fortune Teller")
        val row = playedRow(teller, SavedSession(edits = SavedEdits(wealth = "Wealthy")))
        assertEquals(listOf("Charisma 2", "Secret (Racist Sympathizer)"), row.chips)
    }

    @Test
    fun `a chip prints the level a potion raised it to`() {
        // Demoness 8 adds two levels of Charisma to the sheet's two.
        val teller = Premades.byName("Fortune Teller")
        val row = playedRow(teller, SavedSession(beyonder = BeyonderState("Demoness", 8, 0)))
        assertEquals(listOf("Charisma 4", "Wealth: Comfortable"), row.chips)
    }

    @Test
    fun `the chips keep a name and drop the effect a sheet printed beside it`() {
        assertEquals(listOf("Healer", "Resistant (Disease +3)"), playedRow(Premades.byName("Church Healer"), null).chips)
        assertEquals(listOf("Sanctity", "Vow (Partial Silence)"), playedRow(Premades.byName("Monastery Scholar"), null).chips)
    }

    @Test
    fun `with nothing saved every chip is the sheet's first two, in sheet order`() {
        for (sheet in people) {
            val printed = chipTraits((sheet.advantages + sheet.disadvantages).map { it.name })
            assertEquals(printed, playedRow(sheet, null).chips, sheet.name)
        }
    }

    @Test
    fun `no walk moves a chip but a Charisma the potion raised`() {
        // Every premade on every block: a grant joins the end of the list,
        // so only a level added to a held row can reach the first two.
        val moved = people.flatMap { sheet ->
            val printed = playedRow(sheet, null).chips
            ContentLoader.pathways().mapNotNull { block ->
                val chips = playedRow(sheet, SavedSession(beyonder = BeyonderState(block.pathway, block.sequence, 0))).chips
                "${sheet.name} on ${block.pathway} ${block.sequence}: $chips".takeIf { chips != printed }
            }
        }
        // A set: the catalogue's block order is not the claim.
        assertEquals(
            setOf(
                "Fortune Teller on Error 8: [Charisma 3, Wealth: Comfortable]",
                "Fortune Teller on Error 7: [Charisma 3, Wealth: Comfortable]",
                "Fortune Teller on Error 6: [Charisma 3, Wealth: Comfortable]",
                "Fortune Teller on Demoness 8: [Charisma 4, Wealth: Comfortable]",
                "Fortune Teller on Demoness 7: [Charisma 4, Wealth: Comfortable]",
                "Fortune Teller on Demoness 6: [Charisma 4, Wealth: Comfortable]",
            ),
            moved.toSet(),
        )
    }

    @Test
    fun `the row counts the skills the Skills tab lists, on every walk`() {
        // The tab lists the sheet's rows, then the bought, then
        // [grantedSkills]; the row counts the map all three are read from.
        // Two computations of one number, so every premade on every block.
        val splits = people.flatMap { sheet ->
            ContentLoader.pathways().map { block ->
                val walk = BeyonderState(block.pathway, block.sequence, 0)
                val levels = liveSkillLevels(sheet.toCharacter().copy(beyonder = walk))
                val listed = sheet.skills.size + grantedSkills(sheet, SavedEdits(), levels).size
                Triple("${sheet.name} on ${block.pathway} ${block.sequence}", listed, playedRow(sheet, SavedSession(beyonder = walk)).skills)
            }
        }.filter { it.second != it.third }
        assertEquals(emptyList<Triple<String, Int, Int>>(), splits)
    }
}
