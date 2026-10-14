package com.pathways.rules

import com.pathways.model.RollResult
import com.pathways.model.Trait
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.FrightRecovery
import com.pathways.rules.content.MajorWound
import com.pathways.rules.content.RecoveryState

/**
 * The Major Wound table's own recovery roll, which this app printed and never
 * offered (Ch. 5, p. 42).
 *
 * `TraitEffects.PARTLY_APPLIED` refused Combat Reflexes' printed clause
 * *"+6 to recover from stun"* with the reason **"this app models no stun
 * recovery roll to make"** — a claim about the app, not the book. The Failure
 * row prints one, *"Stunned for 1d6 seconds (roll HT each turn to recover
 * early)"*, and `DamageDialog` has rendered that sentence since 6n. So the
 * clause had a roll to reach and no way to reach it: 6de's shape one table
 * over, and the third clause of one cell to be found unapplied (6bh).
 *
 * **The repetition stays the GM's.** "Each turn" is a clock this app does not
 * hold (6ad, 6ah, 6an, 6dc, 6dq), so one roll is offered and the row's own
 * sentence — which says the book asks for one a turn — is printed beside it.
 * That is [VitalRolls]' reading of "each turn" on the HP table, and [Study]'s
 * warrant for a downtime planner with no downtime: the GM says when the time
 * passed, the rules say what the roll is. `Rapid Recovery`'s *"Stun durations
 * halved"* stays refused, because a halved DURATION does need the clock.
 *
 * **This object answers for the Major Wound's stun and no other.** The book
 * prints a stun-recovery roll in four more places and the attribute is the
 * SOURCE's, never the stun's: the Fright Effects table's rows 4-9 and 10-11
 * roll Will in two columns and IQ in the third, and two pathway abilities
 * (p. 128) disagree with each other about the same stun, one saying HT and
 * one saying Will. [bonusFor] composes [TraitAttributeRolls.htRollBonus] because
 * THIS roll is an HT roll; a caller with a Will stun that reused it would
 * quietly collect `Fit`'s HT bonus. The fright rows' recovery is
 * [FrightCheck.recoveryFor]'s, which rolls the column's own attribute; the
 * two pathway abilities' is offered nowhere.
 *
 * **Nothing is latched.** [Shock] latches because the app creates a state it
 * can then charge for; a recovery roll ends one, and this app holds no stun
 * flag on a character to clear. `DefenseDialog`'s stun is the GM's own
 * declaration and stays that way.
 */
object StunRecovery {

    /** p. 42's Failure row, the one whose effect is the stun. */
    private const val ROW = "Failure"

    /** Ch. 3's Combat Reflexes: "+6 to recover from stun". */
    const val COMBAT_REFLEXES_BONUS = 6

    /**
     * Whether [row] is the Major Wound result a character recovers FROM.
     *
     * The row's name lives here rather than in the screen that rolls it, which
     * is [Shock.causedBy]'s reason exactly: a dialog comparing a string to
     * "Failure" would be a second copy of which row this rule belongs to.
     */
    fun stunsOn(row: MajorWound): Boolean = row.result == ROW

    /**
     * Whether a hit whose wound roll landed on [row] leaves the character
     * stunned once applied: the stun row, unless [recovery] already won it
     * back. What Defend's Stunned is set from (play session 1).
     */
    fun leavesStunned(row: MajorWound, recovery: RollResult?): Boolean =
        stunsOn(row) && recovery?.outcome?.isSuccess != true

    /** That row's effect cell, quoted rather than paraphrased. */
    fun printed(): String = row().effect

    /**
     * Whether [recovery] is a stun, and so whether a stun clause reaches it.
     *
     * The Fright Effects table prints four states across the three rows that
     * offer a way out — `Stunned` twice over, then `Retching`, `Ecstasy` and
     * `Dazed` — and Combat Reflexes' "+6 to recover from stun" names one of
     * them. Answered here rather than at the caller because this object
     * already holds which Major Wound row is the stun, and two sites deciding
     * what a stun IS is the defect, not the drift it produces.
     */
    fun isStun(recovery: FrightRecovery): Boolean = recovery.state == RecoveryState.STUNNED

    /**
     * What [traits] add to this roll, signed.
     *
     * Two clauses reach it and they are different rules. Combat Reflexes says
     * "to recover from stun", which names the condition and not the attribute,
     * so it reaches a Will or IQ recovery roll too, through
     * [FrightCheck.recoveryBonus]. [TraitAttributeRolls.htRollBonus] is "all HT
     * rolls", which this one is.
     *
     * `High Pain Threshold` is NOT among them. Its "+3 on HT rolls to avoid
     * knockdown" reaches the Major Wound roll itself, whose fail-by-5 row is a
     * knockdown; recovering from the stun one row above avoids no knockdown,
     * and widening a clause that names its own scope is the move 6ck refused.
     */
    fun bonusFor(traits: List<Trait>): Int =
        TraitAttributeRolls.htRollBonus(traits) + TraitAttributeRolls.stunRecoveryBonus(traits)

    /**
     * The number 3d6 is rolled against.
     *
     * [poolPenalty] is what the trackers cost a bare attribute roll
     * ([Conditions.penaltyFor]). It applies: the character carried reeling and
     * fatigue in, so the argument that keeps shock off
     * [RollKind.MAJOR_WOUND_HT] — that the roll creates the state — has no
     * counterpart here. The same argument lets in a [shock] carried from an
     * earlier wound, and the declared [states] (6gs).
     *
     * [shock] and [states] are required, unlike [VitalRolls.targetFor]'s: the
     * Damage dialog summed this target by hand from the roll's first commit,
     * and 6gs taught that sum both terms while this function took neither.
     */
    fun targetFor(ht: Int, traits: List<Trait>, poolPenalty: Int, shock: Int, states: Int): Int =
        ht + bonusFor(traits) + poolPenalty + shock + states

    private fun row(): MajorWound = ContentLoader.majorWound().firstOrNull(::stunsOn)
        ?: error("the Major Wound table prints no \"$ROW\" row to recover from")
}
