package com.pathways.rules

import com.pathways.model.SkillKey
import com.pathways.model.Trait

/**
 * Charmed Object's one skill (book 1055-1058): *"One item you own provides +1
 * to one specific skill when used (specify item and skill). Lost permanently
 * if the item is destroyed."*
 *
 * **The skill travels in the held trait's NAME**, `Charmed Object (Guns
 * (Pistol))`, and the save stores the choice beside the purchase. A name and
 * not a parameter on [TraitClauses.skillRollClauses], because the chips and
 * the dice both take the trait list and nothing else: a second argument is
 * one a caller can leave out (6hc). The head is stripped whole, so a
 * specialization's own bracket is never parsed.
 *
 * **"+1 to one specific skill" is read as +1 to a ROLL of it**, declared by
 * the table per roll for "when used". The skill's level does not move, so a
 * parry at skill/2+3 does not either. The match carries no difficulty: the
 * cell names a skill and no difficulty, so a name the book prints at two
 * (Knife) is charmed at both.
 */
object CharmedObject {
    const val ROW = "Charmed Object"

    /** The cell's applied clause, verbatim. */
    const val CLAUSE = "+1 to one specific skill when used"

    /** The cell's other sentence, which this app cannot apply. */
    const val DESTROYED = "Lost permanently if the item is destroyed"

    /**
     * The rolls built on the chosen skill that do not take the +1. Read
     * after the sheet's "Not applied:", so it leads with what is not reached.
     */
    const val REACH =
        "on a parry, an Acrobatic Dodge, a ritual, a divination or a boon " +
            "built on the chosen skill. A roll of the skill from the Skills " +
            "tab, and an attack with it, offer the +1 as a chip"

    /** A row bought and not yet given its skill: said of the whole cell. */
    const val UNCHOSEN =
        "no skill is chosen for it, so no roll offers its +1: choose one in the builder"

    private const val NO_ITEMS =
        "this app tracks no item to destroy; give the row back in the builder"

    /** The name a holder's trait takes once [skill] is chosen. */
    fun heldAs(skill: String, specialization: String?): String = "$HEAD${label(skill, specialization)})"

    private const val HEAD = "$ROW ("

    private fun label(skill: String, specialization: String?): String =
        if (specialization == null) skill else "$skill ($specialization)"

    /** The chosen skill a held name carries, as printed, or null. */
    fun skillOf(printed: String): String? {
        if (!printed.startsWith(HEAD, ignoreCase = true) || !printed.endsWith(")")) return null
        return printed.substring(HEAD.length, printed.length - 1).takeIf { it.isNotBlank() }
    }

    /** [ROW] for a held name carrying a skill; the bare row resolves itself. */
    internal fun rowOf(printed: String): String? = ROW.takeIf { skillOf(printed) != null }

    /** The row's clause, for [TraitEffects.known] and the clause guard. */
    internal val TEMPLATE = TraitClause(ROW, ROW, CLAUSE, 1, conditional = true)

    /** The chip a roll of [skill] offers: one per held charm naming it. */
    fun clauses(traits: List<Trait>, skill: SkillKey): List<TraitClause> {
        val rolled = label(skill.name, skill.specialization)
        return traits
            .filter { skillOf(it.name).equals(rolled, ignoreCase = true) }
            .map { TEMPLATE.copy(shown = it.name) }
    }

    /**
     * What the sheet says of a held row. With no skill chosen nothing of
     * [cell] is applied, and one note on the whole cell says so, which is
     * what [TraitReport.applied] reads as the table's.
     */
    internal fun notes(shown: String, printed: String, cell: String): List<TraitNote> {
        if (skillOf(printed) == null) return listOf(TraitNote(shown, cell, UNCHOSEN))
        return listOf(TraitNote(shown, CLAUSE, REACH), TraitNote(shown, DESTROYED, NO_ITEMS))
    }
}
