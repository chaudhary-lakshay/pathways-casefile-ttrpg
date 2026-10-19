package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.AppearanceLevel
import com.pathways.rules.content.ContentLoader

/**
 * The Appearance ladder (Ch. 3, p. 13-14).
 *
 * The same shape as [Wealth] -- a point-costed level that [PointBudget] bills
 * as a trait -- and it differs in three ways that all matter.
 *
 * **Chapter 3's own trait lists print none of these levels.** Wealth was half
 * printed there, which is what made "Wealth: Poor" the spelling to match on.
 * Nothing here appears among the advantages or disadvantages at all, so the
 * trait carries the level's own printed name and no prefix -- inventing
 * "Appearance: Ugly" would be committing a spelling the book never uses. That
 * disjointness is not assumed: `extract_appearance.py` fails if any of the
 * eight names ever turns up in those lists, and AppearanceTest asserts it
 * from the Kotlin side too.
 *
 * The one chapter 3 trait whose NAME says appearance is "Unsettling
 * Appearance", and it is not a ninth row:
 * the book calls it "a common consequence of Abyss and Chained Pathway
 * potions", so it is something suffered in play, not chosen at creation.
 *
 * **There is no zero-cost middle row.** [Wealth] prints Average at 0 pts, so
 * choosing it is a choice that bills nothing and [Wealth.traitFor] answers
 * null. This table's cheapest row is Attractive at 1 point, so ordinary looks
 * are the ABSENCE of a trait rather than a level -- [traitFor] always returns
 * one, and clearing the choice removes the trait outright.
 *
 * **The costs are this book's, not GURPS's.** The line under the table says so
 * out loud: "Appearance point costs have been adjusted for the Pathways
 * setting and differ from standard GURPS values". Attractive is 1 point here
 * and 4 in GURPS 4e, so a reviewer reaching for the parent system would be
 * correcting the book with its own source material.
 *
 * **The effect is shown, never applied.** v6.9i replaced v6.9d's reaction
 * column with prose at the GM's discretion ("People are subtly warmer to you
 * ..."), and the book prints no reaction roll anywhere. Each level's effect is
 * reported beside the face and the GM adjudicates.
 */
object Appearance {

    /** The eight printed levels, best first. */
    fun all(): List<AppearanceLevel> = ContentLoader.appearance()

    /**
     * The level called [level] ("Ugly").
     *
     * Refused rather than defaulted: quietly answering the cheapest row for a
     * name the book does not print would hand back a character worth up to 25
     * points less than the caller asked for, and say nothing.
     */
    fun named(level: String): AppearanceLevel =
        all().firstOrNull { it.level.equals(level, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "no appearance level called '$level'; the book prints " +
                    all().joinToString { it.level },
            )

    /**
     * [level] as a trait the budget can bill.
     *
     * The table signs its points; [Trait] carries a magnitude and puts the
     * direction in its kind. This is the single place the two conventions
     * meet, because applying both is how a disadvantage starts paying twice.
     *
     * Never null, unlike [Wealth.traitFor]: no row here costs nothing.
     */
    fun traitFor(level: AppearanceLevel): Trait = when {
        level.points > 0 -> Trait(level.level, level.points, TraitKind.ADVANTAGE)
        level.points < 0 -> Trait(level.level, -level.points, TraitKind.DISADVANTAGE)
        else -> throw IllegalStateException(
            "'${level.level}' costs nothing; this table prints no free level, " +
                "so a 0-point row means the content changed",
        )
    }

    /**
     * The appearance level [traits] names, or null when it names none.
     *
     * Null is ordinary looks, which the book does not print as a row. Matching
     * on the bare level name is safe precisely because chapter 3's trait lists
     * carry none of them -- see this object's KDoc.
     */
    fun levelOf(traits: List<Trait>): AppearanceLevel? = played(traits)

    /**
     * The face [traits] play: of the levels they name, the best, or null for
     * none. Two arrive together only when a potion grants one beside a bought
     * one (6fn), and "the potion perfects the Witch's features" never lowers a
     * face, so the better is played. v6.9i dropped the reaction column, so
     * "better" is the cost: the eight are distinct and the good half is
     * positive, so there is never a tie. Granted-replaces-bought is refused on
     * the same line: it would lower a bought Very Beautiful.
     */
    fun played(traits: List<Trait>): AppearanceLevel? {
        val byName = all().associateBy { it.level.lowercase() }
        return traits.mapNotNull { byName[it.name.lowercase()] }.maxByOrNull { it.points }
    }

    /** [level]'s effect cell, copied whole (774-805). What a held face prints (6fq). */
    fun cellFor(level: AppearanceLevel): String = level.effect

    /** Whether [trait] is a face [traits] hold and do not play (6fn): a bought one a potion beat. */
    fun isSuperseded(trait: Trait, traits: List<Trait>): Boolean =
        played(listOf(trait)) != null && played(traits)?.level?.equals(trait.name, ignoreCase = true) != true

    /**
     * [traits] with its appearance level replaced by [level].
     *
     * A character has one face. Ugly left beside a bought Attractive would
     * refund 8 points and spend 1 in the same breath, and both totals would
     * still look plausible.
     */
    fun applied(traits: List<Trait>, level: AppearanceLevel): List<Trait> =
        without(traits) + traitFor(level)

    /** [traits] with any appearance level removed -- back to ordinary looks. */
    fun without(traits: List<Trait>): List<Trait> {
        val names = all().map { it.level.lowercase() }.toSet()
        return traits.filterNot { it.name.lowercase() in names }
    }
}
