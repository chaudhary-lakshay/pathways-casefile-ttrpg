package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.TraitClauses.GRANTED_ONLY_SENSES
import com.pathways.rules.TraitEffects.ACUTE_SMELL
import com.pathways.rules.TraitEffects.CLEANSED_SPIRIT
import com.pathways.rules.TraitEffects.GREATER_ACUTE_VISION
import com.pathways.rules.TraitNames.catalogue
import com.pathways.rules.TraitNames.catalogueName
import com.pathways.rules.TraitNames.displayName
import com.pathways.rules.TraitRefusals.PARTLY_APPLIED
import com.pathways.rules.TraitRefusals.UNAPPLIED

/** A trait whose printed effect is real and reaches nothing here, and why. */
data class TraitNote(val trait: String, val printed: String, val reason: String)

/**
 * What a character's held traits print on the sheet, and what of each this
 * app does not apply: [TraitEffects] applies, this object reports (split
 * step 9).
 */
object TraitReport {

    /**
     * What the CoR stepper says to a holder of [CLEANSED_SPIRIT]: its row in
     * [reported], refused whole in [UNAPPLIED] (6fd). The book lists no CoR
     * source as "passive exposure", so nothing is classified, and nothing is
     * halved.
     */
    fun corruptionNotes(traits: List<Trait>): List<TraitNote> =
        reported(traits.filter { catalogueName(it.name).equals(CLEANSED_SPIRIT, ignoreCase = true) })

    /**
     * The traits a character owns whose printed effect is real and lands on
     * nothing this app rolls, each with the reason.
     *
     * Shown rather than silently dropped. A GM who can see "High Pain
     * Threshold -- this app models no shock penalty to ignore" can apply it
     * themselves; one who sees nothing assumes it was counted.
     */
    fun reported(traits: List<Trait>): List<TraitNote> {
        // Both catalogues, because Unfit is a DRAWBACK with a clause to
        // report -- and reading advantages alone made this function blind to
        // exactly the trait it was widened for, which is the shape 6am found
        // one list over when a character's disadvantages rendered nowhere.
        val catalogue = catalogue()
        val known = TraitEffects.known()
        return traits.flatMap { owned ->
            val bare = catalogueName(owned.name)
            val row = catalogue[bare.lowercase()] ?: return@flatMap emptyList()
            val printed = row.notes
            val shown = displayName(owned.name)
            // No collection names it: nothing this cell prints is applied but
            // its price and, where the sheet offers one, its roll. Saying
            // nothing reads as counted. Asked of the code that offers the
            // roll, which refuses a quirk sharing a drawback's name.
            if (bare !in known) {
                val rolled = SelfControl.checksFor(listOf(owned)).isNotEmpty()
                val reason = if (rolled) ROLLED_ONLY else UNREAD
                return@flatMap listOf(TraitNote(shown, printed, reason))
            }
            // The one row whose note depends on what its holder chose.
            if (bare == CharmedObject.ROW) return@flatMap CharmedObject.notes(shown, owned.name, printed)
            val whole = UNAPPLIED[bare]?.let { listOf(TraitNote(shown, printed, it)) }.orEmpty()
            // A trait can be applied and still have rules left over, so the
            // clauses are reported even though the trait itself is not.
            val clauses = PARTLY_APPLIED[bare].orEmpty().map { (clause, reason) ->
                TraitNote(shown, clause, reason)
            }
            whole + clauses
        }
    }

    /**
     * What [reported] says of a held trait no collection here names.
     *
     * **One sentence for the whole complement of [TraitEffects.known]**, which was 148 of
     * the 198 rows when it was first listed (6es) and had never been: 65
     * advantages printed nowhere on the sheet, and the drawbacks printed
     * their cell with no word on whether it was counted. A reason per row
     * would be the honest-looking alternative and is the one that rots --
     * each is a claim about this app that nothing re-reads (6eq). This one is
     * true by construction, PROVIDED every reader of an EFFECT is in [TraitEffects.known].
     *
     * **Nothing enforces that proviso.** The census tests name the readers
     * that were found; they cannot see the next one. Before this sentence a
     * trait missing from [TraitEffects.known] made the sheet silent, and now the sheet
     * says "applies nothing" beside a rule that is applied -- a disclaimer
     * that expires the day a reader is written (6ba). So a new production
     * reader of a trait by name adds it to [TraitEffects.known] in the same commit.
     *
     * **"Its price aside", because a price is read from rows outside
     * [TraitEffects.known]** and the first wording, "reads no rule from this row", was
     * false of five of them (the diff review's MEDIUM): [Wealth] resolves the
     * four `Wealth:` rows and the builder shows their starting money, and
     * [TraitNames.pricedVariants] takes Social Stigma's two prices out of its cell.
     * Neither applies anything the cell prints to a roll or a pool.
     *
     * Read after the sheet's own "Not applied here:" prefix.
     */
    const val UNREAD =
        "its price aside, this app applies nothing this row prints, so its " +
            "text is the table's to apply"

    /**
     * What [reported] says of a held trait outside [TraitEffects.known] whose row offers
     * a self-control roll: [UNREAD], the roll excepted.
     *
     * **6es exempted these rows whole**, on the reading that a row offering
     * its roll is applied. 28 of the 30 self-control rows were outside
     * [TraitEffects.known] (27 since 6ex applied Reluctant Killer's; a sweep test pins
     * the count), and their cells print
     * rules beside the roll, among them Reckless's *"-1 to any roll where
     * caution would be smarter"*, Reluctant Killer's *"-4 to hit ... cannot
     * Aim"*, Absent-Mindedness's -3 and Nightmares' FP. None reached
     * anything and the sheet said nothing. The same proviso as [UNREAD]: a
     * new reader of one of these rows adds it to [TraitEffects.known] in the same commit.
     *
     * Decided by [SelfControl.checksFor], the code that offers the roll, so
     * a quirk sharing a drawback's name is not told its roll is applied.
     * That is also why Cannot Kill, which prints no roll and takes
     * Reluctant Killer's (6hj), reads this and not [UNREAD].
     */
    const val ROLLED_ONLY =
        "its price and its self-control roll aside, this app applies nothing " +
            "this row prints, so the rest of its text is the table's to apply"

    /** How much of a held trait's cell this app applies, in one word for a collapsed row. */
    enum class Applied { APPLIED, PARTLY, ROLL_ONLY, GM, NOT_IN_BOOK }

    /**
     * [row]'s [Applied], read off its cell and notes and nothing else, so the
     * word cannot disagree with the text it stands for (6gl). A note on the
     * whole cell means none of it is applied; a note on a clause, part.
     */
    fun applied(row: HeldTrait): Applied = when {
        row.cell == null -> Applied.NOT_IN_BOOK
        row.notes.isEmpty() -> Applied.APPLIED
        row.notes.any { it.reason == ROLLED_ONLY } -> Applied.ROLL_ONLY
        row.notes.any { it.printed == row.cell } -> Applied.GM
        else -> Applied.PARTLY
    }

    /**
     * One held trait as the sheet prints it: the book's [cell] where the
     * sheet's spelling finds one, and what of it is not applied.
     */
    data class HeldTrait(
        val trait: Trait,
        val shown: String,
        val cell: String?,
        val notes: List<TraitNote>,
    )

    /**
     * Every trait of [kind] in [traits], in sheet order.
     *
     * The advantages had no such list: the sheet printed [reported] and the
     * drawbacks, so an advantage with nothing to report -- applied in full,
     * or read by nothing -- was nowhere on the sheet, and its cell on no
     * screen at all (6am's finding, one list over). A sheet name that matches no row keeps its name and a
     * null cell, which is the record 4.1 asks for.
     */
    fun held(traits: List<Trait>, kind: TraitKind): List<HeldTrait> {
        val catalogue = catalogue()
        return traits.filter { it.kind == kind }.map { owned ->
            val shown = displayName(owned.name)
            val row = catalogue[catalogueName(owned.name).lowercase()]
            val (cell, notes) = if (row != null) row.notes to reported(listOf(owned)) else unrowed(owned, shown)
            HeldTrait(owned, shown, cell, notes)
        }
    }

    /**
     * The cell and notes of a held trait no catalogue row prints, from the
     * table that owns it; (null, none) where none does, which is 4.1's record.
     * A census over every fixture and table row found three owners (6fr).
     */
    private fun unrowed(owned: Trait, shown: String): Pair<String?, List<TraitNote>> {
        // A face: the appearance table's reaction column, which nothing applies (6fq).
        val face = Appearance.played(listOf(owned))
        if (face != null) {
            val cell = Appearance.cellFor(face)
            return cell to listOf(TraitNote(shown, cell, UNREAD))
        }
        // A wealth level by another name ("Poverty: Struggling", nine sheets)
        // prints the book's row; Wealthy and Very Wealthy have none, so the table's.
        val level = Wealth.levelOf(listOf(owned))
        if (level != null) {
            val book = Wealth.traitFor(level)
            val row = book?.let { catalogue()[it.name.lowercase()] }
            if (row != null) return row.notes to reported(listOf(book)).map { it.copy(trait = shown) }
            val cell = Wealth.cellFor(level)
            return cell to listOf(TraitNote(shown, cell, UNREAD))
        }
        // A potion's sense row: its chip is declared on Per rolls (6fi), and
        // two rows print more than the chip carries.
        val name = catalogueName(owned.name)
        val sense = GRANTED_ONLY_SENSES.firstOrNull { it.trait.equals(name, ignoreCase = true) }
            ?: return null to emptyList()
        val rest = SENSE_ROW_REST[sense.trait] ?: return sense.printed to emptyList()
        return "${sense.printed}; $rest" to listOf(TraitNote(shown, rest, SENSE_REST))
    }

    /** What two of those rows print past their bonus (7196-7198, 8355-8357), which nothing reads (6fr). */
    private val SENSE_ROW_REST: Map<String, String> = mapOf(
        ACUTE_SMELL to "detect subtle scents and emotional cues",
        GREATER_ACUTE_VISION to "allows the Pleasure to see their own Spider's Threads",
    )

    private const val SENSE_REST =
        "its bonus aside, declared on Perception rolls, this app applies nothing this row prints, " +
            "so the rest is the table's to apply"
}
