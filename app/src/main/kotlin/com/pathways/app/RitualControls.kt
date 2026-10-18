package com.pathways.app

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.rules.Languages
import com.pathways.rules.PowerSources
import com.pathways.rules.RitualMode
import com.pathways.rules.RitualPacing
import com.pathways.rules.content.LanguageProficiency
import com.pathways.rules.content.RitualComplexity
import com.pathways.rules.content.SacrificeTier

/**
 * The fuel amounts to offer for [complexity].
 *
 * A closed cell ("2-3 SPI") offers exactly what it prints. An open one
 * ("11-15+") offers three above its printed ceiling: the book names no top,
 * and a stepper that could run to a hundred is not a choice a GM needs made
 * for them at the table.
 * ponytail: widen it if a GM asks.
 *
 * Read off the COMPLEXITY, not the weight. The weight table prints its own
 * cost column and the book's own worked rituals fall outside it three times
 * in six -- see RitualComplexity.
 */
private fun spiChoices(complexity: RitualComplexity): List<Int> {
    val top = complexity.spiMax ?: complexity.spi
    return (complexity.spi..(if (complexity.spiOpen) top + OPEN_END_STEPS else top)).toList()
}

/**
 * The HP sacrifices to offer against a [cost], as whole SPI bought.
 *
 * Stepped in SPI-sized pairs because that is the only granularity the book
 * sells: "every 2 HP spent counts as 1 SPI", so an odd point buys nothing and
 * a chip offering one would be a chip for wasting a hit point.
 * ponytail: four steps covers the rungs a caster survives.
 */
internal fun hpChoices(cost: Int): List<Int> =
    (0..minOf(cost, 4)).map { PowerSources.hpFor(it) }

/**
 * The GM's pick inside one cell, when the cell prints more than a number.
 *
 * "The GM determines the Preparation Modifier from the player's declarations"
 * (p. 63), and some cells of chapter 7's three tables hand them a span or a
 * floor to determine it from -- `ModifierRangeTest` holds the list. A cell that
 * prints one figure draws it as one disabled chip: drawing nothing there made
 * a span pick grow the dialog under the finger (play session 4, 6gn).
 *
 * [applied] is what the ENGINE says the roll is using, not what was tapped:
 * the chip showing selected before anything is tapped is a claim about the
 * default, and there is one authority on that (6dz).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CellValueChips(printed: String, values: List<Int>, applied: Int, onPick: (Int) -> Unit) {
    val isSpan = values.size >= 2
    Text(
        if (isSpan) "The book prints $printed — the GM picks:" else "The book prints $printed.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp),
    )
    FlowRow {
        (if (isSpan) values else listOf(applied)).forEach { value ->
            CaseChip(
                selected = applied == value,
                enabled = isSpan,
                onClick = { if (isSpan) onPick(value) },
                label = { Text(signed(value)) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

/**
 * Ch. 7 IV's declarations, from Complexity to Suspended: a view of
 * [RitualDialog]'s state, which it owns. Every setter here takes an absolute
 * value; the one toggle, [onToggleSuspended], is defined by the dialog so it
 * reads the state live (split step 8a).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RitualPowerSourceChips(
    complexity: RitualComplexity,
    spiChoice: Int,
    cost: Int,
    hpSacrificed: Int,
    pacing: RitualPacing,
    assistants: Int,
    sacrifice: SacrificeTier?,
    sentientBeings: Int,
    catalyst: Int,
    suspended: Boolean,
    /** False once the fuel is spent: complexity, SPI and HP are what it paid. */
    fuelEnabled: Boolean,
    onComplexity: (RitualComplexity) -> Unit,
    onSpiChoice: (Int) -> Unit,
    onHpSacrificed: (Int) -> Unit,
    onPacing: (RitualPacing) -> Unit,
    onAssistants: (Int) -> Unit,
    onSacrifice: (SacrificeTier?) -> Unit,
    onSentientBeings: (Int) -> Unit,
    onCatalyst: (Int) -> Unit,
    onToggleSuspended: () -> Unit,
) {
    // Ch. 7 IV.A. A SECOND axis, not a restatement of the
    // weight above: the weight is the category and gives the
    // modifier, this is the scope and gives the cost and the
    // time. The book prints a Major rung here and none in the
    // weight table, which is why Major has no base modifier.
    SectionLabel("Complexity")
    FlowRow {
        PowerSources.complexities().forEach { option ->
            CaseChip(
                selected = option.complexity == complexity.complexity,
                enabled = fuelEnabled,
                onClick = { onComplexity(option) },
                label = { Text(option.complexity) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }

    // Every cell is a range, so the end is the GM's; the open
    // one ("11-15+") offers three above its printed ceiling
    // rather than pretending to know the top.
    SectionLabel("Fuel")
    FlowRow {
        spiChoices(complexity).forEach { option ->
            CaseChip(
                selected = option == spiChoice,
                enabled = fuelEnabled,
                onClick = { onSpiChoice(option) },
                label = { Text("$option SPI") },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }

    // Ch. 7 IV.B. Fuel, never a bonus -- the panel says so
    // out loud, because the sentence next to it in the book
    // is the one that gets misread.
    SectionLabel("Paid in HP (${PowerSources.HP_PER_SPI} HP = 1 SPI, no bonus to the roll)")
    FlowRow {
        hpChoices(cost).forEach { option ->
            CaseChip(
                selected = option == hpSacrificed,
                enabled = fuelEnabled,
                onClick = { onHpSacrificed(option) },
                label = { Text(if (option == 0) "None" else "$option HP") },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }

    // The Time & Complexity table, whose Rushed and Extended
    // values differ BY RUNG -- -3 at Light, -5 at Major -- so
    // they are read off the complexity chosen above.
    SectionLabel("Pacing")
    FlowRow {
        RitualPacing.entries.forEach { option ->
            val shift = PowerSources.pacingModifier(complexity, option)
            CaseChip(
                selected = option == pacing,
                onClick = { onPacing(option) },
                label = {
                    Text(
                        when (option) {
                            RitualPacing.RUSHED -> "Rushed ${signed(shift)}"
                            RitualPacing.NORMAL -> complexity.baseTime
                            RitualPacing.EXTENDED -> "Extended ${signed(shift)}"
                        },
                    )
                },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }

    // Ch. 7 IV.C. Every assistant is worth +1 -- the book's
    // own Ritual 3 prints "Two trained assistants  +2", which
    // is the only thing that disambiguates "beyond the first".
    SectionLabel("Assistants")
    FlowRow {
        (0..4).forEach { option ->
            CaseChip(
                selected = option == assistants,
                onClick = { onAssistants(option) },
                label = {
                    Text(
                        if (option == 0) "None"
                        else "$option ${signed(PowerSources.participantBonus(option))}",
                    )
                },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
    if (assistants > PowerSources.PARTICIPANT_CAP) {
        Text(
            "Capped at ${signed(PowerSources.PARTICIPANT_CAP)}; " +
                "the rest still pool their SPI.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    // Ch. 7 IV.D.
    SectionLabel("External sacrifice")
    FlowRow {
        CaseChip(
            selected = sacrifice == null,
            onClick = { onSacrifice(null) },
            label = { Text("None") },
            modifier = Modifier.padding(end = 8.dp),
        )
        PowerSources.sacrificeTiers().forEach { option ->
            CaseChip(
                selected = sacrifice?.tier == option.tier,
                onClick = { onSacrifice(option) },
                label = { Text("${option.tier} ${signed(option.modifier)}") },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
    sacrifice?.let { tier ->
        // Offered only alongside a tier, because the per-being
        // bonus rides on one and sacrificeBonus refuses it
        // without. It ADDS to the tier rather than replacing
        // it -- two consecutive sentences, both applied.
        SectionLabel("Sentient beings offered (${signed(PowerSources.SENTIENT_BONUS)} each, on top)")
        FlowRow {
            (0..3).forEach { option ->
                CaseChip(
                    selected = option == sentientBeings,
                    onClick = { onSentientBeings(option) },
                    label = { Text(if (option == 0) "None" else "$option") },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }

    // Ch. 7 IV.E. A range the GM picks inside, like darkness.
    SectionLabel("Catalyst")
    FlowRow {
        (PowerSources.CATALYST_LOWEST..PowerSources.CATALYST_HIGHEST)
            .forEach { option ->
                CaseChip(
                    selected = option == catalyst,
                    onClick = { onCatalyst(option) },
                    label = {
                        Text(if (option == 0) "None" else signed(option))
                    },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
    }

    // p. 65, above the Time table. Not a power source, but the
    // same declaration: it moves the effective skill.
    CaseChip(
        selected = suspended,
        onClick = onToggleSuspended,
        label = {
            Text("Suspended part-way ${signed(PowerSources.SUSPENSION_PENALTY)}")
        },
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * Section II's supporting skills and Ch. 3's mystical language, as a view of
 * [RitualDialog]'s state. [onToggleSupport] is defined by the dialog so it
 * reads `support` live (split step 8a).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RitualSupportAndLanguage(
    ownedSupportSkills: List<String>,
    support: List<String>,
    language: LanguageProficiency?,
    boughtLanguages: List<String>,
    grantedLanguages: List<String>,
    onToggleSupport: (String) -> Unit,
    onLanguage: (LanguageProficiency?) -> Unit,
) {
    // Section II, and the one part of this chapter that is
    // scoped to neither mode. A skill the character does not
    // own is not offered, so the list is empty for most
    // casters -- said out loud rather than shown as a gap.
    SectionLabel("Supporting skills")
    if (ownedSupportSkills.isEmpty()) {
        Text(
            "This character has none of the book's " +
                "${RitualMode.supportSkills().size} supporting skills " +
                "(${RitualMode.supportSkills().joinToString { it.skill }}).",
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        Text(
            "+1 each, capped at +${RitualMode.SUPPORT_CAP} in total.",
            style = MaterialTheme.typography.bodySmall,
        )
        FlowRow {
            ownedSupportSkills.forEach { name ->
                CaseChip(
                    selected = name in support,
                    onClick = { onToggleSupport(name) },
                    label = { Text(name) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }

    MysticalLanguagePicker(language, boughtLanguages, grantedLanguages, onLanguage)
}

/** The tongue a ritual or a prayer is said in: the GM's declaration. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MysticalLanguagePicker(
    language: LanguageProficiency?,
    boughtLanguages: List<String>,
    grantedLanguages: List<String>,
    onLanguage: (LanguageProficiency?) -> Unit,
) {
    // Ch. 3, p. 14. Only the MYSTICAL table speaks about
    // rituals; a common tongue is not offered, because the
    // book never grants it ritual power and offering it would
    // read as permission.
    SectionLabel("Mystical language")
    FlowRow {
        CaseChip(
            selected = language == null,
            onClick = { onLanguage(null) },
            label = { Text("Not stated") },
            modifier = Modifier.padding(end = 8.dp),
        )
        Languages.proficiencies(Languages.MYSTICAL).forEach { option ->
            CaseChip(
                selected = language?.proficiency == option.proficiency,
                onClick = { onLanguage(option) },
                label = { Text(option.proficiency) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
    if (boughtLanguages.isNotEmpty() || grantedLanguages.isNotEmpty()) {
        Text(
            listOfNotNull(
                boughtLanguages.takeIf { it.isNotEmpty() }
                    ?.let { "Bought by this character: ${it.joinToString("; ")}." },
                grantedLanguages.takeIf { it.isNotEmpty() }
                    ?.let { "From the potion: ${it.joinToString("; ")}." },
            ).joinToString(" ") +
                " A trait may grant others, and which " +
                "language this ritual uses is yours to say.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
