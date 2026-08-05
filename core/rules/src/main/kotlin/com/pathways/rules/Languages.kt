package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.LanguageEntry
import com.pathways.rules.content.LanguageProficiency

/**
 * Languages, and the one of them that reaches the dice (Ch. 3, p. 13-14).
 *
 * **The section prints two proficiency tables with the same three row names.**
 * Native / Accented / Broken cost 6/4/2 as a common language and 30/20/10 as a
 * mystical one, and nothing inside a row says which table it came from -- only
 * the header above it does. Every lookup here therefore takes a `type`, and
 * [proficiency] refuses a pair the book does not print rather than returning
 * whichever row it met first. Merging them prices a mystical Native at a fifth
 * of the book's cost.
 *
 * **Only the mystical table speaks about rituals**, which is the half with a
 * caller: [RitualResolution]. Accented is "-2 to language-dependent skill
 * rolls. Rituals using the language at -2", and Broken is "Cannot perform
 * rituals in the language" -- refused by [ritualModifier] rather than given a
 * penalty, because a modifier implies a roll exists to modify.
 *
 * A COMMON row is refused too, and for a different reason: it says nothing
 * about rituals at all. Reading that silence as +0 would let a ritual be cast
 * in everyday Loen, which is the "a missing key and an unlisted type are not
 * the same thing" distinction [Wounds] already had to draw.
 *
 * **The -2 and -3 to "language-dependent skill rolls" are NOT applied.** This
 * book tags no skill as language-dependent -- it prints Body Language (Per)
 * and Linguistics, which a name match would wrongly catch -- so which rolls
 * they touch is the GM's call, exactly as with a reaction modifier. Only the
 * ritual clause is scoped tightly enough for the app to apply it.
 *
 * **The cost rules stay sentences.** "Spoken-only or written-only costs half"
 * and "Language Talent halves all costs (rounded up)" interact, and the book
 * combines them in no worked example. Nothing buys a language yet -- the
 * builder edits attributes, skills, wealth and looks -- so a calculator here
 * would be arithmetic nobody can check against a printed total. See [notes].
 */
object Languages {

    const val COMMON = "Common"
    const val MYSTICAL = "Mystical"

    /** All twelve languages, six common then six mystical. */
    fun all(): List<LanguageEntry> = ContentLoader.languages()

    /** The six everyday tongues of Loen. */
    fun common(): List<LanguageEntry> = all().filter { it.type == COMMON }

    /** The six that carry supernatural power. */
    fun mystical(): List<LanguageEntry> = all().filter { it.type == MYSTICAL }

    /** One table's three rows, best first. */
    fun proficiencies(type: String): List<LanguageProficiency> =
        ContentLoader.languageProficiencies().filter { it.type.equals(type, ignoreCase = true) }

    /**
     * The row for [proficiency] in [type]'s table.
     *
     * Both halves of the key are required and neither is defaulted: the two
     * tables print identical row names, so a lookup missing its type has no
     * right answer to fall back on.
     */
    fun proficiency(type: String, proficiency: String): LanguageProficiency =
        proficiencies(type).firstOrNull { it.proficiency.equals(proficiency, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "no '$proficiency' row in the $type language table; the book prints " +
                    (proficiencies(type).takeIf { it.isNotEmpty() }
                        ?.joinToString { it.proficiency }
                        ?: "no such table, only $COMMON and $MYSTICAL"),
            )

    /**
     * What casting in [proficiency] does to a ritual roll, or 0 when none was
     * stated.
     *
     * Refuses rather than answers for the two rows that cannot cast at all --
     * mystical Broken, which the book forbids outright, and any common row,
     * which the book never permits. A caller holding a proficiency asks
     * [canPerformRituals] first, exactly as one holding a damage type asks
     * [Wounds.knowsType] before asking for a multiplier.
     */
    fun ritualModifier(proficiency: LanguageProficiency?): Int {
        if (proficiency == null) return 0
        require(canPerformRituals(proficiency)) {
            if (proficiency.canPerformRituals == false) {
                "${proficiency.proficiency} prints 'Cannot perform rituals in the " +
                    "language'; there is no roll to modify"
            } else {
                "${proficiency.type} languages are not ritual languages in this " +
                    "book; only the mystical table grants ritual power"
            }
        }
        return proficiency.ritualModifier ?: 0
    }

    /** Whether the book lets [proficiency] cast at all. */
    fun canPerformRituals(proficiency: LanguageProficiency): Boolean =
        proficiency.canPerformRituals == true

    /** Every mystical proficiency a ritual can actually be cast in. */
    fun ritualProficiencies(): List<LanguageProficiency> =
        proficiencies(MYSTICAL).filter { canPerformRituals(it) }

    /** The prose rules printed around the tables, for the GM to read. */
    fun notes(): List<String> = ContentLoader.languageNotes().map { it.text }
}
