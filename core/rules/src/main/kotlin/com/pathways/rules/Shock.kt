package com.pathways.rules

import com.pathways.model.Layer
import com.pathways.model.ModEffect
import com.pathways.model.ModTarget
import com.pathways.model.Modifier
import com.pathways.model.ModifierId
import com.pathways.model.ModifierSource
import com.pathways.model.Trait
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.MajorWound

/**
 * Every kind of 3d6 roll this app makes, so shock's scope is a DECISION.
 *
 * The alternative is an exclusion nobody wrote down: wire the ledger that
 * feeds skill rolls, miss the four attribute rolls that compute their own
 * targets, and the gap looks like an oversight in a UI adapter rather than a
 * reading of the book (Codex, at design time).
 */
enum class RollKind {
    /** Anything resolved through [SkillRoll] -- skills, attacks, rituals, divination, boons. */
    SKILL,

    /** A bare attribute roll: a Fright Check, a self-control roll, [Concentration], [Detection]. */
    ATTRIBUTE,

    /** Dodge, Parry and Block -- a number the player rolls under. */
    ACTIVE_DEFENSE,

    /** The Major Wound's own HT roll, which is where shock comes FROM. */
    MAJOR_WOUND_HT,
}

/**
 * The Major Wound table's success row: "-4 to all rolls for 1 turn from shock
 * and pain" (Ch. 5, p. 42).
 *
 * **This app produces the state and never charged for it.** `DamageDialog`
 * has offered that HT roll since 6n and printed this effect as text; nothing
 * applied it. The same shape as the stun penalty one row down (6bl), which
 * the FAILURE row prints -- so on that table a success costs -4 to everything
 * for a turn and a failure stuns, and until now neither reached a number.
 *
 * **"All rolls" is taken at its word, and that is a reading.** GURPS 4e
 * exempts active defences from its shock penalty; this book prints "all
 * rolls" unqualified, and GURPS's conventions are GURPS's (6l). The one
 * refusal is [RollKind.MAJOR_WOUND_HT]: that roll is what CREATES the shock,
 * so applying it there would let a state penalise the roll that produced it.
 *
 * **It is latched, not declared and not derived.** The app rolled the table,
 * so it knows -- the reasoning `SavedSession.corrupted` already uses (6a). A
 * GM clears it when the turn passes; nothing here counts turns, because this
 * app holds no clock (6ad, 6ah, 6an) and a stored one-turn fact would outlive
 * its turn by construction.
 */
object Shock {

    /**
     * The penalty the row prints.
     *
     * A number inside a prose cell, so it lives here with the sentence pinned
     * by the extractor -- the split the maneuver table's +4/-4 (6i) and the
     * stun's -4 (6bl) already take.
     */
    const val PENALTY = -4

    /** The Major Wound row this penalty is printed on. */
    private const val ROW = "Success"

    /** Ch. 3's High Pain Threshold: "Ignore shock penalties from injury". */
    const val IGNORED_BY = "High Pain Threshold"

    /**
     * Whether [row] is the Major Wound result that causes shock.
     *
     * The row's name lives here rather than in the screen that rolls it: a
     * dialog comparing a string to "Success" would be a second copy of which
     * row this rule belongs to.
     */
    fun causedBy(row: MajorWound): Boolean = row.result == ROW

    /** That row's effect cell, quoted rather than paraphrased. */
    fun printed(): String = ContentLoader.majorWound().first { it.result == ROW }.effect

    /**
     * Whether shock reaches a roll of this kind at all.
     *
     * Three of the four are the same answer for the same reason -- the book
     * says "all rolls" and names no exception -- and the fourth is refused
     * because of WHEN it happens rather than what it is.
     */
    fun appliesTo(kind: RollKind): Boolean = kind != RollKind.MAJOR_WOUND_HT

    /**
     * Whether [traits] carry the advantage that ignores it.
     *
     * Its cell reads "Ignore shock penalties from injury; +3 on HT rolls to
     * avoid knockdown". The first clause is this; the second is applied by
     * [TraitEffects.majorWoundHt], because the Major Wound's own HT roll
     * decides knockdown on its fail-by-5 row. **This sentence used to say
     * that clause reached nothing**, which stopped being true in the commit
     * that made it reach something -- a report of what is not applied expires
     * exactly when the code changes (6ba, 6bl).
     *
     * The book's OTHER knockdown rolls, in the hit-location effects
     * ("Knockdown roll at -5"), print no attribute to roll against, so this
     * app shows those cells rather than rolling them.
     */
    fun ignoredBy(traits: List<Trait>): Boolean = TraitEffects.levelOf(traits, IGNORED_BY) != null

    /**
     * What shock costs a roll of [kind], which is 0 unless it is in force.
     *
     * The single decision both paths ask: the resolver-backed rolls take
     * [modifiers] and the four attribute rolls take this number, and a screen
     * that worked either out for itself would be the two-copies failure this
     * codebase produced three times (6ag, 6ai, 6be).
     */
    fun penaltyFor(kind: RollKind, shocked: Boolean, traits: List<Trait>): Int =
        if (shocked && appliesTo(kind) && !ignoredBy(traits)) PENALTY else 0

    /**
     * Shock as a modifier, for everything [SkillRoll] resolves.
     *
     * A [ModifierSource.StateThreshold], like the pool penalties: it is what
     * the character is carrying, not what the GM says about this turn.
     *
     * **Deliberately NOT a [ModEffect.Cancel] from the trait**, which is what
     * a design review asked for. `Cancel` is keyed by [ModifierSource.kind]
     * and every state penalty in this app shares `SourceKind.STATE` --
     * [Conditions.forSkill] emits reeling, fatigue, Spiritual Attrition and
     * the CoR ladder through that same source -- so a budget of 4 aimed at
     * STATE would also offset a dying character's reeling. High Pain
     * Threshold ignores shock, not fatigue. The trait suppresses this one
     * modifier at its source instead, and [TraitEffects] reports that it did,
     * which is what keeps the cancellation visible without widening it.
     */
    fun modifiers(shocked: Boolean, traits: List<Trait>): List<Modifier> {
        val value = penaltyFor(RollKind.SKILL, shocked, traits)
        if (value == 0) return emptyList()
        return listOf(
            Modifier(
                id = ModifierId("state:shock"),
                source = ModifierSource.StateThreshold("shock"),
                // Retargeted onto the skill being rolled by SkillRoll, as
                // Conditions' rows are. "All rolls" needs no scoping.
                target = ModTarget.AllRolls,
                effect = ModEffect.Flat(value),
                layer = Layer.STATE_THRESHOLD,
            ),
        )
    }
}
