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
 * A row the book prices per instance, and what settling it needs.
 *
 * The book asks for an instance in two notations and they mean the same thing:
 * "(specify)" in the NAME, and a cost RANGE that only an instance can settle.
 * A design review is what joined them -- "a printed range means the row needs
 * an instance/severity/scope to settle cost", so a bare row at an arbitrary
 * price is under-specified. Nineteen instances of nine such rows sit on the 23
 * published sheets and every one pays inside its row's own interval.
 *
 * [variants] is non-empty only where the CELL enumerates priced options
 * (Shyness, Social Stigma). There the variant names the cost and there is
 * nothing to choose inside the range; everywhere else [costMin]..[costMax] is
 * the interval and the instance is prose.
 */
data class InstantiableTrait(
    val name: String,
    val advantage: Boolean,
    val notes: String,
    val costMin: Int,
    val costMax: Int,
    val variants: List<Pair<String, Int>>,
) {
    /** True when the book puts "(specify)" in the name and wants it filled. */
    val needsInstance: Boolean get() = name.endsWith(TraitEffects.PLACEHOLDER)
}

/**
 * One instantiated purchase, as a player made it.
 *
 * The parts are stored and the NAME is composed, never the other way round: a
 * spelling like "Shyness (Mild)" is the SHEETS' convention and the book prints
 * only "Shyness", so persisting it would make an app-authored string into
 * canonical data (Codex, on the design). [PurchasableTraits.instanced] is the
 * one place the parts become a [Trait].
 */
data class InstancedPurchase(
    val row: String,
    val variant: String? = null,
    val instance: String? = null,
    val cost: Int,
) {
    /**
     * What makes two purchases the same purchase.
     *
     * **Not the row alone.** Monastery Scholar carries "Vow (Partial Silence)"
     * AND "Vow (Vegetarian)", so the book's own cast holds one instantiable
     * row twice and a row-keyed refusal would forbid a published character.
     *
     * **And not the variant either**, which is the half a review caught. A
     * variant is a SEVERITY of one condition -- Shyness is Mild or Severe, and
     * a character cannot be both -- where an instance is a different thing
     * entirely, a second vow or a second secret. Since the two are exclusive
     * by construction ([PurchasableTraits.instanced] refuses a purchase
     * carrying both), leaving the variant out gives each shape the rule it
     * needs: an enumerating row keys on itself and admits one, and every other
     * row keys on what the instance is.
     */
    val key: String
        get() = "${row.trim().lowercase()}/${instance?.trim()?.lowercase().orEmpty()}"
}

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
     * The printed rows nothing can buy, grouped by why.
     *
     * Shown, never silently absent. A player reading the book beside the app
     * has to be able to tell "the app cannot price this yet" from "the app
     * lost it" -- the call the Sealed Artifact grade table (6af) and a
     * technique's availability (6ai) already needed.
     *
     * **Every printed row is [offered], [instantiable], or here**, and the
     * three are disjoint. The rows the book prices per instance USED to be
     * listed here; they have a licensed purchase path now, and leaving them
     * would make this object report the same row as buyable and unsupported
     * at once (Codex).
     */
    fun unoffered(): List<UnofferedTraits> {
        val all = ContentLoader.advantages() + ContentLoader.disadvantages()
        val unbounded = all.filter {
            it.cost == null && !isWealth(it) && (it.costMin == null || it.costMax == null)
        }
        return buildList {
            if (unbounded.isNotEmpty()) {
                add(
                    UnofferedTraits(
                        "the book prints no cost bounds for this row at all, so there " +
                            "is no interval to choose inside -- it prices itself by a " +
                            "procedure this app has not read",
                        unbounded.map { it.name }.sorted(),
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

    /**
     * The rows a player instantiates: a printed interval, and what settles it.
     *
     * A row printing NO bounds is deliberately absent. "Reputation" and
     * "Language (specify)" price themselves by a procedure this app does not
     * hold -- the language row points at a cost table of its own -- and an
     * unbounded row is not an interval with no ceiling but a pricing rule
     * nobody has read yet. Offering one would mean taking whatever number the
     * player typed, which makes the SAVE authoritative over the book and
     * inverts the rule the rest of this file follows (Codex, on the design).
     */
    fun instantiable(): List<InstantiableTrait> =
        instantiableIn(ContentLoader.advantages(), advantage = true) +
            instantiableIn(ContentLoader.disadvantages(), advantage = false)

    /** The row called [name], or null when the book prints no instantiable one. */
    fun instantiableNamed(name: String): InstantiableTrait? =
        instantiable().firstOrNull { it.name.equals(name, ignoreCase = true) }

    /**
     * Whether [row] may be instantiated by a character already holding [held].
     *
     * **A row that enumerates variants admits one**, because those are
     * SEVERITIES of one condition: Shyness is Mild or Severe and a character
     * cannot be both, so a premade already printing "Shyness (Mild)" must not
     * be sold "Shyness (Severe)" beside it (Codex). Every other row may repeat
     * -- a second vow is a second vow -- and what repeats there is the
     * instance, which [InstancedPurchase.key] is what refuses.
     *
     * Matched through [TraitEffects.catalogueName], so the sheet's own
     * spelling counts as holding it.
     */
    fun canInstantiate(row: InstantiableTrait, held: List<Trait>): Boolean {
        if (row.variants.isEmpty()) return true
        return held.none { TraitEffects.catalogueName(it.name).equals(row.name, ignoreCase = true) }
    }

    /**
     * [purchase] as the budget and the trait engine see it, or null if the
     * book does not license it.
     *
     * **The name is composed here and stored nowhere**, by three rules:
     *
     *  - a "(specify)" row takes the instance -- "Phobia (Cities)" -- which is
     *    the spelling [TraitEffects.catalogueName] already resolves;
     *  - a row whose cell enumerates variants takes the variant -- "Shyness
     *    (Mild)" -- likewise already resolved, and the variant names the cost;
     *  - **anything else composes the BOOK'S OWN bare name**, keeping the
     *    instance for display alone. "Sense of Duty (Patients)" resolves to
     *    nothing in this app -- a recorded gap, not a refusal -- so composing
     *    it would hand the engine an identity it cannot match, and the trait
     *    would silently do nothing.
     *
     * That third rule is why a caller must never key a duplicate check, an
     * edit, or a delete on the composed name: two "Sense of Duty" purchases
     * differ only in their instance, and by [Trait] alone they are identical
     * (Codex, on the design). [InstancedPurchase.key] is what tells them apart.
     */
    fun instanced(purchase: InstancedPurchase): Trait? {
        val row = instantiableNamed(purchase.row) ?: return null
        val variant = purchase.variant?.let { chosen ->
            row.variants.singleOrNull { it.first.equals(chosen, ignoreCase = true) } ?: return null
        }
        // **Something must settle it, and a bare price is not something.** The
        // two notations are one request, so the interval needs an instance
        // exactly as the placeholder does: an unnamed Secret is a cost with no
        // dangerous hidden truth behind it, and the row's price is supposed to
        // follow from what it is. Where the CELL enumerates, the variant is
        // what settles it and no prose is wanted (Codex).
        if (variant == null && purchase.instance.isNullOrBlank()) return null
        // ...and a variant purchase takes NO instance. It would be dropped
        // from the composed name and kept in the key, so two identical
        // "Shyness (Mild)" purchases carrying different prose would both
        // survive the de-dupe and bill twice -- the identity divergence this
        // whole shape has to avoid (Codex). Refused, not trimmed: a caller
        // that sent one believes it was recorded.
        if (variant != null && !purchase.instance.isNullOrBlank()) return null
        // A cell that prices its own options prices them exactly; everywhere
        // else the interval is what the book prints, and both ends are legal.
        if (variant != null && purchase.cost != variant.second) return null
        if (variant == null && row.variants.isNotEmpty()) return null
        if (purchase.cost < row.costMin || purchase.cost > row.costMax) return null
        val head = row.name.removeSuffix(TraitEffects.PLACEHOLDER)
        val name = when {
            row.needsInstance -> "$head (${purchase.instance!!.trim()})"
            variant != null -> "${row.name} (${variant.first})"
            else -> row.name
        }
        return Trait(
            name,
            purchase.cost,
            if (row.advantage) TraitKind.ADVANTAGE else TraitKind.DISADVANTAGE,
        )
    }

    /**
     * [purchases] as traits, dropping what the book does not license and what
     * repeats.
     *
     * One call rather than a predicate the caller loops over, so a screen and
     * the composition cannot disagree about which purchases count -- the
     * two-copies-of-one-decision failure this codebase records six times.
     * An unlicensed purchase is DROPPED and not deleted, exactly as a saved
     * skill the book no longer prints is (6as): the row may come back.
     */
    fun instancedTraits(
        purchases: List<InstancedPurchase>,
        held: List<Trait> = emptyList(),
    ): List<Trait> = resolved(purchases, held).mapNotNull { it.second }

    /**
     * Each purchase beside what it became, or null where the book does not
     * license it.
     *
     * **One call answers "what does this character have" and "what does the
     * builder show".** Resolving a saved row separately for display let the
     * screen price a purchase the composition had dropped -- a builder listing
     * a trait and a cost the character and the budget do not include, which is
     * the screen-disagrees-with-itself failure this project records six times
     * over (Codex).
     */
    fun resolved(
        purchases: List<InstancedPurchase>,
        held: List<Trait> = emptyList(),
    ): List<Pair<InstancedPurchase, Trait?>> {
        val seen = mutableSetOf<String>()
        return purchases.map { purchase ->
            // The severity rule is enforced HERE and not only in the picker,
            // because a save can be hand-edited: a sheet already printing
            // "Shyness (Mild)" must not gain a saved "Severe" beside it. Same
            // call the wealth exclusion needed one slice ago -- below the UI,
            // where nothing can get round it (Codex).
            val row = instantiableNamed(purchase.row)
            // Validated BEFORE the key is claimed: an entry the book does not
            // license must not consume the slot, or a stale out-of-range save
            // row would suppress the corrected purchase that follows it and
            // the character would silently lose a trait they had paid for
            // (Codex).
            val trait = when {
                row == null || !canInstantiate(row, held) -> null
                else -> instanced(purchase)
            }
            purchase to trait?.takeIf { seen.add(purchase.key) }
        }
    }

    /** How chapter 3 asks for an instance: "Duty (specify)". */
    private const val PLACEHOLDER = "(specify"

    private fun instantiableIn(entries: List<TraitDefinition>, advantage: Boolean) = entries
        .filter { it.cost == null && it.costMin != null && it.costMax != null && !isWealth(it) }
        .map {
            InstantiableTrait(
                name = it.name,
                advantage = advantage,
                notes = it.notes,
                costMin = it.costMin!!,
                costMax = it.costMax!!,
                variants = TraitEffects.pricedVariants(it.notes),
            )
        }
        .sortedBy { it.name }

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
