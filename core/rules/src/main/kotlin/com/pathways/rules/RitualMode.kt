package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.RitualPreparation
import com.pathways.rules.content.TargetLink

/**
 * The two ways Chapter 9 resolves a ritual (v6.9i, III.A-B).
 *
 * Quick Resolution is "for routine work", Full Resolution's three phases "for
 * consequential magic". Each prints its own preparation table and treats the
 * target link differently, so the mode decides which numbers reach the roll.
 *
 * **The two preparation tables disagree, and are not reconciled.** Excellent
 * is **+2** in Quick mode and **+3** in Full. Both tables are carried; neither
 * is repaired. They agree that Adequate is +0.
 *
 * **The target link is counted once in each mode, in different places.** The
 * Quick line prints Target Link as its own +4 to -4 beside Preparation
 * Quality. Full mode's Phase I declares "target link" among the things the GM
 * folds into one Preparation Modifier, so a Full ritual that also added the
 * link would pay for it twice. [modifierTotal] refuses it.
 *
 * **And the Flourish Bonus is FULL's alone.** Phase II: "GM may award +1
 * Flourish Bonus for good roleplay. Effective skill = Ritualistic Magic +
 * Preparation Mod + Flourish Bonus." The Quick line prints no flourish. So
 * each mode has one modifier the other does not, and [modifierTotal] refuses
 * the wrong one either way. That formula is evidence the bonus reaches the
 * roll, not a complete list of the target's terms.
 *
 * v6.9i deleted Section II's supporting skills, the canon procedure and its
 * timing penalty, so none of them is here.
 */
enum class RitualMode(
    /** Whether this mode adds the Target Link table as a separate modifier. */
    val usesTargetLink: Boolean,
    /** Whether this mode's procedure prints a Flourish Bonus to award. */
    val usesFlourish: Boolean,
) {
    /** Four steps, routine work. Preparation caps at +2 and the link is its own. */
    QUICK(usesTargetLink = true, usesFlourish = false),

    /** Three phases, consequential magic. Preparation reaches +3 and includes the link. */
    FULL(usesTargetLink = false, usesFlourish = true),
    ;

    /** This mode's own preparation table, best first. */
    fun preparations(): List<RitualPreparation> = when (this) {
        QUICK -> ContentLoader.ritualQuickPreparation()
        FULL -> ContentLoader.ritualPreparation()
    }

    /**
     * The row called [quality] in THIS mode's table.
     *
     * Refused rather than borrowed from the other mode: "Good" is a Full-mode
     * row and answering +1 for a Quick ritual would invent a step its table
     * does not print.
     */
    fun preparationNamed(quality: String): RitualPreparation =
        preparations().firstOrNull { it.quality.equals(quality, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "no '$quality' row in the $name ritual's preparation table; it prints " +
                    preparations().joinToString { it.quality },
            )

    /**
     * What preparation, link and flourish together do to the roll in this mode.
     *
     * A [link] given to [FULL] is refused, not ignored: its preparation row
     * already priced one, and a caller passing both has misread which table it
     * is in. Silently dropping it would leave a GM believing a perfect link
     * was counted.
     *
     * A [flourish] given to [QUICK] is refused for the mirror reason: that
     * mode sums exactly the two modifiers its Step 3 names, and awarding a
     * bonus it never prints would invent a step. Refused rather than dropped,
     * so a GM who described a performance is not left believing it counted.
     *
     * [preparationValue] and [linkValue] are the GM's picks inside a cell
     * that prints more than one number; see [valueFor]. Null is "not picked",
     * which takes the cell's printed near end.
     */
    fun modifierTotal(
        preparation: RitualPreparation?,
        link: TargetLink? = null,
        flourish: Boolean = false,
        preparationValue: Int? = null,
        linkValue: Int? = null,
    ): Int {
        require(link == null || usesTargetLink) {
            "a $name ritual's preparation modifier already includes the target " +
                "link (its rows name one); adding '${link?.link}' would pay it twice"
        }
        require(!flourish || usesFlourish) {
            "a $name ritual prints no Flourish Bonus; its Step 3 sums exactly " +
                "the two modifiers it names, and only Phase II of a full " +
                "ritual awards +$FLOURISH_BONUS for delivery"
        }
        // A row borrowed from the OTHER mode's table is refused, and it is the
        // sharper of the two guards. The whole of 6y was that Excellent is +2
        // in a Quick ritual and +3 in a Full one, and that the app applied +3
        // to both -- so a cross-mode row reintroduces exactly that defect
        // through the API, with a number that looks ordinary on screen. The
        // dialog cannot reach it (`preparation` is a `remember(mode)`, cleared
        // on every switch), which is why the guard lives here rather than
        // there: nothing can reach it yet, and that is what stops the next
        // caller finding it (6l).
        //
        // Containment is by value: v6.9i's Adequate is "+0" in both tables
        // with no other cell, so either table's row is the same price.
        require(preparation == null || preparation in preparations()) {
            "'${preparation?.quality}' at ${preparation?.printed} is not a row in " +
                "the $name ritual's preparation table, which prints " +
                preparations().joinToString { "${it.quality} ${it.printed}" }
        }
        return valueFor(preparation, preparationValue) + valueFor(link, linkValue) +
            (if (flourish) FLOURISH_BONUS else 0)
    }

    companion object {
        /**
         * "The GM may award a Flourish Bonus (+1) for good in-character
         * delivery" (p. 63, Phase II).
         *
         * One value, so it is a flag rather than a stepper -- unlike the
         * ranges the darkness row and the preparation cells print, where the
         * GM picks inside a printed span. Awarded, never earned by a roll:
         * the book gives it to the GM and this app does not judge acting.
         */
        const val FLOURISH_BONUS = 1

        /**
         * What [preparation] is worth: the figure its cell prints, or the
         * GM's [chosen] one where the cell prints more than a number.
         *
         * "The GM determines the Preparation Modifier from the player's
         * declarations" (p. 63), and some of the cells they determine it
         * from are not a number: "+1 to +2", "-1 to -2" and "-4 or worse" in
         * a Full ritual, "-2 to -4" in a Quick one.
         *
         * **Unpicked is the near end, and that is this app's default rather
         * than the book's rule.** Until 6ef it was the only value there was:
         * the chip read "Good +1 to +2" and the roll took +1, on a warrant
         * ("the gentle reading the darkness row settled") that 6dz had
         * already withdrawn from the darkness row itself.
         *
         * A [chosen] value is held to its OWN cell. +3 is Excellent's and is
         * refused for Good, because a pick that could borrow a neighbour's
         * number would make the row's name mean nothing. Same contract as
         * [CommonModifiers.valueFor], which is the precedent.
         */
        fun valueFor(preparation: RitualPreparation?, chosen: Int? = null): Int {
            if (preparation == null) {
                require(chosen == null) {
                    "$chosen was picked with no preparation row to pick it inside"
                }
                return 0
            }
            return pickedWithin(
                "'${preparation.quality}' preparation", preparation.printed,
                preparation.modifier, preparation.best, preparation.openEnded, chosen,
            )
        }

        /**
         * The same for the Quick Ritual's [link]: "+2 to +3" and "+0 to -1".
         * Ritual 4's "True name known +3" is Strong's far end.
         */
        fun valueFor(link: TargetLink?, chosen: Int? = null): Int {
            if (link == null) {
                require(chosen == null) {
                    "$chosen was picked with no target link to pick it inside"
                }
                return 0
            }
            return pickedWithin(
                "a '${link.link}' link", link.printed, link.modifier, link.best,
                openEnded = false, chosen,
            )
        }

        private fun pickedWithin(
            what: String,
            printed: String,
            near: Int,
            far: Int?,
            openEnded: Boolean,
            chosen: Int?,
        ): Int {
            if (chosen == null) return near
            when {
                // minOf/maxOf, never `near..far`: "+0 to -1" runs downward and
                // Kotlin's `0..-1` is empty, which would refuse every pick.
                far != null -> require(chosen in minOf(near, far)..maxOf(near, far)) {
                    "$what prints $printed; $chosen is outside it"
                }
                openEnded -> require(if (near < 0) chosen <= near else chosen >= near) {
                    "$what prints $printed; $chosen is on the wrong side of it"
                }
                else -> require(chosen == near) {
                    "$what prints exactly $printed, not $chosen"
                }
            }
            return chosen
        }

        /** The Quick Ritual's four link rows, best first. */
        fun links(): List<TargetLink> = ContentLoader.ritualTargetLink()

        /** The link row called [link]; refused rather than defaulted. */
        fun linkNamed(link: String): TargetLink =
            links().firstOrNull { it.link.equals(link, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "no target link called '$link'; the book prints " +
                        links().joinToString { it.link },
                )
    }
}
