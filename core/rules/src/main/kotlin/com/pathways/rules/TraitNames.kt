package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.TraitDefinition

/**
 * How a sheet's printed trait name resolves to the book's catalogue row, and
 * the level it carries. Moved out of [TraitEffects] unchanged, so that file
 * stays readable (split step 5).
 */
object TraitNames {
    /**
     * The level a character owns [name] at, or null when they do not own it.
     *
     * A sheet prints a levelled trait with the level IN the name --
     * "Alertness 1", "Hard to Kill 2" -- while the catalogue prints the bare
     * name and a `perLevel` flag. An unlevelled trait matches exactly and
     * counts as ONE, never zero: zero would silently cancel its own bonus.
     */
    fun levelOf(traits: List<Trait>, name: String): Int? {
        val owned = traits.firstOrNull {
            catalogueName(it.name).equals(name, ignoreCase = true)
        } ?: return null
        // A trailing integer is a level; anything else is part of the name
        // ("Poverty: Struggling"), so it does not become one.
        return owned.name.substringAfterLast(' ').toIntOrNull() ?: 1
    }

    /**
     * The catalogue row an owned trait's printed name resolves to, or that
     * name unchanged when it resolves to none.
     *
     * **One resolution, shared by everything that matches a trait**: this
     * object's applied bonuses and [TraitReport.reported], [SelfControl.checksFor], and
     * the sheet's disadvantage rows. Two matchers is how a character ends up
     * with a bonus whose explanation vanished -- the
     * two-copies-of-one-decision failure this file records three times over
     * (6ag, 6ai, 6be) and 6bp repeated in a caption.
     *
     * Two shapes resolve. A trailing integer is a LEVEL, so "Alertness 2" is
     * Alertness (6al). And **three premades print the trait's name and the
     * book's own effect cell as one string** -- "Healer - +2 to all rolls to
     * diagnose, treat, and heal; ..." -- because the published sheet ran them
     * together on one line.
     *
     * **The second shape is an exact join, not a fuzzy one.** The tail must
     * equal the catalogue row's own notes, ignoring the whitespace the sheet
     * wrapped it with; the sheet is quoting the book verbatim, so the two are
     * provably the same trait. That is what makes it different from the
     * unmatched spellings 6am recorded -- "Absent-Minded" against
     * "Absent-Mindedness", "Paranoia: Bodies of water" against a bare
     * "Paranoia" -- where the only evidence is resemblance and 4.1 says the
     * disagreement is recorded rather than guessed away. Four other glued
     * names in the fixtures fail this test and stay unmatched.
     */
    fun catalogueName(printed: String): String {
        // A row's own name is that row. `Compulsive Behavior (Indulge Evil
        // Desires) SC 6` ends in its self-control number, and the level strip
        // below lost the row the builder had sold (6ew). Returned in the ROW's
        // spelling: [TraitReport.reported] checks [TraitEffects.known] case-sensitively (Codex).
        rowNames[printed.lowercase()]?.let { return it }
        RULED_SPELLINGS[printed]?.let { return it }
        if (printed.substringAfterLast(' ').toIntOrNull() != null) {
            return printed.substringBeforeLast(' ')
        }
        return placeholderRow(printed) ?: variantRow(printed) ?: gluedHead(printed)
            ?: quotedInstanceRow(printed)
            // The one bracket this app itself composes: the skill a Charmed
            // Object's holder chose (6hk).
            ?: CharmedObject.rowOf(printed) ?: printed
    }

    /**
     * Sheet spellings the owner ruled are a printed row, one by one (6hl).
     *
     * The Apothecary prints "Pacifism (Cannot Kill)" at 15 pts. The book
     * prints no Pacifism heading and does print Cannot Kill at 15, so the
     * resemblance is strong and is still only resemblance: this is a ruling
     * (2026-10-13), not a rule. The same sheet's "Pacifism (Cannot Harm
     * Innocents)" at 10 pts was ruled the same way on a second asking, for
     * the Cannot Harm Innocents row at 10. Exact spellings, so nothing here
     * generalises to another bracket.
     */
    private val RULED_SPELLINGS = mapOf(
        "Pacifism (Cannot Kill)" to "Cannot Kill",
        "Pacifism (Cannot Harm Innocents)" to "Cannot Harm Innocents",
    )

    /**
     * The row a bracket instantiates where a SHEET has proved it does, or null.
     *
     * **One row is in this set and the evidence is a quotation.** Church Healer
     * prints `"Sense of Duty (Flock) - "` followed by that row's own effect
     * cell VERBATIM -- the only spelling in the corpus that quotes exactly
     * behind an instantiated head -- which is the sheet asserting that
     * `Sense of Duty (X)` IS the bare `Sense of Duty` the book prints. That is
     * the standard 6br set for glued names, and [gluedHead] cannot reach it
     * because the head is not itself a row.
     *
     * **The five bare instances follow from that one, and no further.** The
     * row's notes say "certain groups", and Patients, Friends, Public,
     * Coworkers and Workers are each a group -- so they are the same pattern
     * applied, not five independent guesses.
     *
     * **The obvious generalisation is REFUSED, and the reason is worth the
     * space.** It is tempting to say that any row printing a cost interval and
     * enumerating no variants is instantiated by a bracket: that resolves
     * `Secret (Courier network)` too, and it excludes Shyness, Social Stigma
     * and Reputation -- exactly the three objections 6by raised. But excluding
     * the counter-examples is not the same as producing evidence, and the
     * corroboration on offer is the wrong kind: **every instance paying inside
     * its printed interval is evidence about PRICING, never about catalogue
     * IDENTITY** (Codex, on the design). With the filters removed it is the
     * plausible match 4.1 calls repair. So no shape rule was written.
     *
     * **`Secret` is in the set on the owner's ruling (6he), not a quotation.**
     * The builder sells "Secret" with an instance and composes the bare row
     * name, so a sheet's "Secret (Courier network)" reading as unmatched made
     * one purchase two identities. The refusal above still holds for every
     * other interval row.
     */
    private fun quotedInstanceRow(printed: String): String? {
        // The glued spelling carries the quotation AND the bracket, so the
        // tail comes off first; the bare five carry the bracket alone.
        val named = printed.substringBefore(SHEET_SEPARATOR)
        if (!named.endsWith(")")) return null
        val open = named.lastIndexOf(" (")
        if (open <= 0 || named.substring(open + 2, named.length - 1).isBlank()) return null
        val head = named.substring(0, open)
        val row = QUOTED_INSTANCE_ROWS.firstOrNull { it.equals(head, ignoreCase = true) }
            ?: return null
        // **A tail that is present must still be the row's own words.**
        // Dropping it unconditionally would resolve the bracket on a sheet
        // whose quotation was a typo, a partial quote or a different note --
        // silently repairing a disagreement 4.1 says to record, and letting
        // the glued path round 6br's exact-tail rule (Codex).
        if (printed == named) return row
        val tail = printed.substringAfter(SHEET_SEPARATOR)
        val notes = catalogue()[row.lowercase()]?.notes ?: return null
        return row.takeIf { quoted(tail) == quoted(notes) }
    }

    /**
     * Rows a bracket instantiates: Sense of Duty on a sheet's quotation,
     * Secret on the owner's ruling (6he).
     *
     * Named rather than derived, because the evidence lives in the FIXTURES
     * and production cannot read them. A test pins Sense of Duty's quotation,
     * so that warrant fails loudly rather than the set drifting.
     */
    private val QUOTED_INSTANCE_ROWS = listOf("Sense of Duty", "Secret")

    /**
     * A priced option a cell enumerates for itself, as (name, points).
     *
     * Two rows in 198 print one of these, and both are offering a CHOICE:
     * Shyness prints "Mild (-5): ... Severe (-10): ..." and Social Stigma
     * "Criminal Record -5, Servant Class -5".
     *
     * **Two conditions make this safe, and they exclude different rows.**
     *
     * **An enumeration has at least two members.** Allow one and the same
     * pattern reads "Must roll vs. Will-3" as a variant called "Will" priced
     * at 3 -- eleven rows print a number in prose that way, including four
     * self-control targets.
     *
     * **A variant is a NAME, so it is capitalised.** This is the condition
     * that keeps out a SCALE with worked points, and the member count cannot:
     * Mystical Item prints "Cost scales with power: a minor charm (5 pts), a
     * useful tool (10 pts), or a significant piece of equipment (15 pts)" and
     * Duty (specify) prints "-5 for light duty, -10 for hazardous duty, -15
     * for extremely hazardous" -- three priced options each, spanning their
     * own cost range, and neither is a closed set. Their own sentences say the
     * value MOVES ("scales with power", "reflects frequency and danger"), so
     * the rows admit a price between the points they work. Read as
     * enumerations they offer a variant called "or a significant piece of
     * equipment".
     *
     * Together the two take 198 rows down to the two this reads. Both are
     * pinned by tests, because widening the parse is the obvious next move
     * here and the near-misses do not look alike.
     */
    fun pricedVariants(notes: String): List<Pair<String, Int>> {
        val found = PRICED_VARIANT.findAll(notes)
            .map { it.groupValues[1].trim() to it.groupValues[2].toInt() }
            .toList()
        return if (found.size >= 2) found else emptyList()
    }

    /** "Mild (-5)" and "Criminal Record -5": a name, then what it costs. */
    private val PRICED_VARIANT =
        Regex("""([A-Z][A-Za-z]*(?: [A-Za-z]+)*?)\s*\(?[-−](\d+)\)?""")

    /**
     * The row whose cell PRICES the variant a sheet spelling names, or null.
     *
     * **A cell that enumerates its own options names them as surely as a
     * placeholder invites one** (see [placeholderRow]). Academic Researcher
     * buys "Shyness (Mild)" for 5 points and the Shyness row prints
     * "Mild (-5)"; Pickpocket buys "Social Stigma (Criminal)" for 5 and the
     * row prints "Criminal Record -5". The qualifier is named by the book and
     * the price agrees -- two independent agreements, which is what separates
     * this from the resemblance 4.1 refuses.
     *
     * **This corrects a stated reason rather than repairing a disagreement.**
     * 6bv refused both on the grounds that "the sheet added a qualifier the
     * book never asked for, so the only evidence is resemblance". That
     * sentence is simply false for these two, and checking a sweep's own
     * excuse is 6bf's lesson. The two spellings whose reason still holds --
     * Paranoia, Blacklisted -- hang off cells that enumerate nothing, and a
     * test pins them still unmatched. Five Sense of Duty and two Secret were
     * counted with them until [quotedInstanceRow] took them in.
     *
     * The qualifier must PREFIX exactly one option, because the sheet writes
     * "Criminal" where the book writes "Criminal Record". Naming none, or
     * several, refuses rather than guesses (Codex, on the design).
     *
     * The COST is deliberately not read here: this takes a name, and
     * threading a cost through [catalogueName] would give one decision a
     * second matcher. It is the warrant, proven across all 23 fixtures by a
     * test, and the day a sheet pays a price the cell does not print for its
     * variant that test fails rather than this resolving on the name alone.
     */
    private fun variantRow(printed: String): String? {
        val open = printed.lastIndexOf(" (")
        if (open <= 0 || !printed.endsWith(")")) return null
        val qualifier = printed.substring(open + 2, printed.length - 1)
        if (qualifier.isBlank()) return null
        val row = catalogue()[printed.substring(0, open).lowercase()] ?: return null
        val named = pricedVariants(row.notes)
            .filter { it.first.startsWith(qualifier, ignoreCase = true) }
        return if (named.size == 1) row.name else null
    }

    /** How the book asks a sheet to name an instance: "Phobia (specify)". */
    const val PLACEHOLDER = " (specify)"

    /**
     * The placeholder row a printed name is an INSTANCE of, or null.
     *
     * Five rows print [PLACEHOLDER] in their name and eight fixture traits
     * fill it in -- "Phobia (Cities)", three Duties, three Vows, "Resistant
     * (Disease +3)". None resolved before, so every clause they carry was
     * invisible, including the only Fright Check modifier a premade owns.
     *
     * **The warrant is an instruction, not a resemblance.** "(specify)" is the
     * book telling the reader to substitute, and Duty's cell says it in words:
     * "Specify organization and nature at creation." The same reading matched
     * `"Hidden Lore (relevant domain)"` for a ritual's supporting skills
     * (6am), and seven other cells print "(specify)" inside their own prose.
     *
     * **So the join turns on the BOOK printing the word, never on the sheet
     * printing a bracket.** That is what keeps it out of 4.1's territory: the
     * qualifiers 6am refused -- "Paranoia: Bodies of water", "Shyness (Mild)",
     * "Social Stigma (Criminal)", "Sense of Duty (Patients)" -- hang off rows
     * whose names print no placeholder at all, and this join leaves them
     * unmatched. [variantRow] and [quotedInstanceRow] reach the last three,
     * each on evidence of its own; the first matches nothing still.
     *
     * The trailing bracket is the instance, so a row name that carries its own
     * ("Pathway Resonance (Hostile)") cannot be mistaken for a head.
     */
    private fun placeholderRow(printed: String): String? {
        if (!printed.endsWith(")")) return null
        // The LAST bracket first, and then the first: an instance can carry a
        // bracket of its own -- "Language (Balam (Dutan), Accented)" -- and
        // splitting on the last one alone reads the head as "Language (Balam".
        return listOf(printed.lastIndexOf(" ("), printed.indexOf(" (")).distinct()
            .firstNotNullOfOrNull { open ->
                if (open <= 0) return@firstNotNullOfOrNull null
                val inside = printed.substring(open + 2, printed.length - 1)
                // An empty bracket names nothing, and the row is not an
                // instance of itself.
                if (inside.isBlank() || " ($inside)" == PLACEHOLDER) return@firstNotNullOfOrNull null
                val head = printed.substring(0, open)
                catalogue()["${head.lowercase()}$PLACEHOLDER"]?.name
            }
    }

    /**
     * The catalogue row whose name AND effect cell a glued sheet spelling
     * reproduces, or null when no row's does.
     *
     * **One resolution for both callers**, because [displayName] used to split
     * on [SHEET_SEPARATOR] itself: a spelling that resolved for MATCHING while
     * failing to resolve for DISPLAY would apply the trait and render its whole
     * effect cell as the name -- 6br's device-only defect, and a review caught
     * the plan to reintroduce it.
     *
     * **Two glue shapes, one standard of proof.** Three sheets write
     * "Healer - +2 to all rolls to ..." and two write "Clueless -3 to all
     * social skill rolls; ..." with a bare space and no separator at all. The
     * tail must reproduce the row's own notes either way, so the sheet is
     * provably quoting the book rather than resembling it -- which is what
     * separates this from the spellings 6am refused, where a name was similar
     * and nothing else agreed. Four sheet spellings still fail this test and
     * stay unmatched.
     *
     * The longest matching name wins, so a row whose name prefixes another's
     * cannot answer for it.
     */
    private fun gluedHead(printed: String): String? {
        val rows = catalogue()
        // A name that IS a catalogue row carries no glue, and this is the
        // common case: skip the scan rather than searching for a prefix of it.
        if (printed.lowercase() in rows) return null
        return rows.values
            .filter { printed.length > it.name.length && printed.startsWith(it.name) }
            .sortedByDescending { it.name.length }
            .firstOrNull { row ->
                val rest = printed.substring(row.name.length)
                // Exactly the glue, never a greedy trim: a cell that opens
                // with its own "-3" would lose that character to one.
                val tail = when {
                    rest.startsWith(SHEET_SEPARATOR) -> rest.removePrefix(SHEET_SEPARATOR)
                    rest.startsWith(" ") -> rest.removePrefix(" ")
                    else -> return@firstOrNull false
                }
                quoted(tail) == quoted(row.notes)
            }
            ?.name
    }

    /**
     * Two quotations of one sentence, compared as sentences.
     *
     * Only the sheet's typography is normalised away: its line wrapping, the
     * U+2212 it writes where the catalogue writes an ASCII hyphen (the
     * two-kinds-of-minus trap 6u set first, here between two files rather than
     * inside one table), and a trailing full stop that Church Healer drops and
     * Monastery Scholar keeps on the same trait. None of the three can make a
     * quotation a different sentence. Nothing else is touched -- a word that
     * differs is a row that does not match.
     */
    private fun quoted(text: String): String =
        squashed(text).replace('−', '-').trimEnd('.')

    /**
     * What a screen prints as an owned trait's NAME.
     *
     * The sheet's own spelling, minus the book's effect cell where the sheet
     * ran the two together -- because the row displays that cell underneath
     * anyway, so leaving it in the name prints the same sentence twice. **The
     * device is the only thing that could show this**: every test asserted the
     * right lookup and the right clause, and a 150-character name reads as a
     * broken row rather than a wrong one.
     *
     * It collapses ONLY where [catalogueName] verified the tail against the
     * book's own words, so an unmatched glued name keeps every character the
     * sheet printed -- "Sense of Duty (Flock) - Must help/protect certain
     * groups" is a `(specify)` instantiation this book never prints, and 4.1
     * says that disagreement is recorded rather than tidied away. A LEVEL is
     * never stripped either: "Alertness 2" is what the sheet says and what the
     * GM needs to see.
     */
    fun displayName(printed: String): String = gluedHead(printed)
        // A quoted instantiation is glued too, and its head is not a row --
        // so without this the sheet would print the whole effect cell as the
        // trait's NAME and then repeat it as the note underneath. That is
        // 6br's device-only defect, one resolver over (Codex).
        // `substringBefore` returns the whole string when there is no
        // separator, so the bare bracket instances pass through untouched and
        // need no guard of their own.
        ?: printed.takeIf { quotedInstanceRow(it) != null }?.substringBefore(SHEET_SEPARATOR)
        ?: printed

    /** How a published sheet separates a trait's name from its effect cell. */
    private const val SHEET_SEPARATOR = " - "

    /** Both catalogues by lowercase name: a trait may be either kind. */
    /**
     * Every trait row the book prints, keyed for resolution.
     *
     * **The blessings are in here, and that is deliberately the ONLY place
     * they are added.** `reported`, `catalogueName`, `displayName` and
     * `PurchasableTraits.canBuy` all resolve through this one map, so a
     * blessing became a trait for every consumer at once. Widening them one
     * consumer at a time is the two-matchers failure this file has produced
     * six times (6ag, 6ai, 6be, 6bp, 6bs, 6cd) -- a row that resolves for
     * MATCHING while failing to resolve for DISPLAY renders its whole effect
     * cell as the trait's own name.
     *
     * They are a third list rather than a third column in an existing file:
     * the price is stated once in prose for all seven and the table prints no
     * cost column, so writing them into `advantages.json` would make that file
     * claim the book printed one (6v).
     */
    private val rowNames: Map<String, String> by lazy { catalogue().mapValues { it.value.name } }

    internal fun catalogue(): Map<String, TraitDefinition> {
        val cost = ContentLoader.blessingCost()
        return (
            ContentLoader.advantages() +
                ContentLoader.disadvantages() +
                ContentLoader.blessings().map { it.asTrait(cost) }
            ).associateBy { it.name.lowercase() }
    }

    /** Wrapping is the sheet's, so it cannot decide whether two cells agree. */
    private fun squashed(text: String): String =
        text.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ").trim()
}
