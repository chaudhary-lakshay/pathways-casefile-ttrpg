package com.pathways.model

import kotlinx.serialization.Serializable

/**
 * The boon a character holds (Ch. 13-15): the patron's pathway and the
 * Sequence granted. The CHOICE only, as [BeyonderState] is -- every gain is
 * derived from it, so nothing free reaches the point budget.
 *
 * Its own type, not a [BeyonderState]: the book prints no digestion and no
 * Potion Consumption Roll for a boon, and its ladder runs past the potion
 * content's Sequence 6. Held BESIDE a potion walk, on the owner's ruling
 * (2026-10-13).
 *
 * No `init` check: a decode that throws discards the whole save, so a name
 * or rung the book does not print is read as granting nothing instead
 * (`NonStandardPathways.chainFor`).
 */
@Serializable
data class BoonState(val pathway: String, val sequence: Int)
