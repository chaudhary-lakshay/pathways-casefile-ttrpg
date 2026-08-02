package com.pathways.rules

import com.pathways.model.Outcome
import com.pathways.model.RollResult
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.DivinationClarity
import com.pathways.rules.content.DivinationDifficulty
import com.pathways.rules.content.DivinationMethod
import com.pathways.rules.content.DivinationModifier
import com.pathways.rules.content.DivinationSupport

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

    /** The skill this resolves (SPI/Hard, p. 53). */
    const val SKILL = "Divination Arts"

    /** "cumulative but cap at +2 total from supporting skills" (p. 54). */
    const val MAX_SUPPORT_BONUS = 2

    /** "A Beyonder may specialise in one method, gaining +1 when using it." */
    const val SPECIALISATION_BONUS = 1

    /** "1 SPI = +1, no cap", spent after seeing the roll (p. 55). */
    const val BURN_PER_SPI = 1

    fun difficulties(): List<DivinationDifficulty> = ContentLoader.divinationDifficulty()
    fun methods(): List<DivinationMethod> = ContentLoader.divinationMethods()
    fun modifiers(): List<DivinationModifier> = ContentLoader.divinationModifiers()
    fun clarities(): List<DivinationClarity> = ContentLoader.divinationClarity()
    fun supportingSkills(): List<DivinationSupport> = ContentLoader.divinationSupport()

    /** The method named [name]; refused rather than defaulted to the cheapest. */
    fun methodNamed(name: String): DivinationMethod =
        methods().firstOrNull { it.method.equals(name, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "no divination method named '$name'; the book prints " +
                    methods().joinToString { it.method },
            )

    /**
     * What the other [skills] the diviner has are worth, capped.
     *
     * The cap is the book's: "These bonuses are cumulative but cap at +2 total
     * from supporting skills." Four skills at +1 each would otherwise be +4,
     * which is two thirds of the hardest question's penalty.
     */
    fun supportBonus(skills: List<DivinationSupport>): Int =
        skills.sumOf { it.bonus }.coerceAtMost(MAX_SUPPORT_BONUS)

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
    fun clarityFor(roll: RollResult): DivinationClarity {
        val rows = clarities()
        if (roll.outcome == Outcome.CRITICAL_FAILURE) return rows.first { it.isCritical }
        val succeeded = roll.outcome == Outcome.SUCCESS || roll.outcome == Outcome.CRITICAL_SUCCESS
        return if (succeeded) {
            when {
                roll.margin >= 5 -> rows[0]
                roll.margin >= 3 -> rows[1]
                else -> rows[2]
            }
        } else {
            if (-roll.margin >= 5) rows[4] else rows[3]
        }
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
        burned: Int = 0,
    ): DivinationOutcome {
        require(burned >= 0) { "SPI burned cannot be negative; was $burned" }
        val target = targetFor(skill, difficulty, extras) + burned * BURN_PER_SPI
        val roll = SuccessRoll.against(target, source)
        return DivinationOutcome(roll, clarityFor(roll), burned)
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
) {
    val isCritical: Boolean get() = clarity.isCritical
}
