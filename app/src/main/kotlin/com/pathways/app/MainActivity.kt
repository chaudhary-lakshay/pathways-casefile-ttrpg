package com.pathways.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.ModEffect
import com.pathways.model.Outcome
import com.pathways.model.SkillKey
import com.pathways.model.TraitKind
import com.pathways.rules.BurnKind
import com.pathways.rules.CommonModifiers
import com.pathways.rules.Concentration
import com.pathways.rules.ConcentrationOutcome
import com.pathways.rules.Conditions
import com.pathways.rules.Corruption
import com.pathways.rules.Derive
import com.pathways.rules.Divination
import com.pathways.rules.EffortBurn
import com.pathways.rules.Equipment
import com.pathways.rules.FrightCheck
import com.pathways.rules.FrightKind
import com.pathways.rules.FrightOutcome
import com.pathways.rules.PenaltyScope
import com.pathways.rules.Pool
import com.pathways.rules.PoolStatus
import com.pathways.rules.Pools
import com.pathways.rules.PotionDigestion
import com.pathways.rules.PremadeSheet
import com.pathways.rules.PremadeSkill
import com.pathways.rules.Premades
import com.pathways.rules.RandomSource
import com.pathways.rules.RitualMode
import com.pathways.rules.RollKind
import com.pathways.rules.SelfControl
import com.pathways.rules.SelfControlCheck
import com.pathways.rules.SelfControlOutcome
import com.pathways.rules.SheetComparison
import com.pathways.rules.SheetRow
import com.pathways.rules.Shock
import com.pathways.rules.SkillRoll
import com.pathways.rules.SkillRollOutcome
import com.pathways.rules.SpiritualSenses
import com.pathways.rules.SuccessDegree
import com.pathways.rules.TraitClause
import com.pathways.rules.TraitEffects
import com.pathways.rules.content.CommonModifier
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.FrightTrigger
import com.pathways.rules.content.SenseModifier
import com.pathways.rules.content.SpiritualSense
import java.io.File
import kotlin.random.Random

/** The app's real dice. Tests inject [com.pathways.rules.SeededRandom] instead. */
private val appDice = RandomSource { sides -> Random.nextInt(1, sides + 1) }

/** The destinations reachable from a selected premade. */
private enum class Screen { SHEET, PATHWAY, BUILDER }

/** [SheetComparison] emits the seven secondaries before any skill row. */
private const val SECONDARY_ROWS = 7

/**
 * Steps offered past an open-ended modifier's printed end ("-6 or worse").
 *
 * The book names no ceiling, so this is a UI reading and not a rule: the same
 * one the open-ended ritual fuel cell takes (6q). `CommonModifiers.valueFor`
 * still validates whatever is passed, so nothing here can widen the book.
 */
private const val OPEN_END_STEPS = 3

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ponytail: loaded on the main thread. 23 small JSONs off the APK
        // classpath; move behind a ViewModel when the sheet screen needs state
        // that survives rotation, not before.
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
    // One walk down a pathway per selected premade, restored from disk and
    // written back on every change.
    val session = remember(selected) {
        val name = selected?.name
        BeyonderSession(saved[name] ?: SavedSession()) { updated ->
            if (name != null) {
                saved[name] = updated
                store.save(saved)
            }
        }
    }

    when (val sheet = selected) {
        null -> PremadeGallery(sheets, themePrefs) { selected = it }
        else -> {
            BackHandler {
                if (screen == Screen.SHEET) selected = null else screen = Screen.SHEET
            }
            when (screen) {
                Screen.SHEET -> SheetScreen(
                    sheet,
                    session = session,
                    onPathways = { screen = Screen.PATHWAY },
                    onBuild = { screen = Screen.BUILDER },
                    onBack = { selected = null },
                )
                Screen.PATHWAY -> PathwayScreen(sheet, session, appDice) { screen = Screen.SHEET }
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
fun SheetScreen(
    sheet: PremadeSheet,
    session: BeyonderSession,
    onPathways: () -> Unit,
    onBuild: () -> Unit,
    onBack: () -> Unit,
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
    var tab by remember(sheet) { mutableStateOf(SheetTab.SKILLS) }
    var frightening by remember(sheet) { mutableStateOf(false) }
    // The Major Wound's shock, latched by the damage dialog that rolls for it
    // and cleared by the GM when the turn passes. ONE owner, above every
    // screen that reads it: a chip per dialog would let the same one-turn fact
    // disagree with itself across surfaces (Codex, at design time). In memory
    // rather than in the session file, because a one-turn fact that survives a
    // force-stop has outlived its turn by construction.
    var shocked by remember(sheet) { mutableStateOf(false) }
    var focusing by remember(sheet) { mutableStateOf(false) }
    var casting by remember(sheet) { mutableStateOf(false) }
    var defending by remember(sheet) { mutableStateOf(false) }
    var attacking by remember(sheet) { mutableStateOf(false) }
    var damaging by remember(sheet) { mutableStateOf(false) }
    var divining by remember(sheet) { mutableStateOf(false) }
    var supplicating by remember(sheet) { mutableStateOf(false) }
    var handling by remember(sheet) { mutableStateOf(false) }
    var sensing by remember(sheet) { mutableStateOf(false) }
    // The CHECK, not the outcome, exactly as `rolling` holds the skill: the
    // roll happens inside the dialog, after the GM has stated a modifier.
    var controlling by remember(sheet) { mutableStateOf<SelfControlCheck?>(null) }
    // Maximums move with the potions drunk: HT gains lift FP, and SPI is
    // entirely a potion's gift. Recomputed from the walk, never stored.
    val vitals = remember(sheet, drunk, session.edits) {
        val base = session.characterFor(sheet)
        val walked = PotionDigestion.of(base.copy(beyonder = drunk), ContentLoader.pathways())
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
    // The CoR ceiling is the character's CURRENT Will, which a potion can
    // move. Computed once: the ritual dialog and the SPI tracker's Soul Debt
    // both charge against it, and two readings of one rule is how they drift.
    // The traits actually in play -- the sheet's, plus anything the edit
    // layer changed. Hoisted because three readers need them: the CoR cap,
    // a skill roll, and the active defences.
    val traits = remember(sheet, session.edits) { session.characterFor(sheet).traits }
    val maxCor = remember(sheet, vitals.will, traits) {
        Corruption.maxFor(vitals.will, traits)
    }
    // The disadvantages that print a number to roll under (6am). Until this
    // slice a character's disadvantages rendered NOWHERE in the app at all --
    // `TraitEffects.reported` reads the advantage catalogue only -- so the
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
    val drawbacks = remember(traits) {
        val printed = ContentLoader.disadvantages().associateBy { it.name.lowercase() }
        traits.filter { it.kind == TraitKind.DISADVANTAGE }
            // Through the one resolution, so a sheet that printed the effect
            // beside the name still finds its row: Church Healer stores
            // "Charity - <the book's own cell>" and got no effect line at all.
            .map { it to printed[TraitEffects.catalogueName(it.name).lowercase()]?.notes }
    }
    // Damage rolls off ST, Boxing's bonus off DX and a Major Wound off HT, all
    // AFTER any potion that raised them -- the same digested character the
    // skill levels come from.
    val attributes = remember(sheet, drunk, session.edits) {
        val played = session.characterFor(sheet)
        PotionDigestion.of(played.copy(beyonder = drunk), ContentLoader.pathways())
            ?.effectiveAttributes
            ?: played.attributes
    }
    // The levels actually rolled: a potion's grants are in here, the sheet's
    // printed column is not. Computed once for defence, attack and the ritual
    // gate rather than three times over.
    val skillLevels = remember(sheet, drunk, session.edits) {
        val played = session.characterFor(sheet)
        PotionDigestion.of(played.copy(beyonder = drunk), ContentLoader.pathways())
            ?.skillLevels
            ?: Derive.of(played).skillLevels
    }
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
    )
    val divination = levels.divination
    val ritual = levels.ritual
    val divinationLevel = divination?.level
    val ritualLevel = ritual?.level

    // What the trackers are costing a roll of [key]. [cor] is a parameter
    // because RitualResolution applies the corruption ladder itself, and
    // passing it here as well would charge a corrupted caster twice.
    // [spiAboutToSpend] is SPI this roll will pay BEFORE it is rolled, which
    // ch. 6.5 pays in Step 2 and rolls in Step 3 (p. 55). Zero everywhere
    // else, where nothing is paid to make the roll at all.
    fun conditionsFor(
        key: SkillKey,
        cor: Int = session.cor,
        spiAboutToSpend: Int = 0,
        /**
         * Three callers collapse this list with `sumOf` into one number they
         * label "hurt or tired" or "circumstances". Shock is neither, so they
         * take it as its own argument and name it; folding it into a bucket
         * would move a target by 4 under the wrong reason (Codex).
         */
        withShock: Boolean = true,
    ) = Conditions.forSkill(
        skill = key,
        hp = Pools.hp(vitals.maxHp - session.hpLost, vitals.maxHp),
        fp = Pools.fp(vitals.maxFp - session.fpLost, vitals.maxFp),
        spi = if (vitals.maxSpi > 0) {
            Pools.spi(vitals.maxSpi - session.spiSpent - spiAboutToSpend, vitals.maxSpi)
        } else {
            null
        },
        cor = cor,
    ) + if (withShock) Shock.modifiers(shocked, traits) else emptyList()

    // The four rolls that do NOT go through the ledger above: a Fright Check,
    // a self-control roll, a concentration check and a detection roll each
    // build their own target. Shock decides once, here, that all four take it
    // -- the exclusion Codex warned would otherwise look like an oversight in
    // whichever adapter went unwired.
    val shockOnAttribute = Shock.penaltyFor(RollKind.ATTRIBUTE, shocked, traits)
    // The same number the ledger would have carried, for the three dialogs
    // that reduce it to an Int and print their own breakdown.
    val shockOnSkill = Shock.penaltyFor(RollKind.SKILL, shocked, traits)
    // Ether Body Damage's "-1 to all SPI-based rolls". The ledger carries it
    // for an SPI-controlled SKILL, but two rolls reach their target another
    // way -- a detection roll takes a bare attribute, and a divination builds
    // its target through Divination.targetFor -- so they are handed the number
    // here. Decided ONCE, for the same reason shock is: an adapter left
    // unwired looks like an oversight rather than a decision (Codex, 6bo).
    val spiTrait = TraitEffects.spiRollBonus(traits)

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
                    if (drunk != null) {
                        // The sheet rows below are still the mortal's: the
                        // diff they carry is against the published sheet, and
                        // no published sheet records a pathway. Saying so is
                        // better than silently mixing two states.
                        Text(
                            "Sequence ${drunk.sequence} of the ${drunk.pathway} Pathway, " +
                                "digested ${drunk.digestion}%, ${session.cor} CoR — " +
                                when {
                                    session.lost -> "LOST to the Rampager"
                                    session.corrupted -> "LOST to corruption"
                                    else -> "see the pathway screen for what it grants"
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (session.isLost) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    Text(
                        if (differing == 0) {
                            "all ${rows.size} values match the published sheet"
                        } else {
                            "$differing of ${rows.size} values differ from the published sheet"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )

                    ActionHub(Modifier.padding(top = 12.dp, bottom = 12.dp)) {
                        // The pathway was a full-width brass Button above this
                        // grid, which made it a thirteenth action drawn to
                        // different rules -- and cost a whole row of height to
                        // say what a tile says. Arcane, because drinking is how
                        // a mortal stops being one; ungated, because that is
                        // exactly who needs it.
                        HubAction(
                            if (drunk == null) "Drink a potion" else "Pathway",
                            Icons.Filled.Science,
                            ActionKind.BEYONDER,
                            onPathways,
                        )
                        HubAction("Fright", Icons.Filled.Psychology, ActionKind.ROLL) {
                            frightening = true
                        }
                        // Ungated, like Fright and Detect: the maneuver is
                        // "powers or skills requiring focus", and which of
                        // them counts is the GM's call, not a skill list this
                        // book never prints (6i).
                        HubAction("Concentrate", Icons.Filled.CenterFocusStrong, ActionKind.ROLL) {
                            focusing = true
                        }
                        // Not gated. Divination Arts is the DIVINER's skill;
                        // being divined needs no skill at all, and the book's
                        // detection roll falls back to Per, which a mortal
                        // has. Who suspects is the GM's call (Detection).
                        HubAction("Detect", Icons.Filled.Radar, ActionKind.ROLL) {
                            sensing = true
                        }
                        HubAction("Attack", Icons.Filled.Bolt, ActionKind.COMBAT) {
                            attacking = true
                        }
                        HubAction("Defend", Icons.Filled.Shield, ActionKind.COMBAT) {
                            defending = true
                        }
                        HubAction("Damage", Icons.Filled.HeartBroken, ActionKind.COMBAT) {
                            damaging = true
                        }
                        // Each gated individually, which the grid allows and a
                        // labelled group did not: an absent tile leaves no hole,
                        // where an absent group left a heading over empty space.
                        if (ritualLevel != null) {
                            HubAction("Ritual", Icons.Filled.AutoAwesome, ActionKind.BEYONDER) {
                                casting = true
                            }
                        }
                        if (divinationLevel != null) {
                            HubAction("Divine", Icons.Filled.Visibility, ActionKind.BEYONDER) {
                                divining = true
                            }
                        }
                        if (ritualLevel != null) {
                            // Same gate as the ritual: Chapter 13 rolls the
                            // same skill, because a boon IS a ritual.
                            HubAction("Boon", Icons.Filled.Church, ActionKind.BEYONDER) {
                                supplicating = true
                            }
                        }
                        HubAction("Spend points", Icons.Filled.Calculate, ActionKind.MANAGE) {
                            onBuild()
                        }
                        // Not gated on anything. A Sealed Artifact is found,
                        // not bought or awarded by Sequence: the grade table's
                        // only gate is church rank, which this app does not
                        // model. See SealedArtifacts.
                        HubAction("Artifacts", Icons.Filled.Lock, ActionKind.MANAGE) {
                            handling = true
                        }
                    }
                }
                HorizontalDivider()
            }

            if (tab == SheetTab.VITALS) {
                item { PoolTrackers(session, vitals, maxCor) }
            }
            if (shocked && tab == SheetTab.VITALS) {
                item {
                    // Only while it is in force: a control that is always
                    // there would read as a state the GM must maintain, and
                    // this one is latched by the roll that caused it.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Shock — \"${Shock.printed()}\"" +
                                if (Shock.ignoredBy(traits)) {
                                    " — ignored by ${Shock.IGNORED_BY}"
                                } else {
                                    ""
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        // Cleared by hand, because nothing here counts turns.
                        TextButton(onClick = { shocked = false }) { Text("Clear") }
                    }
                }
            }

            // Traits whose printed effect is real and lands on nothing this
            // app rolls. Shown with the reason rather than silently dropped:
            // a GM who can read "no shock penalty to ignore" applies it
            // themselves, one who sees nothing assumes it was counted. The
            // call the artifact grade table (6af) already needed.
            items(if (tab == SheetTab.TRAITS) TraitEffects.reported(traits) else emptyList()) { note ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        "${note.trait} — ${note.printed}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        // Muted, not brass. This is a caveat about what the app
                        // does NOT do; painting it the accent colour made every
                        // trait row shout in the same voice as the Roll button
                        // it is not (docs/DESIGN-DIRECTION.md).
                        "Not applied here: ${note.reason}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // The disadvantages, which until this slice rendered NOWHERE in
            // the app: `TraitEffects.reported` reads the advantage catalogue
            // only, so a character's drawbacks -- and the ~24 rows that print
            // a number to roll under -- had no home at all. The same hole
            // 5dcfc25 fixed for bought skills, one list over.
            //
            // The self-control rows are tappable, and nothing rolls on its
            // own: WHEN to call for one is the GM's, exactly as a Fright Check
            // (6c) and a Major Wound's HT roll (6n) are, and both of those
            // ship as a button the GM presses.
            items(if (tab == SheetTab.TRAITS) drawbacks else emptyList()) { (trait, printed) ->
                // The sheet's own name minus the effect cell shown below it,
                // where the two were printed as one string. Keyed the same way
                // `SelfControlCheck.trait` is, so the row and its roll cannot
                // disagree about what the trait is called.
                val shown = TraitEffects.displayName(trait.name)
                val check = selfControl[shown]
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (check != null) {
                                Modifier.clickable { controlling = check }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        // `Trait.cost` is a MAGNITUDE and the sign lives in
                        // the kind (6u). Printed back with the minus the book
                        // gives a disadvantage, never negated in the data.
                        "$shown — −${trait.cost} pts" +
                            (
                                check?.let {
                                    " — 3d6 ≤ " +
                                        SelfControl.targetFor(
                                            it.spec,
                                            vitals.will,
                                            attributes.iq,
                                        )
                                } ?: ""
                                ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (check != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    if (printed != null) {
                        Text(printed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // The derived secondaries (HP, Will, Per, Basic Speed...) sit with
            // the pools rather than the skills: they are what the character IS,
            // and the disagreement with the published sheet is printed here.
            items(
                if (tab == SheetTab.VITALS) rows.take(SECONDARY_ROWS) else emptyList(),
            ) { ComparisonRow(it) }

            // Row order past the secondaries is one per purchased skill, in
            // sheet order - pinned by SheetComparisonTest. That is what makes
            // this positional pairing safe.
            itemsIndexed(
                if (tab == SheetTab.SKILLS) sheet.skills else emptyList(),
            ) { i, skill ->
                val row = rows.getOrNull(SECONDARY_ROWS + i)
                if (row != null) {
                    // The row SHOWS the sheet's own number, because that
                    // comparison is the engine against the book and a potion is
                    // neither (6e). What it ROLLS is the character in front of
                    // the player: a Paragon Seq 9 potion takes this sheet's
                    // Research from 13 to 17, and rolling the printed figure
                    // rolls a character who no longer exists.
                    val roll = {
                        rolling = RollableSkill(
                            skill.label,
                            liveLevelOf(skill.key(), skillLevels, sheet.levelFor(skill)),
                            skill.key(),
                        )
                    }
                    ComparisonRow(row, Modifier.clickable(onClick = roll), onRoll = roll)
                }
            }

            // Every row on the screen, so a label can be checked against the
            // others before it is shown. Only the rows BELOW the comparison
            // table are ever suffixed: the published rows render the sheet's
            // own label and the sheet's own number, and rewriting either would
            // edit the diff that is the engine against the book (6e).
            val granted = grantedSkills(sheet, session.edits, skillLevels)
            val addedKeys = sheet.skills.map { it.key() } +
                session.edits.purchasedKeys() + granted.map { it.first }

            // Skills bought in play. NOT comparison rows: `SheetComparison`
            // keeps using the unedited sheet, because that diff is the engine
            // against the book (6e), so a bought skill has no published
            // column to differ from. Until now it also had no way to be
            // ROLLED -- Ritualistic Magic and Divination Arts got their own
            // buttons and every other purchase was invisible, which is how a
            // chapter's result table could be wired up and still be
            // unreachable. The level comes from [skillLevels], the same
            // digested map the ritual and attack dialogs read.
            // Resolved through the book, exactly as `grantedSkills` resolves
            // them, so the two lists agree on which skill a purchase IS. When
            // they disagreed, a saved skill the catalogue no longer prints
            // failed to suppress itself and a potion's skill of the same name
            // appeared twice, once as bought and once as granted (Codex).
            items(
                if (tab == SheetTab.SKILLS) session.edits.purchasedKeys() else emptyList(),
            ) { key ->
                val level = liveLevelOrNull(key, skillLevels)
                if (level != null) {
                    val label = disambiguated(key, addedKeys)
                    AddedSkillRow(label, level, "bought") {
                        rolling = RollableSkill(label, level, key)
                    }
                }
            }

            // Skills a POTION granted. The two lists above are one row per
            // published skill and one per purchase, so between them they
            // cannot show a skill the book handed over -- and ch. 9 p. 85
            // forbids buying the three SPI-based ones, making a grant their
            // only route (6aq). Chapter 4 prints a result table for two of
            // them; 6aj wired those up and this is what could reach them.
            //
            // Ritualistic Magic and Divination Arts appear here as well as on
            // their own buttons, deliberately: those buttons cast and divine,
            // and a GM may still call for a plain roll of the skill.
            items(if (tab == SheetTab.SKILLS) granted else emptyList()) { (key, level) ->
                val label = disambiguated(key, addedKeys)
                AddedSkillRow(label, level, "potion") {
                    rolling = RollableSkill(label, level, key)
                }
            }
        }
    }

    // Ch. 2's burn, charged the moment it is bought. The book allows it after
    // the result is seen, so the pool moves when the player pushes -- printing
    // the rule and not charging it is how a tracker starts lying (6m).
    //
    // ONE lambda for both dialogs that offer the push. The attack dialog used
    // to charge only the physical half, so a mystical push there would have
    // moved the target for free; two copies of "which pool does this kind
    // spend" is the same-predicate-at-two-call-sites defect, and the cure is
    // to answer it once (a design review, on this slice's decomposition).
    val burn: (BurnKind, Int) -> Unit = { kind, points ->
        val pool = if (kind == BurnKind.PHYSICAL) Pool.FP else Pool.SPI
        val max = if (pool == Pool.FP) vitals.maxFp else vitals.maxSpi
        session.spend(pool, points, max, maxCor)
    }

    rolling?.let { skill ->
        RollDialog(
            label = skill.label,
            base = skill.base,
            // Null for every skill but two. Chapter 4 prints a result table
            // for Spiritual Intuition and Spiritual Perception and for
            // nothing else, so the rest keep Appendix A's general ladder.
            sense = SpiritualSenses.forSkill(skill.key),
            // Which pool may push this roll, decided by the skill's own
            // controlling attribute. Null for IQ, Will and Per, which the
            // book's two rows name neither of -- so no burn is offered
            // rather than fatigue being allowed on a Research roll.
            burnKind = EffortBurn.kindFor(skill.key)
                ?.takeIf { it != BurnKind.MYSTICAL || vitals.maxSpi > 0 },
            // The pool's BALANCE, not its maximum. The gate above excludes a
            // mortal, who never had SPI; this excludes a Beyonder who has
            // spent it, which the gate above cannot see.
            spiAvailable = vitals.maxSpi - session.spiSpent,
            conditionalBurnNote = EffortBurn.conditionalScope(skill.key),
            // Chapter 8's gear that names this exact skill, printed as the
            // book prints it. Not a chip: nothing here records what the
            // character owns, so the screen cannot assert possession, and
            // two of these rows would stack into a bonus the book never
            // grants. Rules decides which rows the sentence names.
            equipment = Equipment.referenceFor(skill.key),
            // Offered only where the book's own cells apply: a social skill,
            // on a character carrying a row that names a circumstance. Rules
            // decides both, so the chips cannot disagree with the modifiers.
            socialClauses = if (
                ContentLoader.skills()
                    .firstOrNull { it.name == skill.key.name }?.category ==
                TraitEffects.SOCIAL_SKILLS
            ) {
                TraitEffects.socialClauses(traits)
            } else {
                emptyList()
            },
            onRoll = { circumstances, declaredSocial ->
                // The trackers reach the dice here: a reeling, fatigued or
                // corrupted character no longer rolls at full skill. The GM's
                // circumstances are a separate source and simply join the list.
                SkillRoll.at(
                    label = skill.label,
                    base = skill.base,
                    key = skill.key,
                    source = appDice,
                    // Three sources now: what the trackers cost (Conditions),
                    // what the character IS (TraitEffects -- Alertness on a
                    // Per roll, Fit on an HT one), and what the GM says the
                    // situation is.
                    mods = conditionsFor(skill.key) +
                        TraitEffects.modifiers(traits, skill.key, declaredSocial) +
                        circumstances,
                )
            },
            onBurn = burn,
            onDismiss = { rolling = null },
        )
    }
    controlling?.let { check ->
        SelfControlDialog(check, vitals.will, attributes.iq, shockOnAttribute) { controlling = null }
    }

    if (frightening) {
        // The traits, not a number: this dialog was the one roll-building
        // boundary that discarded them, so five catalogue rows that modify a
        // Fright Check could not reach the roll they name (Codex, on the
        // design). [TraitEffects] decides which apply and by how much; the
        // screen only asks which circumstances hold.
        FrightDialog(
            will = vitals.will,
            clauses = remember(traits) { TraitEffects.frightClauses(traits) },
            shock = shockOnAttribute,
        ) { frightening = false }
    }
    if (focusing) {
        ConcentrateDialog(vitals.will, shockOnAttribute) { focusing = false }
    }
    if (damaging) {
        DamageDialog(
            // The same test that hides the SPI tracker: a mortal has no
            // spirituality to spend, so it is not offered as somewhere to
            // absorb a hit.
            hasSpirituality = vitals.maxSpi > 0,
            maxHp = vitals.maxHp,
            ht = attributes.ht,
            dice = appDice,
            onApply = { pool, amount ->
                val max = when (pool) {
                    Pool.HP -> vitals.maxHp
                    Pool.FP -> vitals.maxFp
                    Pool.SPI -> vitals.maxSpi
                }
                session.spend(pool, amount, max, maxCor)
            },
            onShocked = { shocked = true },
            majorWoundHt = TraitEffects.majorWoundHt(traits),
            onDismiss = { damaging = false },
        )
    }
    if (defending) {
        // A plain live expression, deliberately NOT remembered: `fpLost` is
        // observable state and `vitals` is keyed on the sheet, the walk and the
        // edits, so a remembered halving would freeze at whatever the pool held
        // when one of THOSE last changed. The same reason `spiAvailable` below
        // is written out rather than cached.
        val fatigue = Pools.fp(vitals.maxFp - session.fpLost, vitals.maxFp)
        DefenseDialog(
            // p. 42: "At 1/3 FP or below ... Move and Dodge halved". The sheet
            // screen has printed that sentence since 6b; until now the dialog
            // one tap away offered the whole Dodge. Halved here, at the one
            // reader of the derived score -- the published-vs-computed row in
            // SheetComparison is an audit of the DERIVATION and stays whole.
            dodge = Pools.halvedForFatigue(vitals.dodge, fatigue),
            fatigued = Pools.isFatigued(fatigue),
            skillLevels = skillLevels,
            weapons = session.characterFor(sheet).weapons,
            dice = appDice,
            // The same ledger the attack dialog and the sheet's own skill rolls
            // use. An Acrobatic Dodge is a skill roll like any other, and
            // rolling it bare would have a reeling character roll Acrobatics at
            // two different targets depending on which screen asked (a design
            // review's finding, and reachable today).
            conditions = { conditionsFor(it) },
            // The traits themselves: Combat Reflexes both adds +1 to every
            // defence and cancels the surprise refusal, and one trait's two
            // consequences belong in one place (ActiveDefenses).
            traits = traits,
            shocked = shocked,
            onSpendFp = { session.spend(Pool.FP, it, vitals.maxFp, maxCor) },
        ) { defending = false }
    }
    if (attacking) {
        AttackDialog(
            skillLevels = skillLevels,
            conditions = { conditionsFor(it) },
            st = attributes.st,
            dx = attributes.dx,
            dice = appDice,
            // An All-Out maneuver's own FP is spent like any other: through
            // the tracker, so the next roll is made by a tireder character.
            // Distinct from [burn]: this is the maneuver's printed price, not
            // Ch. 2's push.
            onSpendFp = { session.spend(Pool.FP, it, vitals.maxFp, maxCor) },
            // A plain expression, deliberately NOT remembered. `spiSpent` is
            // observable state, so this recomputes on every recomposition and
            // the second attack of a Rapid Strike sees what the first one
            // spent. Captured in a `remember` it would go stale and let one
            // maneuver spend the same SPI twice (a design review's finding,
            // and invisible in a diff because the stale version looks right).
            spiAvailable = vitals.maxSpi - session.spiSpent,
            onBurn = burn,
            darknessReduction = TraitEffects.darknessReduction(traits),
            onDismiss = { attacking = false },
        )
    }
    if (divining && divinationLevel != null) {
        DivinationDialog(
            skillLevel = divinationLevel,
            // Divination Arts is SPI/Hard, and this dialog builds its target
            // through Divination.targetFor rather than the ledger.
            spiTrait = spiTrait,
            sourceLine = divination?.let {
                Divination.sourceLine(it, Divination.SKILL, RITUAL_SKILL)
            },
            // Null for a mortal, who has no Sequence for Sec. 6 to compare.
            divinerSequence = drunk?.sequence,
            // Ch. 6.5 p. 55's four supporting skills, which this app has
            // loaded since 6p and offered to nobody: Divination.supportBonus
            // had no production caller at all, so a diviner with Occultism and
            // Psychology rolled flat where the book gives +2. 6am's finding
            // one chapter over, and that slice fixed the ritual half only.
            // Read from the DIGESTED map for the same reason the ritual row
            // is (6as): a potion grants Occultism and six spellings of Hidden
            // Lore, and a Beyonder granted one would otherwise be denied the
            // bonus in silence.
            ownedSupportSkills = remember(skillLevels) {
                Divination.supportSkillsOwnedBy(skillLevels.keys.map { it.name })
            },
            // Divination Arts is SPI/Hard, so Spiritual Attrition reaches it.
            // The CoR ladder does not: it is scoped to ritual rolls (p. 68).
            // Asked per method cost, not computed once: paying for the
            // question can itself push the diviner across a Spiritual
            // Attrition threshold, and the book charges before it rolls.
            conditionPenaltyAfterSpending = { cost ->
                conditionsFor(
                    key = SkillKey(
                        Divination.SKILL,
                        null,
                        ControllingAttribute.Spi,
                        Difficulty.HARD,
                    ),
                    cor = 0,
                    spiAboutToSpend = cost,
                    withShock = false,
                ).sumOf { (it.effect as ModEffect.Flat).n }
            },
            shock = shockOnSkill,
            spiAvailable = vitals.maxSpi - session.spiSpent,
            dice = appDice,
            // Through the tracker, so a question asked at 0 SPI buys the Soul
            // Debt the book charges for it.
            onSpendSpi = { session.spend(Pool.SPI, it, vitals.maxSpi, maxCor) },
            // Sec. 6's HP Sacrifice: 4+ HP forgives 2 of a target's protection.
            // Through the tracker, so a diviner who bleeds for an answer ends
            // the scene wounded.
            onSpendHp = { session.spend(Pool.HP, it, vitals.maxHp, maxCor) },
            onDismiss = { divining = false },
        )
    }
    if (sensing) {
        // The other side of the divination dialog. Nothing is spent here: the
        // book calls this "a free action (once per divination attempt)" that
        // "does not cost SPI".
        DetectionDialog(
            shock = shockOnAttribute,
            // The SPI branch's mirror of alertness below, and equally unable
            // to be reached by SkillKey scoping.
            spiTrait = spiTrait,
            // Ch. 3's Alertness. This is a Per roll made outside SkillKey, so
            // TraitEffects.modifiers cannot reach it; passed explicitly rather
            // than left a silent gap (found in review).
            alertness = TraitEffects.levelOf(traits, TraitEffects.ALERTNESS)
                ?.times(TraitEffects.ALERTNESS_PER_LEVEL) ?: 0,
            // The ATTRIBUTE, not what is left in the pool. The book rolls
            // "SPI or Per, whichever is higher" the way any attribute roll
            // goes; passing the remaining pool made a caster who had spent
            // SPI on a ritual worse at noticing, and in Soul Debt it went
            // NEGATIVE — found on the emulator, which printed "the higher of
            // SPI -2 and Per 10".
            spi = vitals.maxSpi,
            per = vitals.per,
            dice = appDice,
            onDismiss = { sensing = false },
        )
    }
    if (casting && ritualLevel != null) {
        RitualDialog(
            skillLevel = ritualLevel,
            // Section II's five, narrowed to the ones this character bought.
            // Offering all five would let a GM claim an Occultism the sheet
            // does not print; the book means skills the caster brings.
            // The matching lives in RitualMode, not here: a screen with its
            // own comparison can offer a chip `supportBonus` then refuses, or
            // withhold one it would accept. It did -- one of the five rows is
            // printed "Hidden Lore (relevant domain)" and no sheet spells it
            // that way, so the caster's +1 was dropped in silence.
            // Read from the DIGESTED map, not from what was purchased. A
            // potion grants Occultism, Thaumatology, Research and six
            // spellings of Hidden Lore, and a Beyonder granted one was denied
            // Section II's +1 in silence -- the same silence this slice was
            // written to end, one route over. `drunk` is in the memo key for
            // the same reason: without it a potion would not recompute the row.
            ownedSupportSkills = remember(skillLevels) {
                RitualMode.supportSkillsOwnedBy(skillLevels.keys.map { it.name })
            },
            // cor = 0 here on purpose: RitualResolution applies the CoR
            // ladder itself, so including it would charge the caster twice
            // for the same rule.
            conditionPenalty = conditionsFor(
                key = SkillKey(
                    Conditions.RITUAL_SKILL,
                    null,
                    ControllingAttribute.Buyable(Attr.IQ),
                    Difficulty.VERY_HARD,
                ),
                cor = 0,
                withShock = false,
            ).sumOf { (it.effect as ModEffect.Flat).n },
            shock = shockOnSkill,
            cor = session.cor,
            spiAvailable = vitals.maxSpi - session.spiSpent,
            dice = appDice,
            onCorGained = { gained -> session.gainCor(gained, maxCor) },
            // Through the tracker, so a ritual fuelled past 0 SPI buys the
            // Soul Debt the book charges for it.
            onSpendSpi = { session.spend(Pool.SPI, it, vitals.maxSpi, maxCor) },
            // Ch. 7 IV.B: 2 HP buys 1 SPI of fuel. Through the tracker, so a
            // caster who bleeds for a ritual actually ends it wounded.
            onSpendHp = { session.spend(Pool.HP, it, vitals.maxHp, maxCor) },
            onDismiss = { casting = false },
        )
    }
    if (handling) {
        ArtifactDialog(
            spiAvailable = vitals.maxSpi - session.spiSpent,
            // Both through the tracker, so an artifact used at 0 SPI buys the
            // Soul Debt the book charges for it, and the gauntlet's Soul's
            // Price actually wounds the hand it is worn on.
            onSpendSpi = { session.spend(Pool.SPI, it, vitals.maxSpi, maxCor) },
            onSpendHp = { session.spend(Pool.HP, it, vitals.maxHp, maxCor) },
            onDismiss = { handling = false },
        )
    }
    if (supplicating && ritualLevel != null) {
        BoonDialog(
            skillLevel = ritualLevel,
            // cor = 0 for the same reason the ritual passes 0: Boons routes
            // through RitualResolution, which applies the ladder itself.
            conditionPenalty = conditionsFor(
                key = SkillKey(
                    Conditions.RITUAL_SKILL,
                    null,
                    ControllingAttribute.Buyable(Attr.IQ),
                    Difficulty.VERY_HARD,
                ),
                cor = 0,
                withShock = false,
            ).sumOf { (it.effect as ModEffect.Flat).n },
            shock = shockOnSkill,
            cor = session.cor,
            spiAvailable = vitals.maxSpi - session.spiSpent,
            dice = appDice,
            onCorGained = { gained -> session.gainCor(gained, maxCor) },
            onSpendSpi = { session.spend(Pool.SPI, it, vitals.maxSpi, maxCor) },
            onDismiss = { supplicating = false },
        )
    }
}

/**
 * A Fright Check at the table (Ch. 5, p. 43).
 *
 * ponytail: the trigger list IS the interface. Two of the book's five rows
 * print a range and one is open-ended, so the app offers the gentler end it
 * prints and shows the range beside it -- the severity inside that range is
 * the GM's call, exactly like the acting-quality ranges on the pathway screen.
 */
@Composable
private fun FrightDialog(
    will: Int,
    clauses: List<TraitClause> = emptyList(),
    shock: Int = 0,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(FrightKind.FRIGHT) }
    var outcome by remember { mutableStateOf<FrightOutcome?>(null) }
    var picked by remember { mutableStateOf<FrightTrigger?>(null) }
    var modifier by remember { mutableIntStateOf(0) }
    // Which circumstances the GM says hold. Keyed on the clause, because one
    // cell prints two ("-2 on direct exposure; -1 in consecrated buildings")
    // and a key that could not tell them apart would move both at once (6r).
    var declared by remember(clauses) { mutableStateOf(emptySet<String>()) }
    val triggers = remember { FrightCheck.triggers() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(outcome?.let { if (it.passed) "Held their nerve" else "Fright Check failed" } ?: "Fright Check") },
        text = {
            Column {
                val result = outcome
                val chosen = picked
                if (result == null && chosen != null) {
                    // Three of the book's five rows do not print a single
                    // number -- "-2 to -6" and "-8 or worse" are severities the
                    // GM chooses. Rolling the gentlest end for them would
                    // quietly turn their failures into successes.
                    Text(chosen.trigger, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "the book prints ${chosen.printed}" +
                            if (chosen.openEnded) " — no floor, so worse is legal" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { modifier -= 1 }) { Text("−1") }
                        Text(
                            (if (modifier > 0) "+" else "") + modifier,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        TextButton(onClick = { modifier += 1 }) { Text("+1") }
                    }
                    // The character's own traits. Unconditional clauses are
                    // counted without asking; each conditional one is a chip,
                    // because whether this character is in a church or facing
                    // the thing they fear is the GM's to say and this app
                    // holds no scene.
                    val conditional = clauses.filter { it.conditional }
                    if (conditional.isNotEmpty()) {
                        Text(
                            "Does any of this hold?",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        FlowRow {
                            conditional.forEach { clause ->
                                TextButton(onClick = {
                                    declared = if (clause.key in declared) {
                                        declared - clause.key
                                    } else {
                                        declared + clause.key
                                    }
                                }) {
                                    Text(
                                        "${clause.shown}: ${clause.printed}",
                                        color = if (clause.key in declared) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                    // ONE value, displayed and rolled: computing the caption
                    // from one expression and the roll from another is how
                    // these numbers stopped reconciling (6ag, 6ai). The trait
                    // total comes from rules for the same reason -- a screen
                    // that summed its own chips would be the second copy.
                    val fromTraits = TraitEffects.frightBonus(clauses, declared)
                    val effective = modifier + shock + fromTraits.total
                    Text("3d6 vs ${will + effective}", style = MaterialTheme.typography.bodySmall)
                    if (shock != 0) {
                        Text(
                            "includes $shock from shock — \"${Shock.printed()}\"",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    fromTraits.line?.let {
                        Text("includes $it", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(
                        // Guarded rather than relying on the button leaving the
                        // tree: two taps before a recomposition would otherwise
                        // roll twice and keep the second, the re-entrancy the
                        // divination dialog was hardened for (6p).
                        enabled = outcome == null,
                        onClick = {
                            if (outcome == null) {
                                outcome = FrightCheck.against(will, effective, appDice)
                            }
                        },
                    ) {
                        Text("Roll")
                    }
                } else if (result == null) {
                    Text("3d6 vs Will $will, modified by what was witnessed.")
                    Row {
                        FrightKind.entries.forEach { option ->
                            TextButton(onClick = { kind = option }) {
                                Text(
                                    option.name.lowercase(),
                                    color = if (option == kind) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                    triggers.forEach { trigger ->
                        Text(
                            "${trigger.trigger} (${trigger.printed})",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    picked = trigger
                                    modifier = trigger.modifier
                                }
                                .padding(vertical = 8.dp),
                        )
                    }
                } else {
                    Text(
                        "3d6: ${result.roll.total} vs ${result.target}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (!result.passed) {
                        Text("failed by ${result.margin}; effect roll ${result.effectRoll}")
                        Text(
                            "${result.effect?.printed}: ${result.effect?.let { FrightCheck.text(it, kind) }}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        },
    )
}

/**
 * One self-control roll at the table (Ch. 3, 6am).
 *
 * The consequence is [SelfControlCheck.printed] verbatim. Three sentence
 * shapes -- "or investigate any mystery", "to avoid angry outbursts", "to
 * undertake any sustained effort" -- mean a paraphrase would get one of them
 * backwards, so the book's own cell is quoted and nothing is generated.
 *
 * ponytail: one stepper, the [FrightDialog] idiom. [Conditions] are
 * deliberately NOT layered on, because a Fright Check takes a GM modifier and
 * no ledger either, and two Will rolls behaving differently in one app is
 * worse than either choice on its own.
 */
@Composable
private fun SelfControlDialog(
    check: SelfControlCheck,
    will: Int,
    iq: Int,
    shock: Int = 0,
    onDismiss: () -> Unit,
) {
    // Keyed on the check: a modifier left over from another disadvantage
    // would silently move this one's target -- the stale-state family 6k, 6y
    // and 6af each found once.
    var modifier by remember(check) { mutableIntStateOf(0) }
    var outcome by remember(check) { mutableStateOf<SelfControlOutcome?>(null) }
    // The ONE place the number is computed. A caption that re-derived the
    // will/iq choice beside it is 6ai's defect exactly.
    // The GM's stepper and the shock are one number by the time the roll is
    // made, so the caption and the dice cannot disagree.
    val target = SelfControl.targetFor(check.spec, will, iq, modifier + shock)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = {
            Text(
                outcome?.let { if (it.held) "Held" else "Gave in" } ?: check.trait,
            )
        },
        text = {
            Column {
                // Quoted once, and turning red on a failure IS the
                // consequence -- printing it twice would read as two rules.
                Text(
                    check.printed,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (outcome?.held == false) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    check.spec.attribute?.let {
                        "keyed on $it${if (check.spec.offset > 0) "+" else ""}" +
                            "${if (check.spec.offset != 0) "${check.spec.offset}" else ""}" +
                            ", after any potion"
                    } ?: "a flat number the book prints — no attribute moves it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                val result = outcome
                if (result == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { modifier -= 1 }) { Text("−1") }
                        Text(
                            (if (modifier > 0) "+" else "") + modifier,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        TextButton(onClick = { modifier += 1 }) { Text("+1") }
                    }
                    Text("3d6 ≤ $target", style = MaterialTheme.typography.titleLarge)
                    // Replaced by the result, AND guarded inline: two taps can
                    // both reach the handler before the button leaves the
                    // tree, which is the fix 6p needed for the divination
                    // dialog. Nothing is charged here, but the second tap
                    // would silently discard a rolled outcome and pull an
                    // extra draw from the dice.
                    TextButton(
                        onClick = {
                            if (outcome != null) return@TextButton
                            outcome = SelfControl.attempt(check, will, iq, appDice, modifier + shock)
                        },
                    ) { Text("Roll") }
                } else {
                    Text(
                        "3d6: ${result.roll.total} vs ${result.target}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        },
    )
}

/**
 * The Concentrate maneuver's own roll (Ch. 5, p. 36).
 *
 * **The roll is conditional, so the button is withdrawn until it is asked
 * for.** "Roll Will-3 if injured or defending" -- a caster concentrating in
 * peace rolls nothing, and offering the roll anyway would tax every act of
 * focus in the book. [Concentration.attempt] refuses that call outright, so a
 * Roll button here would be one that throws (6t).
 *
 * One chip, because the book prints one sentence, one number and one effect
 * for both circumstances -- unlike the three defence refusals, which print
 * three sentences and where a trait cancels exactly one (6bg).
 */
@Composable
private fun ConcentrateDialog(will: Int, shock: Int = 0, onDismiss: () -> Unit) {
    var disturbed by remember { mutableStateOf(false) }
    // Cleared with the declaration, because an outcome belongs to the
    // circumstance it was rolled under -- the rule the Major Wound's HT roll
    // (6n) and the entity's answer (6z) both follow.
    var outcome by remember(disturbed) { mutableStateOf<ConcentrationOutcome?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(outcome?.let { if (it.held) "Held the focus" else "Lost the focus" } ?: "Concentrate") },
        text = {
            Column {
                Text(Concentration.printed(), style = MaterialTheme.typography.bodySmall)
                FilterChip(
                    selected = disturbed,
                    onClick = { disturbed = !disturbed },
                    label = { Text("Injured or defending") },
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                val result = outcome
                when {
                    !disturbed -> Text(
                        "No roll: the book asks for one only when the character " +
                            "is injured or defending.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    result == null -> {
                        Text(
                            "3d6 ≤ ${Concentration.targetFor(will, shock)} — Will " +
                                "$will${Concentration.PENALTY}" +
                                if (shock != 0) ", $shock shock" else "",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        // Guarded inline as well as by the tree: two taps can
                        // both reach the handler before it leaves (6p).
                        TextButton(
                            onClick = {
                                if (outcome != null) return@TextButton
                                outcome = Concentration.attempt(will, disturbed, appDice, shock)
                            },
                        ) { Text("Roll") }
                    }
                    else -> {
                        Text(
                            "3d6: ${result.roll.total} vs ${result.target}",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        // The book prints the roll and stops. Saying so beats
                        // inventing the only consequence in the rule (6aj).
                        if (!result.held) {
                            Text(
                                "The book prints no consequence for a lost focus — " +
                                    "what it costs is the GM's.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
    )
}

/** The pool maximums and Will as they stand after whatever has been drunk. */
private data class Vitals(
    val maxHp: Int,
    val maxFp: Int,
    val maxSpi: Int,
    val will: Int,
    val per: Int,
    val dodge: Int,
)

/**
 * HP, FP and SPI at the table (Ch. 5).
 *
 * SPI is shown only for a Beyonder: "Non-Beyonders (SPI 0) ... have no
 * spirituality to sacrifice" (p. 41), and [Pools.spi] refuses a maximum of
 * zero rather than reporting a mortal as Spiritually Exhausted.
 */
@Composable
private fun PoolTrackers(session: BeyonderSession, vitals: Vitals, maxCor: Int) {
    Column(Modifier.padding(vertical = 4.dp)) {
        PoolRow(Pools.hp(vitals.maxHp - session.hpLost, vitals.maxHp)) {
            session.spend(Pool.HP, it, vitals.maxHp, maxCor)
        }
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
 * Soul Debt is REPORTED, not applied: SPI below zero is "+1 CoR per 1 point
 * below 0" (p. 35), but CoR is permanent and this stepper is not -- stepping
 * back up would have to un-charge it. The GM adds it on the pathway screen.
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
private data class RollableSkill(val label: String, val base: Int, val key: SkillKey)

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
private fun disambiguated(key: SkillKey, visible: List<SkillKey>): String {
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
private fun AddedSkillRow(label: String, level: Int, provenance: String, onClick: () -> Unit) {
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
private fun ComparisonRow(
    row: SheetRow,
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
        Text(row.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(row.computed, style = MaterialTheme.typography.bodyMedium)
        if (!row.matches) {
            Text(
                " (sheet: ${row.published})",
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
 * Pick the circumstances, then roll.
 *
 * The roll used to fire on the tap that opened this dialog, which left nowhere
 * to state Appendix A's Common Modifiers before the dice were already cast.
 * Re-rolling as each chip is toggled would be worse: the GM would watch
 * results scroll past and keep whichever one arrived last.
 */
@Composable
private fun RollDialog(
    label: String,
    base: Int,
    sense: SpiritualSense? = null,
    burnKind: BurnKind? = null,
    // What the mystical push may spend. Not the maximum and not what the
    // tracker shows: SPI already spent is gone, and 6ah's lesson is that
    // passing a pool where a rule wants a different quantity is a defect no
    // test catches.
    spiAvailable: Int = 0,
    // A push the book allows on part of what this skill covers, which the
    // engine cannot test for itself. Shown, never applied.
    conditionalBurnNote: String? = null,
    // Chapter 8's equipment rows whose printed note names this skill in a
    // signed clause. Reference, never a modifier: this app records no
    // possession, the table prices two disguise kits that would stack, and
    // one of its penalties is aimed at whoever is looking FOR the wearer.
    // Shown for the same reason the conditional push above is shown.
    equipment: List<String> = emptyList(),
    // The character's own conditional social rows, offered only on a skill
    // whose category the book's cells name. The screen asks which hold and
    // hands the keys back; it never turns one into a modifier, because a
    // rules-authored list is the only thing that keeps one decision in one
    // place (6ag, 6ai, 6be, 6bp, 6bs, 6bv).
    socialClauses: List<TraitClause> = emptyList(),
    onBurn: (BurnKind, Int) -> Unit = { _, _ -> },
    onRoll: (List<com.pathways.model.Modifier>, Set<String>) -> SkillRollOutcome,
    onDismiss: () -> Unit,
) {
    var out by remember { mutableStateOf<SkillRollOutcome?>(null) }
    // Points already burned on THIS roll. The book caps the FP side at +5 and
    // caps the SPI side at nothing, so the ceiling comes from EffortBurn
    // rather than from a number chosen here.
    var burned by remember { mutableIntStateOf(0) }
    val chosen = remember { mutableStateListOf<CommonModifier>() }
    // Chapter 4's own circumstances, for the two skills that print them.
    val senseChosen = remember { mutableStateListOf<SenseModifier>() }
    // Which of this character's social circumstances the GM says hold. Keyed
    // on the clause, because Social Anxiety prints two that can both hold.
    val declaredSocial = remember { mutableStateListOf<String>() }
    // Appendix A rows this sense's own price supersedes. Recomputed rather
    // than stored, so a chip turned back off cannot leave a withheld row
    // hidden -- the stale-state defect the boon dialog was fixed for.
    val withheld = SpiritualSenses.displacedCommonModifiers(senseChosen.toList())
    // A GM's pick inside a printed range, keyed by situation. Rebuilt from the
    // current picks on every roll, so dropping a chip cannot leave its value
    // behind -- the stale-state defect the boon dialog was fixed for.
    val gmValue = remember { mutableStateMapOf<String, Int>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        // Both buttons always exist, rather than one slot whose label and
        // onClick swap from Roll to Close once the dice are cast. Roll simply
        // goes disabled, which makes the re-entrancy guard below visible
        // instead of implied -- a GM can see the roll has happened.
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        confirmButton = {
            TextButton(
                enabled = out == null,
                onClick = {
                    // Belt to the disabled state's braces: two taps in the same
                    // frame could otherwise roll twice and show the second, the
                    // guard the divination and ritual dialogs also needed.
                    if (out == null) {
                        out = onRoll(
                            CommonModifiers.modifiers(
                                chosen = chosen.toList(),
                                gmValue = gmValue.filterKeys { key ->
                                    chosen.any { it.situation == key }
                                },
                            ) +
                                // Passed the general picks as well, so the
                                // double-charge is refused in rules even
                                // though the chips above cannot produce it.
                                if (sense == null) emptyList() else SpiritualSenses.modifiers(
                                    sense = sense,
                                    chosen = senseChosen.toList(),
                                    common = chosen.toList(),
                                ),
                            declaredSocial.toSet(),
                        )
                    }
                },
            ) { Text("Roll") }
        },
        title = { Text(label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val shownOut = out
                if (shownOut == null) {
                    Text("Skill $base", style = MaterialTheme.typography.titleLarge)
                    if (sense != null) SenseRow(sense, senseChosen, chosen, gmValue)
                    if (socialClauses.isNotEmpty()) {
                        Text(
                            "Does any of this hold?",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        FlowRow {
                            socialClauses.forEach { clause ->
                                TextButton(onClick = {
                                    if (clause.key in declaredSocial) {
                                        declaredSocial.remove(clause.key)
                                    } else {
                                        declaredSocial.add(clause.key)
                                    }
                                }) {
                                    Text(
                                        "${clause.shown}: ${clause.printed}",
                                        color = if (clause.key in declaredSocial) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                    CircumstanceRow(chosen, gmValue, withheld)
                    return@Column
                }
                RollResult(shownOut, sense)
                // Ch. 2, p. 9: "You may use this after seeing the result."
                // That is the whole reason this sits BELOW the result rather
                // than beside the chips -- it is a decision made once the
                // dice are known, and the same dice are then re-read against
                // a higher target.
                // Withheld when the burn cannot move the result: a roll that
                // never happened, or one only a critical can win, where the
                // total decides and raising the target changes nothing.
                if (burnKind != null && EffortBurn.canPush(shownOut)) {
                    BurnRow(burnKind, burned, spiAvailable) { points ->
                        // The LIVE outcome, never the `shownOut` captured when
                        // this lambda was composed: two taps before a
                        // recomposition would both read the stale one, so the
                        // second push would overwrite the first while the pool
                        // paid for both. Bail rather than guess if it is gone.
                        val live = out ?: return@BurnRow
                        out = EffortBurn.applied(live, burnKind, points, burned)
                        onBurn(burnKind, points)
                        burned += points
                    }
                }
                // Occultism's push is scoped to "vs. Beyonder phenomena", and
                // a SkillKey carries no subject matter. Naming it beats both
                // alternatives: granting it would push rolls the book does
                // not, and withholding it in silence would hide a printed
                // rule -- the shape a wealth level's reaction modifier and a
                // called shot's multiplier already take.
                if (conditionalBurnNote != null && EffortBurn.canPush(shownOut)) {
                    Text(
                        conditionalBurnNote,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                // Every row the table prints for this skill, conditions and
                // all. Showing only the unconditional ones would teach the
                // player that a skill with no line here has no gear -- false
                // exactly in the conditional band, which is most of them.
                if (equipment.isNotEmpty()) {
                    Text(
                        "Gear that names this skill (not applied):",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    equipment.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
    )
}

/**
 * Appendix A's Common Modifiers. The untrained-default row is absent because
 * `CommonModifiers.offered()` withholds it: it replaces the roll with an
 * attribute default rather than modifying a skill, and this screen has no way
 * to offer that as a chip without lying about what it does.
 *
 * Chapter 5's darkness and cover rows are deliberately not here either — they
 * belong to the ranged dialog, and offering both would charge one dark room
 * twice.
 */
@Composable
private fun CircumstanceRow(
    chosen: MutableList<CommonModifier>,
    gmValue: MutableMap<String, Int>,
    withheld: Set<String> = emptySet(),
) {
    Text(
        "Circumstances (Appendix A)",
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 12.dp),
    )
    if (withheld.isNotEmpty()) {
        Text(
            "Chapter 4 prices being distracted or in combat itself, so " +
                "Appendix A's general rows for it do not also apply.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CommonModifiers.offered().filterNot { it.situation in withheld }.forEach { row ->
            val isOn = chosen.any { it.situation == row.situation }
            FilterChip(
                selected = isOn,
                onClick = {
                    if (isOn) {
                        chosen.removeAll { it.situation == row.situation }
                        gmValue.remove(row.situation)
                    } else {
                        chosen.add(row)
                    }
                },
                label = { Text("${row.situation.substringBefore(" (")} ${row.printed}") },
            )
        }
    }
    // The whole printed range, not just its ends. "Improvised / poor tools
    // -2 to -4" prints -3 as surely as it prints -2 and -4, and
    // `CommonModifiers.valueFor` has always accepted it -- the chips were the
    // only thing that could not say it. The gentle end stays the default.
    //
    // An open end ("-6 or worse", "+4 or more") names no ceiling, so it
    // offers its printed end and three steps beyond: the same reading the
    // open-ended ritual fuel cell takes (6q), because a stepper running to a
    // hundred is not a choice worth making at the table.
    chosen.filter { it.otherEnd != null || it.isOpenEnded }.forEach { row ->
        val printed = row.modifier
        val ends = when {
            printed == null -> emptyList()
            row.otherEnd != null -> {
                val lo = minOf(printed, row.otherEnd!!)
                val hi = maxOf(printed, row.otherEnd!!)
                (lo..hi).sortedBy { kotlin.math.abs(it - printed) }
            }
            // Away from zero, which is the only direction the book bounds.
            printed < 0 -> (0..OPEN_END_STEPS).map { printed - it }
            else -> (0..OPEN_END_STEPS).map { printed + it }
        }
        Text(
            "${row.situation}: ${row.printed}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ends.forEach { value ->
                FilterChip(
                    selected = (gmValue[row.situation] ?: row.modifier) == value,
                    onClick = { gmValue[row.situation] = value },
                    label = { Text(if (value > 0) "+$value" else "$value") },
                )
            }
        }
    }
}

/**
 * Chapter 4's own circumstances, for the two skills that print them.
 *
 * "+6 Seer" is offered rather than applied. Seer is the Fool pathway's
 * SEQUENCE 9 NAME -- a Fool at Sequence 8 is a Clown -- so granting it to
 * anyone on the Fool pathway would author a rule the book did not print.
 * Same call as the Sealed Artifact grade table (6af): show it, do not gate.
 */
@Composable
private fun SenseRow(
    sense: SpiritualSense,
    senseChosen: MutableList<SenseModifier>,
    chosen: MutableList<CommonModifier>,
    gmValue: MutableMap<String, Int>,
) {
    Text(
        "Circumstances (${sense.skill}, Ch. 4)",
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 12.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SpiritualSenses.offered(sense).forEach { row ->
            val isOn = senseChosen.any { it.situation == row.situation }
            FilterChip(
                selected = isOn,
                onClick = {
                    if (isOn) {
                        senseChosen.removeAll { it.situation == row.situation }
                    } else {
                        senseChosen.add(row)
                        // Drop any Appendix A pick this row now supersedes,
                        // with the value a GM chose inside it. Leaving one
                        // selected but hidden is how a withheld row gets
                        // charged anyway.
                        val displaced = SpiritualSenses
                            .displacedCommonModifiers(senseChosen.toList())
                        chosen.removeAll { it.situation in displaced }
                        gmValue.keys.retainAll { it !in displaced }
                    }
                },
                label = {
                    Text(
                        "${row.situation} " +
                            if (row.modifier >= 0) "+${row.modifier}" else "${row.modifier}",
                    )
                },
            )
        }
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

@Composable
private fun RollResult(out: SkillRollOutcome, sense: SpiritualSense? = null) {
    Column {
        val roll = out.roll
        if (roll == null) {
            Text("Forbidden — this roll cannot be attempted.")
        } else {
            Text(
                "3d6: ${roll.total} vs ${roll.target}",
                style = MaterialTheme.typography.titleLarge,
            )
            // effectiveOutcome, not roll.outcome: RequireCrit demotes an
            // ordinary success, and showing "Success" with a note underneath
            // would misreport the result.
            val shown = out.effectiveOutcome ?: roll.outcome
            Text(label(shown, out.degree), color = colorFor(shown))
            Text("margin ${roll.margin}", style = MaterialTheme.typography.bodySmall)
            // The skill's own table, when its chapter prints one. Read from
            // the EFFECTIVE outcome, so a roll demoted by requiresCritical
            // cannot come back carrying a success row -- the defect
            // SkillRollOutcome.degree avoids one object over. A failure lands
            // on no row, because chapter 4 prints none.
            if (sense != null) {
                val row = SpiritualSenses.rowFor(sense, shown, roll.margin)
                if (row != null) {
                    Text("${row.result} — ${row.effect}")
                } else {
                    Text(
                        "${sense.skill} prints no row for this result.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (out.target != out.baseTarget) {
            Text(
                "base ${out.baseTarget}, modified to ${out.target}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        out.applied.forEach { contribution ->
            Text(
                "${contribution.ids.joinToString { it.value }}: ${contribution.delta}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (out.requiresCritical) {
            Text(
                "Only a critical success counts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Appendix A grades a success into three bands and gives a critical success
 * its own row above them, so the degree only ever refines the SUCCESS line --
 * a critical is already the best result the table prints and re-labelling it
 * by margin would demote it.
 */
internal fun label(outcome: Outcome, degree: SuccessDegree? = null) = when (outcome) {
    Outcome.CRITICAL_SUCCESS -> "Critical success"
    Outcome.SUCCESS -> when (degree) {
        SuccessDegree.EXCELLENT -> "Excellent success"
        SuccessDegree.GOOD -> "Good success"
        else -> "Success"
    }
    Outcome.FAILURE -> "Failure"
    Outcome.CRITICAL_FAILURE -> "Critical failure"
}

@Composable
private fun colorFor(outcome: Outcome) = when (outcome) {
    Outcome.CRITICAL_FAILURE, Outcome.FAILURE -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}
