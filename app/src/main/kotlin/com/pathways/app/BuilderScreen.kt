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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pathways.model.Attr
import com.pathways.rules.Derive
import com.pathways.rules.InstantiableTrait
import com.pathways.rules.PremadeSheet
import com.pathways.rules.SecondaryCharacteristics
import com.pathways.rules.SkillDefaults
import com.pathways.rules.Study
import com.pathways.rules.StudyMethod
import com.pathways.rules.Appearance
import com.pathways.rules.TraitGrants
import com.pathways.rules.Wealth
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.WealthLevel

/**
 * Spend points and watch the budget answer back (M3).
 *
 * It began as attributes only, under a ponytail that deferred the rest until
 * "a character is worth saving". That was met long ago: this screen sells
 * attributes, secondary characteristics, wealth, appearance, languages,
 * traits, weapons and skills, each stored in [SavedEdits]. The comment went
 * on saying "attributes only" until 6fu.
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
    // The character as PLAYED, for the numbers shown -- the ones the sheet
    // shows. Everything stepped, priced or refused stays on the purchase: a
    // potion's grants are free (p. 85), and `derived` is what the budget bills.
    val played = remember(character, session.state) { character.digestedWith(session.state) }
    val report = derived.budget
    // The six rows the book prices; the three it refuses to price carry no
    // pointsPerStep and so offer no stepper (Ch. 2, pp. 8-9).
    val secondaryRows = remember { ContentLoader.secondaryCosts().filter { it.pointsPerStep != null } }
    var adding by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var pickingLooks by remember { mutableStateOf(false) }
    var pickingTrait by remember { mutableStateOf(false) }
    var pickingWeapon by remember { mutableStateOf(false) }
    var pickingLanguage by remember { mutableStateOf(false) }
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
                    played = played?.effectiveAttributes?.get(attr) ?: character.attributes[attr],
                    startingLevel = start.attributes[attr],
                    // A step the session would refuse: below a score floor (ScoreFloors).
                    canLower = session.canSetAttribute(attr, character.attributes[attr] - 1),
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
                // Never null for a priced row, and pinned by
                // SecondaryPurchaseTest: a content file that grew a seventh
                // price without a field to put it in should fail loudly
                // rather than drop the row off the screen.
                val boughtValue = checkNotNull(
                    SecondaryCharacteristics.valueLabelOf(derived.secondaries, row.key),
                ) { "${row.characteristic} is priced and has no value to show" }
                SecondaryRow(
                    row = row,
                    value = played?.let { SecondaryCharacteristics.valueLabelOf(it.secondaries, row.key) }
                        ?: boughtValue,
                    boughtValue = boughtValue,
                    steps = bought,
                    canLower = session.canSetSecondary(row.key, bought - 1),
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
                    // The face a potion granted (6fn), so a face is not bought
                    // blind: the better of the two is played.
                    granted = remember(session.state) { Appearance.played(TraitGrants.grantedTraits(session.state)) },
                    onPick = { pickingLooks = true },
                )
                HorizontalDivider()
            }

            item {
                LanguagesRow(
                    // What the composition bills, read from the SHEET's own
                    // traits so a Language Talent bought in play is counted.
                    owned = session.edits.ownedLanguages(start.traits),
                    onAdd = { pickingLanguage = true },
                    onGiveBack = { session.sellLanguage(it) },
                )
                HorizontalDivider()
            }

            item {
                WeaponsRow(
                    carried = session.edits.carriedWeapons(),
                    // The levels rolled TODAY, which is what a parry is
                    // computed from -- the same expression the sheet screen
                    // builds. `derived` is the PURCHASED character, which the
                    // budget above is right to use and a parry is not: a
                    // Beyonder whose potion granted the weapon's skill would
                    // read one number here and another on the defence screen.
                    skillLevels = played?.skillLevels ?: derived.skillLevels,
                    // A master's weapon parries off a skill floored at DX as played,
                    // the same reading Defend applies (6ey).
                    mastery = session.state?.let { SkillDefaults.masteryFor(it.pathway, it.sequence) },
                    dx = played?.effectiveAttributes?.get(Attr.DX) ?: character.attributes[Attr.DX],
                    onAdd = { pickingWeapon = true },
                    onRemove = { session.dropWeapon(it) },
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
                    canRemove = { session.canSellTrait(it) },
                    onRemoveInstanced = { index, drawn -> session.sellInstancedTrait(index, drawn) },
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
                    study = studyLine(purchased, character, derived, studyMethod, session.state != null),
                    // A Beyonder's rows stay on BOUGHT levels and say so: a
                    // potion lifts a bought skill or supersedes it with a
                    // grant, and which is `PotionDigestion`'s to decide, on
                    // the sheet -- not a second matcher's here.
                    isBeforePotions = session.state != null,
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

    if (pickingWeapon) {
        WeaponPicker(
            carried = session.edits.weapons,
            onCarry = { session.carryWeapon(it) },
            onDismiss = { pickingWeapon = false },
        )
    }

    if (pickingTrait) {
        TraitPicker(
            // In play, not bought: a potion's row is held and is not sold
            // again (6fk's diff review).
            held = session.traitsInPlay(sheet),
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

    if (pickingLanguage) {
        LanguagePicker(
            owned = session.edits.ownedLanguages(start.traits),
            priced = { session.edits.pricedWith(it, start.traits) },
            onBuy = {
                session.buyLanguage(it)
                pickingLanguage = false
            },
            onDismiss = { pickingLanguage = false },
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

internal fun WealthLevel.pointsLabel(): String = points.pointsLabel()

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
internal fun Int.signed(): String = when {
    this > 0 -> "+$this"
    this < 0 -> "−${-this}"
    else -> "0"
}
