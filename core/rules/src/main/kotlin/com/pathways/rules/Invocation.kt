package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.EntityResponse

/**
 * What the entity gave, once it has been asked.
 *
 * [roll] is what the dice said and [shifted] is what was read off the table --
 * they differ only for an orthodox deity in good standing. [ritualModifier] is
 * 0 for a rejection: the refusal travels in [failsRitual], never as a number a
 * caller might add to a target and then roll against anyway.
 */
data class EntityAnswer(
    val roll: Int,
    val shifted: Int,
    val response: EntityResponse,
    val ritualModifier: Int,
    val failsRitual: Boolean,
    /** The "No response" row: asked, and nothing came back. */
    val unanswered: Boolean = false,
)

/** What an attempt is charged, in SPI and in HP sacrificed toward it. */
data class Fuel(val spi: Int, val hp: Int)

/**
 * Borrowing a ritual's power from an entity (Ch. 7, Section IV.F, p. 66).
 *
 * "The GM rolls secretly on the Entity Response Table first, then applies the
 * entity's modifier to the Ritualistic Magic roll."
 *
 * **The orthodox shift lands on the WRONG roll if read quickly.** The book
 * says orthodox deities "shift the table result by -2 when the request aligns
 * with their domain and the caster is in good standing". That -2 is applied to
 * the GM's 3d6 against THIS table, not to the ritual roll. The table runs
 * best-first -- 3-5 is a Full response worth +5, 18 is Active rejection -- so
 * subtracting 2 walks a favoured caster toward the good rows. It is a reward.
 * Applied to the ritual roll it becomes a penalty, and a Nighthawk praying to
 * the Evernight Goddess ends up worse off than a stranger. Hence
 * [shiftedRoll], which takes a roll and returns a roll: nothing here hands
 * back a number that could be added to a skill by mistake.
 *
 * **Two rows print no modifier, and they are not the same absence.** 12-14 is
 * an entity that does not answer -- the ritual proceeds unaided, so nothing is
 * added. 18 is a refusal: the ritual fails whatever the caster rolled, exactly
 * as a Broken mystical language forbids the attempt instead of pricing it
 * ([Languages]). [ritualModifier] answers 0 for the first and refuses the
 * second.
 *
 * **The secret roll is not modelled.** The book has the GM roll this unseen,
 * and this app is one screen held by one person -- hiding a roll from its only
 * viewer is theatre, not a rule. A GM running the table can roll here and
 * simply not say what came up.
 */
object Invocation {

    /**
     * What an orthodox deity in good standing subtracts from the TABLE roll.
     *
     * Negative BECAUSE the table is ordered best-first. Kept here rather than
     * in the JSON because it is a number inside a sentence -- the same split
     * the maneuver table's +4/-4 and [RitualMode.SUPPORT_CAP] take -- and
     * because a committed column would lose the fact that matters most about
     * it: which roll it lands on.
     */
    const val ORTHODOX_SHIFT = -2

    /**
     * What the caster pays personally when the entity does not answer.
     *
     * The 12-14 row reads "No response. Ritual draws only on caster's residual
     * power (treat as 2 SPI personal)." That parenthetical is the ONLY place
     * this chapter prices a ritual fuelled by invocation, and it was printed,
     * committed to `entity_response.json` and charged by nothing -- the app
     * spent the declared complexity cost on every row, so a Heavy invocation
     * that went unanswered took 8+ SPI where the book prints 2.
     *
     * Kept here rather than in the JSON because it is a number inside a
     * sentence, the same split [ORTHODOX_SHIFT] and [RitualMode.SUPPORT_CAP]
     * take. `InvocationTest` pins the sentence it was read from.
     */
    const val RESIDUAL_SPI = 2

    /** The lowest and highest a 3d6 can show, and this table's own bounds. */
    private const val LOWEST = 3
    private const val HIGHEST = 18

    /** The six bands, best first. */
    fun responses(): List<EntityResponse> = ContentLoader.entityResponse()

    /**
     * The band a [roll] of 3..18 lands in.
     *
     * Refused outside that range rather than clamped: a caller with a 2 has a
     * bug in its dice, and answering "Full response" would hide it.
     */
    fun responseFor(roll: Int): EntityResponse {
        require(roll in LOWEST..HIGHEST) { "an entity response is rolled on 3d6; got $roll" }
        return responses().firstOrNull { roll >= it.low && roll <= it.high }
            ?: throw IllegalStateException("no entity response band covers $roll")
    }

    /**
     * What [response] adds to the ritual roll.
     *
     * 0 when the book prints no number -- an entity that does not answer does
     * not penalise either. Refused for the rejection row, because there is no
     * roll left to modify.
     */
    fun ritualModifier(response: EntityResponse): Int {
        require(!response.automaticFailure) {
            "the ${response.low}-${response.high} row fails the ritual automatically; " +
                "there is no modifier to apply"
        }
        return response.ritualModifier ?: 0
    }

    /**
     * The SPI the caster pays personally, given what the entity gave.
     *
     * [declared] is what the ritual would have cost out of the caster's own
     * pool -- Section IV.A's complexity ladder, less any HP already sacrificed
     * toward it. It is returned unchanged whenever the entity answers.
     *
     * **Only the no-response row changes the charge, and only because it
     * prints a number.** The book states no cost for the five rows that DO
     * answer, so they keep the declared one. Reading the section's "you borrow
     * power from someone higher" as "the entity pays, so those rows cost 0"
     * would author a price on five rows the book never prices -- the refusal
     * [Languages.ritualModifier] makes about a common tongue's silence, where
     * a row saying nothing is not the same as a row saying zero.
     *
     * **That leaves the best outcome costing the most, and it is recorded
     * rather than smoothed.** A Full response charges the full 8+ of a Heavy
     * ritual while No response charges 2. The book prints both and reconciles
     * neither; straightening it means inventing the five missing prices.
     *
     * The row is identified by its own shape -- answering, yet printing no
     * modifier -- rather than by the band 12-14, so a re-extraction that moved
     * the bands cannot silently point this at the wrong row.
     *
     * Refused for the rejection row, like [ritualModifier]: that ritual fails
     * automatically and is never rolled, so there is no fuel to spend.
     */
    fun fuelFor(response: EntityResponse, declared: Int): Int {
        require(declared >= 0) { "a ritual cannot cost negative SPI; was $declared" }
        require(!response.automaticFailure) {
            "the ${response.low}-${response.high} row fails the ritual automatically; " +
                "there is no fuel to spend"
        }
        return if (drawsOnResidualPower(response)) RESIDUAL_SPI else declared
    }

    /**
     * Whether [response] throws the ritual back on the caster's own power.
     *
     * True only for the row that answers while printing no modifier -- the
     * "No response" band. Exposed because [fuelFor] is not the only thing that
     * has to know: a declared HP sacrifice is fuel too, and the row says the
     * ritual "draws ONLY on caster's residual power", so the substitution
     * replaces the whole declared fuel rather than only its SPI half. **That
     * last step is a reading** -- the book prices the row and says nothing
     * about HP already offered -- and it is pinned by a named test.
     *
     * A caller asking this and then re-deriving the charge itself would be the
     * second copy of one decision that made the ritual breakdown stop
     * reconciling twice already (6ag, 6ai). Ask here, then call [fuelFor].
     */
    fun drawsOnResidualPower(response: EntityResponse): Boolean =
        !response.automaticFailure && response.ritualModifier == null

    /**
     * [roll] as it should be read off the table.
     *
     * Floored at 3: a rolled 3 is already the best row and shifting it to 1
     * would land on no row at all. Not clamped at the top, because the shift
     * only ever moves down.
     */
    fun shiftedRoll(roll: Int, orthodoxInGoodStanding: Boolean): Int {
        require(roll in LOWEST..HIGHEST) { "an entity response is rolled on 3d6; got $roll" }
        if (!orthodoxInGoodStanding) return roll
        return maxOf(LOWEST, roll + ORTHODOX_SHIFT)
    }

    /**
     * Ask the entity, and report what it gave.
     *
     * The shift is applied BEFORE the row is read, because it moves the roll
     * and not the result: reading the row first and adjusting afterwards would
     * answer from the wrong band entirely.
     */
    fun ask(source: RandomSource, orthodoxInGoodStanding: Boolean): EntityAnswer {
        val roll = (1..3).sumOf { source.roll(6) }
        val shifted = shiftedRoll(roll, orthodoxInGoodStanding)
        val response = responseFor(shifted)
        return EntityAnswer(
            roll = roll,
            shifted = shifted,
            response = response,
            ritualModifier = if (response.automaticFailure) 0 else ritualModifier(response),
            failsRitual = response.automaticFailure,
            unanswered = drawsOnResidualPower(response),
        )
    }

    /**
     * The no-response row's 2 SPI, taken out of a fuel declared partly in
     * HP: HP first, 2 for 1 (Section IV.B), and the rest of what was
     * declared is displaced.
     *
     * READING, and a prayer's alone (6hq): a mortal plays SPI 0 and prays in
     * blood, so "2 SPI personal" must be payable the way the fuel was. The
     * Ritual dialog still displaces a declared sacrifice whole and asks for
     * the 2 in SPI.
     */
    fun residualFuel(hpSacrificed: Int): Fuel {
        require(hpSacrificed >= 0) { "HP sacrificed cannot be negative; was $hpSacrificed" }
        val covered = minOf(Math.floorDiv(hpSacrificed, PowerSources.HP_PER_SPI), RESIDUAL_SPI)
        return Fuel(spi = RESIDUAL_SPI - covered, hp = covered * PowerSources.HP_PER_SPI)
    }
}
