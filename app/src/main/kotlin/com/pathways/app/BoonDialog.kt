package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import com.pathways.rules.ClauseBonus
import com.pathways.rules.BurnKind
import com.pathways.rules.Pools
import com.pathways.rules.Boons
import com.pathways.rules.RitualResolution
import com.pathways.rules.NonStandardPathways
import com.pathways.rules.RandomSource
import com.pathways.rules.RitualOutcome
import com.pathways.rules.SuccessRoll
import com.pathways.rules.content.BoonModifier
import com.pathways.rules.content.SequenceBlock

/**
 * The number to roll under, and -- when the boon-level doubling has driven it
 * below what 3d6 can show -- the exception that still gives it a chance.
 *
 * A bare "3d6 <= -512" reads as impossible. It is not: [SuccessRoll] treats a
 * rolled 3 or 4 as a critical success whatever the target, and Chapter 13
 * prints no override of that (Codex).
 */
internal fun targetLine(target: Int): String {
    val printed = "3d6 ≤ $target"
    if (target >= SuccessRoll.ALWAYS_SUCCEEDS_AT_OR_UNDER) return printed
    return "$printed — a rolled 3 or 4 still succeeds"
}

/**
 * What the book records for the pathway and sequence being petitioned.
 *
 * Shown before the roll, because "Sequence 8 of the Chaos Mist Pathway" is
 * the thing being asked for and a player choosing a boon level blind is
 * choosing a number. Advantages and drawbacks stay the prose the extractor
 * preserved -- their name/effect boundary is not recoverable from this PDF.
 */
@Composable
private fun GrantedBlock(block: SequenceBlock) {
    Text(
        "${block.name} — Sequence ${block.sequence}",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    val gains = block.stats.joinToString { "${it.attr} ${signed(it.delta.toInt())}" }
    if (gains.isNotEmpty()) Text(gains, style = MaterialTheme.typography.bodySmall)
    block.skills.forEach {
        Text("${it.name} ${signed(it.bonus)}", style = MaterialTheme.typography.bodySmall)
    }
    listOf("Advantages" to block.advantages, "Drawbacks" to block.drawbacks).forEach { (label, text) ->
        if (text.isNotBlank()) {
            Text("$label: $text", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (block.ability.isBlank()) {
        // Three blocks print an " Ability" heading with nothing under it.
        // Saying so beats an empty gap the reader has to interpret.
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

/** The ends a ranged option offers, the row's own figure first. */
private fun endsOf(row: BoonModifier): List<Int> = endsBetween(row.modifier, row.best)

/**
 * Petition a Hidden Existence for a boon (Ch. 13, p. 144-145).
 *
 * Reachable on the same gate as [RitualDialog] -- the chapter rolls
 * Ritualistic Magic, which no premade prints -- and resolved by the same
 * Chapter 7 machinery. What this dialog adds is the three things Chapter 13
 * puts on top: the sequence being asked for, the sacrifice, and how the
 * ritual itself was performed.
 *
 * The weight is NOT chosen here. The book fixes it at Moderate and the
 * "Heavy" it also prints is the running time, not the difficulty.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoonDialog(
    skillLevel: Int,
    conditionPenalty: Int,
    /** The Major Wound's shock, named separately so the breakdown reconciles. */
    shock: Int = 0,
    /** The held rows' declared "-N to all rolls" (6gs), named for the same reason. */
    states: ClauseBonus = ClauseBonus(0, null),
    cor: Int,
    spiAvailable: Int,
    dice: RandomSource,
    onCorGained: (Int) -> Unit,
    onSpendSpi: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val granting = remember { Boons.granting() }
    val groups = remember { Boons.groups() }
    val byGroup = remember { Boons.modifiers().groupBy { it.group } }
    var sequence by remember { mutableIntStateOf(Boons.HIGHEST_SEQUENCE) }
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
    // Ch. 13's ritual is chapter 7's, by its own first sentence about the
    // procedure -- so chapter 7's post-roll push reaches it. See Boons.canPush.
    var burned by remember { mutableIntStateOf(0) }
    var settled by remember { mutableStateOf(false) }
    // Null is a real answer: the Great Ones are not a closed list, and the
    // book records only fourteen of their pathways. Petitioning one it never
    // printed is the GM's to adjudicate, not this dialog's to refuse.
    var pathway by remember { mutableStateOf<String?>(null) }
    var outcome by remember { mutableStateOf<RitualOutcome?>(null) }

    val picks = groups.mapNotNull { chosen[it] }
    // Only the options actually chosen right now. Filtering here rather than
    // pruning [picked] on every change means no path can leave a value behind
    // to modify a later roll -- the defect the attack dialog's "Another turn"
    // shipped with.
    val overrides = picks.mapNotNull { row -> picked[row.key]?.let { row.key to it } }.toMap()
    val effectiveSkill = skillLevel + conditionPenalty + shock + states.total
    val target = Boons.targetFor(effectiveSkill, cor, sequence, picks, overrides)
    val cost = Boons.spiFor(spiChoice)

    // One place charges the push and the consequence, off the FINAL outcome:
    // see RitualDialog for why the CoR cannot be charged at the roll.
    val settle = {
        if (!settled) {
            settled = true
            if (burned > 0) onSpendSpi(burned)
            outcome?.critical?.corGained?.let(onCorGained)
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
        confirmButton = { TextButton(onClick = finish) { Text("Close") } },
        title = { Text("Petition for a boon") },
        text = {
            val result = outcome
            if (result != null) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    // The final outcome, pushed or not: a push re-reads the
                    // same dice against the raised target, roll and all.
                    VerdictCard(
                        if (result.succeeded) "The boon is granted" else "The prayer fails",
                        result.succeeded,
                        result.roll,
                    ) {
                        // No Step 4 grade: the old screen never printed one
                        // for a boon, and printing it is a rule claim.
                        RitualFailureLines(result)
                    }
                    if (Boons.canPush(result)) {
                        BurnRow(
                            kind = BurnKind.MYSTICAL,
                            burned = burned,
                            spiAvailable = spiAvailable - burned,
                            note = "Separate from the ritual's fuel, which is " +
                                "already paid. Charged when this closes.",
                        ) { points ->
                            // See RitualDialog: the row was drawn against a
                            // value two taps can outrun.
                            val live = outcome ?: return@BurnRow
                            if (!Pools.canAffordSpi(burned + points, spiAvailable)) {
                                return@BurnRow
                            }
                            burned += points
                            outcome = Boons.pushed(live, points)
                        }
                    }
                    if (result.succeeded) {
                        // Section IV. None of it is a number, and the book
                        // hands all of it to the GM -- but a boon accepted
                        // without them on screen is a bargain half read.
                        Text(
                            "Sequence $sequence" +
                                (pathway?.let { " of the $it Pathway" } ?: "") +
                                " granted. The patron is aware of you now:",
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        pathway?.let { NonStandardPathways.blockFor(it, sequence) }
                            ?.let { GrantedBlock(it) }
                        granting.consequences.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                return@AlertDialog
            }
            Column {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    SectionLabel("Boon level")
                    FlowRow {
                        (Boons.HIGHEST_SEQUENCE downTo Boons.LOWEST_SEQUENCE).forEach { option ->
                            FilterChip(
                                selected = option == sequence,
                                onClick = { sequence = option },
                                label = { Text("Seq $option") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    Text(
                        "Sequence $sequence: ${signed(Boons.penaltyForSequence(sequence))}. " +
                            granting.boonLevelRule,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    SectionLabel("Patron's pathway")
                    FlowRow {
                        NonStandardPathways.pathways().forEach { option ->
                            FilterChip(
                                selected = option == pathway,
                                onClick = { pathway = if (option == pathway) null else option },
                                label = { Text(option) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    val patron = pathway
                    val documented = patron?.let { NonStandardPathways.blockFor(it, sequence) }
                    when {
                        documented != null -> GrantedBlock(documented)
                        patron != null -> {
                            Text(
                                "The book records no Sequence $sequence for the $patron" +
                                    " Pathway — it prints " +
                                    NonStandardPathways.knownSequences(patron).joinToString(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            // The two chapters spell one pathway differently.
                            // Saying so beats a gap that reads as "the book
                            // never wrote this down" when it did, under the
                            // other name (Codex).
                            NonStandardPathways.alsoPrintedAs(patron)?.let { other ->
                                Text(
                                    "The other chapter prints this pathway as \"$other\" and " +
                                        "records " +
                                        NonStandardPathways.knownSequences(other).joinToString() +
                                        ". The book never reconciles the two names.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    groups.forEach { group ->
                        SectionLabel(group)
                        FlowRow {
                            byGroup.getValue(group).forEach { option ->
                                FilterChip(
                                    selected = chosen[group]?.label == option.label,
                                    onClick = { chosen[group] = option },
                                    label = { Text("${option.label} (${option.printed})") },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                        // Three of the eight options print a range rather than
                        // a number, and the end is the GM's; the default is
                        // the end nearer zero.
                        val selected = chosen[group]
                        if (selected?.best != null) {
                            FlowRow {
                                endsOf(selected).forEach { value ->
                                    FilterChip(
                                        selected =
                                            (picked[selected.key] ?: selected.modifier) == value,
                                        onClick = { picked[selected.key] = value },
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
                            FilterChip(
                                selected = option == spiChoice,
                                onClick = { spiChoice = option },
                                label = { Text("$option SPI") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }

                    SectionLabel("Bring")
                    granting.requirements.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
                // A boon IS a ch. 7 ritual with its parameters fixed (6r),
                // so Power Source A's floor applies here too: "SPI cannot
                // be reduced below 0."
                val affordable = Pools.canAffordSpi(cost, spiAvailable)
                RitualFooter(
                    terms = listOfNotNull(
                        "${granting.skill} $skillLevel",
                        "${granting.weight} ${granting.weightModifier}",
                        "Sequence $sequence ${signed(Boons.penaltyForSequence(sequence))}",
                        "circumstances ${signed(Boons.modifierTotal(picks, overrides))}",
                        if (conditionPenalty != 0) "$conditionPenalty hurt or tired" else null,
                        if (shock != 0) "$shock shock" else null,
                        states.line,
                        if (cor >= 3) "-1 for $cor CoR" else null,
                    ),
                    target = target,
                    cost = "costs $cost SPI, takes ${granting.complexityTime}",
                    refusal = if (affordable) {
                        null
                    } else {
                        "No supplication — this costs $cost SPI and only " +
                            "$spiAvailable is left. SPI cannot be spent below 0."
                    },
                    targetText = targetLine(target),
                    label = "Pray",
                ) {
                    // One Pray spends once: a second tap before the
                    // button leaves the tree charges the fuel again,
                    // and below 0 that buys permanent CoR.
                    if (outcome != null || !affordable) return@RitualFooter
                    // Against the target printed above, never
                    // composed a second time here (6eg, 6eh).
                    val rolled = RitualResolution.rollAgainst(target, dice)
                    outcome = rolled
                    // Spent whether or not the Great One answers:
                    // "No effect. SPI and materials spent" (p. 62).
                    // The CoR waits for [finish]: a push can take this
                    // off the Critical Failure Table entirely.
                    onSpendSpi(cost)
                }
            }
        },
    )
}
