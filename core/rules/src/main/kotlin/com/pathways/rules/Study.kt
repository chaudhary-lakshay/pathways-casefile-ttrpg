package com.pathways.rules

import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.StudyCost
import com.pathways.rules.content.StudyPrice
import com.pathways.rules.content.StudyRule

/** The two roads the book prices (Ch. 3, p. 32). */
enum class StudyMethod(val label: String) {
    TUTOR("With a Tutor"),
    SELF_STUDY("Self-Study"),
}

/** What raising a skill one rung by study would cost. */
data class StudyPlan(
    val pointsNeeded: Int,
    val toLevel: Int,
    val hours: Int,
    val amount: Int,
    val unit: String,
    /** The per-point cell this was multiplied from, for quoting the book. */
    val perPoint: StudyPrice,
) {
    /** "24 hrs + 16s" -- the shape the book prints, at this plan's scale. */
    val printed: String
        get() = "$hours hrs + " + if (unit == "£") "£$amount" else "$amount$unit"
}

/** Carries the book's own sentence for why study offers nothing here. */
class StudyRefusedException(val reason: String) : IllegalStateException(reason)

/**
 * Planning downtime study (Ch. 3, p. 32).
 *
 * **Stateless on purpose, and that is the whole reason this exists.** The book
 * earns study points over "downtime periods" and this app holds no clock --
 * the reason Appendix A's SPI recovery (6ad) and a technique's duration (6ah)
 * are both unmodelled. What makes THIS page buildable anyway is one sentence:
 * a session earns *"1 character point locked to that skill"*. Study points ARE
 * character points, so [SkillLadder] already prices the rung and this table
 * only prices the point. The GM says when the time passed; the planner says
 * what the time buys.
 *
 * **A plan is advice and must never be spent.** Nothing here writes through
 * `SavedEdits`, because that path bills the 70-point creation budget through
 * [PointBudget] and study points are earned OUTSIDE it. A planner that quietly
 * bought the rung would charge a character points for time they spent.
 *
 * Three of the page's rules reach this object and three deliberately do not:
 *  - **Applied**: the Attr+1 cap, "Not possible" for Very Hard self-study, and
 *    potion-granted skills being unstudiable.
 *  - **Shown**: the tutor's own Attr+2 requirement and 3-student limit, and
 *    "no more than two skills may be studied per downtime period" -- this app
 *    models no tutor and no downtime period, so enforcing them would be
 *    inventing state. The call the Sealed Artifact grade table needed (6af).
 */
object Study {

    /** *"A skill cannot exceed Attribute+1 through study alone."* */
    const val CAP_ABOVE_ATTRIBUTE = 1

    fun costs(): List<StudyCost> = ContentLoader.studyCosts()

    fun rules(): List<StudyRule> = ContentLoader.studyRules()

    /** The highest level study alone reaches on an attribute of [attributeLevel]. */
    fun capFor(attributeLevel: Int): Int = attributeLevel + CAP_ABOVE_ATTRIBUTE

    /** The row for [difficulty]; refused rather than defaulted to the cheapest. */
    fun rowFor(difficulty: Difficulty): StudyCost =
        costs().firstOrNull { it.difficulty.equals(labelOf(difficulty), ignoreCase = true) }
            ?: error("the study table prints no row for ${labelOf(difficulty)}")

    /** The printed cell for [difficulty] by [method], priced or not. */
    fun cellFor(difficulty: Difficulty, method: StudyMethod): StudyPrice =
        rowFor(difficulty).let { if (method == StudyMethod.TUTOR) it.tutor else it.selfStudy }

    /** Whether the book prices this road at all. */
    fun isPriced(difficulty: Difficulty, method: StudyMethod): Boolean =
        cellFor(difficulty, method).hours != null

    /**
     * What ONE study point costs, refused where the book refuses it.
     *
     * Very Hard self-study prints "Not possible", which is not a price of
     * zero: read as free it would make the hardest road the cheapest in the
     * table. Refused rather than answered, the call [Languages.ritualModifier]
     * makes about a tongue the book forbids rituals in (6x).
     */
    fun costPerPoint(difficulty: Difficulty, method: StudyMethod): StudyPrice {
        val cell = cellFor(difficulty, method)
        require(cell.hours != null) {
            "the book prints '${cell.printed}' for ${labelOf(difficulty)} by " +
                "${method.label}; there is no cost to quote"
        }
        return cell
    }

    /**
     * The next rung up by study, or the book's own sentence refusing it.
     *
     * [points] is what is already spent on the skill -- 0 for a skill the
     * character does not have, which is where the book's own worked example
     * starts: Tomas owns no Guns at all and studies up from its default.
     *
     * [isPotionGranted] refuses the whole plan: *"Beyonder potion-granted
     * skills (Chapters 9-11) cannot be studied."* The caller knows which
     * skills a potion gave, because [PotionDigestion] computed them.
     *
     * [attr] refuses it for a second, independent reason, and this object is
     * where the two meet. Study earns *"1 character point locked to that
     * skill"* -- the sentence the whole planner rests on -- and ch. 9 p. 85
     * says SPI-based skills *"cannot be raised with character points"* (6aq).
     * A currency this book forbids spending on a skill is still forbidden when
     * it is earned over downtime, so the same [SkillLadder.isBuyableWithPoints]
     * gate that withholds them from the builder withholds them here.
     *
     * It is deliberately NOT left to [isPotionGranted] to cover, even though
     * every SPI skill in play today arrived by potion and would be refused
     * anyway: that overlap is a fact about the premades, not a rule, and a
     * caller passing the flag wrongly would silently buy back a prohibition.
     * Enforced in rules, where it is tested.
     */
    fun plan(
        points: Int,
        difficulty: Difficulty,
        attributeLevel: Int,
        method: StudyMethod,
        attr: ControllingAttribute,
        isPotionGranted: Boolean = false,
    ): Result<StudyPlan> {
        require(points >= 0) { "points spent cannot be negative; was $points" }
        if (!SkillLadder.isBuyableWithPoints(attr)) {
            return refuse(
                "Study earns character points, and this book does not let a " +
                    "${attr.code}-based skill be raised with them.",
            )
        }
        if (isPotionGranted) {
            return refuse("A potion granted this skill, and the book does not let it be studied.")
        }
        val cell = cellFor(difficulty, method)
        if (cell.hours == null) {
            return refuse(
                "The book prints \"${cell.printed}\" for a ${labelOf(difficulty)} " +
                    "skill by ${method.label.lowercase()}.",
            )
        }
        val cap = capFor(attributeLevel)
        // The cap is read against the level the character HAS. "Through study
        // alone" leaves a skill already bought past Attr+1 unsettled, so the
        // smaller reading is taken -- study offers such a skill nothing --
        // rather than granting rungs that sentence never promised.
        val current = if (points == 0) null
        else SkillLadder.levelFor(points, difficulty, attributeLevel)
        if (current != null && current >= cap) {
            return refuse("Study alone cannot pass $cap, and this skill is already $current.")
        }
        val nextPoints = SkillLadder.nextRungPoints(points)
            ?: return refuse("This skill is past the last rung the book's grid prints.")
        val toLevel = SkillLadder.levelFor(nextPoints, difficulty, attributeLevel)
        if (toLevel > cap) {
            return refuse("The next rung is $toLevel, and study alone cannot pass $cap.")
        }
        val needed = nextPoints - points
        return Result.success(
            StudyPlan(
                pointsNeeded = needed,
                toLevel = toLevel,
                // Linear per point, which is the book's own arithmetic: a
                // printed 12 hrs + 8s buys 1 CP, and Tomas pays "another 24
                // hours (16s) for 2 more CP".
                hours = cell.hours * needed,
                amount = (cell.amount ?: 0) * needed,
                unit = cell.unit ?: "s",
                perPoint = cell,
            ),
        )
    }

    private fun refuse(reason: String): Result<StudyPlan> =
        Result.failure(StudyRefusedException(reason))

    /** The book's own spelling of a difficulty, which is this table's key. */
    private fun labelOf(difficulty: Difficulty): String = when (difficulty) {
        Difficulty.EASY -> "Easy"
        Difficulty.AVERAGE -> "Average"
        Difficulty.HARD -> "Hard"
        Difficulty.VERY_HARD -> "Very Hard"
    }
}
