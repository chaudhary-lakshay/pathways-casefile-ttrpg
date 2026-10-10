package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.ModifierId
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
 * **Specific still beats general.** A ritual and a divination charge their own
 * burn -- [RitualResolution.pushed] and [Divination.pushed] -- because
 * offering this one as well would charge a single point of effort twice: the
 * scoping the CoR ladder needed (6h), that keeps chapter 13's boons off Phase
 * I preparation (6r), and that withholds Appendix A's darkness row on a ranged
 * shot (6ae). This object belongs to the plain skill roll.
 *
 * That sentence was a claim about this app before it was a rule about the
 * book, and for a long time it was FALSE: neither of those functions existed
 * and no screen offered the push, so a Beyonder who rolled a ritual was told
 * about a rule on one screen and denied it on the next. A scoping argument
 * names what the other owner does, so it expires the day that owner stops
 * doing it -- which nothing re-reads (6dr).
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
            if (ca.attr in PHYSICAL_ATTRS) BurnKind.PHYSICAL
            else if (key.name in SUPERNATURAL_SKILLS) BurnKind.MYSTICAL
            else null
        ControllingAttribute.Spi -> BurnKind.MYSTICAL
        else -> null
    }

    /**
     * Skills the SPI row names outright, whose controlling attribute is not
     * SPI.
     *
     * The row reads "any **SPI-based or supernatural** skill roll (Ritualistic
     * Magic, Spiritual Perception, Occultism vs. Beyonder phenomena, etc.)".
     * Reading only the first half made "supernatural" do no work: Ritualistic
     * Magic is IQ/Very Hard, so the book's own FIRST example of a mystical
     * push could not be pushed. Spiritual Perception is already SPI-based and
     * needs no naming.
     *
     * **Occultism is deliberately absent.** Its entry is qualified by what is
     * being rolled AGAINST -- "vs. Beyonder phenomena" -- and a `SkillKey`
     * carries no subject matter, so the app cannot tell that roll from an
     * ordinary one about mundane history. Granting it always would push a
     * roll the book does not; refusing it silently would hide a rule. It is
     * reported instead, through [conditionalScope] -- the call the wealth
     * table's reaction modifier and a called shot's multiplier already take.
     *
     * The "etc." is likewise the GM's and is not guessed at. This set holds
     * only what the book prints without a qualifier.
     */
    val SUPERNATURAL_SKILLS = setOf(Conditions.RITUAL_SKILL)

    /**
     * The scope the book prints but this app cannot test, for [key], or null.
     *
     * Shown beside the roll so a GM can apply what the engine declines to.
     */
    fun conditionalScope(key: SkillKey): String? =
        if (key.name.equals(CONDITIONAL_SKILL, ignoreCase = true)) {
            "The book also allows a 1 SPI push on $CONDITIONAL_SKILL " +
                "vs. Beyonder phenomena. Whether this roll is that is yours to say."
        } else {
            null
        }

    /** Named in the SPI row, but only for part of what the skill covers. */
    const val CONDITIONAL_SKILL = "Occultism"

    /** The most points [kind] may buy, or null where the book prints no cap. */
    fun maxFor(kind: BurnKind): Int? = pool(kind).max

    /**
     * The bonus [points] spent on [kind] buys, given [alreadyBurned] on the
     * SAME roll.
     *
     * **The cap is per ROLL, not per spend.** "Max +5 per roll" -- so five
     * separate one-point pushes hit it exactly as one five-point push does,
     * and a caller that checked only its own call would let a stepper walk
     * past the book by clicking twice. Enforced here rather than by the
     * screen's chip list, because a UI that computes its own ceiling is a
     * second copy of the rule, and two copies is how these numbers stop
     * reconciling (6ai).
     *
     * The SPI row has no cap at all, so nothing is checked there beyond the
     * sign: "There is no cap -- you may spend as much SPI as you have", and
     * how much a character HAS is the tracker's question, not this object's.
     */
    fun bonusFor(kind: BurnKind, points: Int, alreadyBurned: Int = 0): Int {
        require(points >= 0) { "cannot burn a negative number of points; was $points" }
        require(alreadyBurned >= 0) { "already burned cannot be negative; was $alreadyBurned" }
        val row = pool(kind)
        val cap = row.max
        require(cap == null || alreadyBurned + points <= cap) {
            "${row.pool} buys at most +$cap per roll; " +
                "$alreadyBurned already spent plus $points is more than that"
        }
        return points * row.perPoint
    }

    /** How many more points [kind] may still buy on a roll [alreadyBurned] into. */
    fun remaining(kind: BurnKind, alreadyBurned: Int): Int? =
        pool(kind).max?.minus(alreadyBurned)?.coerceAtLeast(0)

    /**
     * The most points that may actually be BOUGHT, given the pool's balance.
     *
     * [remaining] answers the book's cap on the bonus; this answers what the
     * character can pay for. The two are different ceilings and the smaller
     * wins.
     *
     * **Only the mystical push is capped by its pool, and the asymmetry is the
     * book's.** Ch. 7 Power Source A says "SPI cannot be reduced below 0", and
     * a push is a choice, so [Pools.spendableSpi] bounds it. p. 42 prices FP
     * below 0 at 1 HP per point instead of forbidding it, so the physical push
     * keeps its unbounded reach -- the contrast that was 6ar's whole evidence.
     * Treating the pools alike in either direction erases a distinction the
     * book draws in two directions at once.
     *
     * This is the fifth SPI spend, and the one 6ar missed: it is not a dialog
     * of its own, so a sweep of the dialogs did not reach it. Kept here rather
     * than in the chip list because a screen that works out its own ceiling is
     * a second copy of the rule, which is how "max +5 per roll" came to be
     * checked per call instead of per roll.
     */
    fun affordable(kind: BurnKind, alreadyBurned: Int, spiAvailable: Int): Int {
        val capLeft = remaining(kind, alreadyBurned)
        val poolLeft = if (kind == BurnKind.MYSTICAL) Pools.spendableSpi(spiAvailable) else null
        return listOfNotNull(capLeft, poolLeft).min()
    }

    /**
     * Whether [outcome] is a roll effort can actually move.
     *
     * False for a roll that never happened -- there is no total to re-read --
     * and false when `requiresCritical` is set. That is arithmetic rather
     * than a reading: whether a roll is a critical depends on the TOTAL (3 or
     * 4), and a burn moves only the target, so under a rule that admits none
     * but criticals the displayed result cannot improve however much is
     * spent. Offering it anyway would charge FP, or SPI whose Soul Debt is
     * permanent (6m), for something incapable of helping.
     */
    fun canPush(outcome: SkillRollOutcome): Boolean =
        outcome.roll != null && !outcome.requiresCritical

    /**
     * The same dice read against a target [points] higher.
     *
     * The book allows this after the result is seen, which is the whole
     * reason it is a separate step rather than a modifier: the roll already
     * happened. [SuccessRoll.evaluate] re-reads the existing total, so the
     * dice are never touched twice -- rolling again would be a different
     * roll, and a seeded source would desynchronise.
     *
     * Returns [outcome] unchanged when [canPush] is false, so a caller that
     * forgets to ask cannot spend a pool on a roll the burn cannot move.
     *
     * The bonus joins [SkillRollOutcome.applied] as its own contribution.
     * Leaving it out would move the target while the breakdown beneath it
     * still summed to the old one -- the arithmetic-that-does-not-reconcile
     * defect 6ag added a 300-case test for.
     */
    fun applied(
        outcome: SkillRollOutcome,
        kind: BurnKind,
        points: Int,
        alreadyBurned: Int = 0,
    ): SkillRollOutcome {
        if (!canPush(outcome)) return outcome
        val roll = outcome.roll ?: return outcome
        val bonus = bonusFor(kind, points, alreadyBurned)
        if (bonus == 0) return outcome
        val target = outcome.target + bonus
        return outcome.copy(
            target = target,
            applied = outcome.applied + Contribution(listOf(idFor(kind)), bonus),
            roll = SuccessRoll.evaluate(roll.total, target),
        )
    }

    /**
     * [applied], or null where [points] is not a chip [affordable] would
     * still offer.
     *
     * What a screen calls. A push row is drawn against one count and can be
     * tapped against another: two taps a few milliseconds apart both reach
     * the handler of the row drawn before the first (6ei). [applied] throws
     * past the cap and knows no pool, so the second tap took the app down on
     * the FP row and bought Soul Debt on the SPI one. Null means nothing was
     * bought, and the caller charges nothing.
     *
     * The skill and attack dialogs drop that second tap themselves, because
     * they charge at the tap and hold only the balance they were drawn with.
     * This is the rule's own refusal, for a caller whose guard is wrong.
     */
    fun pushed(
        outcome: SkillRollOutcome,
        kind: BurnKind,
        points: Int,
        alreadyBurned: Int,
        spiAvailable: Int,
    ): SkillRollOutcome? =
        if (!canPush(outcome) || points !in 1..affordable(kind, alreadyBurned, spiAvailable)) {
            null
        } else {
            applied(outcome, kind, points, alreadyBurned)
        }

    /** The ledger name a burned point appears under. */
    fun idFor(kind: BurnKind) = ModifierId("burn:${pool(kind).pool.lowercase()}")

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

    // scopeOf(kind) lived here, uncalled and untested: a one-line wrapper for
    // pool(kind).scope, for "a screen that shows why it is offered". The
    // screen does show it -- from a row it already holds for pool.pool and
    // pool.max, so the wrapper would have resolved the same row twice to
    // deliver one of its three fields. An accessor for one field of three is
    // not an abstraction, and 6cf's API sweep is what surfaced it.

    private val PHYSICAL_ATTRS = setOf(Attr.ST, Attr.DX, Attr.HT)
}
