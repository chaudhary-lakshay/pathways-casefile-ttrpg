package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.model.TraitKind
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
 * **The cost rules are applied by [owned], and they compose without a
 * ruling.** "Spoken-only or written-only costs half" always lands on a whole
 * number, because every printed price is even, and "Language Talent halves
 * all costs (rounded up)" then carries the only rounding the book prints. No
 * worked example combines them and none is needed: there is one order in
 * which no unprinted rounding ever arises (a design review's reading).
 */
/** One language as a player bought it: three names, and no price. */
data class LanguagePurchase(val language: String, val proficiency: String, val form: String) {
    /** "Hermes, Accented, spoken only" -- the form is said only when it is one. */
    val label: String
        get() = listOfNotNull(
            language,
            proficiency,
            form.lowercase().takeIf { form != Languages.BOTH_FORMS },
        ).joinToString(", ")
}

/** A purchase the book licenses, and what the budget bills for it. */
data class OwnedLanguage(
    val purchase: LanguagePurchase,
    /** [Languages.COMMON] or [Languages.MYSTICAL]. */
    val type: String,
    val cost: Int,
    /**
     * The trait that bills this nothing: one of [Languages.MULTILINGUAL]'s
     * two, or [Languages.DEAD_LANGUAGE_FLUENCY]'s one. Null when billed.
     */
    val freedBy: String?,
    /** The table's own price, before a form or a trait moved it. */
    val listed: Int,
    /** Whether Language Talent's halving is in [cost]. */
    val isHalvedByTalent: Boolean,
) {
    val isFree: Boolean
        get() = freedBy != null

    /**
     * [cost], and what moved it off the table's price. Authored here rather
     * than in the builder, the call [Divination.sourceLine] makes: a screen
     * typing its own account of a number is a second copy of the rule.
     */
    val costLine: String
        get() {
            if (freedBy != null) return "free with $freedBy ($listed listed)"
            val moved = listOfNotNull(
                "half for one form".takeIf { purchase.form != Languages.BOTH_FORMS },
                "halved by ${Languages.TALENT}, rounded up".takeIf { isHalvedByTalent },
            )
            val points = if (cost == 1) "1 pt" else "$cost pts"
            return if (moved.isEmpty()) points else "$points ($listed listed; ${moved.joinToString("; ")})"
        }

    /** The purchase's own label: "Hermes, Accented, spoken only". */
    val label: String
        get() = purchase.label

    /**
     * The purchase as an instance of [Languages.ROW], which is the spelling
     * [TraitNames.catalogueName] resolves for any "(specify)" row.
     */
    fun toTrait(): Trait =
        Trait("${Languages.ROW.removeSuffix(" (specify)")} ($label)", cost, TraitKind.ADVANTAGE)
}

object Languages {

    /** The catalogue row every bought language is an instance of (p. 13). */
    const val ROW = "Language (specify)"

    /** "Points listed are for Spoken + Written at the given level." */
    const val BOTH_FORMS = "Spoken + Written"
    const val SPOKEN_ONLY = "Spoken only"
    const val WRITTEN_ONLY = "Written only"

    /** "Language Talent halves all costs (rounded up)." */
    const val TALENT = "Language Talent"

    /** "grants fluency in two common languages without point cost" (p. 14). */
    const val MULTILINGUAL = "Multilingual"
    const val MULTILINGUAL_LANGUAGES = 2

    /**
     * "Read and speak one dead language (Ancient Hermes, Ancient Loen, etc.)
     * without having formally learned it" (p. 14). The cell prints no level,
     * price or list; the OWNER ruled them on 2026-10-12 (6hh), not the book:
     * [FLUENT], both forms, billed nothing, one of [DEAD_LANGUAGES].
     */
    const val DEAD_LANGUAGE_FLUENCY = "Dead Language Fluency"

    /**
     * The owner's list (6hh): the two Ancient rows the table holds. Jotun is
     * printed "still in daily use", and Ancient Loen is not in the table.
     */
    val DEAD_LANGUAGES = setOf("Ancient Hermes", "Ancient Feysac")

    /**
     * The traits [owned] reads. Exported once so [TraitEffects.known] can
     * count them as applied without a second copy of the names.
     */
    val PRICING_TRAITS = setOf(TALENT, MULTILINGUAL, DEAD_LANGUAGE_FLUENCY)

    /** The row whose effect cell opens "Fluent.", in both tables. */
    const val FLUENT = "Native"

    /** The three ways a language is bought, the full price first. */
    fun forms(): List<String> = listOf(BOTH_FORMS, SPOKEN_ONLY, WRITTEN_ONLY)

    /**
     * Each purchase beside what it became, or null where the book prints no
     * such language, proficiency or form, or the language is already owned.
     *
     * **One call prices, de-duplicates and names**, the shape
     * [PurchasableTraits.resolved] takes for the same reason: a builder that
     * priced a row the composition had dropped would list a cost the budget
     * does not include.
     *
     * The price is the language's own table, halved for one form, halved for
     * [TALENT], and rounded up ONCE. [MULTILINGUAL] then makes the first two
     * purchases that are common, [FLUENT] and in both forms cost nothing --
     * "it does not apply to mystical languages". Which two is this app's
     * convention and changes no total: every eligible purchase costs the
     * same. [DEAD_LANGUAGE_FLUENCY] likewise frees the first purchase of a
     * [DEAD_LANGUAGES] row that is [FLUENT] and in both forms (the owner's
     * ruling, 6hh); the two sets cannot meet, as one is common and one
     * mystical. The book prints no free native tongue, so nothing else is free.
     *
     * A language is owned once, and the FIRST purchase stands. A purchase is
     * validated before it claims its language, so a stale row cannot
     * suppress the corrected one that follows it.
     */
    fun owned(
        purchases: List<LanguagePurchase>,
        held: List<Trait>,
    ): List<Pair<LanguagePurchase, OwnedLanguage?>> {
        val talentDivisor = if (holds(held, TALENT)) 2 else 1
        var free = if (holds(held, MULTILINGUAL)) MULTILINGUAL_LANGUAGES else 0
        var dead = if (holds(held, DEAD_LANGUAGE_FLUENCY)) 1 else 0
        val seen = mutableSetOf<String>()
        return purchases.map { purchase ->
            val entry = all().firstOrNull { it.name.equals(purchase.language, ignoreCase = true) }
            val row = entry?.let { found ->
                proficiencies(found.type)
                    .firstOrNull { it.proficiency.equals(purchase.proficiency, ignoreCase = true) }
            }
            val formDivisor = when (purchase.form) {
                BOTH_FORMS -> 1
                SPOKEN_ONLY, WRITTEN_ONLY -> 2
                else -> null
            }
            if (entry == null || row == null || formDivisor == null || !seen.add(entry.name)) {
                return@map purchase to null
            }
            val fluentInBoth = row.proficiency == FLUENT && purchase.form == BOTH_FORMS
            val freedBy = when {
                !fluentInBoth -> null
                free > 0 && entry.type == COMMON -> MULTILINGUAL.also { free-- }
                dead > 0 && entry.name in DEAD_LANGUAGES -> DEAD_LANGUAGE_FLUENCY.also { dead-- }
                else -> null
            }
            // Ceiling division (4.3): the book rounds UP, and floorDiv on the
            // negated price is how this codebase writes that.
            val cost = if (freedBy != null) 0 else -Math.floorDiv(-row.points, formDivisor * talentDivisor)
            purchase to OwnedLanguage(
                purchase.copy(language = entry.name, proficiency = row.proficiency),
                entry.type,
                cost,
                freedBy,
                listed = row.points,
                isHalvedByTalent = freedBy == null && talentDivisor > 1,
            )
        }
    }

    /** [purchases] as the traits the budget bills, in the order bought. */
    fun traits(purchases: List<LanguagePurchase>, held: List<Trait>): List<Trait> =
        owned(purchases, held).mapNotNull { it.second?.toTrait() }

    /** Through the one matcher every other trait reader uses. */
    private fun holds(held: List<Trait>, name: String): Boolean =
        TraitNames.levelOf(held, name) != null

    const val COMMON = "Common"
    const val MYSTICAL = "Mystical"

    /** All twelve languages, six common then six mystical. */
    fun all(): List<LanguageEntry> = ContentLoader.languages()

    /** The six everyday tongues of Loen. */
    fun common(): List<LanguageEntry> = all().filter { it.type == COMMON }

    /** The six that carry supernatural power. */
    fun mystical(): List<LanguageEntry> = all().filter { it.type == MYSTICAL }

    /**
     * The languages a potion printed (6fo): Mother 8 and Justiciar 6, "Ancient
     * Hermes Fluent -- can speak, read, and write" (book 6783, 8165), which is
     * the [FLUENT] row, whose cell opens "Fluent.", in [BOTH_FORMS]. Free, as
     * every grant is (p. 85), so never a purchase the budget bills.
     */
    private val GRANTED: Map<Pair<String, Int>, LanguagePurchase> = mapOf(
        ("Mother" to 8) to LanguagePurchase("Ancient Hermes", FLUENT, BOTH_FORMS),
        ("Justiciar" to 6) to LanguagePurchase("Ancient Hermes", FLUENT, BOTH_FORMS),
    )

    /** What the walked chain granted, from Sequence 9 down to [state]'s. */
    fun grantedBy(state: com.pathways.model.BeyonderState?): List<LanguagePurchase> =
        if (state == null) {
            emptyList()
        } else {
            GRANTED.filterKeys { (pathway, sequence) -> pathway == state.pathway && sequence >= state.sequence }
                .values.distinct()
        }

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

    /** The prose rules printed around the tables, for the GM to read. */
    fun notes(): List<String> = ContentLoader.languageNotes().map { it.text }
}
