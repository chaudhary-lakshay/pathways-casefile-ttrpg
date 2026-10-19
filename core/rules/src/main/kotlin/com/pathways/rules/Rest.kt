package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.RestPeriod

/**
 * What a rest gives back: FP and SPI, each to be capped by the pool it
 * refills. [allFp] is a long rest's "All FP restored", which ends full even
 * from below 0, where [fp] alone would not.
 */
data class RestRecovery(val fp: Int, val spi: Int, val allFp: Boolean = false)

/**
 * Ch. 7's Rest (v6.9i): a Short Rest (3 hours) or a Long Rest (9 hours).
 *
 * FP is "1 FP per 10 minutes of rest", or "All FP restored" on a long one;
 * SPI is "1 SPI per hour", doubled "for that rest" by a successful
 * Cogitation roll, which the player rolls and declares.
 *
 * Not applied, each with its reason: HP, because ch. 5's daily HT roll is
 * the healing rule this app runs and ch. 7's "1 HP per 8 hours" restates it
 * at another rate (recorded, ch. 5 wins); "one rest per 24-hour period",
 * because the app holds no clock; the focused-task rolls, which are the
 * task's own rolls; and a long rest "counts as a full acting session", whose
 * quality the GM still judges on the digestion tracker.
 */
object Rest {

    private const val MINUTES_PER_FP = 10

    fun rests(): List<RestPeriod> = ContentLoader.restFile().rests

    fun named(name: String): RestPeriod = rests().firstOrNull { it.name == name }
        ?: throw IllegalArgumentException("no rest named '$name'")

    fun recovery(rest: RestPeriod, cogitated: Boolean, maxFp: Int): RestRecovery {
        val spi = ContentLoader.restFile().spi
        val fp = if (rest.allFp) maxFp else Math.floorDiv(rest.hours * 60, MINUTES_PER_FP)
        return RestRecovery(fp, rest.hours * if (cogitated) spi.cogitationPerHour else spi.perHour, rest.allFp)
    }
}
