package com.pathways.app

import com.pathways.rules.PremadeSheet

/** The fixture's own name for the one sheet that is a starting point. */
internal const val BLANK_SHEET = "LOTM Blank Character Sheet"

/**
 * The player's own characters: each save entry with a [SavedSession.basedOn],
 * as that premade under the entry's name. One whose premade is no longer
 * printed is left out rather than guessed at; its entry stays in the file.
 * An entry under a premade's own name is that premade's, never a second sheet.
 */
internal fun ownSheets(premades: List<PremadeSheet>, saved: Map<String, SavedSession>): List<PremadeSheet> {
    val byName = premades.associateBy { it.name }
    return saved.filterKeys { it !in byName }
        .mapNotNull { (name, entry) -> entry.basedOn?.let(byName::get)?.copy(name = name) }
}

/**
 * Every name a new character may not take: the gallery's, and every save
 * entry's, since one whose base is gone is not in the gallery and a new
 * character under its name would overwrite it (the diff review).
 */
internal fun takenNames(sheets: List<PremadeSheet>, saved: Map<String, SavedSession>): Set<String> =
    sheets.map { it.name }.toSet() + saved.keys

/** Why [name] cannot be a new character, or null. Names key the save file. */
internal fun newCharacterRefusal(name: String, taken: Set<String>): String? = when {
    name.isBlank() -> "Name the character."
    taken.any { it.equals(name.trim(), ignoreCase = true) } -> "That name is taken."
    else -> null
}

/**
 * The blank sheet is a starting point, not a character: it has no attributes
 * worth scanning and no traits, and offering it among the people is what made
 * the first gallery show a row with "0 skills" as though someone had rolled it.
 * Matched on the fixture's exact name, so an own character called "Blank
 * Slate" is a person.
 */
internal fun isTemplate(sheet: PremadeSheet): Boolean = sheet.name == BLANK_SHEET

/** Back to the printed sheet; an own character stays one. */
internal fun SavedSession.cleared(): SavedSession = SavedSession(basedOn = basedOn)
