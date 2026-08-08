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
) {
    /** Four steps, routine work. Preparation caps at +2 and the link is its own. */
    QUICK(usesTargetLink = true),

    /** Three phases, consequential magic. Preparation reaches +3 and includes the link. */
    FULL(usesTargetLink = false),
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
     * What preparation and link together do to the roll in this mode.
     *
     * A [link] given to [FULL] is refused, not ignored: its preparation row
     * already priced one, and a caller passing both has misread which table it
     * is in. Silently dropping it would leave a GM believing a perfect link
     * was counted.
     */
    fun modifierTotal(preparation: RitualPreparation?, link: TargetLink? = null): Int {
        require(link == null || usesTargetLink) {
            "a $name ritual's preparation modifier already includes the target " +
                "link (its rows name one); adding '${link?.link}' would pay it twice"
        }
        return (preparation?.modifier ?: 0) + (link?.modifier ?: 0)
    }

    companion object {
        /**
         * "These bonuses are cumulative but cap at +3 total from supporting
         * skills." Five skills grant five points, so the cap is the whole
         * reason the table is not just addition.
         */
        const val SUPPORT_CAP = 3

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
