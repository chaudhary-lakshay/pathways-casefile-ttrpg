package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.TraitDefinition

/** A catalogue row a player may buy, and what it costs. */
data class PurchasableTrait(
    val name: String,
    val cost: Int,
    val advantage: Boolean,
    val notes: String,
) {
    /** The row as the budget bills it: a magnitude, with the kind giving direction. */
    fun toTrait(): Trait = Trait(
        name,
        cost,
        if (advantage) TraitKind.ADVANTAGE else TraitKind.DISADVANTAGE,
    )
}

/** Why a printed row is not offered, in the book's own terms. */
data class UnofferedTraits(val reason: String, val names: List<String>)

/**
 * Buying a trait in play (Ch. 3's advantage and disadvantage lists).
 *
 * [PointBudget] has billed traits since M0, and until now the only ones a
 * character could have were the ones their sheet printed: `SavedEdits` covered
 * the four attributes, bought skills, and the two point-costed LEVELS (wealth
 * and appearance), each of which has its own picker because each is a single
 * choice from a printed ladder. This is the general case, and it is what makes
 * [TraitEffects.ETHER_BODY_DAMAGE] reachable at all -- no premade carries it,
 * and a potion in this codebase grants attributes, SPI, secondaries and skill
 * levels, never a trait.
 *
 * **Three earlier slices deferred this on a blocker that does not apply.** The
 * stated reason was that a Seven Orthodox Gods Blessing costs 75 points AND
 * grants "+3 ST, +3 DX, +3 HT", which would collide with M3's rule that a
 * granted attribute is never written into a billable field. Checked rather
 * than inherited (6au, 6av): the blessings are not in the catalogue -- that
 * table was read and deliberately not extracted (6bc) -- and **not one of the
 * 198 rows grants an attribute or a secondary**. The collision belongs to that
 * table, which stays deferred, and not to this feature.
 *
 * **Only the rows that print a single cost are offered**, and the rest are
 * NAMED rather than dropped: a row costing "2 to 15" is priced by the instance
 * the player has to describe, and a "(specify)" row needs that description
 * before it means anything. Silently omitting nineteen rows a player can read
 * in the book is the kind of quiet gap this project records instead
 * ([unoffered]).
 */
object PurchasableTraits {

    /**
     * Every row a player may buy, advantages first, each side alphabetical.
     *
     * Wealth is EXCLUDED and that is an invariant, not a display choice. A
     * character has one economic status; [Wealth] owns it, stores it as a
     * chosen level rather than a trait, and replaces whatever row names one
     * when the pick changes (6bx). A general picker offering "Wealth: Poor"
     * would let a player hold two statuses or fight the wealth row for the
     * same points -- so the exclusion lives here, below the UI, where a
     * hand-edited save cannot get round it either.
     */
    fun offered(): List<PurchasableTrait> =
        rows(ContentLoader.advantages(), advantage = true) +
            rows(ContentLoader.disadvantages(), advantage = false)

    /**
     * The printed rows this picker cannot offer, grouped by why.
     *
     * Shown, never silently absent. A player reading the book beside the app
     * has to be able to tell "the app cannot price this yet" from "the app
     * lost it" -- the call the Sealed Artifact grade table (6af) and a
     * technique's availability (6ai) already needed.
     */
    fun unoffered(): List<UnofferedTraits> {
        val all = ContentLoader.advantages() + ContentLoader.disadvantages()
        val ranged = all.filter { it.cost == null && !isWealth(it) }
        val instantiable = ranged.filter { it.name.contains(PLACEHOLDER, ignoreCase = true) }
        val priced = ranged.filterNot { it.name.contains(PLACEHOLDER, ignoreCase = true) }
        return buildList {
            if (priced.isNotEmpty()) {
                add(
                    UnofferedTraits(
                        "the book prints a cost range rather than a price, and the " +
                            "instance settles it",
                        priced.map { it.name }.sorted(),
                    ),
                )
            }
            if (instantiable.isNotEmpty()) {
                add(
                    UnofferedTraits(
                        "the book asks you to name the instance, and this app has " +
                            "nowhere to write one down yet",
                        instantiable.map { it.name }.sorted(),
                    ),
                )
            }
            add(
                UnofferedTraits(
                    "economic status is one choice, made in the Wealth row above",
                    all.filter(::isWealth).map { it.name }.sorted(),
                ),
            )
        }
    }

    /**
     * Whether [trait] may be bought by a character already holding [held].
     *
     * **Refused rather than allowed twice.** [PointBudget] bills every row it
     * is given, while [TraitEffects] reads ONE canonical match per trait -- so
     * a second copy would charge its points and change nothing. Buying up a
     * level is a different feature and needs the levelled rows this slice does
     * not offer.
     *
     * Matched through [TraitEffects.catalogueName], so a sheet's own spelling
     * counts as holding it: two premades print "Clueless" glued to the book's
     * own effect cell, and offering them a second Clueless because the strings
     * differ would be the two-matchers failure this file keeps finding.
     */
    fun canBuy(trait: PurchasableTrait, held: List<Trait>): Boolean =
        held.none { TraitEffects.catalogueName(it.name).equals(trait.name, ignoreCase = true) }

    /**
     * The row called [name], or null when the book no longer prints one.
     *
     * A save stores the NAME alone, exactly as a bought skill and a chosen
     * wealth level do: the cost and the kind belong to the book, and a save
     * carrying its own copy could contradict it after a content fix. Null is
     * how a row that stopped being offered drops out of the billing without
     * being deleted from the file (6as).
     */
    fun named(name: String): PurchasableTrait? =
        offered().firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** How chapter 3 asks for an instance: "Duty (specify)". */
    private const val PLACEHOLDER = "(specify"

    private fun rows(entries: List<TraitDefinition>, advantage: Boolean) = entries
        .filter { it.cost != null && !isWealth(it) }
        .map {
            PurchasableTrait(
                name = it.name,
                cost = it.cost!!,
                advantage = advantage,
                notes = it.notes,
            )
        }
        .sortedBy { it.name }

    private fun isWealth(row: TraitDefinition) = row.name.startsWith(Wealth.TRAIT_PREFIX)
}
