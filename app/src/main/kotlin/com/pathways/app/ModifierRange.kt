package com.pathways.app

/**
 * The values a printed range offers, the row's own figure first.
 *
 * Three of this book's modifier tables print a span rather than a number --
 * divination's "+0 to +2", chapter 13's boon rows, and the ranged darkness
 * row's "-3 to -9 depending on severity" -- and every engine that reads one
 * takes a `gmValue` override, defaulting to [from] when none is given.
 *
 * **The order is outward from [from], not numeric.** A descending span like
 * "-1 to -2" or "-3 to -9" worsens as it runs, so sorting numerically would
 * put the worst case first and leave the chip the roll is ACTUALLY using
 * sitting at the far end of the row. First offered is first applied.
 *
 * Shared rather than written per screen: the boon dialog had this logic and
 * the other two had nothing, which is how a chip reading "+0 to +2" came to
 * add zero. One function means a fourth screen cannot get it wrong a fourth
 * way.
 */
internal fun endsBetween(from: Int, to: Int?): List<Int> {
    if (to == null) return listOf(from)
    // No `to == from` short-circuit: `(n downTo n)` is already `[n]`, and a
    // branch no edit can change the output of is a comment pretending to be
    // code -- along with the test that was written to guard it.
    return if (to > from) (from..to).toList() else (from downTo to).toList()
}

/**
 * How many times a "per repeat" row may be counted.
 *
 * One is the row itself: "Previous divination about the same subject
 * (cumulative) -2 per repeat" prices the repeat that is happening, so
 * ticking the chip is already one. Four is where the offer stops, for the
 * reason the burn's own stepper stops at four -- the book names no ceiling,
 * and a stepper running to a hundred is not a choice made at a table.
 */
internal fun repeatChoices(): List<Int> = (1..4).toList()
