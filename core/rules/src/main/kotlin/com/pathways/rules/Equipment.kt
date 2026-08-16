package com.pathways.rules

import com.pathways.model.SkillKey
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.EquipmentItem

/**
 * Chapter 8's Common Equipment table (p. 81-83), as reference beside a roll.
 *
 * SHOWN, NEVER APPLIED
 * --------------------
 * Forty-odd of the 77 rows carry a mechanical clause, and none of them
 * reaches the dice. Nothing in this app records possession: `Character` has
 * no inventory and the 23 fixtures carry no equipment key at all, so a chip
 * that adds "+1 to First Aid" would be the screen asserting the character
 * owns a first aid kit. A design review refused exactly that, on three counts
 * that are worth keeping written down because each survives the others:
 *
 *  - **Ownership.** The one control on the roll dialog that turns a
 *    possession into a modifier, the social clause row, is gated twice --
 *    on the skill's category AND on a list of the traits this character
 *    actually carries. An ungated row has no such list to consult.
 *  - **Stacking.** The table prints Disguise kit (basic) at +1 and Disguise
 *    kit (advanced) at +2, and two cameras at -2 and +1. Selectable together
 *    they read +3 and -1, and no printed field says the two rows are
 *    alternatives; deciding they are would be inventing a taxonomy the book
 *    does not print.
 *  - **Direction.** "Dark clothing: -1 to spot at night" penalises whoever is
 *    looking FOR the wearer. Applied to the wearer's own roll it inverts, the
 *    defect class [ModifierResolver] already measured once.
 *
 * So the printed sentence is put in front of the GM and left there. That is
 * the standing this dialog already gives Occultism's conditional push: naming
 * a rule the engine cannot test beats both granting it and hiding it.
 *
 * WHAT IS DELIBERATELY NOT READ AT ALL
 * ------------------------------------
 * [UNMODELLED] records the rest of the chapter, in production source rather
 * than in a journal, so that a sweep over Kotlin can find the reasons -- the
 * standard [ActiveDefenses.UNPAYABLE] and [Divination.UNAPPLIED] set.
 */
object Equipment {

    /**
     * The parts of chapter 8 nothing here reads, and why. Each would be a
     * loader with no caller: extracted, tested, and consumed by nothing.
     */
    val UNMODELLED = mapOf(
        "Armour" to
            "the three rows price DR, and nothing in this app models DR — " +
            "Damage's own KDoc puts it on the target's side of the hit, " +
            "outside what this engine computes",
        "Currency" to
            "both the Loen three-tier system and the Feysac decimal one, with " +
            "their exchange rate, convert a quantity nothing here holds: a " +
            "wealth level's starting money is a printed STRING and no " +
            "arithmetic anywhere touches money",
        "Legal Licenses & Permits" to
            "a licence is bought with points OR with pounds, which is a " +
            "character-creation question, and this table reaches no roll",
        "Hired Allies & Services" to
            "a rented NPC is a different subject from an owned item, and its " +
            "column prints the ally's own skill LEVELS rather than a bonus to " +
            "the hirer's",
    )

    /** Every row, in printed order. */
    fun all(): List<EquipmentItem> = ContentLoader.equipment()

    /**
     * The rows whose notes name [key] in a signed clause.
     *
     * Matched on the skill's full identity, with one exception the CATALOGUE
     * draws rather than this function:
     *
     *  - `Navigation` is `specializable = false` and the book prints three
     *    separate rows for it (bare, Sea, Land). Those are different skills,
     *    so the compass's "Navigation (Land) +1" must not reach a character
     *    rolling the bare row.
     *  - `Survival` is `specializable = true` with ONE printed row and the
     *    note "specify terrain". `Survival (Woodlands)` is an INSTANCE of
     *    that row, not a rival to it, so the bedroll's bare "+1 to Survival"
     *    reaches it. Hunter-Tracker carries exactly that instance, and
     *    without this the one character in the corpus who would use the
     *    bedroll is the one who cannot see it.
     *
     * So a bare clause reaches any instance of a template skill, and a
     * clause that names a specialization still matches only that one.
     */
    fun forSkill(key: SkillKey): List<EquipmentItem> {
        val instanceOfTemplate = key.specialization != null && isTemplate(key.name)
        return all().filter { item ->
            item.skills.any {
                it.name == key.name && (
                    it.specialization == key.specialization ||
                        (it.specialization == null && instanceOfTemplate)
                    )
            }
        }
    }

    /**
     * Whether the catalogue prints [name] as one row the player specializes,
     * rather than as several rows it enumerates itself.
     */
    private fun isTemplate(name: String): Boolean = ContentLoader.skills().any {
        it.name == name && it.specialization == null && it.specializable
    }

    /**
     * What the roll dialog prints: the item, its price, and the book's own
     * sentence, unedited. No number is pulled out of the sentence, because
     * nothing may add one.
     */
    fun referenceFor(key: SkillKey): List<String> =
        forSkill(key).map { "${it.name} (${it.cost}) — ${it.notes}" }
}
