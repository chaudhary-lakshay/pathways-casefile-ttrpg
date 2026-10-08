package com.pathways.rules

import com.pathways.model.SecondaryDelta
import com.pathways.model.Trait

/**
 * The lowest value each guarded score may stand at, and how far a character
 * falls short of them.
 *
 * ponytail: the app's bound, not the book's. Ch. 2 prices every secondary
 * "±N pts per level" (pp. 8-9) and prints no floor; these are the lowest
 * values the rules this app models can compute on -- a pool reported in
 * thirds, a CoR ceiling to reach -- and they are the constants those rules'
 * own guards read, so the builder and the engine cannot disagree. GURPS Basic
 * prints tighter limits; this book does not, so none is borrowed.
 */
object ScoreFloors {
    /** HP and FP maxima. */
    const val MIN_POOL_MAX = 1

    /** Maximum CoR: Will, +3 with Spiritual Fortitude ([Corruption.maxFor]). */
    const val MIN_COR_CEILING = 1

    /** Perception, which [Detection] rolls against. */
    const val MIN_PER = 0

    /** Whether [ceiling] is a maximum CoR the rules can count towards. */
    fun holdsCeiling(ceiling: Int): Boolean = ceiling >= MIN_COR_CEILING

    /**
     * The steps each guarded row of [s] needs back to reach its floor; all
     * zero when every floor holds. In STEPS, so a save is repaired by adding
     * it to what was bought.
     *
     * The Will row is measured on the CoR ceiling, not on Will, because
     * [traits] can move the ceiling by three on their own.
     */
    fun shortfall(s: Secondaries, traits: List<Trait>): SecondaryDelta = SecondaryDelta(
        hp = (MIN_POOL_MAX - s.maxHp).coerceAtLeast(0),
        fp = (MIN_POOL_MAX - s.maxFp).coerceAtLeast(0),
        will = (MIN_COR_CEILING - Corruption.maxFor(s.will, traits)).coerceAtLeast(0),
        per = (MIN_PER - s.per).coerceAtLeast(0),
    )
}
