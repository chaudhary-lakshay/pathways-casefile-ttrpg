package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** How a sheet's spelling resolves to a catalogue row ([TraitNames]). */
class TraitNamesTest {
    private fun advantage(name: String) = Trait(name, 5, TraitKind.ADVANTAGE)

    // ---- the level in the name -------------------------------------------

    /**
     * A sheet prints "Alertness 1"; the catalogue prints "Alertness" and a
     * flag. An unlevelled trait counts as ONE level of itself, never zero --
     * zero would silently cancel its own bonus.
     */
    @Test
    fun `a level printed in the name is read, and its absence means one`() {
        assertEquals(1, TraitNames.levelOf(listOf(advantage("Alertness 1")), "Alertness"))
        assertEquals(3, TraitNames.levelOf(listOf(advantage("Alertness 3")), "Alertness"))
        assertEquals(1, TraitNames.levelOf(listOf(advantage("Fit")), "Fit"))
        assertNull(TraitNames.levelOf(listOf(advantage("Fit")), "Alertness"))
    }

    /**
     * A name whose last word is not a number keeps it. "Poverty: Struggling"
     * is on ten premades, and reading its tail as a level would invent one.
     */
    @Test
    fun `a trailing word that is not a number is part of the name`() {
        val poverty = Trait("Poverty: Struggling", 10, TraitKind.DISADVANTAGE)
        assertEquals(1, TraitNames.levelOf(listOf(poverty), "Poverty: Struggling"))
        assertNull(TraitNames.levelOf(listOf(poverty), "Poverty"))
    }

    // ---- a sheet that prints the effect beside the name --------------------

    /**
     * **Three premades store a trait's name and the book's own effect cell as
     * ONE string**, because the published sheet printed them on one line:
     * `"Healer - +2 to all rolls to diagnose, treat, and heal; ..."`. Nothing
     * resolved those to a catalogue row, so every clause they carry was
     * invisible -- and `Charity` prints a self-control number, which means the
     * roll 6am shipped could not be made on the character that owns it.
     *
     * **The tail is not a guess: it must equal the catalogue row's own
     * notes.** That is what separates this from the unmatched spellings 6am
     * recorded ("Absent-Minded" against "Absent-Mindedness"), where the only
     * evidence is resemblance. Here the sheet quotes the book verbatim.
     */
    @Test
    fun `a name printed beside the book's own effect resolves to that row`() {
        val healer = ContentLoader.advantages().first { it.name == "Healer" }
        val glued = Trait("Healer - ${healer.notes}", 15, TraitKind.ADVANTAGE)
        assertEquals("Healer", TraitNames.catalogueName(glued.name))
        assertEquals(1, TraitNames.levelOf(listOf(glued), "Healer"))
        // ...and the whitespace the sheet wrapped it with does not matter.
        val wrapped = Trait("Healer - ${healer.notes.replace(" ", "\n")}", 15, TraitKind.ADVANTAGE)
        assertEquals("Healer", TraitNames.catalogueName(wrapped.name))
    }

    /**
     * A tail that is NOT the catalogue's own words stays unmatched. The
     * truncated Sense of Duty below is the near-miss: its tail stops short
     * of the row's own words, and only the COMPLETE quotation licenses that
     * bracket. University Librarian's Blacklisted paraphrase stood here
     * until the owner ruled that one spelling a row (2026-10-13); a
     * different Blacklisted tail takes its place.
     */
    @Test
    fun `a tail that is not the book's own words is left unmatched`() {
        for (name in listOf(
            "Healer - something the book never says",
            "Blacklisted: The Church of Steam",
            // The near-miss: a bracket this app DOES read, behind a tail that
            // stops short of the row's own words. Only the complete quotation
            // licenses it, so a partial one is a disagreement to record.
            "Sense of Duty (Flock) - Must help/protect certain groups",
        )) {
            assertEquals(name, TraitNames.catalogueName(name), name)
        }
        // A level in the name is still a level, not a glued effect.
        assertEquals("Alertness", TraitNames.catalogueName("Alertness 2"))
        // And a colon-suffixed name is part of the name (6al).
        assertEquals("Poverty: Struggling", TraitNames.catalogueName("Poverty: Struggling"))
    }

    /**
     * **"social rolls" and "social skill rolls" are two different things, in
     * the book's own sentence.** Line 6522 prints "(-1 to all emotional
     * reaction rolls and social skill rolls)", so the bare phrase is not an
     * abbreviation of the other -- and it is never defined, never a banner,
     * and used by rows that read like reactions ("first-impression", "people
     * look out for you"). This book prints no reaction roll procedure at all
     * (6w), so which rolls it touches is settled by nothing.
     *
     * Loner is the carried case: Hunter-Tracker owns it, its Will roll is
     * already made by [SelfControl], and this clause is refused beside it.
     */
    /**
     * **Two premades glue Clueless's name to the book's effect cell with a
     * bare space, and resolving only a " - " separator left them unmatched.**
     * Church Healer and Monastery Scholar print
     * "Clueless -3 to all social skill rolls; ..." as one string, so the trait
     * resolved to no catalogue row at all -- no effect line on the sheet, and
     * no way for this slice's -3 to reach the social skills it names.
     *
     * **The join is still exact, which is what keeps it inside 4.1.** The tail
     * is 130 characters of the book's own sentence reproduced verbatim; what
     * differs is the sheet's typography -- its line wrapping, U+2212 where the
     * catalogue writes an ASCII hyphen (the two-kinds-of-minus trap 6u set
     * first), and a trailing full stop that one of the two carriers drops.
     * None of those can make it a different trait. That is what separates it
     * from the resemblances 6am refused, where a NAME was similar and nothing
     * else agreed.
     *
     * Read off the real sheets rather than a copy of their strings, because
     * the claim is about the fixtures and not about this test's spelling.
     */
    @Test
    fun `a bare-space glued name resolves when the tail is the book's own cell`() {
        val carriers = Premades.all().filter { sheet ->
            sheet.disadvantages.any { it.name.startsWith("Clueless") }
        }
        assertEquals(2, carriers.size, carriers.map { it.name }.toString())
        for (sheet in carriers) {
            val printed = sheet.disadvantages.first { it.name.startsWith("Clueless") }.name
            assertTrue(printed.length > 40, "not the glued spelling: $printed")
            assertEquals("Clueless", TraitNames.catalogueName(printed), printed)
            // ...and the sheet shows the name alone, because the row prints
            // the cell underneath anyway (6br's device-only defect).
            assertEquals("Clueless", TraitNames.displayName(printed), printed)
        }
    }

    /**
     * **No long sheet name is left unmatched, and none resolves by glue
     * alone.** University Librarian's "Blacklisted: Specific Industry org or
     * church" paraphrases the row rather than quoting it, so no tail rule
     * reads it. It was the one name listed here until the owner ruled that
     * exact spelling the Blacklisted row (2026-10-13). A new long spelling
     * fails here, and is a question for the owner, not a rule to widen.
     *
     * Church Healer's "Sense of Duty (Flock) - ..." used to be listed here
     * beside it. It is NOT the same case and never was: its tail is the row's
     * own words verbatim, and what refused it was the head. That quotation is
     * now the warrant for the bracket, so it resolves.
     */
    @Test
    fun `an instantiated name the book never prints stays unmatched`() {
        val printed = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .map { it.name }.toSet()
        val glued = Premades.all()
            .flatMap { it.advantages + it.disadvantages }
            .map { it.name }
            .filter { it.length >= 40 }
        val unmatched = glued.filter { TraitNames.catalogueName(it) !in printed }
        assertEquals(
            emptyList<String>(),
            unmatched,
            "the set of unmatched sheet spellings changed",
        )
        // An unmatched name is shown whole: collapsing it would hide the
        // disagreement, and there is no verified cell to collapse it to.
        for (name in unmatched) assertEquals(name, TraitNames.displayName(name))
    }

    /**
     * **Every sheet spelling this app cannot resolve, with why.**
     *
     * There was no such census until a spelling turned out to hide a live
     * defect: nine sheets print "Poverty: Struggling" for the level the book
     * calls "Wealth: Struggling", and the miss was not a blank line but a
     * double-counted point total. The only pin that existed covered names of
     * 40 characters or more, so every short spelling was unwatched.
     *
     * The reasons are not decoration. Three shapes hide in one list:
     *
     * - a **disagreement** the book settles against the sheet, which stays
     *   recorded forever (4.1);
     * - a **paraphrase**, where the sheet instantiates a row without quoting
     *   it, so only resemblance is left;
     * - a **gap**, where the data plainly marks something and this app cannot
     *   see it. A gap is work, not a decision, and calling one a refusal is
     *   how [TraitEffects] shipped four milestones of unreachable rules.
     *
     * The map is the assertion: a spelling that appears without an entry
     * FAILS, so a new one is a finding rather than a silent addition.
     */
    @Test
    fun `every sheet spelling this app cannot resolve is named here`() {
        val printed = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .map { it.name }.toSet()
        val unmatched = Premades.all()
            .flatMap { it.advantages + it.disadvantages }
            .map { it.name }
            .filter { TraitNames.catalogueName(it) !in printed }
            .distinct()
        assertEquals(
            UNRESOLVED_SHEET_SPELLINGS.keys,
            unmatched.toSortedSet(),
            "the set of sheet spellings this app cannot resolve changed",
        )
        // Every one of them is shown whole. Collapsing an unresolved name to
        // the row it resembles is the repair 4.1 forbids, and there is no
        // verified cell to collapse it to.
        for (name in unmatched) assertEquals(name, TraitNames.displayName(name))
    }

    /**
     * The spellings, and the reason each is here. Keep the wording of the
     * REASON honest: it is the thing a later slice will check first, and a
     * false one has already cost this project two slices (a shock penalty the
     * book did print, and a knockdown roll that was never unreachable).
     */
    private val UNRESOLVED_SHEET_SPELLINGS = sortedMapOf(
        // --- the book prints no such row, under any spelling -------------
        "No Sense of Humour" to
            "the book prints no such trait; a text search finds the phrase nowhere",

        // --- resembles a printed row, and the price disagrees -------------
        "Absent-Minded" to
            "the book prints \"Absent-Mindedness\" at 15 and these sheets pay 5, " +
            "so only the name resembles -- the refusal 6am recorded",
        "Paranoia: Bodies of water" to
            "the book prints a bare \"Paranoia\" at 10 and this sheet pays 5, so " +
            "the qualifier is unprinted AND the price disagrees",
        // "Wierdness Magnet" and the Librarian's Blacklisted left this map on
        // the owner's rulings (2026-10-13): each pays its row's own price.

        // --- resolved elsewhere, by design --------------------------------
        "Poverty: Struggling" to
            "resolved by [Wealth], not here: that resolution reads the trait's " +
            "cost and kind, and this API takes a name alone (a design review's " +
            "call in 6bx). The builder's wealth row shows the level",
    )

    /**
     * **The pricing warrant, checked over every sheet rather than asserted.**
     *
     * A row printing "-2 to -15" instead of a price is withheld from the trait
     * picker today, and the question the next slice has to answer is whether a
     * player may buy one at a cost they choose. The book's own cast already
     * does: nine withheld rows are instantiated across the 23 sheets, and all
     * 18 instances pay INSIDE their row's printed interval.
     *
     * That is why this is a test and not a sentence (6bu). The day a sheet
     * pays outside its row's range, the warrant is gone and the reading has to
     * be made again rather than inherited (6ba). It also fails if a row stops
     * printing bounds, which is the other half of the same claim.
     *
     * This reaches names production deliberately refuses -- an audit is a
     * strict SUPERSET of the resolver, free to reach further so it can report
     * on a near-match, never permitted to reach less (6bz).
     */
    @Test
    fun `every instance of a ranged row pays inside the range the book prints`() {
        val ranged = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .filter { it.cost == null && it.costMin != null && it.costMax != null }
            .associateBy { it.name }
        val checked = Premades.all()
            .flatMap { sheet -> (sheet.advantages + sheet.disadvantages).map { sheet.name to it } }
            .mapNotNull { (sheet, owned) ->
                val head = owned.name.substringBefore(" (").substringBefore(" - ")
                val row = ranged[head] ?: ranged["$head${TraitNames.PLACEHOLDER}"]
                    ?: return@mapNotNull null
                assertTrue(
                    owned.cost >= row.costMin!! && owned.cost <= row.costMax!!,
                    "$sheet pays ${owned.cost} for ${owned.name}, outside " +
                        "${row.name}'s printed ${row.costMin}..${row.costMax}",
                )
                owned.name
            }
        assertEquals(19, checked.size, "the fixtures instantiate 19 ranged rows")
        assertEquals(9, checked.map { it.substringBefore(" (") }.distinct().size)
    }

    // ---- the placeholder the book tells the sheet to fill ------------------

    /**
     * **Five catalogue rows print a placeholder in their NAME**, and eight
     * fixture traits across eight premades are instances of four of them:
     * "Phobia (Cities)", three Duties, three Vows, "Resistant (Disease +3)".
     * None resolved before this join, so every clause they carry was
     * invisible -- and one of them is the only Fright Check modifier any
     * premade owns.
     *
     * **The warrant is an INSTRUCTION, not a resemblance.** The book writes
     * "(specify)" where the reader is to substitute, and Duty's own cell says
     * so in words: "Specify organization and nature at creation." A sheet
     * writing "Duty (Regiment)" is obeying that, exactly as
     * `"Hidden Lore (relevant domain)"` was read as an instruction and matched
     * one section over (6am).
     */
    @Test
    fun `an instance of a placeholder row resolves to that row`() {
        val expected = mapOf(
            "Phobia (Cities)" to "Phobia (specify)",
            "Duty (Nighthawks)" to "Duty (specify)",
            "Duty (PMC)" to "Duty (specify)",
            "Duty (Regiment)" to "Duty (specify)",
            "Vow (Poverty)" to "Vow (specify)",
            "Vow (Partial Silence)" to "Vow (specify)",
            "Vow (Vegetarian)" to "Vow (specify)",
            "Resistant (Disease +3)" to "Resistant (specify)",
        )
        // Every one is really on a sheet: a join no premade exercises is a
        // rule nothing can reach (6bc, 6bs).
        val owned = Premades.all()
            .flatMap { it.advantages + it.disadvantages }
            .map { it.name }
            .toSet()
        for ((printed, row) in expected) {
            assertTrue(printed in owned, "$printed is on no sheet any more")
            assertEquals(row, TraitNames.catalogueName(printed), printed)
        }
        // The row itself is not an instance of itself.
        assertEquals("Phobia (specify)", TraitNames.catalogueName("Phobia (specify)"))
        // The instantiation is CONTENT, so the name a screen prints keeps it:
        // "Phobia" alone would drop which fear this character has.
        assertEquals("Phobia (Cities)", TraitNames.displayName("Phobia (Cities)"))
    }

    /**
     * **A qualifier the book never asked for stays unmatched.** These sheets
     * add a bracket or a colon to a catalogue name that prints NO placeholder,
     * so the only evidence is resemblance -- and 4.1 says the disagreement is
     * recorded, never guessed away (6am refused these first).
     *
     * That boundary is the whole reason the join above is legitimate: it turns
     * on the book printing the word, not on the sheet printing a bracket.
     */
    @Test
    fun `a qualifier the book never asked for stays unmatched`() {
        val placeholders = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .map { it.name }
            .filter { it.endsWith(TraitNames.PLACEHOLDER) }
        assertEquals(
            listOf(
                "Language (specify)", "Resistant (specify)",
                "Duty (specify)", "Vow (specify)", "Phobia (specify)",
            ),
            placeholders,
            "the set of rows printing a placeholder changed",
        )
        // Duty says the instruction out loud; the others print the word alone.
        assertTrue(
            ContentLoader.disadvantages().single { it.name == "Duty (specify)" }
                .notes.contains("Specify organization and nature at creation"),
        )
        for (name in listOf(
            "Paranoia: Bodies of water",     // the row is a bare "Paranoia"
            "Blacklisted: The Church of Steam",   // a fixed row, no placeholder
            "Absent-Minded",                 // "Absent-Mindedness" in the book
        )) {
            assertEquals(name, TraitNames.catalogueName(name), name)
        }
    }

    /**
     * **A cell that ENUMERATES its own variants, with their prices, names them
     * as surely as a placeholder invites one.** Two rows do:
     *
     *  - Shyness: "Mild (-5): -1 in groups of 6+; Severe (-10): -2 in groups
     *    of 3+."
     *  - Social Stigma: "Society discriminates: Criminal Record -5, Servant
     *    Class -5"
     *
     * and two sheets buy exactly one of the priced options -- Academic
     * Researcher's "Shyness (Mild)" at 5 pts, Pickpocket's "Social Stigma
     * (Criminal)" at 5 pts. The qualifier is named by the cell AND the cost
     * agrees with the price the cell prints for it: two independent
     * agreements, which is what separates this from resemblance.
     *
     * **This corrects a stated reason, which is not the same as repairing a
     * disagreement (4.1).** 6bv refused these two because "the sheet added a
     * qualifier the book never asked for, so the only evidence is
     * resemblance". That sentence is false for exactly these two, and 6bf's
     * lesson is that a sweep's excuse is the thing to check. The
     * spellings whose reason still holds are pinned unmatched above.
     */
    @Test
    fun `a qualifier the cell itself prices resolves to that row`() {
        assertEquals("Shyness", TraitNames.catalogueName("Shyness (Mild)"))
        assertEquals("Social Stigma", TraitNames.catalogueName("Social Stigma (Criminal)"))
        // The sheet's own spelling is what a screen prints: which variant was
        // bought is content, and it is also what decides Shyness's value.
        assertEquals("Shyness (Mild)", TraitNames.displayName("Shyness (Mild)"))
    }

    /**
     * **An enumeration has at least two members.** Restricted to one, the
     * parse reads "Must roll vs. Will-3" as a variant called "Will" priced at
     * 3, and eleven rows print a number in prose that way. A cell that
     * enumerates variants is offering a CHOICE, so the book prints more than
     * one -- and that alone reduces 198 rows to the two this join reads.
     *
     * Pinned by value: the day a third row starts enumerating, this fails and
     * the reading is made again rather than inherited (6ba).
     */
    @Test
    fun `only the rows that print a choice enumerate variants`() {
        val enumerating = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .filter { TraitNames.pricedVariants(it.notes).isNotEmpty() }
            .map { it.name }
        assertEquals(listOf("Social Stigma", "Shyness"), enumerating)
        assertEquals(
            listOf("Mild" to 5, "Severe" to 10),
            TraitNames.pricedVariants(
                ContentLoader.disadvantages().single { it.name == "Shyness" }.notes,
            ),
        )
        // A lone number in prose is not a choice: Stubborn's "Will-3" would
        // otherwise read as a variant named "Will" costing 3.
        assertTrue(
            TraitNames.pricedVariants(
                ContentLoader.disadvantages().single { it.name == "Stubborn" }.notes,
            ).isEmpty(),
        )
    }

    /**
     * **The quotation that licenses one row's bracket, checked in the fixtures
     * rather than recalled.**
     *
     * Church Healer prints "Sense of Duty (Flock) - " followed by that row's
     * own notes VERBATIM. That is the sheet asserting the instantiated name IS
     * the bare row, and it is the whole warrant for resolving the five other
     * "Sense of Duty (group)" spellings -- so if the sheet is ever re-extracted
     * without it, this fails and the reading has to be made again rather than
     * inherited (6ba).
     */
    @Test
    fun `one sheet quotes the row its bracket instantiates, and that is the warrant`() {
        val row = ContentLoader.disadvantages().single { it.name == "Sense of Duty" }
        val glued = Premades.all()
            .flatMap { it.disadvantages }
            .map { it.name }
            .single { it.startsWith("Sense of Duty (") && it.contains(" - ") }
        val tail = glued.substringAfter(" - ")
        assertEquals(
            row.notes.replace(Regex("\\s+"), " ").trim(),
            tail.replace(Regex("\\s+"), " ").trim(),
            "the quotation this row's resolution rests on is gone",
        )
    }

    /**
     * The bracket resolves for a named row, and for no other.
     *
     * No SHAPE rule: a cost interval with no enumeration is evidence about
     * PRICING, never catalogue IDENTITY (Codex, on the design), so Obsession
     * stays unmatched. Sense of Duty is named on a sheet's quotation, Secret
     * on the owner's ruling (6he).
     */
    @Test
    fun `a bracket resolves only on the row a sheet proved it instantiates`() {
        assertEquals("Sense of Duty", TraitNames.catalogueName("Sense of Duty (Patients)"))
        assertEquals("Sense of Duty", TraitNames.catalogueName("Sense of Duty (Workers)"))
        assertEquals(
            "Sense of Duty",
            TraitNames.catalogueName(
                "Sense of Duty (Flock) - Must help/protect certain groups even at personal risk",
            ),
            "the glued spelling that carries the quotation",
        )
        // The sheet's own spelling still shows, because which group it is is
        // content (6e) -- and the glued one shows the NAME alone, or the sheet
        // would print the effect cell as a title and repeat it underneath.
        assertEquals(
            "Sense of Duty (Patients)",
            TraitNames.displayName("Sense of Duty (Patients)"),
        )
        assertEquals(
            "Sense of Duty (Flock)",
            TraitNames.displayName(
                "Sense of Duty (Flock) - Must help/protect certain groups even at personal risk",
            ),
        )
        // Secret resolves on the owner's ruling, not a quotation (6he): the
        // builder sells "Secret" with an instance, and a sheet's bracket is
        // the same purchase.
        assertEquals("Secret", TraitNames.catalogueName("Secret (Courier network)"))
        assertEquals("Secret", TraitNames.catalogueName("Secret (Racist Sympathizer)"))
        assertEquals("Obsession (A rival)", TraitNames.catalogueName("Obsession (A rival)"))
    }

    /**
     * The Apothecary's "Pacifism (Cannot Kill)" is the Cannot Kill row, on
     * the owner's ruling (6hl, 2026-10-13): the book prints no Pacifism
     * heading, the bracket is a row's whole name, and the sheet pays that
     * row's 15. Its sibling was ruled the same way later that day, on the
     * same grounds at that row's 10. Each is its own exact spelling: a
     * third Pacifism bracket resolves to nothing.
     */
    @Test
    fun `the Apothecary's two Pacifism brackets are the rows they name, by ruling`() {
        assertEquals("Cannot Kill", TraitNames.catalogueName("Pacifism (Cannot Kill)"))
        assertEquals("Pacifism (Cannot Kill)", TraitNames.displayName("Pacifism (Cannot Kill)"))
        assertEquals(
            "Cannot Harm Innocents",
            TraitNames.catalogueName("Pacifism (Cannot Harm Innocents)"),
        )
        assertEquals("Pacifism (Self-Defense Only)", TraitNames.catalogueName("Pacifism (Self-Defense Only)"))
        val apothecary = Premades.all().single { it.name == "Apothecary" }.toCharacter()
        assertEquals(10, apothecary.traits.single { it.name == "Pacifism (Cannot Harm Innocents)" }.cost)
        assertEquals(10, ContentLoader.disadvantages().single { it.name == "Cannot Harm Innocents" }.cost)
        // What the first ruling is for: the premade is offered the roll.
        val check = SelfControl.checksFor(apothecary.traits).single { it.trait == "Pacifism (Cannot Kill)" }
        assertEquals("Will", check.spec.attribute)
        val sheetCost = apothecary.traits.single { it.name == "Pacifism (Cannot Kill)" }.cost
        assertEquals(15, sheetCost)
    }

    /**
     * Two more ruled spellings (2026-10-13), each at its row's own price.
     * The Journalist's "Wierdness Magnet" is a transposition of the book's
     * row: an exact entry, so no other misspelling resolves. The University
     * Librarian's Blacklisted tail restates the row's own cell; the row
     * prints no placeholder, so another tail stays unmatched.
     */
    @Test
    fun `a ruled typo and a ruled tail resolve, and their neighbours do not`() {
        val rows = ContentLoader.disadvantages().associate { it.name to it.cost }
        for ((sheet, printed, row) in listOf(
            Triple("Journalist", "Wierdness Magnet", "Weirdness Magnet"),
            Triple("University Librarian", "Blacklisted: Specific Industry org or church", "Blacklisted"),
        )) {
            assertEquals(row, TraitNames.catalogueName(printed))
            assertEquals(printed, TraitNames.displayName(printed))
            val paid = Premades.byName(sheet).toCharacter().traits.single { it.name == printed }.cost
            assertEquals(rows.getValue(row), paid, printed)
        }
        for (other in listOf("Wierdness Magnett", "Weirdnes Magnet", "Blacklisted: The Church of Steam")) {
            assertEquals(other, TraitNames.catalogueName(other), other)
        }
    }

    /**
     * **A scale with worked points is not a set of variants**, and this is the
     * condition the filter above does NOT explain.
     *
     * `pricedVariants` is safe because of two things, and its KDoc credits one.
     * The "at least two members" rule keeps Stubborn's "Will-3" out, as the
     * test above pins. What keeps a PROSE SCALE out is that a variant is a
     * NAME and a scale point is not:
     *
     *  - Mystical Item: "Cost scales with power: a minor charm (5 pts), a
     *    useful tool (10 pts), or a significant piece of equipment (15 pts)."
     *  - Duty (specify): "Value reflects frequency and danger: -5 for light
     *    duty, -10 for hazardous duty, -15 for extremely hazardous."
     *
     * Both print three priced options spanning their own cost range, so the
     * member count cannot separate them -- only the capital letter does.
     * Mystical Item is the near-miss that shows it: read as an enumeration it
     * offers a variant literally called "or a significant piece of equipment",
     * and its own sentence says the cost SCALES, so the row admits 7 as
     * readily as 5.
     *
     * Pinned because widening the parse is the obvious next move on this
     * object and the two rows look identical to a member count. Nothing can
     * reach the wrong answer today -- no premade owns either row -- which is
     * exactly when this repo pins a permissive path rather than after the next
     * caller finds it (6l).
     */
    @Test
    fun `a prose scale that prices its own examples is not a choice of variants`() {
        val catalogue = ContentLoader.advantages() + ContentLoader.disadvantages()
        val scales = listOf("Mystical Item", "Duty (specify)", "Blood Feud", "Contact")
        for (name in scales) {
            val row = catalogue.single { it.name == name }
            assertTrue(
                TraitNames.pricedVariants(row.notes).isEmpty(),
                "$name prints a scale, not a set of named variants",
            )
        }
        // The evidence, quoted: each says the cost MOVES with something, and
        // names its points in lower case where a variant is capitalised.
        val mystical = catalogue.single { it.name == "Mystical Item" }
        assertTrue(mystical.notes.contains("Cost scales with power"))
        assertTrue(mystical.notes.contains("a minor charm (5 pts)"))
        val duty = catalogue.single { it.name == "Duty (specify)" }
        assertTrue(duty.notes.contains("Value reflects frequency and danger"))
        // ...against a real enumeration, whose members are names.
        assertEquals(
            listOf("Mild" to 5, "Severe" to 10),
            TraitNames.pricedVariants(
                ContentLoader.disadvantages().single { it.name == "Shyness" }.notes,
            ),
        )
    }

    /**
     * The qualifier must select EXACTLY ONE priced variant, and the cost must
     * agree. Social Stigma prices both its variants at 5, so the cost alone
     * cannot say which was bought -- the qualifier does, and the cost only
     * corroborates (Codex, on the design). A qualifier matching two, or one
     * whose price the sheet did not pay, is refused rather than guessed.
     */
    /**
     * A qualifier naming no printed option, or naming two, is refused rather
     * than guessed. The sheet prints "Criminal" where the book prints
     * "Criminal Record", so a qualifier that PREFIXES exactly one option is
     * the join -- never a substring search over the cell (Codex, on the
     * design: "selects exactly one priced variant named by the row").
     */
    @Test
    fun `a qualifier naming no option or several is refused`() {
        assertEquals("Shyness (Crippling)", TraitNames.catalogueName("Shyness (Crippling)"))
        // "S" prefixes both "Servant Class" and nothing else here, but on
        // Shyness it would prefix "Severe" alone; the guard is the count, so
        // a cell whose options shared a prefix could not answer at all.
        assertEquals(
            listOf("Criminal Record" to 5, "Servant Class" to 5),
            TraitNames.pricedVariants(
                ContentLoader.disadvantages().single { it.name == "Social Stigma" }.notes,
            ),
        )
    }

    /**
     * **The WARRANT, checked over every sheet rather than asserted.** The join
     * itself runs on the qualifier alone, because [TraitNames.catalogueName]
     * is handed a name and threading a cost through it would give this
     * decision a second matcher -- the failure this file records six times
     * (6ag, 6ai, 6be, 6bp, 6bs, 6bv). What licenses reading the qualifier at
     * all is that the price agrees too, and that is a fact about the fixtures,
     * so it is proven here across all 23.
     *
     * The day a sheet buys a variant at a price the cell does not print for
     * it, this fails and the disagreement is recorded (4.1) rather than
     * silently resolved on the name alone.
     */
    @Test
    fun `every variant a sheet bought is priced as the cell prints it`() {
        val catalogue = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .associateBy { it.name }
        val checked = Premades.all()
            .flatMap { it.advantages + it.disadvantages }
            .mapNotNull { owned ->
                val row = catalogue[TraitNames.catalogueName(owned.name)] ?: return@mapNotNull null
                val variants = TraitNames.pricedVariants(row.notes)
                if (variants.isEmpty() || owned.name == row.name) return@mapNotNull null
                val qualifier = owned.name.substringAfterLast('(').removeSuffix(")")
                val named = variants.single { it.first.startsWith(qualifier, ignoreCase = true) }
                assertEquals(named.second, owned.cost, "${owned.name} pays ${owned.cost}")
                owned.name
            }
        assertEquals(listOf("Shyness (Mild)", "Social Stigma (Criminal)"), checked.sorted())
    }

    /**
     * **A row's own name must resolve to that row.** The level strip read
     * `Compulsive Behavior (Indulge Evil Desires) SC 6`'s trailing self-control
     * number as a level, so the builder sold a drawback whose cell, roll and
     * note the sheet then could not find (6ew's sweep, the one row that ends
     * in a number).
     */
    @Test
    fun `every catalogue row name resolves to itself`() {
        val lost = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .map { it.name }.filter { TraitNames.catalogueName(it) != it }
        assertEquals(emptyList<String>(), lost)
        // ...in the ROW's spelling, whatever the sheet's casing: `reported`
        // checks `known()` case-sensitively, so a name returned as printed
        // told `PHOBIA (SPECIFY)` that a rule it applies was unread (Codex).
        val recased = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .map { it.name }.filter { TraitNames.catalogueName(it.uppercase()) != it }
        assertEquals(emptyList<String>(), recased)
        assertEquals(
            TraitReport.reported(listOf(advantage("Phobia (specify)"))),
            TraitReport.reported(listOf(advantage("PHOBIA (SPECIFY)")))
                .map { it.copy(trait = "Phobia (specify)") },
        )
        assertEquals("Alertness", TraitNames.catalogueName("Alertness 1"))
    }
}
