package com.pathways.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pathways.model.RollResult
import com.pathways.rules.FrightCheck
import com.pathways.rules.FrightKind
import com.pathways.rules.FrightOutcome
import com.pathways.rules.SuccessRoll
import com.pathways.rules.Shock
import com.pathways.rules.TraitClause
import com.pathways.rules.ClauseBonus
import com.pathways.rules.TraitClauses
import com.pathways.rules.content.FrightRecovery
import com.pathways.rules.content.RecoveryAttribute
import com.pathways.rules.TraitEffects
import com.pathways.rules.content.FrightTrigger

/**
 * A Fright Check at the table (Ch. 5, p. 43).
 *
 * ponytail: the trigger list IS the interface. Two of the book's five rows
 * print a range and one is open-ended, so the app offers the gentler end it
 * prints and shows the range beside it -- the severity inside that range is
 * the GM's call, exactly like the acting-quality ranges on the pathway screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FrightDialog(
    will: Int,
    /** For the Confusion column's recovery roll, which is the only IQ roll here. */
    iq: Int,
    /**
     * For row 12's fright duration, which is the only HT term on this screen.
     *
     * No roll in this table is HT. The cell counts down from it and rolls
     * Will, which is exactly why the duration could not ride on the recovery.
     */
    ht: Int,
    clauses: List<TraitClause> = emptyList(),
    /**
     * The subset of [clauses] that name a bare Will roll rather than the
     * Fright Check itself.
     *
     * The recovery roll is a second bare Will roll in two of three columns, so
     * a clause like "-1 to Will rolls for the day" reaches it — but the rows
     * scoped to the Fright Check do not, because the check is over. Passed
     * separately rather than re-filtered here: which clause names which roll
     * is [TraitEffects]' business, and a composable deciding it would be the
     * second copy.
     */
    willOnlyClauses: List<TraitClause> = emptyList(),
    /**
     * What the character's traits add to a recovery roll:
     * [FrightCheck.recoveryBonus], which gives Combat Reflexes' "+6 to recover
     * from stun" to a row that stuns and nothing to row 12's Retching, Ecstasy
     * and Dazed. Asked of the rules per row; this screen decides neither half.
     */
    recoveryBonus: (FrightRecovery) -> Int = { 0 },
    shock: Int = 0,
    /** Reeling and fatigue: "-1 to all rolls", the phrase shock is read by. */
    hurt: Int = 0,
    /** The held rows' declared "-N to all rolls" (6gs), on the check and its recovery. */
    states: ClauseBonus = ClauseBonus(0, null),
    /** Defend's Stunned, set by a stunning band and cleared by its recovery (play session 1). */
    onStunned: (Boolean) -> Unit,
    /** Whether the character was already stunned when this dialog opened. */
    stunned: Boolean,
    /** Record the quirk the 13-15 band hands out ([BeyonderSession.addQuirk]). */
    onQuirk: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val dice = LocalDice.current
    var kind by remember { mutableStateOf(FrightKind.FRIGHT) }
    var outcome by remember { mutableStateOf<FrightOutcome?>(null) }
    var picked by remember { mutableStateOf<FrightTrigger?>(null) }
    var modifier by remember { mutableIntStateOf(0) }
    // Which circumstances the GM says hold. Keyed on the clause, because one
    // cell prints two ("-2 on direct exposure; -1 in consecrated buildings")
    // and a key that could not tell them apart would move both at once (6r).
    var declared by remember(clauses) { mutableStateOf(emptySet<String>()) }
    // Keyed on the check, so a new Fright Check clears the last one's recovery
    // roll without a second clearing site (6cp: the key is the guard).
    var recovery by remember(outcome) { mutableStateOf<RollResult?>(null) }
    // Whether THIS check stunned the character. Its recovery ends only that
    // stun, never one a Major Wound set before it (the diff review).
    var stunnedHere by remember { mutableStateOf(false) }
    val triggers = remember { FrightCheck.triggers() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = {
            Column {
                Text("Fright Check")
                Text(
                    "Will $will",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        text = {
            val result = outcome
            if (result != null) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    FrightResultCard(result, kind, ht = ht, will = will, iq = iq)
                    result.effect?.takeIf { !result.passed && FrightCheck.grantsQuirk(it, kind) }?.let {
                        QuirkEntry(onQuirk)
                    }
                    // Six of the nine bands print no way out at all, so this
                    // is null for them and no control appears. The cell's own
                    // sentence above already said so.
                    result.effect?.let { FrightCheck.recoveryFor(it, kind) }?.let { out ->
                        FrightRecoveryPanel(
                            recovery = out,
                            // Both of these are the rules' call, asked once
                            // each: the column decides the attribute, and the
                            // same fact decides which clause list can reach
                            // the roll.
                            attributeLevel = FrightCheck.levelFor(out, will, iq),
                            bonus = recoveryBonus(out),
                            clauseBonus = if (FrightCheck.takesWillClauses(out)) {
                                TraitClauses.clauseBonus(willOnlyClauses, declared)
                            } else {
                                ClauseBonus(0, null)
                            },
                            shock = shock,
                            hurt = hurt,
                            states = states,
                            rolled = recovery,
                            onRoll = { target ->
                                // One roll a tap: a second from the same
                                // picture would roll again (6p).
                                if (recovery == null) {
                                    val rolled = SuccessRoll.against(target, dice.dice("Fright recovery"))
                                    recovery = rolled
                                    // Won back: the stun this check set ends.
                                    if (stunnedHere && rolled.outcome.isSuccess) onStunned(false)
                                }
                            },
                        )
                    }
                }
                return@AlertDialog
            }
            val chosen = picked
            // ONE value, displayed and rolled: computing the caption from one
            // expression and the roll from another is how these numbers
            // stopped reconciling (6ag, 6ai). The trait total comes from rules
            // for the same reason -- a screen that summed its own chips would
            // be the second copy.
            val fromTraits = TraitClauses.clauseBonus(clauses, declared)
            val effective = modifier + shock + hurt + fromTraits.total + states.total
            val target = FrightCheck.targetFor(will, effective)
            // The choices scroll and the target with Roll is pinned beneath
            // them, the shape of the Attack and ritual dialogs.
            Column {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    TargetCard(target, baseTarget = will)
                    SectionLabel("Kind")
                    FlowRow {
                        FrightKind.entries.forEach { option ->
                            FilterChip(
                                selected = option == kind,
                                onClick = { kind = option },
                                label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    SectionLabel("What was witnessed")
                    triggers.forEach { trigger ->
                        TriggerRow(trigger, selected = trigger == chosen) {
                            picked = trigger
                            modifier = trigger.modifier
                        }
                    }
                    // Three of the book's five rows do not print a single
                    // number -- "-2 to -6" and "-8 or worse" are severities
                    // the GM chooses. Rolling the gentlest end for them would
                    // quietly turn their failures into successes.
                    // Drawn before a pick too, so the first pick cannot grow
                    // the dialog and slide the rows under a finger. It steps
                    // nothing until a row is picked, which resets it.
                    OptionStepper(
                        label = "Modifier",
                        value = modifier,
                        shown = signed(modifier),
                        note = chosen?.let {
                            "the book prints ${it.printed}" +
                                if (it.openEnded) " — no floor, so worse is legal" else ""
                        } ?: "pick what was witnessed",
                    ) { if (picked != null) modifier += it }
                    // The character's own traits. Unconditional clauses are
                    // counted without asking; each conditional one is a chip,
                    // because whether this character is in a church or facing
                    // the thing they fear is the GM's to say and this app
                    // holds no scene.
                    ClauseChips(clauses, declared) { declared = it }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (shock != 0) {
                    Text(
                        "includes $shock from shock — \"${Shock.printed()}\"",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (hurt != 0) {
                    Text(
                        "includes $hurt from the HP and FP trackers",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                fromTraits.line?.let {
                    Text("includes $it", style = MaterialTheme.typography.bodySmall)
                }
                states.line?.let {
                    Text("includes $it", style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (chosen == null) "Pick what was witnessed." else "3d6 ≤ $target",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = chosen != null,
                        onClick = {
                            // Guarded rather than relying on the button
                            // leaving the tree: two taps before a
                            // recomposition would otherwise roll twice and
                            // keep the second, the re-entrancy the divination
                            // dialog was hardened for (6p).
                            if (outcome == null && chosen != null) {
                                val rolled = FrightCheck.rollAgainst(target, dice.dice("Fright Check"))
                                outcome = rolled
                                if (FrightCheck.leavesStunned(rolled.effect, kind, recovery = null)) {
                                    stunnedHere = !stunned
                                    onStunned(true)
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Roll", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
    )
}

/** One row of the book's trigger table: what was seen, and what it prints. */
@Composable
private fun TriggerRow(trigger: FrightTrigger, selected: Boolean, onPick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        color = if (selected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = if (selected) BorderStroke(1.dp, accent) else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onPick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Text(
                trigger.trigger,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            Tag(trigger.printed, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The check, once rolled: held or failed, the dice against the target they
 * were rolled at, and on a failure the band of the effects table it reached.
 */
@Composable
private fun FrightResultCard(result: FrightOutcome, kind: FrightKind, ht: Int, will: Int, iq: Int) {
    val accent = if (result.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, accent),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (result.passed) "Held their nerve" else "Fright Check failed",
                style = MaterialTheme.typography.headlineMedium,
                color = accent,
            )
            Text(
                "3d6: ${result.roll.total} vs ${result.target}",
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            )
            if (!result.passed) {
                Text("failed by ${result.margin}; effect roll ${result.effectRoll}")
                Text(
                    "${result.effect?.printed}: ${result.effect?.let { FrightCheck.text(it, kind) }}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
                // One band works its duration out from the character
                // ("Retching for (25 - HT) sec"); the other two that print a
                // duration already print a number. Null elsewhere, so no line
                // appears.
                result.effect?.let { FrightCheck.durationFor(it, kind) }?.let { held ->
                    Text(
                        FrightCheck.durationLine(held, ht = ht, will = will, iq = iq),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/**
 * The quirk the 13-15 band hands out, named by the GM and written to the
 * sheet as one gained in play (play session 2, which had nowhere to put it).
 * Recording twice stores one: [BeyonderSession.addQuirk] ignores a name held.
 */
@Composable
private fun QuirkEntry(onQuirk: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var recorded by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(top = 8.dp)) {
        val done = recorded
        if (done != null) {
            Text("Recorded on the sheet: $done", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("The quirk, as the GM names it") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = { onQuirk(name); recorded = name.trim() },
            enabled = name.isNotBlank(),
        ) { Text("Record the quirk") }
    }
}

/**
 * The way out of a Fright Effects row, where the table prints one.
 *
 * Three of the nine bands do. The other six hand out a quirk, a disadvantage
 * or a permanent loss with no roll to escape, so nothing renders for them —
 * a control there would grant a way back the book withholds.
 *
 * **One roll, not one a turn.** Rows 4-9 say "roll vs. Will each sec to
 * recover" and this app holds no clock, so the button says "now" and the
 * cell's own sentence — already printed above — is what says the book asks
 * again. The same reading `DamageDialog` makes of the Major Wound's stun.
 *
 * The duration each cell prints is shown and never computed: row 12 counts
 * down from "(25 − HT)", "(25 − Will)" and "(25 − IQ)", and two of those three
 * are not the attribute that column rolls.
 */
@Composable
private fun FrightRecoveryPanel(
    recovery: FrightRecovery,
    attributeLevel: Int,
    bonus: Int,
    clauseBonus: ClauseBonus,
    shock: Int,
    hurt: Int,
    states: ClauseBonus,
    rolled: RollResult?,
    onRoll: (Int) -> Unit,
) {
    // ONE expression, printed and rolled, for the reason the Major Wound panel
    // states: two would let the caption and the roll drift.
    val target = attributeLevel + bonus + clauseBonus.total + shock + hurt + states.total
    Column(Modifier.padding(top = 8.dp)) {
        if (rolled == null) {
            TextButton(onClick = { onRoll(target) }) {
                // The book's word, as the cell prints it: the enum's name is "WILL".
                val attribute = when (recovery.attribute) {
                    RecoveryAttribute.WILL -> "Will"
                    RecoveryAttribute.IQ -> "IQ"
                }
                Text("Roll $attribute $target to recover now")
            }
            if (bonus != 0) {
                Text(
                    "includes +$bonus from ${TraitEffects.COMBAT_REFLEXES} — " +
                        "\"+6 to recover from stun\"",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            clauseBonus.line?.let {
                Text("includes $it", style = MaterialTheme.typography.bodySmall)
            }
            states.line?.let {
                Text("includes $it", style = MaterialTheme.typography.bodySmall)
            }
            return@Column
        }
        // The roll's OWN target and verdict, never the recomputed ones: a
        // clause chip or a tracker moving while the result is on screen would
        // otherwise reprint an old roll against a number it was not made
        // against (Codex).
        Text("3d6: ${rolled.total} vs ${rolled.target}", style = MaterialTheme.typography.titleMedium)
        Text(
            if (rolled.outcome.isSuccess) "Recovered." else "Still there; the row asks again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
