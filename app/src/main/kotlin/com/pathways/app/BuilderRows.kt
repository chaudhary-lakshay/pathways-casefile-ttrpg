package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pathways.model.Attr
import com.pathways.model.BASE_ATTRIBUTE
import com.pathways.model.MAX_SECONDARY_DELTA_MAGNITUDE
import com.pathways.rules.AttributeCosts
import com.pathways.rules.Derive
import com.pathways.rules.PurchaseCeilings
import com.pathways.rules.Study
import com.pathways.rules.StudyMethod
import com.pathways.rules.StudyRefusedException
import com.pathways.model.Character
import com.pathways.model.PurchasedSkill
import com.pathways.rules.DerivedCharacter
import com.pathways.rules.Appearance
import com.pathways.rules.Wealth
import com.pathways.rules.content.AppearanceLevel
import com.pathways.rules.content.SecondaryCost
import com.pathways.rules.content.WealthLevel

/**
 * A level and what the NEXT one costs.
 *
 * The next level's price is the number that governs the decision, and the
 * ladder doubles every step, so a running total alone hides the cliff.
 */
@Composable
internal fun AttributeRow(
    attr: Attr,
    level: Int,
    played: Int,
    startingLevel: Int,
    canLower: Boolean,
    onChange: (Int) -> Unit,
) {
    // Room left for every potion on top of what is bought (PurchaseCeilings).
    val ceiling = PurchaseCeilings.attribute(attr)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(attr.name, style = MaterialTheme.typography.titleMedium)
            // costOf is CUMULATIVE -- what standing at that level costs in
            // total. The number that governs the next click is the
            // difference, and on a doubling ladder the two diverge fast: at
            // IQ 11 the cumulative figure for 12 is 140, but the click costs
            // 80.
            val spent = AttributeCosts.costOf(attr, level)
            val next = if (level < ceiling) AttributeCosts.costOf(attr, level + 1) - spent else null
            Text(
                buildString {
                    append("$spent pts spent here")
                    next?.let { append(" — next level costs $it") }
                    if (level != startingLevel) append(" — sheet prints $startingLevel")
                    beforePotions("$level", "$played")?.let { append(" — $it") }
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedIconButton(onClick = { onChange(-1) }, enabled = level > BASE_ATTRIBUTE && canLower) { Text("−") }
        Text(
            "$played",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        OutlinedIconButton(onClick = { onChange(1) }, enabled = level < ceiling) { Text("+") }
    }
}

/**
 * A secondary characteristic, what it reads now, and what the book charges to
 * move it (Ch. 2, pp. 8-9).
 *
 * The price is the book's own printed cell rather than a computed figure:
 * `SecondaryCharacteristics.costOf` already bills the purchase through the
 * budget bar above, and an arithmetic second site here is a copy that can
 * drift from the one doing the billing.
 *
 * Basic Speed steps by a quarter and shows two decimals, because that is the
 * unit its row is priced in -- the value label and the step both come from
 * the rules rather than from a `when` on this screen.
 *
 * The second line says what was BOUGHT here, not what the sheet printed.
 * Comparing against the published column is `SheetComparison`'s job and it
 * renders one tap away -- and a copy of it here would have been wrong about
 * the very rows the sheets disagree on, since at least 19 of the 23 floor
 * Basic Speed where the book does not (Codex, verified on an SM-T570: the
 * sheet screen reads `4.50 (sheet: 4.00)` for a row this line would have
 * called "sheet prints 4.50").
 */
@Composable
internal fun SecondaryRow(
    row: SecondaryCost,
    value: String,
    boughtValue: String,
    steps: Int,
    canLower: Boolean,
    onChange: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.characteristic, style = MaterialTheme.typography.titleMedium)
            Text(
                buildString {
                    append(row.cost)
                    if (steps != 0) append(" — ${steps.signed()} bought")
                    beforePotions(boughtValue, value)?.let { append(" — $it") }
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedIconButton(
            onClick = { onChange(-1) },
            enabled = steps > -MAX_SECONDARY_DELTA_MAGNITUDE && canLower,
        ) { Text("−") }
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        OutlinedIconButton(
            onClick = { onChange(1) },
            enabled = steps < PurchaseCeilings.secondarySteps(row.key),
        ) { Text("+") }
    }
}

@Composable
internal fun BudgetBar(spent: Int, budget: Int) {
    val over = spent > budget
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            if (over) "$spent of $budget points — over budget" else "$spent of $budget points, ${budget - spent} spare",
            style = MaterialTheme.typography.titleSmall,
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        LinearProgressIndicator(
            progress = { if (budget == 0) 0f else (spent.toFloat() / budget).coerceIn(0f, 1f) },
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/**
 * What downtime study would cost this skill's next rung (Ch. 3, p. 32).
 *
 * Advice, never a purchase. Study points are earned outside the 70-point
 * creation budget, so this line must never reach `SavedEdits` -- a planner
 * that quietly bought the rung would charge the character points for time
 * they spent instead.
 *
 * A refusal is SHOWN rather than hidden, because a row that simply stops
 * offering study is indistinguishable from a screen that forgot to render it
 * (6am). All three of the book's refusals reach this line.
 */
internal fun studyLine(
    purchased: PurchasedSkill,
    character: Character,
    derived: DerivedCharacter,
    method: StudyMethod,
    isBeforePotions: Boolean,
): String {
    val attributeLevel = Derive.controllingLevel(
        purchased.key.attr,
        character.attributes,
        derived.secondaries,
        character.spi,
    )
    // On the label, not after the level: a refusal names a level too
    // ("...this skill is already 12"), and both are the bought one.
    val label = if (isBeforePotions) "Study ($BEFORE_POTIONS)" else "Study"
    return Study.plan(
        points = purchased.points,
        difficulty = purchased.key.difficulty,
        attributeLevel = attributeLevel,
        method = method,
        attr = purchased.key.attr,
    ).fold(
        onSuccess = { "$label: ${it.printed} for ${it.pointsNeeded} CP → level ${it.toLevel}" },
        onFailure = { "$label: ${(it as StudyRefusedException).reason}" },
    )
}

/**
 * One skill: its level, what it cost, and a stepper when it was bought here.
 *
 * A skill with no [onSpend] is one the published sheet printed. It is shown
 * because the budget bills it, but it is not editable -- see BuilderScreen.
 */
@Composable
internal fun SkillRow(
    label: String,
    level: Int?,
    points: Int,
    onSpend: ((Int) -> Unit)?,
    study: String? = null,
    isBeforePotions: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                "$points pts" + (level?.let { " - level $it" + if (isBeforePotions) " $BEFORE_POTIONS" else "" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
            study?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onSpend != null) {
            OutlinedIconButton(onClick = { onSpend(-1) }) { Text("\u2212") }
            OutlinedIconButton(onClick = { onSpend(1) }) { Text("+") }
        }
    }
}

/**
 * The character's economic status, and what it is worth (Ch. 8, p. 80).
 *
 * The reaction modifier is SHOWN, never applied: this book has no reaction
 * roll to apply it to, and the paragraph under the table hands it to the GM
 * ("GMs may limit this modifier based on context"). Same shape as a called
 * shot's wounding multiplier, which is quoted beside the damage rather than
 * multiplied into it.
 */
@Composable
internal fun WealthRow(level: WealthLevel?, printed: WealthLevel?, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Wealth", style = MaterialTheme.typography.titleMedium)
            Text(
                buildString {
                    // Null is not Average: a sheet that prints no wealth row
                    // has said nothing about its character's status, and
                    // naming a level here would invent the book's silence away.
                    if (level == null) append("The sheet prints no wealth level")
                    else {
                        append("${level.status} — ${level.pointsLabel()}, ")
                        append("${level.startingMoney} to start")
                        if (level.reactionModifier != 0) {
                            append(", reaction ${level.reactionModifier.signed()}")
                        }
                        if (printed != null && printed.status != level.status) {
                            append(" — sheet prints ${printed.status}")
                        }
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )
            level?.let { Text(it.lifestyle, style = MaterialTheme.typography.bodySmall) }
        }
        TextButton(onClick = onPick) { Text("Change") }
    }
}

/**
 * The chosen appearance level, or ordinary looks (Ch. 3, p. 13-14).
 *
 * The reaction modifier is shown here and beside wealth, and the two are NEVER
 * added together. This book prints no reaction roll procedure and no result
 * table at all, while at least five things hand out a modifier for one -- this
 * table, wealth's Status column, Charisma, Reputation and Unsettling
 * Appearance. With no printed rule for combining them, a single total would be
 * arithmetic the book never asked for; each is reported beside the thing that
 * grants it.
 */
@Composable
internal fun AppearanceRow(
    level: AppearanceLevel?,
    printed: AppearanceLevel?,
    granted: AppearanceLevel?,
    onPick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Text(
                buildString {
                    // Null is ordinary looks, and that is not a level: the
                    // book's cheapest row is Attractive at 1 point, so it
                    // prints no ordinary row to name here.
                    if (level == null) append("Ordinary — the book prints no level for it")
                    else {
                        append("${level.level} — ${level.points.pointsLabel()}")
                        append(", reaction ${level.reactionModifier.signed()}")
                        if (printed != null && printed.level != level.level) {
                            append(" — sheet prints ${printed.level}")
                        }
                    }
                    if (granted != null) {
                        val played = Appearance.played(listOfNotNull(level, granted).map(Appearance::traitFor))!!
                        append(" — the potion grants ${granted.level}; ${played.level} is played")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = onPick) { Text("Change") }
    }
}
