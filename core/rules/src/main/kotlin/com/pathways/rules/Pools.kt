package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.PoolThreshold

/** The three pools a character spends and loses in play (Ch. 5). */
enum class Pool { HP, FP, SPI }

/**
 * What a penalty applies to. Carried beside the number so it cannot be
 * misapplied: Spiritual Attrition is "-3 to all SPI-based skill rolls and
 * Beyonder ability checks" (p. 35), which is not -3 to a sword swing.
 */
enum class PenaltyScope { ALL_ROLLS, SPI_ROLLS }

/**
 * Where a pool stands and what the book says about standing there.
 *
 * [level] and [effect] are the printed row, verbatim, so a GM can see the
 * rule rather than this object's reading of it. Everything else is that
 * reading, and each field is pinned by `PoolsTest`.
 */
data class PoolStatus(
    val pool: Pool,
    val current: Int,
    val max: Int,
    val level: String,
    val effect: String,
    val penalty: Int = 0,
    val scope: PenaltyScope = PenaltyScope.ALL_ROLLS,
    val corGained: Int = 0,
    val isDead: Boolean = false,
    val isUnconscious: Boolean = false,
    val abilitiesOffline: Boolean = false,
    val rollsToStayConscious: Boolean = false,
    val rollsToSurvive: Boolean = false,
)

/**
 * HP, FP and SPI against their thresholds (Ch. 5).
 *
 * Nothing is stored here: a status is computed from a current value and a
 * maximum, exactly like [Corruption]. Current values are table state and
 * belong to whoever is running the session.
 *
 * **HP and SPI come from the book's own tables** (`pool_thresholds.json`).
 * **FP does not have one.** Its thresholds are printed in prose, twice, in
 * two different sections, and they do not say the same thing:
 *
 *  - p. 42, the recovery table: "At 1/3 FP or below: -1 to all rolls; Move
 *    and Dodge halved".
 *  - p. 41, spirit body damage: "FP below 0: -1 to all rolls per 1 FP below
 *    0. If FP drops below -1xMaxFP, you fall unconscious."
 *
 * Two penalties, and the book never says whether they stack. [fp] applies
 * the WORSE of the two rather than summing them, which is the reading that
 * cannot make a character worse off than either rule alone states. The
 * disagreement is recorded, not resolved -- see `tools/extract_pools.py`.
 */
object Pools {

    /** "1/3 HP remaining" and "<= 1/3 Max SPI" -- thirds, floored (CLAUDE.md 4.3). */
    private const val THIRD = 3

    /** p. 40: "At -5xHP, death is automatic with no roll." */
    private const val DEAD_MULTIPLE = 5

    /** p. 40: "Reeling: -1 to all rolls". */
    const val REELING_PENALTY = -1

    /** p. 35: "Spiritual Attrition: -3 to all SPI-based skill rolls...". */
    const val ATTRITION_PENALTY = -3

    /** p. 42: "At 1/3 FP or below: -1 to all rolls; Move and Dodge halved". */
    const val TIRED_PENALTY = -1

    /**
     * Printed pool rules this app does not apply, each beside its reason.
     *
     * The standard is [ActiveDefenses.UNPAYABLE]'s and [Divination.UNAPPLIED]'s:
     * a printed rule that reaches nothing says so in production source rather
     * than in a journal, so a sweep over Kotlin finds the reason. Its scope is
     * the same and no larger -- removing an entry here turns no test red.
     *
     * **Recovery is temporal and this object is not.** Everything above
     * computes a status from a current value and a maximum; nothing here
     * advances a pool, because current values are table state the session
     * owner holds. That is the whole reason, and it is the same one
     * [Study] gives for not earning study points over downtime periods.
     *
     * **The subject is chapter 4, not the quick-reference table.** `Study`'s
     * KDoc cited *"Appendix A's SPI recovery"*, which is the SUMMARY (p. 160,
     * three cells: Rest / 1 SPI per hour / the requirement). p. 34 prints the
     * rule and carries two clauses that row drops, and the exclusivity one is
     * what a reader needs. Pinned by `test_tools.py`.
     */
    val UNMODELLED = mapOf(
        "Spirituality Recovery" to
            "p. 34: \"recovers 1 SPI per hour. This is the standard and only " +
            "recovery rate. Combat, ritual casting, or heavy physical " +
            "exertion interrupts rest and halts recovery.\" -- both the rate " +
            "and the interruption are clock-bound, and this app holds no " +
            "clock to run either against. p. 88's Sequence 9 Bard prints a " +
            "narrower ability that restores SPI outside ordinary rest " +
            "(\"allies recover 1 SPI at the song's end\", two at p. 100). " +
            "Reading that as specific-beating-general rather than as a " +
            "contradiction is THIS REPO's, on the model of the Potion " +
            "Consumption Roll against SuccessRoll's crits; the book states " +
            "no priority. Nothing turns on it while both are unmodelled, and " +
            "pathway prose is not modelled here either",
    )

    /** The book's printed rows for [pool]; empty for [Pool.FP], which has none. */
    fun thresholds(pool: Pool): List<PoolThreshold> =
        ContentLoader.poolThresholds().filter { it.pool == pool.name }

    /**
     * Physical health (p. 40).
     *
     * Reeling is not withdrawn as HP falls further: the book states it at 1/3
     * and never lifts it, so it still applies at -2 HP. Death at -5xHP is
     * reported with both roll flags CLEAR, because that row's own text is
     * "Dead -- no roll required".
     */
    fun hp(current: Int, max: Int): PoolStatus {
        requireMax(max, Pool.HP)
        val rows = thresholds(Pool.HP)
        val dead = current <= -DEAD_MULTIPLE * max
        val row = when {
            dead -> rows[4]
            current <= -max -> rows[3]
            current <= 0 -> rows[2]
            current <= Math.floorDiv(max, THIRD) -> rows[1]
            else -> rows[0]
        }
        return PoolStatus(
            pool = Pool.HP,
            current = current,
            max = max,
            level = row.level,
            effect = row.effect,
            penalty = if (current <= Math.floorDiv(max, THIRD)) REELING_PENALTY else 0,
            isDead = dead,
            rollsToStayConscious = !dead && current <= 0,
            rollsToSurvive = !dead && current <= -max,
        )
    }

    /**
     * Fatigue (p. 41-42). See this object's KDoc for why FP is code, not
     * content, and why the two printed penalties are taken at their worse
     * rather than summed.
     */
    fun fp(current: Int, max: Int): PoolStatus {
        requireMax(max, Pool.FP)
        val tired = if (isFatigued(current, max)) TIRED_PENALTY else 0
        // "-1 to all rolls per 1 FP below 0": at -3 FP that is -3.
        val spent = if (current < 0) current else 0
        return PoolStatus(
            pool = Pool.FP,
            current = current,
            max = max,
            level = fpLevel(current, max),
            effect = fpEffect(current, max),
            penalty = minOf(tired, spent),
            isUnconscious = current < -max,
        )
    }

    /**
     * Whether [fp] stands at p. 42's fatigue threshold: "At 1/3 FP or below".
     *
     * One predicate, four readers -- [fp]'s penalty, the printed level, the
     * printed effect, and the defence screen's Dodge. Until this existed each
     * site re-derived the comparison, which is the shape that lets one screen
     * print a rule while another ignores it.
     *
     * True below zero as well: the row says "or below" and "Until above
     * threshold". The effect line switches to p. 41's harsher penalty there
     * and stops naming the halving, so the state cannot be read off the text.
     *
     * @throws IllegalArgumentException if [fp] is not an FP status. Reeling
     * (1/3 HP) and Spiritual Attrition (1/3 SPI) share the fraction and halve
     * nothing.
     */
    fun isFatigued(fp: PoolStatus): Boolean {
        require(fp.pool == Pool.FP) {
            "fatigue is an FP state; ${fp.pool} has its own 1/3 row and halves nothing (p. 42)"
        }
        return isFatigued(fp.current, fp.max)
    }

    /**
     * [value] as fatigue leaves it: "Move and Dodge halved" (p. 42).
     *
     * **The rounding direction is this app's, not the book's.** That row
     * prints "halved" bare, where the book prints "rounded down" for a
     * Deceptive Attack's cap and "round up" for a halved FP maximum -- it has
     * no house convention to fall back on. Rounding UP is recorded as a
     * choice, on the one ground that survives: it cannot leave a character
     * worse off than the printed sentence states.
     *
     * Applied to the BASE score only. The bonuses the defence screen adds
     * afterwards -- a shield's DB, a Retreat, an Acrobatic Dodge -- are
     * printed as whole numbers by rules of their own, and halving the total
     * would quietly halve those too.
     */
    fun halvedForFatigue(value: Int, fp: PoolStatus): Int =
        if (isFatigued(fp)) -Math.floorDiv(-value, 2) else value

    /**
     * Spirituality (p. 35).
     *
     * [max] must be at least 1. A mortal has no SPI pool at all -- "Non-
     * Beyonders (SPI 0) ... have no spirituality to sacrifice" (p. 41) -- and
     * reporting them as Spiritually Exhausted would be a status they cannot
     * have. Callers check the maximum first.
     *
     * SPI loss never kills: "Unlike HP depletion, there is no risk of death
     * from SPI loss" (p. 35). Below zero it buys Soul Debt instead, one CoR
     * per point, which [Corruption] then caps against Will.
     */
    /** SPI can never be spent below this BY CHOICE. See [spendableSpi]. */
    const val SPI_VOLUNTARY_FLOOR = 0

    /**
     * How much SPI a character may VOLUNTARILY spend right now.
     *
     * The book separates two routes to a negative pool, and this is the one it
     * closes. Soul Debt is defined twice and both definitions sit inside
     * **Spirit Body Damage** -- "you may choose a pool already at 0 (risking
     * death, unconsciousness, or Soul Debt)" and "SPI below 0: Soul Debt". It
     * is what an ATTACK on the spirit body does to you.
     *
     * Spending is capped by what you have, in two places:
     *  - the pool table's own 0 SPI row -- "Spiritual Exhaustion: all Beyonder
     *    abilities deactivate; **cannot activate any SPI-cost abilities**";
     *  - ch. 7's Power Source A -- "The caster pays SPI equal to the ritual's
     *    complexity cost. **SPI cannot be reduced below 0.**"
     *
     * **The contrast with FP is the evidence, not either sentence alone.**
     * p. 42 prints "Every point of FP spent below 0 also costs 1 HP", so the
     * book knows exactly how to permit overspending and price it. It does
     * that for FP and states the opposite for SPI. Treating the two pools
     * alike erases a distinction the book draws in two directions at once --
     * which is what this app did, charging permanent Soul Debt CoR for
     * rituals, divinations and artifact uses no caster could have paid for.
     *
     * [current] may be negative -- spirit damage can leave it there -- and a
     * character already in debt may spend NOTHING rather than a negative
     * amount.
     */
    fun spendableSpi(current: Int): Int = current.coerceAtLeast(SPI_VOLUNTARY_FLOOR)

    /**
     * Whether [cost] can be paid from [current] without going below zero.
     *
     * A cost of zero is affordable at any pool, a mortal's included: the book
     * charges nothing for it, and refusing would gate an action it gives away
     * free.
     */
    fun canAffordSpi(cost: Int, current: Int): Boolean {
        require(cost >= 0) { "an SPI cost cannot be negative (was $cost)" }
        return cost <= spendableSpi(current)
    }

    fun spi(current: Int, max: Int): PoolStatus {
        require(max >= 1) {
            "SPI max must be at least 1; a mortal has no SPI pool to report on (p. 41)"
        }
        val rows = thresholds(Pool.SPI)
        val row = when {
            current < 0 -> rows[3]
            current == 0 -> rows[2]
            current <= Math.floorDiv(max, THIRD) -> rows[1]
            else -> rows[0]
        }
        return PoolStatus(
            pool = Pool.SPI,
            current = current,
            max = max,
            level = row.level,
            effect = row.effect,
            penalty = if (current <= Math.floorDiv(max, THIRD)) ATTRITION_PENALTY else 0,
            scope = PenaltyScope.SPI_ROLLS,
            corGained = if (current < 0) -current else 0,
            abilitiesOffline = current <= 0,
        )
    }

    private fun fpLevel(current: Int, max: Int): String = when {
        current < -max -> "below -1×Max FP"
        current < 0 -> "below 0 FP"
        isFatigued(current, max) -> "1/3 FP or below"
        else -> "FP to 1"
    }

    private fun fpEffect(current: Int, max: Int): String = when {
        current < -max -> "Unconscious (p. 41)."
        current < 0 ->
            "-1 to all rolls per 1 FP below 0 (p. 41). Every point spent below 0 also costs 1 HP (p. 42)."
        isFatigued(current, max) -> "-1 to all rolls; Move and Dodge halved (p. 42)."
        else -> "Normal function; no penalties."
    }

    /** The comparison itself, for the three sites that have only the numbers. */
    private fun isFatigued(current: Int, max: Int): Boolean =
        current <= Math.floorDiv(max, THIRD)

    private fun requireMax(max: Int, pool: Pool) =
        require(max >= 1) { "$pool max must be at least 1; was $max" }
}
