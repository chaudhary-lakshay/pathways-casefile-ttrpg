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

    /** The two attributes Sec. 1's rows name for the rolls they grant. */
    const val PER = "PER"
    const val SPI = "SPI"

    /**
     * "the GM calls for a SPI or Per roll (whichever is higher)" (p. 57) --
     * unless a Sec. 1 row named the attribute for the roll it grants.
     *
     * Both are ATTRIBUTES. Neither is a pool balance: passing what is left of
     * SPI makes a caster who spent it on a ritual worse at noticing, and in
     * Soul Debt it goes negative — which is why a negative is refused rather
     * than quietly losing to Per. Found on the emulator, where the breakdown
     * read "the higher of SPI -2 and Per 10".
     *
     * **[freeRoll] is a disagreement, not a refinement, and the book is left
     * holding both halves.** Sec. 2's sentence scopes itself to "a Beyonder
     * with passive awareness" -- Sec. 1's own population -- and says whichever
     * is higher. Sec. 1 then names an attribute on four rows, and not the same
     * one twice: Fool and Fool / Error / Door grant a free PER roll, Darkness
     * and Hermit an automatic SPI roll. Naming different attributes per
     * pathway is a choice rather than loose phrasing, so the row wins for the
     * roll the row grants -- specific beats general, the call the PCR's own
     * critical rules and the ritual CoR ladder already needed. Sec. 2 still
     * governs the other roll its sentence defines, the one a Beyonder makes on
     * suspicion alone, where no row has spoken: that is what null means here.
     */
    fun attributeFor(
        spi: Int,
        per: Int,
        freeRoll: String? = null,
        alertness: Int = 0,
        spiModifier: Int = 0,
        perClause: Int = 0,
    ): Int {
        require(spi >= 0) { "SPI is an attribute, not a pool balance; was $spi" }
        require(per >= ScoreFloors.MIN_PER) { "Per is an attribute, not a pool balance; was $per" }
        require(alertness >= 0) { "Alertness is a bonus, not a penalty; was $alertness" }
        return when (freeRoll?.uppercase()) {
            null -> if (takesPer(spi, per)) per + alertness + perClause else spi + spiModifier
            SPI -> spi + spiModifier
            PER -> per + alertness + perClause
            // Refused rather than falling back to the higher: a caller holding
            // an attribute this table never names has read the wrong row, and
            // the higher of the two would hide that behind a plausible number.
            else -> throw IllegalArgumentException(
                "the awareness table grants only $SPI and $PER rolls; was '$freeRoll'",
            )
        }
    }

    /**
     * How a breakdown explains which attribute it took, and why.
     *
     * Public because the screen prints this sentence directly above the target
     * it explains, and the screen had its own hand-written copy of the choice.
     * That copy still read "the higher of SPI and Per" after [attributeFor]
     * learned to honour a row's named attribute, so a Fool saw a target of 12
     * over a sentence that adds up to 13 -- the reconcile-on-screen defect this
     * dialog already produced once (6ag), reintroduced by the fix for it and
     * caught in review. One sentence, one author.
     */
    /**
     * Whether [row]'s automatic awareness actually fires.
     *
     * The Seq 4+ row is the only one that grants it, and it scopes itself in
     * its own cell: "A demigod always knows when someone of **weaker**
     * Sequence attempts to divine them. Equal or stronger Sequence requires a
     * Quick Contest of SPI vs the diviner's Divination Arts or Ritualistic
     * Magic." The screen read only the `isAutomatic` flag, so it withdrew the
     * Sense roll against every diviner and told a demigod they were aware of
     * an equal who might well have gone unnoticed.
     *
     * [divinerIsWeaker] is the GM's, not the app's. This dialog holds the
     * TARGET's sheet and the diviner is a declared other party with no
     * numbers, which is the same reason the six Quick Contests this chapter
     * calls for are unmodelled (6ah, and Feint before it in 6o). So the
     * comparison is asked for, the way the darkness row's value and a fright
     * trigger's severity are asked for.
     *
     * False for every other row: no other awareness row prints the word.
     */
    fun isAutomaticAgainst(row: DivinationAwareness, divinerIsWeaker: Boolean): Boolean =
        row.isAutomatic && divinerIsWeaker

    fun attributeLine(
        spi: Int,
        per: Int,
        freeRoll: String? = null,
        alertness: Int = 0,
        spiModifier: Int = 0,
        perClause: ClauseBonus = ClauseBonus(0, null),
    ): String {
        val alert = (if (alertness > 0) ", +$alertness Alertness" else "") +
            perClause.line?.let { ", $it" }.orEmpty()
        val perTotal = per + alertness + perClause.total
        // Named only where it lands. A Per roll is not an SPI-based roll, so
        // printing the trait beside one would explain a number it did not move
        // -- and the breakdown and the target above it must reconcile (6ai).
        val ether = if (spiModifier != 0) ", $spiModifier ${TraitEffects.ETHER_BODY_DAMAGE}" else ""
        return when (freeRoll?.uppercase()) {
            SPI -> "SPI ${spi + spiModifier} (the row grants a SPI roll$ether)"
            PER -> "Per $perTotal (the row grants a Per roll$alert)"
            else ->
                if (takesPer(spi, per)) {
                    "Per $perTotal (the higher of SPI $spi and Per $per$alert)"
                } else {
                    "SPI ${spi + spiModifier} (the higher of SPI $spi and Per $per$ether)"
                }
        }
    }

    /**
     * Whether the general rule takes Per, deciding on the RAW attributes.
     *
     * Alertness lifts a Per roll and is deliberately not allowed to decide
     * WHICH attribute is rolled: "the higher of SPI or Per" is a comparison
     * the book makes between two attributes, and letting a trait tip it would
     * be a claim the book never makes. Choose first, then apply -- the
     * smaller reading. Ties still go to SPI, as they always did.
     */
    private fun takesPer(spi: Int, per: Int): Boolean = per > spi

    /**
     * Whether this roll takes Per: what [attributeFor] decides, for a screen
     * that offers a Per-roll clause and must not offer it on an SPI roll,
     * where a lit chip would move nothing.
     */
    fun isPerRoll(spi: Int, per: Int, freeRoll: String? = null): Boolean = when (freeRoll?.uppercase()) {
        null -> takesPer(spi, per)
        PER -> true
        else -> false
    }

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
    fun targetFor(
        spi: Int,
        per: Int,
        modifier: Int = 0,
        freeRoll: String? = null,
        alertness: Int = 0,
        /**
         * The Major Wound's shock, which is "-4 to all rolls" and names no
         * exception -- so it reaches this one too. Passed in rather than
         * folded into [modifier] so [attempt] can NAME it: a target that
         * moves without the breakdown saying why is how these numbers stopped
         * reconciling in 6ag and 6ai.
         */
        shock: Int = 0,
        /**
         * Reeling and fatigue, both printed "-1 to all rolls" -- the same
         * unqualified phrase as the shock above, and this is one of the
         * bare attribute rolls the modifier ledger never sees. Separate from
         * [shock] so [attempt] can name each: two states that moved one target
         * must both appear in a breakdown that has to sum (6ag).
         */
        hurt: Int = 0,
        /**
         * Ether Body Damage's "-1 to all SPI-based rolls", which lands on the
         * ATTRIBUTE rather than beside it: this roll is SPI-based only when
         * SPI is the attribute taken, and a Per roll is not one. Kept out of
         * [modifier] for exactly that reason -- [attributeFor] is where the
         * choice is made, so it is the only place that can tell.
         */
        spiModifier: Int = 0,
        /** The held rows' declared "-N to all rolls" ([TraitClauses.allRollsClauses]). */
        states: ClauseBonus = ClauseBonus(0, null),
        /**
         * A declared Per-roll clause ([TraitClauses.perRollClauses]): Light
         * Sleeper's "Perception rolls while sleeping at +4". On the Per
         * branch alone, as [alertness] is.
         */
        perClause: ClauseBonus = ClauseBonus(0, null),
    ): Int = attributeFor(spi, per, freeRoll, alertness, spiModifier, perClause.total) + modifier + shock + hurt + states.total

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
        freeRoll: String? = null,
        alertness: Int = 0,
        shock: Int = 0,
        /** Reeling and fatigue, as [targetFor] takes them. */
        hurt: Int = 0,
        spiModifier: Int = 0,
        source: RandomSource,
    ): DetectionResult = rollAgainst(
        targetFor(
            spi, per, modifierTotal(situations, techniques, items, awarenessBonus),
            freeRoll, alertness, shock, hurt, spiModifier,
        ),
        source,
        partsFor(
            spi, per, situations, techniques, items, awarenessBonus, awarenessLabel,
            freeRoll, alertness, shock, hurt, spiModifier,
        ),
    )

    /**
     * The roll itself, against a [target] already composed by [targetFor],
     * carrying the breakdown already composed by [partsFor] -- what a screen
     * that printed both calls, so each is composed once (6eg, 6eh).
     */
    fun rollAgainst(target: Int, source: RandomSource, parts: List<String>): DetectionResult {
        val roll = SuccessRoll.against(target, source)
        return DetectionResult(roll, outcomeFor(roll), parts)
    }

    /**
     * The breakdown of [targetFor]'s number, one part per source. The screen
     * prints it before the roll and the result carries it after, and it is
     * the same list both times because it is built once.
     */
    fun partsFor(
        spi: Int,
        per: Int,
        situations: List<DetectionModifier> = emptyList(),
        techniques: List<AntiDivinationTechnique> = emptyList(),
        items: List<AntiDivinationItem> = emptyList(),
        awarenessBonus: Int = 0,
        awarenessLabel: String? = null,
        freeRoll: String? = null,
        alertness: Int = 0,
        shock: Int = 0,
        hurt: Int = 0,
        spiModifier: Int = 0,
        states: ClauseBonus = ClauseBonus(0, null),
        perClause: ClauseBonus = ClauseBonus(0, null),
    ): List<String> = buildList {
        // The attribute line carries the trait itself, because it moved the
        // attribute rather than the target beside it -- naming it twice
        // would double it in a breakdown that has to sum (6ag).
        add(attributeLine(spi, per, freeRoll, alertness, spiModifier, perClause))
        if (shock != 0) add("shock ${signed(shock)}")
        if (hurt != 0) add("hurt or tired ${signed(hurt)}")
        states.line?.let { add(it) }
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

    private fun signed(n: Int) = if (n >= 0) "+$n" else "$n"
}
