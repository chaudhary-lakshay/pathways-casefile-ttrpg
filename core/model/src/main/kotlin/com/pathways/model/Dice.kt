package com.pathways.model

import kotlinx.serialization.Serializable

/**
 * A GURPS dice expression: [count]d[sides] with a flat [modifier].
 *
 * Sides default to 6 and are usually omitted in the book, which creates
 * the notation trap this type exists to close: "1d-3" is 1d6 minus 3,
 * never 1d3. There is no minimum-damage rule in this rulebook, so a roll
 * may legitimately come out zero or negative.
 */
@Serializable
data class DiceExpr(val count: Int, val sides: Int = 6, val modifier: Int = 0) {

    init {
        require(count > 0) { "die count must be positive, was $count" }
        require(sides > 0) { "die size must be positive, was $sides" }
    }

    fun render(): String = buildString {
        append(count).append('d')
        if (sides != 6) append(sides)
        if (modifier > 0) append('+').append(modifier)
        if (modifier < 0) append(modifier)      // already carries its sign
    }

    companion object {
        private val PATTERN = Regex("""^(\d+)d(\d+)?([+-]\d+)?$""")

        fun parse(raw: String): DiceExpr {
            val m = PATTERN.matchEntire(raw.trim())
            require(m != null) { "not a dice expression: '$raw'" }
            val (count, sides, mod) = m.destructured
            return DiceExpr(
                count = count.toInt(),
                sides = sides.ifEmpty { "6" }.toInt(),
                modifier = mod.ifEmpty { "0" }.toInt(),
            )
        }
    }
}
