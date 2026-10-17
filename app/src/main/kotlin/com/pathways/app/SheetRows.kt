package com.pathways.app

import com.pathways.rules.AttributeRoll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
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
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.pathways.model.RollResult
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.rules.BurnKind
import com.pathways.rules.ClauseBonus
import com.pathways.rules.EffortBurn
import com.pathways.rules.PenaltyScope
import com.pathways.rules.NaturalRecovery
import com.pathways.rules.Pool
import com.pathways.rules.PoolStatus
import com.pathways.rules.Pools
import com.pathways.rules.StunRecovery
import com.pathways.rules.SuccessRoll
import com.pathways.rules.VitalCheck
import com.pathways.rules.VitalRolls
import com.pathways.rules.TraitReport

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
private fun VitalRollRow(check: VitalCheck, target: Int, fromTraits: Int, ht: Int, poolPenalty: Int, shock: Int, states: ClauseBonus) {
    val dice = LocalDice.current
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
                if (shock != 0) add("$shock shock")
                states.line?.let { add(it) }
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
                    outcome = SuccessRoll.against(target, dice.dice(if (check == VitalCheck.SURVIVAL) "HT to survive" else "HT to stay conscious"))
                },
                // Named by the book's own row, because below -HP BOTH rolls
                // are offered and their targets differ (6cn).
            ) { Text("Roll HT $target — ${VitalRolls.rowFor(check)}") }
        } else {
            Text(
                "3d6: ${result.total} vs $target - ${label(result.outcome).lowercase()}",
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
private fun RecoveryRollRow(ht: Int, traits: List<Trait>, poolPenalty: Int, states: ClauseBonus) {
    val dice = LocalDice.current
    var modifier by remember { mutableIntStateOf(0) }
    var outcome by remember { mutableStateOf<RollResult?>(null) }
    val fromTraits = NaturalRecovery.bonusFor(traits)
    val target = NaturalRecovery.targetFor(ht, traits, poolPenalty, modifier, states.total)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        // The book's rows and the note on what is entered by hand are
        // reference, closed (6gl): open, three rows -- two for bodies of 20
        // HP and more -- and two sentences read as a wall (play session 4).
        RuleNote("Natural recovery") {
            Column {
                NaturalRecovery.rows().forEach {
                    Text("${it.method}: ${it.recovered}. ${it.notes}", style = MaterialTheme.typography.bodySmall)
                }
                // A potion's advantages are prose, so a grant that moves this
                // roll is held by no trait. Said, or a zero reads as "none" --
                // and scoped to what the line below does not show, because the
                // all-HT-roll traits ARE applied and must not be entered twice.
                Text(
                    "Anything else that moves the daily HT roll - a potion's grant, say - is entered " +
                        "with −1 / +1. Only what the line under them shows is applied for you.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
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
                    states.line?.let { add(it) }
                    if (modifier > 0) add("+$modifier entered")
                    if (modifier < 0) add("$modifier entered")
                }.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = {
                    if (outcome != null) return@TextButton
                    outcome = SuccessRoll.against(target, dice.dice("Natural recovery"))
                },
            ) { Text("Roll HT $target — a day of rest and food") }
        } else {
            Text(
                "3d6: ${result.total} vs ${result.target} - ${label(result.outcome).lowercase()}",
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
 * A held stun, with the roll its own row prints: "roll HT each turn to
 * recover early". The Damage dialog offered that roll once, before Apply,
 * and a stun then outlived its wound by weeks, shown only inside Defend (play
 * session 5). A success clears it; the duration stays the GM's, so Clear is
 * here too.
 */
@Composable
internal fun StunRow(target: Int, onClear: () -> Unit) {
    val dice = LocalDice.current
    var outcome by remember(target) { mutableStateOf<RollResult?>(null) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Stunned — \"${StunRecovery.printed()}\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) { Text("Clear") }
        }
        outcome?.let {
            Text(
                "3d6: ${it.total} vs $target - ${label(it.outcome).lowercase()}, still stunned",
                style = MaterialTheme.typography.titleSmall,
            )
        }
        // Repeatable, one tap a turn: a second tap of the same picture is
        // dropped, as every repeatable roll here drops it (6ei).
        val drawn = outcome
        TextButton(
            onClick = {
                if (outcome !== drawn) return@TextButton
                val roll = SuccessRoll.against(target, dice.dice("Stun recovery"))
                // Stored either way, so a second tap of this picture is
                // dropped even on the success that clears the row (review).
                outcome = roll
                if (!StunRecovery.stillStunned(roll)) onClear()
            },
        ) { Text("Roll HT $target to recover") }
    }
}

/**
 * The rolls a pool's state calls for, under the strip that shows the pools
 * ([PoolStrip]): the HP table's two vital rolls and the recovery roll.
 */
@Composable
internal fun PoolTrackers(
    hp: PoolStatus,
    ht: Int,
    traits: List<Trait>,
    poolPenalty: Int,
    /**
     * Shock's "-4 to all rolls", which reaches the two vital rolls as it
     * reaches every bare attribute roll: neither creates it (6gs). Its own
     * term, so the row names it rather than counting it as hurt or tired. Not
     * the recovery roll: a day of rest outlasts a one-turn state.
     */
    shock: Int,
    /** The held rows' declared "-N to all rolls" (6gs), its own term likewise. */
    states: ClauseBonus,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        // Only where the table prints one: `Pools` has flagged these two
        // rolls since M0 and nothing could make them. See VitalRolls.
        VitalRolls.due(hp).forEach { check ->
            VitalRollRow(
                check = check,
                target = VitalRolls.targetFor(check, ht, traits, poolPenalty, shock, states.total),
                fromTraits = VitalRolls.bonusFor(check, traits),
                ht = ht,
                poolPenalty = poolPenalty,
                shock = shock,
                states = states,
            )
        }
        // The recovery table's one roll.
        if (NaturalRecovery.isOffered(hp)) RecoveryRollRow(ht, traits, poolPenalty, states)
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
    // The tag in the stamp for a potion's grant, a Beyonder fact, as the
    // pool strip colours SPI. The level stays ink: a red number means a
    // named harm state on the pool strip.
    val potion = provenance == "potion"
    val tint = if (potion) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    SkillRowFrame(Modifier.clickable(onClickLabel = "Roll", role = Role.Button, onClick = onClick)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        RowTag(provenance, tint)
        Spacer(Modifier.weight(1f))
        RollGlyph()
        LevelBadge("$level", MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun ComparisonRow(
    line: PlayedLine,
    modifier: Modifier = Modifier,
) {
    SkillRowFrame(modifier) {
        Text(line.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        // Neutral: the character having moved is not a disagreement.
        line.basis?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        line.disagreement?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        // Every row here rolls; the secondaries, which do not, are [VitalsCard]'s.
        RollGlyph()
        LevelBadge(line.number, MaterialTheme.colorScheme.primary)
    }
}

/**
 * The derived values as one card under the notice that counts the
 * disagreements. A row the published sheet disagrees with is tinted and
 * takes the accent, with the sheet's figure beside its name; none rolls.
 */
@Composable
internal fun VitalsCard(
    diffSummary: String?,
    lines: List<PlayedLine>,
    onRoll: (AttributeRoll.Kind) -> Unit,
) {
    // Ink, not the stamp: red means harm or the Beyonder (a9be8f0), and a
    // disagreement with the published sheet is neither.
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (diffSummary != null) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(2.dp, accent),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            ) {
                Text(diffSummary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            }
        }
        SheetSection("Secondary characteristics")
        Column {
                lines.forEachIndexed { i, line ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    val differs = line.disagreement != null
                    // Will and Per open their bare roll; the rest are numbers.
                    val rolls = AttributeRoll.Kind.entries.firstOrNull { it.label == line.label }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (rolls == null) {
                                    Modifier
                                } else {
                                    Modifier.clickable(
                                        onClickLabel = "Roll ${rolls.label}",
                                        role = Role.Button,
                                    ) { onRoll(rolls) }
                                },
                            )
                            .background(if (differs) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            line.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (differs) accent else Color.Unspecified,
                        )
                        Text(
                            listOfNotNull(line.disagreement, line.basis).joinToString("  "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (rolls != null) RollGlyph()
                        Text(
                            line.number,
                            style = MaterialTheme.typography.titleLarge.tabular(),
                            color = if (differs) accent else Color.Unspecified,
                        )
                    }
                }
        }
    }
}

/**
 * A list row: at least 56dp, one tap target when the caller makes it
 * clickable, ruled off from the next (Case File, frame 02).
 */
@Composable
private fun SkillRowFrame(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Column {
        Row(
            modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * The die that says a row rolls, boxed as the frame draws it.
 *
 * It replaced a "Roll" text button: the whole row is the target now, as the
 * rows below a Roll button always were, and a second target inside it was
 * a smaller copy of the same tap. The box is ink, not a button.
 */
@Composable
private fun RollGlyph() {
    Box(
        Modifier.size(40.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Casino,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The level the row rolls, boxed in 2 dp ink (Case File, frame 02). */
@Composable
internal fun LevelBadge(number: String, color: Color) {
    Box(
        // 40dp and titleMedium as before: the Attack dialog and the builder
        // print it too, and a bigger box would move their rows.
        Modifier.size(40.dp).border(2.dp, color, MaterialTheme.shapes.extraSmall),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number,
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = color,
        )
    }
}

/** A small outlined word beside a row's name: where a skill came from. */
@Composable
private fun RowTag(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
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
