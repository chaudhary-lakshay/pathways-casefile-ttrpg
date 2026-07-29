package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.Attributes
import com.pathways.model.Character
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.SecondaryDelta
import com.pathways.model.SkillKey
import com.pathways.rules.content.SequenceBlock
import com.pathways.rules.content.StatGain

/**
 * What a Sequence potion gives, held apart from what the character bought.
 *
 * [secondary] reuses [SecondaryDelta] for its shape only. A SecondaryDelta on
 * a [Character] means "points were spent moving this off base" and is billed
 * by [PointBudget]; here it means "the potion moved this, for free". The two
 * are never stored in the same field -- see [DigestedCharacter].
 */
data class PotionGrants(
    val attributes: Map<Attr, Int> = emptyMap(),
    val spi: Int = 0,
    val secondary: SecondaryDelta = SecondaryDelta(),
    val skillLevels: Map<SkillKey, Int> = emptyMap(),
)

/**
 * A character plus the Sequence potion they have digested.
 *
 * The potion's gains are free (p. 85: "The potion gives you stat bonuses,
 * skill levels, and advantages for free"), so [budget] is computed from the
 * PURCHASED state alone and is identical to the undigested character's. The
 * grants live in [grants] and are folded in only for the *effective* values
 * a sheet displays and a roll resolves against. Nothing here mutates [base],
 * so digesting twice from the same base gives the same answer.
 */
data class DigestedCharacter(
    val base: Character,
    val pathway: String,
    val sequence: Int,
    val grants: PotionGrants,
    val effectiveAttributes: Attributes,
    val effectiveSpi: Int,
    val secondaries: Secondaries,
    val skillLevels: Map<SkillKey, Int>,
    val budget: BudgetReport,
)

/**
 * Applies a Sequence potion (Ch. 9-12) to a character.
 *
 * Two rules from the book drive everything here:
 *  - p. 85, the +N on a granted skill means "attribute + N", not N rungs up
 *    the ladder from default.
 *  - p. 86, if the character already has that skill -- same name AND same
 *    difficulty -- the bonus reads "current level + N" instead, stacking on
 *    the proficiency they already paid for.
 *
 * Attribute gains are folded in before skill levels resolve: the potion's
 * changes are simultaneous, and "your IQ + 3" means the IQ you have once the
 * potion is digested.
 */
object PotionDigestion {

    fun digest(character: Character, block: SequenceBlock): DigestedCharacter {
        val statGrants = statGrants(block.stats)
        val derived = Derive.of(character)

        val effectiveAttributes = Attributes(
            st = character.attributes.st + (statGrants.attributes[Attr.ST] ?: 0),
            dx = character.attributes.dx + (statGrants.attributes[Attr.DX] ?: 0),
            iq = character.attributes.iq + (statGrants.attributes[Attr.IQ] ?: 0),
            ht = character.attributes.ht + (statGrants.attributes[Attr.HT] ?: 0),
        )
        val effectiveSpi = character.spi + statGrants.spi
        val secondaries = SecondaryCharacteristics.of(
            effectiveAttributes,
            character.secondaryDelta + statGrants.secondary,
        )

        // Purchased skills are re-levelled against the attributes the potion
        // just raised: a skill level is "controlling attribute + rungs", so a
        // DX+1 potion lifts every DX skill the character already bought, not
        // only the ones the potion also grants.
        val purchased = character.skills.associate { s ->
            s.key to SkillLadder.levelFor(
                s.points,
                s.key.difficulty,
                Derive.controllingLevel(s.key.attr, effectiveAttributes, secondaries, effectiveSpi),
            )
        }

        val superseded = mutableSetOf<SkillKey>()
        val granted = block.skills.associate { grant ->
            val key = skillKey(grant.name, grant.attr, grant.difficulty)
            // p. 86: an exact name-and-difficulty match stacks on what the
            // character already has; anything else is a new skill at attr+N.
            // The controlling attribute is deliberately not part of the
            // match -- the book keys the rule on name and difficulty.
            val existing = purchased.entries.firstOrNull { (k, _) ->
                k.name == key.name &&
                    k.specialization == key.specialization &&
                    k.difficulty == key.difficulty
            }
            existing?.let { superseded += it.key }
            val from = existing?.value
                ?: Derive.controllingLevel(grant.attr, effectiveAttributes, secondaries, effectiveSpi)
            key to from + grant.bonus
        }

        return DigestedCharacter(
            base = character,
            pathway = block.pathway,
            sequence = block.sequence,
            grants = statGrants.copy(skillLevels = granted),
            effectiveAttributes = effectiveAttributes,
            effectiveSpi = effectiveSpi,
            secondaries = secondaries,
            // A granted level supersedes the purchased one it stacked on:
            // the grant was computed FROM that level, so keeping both would
            // list the same skill twice at two different levels. The two keys
            // can differ (the match ignores the controlling attribute), so
            // the superseded entry is dropped by identity, not by map key.
            skillLevels = purchased.filterKeys { it !in superseded } + granted,
            budget = derived.budget,
        )
    }

    private fun statGrants(stats: List<StatGain>): PotionGrants {
        var attributes = emptyMap<Attr, Int>()
        var spi = 0
        var secondary = SecondaryDelta()
        for (gain in stats) {
            when (gain.attr) {
                "ST" -> attributes = attributes.raise(Attr.ST, wholeSteps(gain))
                "DX" -> attributes = attributes.raise(Attr.DX, wholeSteps(gain))
                "IQ" -> attributes = attributes.raise(Attr.IQ, wholeSteps(gain))
                "HT" -> attributes = attributes.raise(Attr.HT, wholeSteps(gain))
                "SPI" -> spi += wholeSteps(gain)
                "HP" -> secondary = secondary.copy(hp = secondary.hp + wholeSteps(gain))
                "FP" -> secondary = secondary.copy(fp = secondary.fp + wholeSteps(gain))
                "Will" -> secondary = secondary.copy(will = secondary.will + wholeSteps(gain))
                "Per" -> secondary = secondary.copy(per = secondary.per + wholeSteps(gain))
                "Basic Move" -> secondary = secondary.copy(basicMove = secondary.basicMove + wholeSteps(gain))
                // Basic Speed is priced and stored in quarter steps, and the
                // book grants it in quarters too (+0.25, Seq 9 Assassin).
                "Basic Speed" -> secondary = secondary.copy(
                    basicSpeedQuarters = secondary.basicSpeedQuarters + quarterSteps(gain),
                )
                else -> error("unhandled stat grant '${gain.attr}'")
            }
        }
        return PotionGrants(attributes, spi, secondary)
    }

    private fun wholeSteps(gain: StatGain): Int {
        val whole = gain.delta.toInt()
        require(whole.toDouble() == gain.delta) {
            "${gain.attr} moves in whole steps; cannot grant ${gain.delta}"
        }
        return whole
    }

    private fun quarterSteps(gain: StatGain): Int {
        val quarters = gain.delta * 4
        require(quarters.toInt().toDouble() == quarters) {
            "Basic Speed moves in quarter steps; cannot grant ${gain.delta}"
        }
        return quarters.toInt()
    }

    private fun Map<Attr, Int>.raise(attr: Attr, delta: Int): Map<Attr, Int> =
        this + (attr to (this[attr] ?: 0) + delta)

    private operator fun SecondaryDelta.plus(other: SecondaryDelta) = SecondaryDelta(
        hp = hp + other.hp,
        fp = fp + other.fp,
        will = will + other.will,
        per = per + other.per,
        basicSpeedQuarters = basicSpeedQuarters + other.basicSpeedQuarters,
        basicMove = basicMove + other.basicMove,
    )

    /** "Guns (Pistol)" -> name "Guns", specialization "Pistol". */
    private fun skillKey(name: String, attr: ControllingAttribute, difficulty: Difficulty) = SkillKey(
        name = name.substringBefore(" ("),
        specialization = if (" (" in name) name.substringAfter(" (").removeSuffix(")") else null,
        attr = attr,
        difficulty = difficulty,
    )
}
