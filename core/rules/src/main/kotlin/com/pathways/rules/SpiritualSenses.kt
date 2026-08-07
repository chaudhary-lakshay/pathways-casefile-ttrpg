package com.pathways.rules

import com.pathways.model.Layer
import com.pathways.model.ModEffect
import com.pathways.model.ModTarget
import com.pathways.model.Modifier
import com.pathways.model.ModifierId
import com.pathways.model.ModifierSource
import com.pathways.model.Outcome
import com.pathways.model.SkillKey
import com.pathways.rules.content.CommonModifier
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.SenseModifier
import com.pathways.rules.content.SenseRow
import com.pathways.rules.content.SpiritualSense

/**
 * Chapter 4's two spiritual senses (p. 33-34).
 *
 * `Spiritual Intuition` (SPI/Hard) senses a threat before it arrives;
 * `Spiritual Perception` (SPI/Average) notices that something supernatural is
 * present. Both have been in `skills.json` and buyable in the builder since
 * M0, and until now both rolled bare -- the chapter that prints their result
 * tables sat at 0.0% coverage, because `coverage.py` sees no mechanical line
 * in four rows of `Success by 3+ / <prose>`. Same defect class as the
 * trackers reaching no roll (6h) and the Soul Debt printed and not charged
 * (6m); the fifth distinct way that instrument has misled.
 *
 * **This is deliberately not [DegreeOfSuccess].** Both tables band at
 * "Success by 3+" and "Success by 5+", exactly the general ladder's Good
 * (Target-3) and Excellent (Target-5) edges, which makes reusing it look
 * tidy. It would be the silent merge 4.1 forbids: the EFFECT is per-skill --
 * Intuition's 5+ is a specific premonition, Perception's identifies the
 * entity -- and a chapter's own table beats the appendix's general one, the
 * call [Divination]'s clarity rows, [FrightCheck], [RitualResolution] and
 * [Wounds] each already make. The shared edges are a coincidence recorded,
 * never a warrant.
 *
 * **Neither table prints a failure row.** All four rows are successes, so a
 * failed sense roll gets NO row rather than an invented consolation, and
 * nothing here overrides the critical rules -- there is nothing printed to
 * override them with, so [SuccessRoll]'s 3-4 and 17-18 stand. That is the
 * [FrightCheck] reading (6c), not the PCR's.
 *
 * **Intuition's "-2 distracted/in combat" collides with Appendix A.** That
 * appendix prices "Distracted or hurried -2" and "Very adverse conditions
 * (combat, extreme weather) -6 or worse" -- the same circumstances, the same
 * words, generally where this is specific. [modifiers] REFUSES the pair
 * rather than summing, so a distraction cannot be charged twice, and
 * [displacedCommonModifiers] lets a screen withhold the general chip instead
 * of offering one that throws (6t's lesson from the two Aim rows).
 */
object SpiritualSenses {

    /**
     * Perception's "+6 Seer" -- a SEQUENCE 9 RANK, not a pathway. A Fool at
     * Sequence 8 is a Clown, so this is offered as a chip quoting the book
     * rather than applied to anyone on the Fool pathway, which would author a
     * rule the book did not print. Same call as the Sealed Artifact grade
     * table's Power Equivalent column (6af): show it, do not gate on it.
     */
    const val SEER = "Seer"

    /** Both skills, in printed order. */
    fun all(): List<SpiritualSense> = ContentLoader.spiritualSenses()

    fun named(skill: String): SpiritualSense =
        all().firstOrNull { it.skill.equals(skill, ignoreCase = true) }
            ?: throw IllegalArgumentException("chapter 4 prints no sense called '$skill'")

    /**
     * The table for a skill about to be rolled, or null when the book prints
     * none for it -- which is every other skill on a sheet.
     */
    fun forSkill(key: SkillKey): SpiritualSense? =
        all().firstOrNull { it.skill.equals(key.name, ignoreCase = true) }

    /**
     * The row a roll landed on, or null when the book prints none for it.
     *
     * **Outcome first, then the last matching margin.** A rolled 3 or 4 is a
     * critical success by the always-rule whatever its margin, and the
     * critical row is the answer there; grading by margin first would report
     * the book's best row as an ordinary one, the slip [FrightCheck],
     * [RitualResolution] and [Wounds] each made once. Among the plain
     * successes the bands overlap -- a margin of 6 satisfies all three -- and
     * the book prints them least- to most-demanding, so the LAST match wins,
     * exactly as [DegreeOfSuccess] reads its ladder.
     *
     * A failure of either kind returns null: the table has no row for it, and
     * inventing one would be authoring.
     */
    fun rowFor(sense: SpiritualSense, outcome: Outcome?, margin: Int): SenseRow? = when (outcome) {
        Outcome.CRITICAL_SUCCESS -> sense.rows.firstOrNull { it.result == CRITICAL_ROW }
        Outcome.SUCCESS -> sense.rows
            .filter { it.result != CRITICAL_ROW && margin >= (it.margin ?: 0) }
            .lastOrNull()
        else -> null
    }

    /** The circumstances this sense prints, in printed order. */
    fun offered(sense: SpiritualSense): List<SenseModifier> = sense.modifiers

    /**
     * The Appendix A rows a chosen sense circumstance supersedes.
     *
     * Empty unless Intuition's distracted/in-combat row is among [chosen].
     * A screen calls this to withhold the general chips; [modifiers] refuses
     * the pair outright, so nothing can reach the double charge by another
     * route.
     */
    fun displacedCommonModifiers(chosen: List<SenseModifier>): Set<String> {
        val overlapping = chosen.any { it.situation.equals(DISTRACTED, ignoreCase = true) }
        if (!overlapping) return emptySet()
        return CommonModifiers.all()
            .filter { row -> OVERLAP_WORDS.any { row.situation.contains(it, ignoreCase = true) } }
            .map { it.situation }
            .toSet()
    }

    /**
     * The chosen circumstances as modifiers, ready for [SkillRoll].
     *
     * [common] is whatever Appendix A rows the same roll already carries.
     * Passing one this sense supersedes is refused rather than dropped: a
     * caller given a quietly shortened list would believe both were counted.
     *
     * **[sense] is required, and the rows are checked against IT rather than
     * against both tables.** The two senses print different circumstances --
     * a graveyard helps you notice a spirit, not foresee an ambush -- so
     * validating against every printed row would accept Perception's "+6
     * Seer" on an Intuition roll. Nothing can reach that today, because the
     * dialog only ever offers one sense's own rows; removing the permissive
     * path is what stops the next caller finding it, the call `Wounds`
     * already had to make about its damage-type fallback (6l).
     */
    fun modifiers(
        sense: SpiritualSense,
        chosen: List<SenseModifier> = emptyList(),
        common: List<CommonModifier> = emptyList(),
    ): List<Modifier> {
        val displaced = displacedCommonModifiers(chosen)
        val clash = common.map { it.situation }.filter { it in displaced }
        require(clash.isEmpty()) {
            "'$DISTRACTED' is chapter 4's own price for ${clash.joinToString()}; " +
                "take one or the other, never both"
        }
        val unknown = chosen.filterNot { it in sense.modifiers }
        require(unknown.isEmpty()) {
            "${sense.skill} prints no circumstance called " +
                unknown.joinToString { "'${it.situation}'" }
        }
        return chosen.distinctBy { it.situation }.map(::situational)
    }

    /** What the chosen circumstances come to, for a screen that shows a sum. */
    fun modifierTotal(chosen: List<SenseModifier> = emptyList()): Int =
        chosen.distinctBy { it.situation }.sumOf { it.modifier }

    private const val CRITICAL_ROW = "Critical Success"
    private const val DISTRACTED = "distracted/in combat"

    /**
     * The words that make the overlap real, rather than a resemblance. Both
     * appear verbatim in Appendix A's situations and in chapter 4's own row;
     * `extract_spirituality.verify()` fails if either stops doing so, because
     * that is the day the scoping stops being needed.
     */
    private val OVERLAP_WORDS = listOf("Distracted", "combat")

    private fun situational(row: SenseModifier) = Modifier(
        id = ModifierId("sense:${row.situation.lowercase()}"),
        source = ModifierSource.Situational(row.situation),
        // Retargeted onto the skill being rolled by SkillRoll, exactly as
        // CommonModifiers' rows are.
        target = ModTarget.AllRolls,
        effect = ModEffect.Flat(row.modifier),
        layer = Layer.SITUATIONAL,
    )
}
