package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.RitualPreparation
import com.pathways.rules.content.RitualSupportSkill
import com.pathways.rules.content.TargetLink

/**
 * The two ways Chapter 7 resolves a ritual (p. 61-63).
 *
 * "Quick Ritual is for routine work ... Full Ritual is for consequential
 * magic ... Both use the same underlying canon procedure -- they just expose
 * different levels of granularity." The granularity is not cosmetic: each
 * mode prints its own preparation table and treats the target link
 * differently, so the mode decides which numbers reach the roll.
 *
 * **The two preparation tables disagree, and are not reconciled.** Excellent
 * is **+2** in Quick mode and **+3** in Full. `ritual_preparation.json` is the
 * Full one, extracted in M4's ritual slice, and the ritual dialog applied it
 * to every ritual -- so a quick ritual prepared excellently rolled a point
 * higher than its own table allows. Both tables are carried; neither is
 * repaired. They agree that Adequate is +0, which is what makes the one
 * disagreement a real difference rather than two unrelated ladders.
 *
 * **The target link is counted once in each mode, in different places.** Quick
 * mode says "Sum the following two modifiers: 1. Preparation Quality
 * 2. Target Link", so the link is its own +4 to -4. Full mode lists "Link to
 * target: None / Weak / Strong / Perfect" among the declarations that "feed
 * into a single Preparation Modifier", and its rows say so themselves --
 * Excellent is "perfect link", Good "strong link", Adequate "basic link",
 * Poor "weak link", None "no link". So a Full ritual that also added the link
 * would pay a perfect one twice, +8 on a 3d6 roll. [modifierTotal] refuses it
 * rather than quietly summing.
 *
 * **And the Flourish Bonus is the mirror image of the link: FULL's alone.**
 * Phase II says "The GM may award a Flourish Bonus (+1) for good in-character
 * delivery" and then prints the book's own formula for what it lands on --
 * "Effective skill = Ritualistic Magic + Preparation Mod + Flourish Bonus".
 * The Quick Ritual's Step 3 instead says "Sum the following two modifiers"
 * and names Preparation Quality and Target Link; "Flourish" appears exactly
 * twice in this book, both inside Phase II. So each mode has one modifier the
 * other does not, and [modifierTotal] refuses the wrong one either way.
 *
 * That formula is read as EVIDENCE that the bonus reaches the roll, not as a
 * complete list of the target's terms: weight, corruption, supporting skills
 * and language all reach the same number and it names none of them. It is
 * Phase II's summary of Phase II.
 *
 * **Section C's timing penalty is deliberately not applied.** The canon
 * procedure prints "Wrong timing for the entity gives -1 to -2", and Phase I
 * already declares "Timing: Does it match the entity's domain?" among the
 * choices feeding the single Preparation Modifier -- Excellent's own cell
 * names "celestial timing". Charging both prices one mistimed ritual twice,
 * the scoping that keeps Appendix A's adverse conditions off a shot already
 * priced by chapter 5's darkness row. The book also labels Section C "a
 * reference ... when you want the full texture". Both facts are pinned by
 * `extract_ritual.py`, because "it is reference prose" is a reason that has to
 * survive the page printing a number.
 *
 * **The supporting skills are not scoped to a mode.** Section II sits above
 * the split and says nothing about either, so both modes take it -- unlike the
 * link, which each mode handles its own way. The +3 cap lives here rather than
 * in the JSON because it is a number inside a sentence, the same split the
 * maneuver table's +4/-4/-6/-2 take; `extract_ritual_modes.py` pins the
 * sentence it was read from.
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

    /** The cap on supporting skills, which both modes share. */
    val supportCap: Int get() = SUPPORT_CAP

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
        // Containment is an exact test because the two tables share no row --
        // even Adequate, +0 in both, prints a different `looksLike` cell.
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
         * "These bonuses are cumulative but cap at +3 total from supporting
         * skills." Five skills grant five points, so the cap is the whole
         * reason the table is not just addition.
         */
        const val SUPPORT_CAP = 3

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
         * a Full ritual, "-2 to -4" in a Quick one. Section VIII uses them --
         * rituals 3 and 7 print "Long-used sanctified space +2", which is
         * Good's far end.
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

        /** Section II's five supporting skills, in printed order. */
        fun supportSkills(): List<RitualSupportSkill> = ContentLoader.ritualSupport()

        /** The Quick Ritual's four link rows, best first. */
        fun links(): List<TargetLink> = ContentLoader.ritualTargetLink()

        /** The link row called [link]; refused rather than defaulted. */
        fun linkNamed(link: String): TargetLink =
            links().firstOrNull { it.link.equals(link, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "no target link called '$link'; the book prints " +
                        links().joinToString { it.link },
                )

        /**
         * What [skills] add to the roll, capped.
         *
         * A name the table does not print is REFUSED rather than skipped: a
         * caller passing "Occultsm" would otherwise lose a point in silence.
         * Naming one twice pays once -- a character has one Occultism.
         */
        /**
         * A supporting skill's name with the book's instruction stripped.
         *
         * One of the five rows is printed "Hidden Lore (relevant domain)".
         * The bracket is the book telling the caster to pick a domain -- the
         * same shape as "Duty (specify)" in the disadvantage list -- not part
         * of a skill's name, and no character sheet spells it that way. Keyed
         * on the printed string, that row was unreachable and its +1 was lost
         * in silence for anyone carrying Hidden Lore, which four premades do.
         *
         * Only the row's own bracket is stripped, so a SPECIALIZED skill
         * still matches on its bare name -- which is what the book asks for,
         * since it is the domain's relevance and not its spelling that earns
         * the point, and relevance is the GM's call.
         */
        fun bareSupportName(skill: String): String =
            skill.substringBefore(" (").trim().lowercase()

        fun supportBonus(skills: List<String>): Int {
            val printed = supportSkills().associateBy { bareSupportName(it.skill) }
            val bonus = skills.distinctBy { bareSupportName(it) }.sumOf { name ->
                val row = printed[bareSupportName(name)]
                    ?: throw IllegalArgumentException(
                        "'$name' is not a supporting skill for rituals; the book prints " +
                            supportSkills().joinToString { it.skill },
                    )
                row.bonus
            }
            return minOf(bonus, SUPPORT_CAP)
        }

        /**
         * Which of the book's five supporting skills [owned] covers.
         *
         * Lives here rather than in the screen so the matching is decided
         * once: a caller that filtered with its own comparison could offer a
         * chip [supportBonus] then refuses, or withhold one it would accept.
         * Returns the BOOK's spelling, because that is what the chip should
         * read and what [supportBonus] is documented against.
         */
        fun supportSkillsOwnedBy(owned: List<String>): List<String> {
            val bare = owned.map { bareSupportName(it) }.toSet()
            return supportSkills().map { it.skill }.filter { bareSupportName(it) in bare }
        }
    }
}
