package com.pathways.rules

import com.pathways.rules.content.AntiDivinationTechnique
import com.pathways.rules.content.ContentLoader

/**
 * What a target spends SPI to hide behind (Ch. 8 IV, Sec. 3, 3A-3B).
 *
 * This is what fills [AntiDivination.against]'s `techniquePenalty`, which has
 * been a parameter with no caller since the diviner's half of the chapter was
 * read. The seam is deliberate: these are the TARGET's declarations and the
 * counters halve whatever total arrives.
 *
 * **Sum, then halve.** "All active techniques stack -- their penalties add
 * together when a divination is attempted", so they are one penalty by the
 * moment Sec. 6 acts on it. The book's own numbers cannot tell that apart from
 * halving each and then summing: only 3A (-2) prints a penalty and it is
 * EVEN, so the two orders agree on every combination this book can produce.
 * `extract_detection.verify()` fails the day an odd one is printed, which is
 * the day this reading has to be made again rather than inherited.
 *
 * **3B prices no divination attempt at all.** It redirects the divination,
 * which is not a to-hit penalty, so it carries none -- charging it 0 would be
 * indistinguishable from a technique that genuinely costs the diviner nothing.
 *
 * **3A reaches both rolls**, and they are separate fields for that reason: -2
 * on the diviner's and +2 on the target's own detection roll ([Detection]).
 *
 * **Durations are not tracked.** Every technique prints one and nothing here
 * holds a clock, so a technique is a declaration: spend the SPI through the
 * tracker, then say the ward is up. The printed duration travels with the row
 * for the GM to read.
 */
object AntiDivinationTechniques {

    /** All six, in printed order. */
    fun all(): List<AntiDivinationTechnique> = ContentLoader.antiDivinationTechniques()

    /** The technique with this id ("3A") or printed name. */
    fun named(idOrName: String): AntiDivinationTechnique =
        all().firstOrNull {
            it.id.equals(idOrName, ignoreCase = true) ||
                it.name.equals(idOrName, ignoreCase = true)
        } ?: throw IllegalArgumentException(
            "no anti-divination technique called '$idOrName'; the book prints " +
                all().joinToString { "${it.id} ${it.name}" },
        )

    /**
     * What [chosen] costs the diviner, summed before any halving.
     *
     * Zero or negative. A technique printing no penalty contributes nothing,
     * and naming one twice pays once -- a target has one ward.
     */
    fun penaltyTotal(chosen: List<AntiDivinationTechnique>): Int =
        chosen.distinctBy { it.id }.sumOf { it.divinationPenalty ?: 0 }

    /** What [chosen] is worth on the target's own detection roll (3A alone). */
    fun detectionBonus(chosen: List<AntiDivinationTechnique>): Int =
        chosen.distinctBy { it.id }.sumOf { it.detectionBonus ?: 0 }

    /**
     * The techniques a target may raise to help their OWN detection roll --
     * 3A alone, because the ward is the only row printing a detection bonus.
     *
     * Resolved here rather than filtered again at the screen: the offer and
     * any guard on it must read one predicate, or a widening moves the chips
     * and leaves the guard checking the set it used to be.
     *
     * It answers which rows move the roll, and [spiFor] what a set costs.
     */
    fun onDetectionRoll(): List<AntiDivinationTechnique> =
        all().filter { it.detectionBonus != null }

    /** What [chosen] costs the target in SPI. */
    fun spiFor(chosen: List<AntiDivinationTechnique>): Int =
        chosen.distinctBy { it.id }.sumOf { it.spi }
}
