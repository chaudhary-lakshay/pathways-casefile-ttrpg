package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.TraitEffects.ACUTE_HEARING
import com.pathways.rules.TraitEffects.ACUTE_SMELL
import com.pathways.rules.TraitEffects.ACUTE_TASTE_SMELL
import com.pathways.rules.TraitEffects.ACUTE_TOUCH
import com.pathways.rules.TraitEffects.ACUTE_VISION
import com.pathways.rules.TraitEffects.CHARISMA
import com.pathways.rules.TraitEffects.COMBAT_REFLEXES
import com.pathways.rules.TraitEffects.FIT
import com.pathways.rules.TraitEffects.GREATER_ACUTE_VISION
import com.pathways.rules.TraitEffects.HIGH_PAIN_THRESHOLD
import com.pathways.rules.TraitEffects.INCISIVE_VISION
import com.pathways.rules.TraitEffects.PERFECT_BALANCE
import com.pathways.rules.TraitEffects.maxLevelsFor
import com.pathways.rules.TraitNames.catalogueName
import com.pathways.rules.TraitNames.levelOf
import com.pathways.rules.content.ContentLoader

/**
 * The catalogue rows a walked potion chain grants, at zero cost (p. 85), and
 * the rows it prints and does not grant, each with its reason. Moved out of
 * [TraitEffects] unchanged, so that file stays readable (split step 5).
 */
object TraitGrants {
    /**
     * What a walkable potion block grants that the trait catalogue, or a
     * sense row above, can apply (6fi; 6fk). Pathway advantages and drawbacks
     * are prose and never become traits, so the table is written here, as
     * the Lucky One is ([TraitEffects.rerollSource]), and a census test reads it against
     * every block's prose both ways, with [REFUSED_GRANTS] as its other side.
     *
     * A catalogue row is granted where the block prints the catalogue's own
     * numbers; a wording that differs only in a clause nothing applies
     * (Tyrant 7's "never become lost at sea or on land") is the same row.
     * High Pain Threshold's "+3 to HT rolls to avoid knockdown/stun" is the
     * Major Wound roll, whose failure stuns, and which the catalogue row
     * already reaches ([TraitAttributeRolls.majorWoundHt]); recovering FROM stun is Combat
     * Reflexes's, a different verb in the same table (Hermit 8).
     *
     * A drawback is granted like an advantage: the book makes it part of
     * the potion and gives no points back for it (5440-5448). Second Law's
     * and Sublunary Eye's blocks print rows too (9335, 9386), but they are
     * non-standard pathways, never drunk, and not in [ContentLoader.pathways].
     */
    private val GRANTED_TRAITS: Map<Pair<String, Int>, List<Trait>> = mapOf(
        ("Fool" to 8) to advantages(PERFECT_BALANCE),
        ("Visionary" to 9) to advantages(ACUTE_VISION, EIDETIC_MEMORY),
        ("Visionary" to 8) to advantages(INCISIVE_VISION),
        ("Visionary" to 7) to advantages(ACUTE_SMELL),
        ("Sun" to 9) to advantages(FIT),
        ("Sun" to 7) to advantages(FIT),
        ("Tyrant" to 9) to advantages(PERFECT_BALANCE),
        ("Tyrant" to 7) to advantages("Absolute Direction", EIDETIC_MEMORY),
        ("White Tower" to 9) to advantages(EIDETIC_MEMORY),
        ("Death" to 9) to advantages("Cold Resistance"),
        ("Demoness" to 9) to advantages(ACUTE_VISION, ACUTE_HEARING),
        ("Demoness" to 6) to advantages(GREATER_ACUTE_VISION),
        ("Red Priest" to 9) to advantages(ACUTE_VISION, ACUTE_HEARING),
        ("Red Priest" to 6) to advantages(ACUTE_VISION, ACUTE_HEARING, ACUTE_TASTE_SMELL, ACUTE_TOUCH),
        ("Justiciar" to 9) to advantages("Voice"),
        ("Justiciar" to 8) to advantages(EIDETIC_MEMORY),
        ("Hanged Man" to 8) to advantages(ACUTE_HEARING),
        ("Twilight Giant" to 8) to advantages(HIGH_PAIN_THRESHOLD),
        ("Hermit" to 8) to advantages(COMBAT_REFLEXES),
        ("Wheel of Fortune" to 8) to advantages("Danger Sense"),
        ("Error" to 7) to advantages(EIDETIC_MEMORY),
        // Each block's own increment; [grantedTraits] adds them down the chain.
        ("Error" to 8) to advantages("$CHARISMA 1"),
        ("Demoness" to 8) to advantages("$CHARISMA 2"),
        // A face is a rung of the appearance table, not a catalogue row:
        // [grantedTraits] keeps the chain's better one (6fn).
        ("Demoness" to 7) to advantages("$CHARISMA 1", "Handsome / Beautiful"),
        ("Demoness" to 6) to advantages(GREATER_ACUTE_VISION, "$CHARISMA 1", "Very Handsome / Very Beautiful"),
        ("Abyss" to 8) to listOf(
            "Compulsive Behavior (Indulge Evil Desires) SC 6", "Unsettling Appearance", "Bloodlust", "Callous",
        ).map { Trait(it, 0, TraitKind.DISADVANTAGE) },
    )

    private const val EIDETIC_MEMORY = "Eidetic Memory"

    private fun advantages(vararg names: String) = names.map { Trait(it, 0, TraitKind.ADVANTAGE) }

    /**
     * The catalogue rows a walkable block prints and this app does NOT
     * grant, each with its reason: the census's other side (6fk).
     */
    private val REFUSED_GRANTS: Map<Pair<Pair<String, Int>, String>, String> = mapOf(
        (("Fool" to 9) to EIDETIC_MEMORY) to
            "\"continuously grows as the potion digests\" prints no number, not the catalogue's +5",
        (("Red Priest" to 9) to "Danger Sense") to "\"Danger Sense (partial)\" is a variant the catalogue does not print",
        (("Red Priest" to 8) to "Fast Healer") to "\"Fast Healer (Limited)\": 1 HP per 2 days, not the catalogue's per day",
        (("Moon" to 8) to "Empathy") to "\"Empathy (animals)\" is a variant the catalogue does not print",
        (("Tyrant" to 7) to PERFECT_BALANCE) to
            "\"Perfect Balance (Enhanced)\" is a variant; Tyrant 9's own Perfect Balance is granted",
    )

    /**
     * A block that makes the drinker choose one drawback, and the catalogue
     * rows it offers (6he). Hanged Man 9 prints "Paranoia, Hallucinations, or
     * Compulsion (Hanged Man; -10 pts) Choose one of these three drawbacks";
     * the third is the catalogue's "Compulsion (pathway)", 5-15.
     */
    private val DRAWBACK_CHOICES: Map<Pair<String, Int>, List<String>> = mapOf(
        ("Hanged Man" to 9) to listOf("Paranoia", "Hallucinations", "Compulsion (pathway)"),
    )

    /** The table, for its census. */
    fun grantedTraitTable(): Map<Pair<String, Int>, List<Trait>> = GRANTED_TRAITS

    /** The choice table, for its census. */
    fun drawbackChoiceTable(): Map<Pair<String, Int>, List<String>> = DRAWBACK_CHOICES

    /** The drawbacks [state]'s walked chain offers to choose between. */
    fun drawbackChoices(state: com.pathways.model.BeyonderState): List<String> =
        DRAWBACK_CHOICES
            .filterKeys { (pathway, sequence) -> pathway == state.pathway && sequence >= state.sequence }
            .values.flatten()

    /** The refusals, for the census: (block, catalogue row) to reason. */
    fun refusedGrants(): Map<Pair<Pair<String, Int>, String>, String> = REFUSED_GRANTS

    /**
     * Every row the walked chain granted, from Sequence 9 down to [state]'s,
     * because sequences are cumulative. Zero cost: the potion paid (p. 85).
     *
     * A row granted twice is held once, except a LEVELLED one, whose blocks'
     * levels add (6fm): Charisma sits in the Stat/Advantage column beside
     * "DX +1", which "goes up by 1 permanently" (5446), so Demoness 8's +2,
     * 7's +1 and 6's +1 hold Charisma 4 -- the catalogue's own "[max 4
     * levels]", which [levelled] also caps the printed level at.
     *
     * A chosen drawback is granted like the rest, when it is one of the
     * chain's own [drawbackChoices]; any other stored name grants nothing.
     */
    fun grantedTraits(state: com.pathways.model.BeyonderState?): List<Trait> {
        if (state == null) return emptyList()
        val chosen = state.drawback?.takeIf { it in drawbackChoices(state) }
            ?.let { listOf(Trait(it, 0, TraitKind.DISADVANTAGE)) }.orEmpty()
        return chosen + GRANTED_TRAITS
            .filterKeys { (pathway, sequence) -> pathway == state.pathway && sequence >= state.sequence }
            .values.flatten()
            .groupBy { catalogueName(it.name).lowercase() }.values
            .map { rows ->
                val first = rows.first()
                if (!isLevelled(first)) first else levelled(first, rows.sumOf { levelOf(listOf(it), catalogueName(it.name))!! })
            }
            .let { rows -> rows.filter { !isFace(it) || it.name == Appearance.played(rows)?.level } }
    }

    /**
     * [traits] plus every granted row they do not already hold, by catalogue
     * name, so a row bought and granted is one row and a bonus once. What a
     * sheet's rolls read; never the character, which stays purchased-only.
     *
     * A levelled grant ADDS to a bought level instead (6fm): the held row is
     * replaced in place by one at the sum, keeping its cost and kind, because
     * [levelOf] reads one row per trait and a second copy would be hidden.
     */
    fun withGrantedTraits(traits: List<Trait>, state: com.pathways.model.BeyonderState?): List<Trait> {
        val granted = grantedTraits(state).associateBy { catalogueName(it.name).lowercase() }
        val held = traits.map { catalogueName(it.name).lowercase() }.toSet()
        val merged = traits.map { owned ->
            val name = catalogueName(owned.name)
            val grant = granted[name.lowercase()]?.takeIf { isLevelled(it) } ?: return@map owned
            levelled(owned, levelOf(listOf(owned), name)!! + levelOf(listOf(grant), name)!!)
        }
        // A face is played only when it beats the bought one (6fn); a worse
        // bought face stays, because the budget bills it.
        return merged + granted.filterKeys { it !in held }.values
            .filter { !isFace(it) || Appearance.played(traits + it)?.level == it.name }
    }

    private fun isFace(trait: Trait): Boolean = Appearance.played(listOf(trait)) != null

    /**
     * The levels a potion ADDED to [merged], a row of [withGrantedTraits],
     * over what [bought] holds of it; null when it added none, or the row was
     * not bought. Never the grant's own level: a bought Charisma 3 under
     * Demoness 8's +2 stops at the cell's 4, so the potion added one (6fm).
     * What the Traits tab says beside the points.
     */
    fun grantedLevelsIn(merged: Trait, bought: List<Trait>): Int? {
        val name = catalogueName(merged.name)
        val had = levelOf(bought, name) ?: return null
        return (levelOf(listOf(merged), name)!! - had).takeIf { it > 0 }
    }

    /**
     * Whether the catalogue prices [trait] per level: never a number in the
     * name, which Abyss 8's "Compulsive Behavior (...) SC 6" carries too.
     */
    private fun isLevelled(trait: Trait): Boolean =
        ContentLoader.advantages().any { it.perLevel && it.name.equals(catalogueName(trait.name), ignoreCase = true) }

    /** [trait] at [level], no higher than its catalogue row's printed ceiling. */
    private fun levelled(trait: Trait, level: Int): Trait {
        val name = catalogueName(trait.name)
        return trait.copy(name = "$name ${minOf(level, maxLevelsFor(name) ?: level)}")
    }
}
