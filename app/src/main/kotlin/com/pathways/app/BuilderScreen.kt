package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import com.pathways.model.Attr
import com.pathways.model.BASE_ATTRIBUTE
import com.pathways.model.MAX_ATTRIBUTE
import com.pathways.model.MAX_SECONDARY_DELTA_MAGNITUDE
import com.pathways.rules.AttributeCosts
import com.pathways.rules.Derive
import com.pathways.rules.InstantiableTrait
import com.pathways.rules.PremadeSheet
import com.pathways.rules.PurchasableTraits
import com.pathways.rules.SecondaryCharacteristics
import com.pathways.rules.SkillLadder
import com.pathways.rules.Study
import com.pathways.rules.StudyMethod
import com.pathways.rules.StudyRefusedException
import com.pathways.model.Character
import com.pathways.model.PurchasedSkill
import com.pathways.rules.DerivedCharacter
import com.pathways.rules.Appearance
import com.pathways.rules.Wealth
import com.pathways.rules.content.AppearanceLevel
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.SecondaryCost
import com.pathways.rules.content.SkillDefinition
import com.pathways.rules.content.WealthLevel

/**
 * Spend points and watch the budget answer back (M3).
 *
 * ponytail: attributes only. They are the whole of the cost curve worth
 * seeing live -- the ladder doubles every level, so ST 9 -> 12 is the
 * difference between affordable and impossible -- while skills and traits are
 * list editing, which is a bigger screen for a smaller lesson. Add them when
 * a character is worth saving.
 *
 * The budget is [com.pathways.rules.PointBudget]'s, unchanged: 70 + capped
 * disadvantages + capped quirks, and surplus is normal. It validates
 * `spent <= budget`, never `spent == budget` -- Hunter-Tracker leaves 26
 * unspent and is not thereby broken.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuilderScreen(sheet: PremadeSheet, session: BeyonderSession, onBack: () -> Unit) {
    val start = remember(sheet) { sheet.toCharacter() }
    val character = session.characterFor(sheet)
    val derived = remember(character) { Derive.of(character) }
    val report = derived.budget
    // The six rows the book prices; the three it refuses to price carry no
    // pointsPerStep and so offer no stepper (Ch. 2, pp. 8-9).
    val secondaryRows = remember { ContentLoader.secondaryCosts().filter { it.pointsPerStep != null } }
    var adding by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var pickingLooks by remember { mutableStateOf(false) }
    var pickingTrait by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf<InstantiableTrait?>(null) }
    // Which road the study lines are priced for. The book prints two
    // and they differ by more than money -- Very Hard self-study is
    // "Not possible" at any price.
    var studyMethod by remember { mutableStateOf(StudyMethod.TUTOR) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            sheet.name,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Spend points",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to sheet",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { insets ->
        LazyColumn(Modifier.padding(insets).fillMaxSize()) {
            // Pinned, because this screen exists to spend against it. The
            // budget used to scroll away with the header, so the number being
            // spent against was off-screen for every decision after the first.
            stickyHeader {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        BudgetBar(report.spent, report.budget)
                        report.violations.forEach { violation ->
                            Text(
                                violation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                    }
                }
            }

            items(Attr.entries) { attr ->
                AttributeRow(
                    attr = attr,
                    level = character.attributes[attr],
                    startingLevel = start.attributes[attr],
                    onChange = { delta ->
                        session.setAttribute(attr, character.attributes[attr] + delta)
                    },
                )
                HorizontalDivider()
            }

            item {
                Text(
                    "Secondary characteristics",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            items(secondaryRows) { row ->
                // The STEPS come from the save, never from the composed
                // character: a potion grant lands in the same field, and
                // stepping from the composed figure would buy the grant a
                // second time.
                val bought = session.edits.secondaries[row.key] ?: 0
                SecondaryRow(
                    row = row,
                    // Never null for a priced row, and pinned by
                    // SecondaryPurchaseTest: a content file that grew a
                    // seventh price without a field to put it in should fail
                    // loudly rather than drop the row off the screen.
                    value = checkNotNull(
                        SecondaryCharacteristics.valueLabelOf(derived.secondaries, row.key),
                    ) { "${row.characteristic} is priced and has no value to show" },
                    steps = bought,
                    onChange = { delta -> session.setSecondary(row.key, bought + delta) },
                )
                HorizontalDivider()
            }

            item {
                WealthRow(
                    // The CHOICE, not the composed character: Average costs 0
                    // pts and so leaves no trait to read back (Codex).
                    level = session.edits.wealthShownFor(character),
                    printed = Wealth.levelOf(start.traits),
                    onPick = { picking = true },
                )
                HorizontalDivider()
            }

            item {
                AppearanceRow(
                    // The composed character is enough here, unlike wealth:
                    // every level costs something, so a chosen one always
                    // leaves a trait behind to read back.
                    level = Appearance.levelOf(character.traits),
                    printed = Appearance.levelOf(start.traits),
                    onPick = { pickingLooks = true },
                )
                HorizontalDivider()
            }

            item {
                TraitsRow(
                    bought = session.edits.traits,
                    // Resolved against the same list the composition asks the
                    // severity rule about, so a row the character does not
                    // have cannot be listed here with a price.
                    instanced = session.edits.instancedRows(
                        session.edits.traitsBeforeInstanced(start.traits),
                    ),
                    onAdd = { pickingTrait = true },
                    onRemove = { session.sellTrait(it) },
                    onRemoveInstanced = { session.sellInstancedTrait(it) },
                )
                HorizontalDivider()
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Skills",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { adding = true }) { Text("Add a skill") }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Study cost shown for: ${studyMethod.label}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        studyMethod = if (studyMethod == StudyMethod.TUTOR) {
                            StudyMethod.SELF_STUDY
                        } else {
                            StudyMethod.TUTOR
                        }
                    }) { Text("Switch") }
                }
                HorizontalDivider()
            }

            items(character.skills) { purchased ->
                val bought = session.edits.skills.firstOrNull {
                    it.name == purchased.key.name && it.specialization == purchased.key.specialization
                }
                SkillRow(
                    label = purchased.key.display,
                    level = derived.skillLevels[purchased.key],
                    points = purchased.points,
                    // What downtime study would cost to reach the next rung
                    // (Ch. 3, p. 32). Advice only: study points are earned
                    // outside the creation budget, so nothing here spends.
                    //
                    // `isPotionGranted` is left false because it cannot be
                    // true on THIS screen -- a Character holds purchases only
                    // (M3), so every row here was bought. The rule is enforced
                    // in `Study` regardless, for a caller that does hold grants.
                    study = studyLine(purchased, character, derived, studyMethod),
                    // Only skills bought in play are editable. The published
                    // sheet's own purchases stay put: the diff against it is a
                    // feature (spec section 6), and quietly re-pointing a
                    // printed skill would make that comparison meaningless.
                    onSpend = bought?.let { saved ->
                        { delta: Int -> session.spendOnSkill(saved.name, saved.specialization, delta) }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    if (picking) {
        WealthPicker(
            // The OVERRIDE, not the shown level: with the sheet's own level
            // here, "As printed" could never tick.
            chosen = session.edits.wealthLevel()?.status,
            printed = Wealth.levelOf(start.traits),
            onPick = { level ->
                session.setWealth(level?.status)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }

    if (pickingTrait) {
        TraitPicker(
            held = character.traits,
            bought = session.edits.traits,
            onBuy = { session.buyTrait(it.name); pickingTrait = false },
            // The picker closes behind the settle dialog rather than stacking
            // two: a row priced by its instance is one choice made in two
            // steps, not two choices.
            onSettle = { settling = it; pickingTrait = false },
            onDismiss = { pickingTrait = false },
        )
    }

    settling?.let { row ->
        InstancedTraitDialog(
            row = row,
            onBuy = { purchase ->
                session.buyInstancedTrait(
                    SavedInstancedTrait(
                        purchase.row,
                        purchase.variant,
                        purchase.instance,
                        purchase.cost,
                    ),
                )
                settling = null
            },
            onDismiss = { settling = null },
        )
    }

    if (pickingLooks) {
        AppearancePicker(
            chosen = session.edits.appearanceLevel()?.level,
            printed = Appearance.levelOf(start.traits),
            onPick = { level ->
                session.setAppearance(level?.level)
                pickingLooks = false
            },
            onDismiss = { pickingLooks = false },
        )
    }

    if (adding) {
        SkillPicker(
            owned = character.skills.map { it.key.name.lowercase() to it.key.specialization }.toSet(),
            onPick = { definition ->
                // The specialization travels with the pick. Five names carry
                // several rows -- Guns has four, Navigation three -- and
                // dropping it would buy whichever the book prints first.
                session.buySkill(definition.name, definition.specialization)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}

/**
 * A level and what the NEXT one costs.
 *
 * The next level's price is the number that governs the decision, and the
 * ladder doubles every step, so a running total alone hides the cliff.
 */
@Composable
private fun AttributeRow(attr: Attr, level: Int, startingLevel: Int, onChange: (Int) -> Unit) {
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
            val next = if (level < MAX_ATTRIBUTE) AttributeCosts.costOf(attr, level + 1) - spent else null
            Text(
                buildString {
                    append("$spent pts spent here")
                    next?.let { append(" — next level costs $it") }
                    if (level != startingLevel) append(" — sheet prints $startingLevel")
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedIconButton(onClick = { onChange(-1) }, enabled = level > BASE_ATTRIBUTE) { Text("−") }
        Text(
            "$level",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        OutlinedIconButton(onClick = { onChange(1) }, enabled = level < MAX_ATTRIBUTE) { Text("+") }
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
private fun SecondaryRow(
    row: SecondaryCost,
    value: String,
    steps: Int,
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
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedIconButton(
            onClick = { onChange(-1) },
            enabled = steps > -MAX_SECONDARY_DELTA_MAGNITUDE,
        ) { Text("−") }
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        OutlinedIconButton(
            onClick = { onChange(1) },
            enabled = steps < MAX_SECONDARY_DELTA_MAGNITUDE,
        ) { Text("+") }
    }
}

@Composable
private fun BudgetBar(spent: Int, budget: Int) {
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
private fun studyLine(
    purchased: PurchasedSkill,
    character: Character,
    derived: DerivedCharacter,
    method: StudyMethod,
): String {
    val attributeLevel = Derive.controllingLevel(
        purchased.key.attr,
        character.attributes,
        derived.secondaries,
        character.spi,
    )
    return Study.plan(
        points = purchased.points,
        difficulty = purchased.key.difficulty,
        attributeLevel = attributeLevel,
        method = method,
        attr = purchased.key.attr,
    ).fold(
        onSuccess = {
            "Study: ${it.printed} for ${it.pointsNeeded} CP → level ${it.toLevel}"
        },
        onFailure = { "Study: ${(it as StudyRefusedException).reason}" },
    )
}

/**
 * One skill: its level, what it cost, and a stepper when it was bought here.
 *
 * A skill with no [onSpend] is one the published sheet printed. It is shown
 * because the budget bills it, but it is not editable -- see BuilderScreen.
 */
@Composable
private fun SkillRow(
    label: String,
    level: Int?,
    points: Int,
    onSpend: ((Int) -> Unit)?,
    study: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                "$points pts" + (level?.let { " - level $it" } ?: ""),
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
private fun WealthRow(level: WealthLevel?, printed: WealthLevel?, onPick: () -> Unit) {
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
 * The seven printed levels, poorest first.
 *
 * A vertical list rather than a chip row: a horizontally scrolling Row inside
 * an AlertDialog never receives the drag on a phone, which cost three
 * maneuvers their selectability in M4's damage slice and was invisible to
 * every test.
 */
@Composable
private fun WealthPicker(
    chosen: String?,
    printed: WealthLevel?,
    onPick: (WealthLevel?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Economic status") },
        text = {
            LazyColumn {
                // The way back to the published sheet (Codex). Without it a
                // single tap is permanent, and the 20 of 23 premades that
                // print no wealth level could never be returned to printing
                // none.
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(null) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            "As printed" + if (chosen == null) " ✓" else "",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            printed?.let { "The sheet prints ${it.status}" }
                                ?: "The sheet prints no wealth level",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    HorizontalDivider()
                }
                items(Wealth.all()) { level ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(level) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            level.status + if (level.status == chosen) " ✓" else "",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "${level.pointsLabel()} · ${level.startingMoney} · " +
                                "reaction ${level.reactionModifier.signed()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(level.lifestyle, style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        },
    )
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
private fun AppearanceRow(
    level: AppearanceLevel?,
    printed: AppearanceLevel?,
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
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = onPick) { Text("Change") }
    }
}

/**
 * The eight printed levels, best first.
 *
 * A vertical list for the same reason [WealthPicker] is one.
 *
 * ponytail: "As printed" is the only way back, which on all 23 premades is
 * also the way to ordinary looks -- none of them prints an appearance level.
 * If a sheet ever printed one, ordinary would become unreachable for that
 * character; the upgrade is a separate "Ordinary" row storing a sentinel, and
 * it is not worth a saved-state distinction until a sheet needs it.
 */
@Composable
private fun AppearancePicker(
    chosen: String?,
    printed: AppearanceLevel?,
    onPick: (AppearanceLevel?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Appearance") },
        text = {
            LazyColumn {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(null) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            "As printed" + if (chosen == null) " ✓" else "",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            printed?.let { "The sheet prints ${it.level}" }
                                ?: "The sheet prints no appearance level — ordinary looks",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    HorizontalDivider()
                }
                items(Appearance.all()) { level ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(level) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            level.level + if (level.level == chosen) " ✓" else "",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "${level.points.pointsLabel()} · " +
                                "reaction ${level.reactionModifier.signed()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    HorizontalDivider()
                }
            }
        },
    )
}

private fun WealthLevel.pointsLabel(): String = points.pointsLabel()

/**
 * "+1 pt", "−8 pts" -- the book's own spelling.
 *
 * Wealth never needed the singular: its smallest magnitude is 5. Appearance's
 * Attractive is one point and the book prints "+1 pt", so a flat "pts" made
 * the app quote the table wrongly (found on device).
 */
internal fun Int.pointsLabel(): String =
    "${signed()} " + if (this == 1 || this == -1) "pt" else "pts"

/** Zero is unsigned: "+0 pts" and "reaction +0" read as typos, not as nothing. */
private fun Int.signed(): String = when {
    this > 0 -> "+$this"
    this < 0 -> "−${-this}"
    else -> "0"
}

/**
 * Every skill the book prints, minus the ones already owned.
 *
 * ponytail: no search box. 158 rows scroll fine on a phone, and a text field
 * is a keyboard, an IME and another piece of hoisted state for a list this
 * size. Add one when the list is the complaint.
 */
@Composable
private fun SkillPicker(
    owned: Set<Pair<String, String?>>,
    onPick: (SkillDefinition) -> Unit,
    onDismiss: () -> Unit,
) {
    val skills = remember(owned) {
        ContentLoader.skills()
            .filterNot { (it.name.lowercase() to it.specialization) in owned }
            // Ch. 9 p. 85: the three SPI-based skills "cannot be raised with
            // character points ... they improve only through Sequence
            // progression and pathway bonuses". Withheld rather than offered
            // and then refused, the lesson the two Aim rows taught (6t).
            .filter { SkillLadder.isBuyableWithPoints(it.attr) }
            .sortedBy { it.name }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Add a skill") },
        text = {
            LazyColumn {
                items(skills) { definition ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(definition) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            definition.specialization?.let { "${definition.name} ($it)" }
                                ?: definition.name,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "${definition.attr.code}/${definition.difficulty.code}" +
                                if (definition.specializable) " - needs a specialization" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    HorizontalDivider()
                }
            }
        },
    )
}
