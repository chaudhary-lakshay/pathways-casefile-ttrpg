package com.pathways.rules.content

import kotlinx.serialization.Serializable

/**
 * One row of the skill-boundary table (Ch. 6.5, Sec. I, p. 54).
 *
 * Which of the two skills a question of this scope needs -- NOT how hard the
 * question is, which is [DivinationDifficulty]'s separate axis. Only the
 * Complex row carries a [modifier], and it prices the SUBSTITUTION: what a
 * caster pays for reaching for Divination Arts where the book asks for
 * Ritualistic Magic.
 *
 * [printedCost] is prose and never a number. Step 2 sends the payment to
 * Section II's method table, which `Divination.spiFor` already charges, so an
 * integer here would be charged twice.
 */
@Serializable
data class DivinationScope(
    val scope: String,
    val examples: String,
    val guidance: String,
    val printedCost: String,
    val modifier: Int? = null,
    val printedModifier: String? = null,
)

/**
 * One band of the Question Difficulty table (Ch. 6.5, Step 1).
 *
 * [openEnded] marks "-6 or worse", the one band whose modifier is a floor
 * rather than a value -- the same shape as the "100+ meters" range band and
 * the darkness row. [modifier] is its gentle end.
 */
@Serializable
data class DivinationDifficulty(
    val band: String,
    val modifier: Int,
    val printedModifier: String,
    val example: String,
    val openEnded: Boolean = false,
)

/**
 * One row of the Methods & Tools table (Ch. 6.5, Section II).
 *
 * [spiMax] is present only on Dowsing Rods, whose cell reads "1-2": a cost the
 * GM picks inside, not a fixed one.
 */
@Serializable
data class DivinationMethod(
    val method: String,
    val bestFor: String,
    val time: String,
    val spi: Int,
    val spiMax: Int? = null,
    val notes: String,
)

/**
 * One row of the Step 3 modifier table (Ch. 6.5).
 *
 * [crossReference] marks "Question difficulty / See Step 1 table", which is
 * not a number at all -- it points at [DivinationDifficulty], which the caller
 * already applies. Reading it as zero would drop the whole of Step 1, so it is
 * refused, the same way the Aim rows are.
 *
 * [best] is the other end of a printed range ("+0 to +2"); [modifier] is the
 * end nearer zero. [perRepeat] marks the one row that multiplies.
 */
@Serializable
data class DivinationModifier(
    val source: String,
    val printed: String,
    val modifier: Int? = null,
    val best: Int? = null,
    val perRepeat: Boolean = false,
    val crossReference: Boolean = false,
)

/**
 * One row of the Step 4 clarity table (Ch. 6.5).
 *
 * Five rows are named by margin and [isCritical] marks the sixth, which is
 * named by OUTCOME. Two doors into one table, exactly as the ritual failure
 * table has -- see [com.pathways.rules.Divination.clarityFor].
 */
@Serializable
data class DivinationClarity(
    val result: String,
    val clarity: String,
    val effect: String,
    val isCritical: Boolean = false,
    /**
     * The lower bound the Result cell prints ("Success by 5+" is 5), with
     * [isFailure] saying which side of the table it sits on. They exist so the
     * number that SELECTS a row travels ON the row -- the shape
     * `MajorWound.failBy`, `RitualFailure.from` and `SpiritualSenseRow.margin`
     * already have. [com.pathways.rules.Divination.clarityFor] held 5 and 3 as
     * Kotlin literals and reached its rows by array position, the only
     * margin-reader in this codebase that did. Bands overlap by design, so the
     * DEEPEST match wins.
     *
     * The critical row carries neither, being named by outcome: printing no
     * margin is not printing zero, the distinction [RitualCritical.corGained]
     * draws for corruption.
     */
    val margin: Int? = null,
    val isFailure: Boolean = false,
)

/**
 * One row of Ch. 6.5's Sequence Immunity table (Sec. 5, p. 59-60).
 *
 * [penalty] is what a weaker diviner takes; [equalPenalty] and [linkedPenalty]
 * are printed by one row each and are absent elsewhere, because printing no
 * value is not printing zero.
 *
 * **[requiresCritical] is a SECOND rule, not a restatement of [penalty].** Seq
 * 4 and Seq 3 print "only a critical success (3-4) can succeed -- the penalty
 * cannot be offset by skill alone", which forbids the ordinary success a
 * skill-20 diviner would otherwise buy at -10. Seq 2 prints -10 and no such
 * clause; that asymmetry is the book's and is recorded, never repaired.
 */
@Serializable
data class SequenceImmunity(
    val label: String,
    val sequenceLow: Int,
    val sequenceHigh: Int,
    val requiresCritical: Boolean = false,
    val text: String,
    val penalty: Int? = null,
    val equalPenalty: Int? = null,
    val linkedPenalty: Int? = null,
)

/**
 * One way a diviner pierces anti-divination (Ch. 6.5, Sec. 6, p. 60).
 *
 * [ignoresPenalty] keeps the sign the book prints ("ignores -2 of
 * anti-divination penalties"), so a caller subtracts it rather than guessing a
 * convention. Only HP Sacrifice carries numbers the app applies; the ritual and
 * the deity are the GM's, and a field on them would imply otherwise.
 */
@Serializable
data class AntiDivinationCounter(
    val name: String,
    val text: String,
    val ignoresPenalty: Int? = null,
    val hpRequired: Int? = null,
)

/**
 * One pathway that feels a divination coming (Ch. 6.5, Sec. 1, p. 57).
 *
 * The row's primary output is a GATE, not a number: [isAutomatic] means no
 * roll is needed, [freeRoll] names the attribute when the book grants one
 * anyway, and a row with neither grants nothing.
 *
 * [bonusText] travels with [bonus] because three of the four printed bonuses
 * are scoped to what the divination is ABOUT ("related to their hidden
 * knowledge"), which this app cannot test. The scope belongs beside the value,
 * the same reason [PoolThreshold] carries its own.
 *
 * [divinerPenalty] appears on exactly one row and is not awareness at all --
 * Wheel of Fortune's passive luck penalises the DIVINER. Recorded here so the
 * cross-axis row stays visible; nothing applies it. See
 * [com.pathways.rules.Detection].
 */
@Serializable
data class DivinationAwareness(
    val pathway: String,
    val sequence: String,
    val detection: String,
    val isAutomatic: Boolean = false,
    val freeRoll: String? = null,
    val bonus: Int? = null,
    val bonusText: String? = null,
    val divinerPenalty: Int? = null,
)

/**
 * What noticing a divination tells the target (Ch. 6.5, Sec. 2, p. 57).
 *
 * Four rows are named by [outcome] and one by [margin] -- the two doors
 * [com.pathways.rules.FrightCheck], [com.pathways.rules.RitualResolution] and
 * [com.pathways.rules.Wounds] each read the wrong way round once.
 */
@Serializable
data class DetectionOutcome(
    val result: String,
    val outcome: String,
    val effect: String,
    val margin: Int? = null,
)

/** One circumstance on the target's detection roll (Ch. 6.5, Sec. 2, p. 57). */
@Serializable
data class DetectionModifier(val situation: String, val modifier: Int)

/**
 * One active anti-divination technique (Ch. 6.5, Sec. 3, p. 58-59).
 *
 * [divinationPenalty] is what the DIVINER takes; [detectionBonus] is what the
 * target gains on its own roll. 3A prints both, which is why the two are
 * separate fields rather than one signed number.
 *
 * [spi] is absent rather than zero for 3F, which prices itself as "SPI equal
 * to what a divination of that scope would normally cost" -- a rule, not a
 * number, the distinction [RitualCritical.corGained] already draws.
 */
@Serializable
data class AntiDivinationTechnique(
    val id: String,
    val name: String,
    val availability: String,
    val bullets: List<TechniqueBullet>,
    val spi: Int? = null,
    val divinationPenalty: Int? = null,
    val detectionBonus: Int? = null,
) {
    fun bullet(label: String): String? = bullets.firstOrNull { it.label == label }?.text
}

/** One labelled line of a technique's printed rules ("Cost", "Duration", ...). */
@Serializable
data class TechniqueBullet(val label: String, val text: String)

/**
 * One item that protects against divination (Ch. 6.5, Sec. 4, p. 59).
 *
 * [ritualDetectionPenalty] is deliberately not [detectionBonus]'s opposite:
 * the candle suppresses "detection of that ritual", which is sensing a ritual
 * being performed, not the being-divined roll Sec. 2 defines. Two keys so a
 * caller cannot sum them.
 */
@Serializable
data class AntiDivinationItem(
    val name: String,
    val effect: String,
    val detectionBonus: Int? = null,
    val ritualDetectionPenalty: Int? = null,
)
