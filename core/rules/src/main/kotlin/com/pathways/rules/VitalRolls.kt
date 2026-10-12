package com.pathways.rules

import com.pathways.model.Trait

/**
 * The two HT rolls chapter 5's HP table prints, as a closed set.
 *
 * They are different rolls, not two names for one: three traits reach them
 * differently, and a single "HT roll" would have to answer for both at once.
 */
enum class VitalCheck {
    /** `0 HP`: "Roll 3d6 <= HT or fall unconscious". */
    CONSCIOUSNESS,

    /** `-HP`: "Roll vs. HT or die". */
    SURVIVAL,
}

/**
 * The HP table's own rolls, which this app printed and never offered.
 *
 * [Pools.hp] has set `rollsToStayConscious` and `rollsToSurvive` since M0 and
 * **nothing read either** -- `PoolRow` printed the row's sentence on the sheet
 * and no screen could roll it. The same shape 6dc found one table below (a
 * screen printing a rule the next screen ignores) and 6cf found in the API (a
 * function tested, documented and called by nothing).
 *
 * **The repetition is deliberately not modelled.** Both rows say "each turn",
 * and this app holds no clock (6ad, 6ah, 6an, 6dc) -- so one roll is offered
 * and the sentence saying the book asks for one a turn is printed beside it.
 * Silence there would understate the danger, which is the disclaimer class
 * 6bl found.
 *
 * **No outcome is latched.** [Shock] latches because the app creates a state
 * it can then charge for; a failed consciousness roll has no consumer here, so
 * storing it would be the field-with-no-reader this object exists to fix.
 */
object VitalRolls {

    /** p. 40's `0 HP` row. */
    private const val CONSCIOUSNESS_ROW = "0 HP"

    /** p. 40's `-HP` row. */
    private const val SURVIVAL_ROW = "-HP"

    /**
     * The checks the table asks for at [hp], in the order the rows print.
     *
     * Reported from [Pools]' own two flags rather than re-derived, because a
     * threshold decided at a second site is what let one screen print a rule
     * while another ignored it ([Pools.isFatigued], 6dc). At `-5xHP` both
     * flags are clear -- "Dead - no roll required" -- so a screen cannot offer
     * a chance the book withholds.
     *
     * **Below `-HP` this returns BOTH**, because `Pools` sets both flags there
     * and the dying row does not withdraw the consciousness roll one row above
     * it. Reporting one would be a reading; reporting what the model already
     * says is not.
     */
    fun due(hp: PoolStatus): List<VitalCheck> = buildList {
        if (hp.rollsToStayConscious) add(VitalCheck.CONSCIOUSNESS)
        if (hp.rollsToSurvive) add(VitalCheck.SURVIVAL)
    }

    /**
     * What [traits] add to [check], signed.
     *
     * Three traits name an HT roll and they do not agree on which:
     *
     *  - `Fit` / `Very Fit` / `Unfit` say "to all HT rolls", so both.
     *  - `Hard to Kill` says "HT rolls **to avoid death**", so [VitalCheck.SURVIVAL]
     *    alone: dying starts at the `-HP` row, and losing consciousness one
     *    row above it is not death. Applying it to both would widen a clause
     *    that names its own scope -- the move 6ck refused.
     *  - `Hard to Subdue` says "+1/level **to remain conscious** when reduced
     *    to 0 or negative HP", so [VitalCheck.CONSCIOUSNESS] alone -- the
     *    mirror of the row printed directly above it in the same catalogue.
     *    It names negative HP, where BOTH checks are due, and still does not
     *    reach the survival roll: that row's own words are "or die".
     *  - `High Pain Threshold` says "to avoid knockdown", so neither. Its roll
     *    is the Major Wound's ([TraitAttributeRolls.knockdownBonus]).
     *
     * **This said THREE traits until 6dt and there were four.** `Hard to
     * Subdue`'s cell never contains the words "HT roll", so every census that
     * would have found it -- all of them filtered on that phrase -- was blind
     * to it by construction (6bu).
     *
     * Decided here rather than at the screen, so the two checks cannot drift
     * apart in a composable nothing tests.
     */
    fun bonusFor(check: VitalCheck, traits: List<Trait>): Int =
        TraitAttributeRolls.htRollBonus(traits) + when (check) {
            VitalCheck.SURVIVAL -> TraitAttributeRolls.deathRollBonus(traits)
            VitalCheck.CONSCIOUSNESS -> TraitAttributeRolls.consciousnessRollBonus(traits)
        }

    /**
     * The number 3d6 is rolled against.
     *
     * [poolPenalty] is what the trackers cost a bare attribute roll
     * ([Conditions.penaltyFor]). It applies: a character at 0 HP has been
     * reeling since 1/3 HP, so nothing here creates the state mid-roll -- the
     * argument that keeps shock off [RollKind.MAJOR_WOUND_HT] has no
     * counterpart on these two.
     *
     * **It carries the FP penalty as well as the HP one, deliberately.** These
     * rolls are gated on an HP threshold, but "-1 to all rolls per 1 FP below
     * 0" (p. 41) names ROLLS and not a pool, so a character at -8 FP rolls to
     * stay conscious at -8. Scoping it to HP would be this app inventing the
     * qualifier the book left out -- the move 4.1 forbids in both directions.
     */
    fun targetFor(check: VitalCheck, ht: Int, traits: List<Trait>, poolPenalty: Int, shock: Int = 0, states: Int = 0): Int =
        ht + bonusFor(check, traits) + poolPenalty + shock + states

    /** The threshold row's own sentence, quoted rather than paraphrased. */
    fun printed(check: VitalCheck): String = row(check).effect

    /**
     * The row's printed LEVEL, which is what tells two offered rolls apart.
     *
     * Below `-HP` both checks are due and their targets differ (`Hard to Kill`
     * lifts one of them), so two buttons reading "Roll HT n" would leave the
     * player guessing which number belonged to which rule. The book's own
     * "0 HP" and "-HP" are the discriminator, so no label is invented here --
     * 6cn's rule that an unnamed label gets picked by whoever writes the
     * screen, and the default is the worse one.
     */
    fun rowFor(check: VitalCheck): String = row(check).level

    private fun row(check: VitalCheck) = Pools.thresholds(Pool.HP)
        .firstOrNull { it.level == if (check == VitalCheck.CONSCIOUSNESS) CONSCIOUSNESS_ROW else SURVIVAL_ROW }
        ?: error("the HP table prints no row for $check")
}
