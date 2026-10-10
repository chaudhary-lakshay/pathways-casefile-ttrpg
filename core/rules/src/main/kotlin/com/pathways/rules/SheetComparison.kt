package com.pathways.rules

import java.util.Locale

/**
 * One line of the `computed vs asPublished` diff (spec section 8).
 *
 * [published] is null only when the sheet printed no value for the field. That
 * is the sole case where a row counts as matching without being compared —
 * absence of evidence is not a disagreement.
 */
data class SheetRow(
    val label: String,
    val computed: String,
    val published: String?,
) {
    val matches: Boolean get() = published == null || computed == published
}

/**
 * Compares what the engine derives from a [PremadeSheet] against what that
 * sheet printed.
 *
 * The book is the authority and a disagreement is REPORTED, never repaired:
 * at least 19 of the 23 sheets floor Basic Speed where the book does not, and
 * Docks Bouncer's printed HP contradicts its own ST. Both surface here as
 * non-matching rows rather than being reconciled away.
 *
 * Row order is stable — the seven secondaries first, then one row per
 * purchased skill in sheet order — so a UI can render it without sorting.
 */
object SheetComparison {

    fun of(sheet: PremadeSheet): List<SheetRow> =
        secondaryRows(Derive.of(sheet.toCharacter()).secondaries, sheet.published) +
            sheet.skills.map { skillRow(sheet, it) }

    /**
     * The seven secondary rows for [s] against what a sheet printed. [of]
     * asks it of the unedited sheet, which is the diff. A screen asks it of
     * the character as PLAYED for the number to print beside that diff, and
     * pairs the two by [SheetRow.label], so the list has one order and one
     * set of labels (6fz).
     */
    fun secondaryRows(s: Secondaries, p: PublishedValues): List<SheetRow> = listOf(
        row("HP", s.maxHp, p.hp),
        row("FP", s.maxFp, p.fp),
        row("Will", s.will, p.will),
        row("Per", s.per, p.per),
        SheetRow("Basic Speed", s.basicSpeed, p.basicSpeed?.let(::formatSpeed)),
        row("Basic Move", s.basicMove, p.basicMove),
        row("Dodge", s.dodge, p.dodge),
    )

    /**
     * Levels come from [PremadeSheet.levelFor] rather than out of
     * [DerivedCharacter.skillLevels], which is keyed by `SkillKey`: two entries
     * sharing a key would collapse into one map entry and silently drop a row.
     */
    private fun skillRow(sheet: PremadeSheet, skill: PremadeSkill) =
        row(skill.label, sheet.levelFor(skill), skill.publishedLevel)

    private fun row(label: String, computed: Int, published: Int?) =
        SheetRow(label, computed.toString(), published?.toString())

    /** Mirrors [Secondaries.basicSpeed] so both sides are string-comparable. */
    private fun formatSpeed(speed: Double) = String.format(Locale.ROOT, "%.2f", speed)
}
