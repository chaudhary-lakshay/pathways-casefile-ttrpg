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

    /**
     * What a Sequence block carries that this object deliberately does NOT read.
     *
     * [SequenceBlock.ability] and `reinforcedAbilities` are prose, printed by
     * `PathwayScreen` and parsed by nothing. That is 46.1% of the book's
     * mechanical lines, and chapters 9-12 report 100% coverage while it sits
     * there, because a coverage number measures extraction and this is a
     * question about application.
     *
     * **This is a GAP, not a refusal, and the distinction is the point.**
     * [Equipment.UNMODELLED] and [ActiveDefenses.UNPAYABLE] record rules this
     * app cannot support -- no inventory to own the item, no field to read.
     * Nothing of that kind blocks these: `Character.beyonder` records the
     * pathway and Sequence, [digestThrough] already folds the chain, and the
     * app spends SPI for a sealed artifact's ability on exactly this shape.
     * It is unbuilt work, and it must not be inherited as settled.
     *
     * **Every GURPS Basic Set deferral this book makes is either ruled on
     * already or sits in here.** Seven lines defer to a Basic Set rule this
     * book does not reprint (2723; 3653-3654; 6553; 7348; 8030; 8059; 8062,
     * whose clause opens at 8061). Two are outside this prose, are claimed
     * by a content file, and have a verdict: 2723 is
     * `spirit_damage.json`'s threshold list,
     * whose `HP <= 0` row points at rules chapter 5 prints for itself and
     * [VitalRolls] applies; 3653's specialisation technique is REFUSED in
     * [Divination.UNAPPLIED]. The other five are `ability` and
     * `reinforcedAbilities` text, so they are this entry's, not a separate
     * refusal's.
     *
     * That answers what thirteen slices kept deferring. `Shaken` -- *"the
     * target must roll HT or be Shaken (see GURPS p. B360) for 1 turn"*,
     * line 8030, which prints the roll and never what failing it costs --
     * was read as a special case earning its own recorded refusal. It is
     * not one. Three of the five name a term this book prints elsewhere and
     * this app models (`grappled`, and `mentally Stunned` twice), and TWO
     * name a term printed exactly once in the whole book: `Shaken` and
     * 7348's `suffocates`. A refusal filed against `Shaken` alone would be
     * a blocklist of one with its twin a chapter away. `test_tools_book_checks.py` pins
     * the census and both single printings, so a second printing of either
     * reddens rather than quietly widening this.
     *
     * What a design review stopped, and what the next attempt has to fix:
     *
     *  - **The price is not one number with a boolean.** The deleted Sealed
     *    Artifact cost record carried a `perTurn` boolean. The
     *    book prices these abilities in **at least three kinds** of qualifier,
     *    which is the part a test pins: recurring per turn AND per minute
     *    (this app's clock is one turn = one second, so collapsing those is a
     *    60x error), and per-unit scaling ("per raven", "per 2 bullets", "per
     *    fireball"). Reading the prose also shows action economy ("Free
     *    Action") and one composite ("+ Ritualistic Magic"), for fifteen
     *    distinct qualifier strings in all -- that fuller count is a reading,
     *    not a checked number, and the three pinned kinds are already one more
     *    than a boolean can carry.
     *  - **58 of the 237 printed SPI costs sit outside any named clause.**
     *    The named `Name (N SPI):` shape yields 173 clauses, but six of those
     *    carry two prices each, so it accounts for 179 mentions and not 173 --
     *    a distinction that matters because subtracting the clause COUNT from
     *    the mention count overstates the prose-only figure by exactly those
     *    six. The remainder are priced in running prose -- "Spend 1 SPI to
     *    activate", "All spells cost 2 SPI". A per-block
     *    list built from the named shape alone would read as exhaustive while
     *    silently dropping a clause, which is an invisible line reading as
     *    completion one layer down. Any parse needs a per-block reconciliation
     *    against the raw SPI count, not a global total.
     *  - **The entry point takes the chain, not the block.** Abilities are
     *    cumulative exactly as stats and skills are; a Sequence 5 Seer holds
     *    five blocks' worth. A `forBlock` API bypasses the three `require`
     *    guards in [digestThrough] and answers a different question than the
     *    caller asked.
     *
     * Possession is the chain's question: [digestThrough] answers it.
     */
    val UNPARSED = mapOf(
        "ability" to
            "priced Beyonder powers, 237 SPI costs in 15 qualifier shapes; " +
            "unbuilt work, not a refusal -- see this object's KDoc",
        "reinforcedAbilities" to
            "the same, for the blocks that reinforce an earlier Sequence",
    )

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
            ?: boonChain(character).takeIf { it.isNotEmpty() }?.let { digest(character, it) }

    /**
     * The boon [character] holds, as blocks (Ch. 13-15). A boon "functions
     * similarly to a Beyonder potion", and ch. 15 applies "the same rules
     * for potion-granted skills and attributes", so it is folded by the
     * same loop.
     *
     * READING: after the potions. A granted level is frozen at the
     * attributes of its moment and the save stores no order of acquisition,
     * so one is fixed here.
     */
    private fun boonChain(character: Character): List<SequenceBlock> =
        character.boon?.let(NonStandardPathways::chainFor).orEmpty()

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
        return digest(character, chain + boonChain(character), named = chain.last())
    }

    private fun digest(
        character: Character,
        chain: List<SequenceBlock>,
        /** The block the result is named for: the potion walked, where there is one. */
        named: SequenceBlock = chain.last(),
    ): DigestedCharacter {
        val derived = Derive.of(character)
        var grants = PotionGrants()
        val superseded = mutableSetOf<SkillKey>()
        val stacked = mutableMapOf<SkillKey, Stacked>()
        // A blessing's attributes, then the potions on top (6hf): one base,
        // read before the loop and inside it, which rebuilds every block.
        val base = Blessings.played(character.attributes, character.traits)
        var effectiveAttributes = base
        var effectiveSpi = character.spi
        var secondaries = SecondaryCharacteristics.of(effectiveAttributes, character.secondaryDelta)

        for (block in chain) {
            val stats = statGrants(block.stats)
            grants = grants.mergeStats(stats)

            effectiveAttributes = Attributes(
                st = base.st + (grants.attributes[Attr.ST] ?: 0),
                dx = base.dx + (grants.attributes[Attr.DX] ?: 0),
                iq = base.iq + (grants.attributes[Attr.IQ] ?: 0),
                ht = base.ht + (grants.attributes[Attr.HT] ?: 0),
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
            // The exception, on the owner's ruling (6hq): a bonus stacked on
            // a BOUGHT skill has those points behind it, so it follows them.
            grants = grants.copy(
                skillLevels = grants.skillLevels + stacked.mapValues { (_, on) -> purchased.getValue(on.bought) + on.bonus },
            )

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
                val grantedKey = alreadyGranted?.key ?: key
                val on = stacked[grantedKey] ?: bought?.takeIf { alreadyGranted == null }?.let { Stacked(it.key, 0) }
                on?.let { stacked -= grantedKey; stacked[key] = it.copy(bonus = it.bonus + grant.bonus) }

                val from = alreadyGranted?.value
                    ?: bought?.value
                    ?: Derive.controllingLevel(grant.attr, effectiveAttributes, secondaries, effectiveSpi)
                grants = grants.copy(
                    skillLevels = grants.skillLevels
                        .filterKeys { !it.matches(key) } + (key to from + grant.bonus),
                )
            }
        }

        return DigestedCharacter(
            base = character,
            pathway = named.pathway,
            sequence = named.sequence,
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

    /** A granted level that stands on a [bought] skill, and the bonuses on it. */
    private data class Stacked(val bought: SkillKey, val bonus: Int)

    /** The p. 86 identity: same name, same specialization, same difficulty. */
    private fun SkillKey.matches(other: SkillKey) = isSameSkill(this, other)

    /**
     * The p. 86 identity, public so a caller asking "does a grant supersede
     * this bought skill" (study, 6hu) asks the same question digestion does.
     */
    fun isSameSkill(a: SkillKey, b: SkillKey): Boolean =
        a.name == b.name && a.specialization == b.specialization && a.difficulty == b.difficulty

    /**
     * The most any walk of [blocks] adds to each attribute and secondary row,
     * at whichever Sequence it peaks: what a purchase must leave room for
     * ([PurchaseCeilings]). Each row is maximised on its own, so the answer
     * may combine two pathways no one character walks.
     */
    fun largestGrants(blocks: List<SequenceBlock>): PotionGrants =
        blocks.groupBy { it.pathway }.values.fold(PotionGrants()) { highest, pathway ->
            pathway.sortedByDescending { it.sequence }
                .runningFold(PotionGrants()) { walked, block -> walked.mergeStats(statGrants(block.stats)) }
                .fold(highest) { acc, walked -> acc.atLeast(walked) }
        }

    /**
     * The most a potion walk and a boon walk add TOGETHER: a boon is held
     * beside a potion, so a purchase must leave room for both.
     */
    fun largestGrants(potions: List<SequenceBlock>, boons: List<SequenceBlock>): PotionGrants =
        largestGrants(potions).mergeStats(largestGrants(boons))

    private fun PotionGrants.atLeast(other: PotionGrants) = copy(
        attributes = Attr.entries.associateWith { maxOf(attributes[it] ?: 0, other.attributes[it] ?: 0) },
        secondary = SecondaryDelta(
            hp = maxOf(secondary.hp, other.secondary.hp),
            fp = maxOf(secondary.fp, other.secondary.fp),
            will = maxOf(secondary.will, other.secondary.will),
            per = maxOf(secondary.per, other.secondary.per),
            basicSpeedQuarters = maxOf(secondary.basicSpeedQuarters, other.secondary.basicSpeedQuarters),
            basicMove = maxOf(secondary.basicMove, other.secondary.basicMove),
        ),
    )

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
