package com.pathways.app

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.pathways.model.SkillKey
import com.pathways.rules.Derive
import com.pathways.rules.PurchaseCeilings
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
    // PURCHASES only, never a potion's grants: every level this screen prints
    // is a bought one, which is what the Skills header's single "Levels
    // before potions" tag claims for every row at once (6gm). Merge grants
    // into this and that tag lies for all of them.
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
    var pickingCharm by remember { mutableStateOf(false) }
    var pickingWeapon by remember { mutableStateOf(false) }
    var pickingLanguage by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf<InstantiableTrait?>(null) }
    // Which road the study lines are priced for. The book prints two
    // and they differ by more than money -- Very Hard self-study is
    // "Not possible" at any price.
    var studyMethod by remember { mutableStateOf(StudyMethod.TUTOR) }
    // Outside the list's items, which forget their state when scrolled off.
    // Keyed by the SkillKey, never its display, which drops the difficulty:
    // one name at two difficulties is two skills (p. 85; the diff review).
    var openStudy by remember { mutableStateOf(setOf<SkillKey>()) }

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

            // Named like the section below it: the four rows were the only
            // block on the screen without a heading.
            item {
                Text(
                    "Attributes",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            items(Attr.entries) { attr ->
                AttributeRow(
                    attr = attr,
                    level = character.attributes[attr],
                    played = Derive.attributes(character, played)[attr],
                    startingLevel = start.attributes[attr],
                    ceiling = PurchaseCeilings.attribute(attr, session.edits.traits),
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
                SectionSurface(first = true) {
                WealthRow(
                    // The CHOICE, not the composed character: Average costs 0
                    // pts and so leaves no trait to read back (Codex).
                    level = session.edits.wealthShownFor(character),
                    printed = Wealth.levelOf(start.traits),
                    onPick = { picking = true },
                )
                }
            }

            item {
                SectionSurface {
                AppearanceRow(
                    // The composed character is enough here, unlike wealth:
                    // every level costs something, so a chosen one always
                    // leaves a trait behind to read back.
                    level = Appearance.levelOf(character.traits),
                    printed = Appearance.levelOf(start.traits),
                    // The face a potion granted (6fn), so a face is not bought
                    // blind: the better of the two is played.
                    granted = remember(session.state, session.boon) {
                        Appearance.played(TraitGrants.grantedTraits(session.state, session.boon))
                    },
                    onPick = { pickingLooks = true },
                )
                }
            }

            item {
                SectionSurface {
                LanguagesRow(
                    // What the composition bills, read from the SHEET's own
                    // traits so a Language Talent bought in play is counted.
                    owned = session.edits.ownedLanguages(start.traits),
                    onAdd = { pickingLanguage = true },
                    onGiveBack = { session.sellLanguage(it) },
                )
                }
            }

            item {
                SectionSurface {
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
                    dx = Derive.attributes(character, played).dx,
                    onAdd = { pickingWeapon = true },
                    onRemove = { session.dropWeapon(it) },
                )
                }
            }

            item {
                SectionSurface(last = true) {
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
                    charmLine = charmLine(session.edits.charmedSkill, charmableSkills(character, session.state)),
                    onChooseCharm = { pickingCharm = true },
                    gained = session.edits.gained,
                    onGained = session::setGained,
                    quirks = session.edits.quirks,
                    onRemoveQuirk = session::removeQuirk,
                )
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Skills", style = MaterialTheme.typography.titleMedium)
                        // Said once for the section: every row's level, and
                        // a refusal's "already 12", is the bought one.
                        if (session.state != null) {
                            Text(
                                "Levels before potions",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    OutlinedButton(onClick = { adding = true }) { Text("Add a skill") }
                }
                // Two roads the book prices, so one toggle: Very Hard
                // self-study is "Not possible", and a tag saying so is lifted
                // here.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Study by", style = MaterialTheme.typography.labelLarge)
                    // A set width: left to itself the row takes its narrowest
                    // wrap, which broke "Self-Study" in two.
                    SingleChoiceSegmentedButtonRow(Modifier.width(280.dp)) {
                        StudyMethod.entries.forEachIndexed { i, method ->
                            SegmentedButton(
                                selected = studyMethod == method,
                                onClick = { studyMethod = method },
                                shape = SegmentedButtonDefaults.itemShape(i, StudyMethod.entries.size),
                                icon = {},
                            ) { Text(method.label) }
                        }
                    }
                }
            }

            items(character.skills) { purchased ->
                val bought = session.edits.skills.firstOrNull {
                    it.name == purchased.key.name && it.specialization == purchased.key.specialization
                }
                SkillRow(
                    label = purchased.key.display,
                    level = derived.skillLevels[purchased.key],
                    // What was BOUGHT; recorded study rides beside it, unbilled.
                    points = purchased.points - purchased.studied,
                    studied = purchased.studied,
                    expanded = purchased.key in openStudy,
                    onToggle = {
                        val key = purchased.key
                        openStudy = if (key in openStudy) openStudy - key else openStudy + key
                    },
                    // What downtime study would cost to reach the next rung
                    // (Ch. 3, p. 32). Advice only: study points are earned
                    // outside the creation budget, so nothing here spends.
                    //
                    // A potion lifts a bought skill or supersedes it with a
                    // grant, and which is `PotionDigestion`'s to decide: a
                    // superseded row is a potion's skill now, and cannot be
                    // studied (the design review). A Beyonder's rows stay on
                    // BOUGHT levels, said once on the header.
                    study = studyAdvice(
                        purchased, character, derived, studyMethod,
                        isPotionGranted = isSupersededByPotion(purchased.key, played),
                    ),
                    // Recorded study points (ch. 3 p. 32), on any row: a
                    // printed skill can be studied as well as a bought one.
                    onStudy = { n ->
                        session.setStudied(purchased.key.name, purchased.key.specialization, n.coerceAtLeast(0))
                    },
                    // Only skills bought in play are editable. The published
                    // sheet's own purchases stay put: the diff against it is a
                    // feature (spec section 6), and quietly re-pointing a
                    // printed skill would make that comparison meaningless.
                    onStep = bought?.let { saved ->
                        { up: Boolean -> session.stepSkill(saved.name, saved.specialization, up) }
                    },
                )
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

    if (pickingCharm) {
        CharmSkillPicker(
            skills = charmableSkills(character, session.state).sortedBy { it.label() },
            current = session.edits.charmedSkill,
            onPick = { session.setCharmedSkill(it); pickingCharm = false },
            onDismiss = { pickingCharm = false },
        )
    }
    if (pickingTrait) {
        TraitPicker(
            // In play, not bought: a potion's row is held and is not sold
            // again (6fk's diff review).
            held = session.traitsInPlay(sheet),
            bought = session.edits.traits,
            refusalFor = session::buyRefusal,
            // A refused row draws no Buy button; the guard drops a stale tap.
            onBuy = { if (session.buyRefusal(it.name) == null) session.buyTrait(it.name); pickingTrait = false },
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
