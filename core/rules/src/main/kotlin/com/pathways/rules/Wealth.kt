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
     * [level]'s row of the table (4938-4972), its reaction column worded as
     * 4973 words it. What a held level prints where the trait list prints no
     * row for it: Wealthy and Very Wealthy (6fr).
     */
    fun cellFor(level: WealthLevel): String =
        "Starting money ${level.startingMoney}. " +
            "${if (level.reactionModifier > 0) "+" else ""}${level.reactionModifier} to reaction rolls. " +
            level.lifestyle

    /**
     * The wealth level [traits] carries, or null when it names none.
     *
     * Null is not Average. A sheet that prints no wealth row has said nothing
     * about its character's economic status, and answering "Average" would
     * make that silence look like a choice the book made.
     *
     * **Nine of the 23 sheets spell it "Poverty: Struggling".** The book
     * writes "Wealth: Struggling", and a prefix match answered null for all of
     * them -- so nine characters' economic status was invisible here, and
     * [applied] left their row standing beside whatever the picker chose. See
     * [levelNamedBy] for what makes that spelling resolvable rather than a
     * guess.
     */
    fun levelOf(traits: List<Trait>): WealthLevel? =
        traits.firstNotNullOfOrNull { levelNamedBy(it) }

    /**
     * Whether [trait] names an economic status at all, whatever it costs.
     *
     * **Not the same question as [levelOf].** A row may plainly name a level
     * and still not settle WHICH one the book would give: University
     * Librarian prints "Poverty: Struggling" at 5 where the book prints 10, so
     * the only evidence left that it means Struggling is that the words
     * resemble -- which 4.1 refuses. That row derives no level and must still
     * be replaced by the picker, because a character has one economic status
     * and leaving it would bill two.
     */
    fun namesEconomicStatus(trait: Trait): Boolean = statusNamedBy(trait) != null

    /**
     * [traits] with its wealth level replaced by [level].
     *
     * A character has one economic status, so this replaces rather than adds:
     * a sheet's printed "Wealth: Poor" left beside a bought "Wealth: Wealthy"
     * would raise the budget by 15 and spend 15 in the same breath, and the
     * totals would still look plausible. It removes every row that NAMES a
     * status ([namesEconomicStatus]), not only the ones that resolve to one.
     */
    fun applied(traits: List<Trait>, level: WealthLevel): List<Trait> {
        val without = traits.filterNot(::namesEconomicStatus)
        return traitFor(level)?.let { without + it } ?: without
    }

    /**
     * The printed level [trait] states its character IS, or null.
     *
     * Two doors, and the second needs a second piece of evidence:
     *
     * - The book's own **"Wealth: <status>"** resolves on the status alone,
     *   whatever the sheet charged. Fortune Teller prints "Wealth:
     *   Comfortable" at 10 where the book prints 5 -- a cost conflict recorded
     *   in `ContentLoaderTest`, and still an unambiguous statement of which
     *   level the character is. Requiring price agreement of every row would
     *   have stopped resolving a row that always has (a design review caught
     *   that before this was written).
     * - Any **other head word** resolves only when the trait's cost AND kind
     *   match the level's printed points. That is the two-agreement standard:
     *   the book prints "Poverty" as an economic status nowhere -- its only
     *   two occurrences are a Vow's example list and setting prose -- and
     *   "Struggling" only ever as a wealth level, so the qualifier names one
     *   thing, and the price is the independent second agreement that makes
     *   this a resolution rather than the resemblance 4.1 refuses. Nine sheets
     *   supply it; the tenth does not, and stays recorded.
     *
     * The kind is checked as well as the magnitude, because [Trait] carries a
     * magnitude and puts the direction in its kind -- so a row of the wrong
     * sign could otherwise resolve to a level that happens to cost the same
     * the other way.
     */
    private fun levelNamedBy(trait: Trait): WealthLevel? {
        val level = statusNamedBy(trait) ?: return null
        if (trait.name.startsWith(TRAIT_PREFIX)) return level
        return level.takeIf { trait.cost == magnitudeOf(it) && trait.kind == kindOf(it) }
    }

    /**
     * The level whose status [trait] names, under either printed head word.
     *
     * **Only these two heads, and that is deliberate.** Matching any
     * "<head>: <status>" would let an unrelated row be read as an economic
     * status the moment its qualifier and price happened to line up --
     * "Reputation: Wealthy" at 15 pts resolves under a head-free rule, and
     * [applied] would then DELETE it. No fixture can reach that today, which
     * is precisely the reason to close it now rather than leave it for the
     * next caller to find. Naming the heads also makes a new spelling a
     * finding: an eleventh sheet inventing a third word for the same idea
     * fails a test instead of resolving in silence.
     *
     * "Paranoia: Bodies of water" and "Blacklisted: Specific Industry org or
     * church" are the same shape under other heads and name no level either
     * way; "Vow (Poverty)" is the one other place the book writes the word,
     * and it is a promise priced as a Vow, with no colon to match on.
     */
    private fun statusNamedBy(trait: Trait): WealthLevel? {
        val head = HEADS.firstOrNull { trait.name.startsWith(it) } ?: return null
        val status = trait.name.removePrefix(head)
        return all().firstOrNull { it.status.equals(status, ignoreCase = true) }
    }

    /**
     * The book's head word, then the sheets' own.
     *
     * Nine of the 23 write "Poverty: Struggling" for the level the book calls
     * "Wealth: Struggling". The word is the sheets' alone: the book prints
     * "Poverty" as an economic status nowhere -- its only two occurrences are
     * a Vow's example list and setting prose -- which is why a row under this
     * head has to earn its resolution with the price ([levelNamedBy]) where a
     * row under the book's own does not.
     */
    private val HEADS = listOf(TRAIT_PREFIX, "Poverty: ")

    /** The magnitude [Trait] would carry for [level]; 0 for a free level. */
    private fun magnitudeOf(level: WealthLevel) =
        if (level.points < 0) -level.points else level.points

    /** The kind [Trait] would carry, or null for a level that costs nothing. */
    private fun kindOf(level: WealthLevel): TraitKind? = when {
        level.points > 0 -> TraitKind.ADVANTAGE
        level.points < 0 -> TraitKind.DISADVANTAGE
        else -> null
    }
}
