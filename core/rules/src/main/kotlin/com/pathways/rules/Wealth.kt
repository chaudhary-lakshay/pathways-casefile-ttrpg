package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.WealthLevel

/**
 * Starting Wealth by Economic Status (Ch. 8, p. 80).
 *
 * [PointBudget] has billed traits since M0, so this table's caller was already
 * there -- but chapter 3 prints only four wealth levels among its advantages
 * and disadvantages (Dead Broke, Poor, Struggling, Comfortable). Average,
 * Wealthy and Very Wealthy exist only here, which is why nothing could give a
 * character the two levels above Comfortable until this chapter was read.
 * Where both chapters price a level they agree, and `extract_wealth.py`'s
 * verify() fails if that ever stops being true.
 *
 * **The reaction ladder is not symmetric.** The paragraph under the table says
 * the modifier is "+1 to reaction rolls per Status level above Average ... and
 * -1 per level below Average". Above Average the column obeys that exactly.
 * Below it, it does not: Struggling is one level down and prints 0, and the
 * paragraph's own example puts Poor -- two levels down -- at -1. The printed
 * column is the answer (CLAUDE.md 4.1); deriving the modifier from a level's
 * position would quietly give every Struggling character a penalty the book
 * does not print.
 *
 * The modifier is offered, never applied: this book has no reaction roll to
 * apply it to. It is Status, which is a social situation the GM adjudicates,
 * and the paragraph says so itself -- "GMs may limit this modifier based on
 * context".
 */
object Wealth {

    /** How chapter 3 names these traits on a sheet: "Wealth: Poor". */
    const val TRAIT_PREFIX = "Wealth: "

    /** The seven printed levels, poorest first. */
    fun all(): List<WealthLevel> = ContentLoader.wealth()

    /**
     * The level called [status] ("Wealthy").
     *
     * Refused rather than defaulted: quietly answering Average for a level the
     * book does not print would hand back a character worth 25 points less
     * than the caller asked for, and say nothing.
     */
    fun named(status: String): WealthLevel =
        all().firstOrNull { it.status.equals(status, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "no wealth level called '$status'; the book prints " +
                    all().joinToString { it.status },
            )

    /**
     * [level] as a trait the budget can bill, or null when it costs nothing.
     *
     * The table signs its points; [Trait] carries a magnitude and puts the
     * direction in its kind. This is the single place the two conventions
     * meet, because applying both is how a disadvantage starts paying twice.
     *
     * Average is 0 pts and returns null. A zero-cost trait of either kind
     * would be a row on the sheet that bills nothing -- the same distinction
     * the ritual critical table draws between charging no corruption and
     * charging zero.
     */
    fun traitFor(level: WealthLevel): Trait? = when {
        level.points > 0 -> Trait(TRAIT_PREFIX + level.status, level.points, TraitKind.ADVANTAGE)
        level.points < 0 -> Trait(TRAIT_PREFIX + level.status, -level.points, TraitKind.DISADVANTAGE)
        else -> null
    }

    /**
     * The wealth level [traits] carries, or null when it names none.
     *
     * Null is not Average. A sheet that prints no wealth row has said nothing
     * about its character's economic status, and answering "Average" would
     * make that silence look like a choice the book made.
     */
    fun levelOf(traits: List<Trait>): WealthLevel? = traits
        .firstOrNull { it.name.startsWith(TRAIT_PREFIX) }
        ?.let { trait ->
            val status = trait.name.removePrefix(TRAIT_PREFIX)
            all().firstOrNull { it.status.equals(status, ignoreCase = true) }
        }

    /**
     * [traits] with its wealth level replaced by [level].
     *
     * A character has one economic status, so this replaces rather than adds:
     * a sheet's printed "Wealth: Poor" left beside a bought "Wealth: Wealthy"
     * would raise the budget by 15 and spend 15 in the same breath, and the
     * totals would still look plausible.
     */
    fun applied(traits: List<Trait>, level: WealthLevel): List<Trait> {
        val without = traits.filterNot { it.name.startsWith(TRAIT_PREFIX) }
        return traitFor(level)?.let { without + it } ?: without
    }
}
