package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.HpRecoveryRow

/**
 * The daily HT roll that restores HP (Ch. 5, p. 42): "Natural Recovery (HT
 * roll success) -- 1 HP ... Per day; rest & food required", and line 2897's
 * "the daily HT roll used for Natural Recovery".
 *
 * The table was refused whole as "clocks, and this app holds none". That
 * reason covers its RATES. This row is also a roll, and [StunRecovery] is the
 * same split already made: the roll is offered and the time is the GM's. So
 * the DAY is declared by whoever taps the button, as a new round is.
 *
 * **Nothing is restored here.** The roll is offered and the book's rows are
 * printed beside it; the HP stepper applies the result. Three things the book
 * leaves open would otherwise be decided by this object in silence:
 *  - "2x base rate" for "20-29 HP" does not say maximum or current HP;
 *  - "scales proportionally" prints no band past 39, and the builder reaches
 *    a maximum far beyond it;
 *  - "2 HP w/ Very Rapid Healing" names a trait no catalogue prints (the
 *    phrase occurs once in the book).
 *
 * **One modifier has no carrier, which is why the GM's is a parameter.**
 * Chained Sequence 8 grants "Rapid Healing +5 to daily HT rolls to recover
 * HP", and a potion's advantages are prose (tools/extract_pathways.py says
 * why they cannot be rows), so no [Trait] holds it. It is entered by hand
 * rather than matched out of prose at roll time.
 */
object NaturalRecovery {

    /**
     * The rest of p. 42's recovery section, and why each part stays out. Kept
     * in production source, where a sweep over Kotlin can find it: the whole
     * section used to sit under one reason, "clocks", which was true of one
     * of these and of the daily roll's RATE, and of nothing else.
     */
    val UNMODELLED = mapOf(
        "First Aid" to
            "\"1 HP + stops bleeding ... 1 minute; once per wound\" -- a skill " +
            "roll the sheet offers to a character who has the skill, and the " +
            "HP stepper applies the point. The minute, the bleeding and the " +
            "once are the GM's: this app tracks a pool and no wounds",
        "Physician" to
            "\"1 HP (2 HP on critical success) ... Per day of bed rest\" -- " +
            "a skill roll the sheet offers to a character who has the skill, " +
            "and the HP stepper applies it. \"skill 12+\", the day and the " +
            "equipment are the GM's",
        "Healing advantage" to
            "\"2 HP (costs 1 FP) ... Concentration + IQ roll\" -- an " +
            "advantage no catalogue prints, so no character can hold it",
        "Regeneration" to
            "five rates, \"1 HP per 12 hours\" down to \"10 HP per second\" " +
            "-- restored \"without rolls\", so all that is left is a clock, " +
            "and this app holds none. No catalogue prints the advantage; " +
            "Chained Sequences 7 and 6 grant it as prose",
    )

    /**
     * The book's three rows, in the order it prints them: the roll and what
     * it restores, then the multiples for a body of 20 HP and more. Printed
     * whole and never applied.
     */
    fun rows(): List<HpRecoveryRow> = ContentLoader.hpRecovery()

    /**
     * Hurt, and not dead. "Dead -- no roll required" is the HP table's own
     * last row, and a day's rest must not walk a character back out of it.
     * The book prints no bar at 0 HP or below, so none is added.
     */
    fun isOffered(hp: PoolStatus): Boolean = hp.current < hp.max && !hp.isDead

    /**
     * The traits whose cell says "to all HT rolls". NOT [VitalRolls.bonusFor]:
     * Hard to Kill is scoped "to avoid death" and Hard to Subdue "to stay
     * conscious", and this roll is neither.
     */
    fun bonusFor(traits: List<Trait>): Int = TraitEffects.htRollBonus(traits)

    /** [poolPenalty] is [Conditions.penaltyFor]'s, as it is for the other bare HT rolls. */
    fun targetFor(ht: Int, traits: List<Trait>, poolPenalty: Int, gmModifier: Int): Int =
        ht + bonusFor(traits) + poolPenalty + gmModifier
}
