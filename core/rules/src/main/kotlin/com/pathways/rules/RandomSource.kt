package com.pathways.rules

import com.pathways.model.DiceExpr
import kotlin.random.Random

/** Randomness as a dependency, so every roll in the test suite is reproducible. */
fun interface RandomSource {
    /** Returns a value in 1..[sides]. */
    fun roll(sides: Int): Int

    /**
     * The faces of one throw, reported by the roller after taking them:
     * [target] for a success roll, null for a table or damage. A no-op here;
     * the app's roll log listens. Dice only: a verdict is the engine's, and
     * the PCR, a demoted divination and every push decide it after this.
     */
    fun thrown(faces: List<Int>, target: Int?) {}
}

/** 3d6 for a table, reported through [RandomSource.thrown] with no target. */
fun RandomSource.threeDSix(): Int = throwThree(null)

/** Three d6, reported with [target]: [SuccessRoll]'s throw and the tables'. */
internal fun RandomSource.throwThree(target: Int?): Int {
    val faces = List(3) { roll(6) }
    thrown(faces, target)
    return faces.sum()
}

class SeededRandom(seed: Long) : RandomSource {
    private val rng = Random(seed)
    override fun roll(sides: Int): Int = rng.nextInt(1, sides + 1)
}

fun DiceExpr.roll(source: RandomSource): Int =
    (1..count).sumOf { source.roll(sides) } + modifier
