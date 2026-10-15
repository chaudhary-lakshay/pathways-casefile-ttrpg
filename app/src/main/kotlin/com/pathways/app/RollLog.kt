package com.pathways.app

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.pathways.rules.RandomSource

/** One throw: whose, which roll, the faces, and the target if it had one. */
internal data class LoggedThrow(
    val character: String,
    val label: String,
    val faces: List<Int>,
    val target: Int?,
) {
    /** "4 + 5 + 6 = 15 vs 11"; a table's or damage's throw has no target. */
    fun line(): String =
        faces.joinToString(" + ") + " = ${faces.sum()}" + target?.let { " vs $it" }.orEmpty()
}

/**
 * Every throw at the table, newest first: what [RandomSource.thrown] reports.
 *
 * Dice and target only, never a verdict: the PCR, a demoted divination and
 * every push decide theirs after the dice, so a verdict read back from here
 * could contradict the dialog that rolled.
 *
 * ponytail: in memory, held by [App], so switching characters keeps it and
 * a restart clears it. Not in the session file, which `SessionStore.load`
 * drops whole on a decode failure. Persist it when a session needs to be
 * replayed after a restart. [cap] is per character.
 */
internal class RollLog(private val source: RandomSource, private val cap: Int = CAP) {
    private val entries = mutableStateListOf<LoggedThrow>()

    /** [source], with each throw logged under [character] and [label]. */
    fun dice(character: String, label: String): RandomSource = object : RandomSource {
        override fun roll(sides: Int): Int = source.roll(sides)
        override fun thrown(faces: List<Int>, target: Int?) {
            entries.add(0, LoggedThrow(character, label, faces, target))
            // Per character: a GM running two PCs keeps both histories.
            if (entries.count { it.character == character } > cap) {
                entries.removeAt(entries.indexOfLast { it.character == character })
            }
        }
    }

    fun of(character: String): List<LoggedThrow> = entries.filter { it.character == character }

    /** Reset to sheet starts the character's log clean too. */
    fun forget(character: String) {
        entries.removeAll { it.character == character }
    }

    private companion object {
        const val CAP = 100
    }
}

/** The selected character's dice: [RollLog.dice] with the name filled in. */
internal fun interface CharacterDice {
    fun dice(label: String): RandomSource
}

/**
 * For the roll sites inside click handlers, which cannot take a parameter
 * from the screen without threading it through every composable between.
 * No default: a roll site outside [App]'s provider is a wiring defect, and
 * it should fail on the first open rather than roll unlogged.
 */
internal val LocalDice = staticCompositionLocalOf<CharacterDice> { error("no CharacterDice provided") }
