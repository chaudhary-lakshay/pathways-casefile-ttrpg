package com.pathways.rules

import com.pathways.rules.content.AntiDivinationTechnique
import com.pathways.rules.content.ContentLoader

/**
 * What a target spends SPI to hide behind (Ch. 6.5, Sec. 3, 3A-3F).
 *
 * This is what fills [AntiDivination.against]'s `techniquePenalty`, which has
 * been a parameter with no caller since the diviner's half of the chapter was
 * read. The seam is deliberate: these are the TARGET's declarations and the
 * counters halve whatever total arrives.
 *
 * **Sum, then halve.** "All active techniques stack -- their penalties add
 * together when a divination is attempted", so they are one penalty by the
 * moment Sec. 6 acts on it. The book's own numbers cannot tell that apart from
 * halving each and then summing: only 3A (-2) and 3B (-4) print a penalty and
 * both are EVEN, so the two orders agree on every combination this book can
 * produce -- the Major Wound trap (6n) with the parities the other way round.
 * `extract_detection.verify()` fails the day an odd one is printed, which is
 * the day this reading has to be made again rather than inherited.
 *
 * **Four of the six price no divination attempt at all.** 3C diverts the
 * divination to someone else, 3D redirects it, 3E answers with a historical
 * figure and 3F with a false echo. None of them is a to-hit penalty, so none
 * carries one -- charging them 0 would be indistinguishable from a technique
 * that genuinely costs the diviner nothing.
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
     * What [chosen] costs the target in SPI.
     *
     * Refuses a technique the book prices in prose rather than in a number:
     * 3F costs "SPI equal to what a divination of that scope would normally
     * cost", which is the divination's own cost and not this object's to
     * guess. Reading it as free would be the cheapest technique in the
     * chapter having the strongest effect.
     */
    fun spiFor(chosen: List<AntiDivinationTechnique>): Int =
        chosen.distinctBy { it.id }.sumOf {
            it.spi ?: throw IllegalArgumentException(
                "${it.id} ${it.name} prints no SPI cost: \"${it.bullet("Cost")}\"",
            )
        }
}
