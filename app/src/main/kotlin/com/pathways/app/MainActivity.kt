package com.pathways.app

import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pathways.model.TraitKind
import com.pathways.rules.Conditions
import com.pathways.rules.Derive
import com.pathways.rules.Divination
import com.pathways.rules.Pool
import com.pathways.rules.Pools
import com.pathways.rules.SpiritVision
import com.pathways.rules.PremadeSheet
import com.pathways.rules.Premades
import com.pathways.rules.RandomSource
import com.pathways.rules.RollKind
import com.pathways.rules.SelfControl
import com.pathways.rules.SelfControlCheck
import com.pathways.rules.SheetComparison
import com.pathways.rules.Shock
import com.pathways.rules.TraitClauses
import com.pathways.rules.TraitEffects
import com.pathways.rules.TraitReport
import com.pathways.rules.TraitGrants
import java.io.File
import kotlin.random.Random

/** The app's real dice. Tests inject [com.pathways.rules.SeededRandom] instead. */
internal val appDice = RandomSource { sides -> Random.nextInt(1, sides + 1) }

/** The destinations reachable from a selected premade. */
private enum class Screen { SHEET, PATHWAY, BUILDER }

/** [SheetComparison] emits the seven secondaries before any skill row. */
internal const val SECONDARY_ROWS = 7

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ponytail: loaded on the main thread. 23 small JSONs off the APK
        // classpath. It said to move behind a ViewModel "when the sheet screen
        // needs state that survives rotation"; it did (6dy lost a pushed roll
        // to one), and 6fv answered in the manifest instead: a rotation no
        // longer recreates the activity, so no state has to survive one.
        val store = SessionStore(File(filesDir, "sessions.json"))
        // Draw behind the system bars. Every screen already pads from
        // Scaffold's inset parameter, so this needs no per-screen change.
        enableEdgeToEdge()
        setContent {
            // Read once, here, so the theme wraps the whole tree: changing a
            // preference in the picker has to repaint every screen, not just
            // the one holding the picker.
            val theme = rememberThemePrefs()
            PathwaysTheme(theme.value) { App(Premades.all(), store, theme) }
        }
    }
}

/**
 * ponytail: four destinations, one enum, no navigation library. Add one at the
 * first deep link or saved back stack.
 */
@Composable
fun App(
    sheets: List<PremadeSheet>,
    store: SessionStore,
    themePrefs: MutableState<ThemePrefs>,
) {
    var selected by remember { mutableStateOf<PremadeSheet?>(null) }
    // Read once; every write goes straight back out, so the file and the
    // screens cannot drift apart.
    val saved = remember { store.load().toMutableMap() }
    var screen by remember(selected) { mutableStateOf(Screen.SHEET) }
    // Bumped by "Reset to sheet": the session and scene below are rebuilt
    // from a save entry that is no longer there.
    var resets by remember { mutableIntStateOf(0) }
    // Every throw, per character, for as long as the app runs (RollLog).
    val rollLog = remember { RollLog(appDice) }
    // Above the screen switch, so the builder and the pathway screen do not
    // take the scene down with the sheet (play session 1).
    val scene = remember(selected, resets) { SheetScene() }
    // One walk down a pathway per selected premade, restored from disk and
    // written back on every change.
    val session = remember(selected, resets) {
        val name = selected?.name
        BeyonderSession(saved[name] ?: SavedSession(), selected) { updated ->
            if (name != null) {
                saved[name] = updated
                store.save(saved)
            }
        }
    }

    when (val sheet = selected) {
        null -> PremadeGallery(sheets, saved, themePrefs) { selected = it }
        else -> CompositionLocalProvider(
            LocalDice provides remember(sheet) { CharacterDice { label -> rollLog.dice(sheet.name, label) } },
        ) {
            BackHandler {
                if (screen == Screen.SHEET) selected = null else screen = Screen.SHEET
            }
            when (screen) {
                Screen.SHEET -> SheetScreen(
                    sheet,
                    session = session,
                    scene = scene,
                    onPathways = { screen = Screen.PATHWAY },
                    onBuild = { screen = Screen.BUILDER },
                    onBack = { selected = null },
                    onReset = {
                        saved.remove(sheet.name)
                        store.save(saved)
                        rollLog.forget(sheet.name)
                        resets++
                    },
                    throws = { rollLog.of(sheet.name) },
                )
                Screen.PATHWAY -> PathwayScreen(sheet, session, rollLog.dice(sheet.name, "Potion Consumption Roll")) { screen = Screen.SHEET }
                Screen.BUILDER -> BuilderScreen(sheet, session) { screen = Screen.SHEET }
            }
        }
    }
}

/**
 * The `computed vs asPublished` diff (spec section 8). A disagreement is shown,
 * never reconciled: most sheets floor Basic Speed where the book does not, and
 * Docks Bouncer's printed HP contradicts its own ST. Those rows render in the
 * error colour alongside what the sheet claimed.
 *
 * Skill rows are tappable and roll 3d6 against the resolved target.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SheetScreen(
    sheet: PremadeSheet,
    session: BeyonderSession,
    scene: SheetScene,
    onPathways: () -> Unit,
    onBuild: () -> Unit,
    onBack: () -> Unit,
    onReset: () -> Unit,
    /** This character's throws, newest first, read only when the log opens. */
    throws: () -> List<LoggedThrow>,
) {
    val drunk = session.state
    val rows = remember(sheet) { SheetComparison.of(sheet) }
    val differing = rows.count { !it.matches }
    // The SKILL, not the outcome: the roll now happens inside the dialog, after
    // the GM has stated the circumstances.
    var rolling by remember(sheet) { mutableStateOf<RollableSkill?>(null) }
    // Which third of the sheet is on screen. The screen was one scroll of
    // roughly six phone-heights -- status, actions, pools, trait prose, then
    // the skills, which are the reason anyone opened it. Defaulting to SKILLS
    // puts the primary interaction one tap from the character, not six swipes.
    var tab by scene.tab
    // Which of the hub's dialogs are open. A set, because two tiles
    // tapped a few milliseconds apart both reach this composition's
    // handlers and open two dialogs, as eleven flags did (6ei).
    var open by remember(sheet) { mutableStateOf(emptySet<SheetDialog>()) }
    // The Major Wound's shock, latched by the damage dialog that rolls for it
    // and cleared by the GM when the turn passes. ONE owner, above every
    // screen that reads it: a chip per dialog would let the same one-turn fact
    // disagree with itself across surfaces (Codex, at design time). In memory
    // rather than in the session file, because a one-turn fact that survives a
    // force-stop has outlived its turn by construction.
    var shocked by scene.shocked
    // The held rows' "-N to all rolls" the GM says are in force (6gs): one
    // owner for the same reason as shock, declared once on the Vitals tab
    // rather than at every roll. Both live in [SheetScene], above this screen.
    var declaredStates by scene.declaredStates
    // The CHECK, not the outcome, exactly as `rolling` holds the skill: the
    // roll happens inside the dialog, after the GM has stated a modifier.
    var controlling by remember(sheet) { mutableStateOf<SelfControlCheck?>(null) }
    // Maximums move with the potions drunk: HT gains lift FP, and SPI is
    // entirely a potion's gift. Recomputed from the walk, never stored.
    val vitals = remember(sheet, drunk, session.edits) {
        val base = session.characterFor(sheet)
        val walked = base.digestedWith(drunk)
        val secondaries = walked?.secondaries ?: Derive.of(base).secondaries
        Vitals(
            secondaries.maxHp,
            secondaries.maxFp,
            walked?.effectiveSpi ?: base.spi,
            secondaries.will,
            secondaries.per,
            secondaries.dodge,
        )
    }
    // The seven secondaries as PLAYED, each beside its row of the diff
    // (6fz). Keyed as the pools above are: a potion or an edit moves both.
    val secondaryLines = remember(sheet, rows, drunk, session.edits) {
        vitalLines(sheet, rows, session.characterFor(sheet).secondariesWith(drunk))
    }
    // The traits actually in play -- the sheet's, plus anything the edit
    // layer changed. Hoisted because more than one reader needs them.
    //
    // Plus every row the potions drunk granted (6fk), from the walk the stat
    // grants come from: a potion's Combat Reflexes is held as surely as a
    // bought one. Every reader on this screen takes the merged list; the
    // budget never sees it, because it prices `characterFor` itself.
    val grantedTraits = remember(drunk, session.boon) { TraitGrants.grantedTraits(drunk, session.boon) }
    // What was bought, for the levels a potion added to it (6fm).
    val bought = remember(sheet, session.edits) { session.characterFor(sheet).traits }
    val traits = remember(sheet, session.edits, drunk) { session.traitsInPlay(sheet) }
    // The CoR ceiling: the CURRENT Will, which a potion can move, +3 with
    // Spiritual Fortitude. Computed once: the ritual dialog and the SPI
    // tracker's Soul Debt both charge against it, and it is the same reading
    // the pathway screen and the builder's refusal use.
    val maxCor = remember(sheet, drunk, session.edits) { session.edits.corCeilingOn(sheet, drunk) }
    // The disadvantages that print a number to roll under (6am). Until this
    // slice a character's disadvantages rendered NOWHERE in the app at all --
    // `TraitReport.reported` reads the advantage catalogue only -- so the
    // self-control rolls the book prints had no way to be made.
    val selfControl = remember(traits) {
        SelfControl.checksFor(traits).associateBy { it.trait }
    }
    // Every disadvantage the character carries, in sheet order, with the
    // book's own effect cell where the two spellings meet. A sheet name that
    // matches nothing gets its name alone -- three sheets print
    // "Absent-Minded" against the book's "Absent-Mindedness" and one prints
    // "Paranoia: Bodies of water" against a bare "Paranoia", and 4.1 says
    // those are recorded, not repaired. The blank effect line is the record.
    //
    // Through the one resolution, so a sheet that printed the effect beside
    // the name still finds its row: Church Healer stores "Charity - <the
    // book's own cell>" and got no effect line at all.
    // A quirk gives points as a drawback does and prints with the same sign;
    // nothing this app ships produces one, and one that arrives is not dropped.
    val drawbacks = remember(traits) {
        TraitReport.held(traits, TraitKind.DISADVANTAGE) +
            TraitReport.held(traits, TraitKind.QUIRK)
    }
    // The advantages, which had no list at all until 6es: the sheet printed
    // `TraitReport.reported` and the drawbacks, so an advantage with nothing
    // to report had its cell on no screen -- the gallery card and the builder
    // show a name. 6am's finding, one list over, and 24 of the premades' 50
    // advantage rows.
    val advantages = remember(traits) { TraitReport.held(traits, TraitKind.ADVANTAGE) }
    // Which Traits rows are open, by kind and name (6gl).
    val expandedTraits = remember(sheet) { mutableStateMapOf<String, Boolean>() }
    // Damage rolls off ST, Boxing's bonus off DX and a Major Wound off HT, all
    // AFTER any potion that raised them -- the same digested character the
    // skill levels come from.
    val attributes = remember(sheet, drunk, session.edits) {
        session.characterFor(sheet).attributesWith(drunk)
    }
    // The levels actually rolled: a potion's grants are in here, the sheet's
    // printed column is not. Computed once for defence, attack and the ritual
    // gate rather than three times over.
    val skillLevels = remember(sheet, drunk, session.edits) {
        liveSkillLevels(session.characterFor(sheet).copy(beyonder = drunk))
    }
    var pickingUntrained by remember(sheet) { mutableStateOf(false) }
    // Only a caster can cast. No premade prints either skill, so both are
    // null until a potion grants one -- ch. 9 p. 85 forbids buying Divination
    // Arts with points, so a potion is its only route (6aq).
    val ownedRitual = skillLevelOf(RITUAL_SKILL, skillLevels)
    val ownedDivination = skillLevelOf(Divination.SKILL, skillLevels)
    // p. 54: "Divination Arts defaults to Ritualistic Magic-2 and vice versa.
    // A character with one skill at 12+ can attempt the other at -2."
    //
    // This matters because the two are granted very unevenly -- 25 potion
    // blocks grant Ritualistic Magic against 5 that grant Divination Arts --
    // so a gate on ownership alone hid the Divine button from most casters
    // the book lets ask a question. Below 12 the default is null and the
    // button stays hidden, which is the book withholding it, not this app.
    //
    // Which of the two numbers wins lives in Divination.divinationLevelFor,
    // NOT here: a screen picking for itself is the two-copies-of-one-decision
    // failure 6ag and 6ai each produced once, and the answer is not simply
    // "the one you own" (Codex).
    //
    // It runs ONE WAY. The "vice versa" clause loses to ch. 7 II p. 61 --
    // "Ritualistic Magic ... has no default and cannot be substituted" -- so
    // a ritual is rolled at the caster's own skill or not at all.
    val levels = Divination.levelsFor(
        ownDivination = ownedDivination,
        ownRitual = ownedRitual,
        defaultBonus = TraitEffects.defaultBonus(traits),
    )
    val divination = levels.divination
    val ritual = levels.ritual
    val divinationLevel = divination?.level
    val ritualLevel = ritual?.level

    // Ch. 7.6. Gated on the character's OWN potion block, via the grants the
    // extractor derives -- never on 7.6's own section C summary, which lists
    // Visionary (whose Spectator block grants none) and omits Moon (whose
    // Apothecary block grants it). A gate on the summary would hide the tile
    // from the one premade in this corpus who has the ability.
    val spiritVisionForm = drunk?.let { SpiritVision.formFor(it.pathway)?.takeIf { _ ->
        SpiritVision.grantedTo(it.pathway, it.sequence)
    } }

    // The two pools whose penalties the book prints unscoped, derived ONCE:
    // the ledger below reads them for a skill and the bare rolls read them
    // through Conditions.penaltyFor, and two sites deciding "how hurt is this
    // character" is the drift this codebase has produced three times (6ag,
    // 6ai, 6be). Plain live expressions, deliberately not remembered, for the
    // reason the defence dialog's own halving is not (6dc).
    val hpNow = Pools.hp(vitals.maxHp - session.hpLost, vitals.maxHp)
    val fpNow = Pools.fp(vitals.maxFp - session.fpLost, vitals.maxFp)

    // The bare attribute rolls, which do NOT go through the ledger above
    // (RollKind.ATTRIBUTE lists them): each builds its own target. Shock
    // decides once, here, that all of them take it -- the exclusion Codex
    // warned would otherwise look like an oversight in whichever adapter
    // went unwired.
    val shockOnAttribute = Shock.penaltyFor(RollKind.ATTRIBUTE, shocked, traits)
    // Reeling and fatigue, for those same rolls. p. 40's "Reeling: -1 to
    // all rolls" and p. 42's "-1 to all rolls" are the phrase Shock already
    // reads at its word, so this is the same scope decision -- made ONCE here,
    // because an adapter left unwired looks like an oversight rather than a
    // decision (Codex, 6bo). Named separately from the shock beside it: a
    // breakdown that has to sum must say which state moved the target.
    val hurtOnAttribute = Conditions.penaltyFor(RollKind.ATTRIBUTE, hpNow, fpNow)
    // The declared states, as one number with its words for the bare rolls
    // and as modifiers for the ledger, both from the same two inputs.
    // A physical roll also takes the states scoped to those (6gv), so there
    // are two numbers, and the ledger asks which kind its roll is.
    val allRolls = remember(traits) { TraitClauses.declaredStateClauses(traits) }
    val declaredNow = TraitClauses.declaredStates(traits, declaredStates)
    val states = declaredNow.all
    val physicalStates = declaredNow.physical
    val stateModifiers = { physical: Boolean -> TraitClauses.declaredModifiers(traits, declaredStates, physical) }
    // What each self-control roll stands at, for the Traits row that prints
    // it and the dialog that opens on it ([standingTargets]).
    val standing = standingTargets(
        selfControl.values,
        vitals.will,
        attributes.iq,
        shockOnAttribute + hurtOnAttribute + states.total,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        sheet.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = { SheetMenu(sheet.name, throws, onReset) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to characters",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    // The character's own name, in the reading colour. Brass
                    // here made the title compete with the primary button two
                    // rows below it, and a name is not an action.
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            // A footer, not a tab row. Three destinations with icons, at the
            // bottom where a thumb reaches without crossing the screen -- the
            // tab row sat under the top bar, which is the furthest point from
            // where the hand holds a phone.
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                for (option in SheetTab.entries) {
                    NavigationBarItem(
                        selected = tab == option,
                        onClick = { tab = option },
                        icon = { Icon(option.icon, contentDescription = null) },
                        label = { Text(option.label) },
                    )
                }
            }
        },
    ) { insets ->
        LazyColumn(Modifier.padding(insets).fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    // A boon is held beside a potion walk, or by a mortal.
                    val boon = session.boon
                    if (drunk != null || boon != null) {
                        // The sheet rows below are still the mortal's: the
                        // diff they carry is against the published sheet, and
                        // no published sheet records a pathway. Saying so is
                        // better than silently mixing two states.
                        // Pills, not a sentence (6gl): what the Pathway tile
                        // opens is said by the tile itself.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // Lost first, and every tag in the error colour,
                            // so a narrow screen cannot wrap it out of sight.
                            when {
                                session.lost -> HeaderPill("Lost to the Rampager", lost = true)
                                session.corrupted -> HeaderPill("Lost to corruption", lost = true)
                            }
                            if (drunk != null) {
                                HeaderPill("${drunk.pathway} · Seq ${drunk.sequence}", lost = session.isLost)
                                HeaderPill("Digested ${drunk.digestion}%", lost = session.isLost)
                            }
                            if (boon != null) HeaderPill(boonPill(boon), lost = session.isLost)
                            HeaderPill("CoR ${session.cor}", lost = session.isLost)
                        }
                    }
                }
            }
            // Pinned while the list scrolls: health is checked between every
            // roll, and a strip that scrolled away was the tab it replaced.
            stickyHeader {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    // Health first and always: it sat behind the Vitals tab.
                    // SPI only for a Beyonder -- "Non-Beyonders (SPI 0) ...
                    // have no spirituality to sacrifice" (p. 41), and
                    // Pools.spi refuses a maximum of zero.
                    PoolStrip(
                        pools = listOfNotNull(
                            hpNow,
                            fpNow,
                            if (vitals.maxSpi > 0) Pools.spi(vitals.maxSpi - session.spiSpent, vitals.maxSpi) else null,
                        ),
                        onSpend = { pool, n ->
                            val max = when (pool) {
                                Pool.HP -> vitals.maxHp
                                Pool.FP -> vitals.maxFp
                                Pool.SPI -> vitals.maxSpi
                            }
                            session.spend(pool, n, max, maxCor)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SheetActions(
                        drunk = drunk,
                        ritualLevel = ritualLevel,
                        divinationLevel = divinationLevel,
                        spiritVisionForm = spiritVisionForm,
                        onPathways = onPathways,
                        onBuild = onBuild,
                        onOpen = { open = open + it },
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                }
                HorizontalDivider()
            }

            when (tab) {
                SheetTab.VITALS -> vitalsTab(
                    // The engine against the published sheet: what the rows
                    // below carry in red. A check on the book, not on the
                    // character, so it heads the stat list rather than the
                    // screen a player reads every turn.
                    diffSummary = if (differing == 0) {
                        "All ${rows.size} values match the published sheet"
                    } else {
                        "$differing of ${rows.size} values differ from the published sheet"
                    },
                    hpNow = hpNow,
                    ht = attributes.ht,
                    traits = traits,
                    hurtOnAttribute = hurtOnAttribute,
                    shockOnAttribute = shockOnAttribute,
                    shocked = shocked,
                    onClearShock = { shocked = false },
                    allRolls = allRolls,
                    declaredStates = declaredStates,
                    onDeclareStates = { declaredStates = it },
                    // The Vitals tab's rolls are all HT rolls.
                    states = physicalStates,
                    secondaryLines = secondaryLines,
                )
                SheetTab.TRAITS -> traitsTab(
                    advantages = advantages,
                    drawbacks = drawbacks,
                    grantedTraits = grantedTraits,
                    grantedBy = grantedBy(drunk != null, session.boon != null),
                    bought = bought,
                    traits = traits,
                    selfControl = selfControl,
                    standing = standing,
                    expanded = expandedTraits,
                    onToggle = { expandedTraits[it] = expandedTraits[it] != true },
                    onControl = { controlling = it },
                )
                SheetTab.SKILLS -> skillsTab(
                    sheet = sheet,
                    rows = rows,
                    skillLevels = skillLevels,
                    edits = session.edits,
                    onRoll = { rolling = it },
                    onPickUntrained = { pickingUntrained = true },
                )
            }
        }
    }

    SheetDialogs(
        open = open,
        onClose = { open = open - it },
        rolling = rolling,
        onRolling = { rolling = it },
        controlling = controlling,
        onControlling = { controlling = it },
        pickingUntrained = pickingUntrained,
        onPickingUntrained = { pickingUntrained = it },
        shocked = shocked,
        onShocked = { shocked = true },
        roundState = scene.round,
        stunnedState = scene.stunned,
        sheet = sheet,
        session = session,
        drunk = drunk,
        vitals = vitals,
        maxCor = maxCor,
        traits = traits,
        standing = standing,
        attributes = attributes,
        skillLevels = skillLevels,
        divination = divination,
        ritualLevel = ritualLevel,
        spiritVisionForm = spiritVisionForm,
        hpNow = hpNow,
        fpNow = fpNow,
        shockOnAttribute = shockOnAttribute,
        hurtOnAttribute = hurtOnAttribute,
        states = states,
        physicalStates = physicalStates,
        stateModifiers = stateModifiers,
    )
}

/** One fact about the walk, in the sheet's header. */
@Composable
private fun HeaderPill(text: String, lost: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (lost) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
}
