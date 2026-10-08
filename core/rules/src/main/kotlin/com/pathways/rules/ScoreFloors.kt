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

    /**
     * Basic Move, and Basic Speed in quarters.
     *
     * ponytail: policy, on a different warrant from the three above -- the
     * rules compute on both at any value. Move is a distance ("how far you
     * move per second", "up to your full Basic Move in meters"), and below 0
     * it names none. Speed is held with it because Move alone bounds nothing:
     * Speed sold a hundred quarters and Move bought back on top of it kept
     * some 395 points. Zero rather than lower because the book rounds Speed
     * two ways ("rounded down", "drop fractions") that part company on a
     * negative fraction. What the pair can still refund is 5 x (HT + DX)
     * points, 90 at the book's 9s.
     */
    const val MIN_MOVE = 0
    const val MIN_SPEED_QUARTERS = 0

    /** Whether [ceiling] is a maximum CoR the rules can count towards. */
    fun holdsCeiling(ceiling: Int): Boolean = ceiling >= MIN_COR_CEILING

    /**
     * The steps each guarded row of [s] needs back to reach its floor; all
     * zero when every floor holds. In STEPS, so a save is repaired by adding
     * it to what was bought.
     *
     * The Will row is measured on the CoR ceiling, not on Will, because
     * [traits] can move the ceiling by three on their own. The Move row is
     * measured as it stands once Speed's is bought back: Move's base is Speed
     * rounded down, so the repair of one returns part of the other.
     */
    fun shortfall(s: Secondaries, traits: List<Trait>): SecondaryDelta {
        val speed = (MIN_SPEED_QUARTERS - s.basicSpeedQuarters).coerceAtLeast(0)
        val moveReturned = Math.floorDiv(s.basicSpeedQuarters + speed, 4) - Math.floorDiv(s.basicSpeedQuarters, 4)
        return SecondaryDelta(
            hp = (MIN_POOL_MAX - s.maxHp).coerceAtLeast(0),
            fp = (MIN_POOL_MAX - s.maxFp).coerceAtLeast(0),
            will = (MIN_COR_CEILING - Corruption.maxFor(s.will, traits)).coerceAtLeast(0),
            per = (MIN_PER - s.per).coerceAtLeast(0),
            basicSpeedQuarters = speed,
            basicMove = (MIN_MOVE - (s.basicMove + moveReturned)).coerceAtLeast(0),
        )
    }
}
