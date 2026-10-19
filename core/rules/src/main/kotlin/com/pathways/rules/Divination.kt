package com.pathways.rules

import com.pathways.model.Outcome
import com.pathways.model.RollResult
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.DivinationClarity
import com.pathways.rules.content.DivinationDifficulty
import com.pathways.rules.content.DivinationMethod
import com.pathways.rules.content.DivinationModifier

/**
 * Asking the world a question (Ch. 6.5, Sections I-III).
 *
 * The book's four steps, in order: state the question and take its difficulty
 * modifier, choose a method and pay its SPI, roll Divination Arts with every
 * applicable modifier, and read the clarity off the margin.
 *
 * The same shape as [RitualResolution], and it borrows that object's hardest-
 * won reading: the clarity table has TWO DOORS. Five of its rows are named by
 * margin ("Success by 3-4") and the sixth by OUTCOME ("Critical Failure"), and
 * a margin-first read reports the book's own critical row as an ordinary
 * failure. The outcome is checked first for exactly that reason.
 *
 * A critical failure here is not given effects of its own: the row says "treat
 * as a ritual critical failure", so [RitualResolution.criticalFor] is the
 * second roll and this object carries no duplicate table.
 */
object Divination {

    /** The skill this resolves (IQ/Hard, ch. 8 I). */
    const val SKILL = "Divination Arts"


    /**
     * "A Beyonder may specialise in one method, gaining +1 when using it."
     *
     * Read, and deliberately not applied: the reason is the value in
     * [UNAPPLIED], where a sweep over Kotlin finds it. Kept as a constant so
     * the number is extracted once if the app ever gains a place to record
     * which method a diviner trained in.
     */
    const val SPECIALISATION_BONUS = 1

    /**
     * Printed divination rules this app does not apply, each beside its reason.
     *
     * The standard is [ActiveDefenses.UNPAYABLE]'s: a printed rule that reaches
     * nothing says so with its reason, in production source rather than in a
     * journal, so the omission stays a decision instead of becoming an
     * accident. **That buys discoverability in the RULES SOURCE and nothing
     * more** -- removing an entry here turns no test red, adding one does not
     * either, and the screen that renders this map could be deleted with the
     * suite still green, because nothing in this repo tests a composable. The
     * scope is stated rather than assumed: a review's finding, and the
     * alternative was an app-side assertion that cannot reach the render.
     *
     * The value is shown to the PLAYER, so it is one sentence rather than the
     * argument behind it. That argument: the pricing sentence puts it under
     * GURPS Techniques, which this book does not reprint and the builder
     * cannot buy, and nothing in this repo -- no sheet, no potion grant, no
     * saved edit -- records which method a Beyonder trained in. Every other
     * bonus on that screen is gated on something the character demonstrably
     * owns; a chip here would be gated on nothing.
     */
    val UNAPPLIED = mapOf(
        "Specialisation" to
            "the book prices it as a technique (GURPS p. B229), which this book " +
            "does not reprint, and nothing here records which method a diviner " +
            "trained in",
    )

    /** "1 SPI = +1, no cap", spent after seeing the roll (p. 55). */
    const val BURN_PER_SPI = 1

    /**
     * "Default: Divination Arts defaults to SPI-4 (unskilled)." (ch. 8 I)
     *
     * A SPECIFIC default, and it replaces the IQ/Hard table's IQ-6 the way
     * Brawling's own line replaces the table's. Ritualistic Magic lends it
     * nothing: "The two skills do not overlap -- a Beyonder cannot substitute
     * one for the other."
     */
    const val DEFAULT_PENALTY = -4

    /**
     * A level to roll Divination Arts at, and whether it is the default.
     *
     * [isDefault] is what the screen says out loud, so a caster is never
     * shown a number they do not own without being told where it came from.
     */
    data class SkillOrDefault(
        val level: Int,
        val isDefault: Boolean,
        /**
         * What the caster's OWN skill stands at, or null if they have none.
         *
         * Carried because [isDefault] alone cannot tell the two defaulting
         * cases apart: a caster with no Divination Arts at all, and one whose
         * skill is worse than their SPI-4.
         */
        val own: Int? = null,
        /**
         * What a trait added to the DEFAULT, already inside [level]; zero
         * when [isDefault] is false. Carried so [sourceLine] can print the
         * SPI behind the number.
         */
        val defaultBonus: Int = 0,
    )

    /**
     * One sentence saying where [chosen]'s number came from, or null when it
     * is simply the caster's own skill.
     *
     * Authored HERE rather than in the dialog, the call [Detection] already
     * had to make for its attribute line (6ai): a screen writing its own
     * version is a second copy of a decision.
     */
    fun sourceLine(chosen: SkillOrDefault): String? {
        if (!chosen.isDefault) return null
        val spi = chosen.level - DEFAULT_PENALTY - chosen.defaultBonus
        val lift = if (chosen.defaultBonus == 0) {
            ""
        } else {
            ", +${chosen.defaultBonus} for ${TraitEffects.VERSATILE}"
        }
        val base = "Defaulted from SPI $spi$DEFAULT_PENALTY$lift. " +
            "\"Divination Arts defaults to SPI-4 (unskilled).\""
        return if (chosen.own == null) {
            "$base This character has no $SKILL of their own."
        } else {
            "$base Better than their own $SKILL ${chosen.own}."
        }
    }

    /**
     * The level a caster rolls DIVINATION at, given what they [own] of
     * Divination Arts and their [spi].
     *
     * Never null: SPI-4 is open to anyone, and a mortal at SPI 0 asks at -4.
     *
     * The better of the owned skill and the default wins -- GURPS's best of
     * skill or default -- and a tie keeps the owned skill, because the
     * default adds nothing there and a caster who has the skill should be
     * shown as having it.
     *
     * [defaultBonus] is [TraitEffects.defaultBonus]: Versatile's "any time you
     * use a skill at default, you are effectively one level better". It is
     * added BEFORE the comparison, because own 8 against SPI 12 is a tie
     * without it and a default of 9 with it.
     */
    fun divinationLevelFor(own: Int?, spi: Int, defaultBonus: Int): SkillOrDefault {
        val defaulted = spi + DEFAULT_PENALTY + defaultBonus
        return if (own != null && defaulted <= own) {
            SkillOrDefault(own, isDefault = false, own = own)
        } else {
            SkillOrDefault(defaulted, isDefault = true, own = own, defaultBonus = defaultBonus)
        }
    }

    fun difficulties(): List<DivinationDifficulty> = ContentLoader.divinationDifficulty()
    fun methods(): List<DivinationMethod> = ContentLoader.divinationMethods()
    fun modifiers(): List<DivinationModifier> = ContentLoader.divinationModifiers()
    fun clarities(): List<DivinationClarity> = ContentLoader.divinationClarity()

    /** The method named [name]; refused rather than defaulted to the cheapest. */
    fun methodNamed(name: String): DivinationMethod =
        methods().firstOrNull { it.method.equals(name, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "no divination method named '$name'; the book prints " +
                    methods().joinToString { it.method },
            )

    /**
     * The chosen [rows] as one modifier, with [gmValue] overriding any row
     * that prints a range.
     *
     * A row that carries no number is refused, not applied as zero. There is
     * one -- "Question difficulty / See Step 1 table" -- and it is not a
     * modifier at all: it points back at Step 1, which [targetFor] applies
     * separately. Adding it here would either double the difficulty or, read
     * as zero, silently drop it.
     *
     * [repeats] multiplies the one row that says "per repeat".
     */
    fun modifierTotal(
        rows: List<DivinationModifier>,
        gmValue: Map<String, Int> = emptyMap(),
        repeats: Int = 1,
    ): Int {
        require(repeats >= 1) { "a repeat count starts at 1; was $repeats" }
        return rows.sumOf { row ->
            require(!row.crossReference) {
                "'${row.source}' is Step 1's own table, not a modifier to add beside it"
            }
            val value = gmValue[row.source] ?: row.modifier
                ?: error("'${row.source}' prints no modifier and none was given")
            if (row.best != null) {
                val ends = listOf(row.modifier!!, row.best).sorted()
                require(value in ends[0]..ends[1]) {
                    "'${row.source}' prints ${row.printed}; $value is outside it"
                }
            }
            if (row.perRepeat) value * repeats else value
        }
    }

    /** The number to roll under: skill, the question's difficulty, and [extras]. */
    fun targetFor(skill: Int, difficulty: DivinationDifficulty, extras: Int = 0): Int =
        skill + difficulty.modifier + extras

    /**
     * The clarity row a [roll] lands on.
     *
     * Outcome BEFORE margin. A 17 fails a target of 18 with a positive margin,
     * so reading the margin first would call the book's critical-failure row a
     * "Success by 0-2" -- the slip [FrightCheck] and [RitualResolution] each
     * made once, and the reason this object was written after them.
     *
     * A critical SUCCESS is read by its margin like any other success. The
     * book prints a critical-failure row and no critical-success row, and that
     * omission is deliberate enough to follow: inventing "Crystal clear" for a
     * lucky 4 against a target of 5 would be inventing content.
     */
    fun clarityFor(roll: RollResult, demoted: Boolean = false): DivinationClarity {
        val rows = clarities()
        if (roll.outcome == Outcome.CRITICAL_FAILURE) return rows.first { it.isCritical }
        // A success the target's Sequence Immunity forbids is a failure, and
        // must not keep a success row: "Clear" printed beside the word Failure
        // is the defect [SkillRollOutcome.degree] avoids by reading the
        // effective outcome. Its margin is positive, so it lands on the mildest
        // failure row -- the call FrightCheck already makes for a roll that
        // overshoots its table by nothing.
        val succeeded = !demoted &&
            (roll.outcome == Outcome.SUCCESS || roll.outcome == Outcome.CRITICAL_SUCCESS)
        val side = rows.filter { !it.isCritical && it.isFailure != succeeded }
        val by = if (succeeded) roll.margin else -roll.margin
        // Bands overlap -- a margin of 5 satisfies "0-2", "3-4" and "5+" at
        // once -- so the DEEPEST match wins, as DegreeOfSuccess reads its own
        // ladder. Taking the first would report every success as "Vague".
        return side.filter { (it.margin ?: 0) <= by }.maxByOrNull { it.margin ?: 0 }
        // A 17 fails a target it beat on the dice, so `by` can be negative and
        // match no band at all. The mildest row is the answer there -- the
        // call FrightCheck makes for a roll that overshoots its table by
        // nothing -- and crashing mid-scene is not.
            ?: side.minByOrNull { it.margin ?: 0 }
            ?: error("the clarity table prints no ${if (succeeded) "success" else "failure"} row")
    }

    /**
     * One divination: roll against [skill] for [difficulty], plus [extras].
     *
     * [burned] is SPI spent after seeing the roll to push it -- "1 SPI = +1,
     * no cap" -- and is added to the TARGET rather than subtracted from the
     * total, because the book raises the number you roll under.
     */
    fun attempt(
        skill: Int,
        difficulty: DivinationDifficulty,
        source: RandomSource,
        extras: Int = 0,
        requiresCritical: Boolean = false,
    ): DivinationOutcome = rollAgainst(targetFor(skill, difficulty, extras), source, requiresCritical)

    /**
     * The roll itself, against a [target] already composed by [targetFor] --
     * what a screen that printed the number calls (6eg, 6eh).
     *
     * [requiresCritical] rides with the roll and not with the inputs that
     * composed the number: Sequence Immunity changes how the dice are READ,
     * never what they are rolled against.
     */
    fun rollAgainst(
        target: Int,
        source: RandomSource,
        requiresCritical: Boolean = false,
    ): DivinationOutcome = read(SuccessRoll.against(target, source), 0, requiresCritical)

    /**
     * Whether a burn can still move [outcome] (p. 55).
     *
     * False under Sequence Immunity's critical-only rule, and for the same
     * arithmetic reason [EffortBurn.canPush] gives: a push raises the TARGET
     * and never the total, so no amount of it reaches a rolled 3 or 4. True
     * everywhere else, a critical success included -- the clarity table has
     * no critical row and grades one by margin like any other roll, so a
     * point still buys a clearer answer -- until the answer is the clearest
     * the table prints, where a point buys nothing (play session 3).
     */
    fun canPush(outcome: DivinationOutcome): Boolean =
        !outcome.requiresCritical && outcome.clarity != clearest()

    /** The success row with the deepest margin: nothing is clearer. */
    private fun clearest(): DivinationClarity =
        clarities().filter { !it.isFailure && !it.isCritical }.maxBy { it.margin ?: 0 }

    /**
     * The same dice read against a target [points] SPI higher (p. 55).
     *
     * "Burning SPI to improve the roll: As with rituals, you may spend
     * additional SPI (1 SPI = +1, no cap) after seeing your result." After is
     * the load-bearing word, and it is why this is a second step rather than
     * an argument to [attempt]: the dice are already cast, so the same total
     * is re-read against a higher number. [spiFor] takes the same points as
     * COST; this takes them as the bonus.
     *
     * [points] is what is spent on THIS call, and [DivinationOutcome.spiBurned]
     * accumulates -- a caller pushing twice has spent both.
     *
     * Returns [outcome] unchanged when [canPush] is false, so a caller that
     * forgets to ask cannot spend SPI on a roll the burn cannot move. SPI's
     * Soul Debt is permanent (6m), which is why that is refused here rather
     * than left to the screen.
     */
    fun pushed(outcome: DivinationOutcome, points: Int): DivinationOutcome {
        require(points >= 0) { "cannot burn a negative number of SPI; was $points" }
        if (points == 0 || !canPush(outcome)) return outcome
        val target = Math.addExact(outcome.roll.target, points * BURN_PER_SPI)
        return read(
            SuccessRoll.evaluate(outcome.roll.total, target),
            Math.addExact(outcome.spiBurned, points),
            outcome.requiresCritical,
        )
    }

    /**
     * What a resolved [roll] means, in one place.
     *
     * [attempt] and [pushed] read the same clarity table off the same roll and
     * must not answer that twice -- the demotion in particular, which is easy
     * to apply on one path and forget on the other.
     */
    private fun read(
        roll: RollResult,
        burned: Int,
        requiresCritical: Boolean,
    ): DivinationOutcome {
        // Sec. 5's "only a critical success (3-4) can succeed" against a Seq 4
        // or Seq 3 target. Not expressible as a penalty, which is the whole
        // point of it -- see [AntiDivination].
        val demoted = requiresCritical && roll.outcome == Outcome.SUCCESS
        return DivinationOutcome(roll, clarityFor(roll, demoted), burned, requiresCritical)
    }

    /**
     * What [method] costs when the GM picks [chosen] inside its printed range,
     * plus anything [burned] to push the roll.
     *
     * Dowsing Rods prints "1-2", and defaulting to the low end while the
     * screen displayed the range charged 1 SPI for a 2 SPI question (Codex).
     * Undercharging matters more than it looks: SPI below zero is Soul Debt,
     * and that corruption is permanent.
     */
    fun spiFor(method: DivinationMethod, chosen: Int = method.spi, burned: Int = 0): Int {
        val most = method.spiMax ?: method.spi
        require(chosen in method.spi..most) {
            "${method.method} costs ${method.spi}..$most SPI; $chosen is outside that"
        }
        require(burned >= 0) { "SPI burned cannot be negative; was $burned" }
        return chosen + burned
    }
}

/**
 * One divination's answer.
 *
 * [clarity] is the row the GM reads out; on a critical failure that row sends
 * them to the ritual Critical Failure Table rather than printing effects of
 * its own, so [isCritical] is what a caller checks before rolling
 * [RitualResolution.criticalFor].
 */
data class DivinationOutcome(
    val roll: RollResult,
    val clarity: DivinationClarity,
    val spiBurned: Int,
    val requiresCritical: Boolean = false,
) {
    val isCritical: Boolean get() = clarity.isCritical

    /**
     * The outcome after Sequence Immunity's critical-only rule.
     *
     * Exposed as a property so a caller cannot read [roll]'s outcome and
     * forget the demotion -- the trap [SkillRollOutcome.effectiveOutcome]
     * exists for one object over.
     */
    val effectiveOutcome: Outcome
        get() = if (requiresCritical && roll.outcome == Outcome.SUCCESS) {
            Outcome.FAILURE
        } else {
            roll.outcome
        }
}
