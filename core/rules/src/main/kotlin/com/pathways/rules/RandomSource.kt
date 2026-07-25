package com.pathways.rules

import com.pathways.model.DiceExpr
import kotlin.random.Random

/** Randomness as a dependency, so every roll in the test suite is reproducible. */
fun interface RandomSource {
    /** Returns a value in 1..[sides]. */
    fun roll(sides: Int): Int
}

class SeededRandom(seed: Long) : RandomSource {
    private val rng = Random(seed)
    override fun roll(sides: Int): Int = rng.nextInt(1, sides + 1)
}

fun DiceExpr.roll(source: RandomSource): Int =
    (1..count).sumOf { source.roll(sides) } + modifier
