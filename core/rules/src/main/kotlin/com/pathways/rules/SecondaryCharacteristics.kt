package com.pathways.rules

import com.pathways.model.Attributes
import com.pathways.model.MAX_SECONDARY_DELTA_MAGNITUDE
import com.pathways.model.SecondaryDelta
import java.util.Locale

/** Computed secondary characteristics. Never persisted — spec 3.2. */
data class Secondaries(
    val maxHp: Int,
    val maxFp: Int,
    val will: Int,
    val per: Int,
    val basicSpeedQuarters: Int,
    val basicMove: Int,
    val dodge: Int,
) {
    /** For display only. The engine never computes on this. */
    val basicSpeed: String get() = String.format(Locale.ROOT, "%.2f", basicSpeedQuarters / 4.0)
}

object SecondaryCharacteristics {

    private const val COST_HP = 2
    private const val COST_FP = 3
    private const val COST_WILL = 5
    private const val COST_PER = 5
    private const val COST_SPEED_QUARTER = 5
    private const val COST_MOVE = 5

    fun of(a: Attributes, d: SecondaryDelta): Secondaries {
        // (HT+DX)/4 expressed in quarters is simply HT+DX. At 9/9 that is
        // 18 quarters = 4.50, matching the book.
        val speedQuarters = (a.ht + a.dx) + d.basicSpeedQuarters
        val wholeSpeed = Math.floorDiv(speedQuarters, 4)
        return Secondaries(
            maxHp = a.st + d.hp,
            maxFp = a.ht + d.fp,
            will = a.iq + d.will,
            per = a.iq + d.per,
            basicSpeedQuarters = speedQuarters,
            basicMove = wholeSpeed + d.basicMove,
            // Ch. 5: Dodge follows Basic SPEED, so a Basic Move purchase does not raise it.
            dodge = wholeSpeed + 3,
        )
    }

    fun costOf(d: SecondaryDelta): Int =
        d.hp * COST_HP +
            d.fp * COST_FP +
            d.will * COST_WILL +
            d.per * COST_PER +
            d.basicSpeedQuarters * COST_SPEED_QUARTER +
            d.basicMove * COST_MOVE

    /**
     * The steps bought on the book's row [key] (Ch. 2, pp. 8-9), or 0.
     *
     * The key is the one `secondary_costs.json` prints, and this is the ONE
     * place that says which field a row moves: the builder's stepper, the
     * save file's resolver and the value shown beside it all ask here rather
     * than each carrying a copy of the table.
     *
     * Basic Speed counts QUARTERS, because that is the step the book prices.
     * A row the table refuses to price -- SPI, CoR, Digestion -- and a key
     * the book does not print answer 0 and change nothing: [SecondaryDelta]
     * has no field for any of them, so the refusal is structural.
     */
    fun stepsOf(d: SecondaryDelta, key: String): Int = when (key) {
        "hp" -> d.hp
        "fp" -> d.fp
        "will" -> d.will
        "per" -> d.per
        "basicSpeed" -> d.basicSpeedQuarters
        "basicMove" -> d.basicMove
        else -> 0
    }

    /**
     * [d] with the book's row [key] set to [steps].
     *
     * Coerced into [MAX_SECONDARY_DELTA_MAGNITUDE] rather than allowed to
     * throw: the steps arrive from a save file, and `SessionStore.load`
     * discards the WHOLE file on a decode failure, so one hand-edited number
     * would delete every other edit a player had made.
     */
    fun withSteps(d: SecondaryDelta, key: String, steps: Int): SecondaryDelta {
        val n = steps.coerceIn(-MAX_SECONDARY_DELTA_MAGNITUDE, MAX_SECONDARY_DELTA_MAGNITUDE)
        return when (key) {
            "hp" -> d.copy(hp = n)
            "fp" -> d.copy(fp = n)
            "will" -> d.copy(will = n)
            "per" -> d.copy(per = n)
            "basicSpeed" -> d.copy(basicSpeedQuarters = n)
            "basicMove" -> d.copy(basicMove = n)
            else -> d
        }
    }

    /**
     * [base] with [steps] bought on top of it, row by row.
     *
     * ADDED, never substituted: a potion grant lands in this same field
     * (`PotionDigestion`), so replacing it with a sparse map naming one row
     * would silently zero a granted Will the moment a player bought a point
     * of HP (Codex, on the design).
     */
    fun plusSteps(base: SecondaryDelta, steps: Map<String, Int>): SecondaryDelta =
        steps.entries.fold(base) { acc, (key, n) ->
            // The saved number is coerced BEFORE the addition, not only by
            // [withSteps] after it: a hand-edited Int.MAX_VALUE plus a granted
            // step overflows to a negative, which the bound would then accept
            // (Codex).
            withSteps(acc, key, stepsOf(acc, key) + n.coerceIn(-MAX_SECONDARY_DELTA_MAGNITUDE, MAX_SECONDARY_DELTA_MAGNITUDE))
        }

    /**
     * What the book's row [key] currently reads, or null where the table
     * prints no price and therefore offers nothing to buy.
     *
     * Basic Speed shows the book's own two decimals; every other row is a
     * whole number. Formatting lives here for the reason [Secondaries
     * .basicSpeed] does: the engine never computes on it.
     */
    fun valueLabelOf(s: Secondaries, key: String): String? = when (key) {
        "hp" -> s.maxHp.toString()
        "fp" -> s.maxFp.toString()
        "will" -> s.will.toString()
        "per" -> s.per.toString()
        "basicSpeed" -> s.basicSpeed
        "basicMove" -> s.basicMove.toString()
        else -> null
    }
}
