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
 * What the GM says is behind "Darkness or heavy fog" (Ch. 5, p. 39).
 *
 * The book prices two causes with ONE number, and Ch. 3's Night Eyes covers
 * only one of them: "Reduce all **darkness** penalties by 2; in dim gaslight
 * or moonlight suffer no penalty at all." Its examples are light levels and it
 * never writes fog -- eyes that see in the dark do not see through it.
 *
 * **Three states because the source produces three outcomes**, which is the
 * only reason to draw a distinction the combat table does not: fog is
 * untouched, darkness is relieved by 2, and the two named lightings are
 * cancelled outright. It is a DECLARATION, like the surprise refusals (6bg)
 * and a cancelled aim (6bm) -- the GM knows what the scene is lit by and this
 * app cannot compute it.
 *
 * Nothing is offered to a character without the trait, and nothing is refused
 * to one: the row itself stays universal, so an ordinary shooter still takes
 * the book's penalty in fog and dark alike (a design review's blocking
 * finding, before any of this was written).
 */
enum class DarknessCause {
    /** The row's second cause, which Night Eyes does not cover. */
    HEAVY_FOG,

    /** The trait's general clause: 2 comes off. */
    DARKNESS,

    /** "in dim gaslight or moonlight suffer no penalty at all." */
    DIM_LIGHT,
}

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
     * The rows of this table that also print a number for REACH.
     *
     * Exactly one today: *"Target is prone: -2 ranged (but -4 in melee)"*. The
     * scope is the data's own -- a row is in reach exactly when it carries a
     * melee value -- rather than a list of names, so a re-extraction cannot
     * leave this holding a string nothing matches (the shape the aim rows and
     * the darkness row already take).
     *
     * **Checked, not assumed.** The table is global lines 2665-2685 and the
     * word "melee" appears on exactly one of them, 2681. The other eight rows
     * are distance, cover, movement and light: priced for a shot and for
     * nothing else. No other page prices these conditions in reach either, so
     * one row is the whole scope, and a second one appearing here would be a
     * finding rather than a widening ([RangedTest] pins the list whole).
     *
     * **The direction, because one file had it backwards.** Every row of this
     * table modifies the attacker's to-hit roll, so -4 in reach is the HARDER
     * case and -2 at distance the gentler one -- a prone target is no easier
     * to hit with a fist than with a rifle. `tools/extract_ranged.py` said the
     * opposite in prose until 6cn, and nothing could contradict it while the
     * melee value had no reader.
     *
     * This is the ATTACKER's to-hit roll against a target who is down. It is
     * not the same state as the defender's own prone, which the Dive! chip
     * hands out on the defence screen: no page in this book prices a prone
     * character's own defences, which is why that chip lowers nothing.
     */
    fun meleeConditions(): List<RangedModifier> = conditions().filter { it.melee != null }

    /**
     * Each chosen row of [conditions] as a modifier on a MELEE attack.
     *
     * Separate from [modifiers] rather than a flag on it, because everything
     * else that function takes belongs to a shot -- a band, a weapon's Acc, an
     * aim that was cancelled, what the scene is lit by. A melee attack has one
     * question to answer and this is it.
     *
     * A row printing no melee value is REFUSED, never quietly given its ranged
     * number: -2 on a kick would have the sign right and the rule wrong, which
     * is the exact defect keeping the two values apart prevents. Nothing
     * reachable can build that call -- the screen offers only
     * [meleeConditions] -- which is why the guard is here (6l).
     */
    fun meleeModifiers(conditions: List<RangedModifier>): List<Modifier> =
        conditions.mapNotNull { row ->
            val value = requireNotNull(row.melee) {
                "'${row.condition}' prints ${row.printed}, which is a ranged number only"
            }
            situational(row.condition.lowercase(), value, row.condition)
        }

    /** The maneuver row that grants the aim, and says what takes it away. */
    private const val AIM_ROW = "Aim (Ranged)"

    /**
     * The Aim maneuver's own cell, quoted whole (Ch. 5, p. 37).
     *
     * The two Acc rows live in the ranged modifiers table; the sentence that
     * CANCELS them is printed one table away, on the maneuver itself --
     * "Canceled by any Active Defense or injury." Nothing read it, so an aim
     * survived a parry and a bullet, and a rifleman who defended between
     * taking aim and firing still collected the full Acc.
     *
     * The two printings agree where they overlap ("max ACC+2" here, "max Acc
     * +2 total" there), which is what licenses reading this cell as the same
     * aim rather than a rule about a different one -- the per-table standard
     * 6ad set, pinned in both languages.
     */
    fun printedAim(): String =
        ContentLoader.maneuvers().first { it.maneuver == AIM_ROW }.description

    /**
     * The condition rows offered when the GM says the aim was [cancelled].
     *
     * A REFUSAL, not a penalty: the book takes the aim away rather than
     * pricing it, so the rows go and the sentence is shown in their place --
     * the call a Broken mystical tongue (6x) and an unarmed parry against a
     * shot (6bj) each needed. The decision lives here rather than in the
     * screen's filter, because a dialog holding its own opinion about a
     * printed refusal is the two-copies failure (6ag, 6ai, 6be).
     *
     * Whether the shooter defended or was hurt since aiming is a DECLARATION:
     * this app holds no turn to remember an aim across, and the GM does. Same
     * reading as the three defence refusals (6bg).
     */
    fun offeredConditions(cancelled: Boolean): List<RangedModifier> =
        if (cancelled) conditions().filterNot { it.weaponAcc } else conditions()

    /**
     * The rows that cannot stand beside [condition]: the other Aim row, or
     * the target's other speeds. Empty for a row that is a fact of its own.
     * [modifiers] refuses the same pairs, and a screen swaps on this.
     *
     * **The speeds are a reading, not a printed sentence.** The book says
     * nothing about combining rows (lines 2665-2685). "Target is stationary",
     * "up to 3 meters/sec" and "4+ meters/sec" are bounds on one quantity
     * that do not meet, so no target is in two of them, and walking with
     * running is a -3 no speed produces. That is why the range band is one
     * pick: a target stands at one distance. It is not Fit beside Very Fit,
     * two traits a character can hold at once, which this app leaves
     * stackable because the book prints no exclusivity for them (6fk).
     * tools/extract_ranged.py marks the three rows from their own words and
     * fails if the bounds ever meet.
     */
    fun alternativesTo(condition: String): Set<String> {
        val row = conditions().firstOrNull { it.condition == condition } ?: return emptySet()
        return conditions()
            .filter { it.condition != condition }
            .filter { (row.weaponAcc && it.weaponAcc) || (row.targetSpeed && it.targetSpeed) }
            .map { it.condition }
            .toSet()
    }

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
     * [weaponAcc] is the Acc of the weapon being fired, for the two Aim rows.
     * It used to be refused outright, on the grounds that this book printed no
     * weapon table -- it does, in chapter 8, and the row's own cell said so
     * ("see weapon table"). Still refused when no weapon was chosen: applying
     * zero is not the same as having no value, which is the reading this file
     * already takes for the darkness row.
     */
    fun modifiers(
        band: RangePenalty? = null,
        conditions: List<RangedModifier> = emptyList(),
        gmValue: Map<String, Int> = emptyMap(),
        weaponAcc: Int? = null,
        aimCancelled: Boolean = false,
        darknessCause: DarknessCause? = null,
        darknessReduction: Int = 0,
    ): List<Modifier> = buildList {
        // The aim is GONE, not reduced, so a plan still carrying it is refused
        // rather than quietly stripped: 1 FP or a turn was spent on it, and a
        // caller told nothing would believe the Acc was counted. The screen
        // withdraws the rows through [offeredConditions], so nothing reachable
        // can build this plan -- which is why the guard is here (6l).
        require(!aimCancelled || conditions.none { it.weaponAcc }) {
            "the aim was cancelled: " + printedAim()
        }
        // "Aim last turn" and "Aim for 3+ turns" are the same aim, held for
        // different lengths. Both at once would pay the Acc twice and break
        // the second row's own printed ceiling ("max Acc +2 total").
        require(conditions.count { it.weaponAcc } <= 1) {
            "a shot is aimed for one length of time; " +
                conditions.filter { it.weaponAcc }.joinToString { it.condition } +
                " cannot both apply"
        }
        // One quantity in three rows; see [alternativesTo] for the reading.
        require(conditions.count { it.targetSpeed } <= 1) {
            "a target moves at one speed; " +
                conditions.filter { it.targetSpeed }.joinToString { it.condition } +
                " cannot both apply"
        }
        if (band != null) {
            situational("range", band.penalty, band.printed)?.let(::add)
        }
        conditions.forEach { row ->
            val value = if (row.weaponAcc) {
                requireNotNull(weaponAcc) {
                    "'${row.condition}' is worth the weapon's Acc, and no weapon was chosen"
                } + row.accBonus
            } else {
                gmValue[row.condition] ?: row.modifier
                    ?: error("'${row.condition}' prints no modifier and none was given")
            }
            if (row.worstCase != null) {
                // The printed range runs from the gentle end to the harsh one,
                // both negative, so the harsh end is the LOWER bound.
                require(value in row.worstCase..(row.modifier ?: value)) {
                    "'${row.condition}' prints ${row.printed}; $value is outside it"
                }
            }
            // AFTER the bounds check, never through it: the GM's severity is
            // the book's and stays inside the printed range, while the trait
            // moves the result outside it. Relieving first and validating
            // second would refuse -1 as "gentler than the book's best" and
            // throw on Roll -- a chip that throws is not a choice (6t).
            val relief = reliefFor(row, value, darknessCause, darknessReduction)
            situational(
                row.condition.lowercase(),
                value + relief,
                // The chip reads "-3 to -9" and the contribution would read -1
                // with nothing joining them, which is the breakdown that does
                // not add up (6ag, 6ai). The rules author the sentence.
                if (relief != 0) {
                    "${row.condition} — ${TraitEffects.NIGHT_EYES} takes off $relief"
                } else {
                    row.condition
                },
            )?.let(::add)
        }
    }

    /**
     * How much of [value] Night Eyes takes off, given what the GM says the
     * scene is.
     *
     * Only the one row that prices darkness can be relieved, found by its own
     * `worstCase` range rather than by its name -- a re-extraction that
     * reworded the condition would otherwise leave this holding a string
     * nothing matches, silently withdrawing the trait.
     *
     * The two clauses are NOT one rule stated twice. Subtracting 2 from the
     * gentlest printed severity gives -1 and the second clause says 0, so it
     * is a state of its own and specific beats general. And the relief floors
     * at zero: -9 + 2 is -7, so nothing printed can reach it today, but a
     * "reduction" that overshot into a bonus is the sign class 4.3 exists for,
     * guarded before a caller finds it (6l).
     */
    private fun reliefFor(
        row: RangedModifier,
        value: Int,
        cause: DarknessCause?,
        reduction: Int,
    ): Int {
        if (row.worstCase == null || reduction == 0) return 0
        return when (cause) {
            null, DarknessCause.HEAVY_FOG -> 0
            DarknessCause.DARKNESS -> minOf(reduction, -value)
            DarknessCause.DIM_LIGHT -> -value
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
