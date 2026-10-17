package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.pathways.model.RollResult
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.rules.ActiveDefenses
import com.pathways.rules.DefenseBoost
import com.pathways.rules.DefenseRefusal
import com.pathways.rules.Facing
import com.pathways.rules.ShieldCoverage
import com.pathways.rules.Shields
import com.pathways.rules.content.Shield

/**
 * Which shield is Readied, if any.
 *
 * Chips WRAP rather than scroll sideways: a horizontally scrolling Row inside
 * an AlertDialog never receives the drag on a 1080px phone, which cost the
 * attack dialog three of its five maneuvers before it was found on device.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShieldPicker(
    shields: List<Shield>,
    carried: Shield?,
    coverage: ShieldCoverage,
    onPick: (Shield) -> Unit,
) {
    SectionLabel("Shield Readied")
    FlowRow {
        shields.forEach { option ->
            FilterChip(
                selected = option == carried,
                onClick = { onPick(option) },
                // The parenthesised examples are what makes the row long;
                // the word before them is what the GM says out loud.
                label = { Text("${option.type.substringBefore(" ")} +${option.db}") },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
    // BELOW the chips: above them, readying a shield inserted this line and
    // pushed the chips down under the finger (play session 6).
    // One note per coverage state, because the old single note told the GM to
    // hand-check the rear -- which the app now does -- while saying nothing
    // about the side, which it still cannot decide. A disclaimer that outlives
    // the thing it disclaims is the defect 6bl found on this very screen.
    if (carried != null) {
        DefenseNote(
            when (coverage) {
                ShieldCoverage.COUNTS ->
                    "Arrow shows the DB applied: the book grants it from the front."
                // Deliberately NOT the COUNTS lead-in. Both states began
                // "Arrow shows the DB applied", and a GM who stops after the
                // first sentence reads the app as having decided a side the
                // book leaves open (a review's finding). The verb carries the
                // difference, in the clause a skimmer actually reaches.
                ShieldCoverage.GM_DECIDES ->
                    "Arrow OFFERS the DB — it is not applied. From the side it " +
                        "counts only if that is your shield side, and the book " +
                        "names no hand, so the call is yours."
                ShieldCoverage.WITHHELD ->
                    "No DB from behind: \"${Shields.PRINTED_ARC}\". The rows below " +
                        "are the unshielded numbers."
            },
        )
    }
}

/**
 * The round boundary p. 36 names, live only once there is something to end.
 *
 * Disabled while nothing is spent, because a control that clears nothing
 * invites the tap that later clears something -- and what it clears includes a
 * boost the GM has already paid FP for, which no refund rule in this book
 * undoes. Drawn from the start all the same: hidden, its first appearance
 * pushed every row down under the finger that ticked Retreat (play session 5).
 */
@Composable
internal fun NewRoundControl(round: RoundState, onNewRound: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        // Not "spent": a declared surprise is enough to show this, and it
        // spends nothing (play session 2).
        Text(
            "What is marked here holds until the next round.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(end = 8.dp),
        )
        TextButton(onClick = onNewRound, enabled = round.isSpent()) { Text("New round") }
    }
}

/**
 * The Dive!: the second Defense Modifiers bullet with a number this app can
 * reach (Ch. 5, p. 37).
 *
 * "[Free Action] [Dive!] +3 Dodge vs. ranged only. Spend 1 turn getting back up
 * (Change Posture)." Extracted since 6g and applied by nothing until now.
 *
 * A chip rather than a roll, unlike its neighbour: this bullet asks for no die,
 * only for the GM to say the character went flat. Both of its scopes are
 * decided in [ActiveDefenses.dive]; what arrives here is [bonus], the answer.
 *
 * **[bonus] is read rather than [taken] re-tested against the attack.** A zero
 * while the chip is on means the rule refused it, which is exactly the sentence
 * the note needs — and asking `incoming` a second time here is how one rule
 * becomes two disagreeing copies (6ag, 6ai, 6be).
 *
 * The turn spent getting back up is SAID and not enforced: this app holds no
 * clock (6bf), the same split [StunPicker] takes on recovering from a stun.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiveControl(taken: Boolean, bonus: Int, onToggle: () -> Unit) {
    DefenseNote(ActiveDefenses.printedDive())
    FlowRow {
        FilterChip(
            selected = taken,
            onClick = { onToggle() },
            label = { Text("Dived") },
            modifier = Modifier.padding(end = 8.dp),
        )
    }
    if (!taken) return
    // A chip that is on while the row above it has not moved would read as a
    // tick that failed -- the reason a cancelled refusal is SAID rather than
    // silently dropped two controls up.
    if (bonus == 0) {
        DefenseNote(
            "Not against this attack: the bullet is \"vs. ranged only\", so the " +
                "+${ActiveDefenses.DIVE_BONUS} lands on nothing. Pick \"A shot\" above.",
        )
    }
    DefenseNote(
        "Getting back up costs a turn (Change Posture) — the GM's to spend; " +
            "this app holds no clock to run it against.",
    )
}

/**
 * The Retreat: the third Defense Modifiers bullet with a number, and the first
 * that moves more than the Dodge row (Ch. 5, p. 37).
 *
 * "[Free Action] [Retreat] Step back 1 hex: +3 Dodge / +1 Block / +1 Parry vs.
 * 1 melee attack per round." Extracted since 6g and applied by nothing until
 * now.
 *
 * A chip rather than a roll, like [DiveControl]: the bullet asks for no die,
 * only for the GM to say the character gave ground. Its scope is decided in
 * [ActiveDefenses.retreat], and what arrives here is [bonus] -- the answer on
 * the Dodge row, which answers for all three, because one melee test allows or
 * refuses every one of them.
 *
 * **[bonus] is read rather than the attack re-tested here**, for the reason
 * [DiveControl] states: a zero while the chip is on is exactly the sentence the
 * note needs, and asking `incoming` a second time is how one rule becomes two
 * disagreeing copies (6ag, 6ai, 6be).
 *
 * WHICH melee attack is the one is SAID and not decided, the split
 * [DiveControl] takes on the turn spent standing up: [RoundState] holds the
 * chip and **New round** clears it (6cp), but this screen sees no attack to
 * count, and the parry stepper below is the GM's for the same reason.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RetreatControl(taken: Boolean, bonus: Int, onToggle: () -> Unit) {
    DefenseNote(ActiveDefenses.printedRetreat())
    FlowRow {
        FilterChip(
            selected = taken,
            onClick = { onToggle() },
            // Named from the bullet's own verb, not from the rule's effect: the
            // GM ticks what the character DID, and every other chip on this
            // screen is worded that way.
            label = { Text("Stepped back") },
            modifier = Modifier.padding(end = 8.dp),
        )
    }
    // Said before the tick too, so ticking moves no row under the finger
    // (play session 5): the sentence is the rule's, true either way.
    if (!taken) {
        DefenseNote(RETREAT_ONCE)
        return
    }
    // A chip that is on while no row has moved would read as a tick that failed
    // -- the reason a cancelled refusal is SAID rather than silently dropped.
    if (bonus == 0) {
        DefenseNote(
            "Not against this attack: the bullet is \"vs. 1 melee attack\", so " +
                "nothing lands. Pick a bare hand or a weapon above.",
        )
        return
    }
    DefenseNote(RETREAT_ONCE)
}

private const val RETREAT_ONCE =
    "One melee attack this round — which one is the GM's to count. New round clears this."

/**
 * Which +2 was paid for, and on which row.
 *
 * Two chips rather than one, because the book prints two rules at different
 * prices with different drawbacks, and a GM has to say which they paid. The
 * ROW is part of the same choice for the same reason the option is: "+2 to
 * one defence" is meaningless without naming the one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BoostPicker(
    rows: List<String>,
    chosen: Pair<DefenseBoost, String>?,
    onPick: (Pair<DefenseBoost, String>?) -> Unit,
) {
    // Nothing to buy when no defence stands -- but a boost already paid for
    // still needs its clear button, so an empty row list is not an early exit
    // when one is held.
    if (rows.isEmpty() && chosen == null) return
    SectionLabel("Buy +${ActiveDefenses.BOOST_BONUS} on one defence")
    DefenseBoost.entries.forEach { boost ->
        val fp = ActiveDefenses.fpCostOf(boost)
        Text(
            "${boost.printed} — $fp FP: ${ActiveDefenses.printedBoost(boost)}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        FlowRow {
            rows.forEach { row ->
                val isChosen = chosen == boost to row
                FilterChip(
                    selected = isChosen,
                    // Tapping the chosen chip clears it, which is the only way
                    // back -- a choice with no way out is the defect the
                    // wealth picker was fixed for (6u).
                    onClick = { onPick(if (isChosen) null else boost to row) },
                    // While one is paid for, the others are DISABLED rather
                    // than swapping: swapping charged twice and showed one +2
                    // (Codex), and nothing here can refund the first payment,
                    // because the book spends the FP on the declaration.
                    // Clearing first is the honest way to change your mind.
                    enabled = chosen == null || isChosen,
                    label = { Text(row) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
    chosen?.let { (boost, row) ->
        DefenseNote("Paid ${ActiveDefenses.fpCostOf(boost)} FP: +${ActiveDefenses.BOOST_BONUS} on $row this turn.")
        // Always reachable, even when the paid row is not on screen: declaring
        // a shot withdraws an unarmed parry, and with only the chips to clear
        // with, a boost bought on that row left every control disabled
        // (Codex). The FP stays spent -- clearing is a correction, not a
        // refund, and the label says so.
        TextButton(onClick = { onPick(null) }) {
            Text("Clear the +${ActiveDefenses.BOOST_BONUS} (the FP stays spent)")
        }
    }
}

/**
 * What is coming at the defender.
 *
 * Three values because the Parry (Unarmed) row draws exactly these lines:
 * a bare hand is the plain formula, a weapon costs -3 unless you box, and a
 * shot cannot be parried at all. A declaration rather than something derived,
 * because this dialog holds one sheet and the attacker is not on it — the
 * same reason the three refusals are chips (6bg).
 */
internal enum class Incoming(val label: String) {
    BARE_HAND("A bare hand"),
    WEAPON("A weapon"),
    SHOT("A shot"),
}

/**
 * Where the attack is coming from.
 *
 * A declaration, like [Incoming] and the three refusals: this app holds no map
 * and no facing, and the GM does (6bf).
 *
 * **The -2 and -4 the book prints beside these positions are NOT applied**, and
 * [com.pathways.rules.ActiveDefenses.everyDefence] carries the reason. What
 * this control reaches is the shield's DB, whose own sentence is conditional on
 * the same arc and says "all active defenses (Dodge, Parry, and Block)" in as
 * many words. The rows below therefore change on REAR only because the shield
 * bonus does.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FacingPicker(chosen: Facing, onPick: (Facing) -> Unit) {
    SectionLabel("Where the attack comes from")
    FlowRow {
        Facing.entries.forEach { option ->
            FilterChip(
                selected = option == chosen,
                onClick = { onPick(option) },
                label = { Text(FACING_LABELS.getValue(option)) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
    // The penalty is SHOWN and not applied, so the GM can still use it. Saying
    // which half the screen did is the honest version of a rule that is partly
    // outside the model -- the same split ActiveDefenses takes on surprise.
    if (chosen != Facing.FRONT) {
        DefenseNote(
            "The book also prints a defence penalty for this position. It is " +
                "listed under Modifiers below and is NOT in the numbers above: " +
                "it prints \"to defense\" in the singular, and this app only " +
                "takes a penalty to every row when a second printing says so.",
        )
    }
}

/** What the GM says out loud, per printed position. */
private val FACING_LABELS = mapOf(
    Facing.FRONT to "Front",
    Facing.SIDE to "Side",
    Facing.REAR to "Rear",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IncomingPicker(chosen: Incoming, onPick: (Incoming) -> Unit) {
    SectionLabel("The attack coming in")
    FlowRow {
        Incoming.entries.forEach { option ->
            FilterChip(
                selected = option == chosen,
                onClick = { onPick(option) },
                label = { Text(option.label) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

/**
 * Whether the character is stunned, and the book's own cell for what it costs.
 *
 * The cell is quoted WHOLE. Its second clause is a rest that recovers FP,
 * which needs a clock this app does not hold (6bf) -- cutting the sentence at
 * render time to hide it would be a parser nobody tests (6f), and a GM reading
 * the row can see for themselves which half the screen applied.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StunPicker(stunned: Boolean, onToggle: () -> Unit) {
    SectionLabel("Stunned")
    FlowRow {
        FilterChip(
            selected = stunned,
            onClick = { onToggle() },
            label = { Text("Stunned") },
            modifier = Modifier.padding(end = 8.dp),
        )
    }
    if (stunned) {
        DefenseNote("Do Nothing (p. 37): \"${ActiveDefenses.printedStun()}\"")
        DefenseNote(
            "Every defence below already includes it. The recovery roll is on the " +
                "Vitals tab; the duration is the GM's, as this app holds no clock.",
        )
    }
}

/** What the GM declares about this character's ability to defend at all. */
private val REFUSAL_LABELS = mapOf(
    DefenseRefusal.SURPRISE to "Surprised",
    // Not "All-out last turn": the book forfeits the defence for a Charge and
    // for Feverish Defense too, and a label naming one table would make the
    // other two look like a different rule.
    DefenseRefusal.ALL_OUT_ATTACK to "Forfeited last turn",
    DefenseRefusal.UNAWARE_RANGED to "Never saw the shot",
)

/**
 * The three declarations that withdraw every active defence.
 *
 * Offered as chips rather than enforced from state: the app holds no turn, so
 * it cannot know that a Charge happened last turn any more than it can know a
 * shot came from concealment. The GM does.
 *
 * [forfeiting] names everything whose own row forfeits the defence, read from
 * the book's tables rather than restated -- five, where p. 37's parenthetical
 * names three.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RefusalPicker(
    declared: Set<DefenseRefusal>,
    forfeiting: List<String>,
    onToggle: (DefenseRefusal) -> Unit,
) {
    SectionLabel("Can this character defend?")
    FlowRow {
        REFUSAL_LABELS.forEach { (cause, label) ->
            FilterChip(
                selected = cause in declared,
                onClick = { onToggle(cause) },
                label = { Text(label) },
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
    if (DefenseRefusal.ALL_OUT_ATTACK in declared) {
        DefenseNote("Forfeits the next turn's defence: ${forfeiting.joinToString(", ")}.")
    }
}

/**
 * How many parries this character has already made this round.
 *
 * A stepper rather than a counter the app increments, because nothing here
 * sees a parry made -- the same reason a Fright Check and a Major Wound's HT
 * roll are buttons the GM presses (6c, 6n). **New round** zeroes it with the
 * rest of [RoundState] (6cp). The penalty is shown
 * beside it so the number on the rows above can be reconciled.
 */
@Composable
internal fun ParriesThisRound(prior: Int, penalty: Int, onChange: (Int) -> Unit) {
    OptionStepper(
        "Parries already this round",
        prior,
        if (penalty == 0) {
            "The first parry of a round is free."
        } else {
            // The minus sign is normalised: `penalty` is an Int and prints
            // ASCII "-", while the two tiers beside it are typed. One line
            // with two different dashes reads as two different rules.
            "Parry rows above include ${"$penalty".replace('-', '−')}. " +
                "Chapter 8 prints no fencing weapon or rapier, whose tiers are −2 and −1."
        },
    ) { onChange((prior + it).coerceAtLeast(0)) }
}

/**
 * One defence and the figures it stands at, as a tile, with a roll at each
 * (play session 1: Defend printed the numbers and rolled none). Two figures
 * when a shield's DB may apply -- the GM picks, as [shieldedTargets] says.
 * The verdict has a line of its own, drawn empty before the first roll and
 * held to one line, so a roll moves no row under a finger even at phone
 * width beside two chips (the diff review). A defence repeats, so a chip rolls again; a tap of a picture
 * already replaced is dropped (the second-tap guard, 6gb).
 */
@Composable
internal fun DefenseRow(name: String, targets: List<Int>, roll: (Int) -> RollResult) {
    // Keyed on the row, not its figures: a rolled parry lowers the next one
    // by 4 (play session 5), and the verdict it just made must not vanish
    // with the old figure. The line names its own target, so it stays true.
    var rolled by remember(name) { mutableStateOf<RollResult?>(null) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(
                    targets.joinToString(" → "),
                    style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val drawn = rolled
                targets.forEach { target ->
                    AssistChip(
                        onClick = { if (rolled === drawn) rolled = roll(target) },
                        label = { Text("Roll ≤ $target") },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
            Text(
                rolled?.let { "3d6: ${it.total} vs ${it.target} — ${label(it.outcome)}" }.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                minLines = 1,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun DefenseNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}
