package com.pathways.app

/**
 * The values a printed range offers, the row's own figure first.
 *
 * For a modifier table that prints a span rather than a number --
 * divination's "+0 to +2", chapter 13's boon rows, the ranged darkness row's
 * "-3 to -9 depending on severity", Appendix A's "Improvised / poor tools
 * -2 to -4" -- where the engine reading it takes a `gmValue` override,
 * defaulting to [from] when none is given.
 *
 * **These are examples, not a count.** This KDoc said "three" while a fourth
 * caller stood and "four" while eight content files stored a second bound
 * (6ee). Chapter 7's three ritual tables reach it through [valuesOf] (6ef);
 * the fright trigger's span is reached by a free stepper instead. To know
 * which tables print a span, query the content for `best`, `worstCase`,
 * `otherEnd` and `openEnded` -- do not read a number off this comment.
 *
 * **The order is outward from [from], not numeric.** A descending span like
 * "-1 to -2" or "-3 to -9" worsens as it runs, so sorting numerically would
 * put the worst case first and leave the chip the roll is ACTUALLY using
 * sitting at the far end of the row. First offered is first applied.
 *
 * Shared rather than written per screen: the boon dialog had this logic and
 * two others had nothing, which is how a chip reading "+0 to +2" came to add
 * zero. The plain skill roll had it all along under a different spelling --
 * the range sorted by distance from the printed figure, which is this list --
 * for as long as that copy stood. One function means the next screen cannot
 * get it wrong a new way.
 */
internal fun endsBetween(from: Int, to: Int?): List<Int> {
    if (to == null) return listOf(from)
    // No `to == from` short-circuit: `(n downTo n)` is already `[n]`, and a
    // branch no edit can change the output of is a comment pretending to be
    // code -- along with the test that was written to guard it.
    return if (to > from) (from..to).toList() else (from downTo to).toList()
}

/**
 * Steps offered past an open-ended cell's printed end ("-6 or worse",
 * "-4 or worse", "11-15+").
 *
 * The book names no ceiling, so this is a UI reading and not a rule. The
 * engines still validate whatever is passed and take a value further out, so
 * nothing here can widen the book -- or narrow it.
 */
internal const val OPEN_END_STEPS = 3

/** [from] and [OPEN_END_STEPS] values beyond it, running away from zero. */
internal fun endsBeyond(from: Int): List<Int> =
    (0..OPEN_END_STEPS).map { if (from < 0) from - it else from + it }

/**
 * Everything one cell offers: a span's whole run, an open end's first few
 * steps, or the single figure a plain cell prints. [openEnded] wins only
 * where there is no [to], which is how the book writes them -- no cell prints
 * both a far end and "or worse".
 */
internal fun valuesOf(from: Int, to: Int?, openEnded: Boolean): List<Int> =
    if (to == null && openEnded) endsBeyond(from) else endsBetween(from, to)

/**
 * The GM's pick for [row], out of the one [held] with the row it was made
 * inside -- null for any other row, and for no row at all.
 *
 * Filtered on read rather than cleared on change (6dz's design): a click
 * handler reads state as it stands NOW, and a `remember` keyed on the row
 * resets only at the next recomposition, so a row tap and a Roll tap inside
 * one frame carried Good's +2 onto Excellent, which the engine refuses.
 */
internal fun <R : Any> pickInside(row: R?, held: Pair<R, Int>?): Int? =
    held?.takeIf { it.first == row }?.second

/**
 * How many times a "per repeat" row may be counted.
 *
 * One is the row itself: "Previous divination about the same subject
 * (cumulative) -2 per repeat" prices the repeat that is happening, so
 * ticking the chip is already one. Four is where the offer stops, because the
 * book names no ceiling and a stepper running to a hundred is not a choice
 * made at a table.
 *
 * **That ceiling is harder here than the burn's, and the difference is
 * deliberate.** `BurnRow` offers four as an INCREMENT, so a caster who taps
 * twice has spent eight; this is a terminal selection, so a seventh repeat is
 * unexpressible. The narrower reading ships because the penalty grows without
 * limit and the row is the GM's declaration about history rather than a
 * purchase -- a diviner who has asked the same question seven times is in
 * territory the screen should hand back to the GM rather than price. Widen it
 * the day a table asks, not on the strength of the symmetry.
 */
internal fun repeatChoices(): List<Int> = (1..4).toList()
