package com.pathways.rules

import com.pathways.model.Layer
import com.pathways.model.ModEffect
import com.pathways.model.ModTarget
import com.pathways.model.Modifier
import com.pathways.model.ModifierId
import com.pathways.model.ModifierSource
import com.pathways.rules.content.AntiDivinationCounter
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.SequenceImmunity

/**
 * What divining a protected target costs, and whether skill can pay it.
 *
 * [penalty] is negative or zero. [requiresCritical] means the book forbids an
 * ordinary success outright, which is NOT the same as a large penalty -- see
 * [AntiDivination]. [parts] is what moved the number, for the screen to quote.
 */
data class AntiDivinationResult(
    val penalty: Int,
    val requiresCritical: Boolean,
    val parts: List<String>,
    /**
     * The HP the sacrifice actually cost.
     *
     * Zero when the offer bought nothing -- there was no penalty to forgive, or
     * too little HP was put up. A caller spends THIS rather than what it
     * offered, so a GM cannot burn 4 HP against an unprotected target and get
     * nothing back for it.
     */
    val hpSpent: Int = 0,
)

/**
 * Ch. 6.5's Sequence Immunity (Sec. 5) and its counters (Sec. 6) -- the
 * DIVINER's side of anti-divination.
 *
 * **A -10 and "only a critical success can succeed" are two different rules.**
 * Seq 4 and Seq 3 print both. Applying only the number gets it wrong the moment
 * the diviner is good: a skill-20 diviner at -10 rolls against 10, and an
 * ordinary 10 succeeds -- which is exactly what "the penalty cannot be offset
 * by skill alone" forbids. So [modifiers] emits `ModEffect.RequireCrit`
 * alongside the flat penalty, and [SkillRoll] demotes the success it would
 * otherwise have reported.
 *
 * **Seq 2 (Angel) prints -10 and no such clause**, while the two weaker rows do.
 * Read literally, the strongest listed target is the easiest of the three for a
 * highly skilled diviner. That is the book's asymmetry and it is reproduced
 * rather than smoothed (CLAUDE.md 4.1); `extract_anti_divination.verify()` and
 * a named test both pin it.
 *
 * **Lower Sequence means stronger.** Sequence Advantage asks whether the
 * diviner is "2+ Sequences stronger", which is `divinerSequence <=
 * targetSequence - 2`. Getting that comparison backwards hands the advantage to
 * exactly the wrong party and still produces plausible numbers.
 *
 * **Two halvings round in opposite printed directions, on negatives.** Direct
 * Link is "(round down)" and Sequence Advantage leaves techniques at "half
 * penalty, rounded up". Both are read as halving the MAGNITUDE -- see
 * [halvedMagnitude] for why, and note that the book's only worked halving
 * (Seq 5's -6 to -3) is even and so settles nothing on its own.
 *
 * **The order is the book's printing order, because the book gives no other.**
 * Sequence Advantage, then Direct Link, then HP Sacrifice. It matters: halving
 * -10 and then forgiving 2 gives -3, while forgiving 2 first and then halving
 * gives -4. A documented reading, pinned by a named test.
 */
object AntiDivination {

    /** Every printed immunity row, strongest target first. */
    fun rows(): List<SequenceImmunity> = ContentLoader.sequenceImmunity()

    /** The five printed counters, in the order they are applied. */
    fun counters(): List<AntiDivinationCounter> =
        ContentLoader.antiDivinationCounters()

    fun counterNamed(name: String): AntiDivinationCounter =
        counters().firstOrNull { it.name == name }
            ?: error("the book prints no anti-divination counter called '$name'")

    /**
     * The row covering a target at [sequence].
     *
     * Throws for a Sequence the table does not print. The book's rows run 2..9,
     * so Sequence 1 and 0 have none -- answering "no penalty" for a god would
     * invent the most consequential row in the table, and answering Angel's
     * would invent it differently.
     */
    fun rowFor(sequence: Int): SequenceImmunity =
        rows().firstOrNull { sequence in it.sequenceLow..it.sequenceHigh }
            ?: error(
                "the Sequence Immunity table prints no row for Sequence " +
                    "$sequence; it covers ${rows().minOf { it.sequenceLow }}.." +
                    "${rows().maxOf { it.sequenceHigh }}"
            )

    /**
     * Half a penalty, by magnitude.
     *
     * The book writes "half penalty, rounded up" and "halves ... (round down)"
     * about quantities it prints as negatives. Read numerically, "round down"
     * on -5 gives -3 -- HARSHER than the -5 it was meant to soften, in a
     * section titled "Countering Anti-Divination" where every other bullet
     * helps the diviner. So the halving is of the magnitude: -5 rounds down to
     * -2 and up to -3.
     *
     * `Math.floorDiv`, never `/` (CLAUDE.md 4.3): the two differ on exactly the
     * negatives this function exists to handle.
     */
    fun halvedMagnitude(penalty: Int, roundUp: Boolean): Int {
        require(penalty <= 0) { "penalties are negative or zero; was $penalty" }
        val magnitude = -penalty
        val half =
            if (roundUp) -Math.floorDiv(-magnitude, 2) else Math.floorDiv(magnitude, 2)
        return -half
    }

    /**
     * What a divination against a protected target is modified by.
     *
     * [targetSequence] is the target's; [divinerSequence] is null when the
     * diviner is a mortal or the GM has not said, in which case neither
     * Sequence Advantage nor the equal-Sequence row can apply -- both need a
     * number to compare, and assuming one would silently pick a side.
     *
     * [techniquePenalty] is the total from Sec. 3's active techniques, which
     * the target activates and pays SPI for. It is a parameter rather than a
     * table here because those are the TARGET's side of the chapter; 0 is the
     * honest default, and the counters below halve whatever is passed.
     */
    fun against(
        targetSequence: Int,
        divinerSequence: Int? = null,
        techniquePenalty: Int = 0,
        directLink: Boolean = false,
        hpSacrificed: Int = 0,
    ): AntiDivinationResult {
        require(techniquePenalty <= 0) {
            "an active technique is a penalty; was $techniquePenalty"
        }
        require(hpSacrificed >= 0) { "HP sacrificed cannot be negative" }
        val row = rowFor(targetSequence)
        val parts = mutableListOf<String>()

        // Lower Sequence is stronger, so "2+ stronger" counts DOWN.
        val hasAdvantage =
            divinerSequence != null && divinerSequence <= targetSequence - 2
        val isEqual = divinerSequence != null && divinerSequence == targetSequence

        var immunity = when {
            // Sequence Advantage ignores the passive penalty outright, so the
            // clause attached to it goes too: "the -10 penalty cannot be offset
            // by skill alone" is about a penalty no longer being taken.
            hasAdvantage -> 0
            isEqual && row.equalPenalty != null -> row.equalPenalty
            else -> row.penalty ?: 0
        }
        val critical = row.requiresCritical && !hasAdvantage

        // Techniques survive Sequence Advantage at half, rounded up.
        var techniques =
            if (hasAdvantage) halvedMagnitude(techniquePenalty, roundUp = true)
            else techniquePenalty

        if (directLink) {
            // Halves ALL anti-divination penalties, so both parts, round down.
            immunity = halvedMagnitude(immunity, roundUp = false)
            techniques = halvedMagnitude(techniques, roundUp = false)
        }

        var total = immunity + techniques
        val sacrifice = counterNamed("HP Sacrifice")
        val required = sacrifice.hpRequired ?: 0
        var hpSpent = 0
        var relief = 0
        if (required > 0 && hpSacrificed >= required && total != 0) {
            // "ignores -2 of anti-divination penalties" -- the sign is the
            // book's, so subtracting it moves the total toward zero. Never past
            // it: forgiving more penalty than was taken would be a bonus the
            // book does not grant.
            val forgiven = sacrifice.ignoresPenalty ?: 0
            val eased = minOf(0, total - forgiven)
            relief = eased - total
            total = eased
            // Charged only where it bought something. Offering the sacrifice
            // against an unprotected target and taking the HP anyway would be
            // the app inventing a cost the book attaches to a benefit.
            hpSpent = hpSacrificed
        }

        // Built from the FINAL values, so the printed parts sum to [total]. An
        // earlier version listed the pre-halved penalty and then said "Direct
        // Link halves them" with no number, and printed the forgiveness with
        // the book's stored sign -- so a GM adding up what was on screen got
        // -12 where the app had -3. Same defect class as the double-count the
        // dialog was fixed for: every number shown must reconcile.
        val halved = if (directLink) " (halved by Direct Link)" else ""
        if (hasAdvantage && (row.penalty ?: 0) != 0) {
            parts += "Sequence Advantage ignores ${row.label}"
        }
        if (immunity != 0) parts += "${row.label} ${signed(immunity)}$halved"
        if (techniques != 0) parts += "active techniques ${signed(techniques)}$halved"
        if (relief != 0) parts += "$hpSpent HP forgives ${signed(relief)}"

        return AntiDivinationResult(total, critical, parts, hpSpent)
    }

    /**
     * [against]'s answer as modifiers, ready for [SkillRoll].
     *
     * The critical-only rule travels as `ModEffect.RequireCrit` rather than as
     * a very large penalty, because they are not interchangeable: a penalty can
     * be bought off by skill and this rule says it cannot.
     */
    fun modifiers(result: AntiDivinationResult): List<Modifier> = buildList {
        if (result.penalty != 0) {
            add(
                modifier(
                    "anti-divination",
                    "Target's protection",
                    ModEffect.Flat(result.penalty),
                ),
            )
        }
        if (result.requiresCritical) {
            add(
                modifier(
                    "anti-divination:critical",
                    "Only a critical success can succeed",
                    ModEffect.RequireCrit,
                ),
            )
        }
    }

    private fun modifier(id: String, label: String, effect: ModEffect) = Modifier(
        id = ModifierId(id),
        source = ModifierSource.Situational(label),
        target = ModTarget.AllRolls,
        effect = effect,
        layer = Layer.SITUATIONAL,
    )

    private fun signed(n: Int) = if (n >= 0) "+$n" else "$n"
}
