package com.pathways.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.pathways.model.RollResult
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.rules.BurnKind
import com.pathways.rules.EffortBurn
import com.pathways.rules.PenaltyScope
import com.pathways.rules.NaturalRecovery
import com.pathways.rules.Pool
import com.pathways.rules.PoolStatus
import com.pathways.rules.Pools
import com.pathways.rules.SuccessRoll
import com.pathways.rules.VitalCheck
import com.pathways.rules.VitalRolls
import com.pathways.rules.TraitEffects

/** The pool maximums and Will as they stand after whatever has been drunk. */
internal data class Vitals(
    val maxHp: Int,
    val maxFp: Int,
    val maxSpi: Int,
    val will: Int,
    val per: Int,
    val dodge: Int,
)

/**
 * One of the HP table's own HT rolls, offered rather than made.
 *
 * The Major Wound's precedent (6n): the book says "make an immediate HT roll",
 * and a roll the GM did not ask for is one they cannot un-ask. The outcome is
 * deliberately NOT latched -- nothing in this app consumes unconsciousness,
 * and a stored fact with no reader is what this whole slice exists to remove.
 *
 * Keyed on the target, so a character healed or hurt between rolls does not
 * keep an outcome that belonged to a different number (6k, 6y).
 */
@Composable
private fun VitalRollRow(check: VitalCheck, target: Int, fromTraits: Int, ht: Int, poolPenalty: Int) {
    var outcome by remember(check, target) { mutableStateOf<RollResult?>(null) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(VitalRolls.printed(check), style = MaterialTheme.typography.bodySmall)
        // The parts, because they have to sum to the number below them (6ag,
        // 6ai). Traits are one term rather than named individually: the two
        // rules that reach these rolls are scoped in VitalRolls, and a screen
        // re-deciding which applied would be the second copy.
        Text(
            buildList {
                add("HT $ht")
                if (fromTraits > 0) add("+$fromTraits from traits")
                if (fromTraits < 0) add("$fromTraits from traits")
                if (poolPenalty != 0) add("$poolPenalty hurt or tired")
            }.joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
        )
        val result = outcome
        if (result == null) {
            TextButton(
                onClick = {
                    // The re-entrancy guard every roll button in this app
                    // needs: two taps can both reach the handler before the
                    // button leaves the tree (6p).
                    if (outcome != null) return@TextButton
                    outcome = SuccessRoll.against(target, appDice)
                },
                // Named by the book's own row, because below -HP BOTH rolls
                // are offered and their targets differ (6cn).
            ) { Text("Roll HT $target — ${VitalRolls.rowFor(check)}") }
        } else {
            Text(
                "3d6: ${result.total} vs $target - ${result.outcome.name.lowercase()}",
                style = MaterialTheme.typography.titleSmall,
            )
            // The book asks for one of these A TURN and this app holds no
            // clock, so the repetition is said rather than run. Silence would
            // let one roll read as the whole rule (6bl).
            Text(
                "The book asks for this roll each turn while the threshold holds - " +
                    "that repetition is the GM's to run.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { outcome = null }) { Text("Roll again") }
        }
    }
}

/**
 * The book's cell for one held trait and, under it, what of that cell is not
 * applied. Shared by the advantage and drawback rows, so neither can print a
 * caveat the other drops.
 */
@Composable
internal fun HeldCell(row: TraitEffects.HeldTrait) {
    row.cell?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    for (note in row.notes) {
        Text(
            // A note on the whole cell would print the cell twice; a note on
            // one clause of it says which. Muted, not brass: this is a caveat
            // about what the app does NOT do, and painting it the accent
            // colour made every trait row shout in the same voice as the Roll
            // button it is not (docs/DESIGN-DIRECTION.md).
            (if (note.printed == row.cell) "" else "${note.printed}. ") +
                "Not applied here: ${note.reason}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The recovery table's daily HT roll, offered while the character is hurt.
 *
 * The DAY is whoever taps the button's to declare, as a new round is: this app
 * holds no clock, and the roll does not need one. Nothing is restored here --
 * the book's rows are printed and the HP stepper above applies them, because
 * the bands for a larger body and "Very Rapid Healing" are readings this
 * screen would otherwise make in silence. See [NaturalRecovery].
 *
 * NOT keyed on the target, unlike [VitalRollRow]: the HP a success restores
 * can lift the pool penalty, and the result has to outlive the taps that
 * apply it. It prints the target it was rolled against, so it cannot be read
 * against a later one.
 */
@Composable
private fun RecoveryRollRow(ht: Int, traits: List<Trait>, poolPenalty: Int) {
    var modifier by remember { mutableIntStateOf(0) }
    var outcome by remember { mutableStateOf<RollResult?>(null) }
    val fromTraits = NaturalRecovery.bonusFor(traits)
    val target = NaturalRecovery.targetFor(ht, traits, poolPenalty, modifier)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        NaturalRecovery.rows().forEach {
            Text("${it.method}: ${it.recovered}. ${it.notes}", style = MaterialTheme.typography.bodySmall)
        }
        val result = outcome
        if (result == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { modifier -= 1 }) { Text("−1") }
                Text(
                    (if (modifier > 0) "+" else "") + modifier,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                TextButton(onClick = { modifier += 1 }) { Text("+1") }
            }
            Text(
                buildList {
                    add("HT $ht")
                    if (fromTraits > 0) add("+$fromTraits from traits")
                    if (fromTraits < 0) add("$fromTraits from traits")
                    if (poolPenalty != 0) add("$poolPenalty hurt or tired")
                    if (modifier > 0) add("+$modifier entered")
                    if (modifier < 0) add("$modifier entered")
                }.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
            )
            // A potion's advantages are prose, so a grant that moves this
            // roll is held by no trait. Said, or a zero reads as "none" --
            // and scoped to what the line above does not show, because the
            // all-HT-roll traits ARE applied and must not be entered twice.
            Text(
                "Anything else that moves the daily HT roll - a potion's grant, say - is entered " +
                    "with −1 / +1. Only what the line above shows is applied for you.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = {
                    if (outcome != null) return@TextButton
                    outcome = SuccessRoll.against(target, appDice)
                },
            ) { Text("Roll HT $target — a day of rest and food") }
        } else {
            Text(
                "3d6: ${result.total} vs ${result.target} - ${result.outcome.name.lowercase()}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                NaturalRecovery.dayLine(result.outcome.isSuccess, traits),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { outcome = null }) { Text("Another day") }
        }
    }
}

/**
 * HP, FP and SPI at the table (Ch. 5).
 *
 * SPI is shown only for a Beyonder: "Non-Beyonders (SPI 0) ... have no
 * spirituality to sacrifice" (p. 41), and [Pools.spi] refuses a maximum of
 * zero rather than reporting a mortal as Spiritually Exhausted.
 */
@Composable
internal fun PoolTrackers(
    session: BeyonderSession,
    vitals: Vitals,
    maxCor: Int,
    hp: PoolStatus,
    ht: Int,
    traits: List<Trait>,
    poolPenalty: Int,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        PoolRow(hp) {
            session.spend(Pool.HP, it, vitals.maxHp, maxCor)
        }
        // Under the row that produced them, and only where the table prints
        // one: `Pools` has flagged these two rolls since M0 and nothing could
        // make them. See VitalRolls.
        VitalRolls.due(hp).forEach { check ->
            VitalRollRow(
                check = check,
                target = VitalRolls.targetFor(check, ht, traits, poolPenalty),
                fromTraits = VitalRolls.bonusFor(check, traits),
                ht = ht,
                poolPenalty = poolPenalty,
            )
        }
        // The recovery table's one roll, under the pool it restores.
        if (NaturalRecovery.isOffered(hp)) RecoveryRollRow(ht, traits, poolPenalty)
        PoolRow(Pools.fp(vitals.maxFp - session.fpLost, vitals.maxFp)) {
            session.spend(Pool.FP, it, vitals.maxFp, maxCor)
        }
        if (vitals.maxSpi > 0) {
            PoolRow(Pools.spi(vitals.maxSpi - session.spiSpent, vitals.maxSpi)) {
                session.spend(Pool.SPI, it, vitals.maxSpi, maxCor)
            }
        }
        HorizontalDivider()
    }
}

/**
 * One pool: where it stands, what the book says about standing there, and a
 * stepper.
 *
 * ponytail: -1/+1 only. Damage is rolled at the table off dice this app does
 * not own, and a "take N damage" field is a keyboard in the middle of a sheet.
 *
 * Soul Debt, "+1 CoR per 1 point below 0" (p. 35), is CHARGED by
 * [BeyonderSession.spend] since `ea6ae38`, one way: stepping back up refunds
 * no CoR, because CoR from Soul Debt is permanent (p. 41). This said
 * "reported, not applied" until 6fd.
 */
@Composable
private fun PoolRow(status: PoolStatus, onSpend: (Int) -> Unit) {
    val bad = status.penalty != 0 || status.isDead || status.isUnconscious
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${status.pool} ${status.current} / ${status.max}",
                style = MaterialTheme.typography.titleMedium,
                color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (bad) {
                Text(
                    buildList {
                        add(status.level)
                        // The number first, with what it applies to: -3 for
                        // Spiritual Attrition is SPI rolls only, and a GM
                        // reading it as -3 to everything would be the whole
                        // point of carrying the scope.
                        if (status.penalty != 0) {
                            val what = when (status.scope) {
                                PenaltyScope.SPI_ROLLS -> "SPI rolls and ability checks"
                                PenaltyScope.ALL_ROLLS -> "all rolls"
                            }
                            add("${status.penalty} to $what")
                        }
                        // Reported as DONE, not as a chore. It used to read
                        // "add N CoR on the pathway screen", which was honest
                        // while nothing charged it and became an instruction
                        // to double-count the moment something did. Found on
                        // device, in the same slice that applied the charge.
                        if (status.corGained > 0) add("${status.corGained} CoR charged")
                        add(status.effect)
                    }.joinToString(" — "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        // Outlined and neutral, not brass text. Three pools times two steppers
        // put six accent-coloured controls on screen at once, which is most of
        // why the sheet read as one yellow note; and an outline says "button"
        // more plainly than colour does.
        OutlinedIconButton(onClick = { onSpend(1) }) { Text("−1") }
        OutlinedIconButton(onClick = { onSpend(-1) }) { Text("+1") }
    }
}

/**
 * A skill the sheet screen can roll, wherever it came from.
 *
 * A published skill resolves its level off the sheet; one bought in play
 * resolves it off the digested skill map, and has no published column to be
 * compared against at all. The dialog needs neither distinction -- only a
 * label, a level and a key -- so the two paths meet here rather than in the
 * dialog's parameter list.
 */
internal data class RollableSkill(val label: String, val base: Int, val key: SkillKey)

/**
 * The three thirds of a character sheet.
 *
 * Order is the reading order of a physical sheet -- what you are, what you can
 * do, what is written against you -- but the DEFAULT is [SKILLS], because a
 * roll is why the screen gets opened at a table.
 */
enum class SheetTab(val label: String, val icon: ImageVector) {
    VITALS("Vitals", Icons.Filled.MonitorHeart),
    // The same die that sits on every Roll button in the list behind it, so
    // the destination and the thing it contains are marked the same way.
    SKILLS("Skills", Icons.Filled.Casino),
    TRAITS("Traits", Icons.Filled.Fingerprint),
}

/**
 * [key]'s label, with its difficulty named only where that is what tells it
 * apart from another row.
 *
 * Two rows can share a label and mean different skills: the Abyss grants
 * Knife/Average to a sheet that prints Knife/Easy, and p. 85 makes those two
 * skills at two levels (4.1 keeps the disagreement rather than repairing it).
 * Two rows reading "Knife" with different numbers is the unreconcilable screen
 * 6ag and 6ai were each fixed for -- and a suffix on every row instead would
 * be noise on the ~40 that need none.
 *
 * Checked against the whole added set rather than against the sheet, because a
 * bought Knife collides with a granted one exactly as a printed Knife does
 * (Codex).
 */
internal fun disambiguated(key: SkillKey, visible: List<SkillKey>): String {
    val label = key.label()
    val collides = visible.any { it != key && it.label() == label }
    return if (collides) "$label [${key.difficulty.code}]" else label
}

/**
 * A skill the published sheet does not print, shown so it can be rolled.
 *
 * Deliberately not a [ComparisonRow]: there is nothing to compare it to. The
 * published sheet never printed it, and saying "sheet: -" would invent a
 * disagreement where there is only an addition.
 *
 * [provenance] says where it came from, because the two are not the same
 * offer: a bought skill can be re-pointed in the builder and a granted one
 * cannot -- it has no points behind it, only the potion (M3).
 */
@Composable
internal fun AddedSkillRow(label: String, level: Int, provenance: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("$level", style = MaterialTheme.typography.bodyMedium)
        Text(
            // Arcane, not brass: a potion grant is a Beyonder fact, and the
            // gallery already uses this colour for exactly that. Brass is the
            // colour of a thing you can DO, and provenance is not one.
            " ($provenance)",
            style = MaterialTheme.typography.bodySmall,
            color = if (provenance == "potion") {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        // The same button the published rows carry. Without it, a sheet showed
        // Roll on ten skills and nothing on the three a potion granted, which
        // reads as "these cannot be rolled" -- an inconsistent affordance is
        // worse than none, because it makes a false statement about the data.
        RollButton(onClick)
    }
}

@Composable
internal fun ComparisonRow(
    line: PlayedLine,
    modifier: Modifier = Modifier,
    // Present only on a row that can be rolled. The secondaries above the
    // skills share this composable and are not rollable, so the button is
    // absent rather than disabled -- a control that cannot do anything should
    // not be drawn.
    onRoll: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(line.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(line.number, style = MaterialTheme.typography.bodyMedium)
        // Neutral: the character having moved is not a disagreement.
        line.basis?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        line.disagreement?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (onRoll != null) {
            RollButton(onRoll)
        }
    }
}

/**
 * The affordance that used to be a sentence.
 *
 * Rolling a skill is the single most-used interaction in the app and it was
 * reachable only by tapping a row that gave no sign it was tappable. A line of
 * help text was added and then removed again: a sentence teaching an
 * affordance means the affordance is missing (docs/DESIGN-DIRECTION.md).
 *
 * The row stays clickable as well, so the larger target still works.
 */
@Composable
private fun RollButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(start = 4.dp)) {
        Icon(
            Icons.Filled.Casino,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Text(" Roll", style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Ch. 2's burn (p. 9), offered once the dice are known.
 *
 * One pool per roll, and which one is decided by the SKILL, not by the
 * player: the FP row names ST/DX/HT rolls and the SPI row names SPI-based
 * ones, so a roll can only ever reach one of them and the book's "you cannot
 * spend both" cannot be violated from here. An IQ, Will or Per skill is named
 * by neither row and gets no burn at all, which is why this row is absent
 * rather than empty on most rolls.
 */
@Composable
internal fun BurnRow(
    kind: BurnKind,
    burned: Int,
    spiAvailable: Int,
    /**
     * What this screen's own chapter adds to chapter 2's row, or null.
     *
     * The ritual and divination screens have already charged a cost, and the
     * book is explicit that the push is "separate from the ritual's fuel
     * cost" -- a caster reading a second SPI figure needs that sentence, and
     * the plain skill roll, which charged nothing, does not.
     */
    note: String? = null,
    onBurn: (Int) -> Unit,
) {
    val pool = EffortBurn.pool(kind)
    val cap = pool.max
    // Ch. 7 Power Source A: "SPI cannot be reduced below 0." The burn is a
    // CHOICE, so it is capped by what the caster has -- the floor 6ar applied
    // to the ritual, boon, artifact and divination dialogs and missed here,
    // in the one SPI spend that is not a dialog of its own. FP is deliberately
    // untouched: p. 42 prices FP below 0 at 1 HP per point, so an unbounded
    // physical push is the book's own rule, and treating the two pools alike
    // is exactly the merge 6ar was written to undo.
    val affordable = EffortBurn.affordable(kind, burned, spiAvailable)
    if (affordable == 0 && kind == BurnKind.MYSTICAL) {
        Text(
            "Push the roll — ${pool.pool} on ${pool.scope}",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            "No push — this character has no spirituality left, and personal " +
                "SPI cannot be spent below 0.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    Text(
        "Push the roll — ${pool.pool} on ${pool.scope}",
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 12.dp),
    )
    Text(
        if (cap == null) {
            "1 ${pool.pool} buys +1, and the book prints no cap. Spent so far: $burned."
        } else {
            "1 ${pool.pool} buys +1, at most +$cap per roll. Spent so far: $burned."
        },
        style = MaterialTheme.typography.bodySmall,
    )
    note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    // Four steps, or fewer when the printed cap is closer. The uncapped pool
    // is not offered without limit: the book names no ceiling, and a stepper
    // running to a hundred is not a choice worth making at the table -- the
    // same reading the open-ended ritual fuel cell already takes (6q).
    val steps = (1..minOf(4, affordable)).toList()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEach { points ->
            FilterChip(
                selected = false,
                onClick = { onBurn(points) },
                label = { Text("+$points") },
            )
        }
    }
}
