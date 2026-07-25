package com.pathways.rules

import com.pathways.model.Attributes
import com.pathways.model.SecondaryDelta

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
    val basicSpeed: String get() = "%.2f".format(basicSpeedQuarters / 4.0)
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
}
