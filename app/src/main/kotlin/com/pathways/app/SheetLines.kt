package com.pathways.app

import com.pathways.rules.PremadeSheet
import com.pathways.rules.Secondaries
import com.pathways.rules.SelfControl
import com.pathways.rules.SelfControlCheck
import com.pathways.rules.SheetComparison
import com.pathways.rules.SheetRow

/**
 * One line of the sheet screen's stat list: a secondary on Vitals or a
 * printed skill on Skills.
 *
 * Two differences meet on such a line and they are not the same kind (6fz):
 *
 *  - **The sheet against the engine**, the spec's `computed vs asPublished`
 *    diff. It is the UNEDITED sheet's, it is what the Vitals tab counts, and it
 *    is the only thing marked, red on a skill row and the accent on the
 *    Vitals card: [disagreement].
 *  - **The character played against the sheet**, which a potion or a builder
 *    edit makes. That is not a disagreement with the book, so it is never
 *    red: [number] simply is the played value.
 *
 * Until 6fz the line printed the engine's number for the unedited sheet, so
 * a Docks Bouncer on Twilight Giant 9 read "HP 11 (sheet: 9)" under a pool
 * of 14 / 14, Brawling 12 beside a Roll that rolled 16, and a Basic Speed,
 * Move and Dodge the character no longer had, with the played ones on no
 * screen.
 */
internal data class PlayedLine(
    val label: String,
    /** What the character plays: the number a roll or a pool beside it uses. */
    val number: String,
    /**
     * The engine's number for the unedited sheet, shown only where the
     * character has moved off it AND the sheet disputes it: the figure
     * [disagreement] is about is otherwise not on the line at all.
     */
    val basis: String?,
    /** The diff, worded as it always was: " (sheet: 9)". Null where they agree. */
    val disagreement: String?,
)

/** [row] of the diff as a line for a character who plays [played] there. */
internal fun playedLine(row: SheetRow, played: String): PlayedLine {
    val disagreement = if (row.matches) null else " (sheet: ${row.published})"
    val basis = " \u00b7 ${row.computed} from the sheet's points"
        .takeIf { disagreement != null && played != row.computed }
    return PlayedLine(row.label, played, basis, disagreement)
}

/**
 * The seven secondary lines of [sheet] for a character whose secondaries are
 * [played], each beside its own row of [rows]. Paired by label, never by
 * position: the two lists come from one function, and a label is what says
 * which row a number belongs to.
 *
 * [compared] is false for an own character: the sheet it was copied from is
 * where it started, not a book it can disagree with, so no line carries a
 * note (play session 2).
 */
internal fun vitalLines(
    sheet: PremadeSheet,
    rows: List<SheetRow>,
    played: Secondaries,
    compared: Boolean = true,
): List<PlayedLine> =
    SheetComparison.secondaryRows(played, sheet.published)
        .map { now ->
            val line = playedLine(rows.first { it.label == now.label }, now.computed)
            if (compared) line else line.copy(basis = null, disagreement = null)
        }

/**
 * What each self-control roll stands at before the GM has said anything, by
 * the check's trait: the book's number, with [state] on it -- the shock and
 * the reeling or fatigue a bare attribute roll takes.
 *
 * ONE number for two readers. The Traits tab prints it on the row and the
 * dialog that row opens starts from it, adding only what is asked there (a
 * declared clause, the GM's stepper). Until 6ga the row computed its own
 * without [state], so a reeling character read "3d6 ≤ 12" on the row and
 * "3d6 ≤ 11" in the dialog it opened.
 */
internal fun standingTargets(checks: Collection<SelfControlCheck>, will: Int, iq: Int, state: Int): Map<String, Int> =
    checks.associate { it.trait to SelfControl.targetFor(it.spec, will, iq, state) }
