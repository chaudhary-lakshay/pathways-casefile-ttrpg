package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.SkillKey
import com.pathways.rules.content.BurnablePool
import com.pathways.rules.content.ContentLoader

/** Which pool a roll may be pushed with, if any (Ch. 2, p. 9). */
enum class BurnKind { PHYSICAL, MYSTICAL }

/**
 * Burning FP or SPI to push a roll (Ch. 2, p. 9).
 *
 * "Spend 1 FP to gain +1 on any roll based on ST, DX, or HT ... Max +5 per
 * roll." "Spend 1 SPI to gain +1 on any SPI-based or supernatural skill roll
 * ... There is no cap." Both are usable AFTER the dice are seen, and the
 * section closes with a sentence nothing in this app enforced until now:
 * "You cannot spend both FP and SPI on the same roll."
 *
 * **The warrant is that two later chapters implement it already.**
 * [Divination.BURN_PER_SPI] (ch 6.5, p. 55) and [RitualResolution]'s `burned`
 * (ch 7, p. 66) are this same rule scoped to their own rolls, and they agree
 * with it exactly -- 1 SPI, +1, no cap. That agreement is why the general
 * form may be adopted; the per-table standard 6ad set, and 6v is why it must
 * be per-table. Neither chapter prints the FP half, the +5 cap, or the
 * either-or; those come from here alone.
 *
 * **Specific still beats general.** A ritual and a divination keep charging
 * their own burn, because offering this one as well would charge a single
 * point of effort twice -- the scoping the CoR ladder needed (6h), that keeps
 * chapter 13's boons off Phase I preparation (6r), and that withholds
 * Appendix A's darkness row on a ranged shot (6ae). This object belongs to
 * the plain skill roll.
 *
 * **The bonus raises the TARGET, it does not lower the roll.** The dice are
 * already cast when it is spent, so pushing means the same total is read
 * against a higher number -- [Divination.attempt] already does exactly that.
 */
object EffortBurn {

    /** Both printed pools, in printed order. */
    fun all(): List<BurnablePool> = ContentLoader.effortBurn()

    fun pool(kind: BurnKind): BurnablePool =
        all().firstOrNull { it.kind.equals(kind.name, ignoreCase = true) }
            ?: error("the book prints no ${kind.name.lowercase()} burn")

    /**
     * Which pool [key] may be pushed with, or null when the book names
     * neither.
     *
     * IQ, Will and Per skills fall in that gap: the FP row lists ST, DX and
     * HT and the SPI row lists SPI-based rolls, and neither mentions the
     * mental attributes. Null rather than a default, so a caller cannot push
     * a Research roll with fatigue on the strength of the book saying
     * nothing -- the refusal `Languages.ritualModifier` makes about a common
     * tongue's silence (6x).
     */
    fun kindFor(key: SkillKey): BurnKind? = when (val ca = key.attr) {
        is ControllingAttribute.Buyable ->
            if (ca.attr in PHYSICAL_ATTRS) BurnKind.PHYSICAL else null
        ControllingAttribute.Spi -> BurnKind.MYSTICAL
        else -> null
    }

    /** The most points [kind] may buy, or null where the book prints no cap. */
    fun maxFor(kind: BurnKind): Int? = pool(kind).max

    /**
     * The bonus [points] spent on [kind] buys.
     *
     * Refuses a negative, and refuses more than the printed cap. The SPI row
     * has no cap at all, so nothing is checked there beyond the sign: "There
     * is no cap -- you may spend as much SPI as you have", and how much a
     * character HAS is the tracker's question, not this object's.
     */
    fun bonusFor(kind: BurnKind, points: Int): Int {
        require(points >= 0) { "cannot burn a negative number of points; was $points" }
        val row = pool(kind)
        val cap = row.max
        require(cap == null || points <= cap) {
            "${row.pool} buys at most +$cap per roll; $points is more than that"
        }
        return points * row.perPoint
    }

    /**
     * The same dice read against a target [points] higher.
     *
     * The book allows this after the result is seen, which is the whole
     * reason it is a separate step rather than a modifier: the roll already
     * happened. [SuccessRoll.evaluate] re-reads the existing total, so the
     * dice are never touched twice -- rolling again would be a different
     * roll, and a seeded source would desynchronise.
     *
     * A forbidden roll is returned unchanged. There is no total to re-read,
     * and buying a bonus on a roll that never happened would invent one.
     */
    fun applied(outcome: SkillRollOutcome, kind: BurnKind, points: Int): SkillRollOutcome {
        val roll = outcome.roll ?: return outcome
        val bonus = bonusFor(kind, points)
        if (bonus == 0) return outcome
        val target = outcome.target + bonus
        return outcome.copy(target = target, roll = SuccessRoll.evaluate(roll.total, target))
    }

    /**
     * The either-or, as a check a caller can make before offering both.
     *
     * "You cannot spend both FP and SPI on the same roll." Refused rather
     * than silently preferring one: a caller handed a quietly dropped pool
     * would charge for it and not receive it.
     */
    fun requireOnePool(fp: Int, spi: Int) {
        require(fp == 0 || spi == 0) {
            "the book allows one pool per roll, not both; was $fp FP and $spi SPI"
        }
    }

    /** The row's own words, for a screen that shows why it is offered. */
    fun scopeOf(kind: BurnKind): String = pool(kind).scope

    private val PHYSICAL_ATTRS = setOf(Attr.ST, Attr.DX, Attr.HT)
}
