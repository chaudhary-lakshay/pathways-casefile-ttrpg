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
import com.pathways.model.BoonState
import com.pathways.rules.ClauseBonus
import com.pathways.rules.Corruption
import com.pathways.rules.BurnKind
import com.pathways.rules.Pools
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
    cost: Int,
    spiAvailable: Int,
    lost: Boolean = false,
    hpSacrificed: Int = 0,
): String? = when {
    lost -> "No prayer — this character is lost, and the GM plays them."
    // Ch. 7 II: the skill "has no default and cannot be substituted".
    skillLevel == null ->
        "No prayer — a boon is rolled on $RITUAL_SKILL, which has no default. Buy it under Spend points."
    patron == null -> "Choose a patron."
    next == null && held != null ->
        "The book records no boon past Sequence ${held.sequence} for this patron."
    next == null -> "No boon can be asked of this patron."
    // Ch. 7 IV.B, which ch. 13 III defers to: HP "in addition to or instead
    // of SPI". It is how a mortal, who plays SPI 0, prays at all.
    !Pools.canAffordSpi(PowerSources.spiOwed(cost, hpSacrificed), spiAvailable) ->
        "No supplication — this costs ${PowerSources.spiOwed(cost, hpSacrificed)} SPI and only " +
            "$spiAvailable is left. SPI cannot be spent below 0. Pay in HP: " +
            "${PowerSources.HP_PER_SPI} HP counts as 1 SPI."
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
 * settles, never at the roll -- a push can still turn the verdict.
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
    spiAvailable: Int,
    /** The boon already held: its patron is the only one petitioned again. */
    held: BoonState?,
    nextBoon: (String) -> BoonState?,
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
    // Ch. 13's ritual is chapter 7's, by its own first sentence about the
    // procedure -- so chapter 7's post-roll push reaches it. See Boons.canPush.
    var burned by remember { mutableIntStateOf(0) }
    var settled by remember { mutableStateOf(false) }
    var patron by remember { mutableStateOf(held?.pathway) }
    var outcome by remember { mutableStateOf<RitualOutcome?>(null) }
    // The boon the roll was made for: [next] moves once it is granted.
    var asked by remember { mutableStateOf<BoonState?>(null) }

    val next = patron?.let(nextBoon)
    val picks = groups.mapNotNull { chosen[it] }
    // Only the options actually chosen right now. Filtering here rather than
    // pruning [picked] on every change means no path can leave a value behind
    // to modify a later roll -- the defect the attack dialog's "Another turn"
    // shipped with.
    val overrides = picks.mapNotNull { row -> picked[row.key]?.let { row.key to it } }.toMap()
    val target = if (skillLevel == null || next == null) {
        null
    } else {
        Boons.targetFor(skillLevel + conditionPenalty + shock + states.total, cor, next.sequence, picks, overrides)
    }
    val cost = Boons.spiFor(spiChoice)
    // Keyed on the fuel, as RitualDialog's is: spiOwed refuses HP that
    // covers more than the cost.
    var hpSacrificed by remember(spiChoice) { mutableIntStateOf(0) }
    val spiOwed = PowerSources.spiOwed(cost, hpSacrificed)

    // One place charges the push and the consequence and stores the grant,
    // off the FINAL outcome: see RitualDialog for why not at the roll.
    val settle = {
        if (!settled) {
            settled = true
            if (burned > 0) onSpendSpi(burned)
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
                return@AlertDialog
            }
            Column {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    if (held == null) {
                        SectionLabel("Patron's pathway")
                        FlowRow {
                            NonStandardPathways.patrons().forEach { option ->
                                FilterChip(
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
                    // Ch. 7 IV.B. Fuel, never a bonus.
                    SectionLabel("Paid in HP (${PowerSources.HP_PER_SPI} HP = 1 SPI, no bonus to the roll)")
                    FlowRow {
                        hpChoices(cost).forEach { option ->
                            FilterChip(
                                selected = option == hpSacrificed,
                                onClick = { hpSacrificed = option },
                                label = { Text(if (option == 0) "None" else "$option HP") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
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
                // A boon IS a ch. 7 ritual with its parameters fixed (6r),
                // so Power Source A's floor applies here too: "SPI cannot
                // be reduced below 0."
                val refusal = boonRefusal(skillLevel, patron, held, next, cost, spiAvailable, lost, hpSacrificed)
                RitualFooter(
                    terms = listOfNotNull(
                        skillLevel?.let { "${granting.skill} $it" },
                        "${granting.weight} ${granting.weightModifier}",
                        next?.let { "Sequence ${it.sequence} ${signed(Boons.penaltyForSequence(it.sequence))}" },
                        "circumstances ${signed(Boons.modifierTotal(picks, overrides))}",
                        if (conditionPenalty != 0) "$conditionPenalty hurt or tired" else null,
                        if (shock != 0) "$shock shock" else null,
                        states.line,
                        Corruption.ritualPenalty(cor).takeIf { it != 0 }?.let { "$it for $cor CoR" },
                    ),
                    target = target,
                    cost = "costs " + (if (hpSacrificed > 0) "$spiOwed SPI and $hpSacrificed HP" else "$spiOwed SPI") +
                        ", takes ${granting.complexityTime}",
                    refusal = refusal,
                    targetText = target?.let(::targetLine).orEmpty(),
                    label = "Pray",
                ) {
                    // One Pray spends once: a second tap before the
                    // button leaves the tree charges the fuel again,
                    // and below 0 that buys permanent CoR.
                    if (outcome != null || refusal != null || target == null) return@RitualFooter
                    // Against the target printed above, never
                    // composed a second time here (6eg, 6eh).
                    asked = next
                    outcome = RitualResolution.rollAgainst(target, dice)
                    // Spent whether or not the Great One answers:
                    // "No effect. SPI and materials spent" (p. 62).
                    // The CoR and the grant wait for [finish]: a push
                    // can take this off the Critical Failure Table, or
                    // onto a success.
                    onSpendSpi(spiOwed)
                    if (hpSacrificed > 0) onSpendHp(hpSacrificed)
                }
            }
        },
    )
}
