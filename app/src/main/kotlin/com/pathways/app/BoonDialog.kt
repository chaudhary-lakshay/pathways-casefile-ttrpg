package com.pathways.app

import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.model.BoonState
import com.pathways.rules.ClauseBonus
import com.pathways.rules.Corruption
import com.pathways.rules.Languages
import com.pathways.rules.content.LanguageProficiency
import com.pathways.rules.PowerSources
import com.pathways.rules.Boons
import com.pathways.rules.RitualResolution
import com.pathways.rules.NonStandardPathways
import com.pathways.rules.RandomSource
import com.pathways.rules.RitualOutcome
import com.pathways.rules.SuccessRoll
import com.pathways.rules.content.BoonModifier
import com.pathways.rules.content.SequenceBlock

/**
 * The two refusals no pick in the dialog can lift: they lead it, above
 * the form (play session 3, the owner's call), and open [boonRefusal].
 */
internal fun standingRefusal(skillLevel: Int?, lost: Boolean): String? = when {
    lost -> "No prayer — this character is lost, and the GM plays them."
    // Ch. 7 II: the skill "has no default and cannot be substituted".
    skillLevel == null ->
        "No prayer — a boon is rolled on $RITUAL_SKILL, which has no default. Buy it under Spend points."
    else -> null
}

/**
 * Why no prayer can be made, or null. One answer for the button and the
 * line above it.
 *
 * A patron is REQUIRED: until 2026-10-13 a prayer to nobody rolled, was
 * "granted", and granted nothing. And the rung is never chosen -- [next] is
 * the one the session offers ([BeyonderSession.nextBoon]).
 */
internal fun boonRefusal(
    skillLevel: Int?,
    patron: String?,
    held: BoonState?,
    next: BoonState?,
    lost: Boolean = false,
    language: LanguageProficiency? = null,
): String? = standingRefusal(skillLevel, lost) ?: when {
    patron == null -> "Choose a patron."
    next == null && held != null ->
        "The book records no boon past Sequence ${held.sequence} for this patron."
    next == null -> "No boon can be asked of this patron."
    // Ch. 3's mystical table, by the owner's ruling (6hq).
    language != null && !Languages.canPerformRituals(language) ->
        "No prayer in a ${language.proficiency} tongue: the book forbids a ritual in it."
    // No fuel refusal: v6.9i prints no floor on spending SPI (ruling 11).
    else -> null
}

/** What a granted Traits row says gave it: a boon's rows sit beside a potion's (6hn). */
internal fun grantedBy(potion: Boolean, boon: Boolean): String = when {
    potion && boon -> "the potion or the boon"
    boon -> "the boon"
    else -> "the potion"
}

/** The sheet's tag for a held boon: the rung's own name, as the book prints it. */
internal fun boonPill(boon: BoonState): String {
    val name = NonStandardPathways.chainFor(boon).lastOrNull { it.sequence == boon.sequence }?.name
    return "Boon · ${name ?: boon.pathway} · Seq ${boon.sequence}"
}

/**
 * What the book records for the rung being asked for, as rows that open
 * (the owner rejects text walls). Every word is the book's.
 */
@Composable
private fun GrantedBlock(block: SequenceBlock) {
    val gains = block.stats.joinToString(" · ") { "${it.attr} ${formatDelta(it.delta)}" }
    if (gains.isNotEmpty()) Text(gains, style = MaterialTheme.typography.bodyMedium)
    if (block.skills.isNotEmpty()) {
        Text(
            block.skills.joinToString(" · ") { "${it.name} ${signed(it.bonus)}" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // Prose. A row that names a catalogue trait with its own numbers is
    // granted and on the Traits tab (TraitGrants, 6hn); the rest is the GM's.
    listOf("Advantages" to block.advantages, "Drawbacks" to block.drawbacks).forEach { (label, text) ->
        if (text.isNotBlank()) {
            RuleNote(label) { Text(text, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (block.ability.isBlank()) {
        // Three blocks print an " Ability" heading with nothing under it.
        Text(
            "The book prints an Ability heading for this block and nothing under it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    } else {
        // The potion screen's rows: Everlasting 9 prints 23 headings (6hl).
        Abilities("Abilities", block.ability)
    }
    if (block.reinforcedAbilities.isNotBlank()) {
        Abilities("Reinforced abilities", block.reinforcedAbilities)
    }
}

internal fun fuelLine(spi: Int, hp: Int): String = if (hp > 0) "$spi SPI and $hp HP" else "$spi SPI"

/** The ends a ranged option offers, the row's own figure first. */
private fun endsOf(row: BoonModifier): List<Int> = endsBetween(row.modifier, row.best)

/**
 * Petition a Hidden Existence for a boon (Ch. 13, p. 144-145).
 *
 * Resolved by Chapter 7's machinery, on Ritualistic Magic. What this dialog
 * adds is what Chapter 13 puts on top: the patron, the sacrifice, and how
 * the ritual itself was performed.
 *
 * Neither the weight nor the Sequence is chosen here. The book fixes the
 * weight at Moderate (the "Heavy" it also prints is the running time), and
 * the Sequence asked for is [nextBoon]'s: the next rung the book prints for
 * the patron. A granted boon is handed to [onGranted] when the dialog
 * settles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoonDialog(
    /** Null without Ritualistic Magic: the dialog opens and says what is missing. */
    skillLevel: Int?,
    conditionPenalty: Int,
    /** The Major Wound's shock, named separately so the breakdown reconciles. */
    shock: Int = 0,
    /** The held rows' declared "-N to all rolls" (6gs), named for the same reason. */
    states: ClauseBonus = ClauseBonus(0, null),
    cor: Int,
    /** The boon already held: its patron is the only one petitioned again. */
    held: BoonState?,
    nextBoon: (String) -> BoonState?,
    /** The mystical languages bought and granted, as the Ritual dialog prints them. */
    boughtLanguages: List<String> = emptyList(),
    grantedLanguages: List<String> = emptyList(),
    /** Lost to the Rampager or to corruption: the refusal says so. */
    lost: Boolean = false,
    dice: RandomSource,
    onCorGained: (Int) -> Unit,
    onSpendSpi: (Int) -> Unit,
    onSpendHp: (Int) -> Unit,
    onGranted: (BoonState) -> Unit,
    onDismiss: () -> Unit,
) {
    val granting = remember { Boons.granting() }
    val groups = remember { Boons.groups() }
    val byGroup = remember { Boons.modifiers().groupBy { it.group } }
    // The +0 row of each group: an unremarkable sacrifice and an ordinary
    // ritual. Defaulting to the best of either would hand it out for free,
    // the same reason preparation starts at Adequate in RitualDialog.
    val chosen = remember {
        mutableStateMapOf<String, BoonModifier>().apply {
            groups.forEach { group ->
                put(group, byGroup.getValue(group).first { it.modifier == 0 })
            }
        }
    }
    // What the GM picked inside a range, by BoonModifier.key -- never by
    // label, which the two groups are free to share.
    val picked = remember { mutableStateMapOf<String, Int>() }
    var spiChoice by remember { mutableIntStateOf(granting.spi) }
    // Ch. 9's "Before rolling, spend additional SPI": the boon ritual "uses
    // the standard Ritualistic Magic system". See Boons.targetFor.
    var burned by remember { mutableIntStateOf(0) }
    var settled by remember { mutableStateOf(false) }
    var patron by remember { mutableStateOf(held?.pathway) }
    var outcome by remember { mutableStateOf<RitualOutcome?>(null) }
    // The boon the roll was made for: [next] moves once it is granted.
    var asked by remember { mutableStateOf<BoonState?>(null) }

    // The GM's declaration, as a ritual's is: null is "not stated".
    var language by remember { mutableStateOf<LanguageProficiency?>(null) }
    val hurt = conditionPenalty

    val next = patron?.let(nextBoon)
    val picks = groups.mapNotNull { chosen[it] }
    // Only the options actually chosen right now. Filtering here rather than
    // pruning [picked] on every change means no path can leave a value behind
    // to modify a later roll -- the defect the attack dialog's "Another turn"
    // shipped with.
    val overrides = picks.mapNotNull { row -> picked[row.key]?.let { row.key to it } }.toMap()
    // Null where targetFor would throw: it is drawn, not tapped.
    val canSpeak = language?.let { Languages.canPerformRituals(it) } != false
    val target = if (skillLevel == null || next == null || !canSpeak) {
        null
    } else {
        Boons.targetFor(
            skillLevel + hurt + shock + states.total, cor, next.sequence, picks, overrides,
            language = language, burned = burned,
        )
    }
    val cost = Boons.spiFor(spiChoice)
    // Keyed on the fuel, as RitualDialog's is: spiOwed refuses HP that
    // covers more than the cost.
    var hpSacrificed by remember(spiChoice) { mutableIntStateOf(0) }
    val spiOwed = PowerSources.spiOwed(cost, hpSacrificed)
    // The HP as DRAWN beside that SPI, for the handler (Codex).
    val hpOwed = hpSacrificed
    val burnOwed = burned

    // One place charges the consequence and stores the grant.
    val settle = {
        if (!settled) {
            settled = true
            outcome?.critical?.corGained?.let(onCorGained)
            if (outcome?.succeeded == true) asked?.let(onGranted)
        }
    }
    // See RitualDialog: leaving composition is not a dismiss request.
    DisposableEffect(Unit) { onDispose { settle() } }
    val finish = {
        settle()
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = finish,
        confirmButton = { CloseButton(finish) },
        title = { FormTitle(FORM_BOON) { Text("Petition for a boon") } },
        text = steadyBody {
            val result = outcome
            if (result != null) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    VerdictCard(
                        if (result.succeeded) "The boon is granted" else "The prayer fails",
                        result.succeeded,
                        result.roll,
                    ) {
                        // No Step 4 grade: the old screen never printed one
                        // for a boon, and printing it is a rule claim.
                        RitualFailureLines(result)
                    }
                    val granted = asked
                    if (result.succeeded && granted != null) {
                        Text(
                            "${boonPill(granted).removePrefix("Boon · ")}, of the ${granted.pathway} " +
                                "Pathway. Its gains are on the sheet when this closes.",
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        // Section IV. None of it is a number, and the book
                        // hands all of it to the GM.
                        RuleNote("The patron is aware of you now") {
                            Column {
                                granting.consequences.forEach {
                                    Text("• $it", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                return@steadyBody
            }
            Column {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    // Before the form, not under it: no pick below lifts it.
                    standingRefusal(skillLevel, lost)?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    if (held == null) {
                        SectionLabel("Patron's pathway")
                        FlowRow {
                            NonStandardPathways.patrons().forEach { option ->
                                CaseChip(
                                    selected = option == patron,
                                    onClick = { patron = option },
                                    label = { Text(option) },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                    } else {
                        // One patron: each Outer Deity "is the sole source
                        // of all boons on that path" (ch. 14).
                        Text("Held: ${boonPill(held).removePrefix("Boon · ")}, ${held.pathway} Pathway")
                    }
                    groups.forEach { group ->
                        SectionLabel(group)
                        FlowRow {
                            byGroup.getValue(group).forEach { option ->
                                CaseChip(
                                    selected = chosen[group]?.label == option.label,
                                    onClick = { chosen[group] = option },
                                    label = { Text("${option.label} (${option.printed})") },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                        // Three of the eight options print a range rather than
                        // a number, and the end is the GM's; the default is
                        // the end nearer zero. Drawn for every pick in a group
                        // that has a range, a single figure as one disabled
                        // chip: appearing on the pick moved the rows below it
                        // under the finger (play session 4, 6gn's rule).
                        val selected = chosen[group]
                        if (byGroup.getValue(group).any { it.best != null }) {
                            FlowRow {
                                val ends = selected?.takeIf { it.best != null }?.let(::endsOf)
                                (ends ?: listOf(selected?.modifier ?: 0)).forEach { value ->
                                    CaseChip(
                                        selected = selected != null &&
                                            (picked[selected.key] ?: selected.modifier) == value,
                                        enabled = ends != null,
                                        onClick = { if (ends != null) picked[selected.key] = value },
                                        label = { Text(signed(value)) },
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                }
                            }
                        }
                    }

                    SectionLabel("Fuel")
                    FlowRow {
                        (granting.spi..granting.spiMax).forEach { option ->
                            CaseChip(
                                selected = option == spiChoice,
                                onClick = { spiChoice = option },
                                label = { Text("$option SPI") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    OptionStepper(
                        label = "Burn SPI",
                        value = burned,
                        shown = "+$burned",
                        note = "before rolling: 1 SPI buys +1, no cap, separate from the fuel",
                    ) { burned = (burned + it).coerceAtLeast(0) }
                    // Ch. 7 IV.B. Fuel, never a bonus.
                    SectionLabel("Paid in HP (${PowerSources.HP_PER_SPI} HP = 1 SPI, no bonus to the roll)")
                    FlowRow {
                        hpChoices(cost).forEach { option ->
                            CaseChip(
                                selected = option == hpSacrificed,
                                onClick = { hpSacrificed = option },
                                label = { Text(if (option == 0) "None" else "$option HP") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }

                    MysticalLanguagePicker(language, boughtLanguages, grantedLanguages) { language = it }

                    // Below the form, not above it: picking a patron drew this
                    // block over the sacrifice chips the next tap was aimed at
                    // (play session 4).
                    if (next != null) {
                        val block = NonStandardPathways.chainFor(next).last()
                        Text(
                            "${block.name} — Sequence ${next.sequence} · " +
                                signed(Boons.penaltyForSequence(next.sequence)),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        GrantedBlock(block)
                    }

                    RuleNote("What a petition needs") {
                        Column {
                            granting.requirements.forEach {
                                Text("• $it", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    RuleNote("How the boon's level is priced") {
                        Text(granting.boonLevelRule, style = MaterialTheme.typography.bodySmall)
                    }
                }
                val refusal = boonRefusal(skillLevel, patron, held, next, lost, language = language)
                RitualFooter(
                    terms = listOfNotNull(
                        skillLevel?.let { "${granting.skill} $it" },
                        "${granting.weight} ${granting.weightModifier}",
                        next?.let { "Sequence ${it.sequence} ${signed(Boons.penaltyForSequence(it.sequence))}" },
                        "circumstances ${signed(Boons.modifierTotal(picks, overrides))}",
                        language?.ritualModifier?.takeIf { it != 0 }?.let { "$it for an accented tongue" },
                        if (hurt != 0) "$hurt hurt or tired" else null,
                        if (shock != 0) "$shock shock" else null,
                        states.line,
                        Corruption.ritualPenalty(cor).takeIf { it != 0 }?.let { "$it for $cor CoR" },
                        if (burned > 0) "+$burned SPI burned" else null,
                    ),
                    target = target,
                    cost = "costs " + fuelLine(spiOwed + burnOwed, hpSacrificed) + ", takes ${granting.complexityTime}",
                    refusal = refusal,
                    label = "Pray",
                    icon = Icons.Filled.Church,
                ) {
                    // One Pray rolls once.
                    if (outcome != null || refusal != null || target == null) return@RitualFooter
                    // Against the target printed above, never
                    // composed a second time here (6eg, 6eh).
                    asked = next
                    outcome = RitualResolution.rollAgainst(target, dice)
                    // Spent whether or not the Great One grants it: "No
                    // effect. SPI and materials spent."
                    onSpendSpi(spiOwed + burnOwed)
                    if (hpOwed > 0) onSpendHp(hpOwed)
                    // The CoR and the grant wait for [finish].
                }
            }
        },
    )
}
