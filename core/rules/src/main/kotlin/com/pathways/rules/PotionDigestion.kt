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

    /** Every standard pathway begins at Sequence 9 (Ch. 9). */
    const val STARTING_SEQUENCE = 9

    /** One potion, drunk by a character who has digested nothing before it. */
    fun digest(character: Character, block: SequenceBlock): DigestedCharacter =
        digest(character, listOf(block))

    /**
     * What [character] currently is, given the pathway and Sequence they have
     * drunk their way to. Null for a mortal -- there is nothing to digest, and
     * a mortal is not a Beyonder at Sequence 9 with no grants.
     */
    fun of(character: Character, blocks: List<SequenceBlock>): DigestedCharacter? =
        character.beyonder?.let { digestThrough(character, it.pathway, it.sequence, blocks) }

    /**
     * Every potion up to and including [sequence] on [pathway], in the order
     * a Beyonder drinks them (9 first, then 8, ...).
     *
     * Sequences are cumulative: a Sequence 8 Fool drank the Sequence 9 potion
     * first and keeps its SPI +9. Digesting the Sequence 8 block alone
     * describes what that one potion adds, not what the Beyonder is.
     */
    fun digestThrough(
        character: Character,
        pathway: String,
        sequence: Int,
        blocks: List<SequenceBlock>,
    ): DigestedCharacter {
        val chain = blocks
            .filter { it.pathway == pathway && it.sequence >= sequence }
            .sortedByDescending { it.sequence }
        require(chain.isNotEmpty()) { "no Sequence $sequence or earlier on the $pathway Pathway" }
        require(chain.last().sequence == sequence) {
            "the $pathway Pathway has no Sequence $sequence block; digesting through it would " +
                "silently report Sequence ${chain.last().sequence} instead"
        }
        require(chain.first().sequence == STARTING_SEQUENCE) {
            "the $pathway Pathway chain starts at Sequence ${chain.first().sequence}, " +
                "not $STARTING_SEQUENCE -- a Beyonder cannot skip the potion they began with"
        }
        return digest(character, chain)
    }

    private fun digest(character: Character, chain: List<SequenceBlock>): DigestedCharacter {
        val derived = Derive.of(character)
        var grants = PotionGrants()
        val superseded = mutableSetOf<SkillKey>()
        var effectiveAttributes = character.attributes
        var effectiveSpi = character.spi
        var secondaries = SecondaryCharacteristics.of(effectiveAttributes, character.secondaryDelta)

        for (block in chain) {
            val stats = statGrants(block.stats)
            grants = grants.mergeStats(stats)

            effectiveAttributes = Attributes(
                st = character.attributes.st + (grants.attributes[Attr.ST] ?: 0),
                dx = character.attributes.dx + (grants.attributes[Attr.DX] ?: 0),
                iq = character.attributes.iq + (grants.attributes[Attr.IQ] ?: 0),
                ht = character.attributes.ht + (grants.attributes[Attr.HT] ?: 0),
            )
            effectiveSpi = character.spi + grants.spi
            secondaries = SecondaryCharacteristics.of(
                effectiveAttributes,
                character.secondaryDelta + grants.secondary,
            )

            // Purchased skills are re-levelled against the attributes the
            // potions raised: a skill level is "controlling attribute +
            // rungs", so a DX+1 potion lifts every DX skill the character
            // already bought, not only the ones a potion also grants. A
            // level a potion GRANTED is not re-levelled -- the book fixes it
            // at digestion ("your IQ + 3"), with no points behind it to
            // recompute from.
            val purchased = purchasedLevels(character, effectiveAttributes, secondaries, effectiveSpi)

            for (grant in block.skills) {
                val key = skillKey(grant.name, grant.attr, grant.difficulty)
                // p. 86: an exact name-and-difficulty match stacks on what the
                // character already has -- bought earlier, or granted by an
                // earlier potion on this same pathway. The controlling
                // attribute is deliberately not part of the match; the book
                // keys the rule on name and difficulty.
                val alreadyGranted = grants.skillLevels.entries.firstOrNull { it.key.matches(key) }
                val bought = purchased.entries.firstOrNull { it.key.matches(key) }
                bought?.let { superseded += it.key }

                val from = alreadyGranted?.value
                    ?: bought?.value
                    ?: Derive.controllingLevel(grant.attr, effectiveAttributes, secondaries, effectiveSpi)
                grants = grants.copy(
                    skillLevels = grants.skillLevels
                        .filterKeys { !it.matches(key) } + (key to from + grant.bonus),
                )
            }
        }

        val last = chain.last()
        return DigestedCharacter(
            base = character,
            pathway = last.pathway,
            sequence = last.sequence,
            grants = grants,
            effectiveAttributes = effectiveAttributes,
            effectiveSpi = effectiveSpi,
            secondaries = secondaries,
            // A granted level supersedes the purchased one it stacked on:
            // the grant was computed FROM that level, so keeping both would
            // list the same skill twice at two different levels. The two keys
            // can differ (the match ignores the controlling attribute), so
            // the superseded entry is dropped by identity, not by map key.
            skillLevels = purchasedLevels(character, effectiveAttributes, secondaries, effectiveSpi)
                .filterKeys { it !in superseded } + grants.skillLevels,
            budget = derived.budget,
        )
    }

    private fun purchasedLevels(
        character: Character,
        attributes: Attributes,
        secondaries: Secondaries,
        spi: Int,
    ): Map<SkillKey, Int> = character.skills.associate { s ->
        s.key to SkillLadder.levelFor(
            s.points,
            s.key.difficulty,
            Derive.controllingLevel(s.key.attr, attributes, secondaries, spi),
        )
    }

    /** The p. 86 identity: same name, same specialization, same difficulty. */
    private fun SkillKey.matches(other: SkillKey) =
        name == other.name && specialization == other.specialization && difficulty == other.difficulty

    private fun PotionGrants.mergeStats(other: PotionGrants) = copy(
        attributes = (attributes.keys + other.attributes.keys).associateWith {
            (attributes[it] ?: 0) + (other.attributes[it] ?: 0)
        },
        spi = spi + other.spi,
        secondary = secondary + other.secondary,
    )

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
