package com.pathways.rules

import com.pathways.model.Outcome
import com.pathways.model.RollResult
import com.pathways.rules.content.AntiDivinationItem
import com.pathways.rules.content.AntiDivinationTechnique
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.DetectionModifier
import com.pathways.rules.content.DetectionOutcome
import com.pathways.rules.content.DivinationAwareness

/** One target's attempt to feel a divination, and what it told them. */
data class DetectionResult(
    val roll: RollResult,
    val row: DetectionOutcome,
    val parts: List<String>,
)

/**
 * Feeling a divination land on you (Ch. 6.5, Sec. 1, 2 and 4).
 *
 * [AntiDivination] is the DIVINER's side of this chapter; this is the
 * TARGET's. The roll is the target's own -- "the GM calls for a SPI or Per
 * roll (whichever is higher)" -- which is why it does not live in the
 * divination dialog: there the played character is the diviner and the target
 * is a declared Sequence with no sheet to roll from.
 *
 * **Sec. 1 is a gate before it is a number.** Its eight rows answer whether
 * there is a roll at all -- automatic awareness, a free roll, or nothing --
 * and only three of them also print a bonus. That is why it stays its own
 * table beside Sec. 2's six situations rather than being merged into one
 * modifier list.
 *
 * **Four sources reach one roll**, the shape [Conditions], [Attacks],
 * [Ranged] and [CommonModifiers] already have on a skill roll: Sec. 1's
 * ability bonus, Sec. 2's situation, Sec. 3A's ward, and Sec. 4's pendant. So
 * they compose here and nowhere else, and the scope travels with each number.
 *
 * **One item is priced twice.** Sec. 2 prints "Target carries a protective
 * charm or item / +1" and Sec. 4's Concealment Pendant is worth +2. Specific
 * beats general, so [modifierTotal] REFUSES the pair rather than summing them
 * -- the scoping Appendix A's adverse-conditions row already needed against
 * chapter 5's darkness (6ae). Refused rather than silently dropped, so a
 * caller cannot believe the charm was counted twice.
 *
 * **Outcome first, then the last matching margin.** Four rows are named by
 * outcome and one by margin ("Success by 3+"), and a margin-first read reports
 * the book's own critical rows as ordinary ones -- the slip [FrightCheck],
 * [RitualResolution] and [Wounds] each made once. Among the successes the
 * bands overlap, so the last match wins, exactly as [DegreeOfSuccess] reads
 * its ladder.
 *
 * **This table prints no critical override**, so [SuccessRoll]'s 3-4 and
 * 17-18 stand. The PCR prints its own and is the exception, not the rule.
 *
 * **Nothing gates who may roll.** Sec. 2 scopes itself to Beyonders, but the
 * roll it defines falls back to Per, which a mortal has. Who suspects they are
 * watched is the GM's call, the same reading that refused to enforce the
 * Sealed Artifact grade table as a wielder requirement (6af).
 */
object Detection {

    /** Sec. 2's general price for a charm, superseded by naming the charm. */
    const val GENERIC_CHARM = "Target carries a protective charm or item"

    /** "the GM calls for a SPI or Per roll (whichever is higher)" (p. 57). */
    fun attributeFor(spi: Int, per: Int): Int = maxOf(spi, per)

    /** Sec. 1's eight rows, in printed order. */
    fun awareness(): List<DivinationAwareness> = ContentLoader.divinationAwareness()

    /** Sec. 2's five outcomes, in printed order. */
    fun outcomes(): List<DetectionOutcome> = ContentLoader.detectionOutcomes()

    /** Sec. 2's six situations, in printed order. */
    fun modifiers(): List<DetectionModifier> = ContentLoader.detectionModifiers()

    /** Sec. 4's six items, in printed order. */
    fun items(): List<AntiDivinationItem> = ContentLoader.antiDivinationItems()

    /**
     * The Sec. 2 row named [situation]; refused rather than defaulted, because
     * every default here is a number the GM did not ask for.
     */
    fun modifierNamed(situation: String): DetectionModifier =
        modifiers().firstOrNull { it.situation.equals(situation, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "no detection modifier called '$situation'; the book prints " +
                    modifiers().joinToString { it.situation },
            )

    fun itemNamed(name: String): AntiDivinationItem =
        items().firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "no anti-divination item called '$name'; the book prints " +
                    items().joinToString { it.name },
            )

    /** The Sec. 1 row for [pathway] as the book spells it. */
    fun awarenessFor(pathway: String): DivinationAwareness =
        awareness().firstOrNull { it.pathway.equals(pathway, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "the awareness table prints no row for '$pathway'; it prints " +
                    awareness().joinToString { it.pathway },
            )

    /**
     * Everything the target adds to their own roll.
     *
     * [awarenessBonus] is Sec. 1's, passed as a number rather than as a row
     * because three of the four printed bonuses are scoped to what the
     * divination is ABOUT and only the GM can say whether it applies.
     *
     * Throws when [situations] names the generic charm row and [items] also
     * names an item that carries its own bonus: one charm, priced generally
     * and specifically, and taking both charges it twice.
     */
    fun modifierTotal(
        situations: List<DetectionModifier> = emptyList(),
        techniques: List<AntiDivinationTechnique> = emptyList(),
        items: List<AntiDivinationItem> = emptyList(),
        awarenessBonus: Int = 0,
    ): Int {
        val named = items.filter { it.detectionBonus != null }
        // Case-insensitively, because [modifierNamed] resolves that way: a
        // caller that looked a row up by name must not slip past the guard
        // the lookup was tolerant enough to reach.
        if (named.isNotEmpty() &&
            situations.any { it.situation.equals(GENERIC_CHARM, ignoreCase = true) }
        ) {
            throw IllegalArgumentException(
                "'$GENERIC_CHARM' is the general price of " +
                    named.joinToString { it.name } +
                    "; take one or the other, never both",
            )
        }
        return situations.sumOf { it.modifier } +
            techniques.sumOf { it.detectionBonus ?: 0 } +
            named.sumOf { it.detectionBonus ?: 0 } +
            awarenessBonus
    }

    /** SPI or Per, whichever is higher, plus what the circumstances are worth. */
    fun targetFor(spi: Int, per: Int, modifier: Int = 0): Int =
        attributeFor(spi, per) + modifier

    /**
     * The row [roll] landed on.
     *
     * The outcome door first: a critical is a critical whatever its margin,
     * and the book prints those rows by name. Only then the margin, taking the
     * LAST band that fits -- a margin of 5 satisfies "Success" and "Success by
     * 3+" alike, and the book prints them least-demanding first.
     */
    fun outcomeFor(roll: RollResult): DetectionOutcome {
        val named = outcomes().lastOrNull {
            it.outcome == roll.outcome.name && it.margin == null
        }
        if (roll.outcome != Outcome.SUCCESS) {
            return named ?: error("the detection table prints no ${roll.outcome} row")
        }
        val banded = outcomes().lastOrNull {
            it.outcome == Outcome.SUCCESS.name && (it.margin ?: 0) <= roll.margin
        }
        return banded ?: named ?: error("the detection table prints no success row")
    }

    /** Roll it, and read the row. [parts] is what moved the target. */
    fun attempt(
        spi: Int,
        per: Int,
        situations: List<DetectionModifier> = emptyList(),
        techniques: List<AntiDivinationTechnique> = emptyList(),
        items: List<AntiDivinationItem> = emptyList(),
        awarenessBonus: Int = 0,
        awarenessLabel: String? = null,
        source: RandomSource,
    ): DetectionResult {
        val total = modifierTotal(situations, techniques, items, awarenessBonus)
        val roll = SuccessRoll.against(targetFor(spi, per, total), source)
        val parts = buildList {
            add(
                if (spi >= per) "SPI $spi (the higher of SPI and Per)"
                else "Per $per (the higher of SPI and Per)",
            )
            situations.filter { it.modifier != 0 }
                .forEach { add("${it.situation} ${signed(it.modifier)}") }
            techniques.filter { (it.detectionBonus ?: 0) != 0 }
                .forEach { add("${it.name} ${signed(it.detectionBonus ?: 0)}") }
            items.filter { (it.detectionBonus ?: 0) != 0 }
                .forEach { add("${it.name} ${signed(it.detectionBonus ?: 0)}") }
            if (awarenessBonus != 0) {
                add("${awarenessLabel ?: "pathway ability"} ${signed(awarenessBonus)}")
            }
        }
        return DetectionResult(roll, outcomeFor(roll), parts)
    }

    private fun signed(n: Int) = if (n >= 0) "+$n" else "$n"
}
