package com.pathways.rules

import com.pathways.model.Layer
import com.pathways.model.ModEffect
import com.pathways.model.ModTarget
import com.pathways.model.Modifier
import com.pathways.model.ModifierId
import com.pathways.model.ModifierSource
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.RangePenalty
import com.pathways.rules.content.RangedModifier

/**
 * What distance and conditions do to a shot (Ch. 5, p. 39-40).
 *
 * The third of the three modifier sources, beside [Conditions] (what the
 * character is carrying) and [Attacks] (what the player chose to do). This one
 * is the situation: how far away the target is, whether it is moving, whether
 * anyone can see.
 *
 * Everything here is [Layer.SITUATIONAL], like [Attacks] and unlike
 * [Conditions] -- a shot into fog is circumstance, not a threshold state.
 */
object Ranged {

    /** The distance bands, nearest first. */
    fun bands(): List<RangePenalty> = ContentLoader.rangePenalties()

    /** The nine Other Ranged Modifiers rows, as printed. */
    fun conditions(): List<RangedModifier> = ContentLoader.rangedModifiers()

    /**
     * The band [meters] falls in.
     *
     * **The book's bands overlap.** "51-100 meters" is -5 and "100+ meters" is
     * -6, so a target at exactly 100 metres matches both. The NEARER band
     * wins: the bands are walked in ascending order and the first match is
     * taken, so 100 metres is -5. That is the gentler of the two, and it is
     * also the only reading under which the closed band means what it says --
     * if the open band won at its own lower bound, "51-100" would silently
     * stop at 99. The overlap is the book's own arithmetic and is recorded,
     * never repaired: tools/extract_ranged.py's verify() fails if it goes.
     */
    fun bandFor(meters: Int): RangePenalty {
        require(meters >= 0) { "a distance cannot be negative: $meters" }
        return bands().firstOrNull { meters >= it.from && (it.to == null || meters <= it.to) }
            ?: error("no range band covers $meters metres")
    }

    /** The to-hit penalty at [meters]. */
    fun penaltyFor(meters: Int): Int = bandFor(meters).penalty

    /**
     * [band] and each chosen row of [conditions] as modifiers on a shot.
     *
     * A BAND, not a distance in metres, because the two are not
     * interchangeable at the overlap: "100+ meters" begins at 100 and
     * [bandFor] resolves 100 to "51-100". A caller that stored a chosen band
     * and passed back its lower bound would display -6 and roll -5 (Codex).
     * A caller holding metres calls [bandFor] itself and gets one answer.
     *
     * [gmValue] overrides a row's own modifier, for the one row that prints a
     * range rather than a number ("-3 to -9 depending on severity"). Rolling
     * the gentle end silently would turn the GM's harsher call into a softer
     * one -- the same reading the fright triggers settled.
     *
     * A row needing a weapon's Acc is refused rather than applied as zero:
     * this book prints no weapon table, so the number does not exist. Passing
     * one is a caller bug, not a shrug.
     */
    fun modifiers(
        band: RangePenalty? = null,
        conditions: List<RangedModifier> = emptyList(),
        gmValue: Map<String, Int> = emptyMap(),
    ): List<Modifier> = buildList {
        if (band != null) {
            situational("range", band.penalty, band.printed)?.let(::add)
        }
        conditions.forEach { row ->
            require(!row.weaponAcc) {
                "'${row.condition}' is worth the weapon's Acc, and this book prints no weapon table"
            }
            val value = gmValue[row.condition] ?: row.modifier
                ?: error("'${row.condition}' prints no modifier and none was given")
            if (row.worstCase != null) {
                // The printed range runs from the gentle end to the harsh one,
                // both negative, so the harsh end is the LOWER bound.
                require(value in row.worstCase..(row.modifier ?: value)) {
                    "'${row.condition}' prints ${row.printed}; $value is outside it"
                }
            }
            situational(row.condition.lowercase(), value, row.condition)?.let(::add)
        }
    }

    private fun situational(id: String, value: Int, description: String): Modifier? {
        if (value == 0) return null
        return Modifier(
            id = ModifierId("ranged:$id"),
            source = ModifierSource.Situational(description),
            // Retargeted onto the skill being rolled by SkillRoll.
            target = ModTarget.AllRolls,
            effect = ModEffect.Flat(value),
            layer = Layer.SITUATIONAL,
        )
    }
}
