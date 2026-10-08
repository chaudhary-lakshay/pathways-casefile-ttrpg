package com.pathways.rules

import com.pathways.model.Character
import com.pathways.model.Trait
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.CorGain
import com.pathways.rules.content.RitualCorThreshold

/**
 * CoR -- Corruption (Ch. 6, p. 45).
 *
 * "CoR measures how close a Beyonder is to losing their humanity and sanity.
 * Maximum CoR equals the character's Will score. Reaching maximum CoR means
 * the character becomes an NPC monster or irreversibly mad Beyonder --
 * removed from play."
 *
 * Nothing here stores a running total. CoR is table state, like current HP:
 * [Character] holds purchased state plus the Beyonder CHOICE, and billing a
 * play-time counter into it would be the same mistake as writing potion
 * grants into `attributes` (CLAUDE.md section 6). The caller owns the number;
 * this object says what it means.
 *
 * **The loss is latched by the caller, not recomputed here.** [isLostAt] is a
 * question about a moment -- "is this total at or past this maximum" -- and
 * the maximum MOVES: a potion that raises Will raises the cap with it. A
 * character who hit their cap at Will 10 is permanently lost (the GM has
 * them), and must not become playable again because Will later reached 11.
 * Whoever tracks the total records the crossing when it happens.
 */
object Corruption {

    /**
     * The Ch. 3 advantage that widens the cap: "Maximum CoR equal Will + 3
     * rather than just Will" (p. 18, 5 pts).
     */
    const val FORTITUDE = "Spiritual Fortitude"

    const val FORTITUDE_BONUS = 3

    /**
     * The penalty the 3-CoR rung imposes: "All ritual rolls at -1 from
     * spiritual instability" (p. 68).
     *
     * A constant rather than a number parsed out of the row's prose -- the
     * effect text is a sentence, not a field. `CorruptionTest` asserts the row
     * still says -1, so content and constant cannot drift apart silently.
     */
    const val RITUAL_INSTABILITY_PENALTY = -1

    /** The CoR Gained table (p. 45), verbatim. */
    fun gained(): List<CorGain> = ContentLoader.corGained()

    /**
     * Maximum CoR for [character]: their Will, plus 3 with [FORTITUDE].
     *
     * Will comes from [Derive], not from IQ, so a bought Will -- or a potion's,
     * once the caller passes the digested character -- raises the ceiling the
     * same way the book does.
     */
    fun maxFor(character: Character): Int =
        maxFor(Derive.of(character).secondaries.will, character.traits)

    /**
     * Maximum CoR from an already-derived [will], for a character whose Will
     * a potion has moved: pass [com.pathways.rules.DigestedCharacter]'s
     * `secondaries.will` and the base character's [traits]. Deriving it again
     * from the purchased sheet would cap a Beyonder at their mortal Will.
     */
    fun maxFor(will: Int, traits: List<Trait>): Int {
        val warded = traits.any { it.name.equals(FORTITUDE, ignoreCase = true) }
        return will + if (warded) FORTITUDE_BONUS else 0
    }

    /** A total held inside 0..[max]; a negative gain cannot dig below zero. */
    fun clamp(cor: Int, max: Int): Int {
        requireWillScore(max)
        return cor.coerceIn(0, max)
    }

    /**
     * Whether [cor] has reached [max] -- the point at which "the character is
     * permanently lost. The GM takes full control" (p. 45).
     *
     * At or past, not merely past: the book's threshold is reaching the
     * maximum, not exceeding it.
     */
    fun isLostAt(cor: Int, max: Int): Boolean {
        requireWillScore(max)
        return cor >= max
    }

    /**
     * Whether gaining [delta] from [cor] under [max] ends the character.
     *
     * Asked twice of one rule: by the session when it charges a gain, and by
     * the screen that prints what a potion will cost BEFORE it is drunk. A
     * [delta] of 0 is a real question -- a potion can lower Will onto a total
     * that was safe under the old ceiling.
     */
    fun isLostAfter(cor: Int, delta: Int, max: Int): Boolean = isLostAt(clamp(cor + delta, max), max)

    /**
     * The rungs of the RITUAL ladder (p. 68) a total of [cor] has reached, in
     * ascending order. Ritual-scoped: see [RitualCorThreshold].
     */
    fun ritualThresholdsReached(
        cor: Int,
        thresholds: List<RitualCorThreshold> = ContentLoader.ritualCorThresholds(),
    ): List<RitualCorThreshold> = thresholds.filter { it.cor <= cor }

    /**
     * The penalty a total of [cor] applies to RITUAL rolls, and to nothing
     * else. There is deliberately no general `penaltyFor`: the book's ladder
     * is filed under ritual work, and a combat roll is not a ritual.
     */
    fun ritualPenalty(
        cor: Int,
        thresholds: List<RitualCorThreshold> = ContentLoader.ritualCorThresholds(),
    ): Int {
        val instability = thresholds.minOfOrNull { it.cor } ?: return 0
        return if (cor >= instability) RITUAL_INSTABILITY_PENALTY else 0
    }

    private fun requireWillScore(max: Int) =
        require(ScoreFloors.holdsCeiling(max)) { "maximum CoR is a Will score; was $max" }
}
