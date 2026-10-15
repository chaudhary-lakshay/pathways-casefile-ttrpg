package com.pathways.app

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.model.RollResult
import com.pathways.rules.ClauseBonus
import com.pathways.rules.MajorWoundHt
import com.pathways.rules.Pool
import com.pathways.rules.Shock
import com.pathways.rules.StunRecovery
import com.pathways.rules.SuccessRoll
import com.pathways.rules.TraitEffects
import com.pathways.rules.Wounds
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.HitLocation

/**
 * One hit: how much, and which pool absorbs it (Ch. 5, p. 40-42).
 *
 * The trackers already let a GM move any pool by hand, so what this adds is
 * the two things a single hit decides that a stepper cannot.
 *
 * **Which pool.** Spirit body damage lets the target choose: "the target
 * chooses which of three pools it reduces: HP, FP, or SPI". Applying it here
 * is what makes "One pool per instance -- you cannot split a single hit across
 * multiple pools" a rule the app keeps rather than a line it prints: one
 * amount, one pool, and the dialog closes. [hasSpirituality] is false for a
 * mortal, who is offered HP and FP only -- "Non-Beyonders (SPI 0) ... have no
 * spirituality to sacrifice". The sheet hides the SPI tracker on the same
 * test, so the two cannot disagree.
 *
 * **Whether it is a Major Wound.** That threshold is a property of the single
 * hit, which is exactly what the pool steppers destroy by moving one point at
 * a time. The HT roll is offered rather than made: the book says "make an
 * immediate HT roll", and a roll the GM did not ask for is one they cannot
 * decline for a hit they have already resolved at the table.
 *
 * SPI taken below zero is Soul Debt and buys CoR, which [BeyonderSession.spend]
 * charges. Nothing here needs to know that, and deliberately does not: a
 * second place that knows the rate is a second place to get it wrong.
 */
@Composable
internal fun DamageDialog(
    hasSpirituality: Boolean,
    maxHp: Int,
    ht: Int,
    /** Labelled per throw: the log said "Damage 7 vs 10" for the HT roll. */
    dice: CharacterDice,
    onApply: (Pool, Int) -> Unit,
    /**
     * Latches the Major Wound's shock, which this dialog is where it comes
     * from: the app rolled the table, so it knows -- the reasoning
     * `SavedSession.corrupted` already uses (6a) rather than asking the GM to
     * re-declare a fact the screen just produced.
     */
    onShocked: () -> Unit,
    /** The stun this hit leaves, for Defend's Stunned (play session 1). */
    onStunned: () -> Unit,
    /**
     * What the character's traits do to this roll, and the sentence saying so.
     *
     * Two rules reach it. High Pain Threshold's "+3 on HT rolls to avoid
     * knockdown" applies because the fail-by-5 row is "Knocked down and
     * unconscious" (Codex, 6bo); Fit's "+1 to all HT rolls" applies because
     * this is an HT roll.
     *
     * The number and the sentence arrive together on purpose: a screen that
     * added one and printed the other would show a breakdown that does not
     * sum, which this codebase has produced three times (6ag, 6ai, 6bp).
     */
    majorWoundHt: MajorWoundHt = MajorWoundHt(0, null),
    /**
     * The same, for the recovery roll the Failure row prints
     * ([com.pathways.rules.StunRecovery]).
     *
     * A second parameter rather than a flag on the one above, because the two
     * rolls are reached by different clauses: `High Pain Threshold` moves the
     * table's roll and not this one, Combat Reflexes moves this one and not
     * that one. Only its sentence is read here: its number is inside
     * [recoveryTarget], built at the same call site from the same traits, and
     * the sentence's own bonus is built from `StunRecovery.bonusFor`.
     */
    stunRecoveryHt: MajorWoundHt = MajorWoundHt(0, null),
    /**
     * What the HP and FP trackers cost this roll, from
     * [com.pathways.rules.Conditions.penaltyFor].
     *
     * `Shock` refuses `RollKind.MAJOR_WOUND_HT` because that roll is what
     * CREATES the shock. Reeling and fatigue are not created by it -- the
     * character carried them in -- so the exclusion is about WHEN and does not
     * transfer, and "-1 to all rolls" reaches this bare HT roll as it does the
     * other four (consciousness, survival, stun recovery, a day's recovery).
     *
     * **It is live, and the tap order cannot make it wrong.** The value is
     * re-read from the trackers on every recomposition, so the obvious worry
     * is a roll made AFTER Apply charging the character for the very blow that
     * triggered it. Apply calls `onDismiss` in the same handler, so the panel
     * is gone before that state exists: this roll can only be made on the HP
     * the character walked in with. Checked rather than latched -- 6ch's
     * `spiAvailable` says a live pool must not be `remember`ed.
     */
    hurt: Int,
    /**
     * The Failure row's recovery roll, from
     * [com.pathways.rules.StunRecovery.targetFor]: printed on its button and
     * rolled as printed.
     *
     * It takes a shock the character walked in with, which the Major Wound
     * roll above does not: `Shock` refuses `RollKind.MAJOR_WOUND_HT` because
     * that roll CREATES the shock, and the recovery roll creates nothing. It
     * can only ever be a shock from an EARLIER wound. This hit's shock comes
     * from the Success row, and the recovery button is offered on the Failure
     * row, so the two are never the same -4.
     */
    recoveryTarget: Int,
    /**
     * The held rows' declared "-N to all rolls" (6gs), on BOTH rolls. Shock's
     * refusal of the Major Wound roll is that the roll creates it; withdrawal
     * or a phobia is carried in, the way [hurt] is.
     */
    states: ClauseBonus = ClauseBonus(0, null),
    onDismiss: () -> Unit,
) {
    val rules = remember { ContentLoader.spiritDamage() }
    val pools = remember(hasSpirituality) {
        if (hasSpirituality) Pool.entries else Pool.entries.filter { it != Pool.SPI }
    }
    var amount by remember { mutableIntStateOf(1) }
    var pool by remember { mutableStateOf(Pool.HP) }
    var shock by remember { mutableStateOf<RollResult?>(null) }
    // Keyed on the wound roll, never cleared by hand: a recovery roll belongs
    // to the stun that roll produced, so it dies with it. More than one site
    // clears `shock` and adding a line to each is how one gets missed — the
    // key IS the guard (6cp), and it resets through null on every path.
    var recovery by remember(shock) { mutableStateOf<RollResult?>(null) }
    // Optional, and null by default: a hit with no called shot behind it is
    // the common case, and the book's own effect column has nothing to say
    // about it. Only HP carries these -- every threshold in that column is
    // written against HP.
    var landed by remember { mutableStateOf<HitLocation?>(null) }
    // Apply dismisses, and a second tap of the same picture still reaches its
    // handler (6ei): the hit landed twice, and on SPI the second one is Soul
    // Debt. Nothing clears this; the dialog is gone once it is set.
    var applied by remember { mutableStateOf(false) }

    // Only HP has a Major Wound threshold: the rule is written against "half
    // your HP", and FP and SPI print no equivalent.
    val isMajor = pool == Pool.HP && Wounds.isMajorWound(amount, maxHp)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (applied) return@Button
                    applied = true
                    // Latched with the hit, never with the roll (Codex): a GM
                    // who rolls the table and then cancels, changes the pool
                    // or drops the damage below the threshold has applied no
                    // wound, and a -4 for a wound nobody took would follow
                    // them into every later roll. The stepper and the pool
                    // chips already clear the roll, so reaching here means
                    // this roll belongs to this hit.
                    if (isMajor && shock?.let { Shock.causedBy(Wounds.majorWoundFor(it)) } == true) {
                        onShocked()
                    }
                    // The same latch, for the stun: unless the recovery roll
                    // beside it already won it back.
                    if (isMajor && shock?.let { StunRecovery.leavesStunned(Wounds.majorWoundFor(it), recovery) } == true) {
                        onStunned()
                    }
                    onApply(pool, amount)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("Apply $amount to $pool") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Damage") },
        text = {
            // Scrolls: the hit locations and the Major Wound panel do not
            // fit a phone, and an AlertDialog does not scroll its own text slot.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AmountCard(
                    amount = amount,
                    // The threshold as a tag under the number it is about,
                    // lit once this one hit crosses it.
                    threshold = Wounds.majorWoundThreshold(maxHp).let {
                        if (isMajor) "Major wound: $it or more in one hit" else "Major wound at $it+"
                    },
                    isMajor = isMajor,
                    onStep = { delta ->
                        // Up is always a hit; down stops at one (see [step]).
                        val next = if (delta > 0) amount + delta else step(amount, delta)
                        next?.let { amount = it; shock = null }
                    },
                )
                SectionLabel("Pool")
                Row {
                    pools.forEach { option ->
                        FilterChip(
                            selected = pool == option,
                            // The roll belongs to the hit it was rolled for.
                            // Moving it to another pool makes it a different
                            // hit, so its shock result is no longer an answer.
                            // The location goes with the shock, and for the
                            // same reason: moving the damage to another pool
                            // makes it a different hit. Without this the
                            // picker's choice survives out of sight while the
                            // amount changes, and coming back to HP shows an
                            // effect fired for a hit nobody described.
                            onClick = { pool = option; shock = null; landed = null },
                            label = { Text(option.name) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                if (!hasSpirituality) {
                    Text(
                        "No SPI: a non-Beyonder has no spirituality to sacrifice.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                // ONE expression, printed and rolled. It was written out
                // twice, which is the shape that let a caption and a roll drift
                // apart three times in this codebase (6ag, 6ai, 6be) -- and a
                // third term is exactly the edit that would have split them.
                val htTarget = ht + majorWoundHt.bonus + hurt + states.total
                MajorWoundPanel(
                    isMajor = isMajor,
                    isHp = pool == Pool.HP,
                    ht = htTarget,
                    traitLine = majorWoundHt.line,
                    statesLine = states.line,
                    shock = shock,
                    // Rolled WITHOUT any shock of its own: this is the roll
                    // that creates the state, and Shock refuses to reach
                    // RollKind.MAJOR_WOUND_HT for that reason. Reeling and
                    // fatigue are inside [htTarget], because the character was
                    // already carrying those when the blow landed.
                    // One roll a wound: a second tap of the same picture
                    // would roll again and keep the second (6p).
                    // And only on a hit that is major NOW: the button is drawn
                    // below the threshold too, and a stale tap of a major
                    // picture would roll for a hit since stepped down.
                    onRoll = {
                        if (shock == null && pool == Pool.HP && Wounds.isMajorWound(amount, maxHp)) {
                            shock = SuccessRoll.against(htTarget, dice.dice("Major wound HT"))
                        }
                    },
                    recoveryTarget = recoveryTarget,
                    recoveryLine = stunRecoveryHt.line,
                    recovery = recovery,
                    onRecoveryRoll = {
                        if (recovery == null) recovery = SuccessRoll.against(recoveryTarget, dice.dice("Stun recovery"))
                    },
                )
                if (pool == Pool.HP) {
                    WhereItLanded(
                        landed = landed,
                        damage = amount,
                        maxHp = maxHp,
                        onLocation = { landed = if (landed == it) null else it },
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                // Reference for the GM, not a control: closed until asked (6gl).
                RuleNote("Spirit body damage rules") {
                    var section = ""
                    rules.forEach {
                        if (it.section != section) {
                            section = it.section
                            Text(
                                section,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Text(it.rule, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
    )
}

/** The threshold, and the HT roll the book asks for once it is crossed. */
@Composable
private fun MajorWoundPanel(
    isMajor: Boolean,
    /**
     * Drawn, disabled, for every HP hit below the threshold: appearing at
     * the threshold grew the dialog, which re-centred and moved the stepper
     * under the finger (play session 1, at 5 damage). 6gn's rule.
     */
    isHp: Boolean,
    ht: Int,
    traitLine: String? = null,
    statesLine: String? = null,
    shock: RollResult?,
    onRoll: () -> Unit,
    recoveryTarget: Int,
    recoveryLine: String? = null,
    recovery: RollResult?,
    onRecoveryRoll: () -> Unit,
) {
    if (!isMajor && !isHp) return
    if (shock == null) {
        OutlinedButton(onClick = onRoll, enabled = isMajor, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Filled.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Roll HT $ht", modifier = Modifier.padding(start = 8.dp))
        }
        if (traitLine != null) {
            Text(traitLine, style = MaterialTheme.typography.bodySmall)
        }
        statesLine?.let { Text("includes $it", style = MaterialTheme.typography.bodySmall) }
        return
    }
    // The roll's own target, as the recovery line below prints its own.
    Text("3d6: ${shock.total} vs ${shock.target}", style = MaterialTheme.typography.titleMedium)
    val row = Wounds.majorWoundFor(shock)
    Text(
        "${row.result} — ${row.effect}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
    )
    if (!StunRecovery.stunsOn(row)) return
    if (recovery == null) {
        // The button says "now" because the duration is the GM's: the row
        // itself asks for one of these a turn and this app holds no clock,
        // so a bare "Recover" would promise a rule the book does not print.
        TextButton(onClick = onRecoveryRoll) { Text("Roll HT $recoveryTarget to recover now") }
        if (recoveryLine != null) {
            Text(recoveryLine, style = MaterialTheme.typography.bodySmall)
        }
        statesLine?.let { Text("includes $it", style = MaterialTheme.typography.bodySmall) }
        return
    }
    // The roll's OWN target and verdict. Re-judging a stored roll against a
    // recomputed target lets a tracker moving under an open dialog reprint an
    // old roll against a number it was never made against (Codex, 6ds).
    Text("3d6: ${recovery.total} vs ${recovery.target}", style = MaterialTheme.typography.titleMedium)
    Text(
        if (recovery.outcome.isSuccess) "Recovered early." else "Still stunned; the row asks again next turn.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * The hit location's own effect column (Ch. 5, p. 41), which had no reader.
 *
 * **This picker does NOT re-price the damage.** The wounding multiplier is
 * the attacker's business and `AttackDialog` already prints it ("×4 there"),
 * so the number typed above is the injury that got through. Choosing a
 * location here asks one question the app could not answer before: what does
 * the book say a hit THERE, of THIS size, on THIS character, triggers.
 *
 * **It reports a trigger and never applies a state.** Crippling is "unusable
 * until healed" and this app tracks no such duration, so nothing is marked
 * and nothing is stored — the same call `Wounds.effectsAt` records, one layer
 * down. The row's printed cell and the paragraph governing it are shown
 * whole, because they say what the thresholds cannot.
 *
 * Offered on HP only: every threshold in that column is written against HP,
 * and FP and SPI print no equivalent — the reading the Major Wound panel one
 * control up already makes.
 */
@Composable
private fun WhereItLanded(
    landed: HitLocation?,
    damage: Int,
    maxHp: Int,
    onLocation: (HitLocation) -> Unit,
) {
    val locations = remember { Wounds.locations() }
    SectionLabel("Where it landed")
    FlowRow {
        locations.forEach { option ->
            FilterChip(
                selected = option == landed,
                onClick = { onLocation(option) },
                label = { Text(option.target) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
    landed?.let { location ->
        val fired = Wounds.effectsAt(location, damage, maxHp)
        Text(
            if (fired.isEmpty()) "Nothing this hit triggers there."
            else fired.joinToString("; ") { row ->
                val at = Wounds.thresholdFor(location, row.outcome, maxHp)
                "${row.outcome} (at $at${if (row.isApproximate) ", approx." else ""})"
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            location.effect,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (fired.any { it.outcome == "Crippled" }) {
            Text(
                Wounds.cripplingRule(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * How much got through, large, with steps of one and five either way, and the
 * Major Wound threshold under it as a tag, lit in the error colour once
 * crossed.
 */
@Composable
private fun AmountCard(amount: Int, threshold: String, isMajor: Boolean, onStep: (Int) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Damage",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton("−5") { onStep(-5) }
                StepButton("−") { onStep(-1) }
                Text(
                    "$amount",
                    style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 56.dp),
                )
                StepButton("+") { onStep(1) }
                StepButton("+5") { onStep(5) }
            }
            val tone = if (isMajor) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                threshold,
                style = MaterialTheme.typography.labelLarge,
                color = tone,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .border(1.dp, tone.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * 40dp, so four of them and the amount fit a 360dp phone's dialog (about
 * 232dp of 264); Material still gives each a 48dp touch target.
 */
@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.padding(horizontal = 2.dp).size(40.dp),
    ) { Text(text) }
}

/** One damage is the smallest hit there is; zero is not a hit at all. */
private fun step(amount: Int, delta: Int): Int? = (amount + delta).takeIf { it >= 1 }
