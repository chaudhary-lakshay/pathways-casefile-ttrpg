package com.pathways.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.ModEffect
import com.pathways.model.SkillKey
import com.pathways.model.TraitKind
import com.pathways.rules.BurnKind
import com.pathways.rules.Conditions
import com.pathways.rules.Derive
import com.pathways.rules.Divination
import com.pathways.rules.EffortBurn
import com.pathways.rules.Equipment
import com.pathways.rules.Pool
import com.pathways.rules.Languages
import com.pathways.rules.Pools
import com.pathways.rules.SpiritVision
import com.pathways.rules.PremadeSheet
import com.pathways.rules.Premades
import com.pathways.rules.RandomSource
import com.pathways.rules.RitualMode
import com.pathways.rules.RollKind
import com.pathways.rules.SelfControl
import com.pathways.rules.SelfControlCheck
import com.pathways.rules.SheetComparison
import com.pathways.rules.Shock
import com.pathways.rules.SkillDefaults
import com.pathways.rules.SkillRoll
import com.pathways.rules.SpiritualSenses
import com.pathways.rules.TraitEffects
import com.pathways.rules.TraitGrants
import java.io.File
import kotlin.random.Random

/** The app's real dice. Tests inject [com.pathways.rules.SeededRandom] instead. */
internal val appDice = RandomSource { sides -> Random.nextInt(1, sides + 1) }

/** The destinations reachable from a selected premade. */
private enum class Screen { SHEET, PATHWAY, BUILDER }

/** [SheetComparison] emits the seven secondaries before any skill row. */
private const val SECONDARY_ROWS = 7

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
    // One walk down a pathway per selected premade, restored from disk and
    // written back on every change.
    val session = remember(selected) {
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
    var looking by remember(sheet) { mutableStateOf(false) }
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
    // layer changed. Hoisted because two readers need them: a skill roll and
    // the active defences.
    //
    // Plus every row the potions drunk granted (6fk), from the walk the stat
    // grants come from: a potion's Combat Reflexes is held as surely as a
    // bought one. Every reader on this screen takes the merged list; the
    // budget never sees it, because it prices `characterFor` itself.
    val grantedTraits = remember(drunk) { TraitGrants.grantedTraits(drunk) }
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
    // `TraitEffects.reported` reads the advantage catalogue only -- so the
    // self-control rolls the book prints had no way to be made.
    val selfControl = remember(traits) {
        SelfControl.checksFor(traits).associateBy { it.trait }
    }
    // Derived once for both bare Will rolls. Two `remember`s over one input
    // is two chances to key them differently, which is the drift this file
    // records against every re-derived value (6ai).
    val willClauses = remember(traits) { TraitEffects.willClauses(traits) }
    // The Will clauses plus the ones naming concentration itself (6fa).
    val concentrationClauses = remember(traits) { TraitEffects.concentrationClauses(traits) }
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
        TraitEffects.held(traits, TraitKind.DISADVANTAGE) +
            TraitEffects.held(traits, TraitKind.QUIRK)
    }
    // The advantages, which had no list at all until 6es: the sheet printed
    // `TraitEffects.reported` and the drawbacks, so an advantage with nothing
    // to report had its cell on no screen -- the gallery card and the builder
    // show a name. 6am's finding, one list over, and 24 of the premades' 50
    // advantage rows.
    val advantages = remember(traits) { TraitEffects.held(traits, TraitKind.ADVANTAGE) }
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
    // Every printed skill this character does NOT have and may attempt at its
    // default (ch. 2 p. 10), off the same played character as the levels
    // above. Until 6et no screen could roll one.
    val untrained = remember(sheet, drunk, session.edits) {
        SkillDefaults.offeredTo(session.characterFor(sheet).copy(beyonder = drunk))
    }
    var pickingUntrained by remember(sheet) { mutableStateOf(false) }
    // A Twilight Giant's weapon line, read once for both combat dialogs.
    val mastery = drunk?.let { SkillDefaults.masteryFor(it.pathway, it.sequence) }
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
        hp = hpNow,
        fp = fpNow,
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
    // Reeling and fatigue, for those same four rolls. p. 40's "Reeling: -1 to
    // all rolls" and p. 42's "-1 to all rolls" are the phrase Shock already
    // reads at its word, so this is the same scope decision -- made ONCE here,
    // because an adapter left unwired looks like an oversight rather than a
    // decision (Codex, 6bo). Named separately from the shock beside it: a
    // breakdown that has to sum must say which state moved the target.
    val hurtOnAttribute = Conditions.penaltyFor(RollKind.ATTRIBUTE, hpNow, fpNow)
    // What each self-control roll stands at, for the Traits row that prints
    // it and the dialog that opens on it ([standingTargets]).
    val standing = standingTargets(
        selfControl.values,
        vitals.will,
        attributes.iq,
        shockOnAttribute + hurtOnAttribute,
    )
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
                        if (spiritVisionForm != null) {
                            // Labelled with the pathway's OWN name for it: a
                            // Hermit's block writes "Eyes of Mystery Prying"
                            // and never "Spirit Vision", so a fixed label
                            // would name something their sheet does not.
                            HubAction(
                                spiritVisionForm,
                                Icons.Filled.RemoveRedEye,
                                ActionKind.BEYONDER,
                            ) { looking = true }
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
                item {
                    PoolTrackers(
                        session = session,
                        vitals = vitals,
                        maxCor = maxCor,
                        hp = hpNow,
                        ht = attributes.ht,
                        traits = traits,
                        // The same number every other bare attribute roll
                        // takes. A character at 0 HP has been reeling since
                        // 1/3 HP, so this is not the state the roll creates.
                        poolPenalty = hurtOnAttribute,
                    )
                }
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

            // Every advantage held, with the book's cell and, under it, what
            // of that cell lands on nothing this app rolls. Shown with the
            // reason rather than silently dropped: a GM who can read "no shock
            // penalty to ignore" applies it themselves, one who sees nothing
            // assumes it was counted. The call the artifact grade table (6af)
            // already needed.
            items(if (tab == SheetTab.TRAITS) advantages else emptyList()) { row ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        // A granted row costs nothing and was not bought (6fk);
                        // a bought level a potion added to says both (6fm).
                        when {
                            row.trait in grantedTraits -> "${row.shown} — from the potion"
                            else -> "${row.shown} — ${row.trait.cost} pts" +
                                TraitGrants.grantedLevelsIn(row.trait, bought)
                                    ?.let { ", +$it from the potion" }.orEmpty()
                        } + superseded(row.trait, traits),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    HeldCell(row)
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
            items(if (tab == SheetTab.TRAITS) drawbacks else emptyList()) { row ->
                val trait = row.trait
                // The sheet's own name minus the effect cell shown below it,
                // where the two were printed as one string. Keyed the same way
                // `SelfControlCheck.trait` is, so the row and its roll cannot
                // disagree about what the trait is called.
                val shown = row.shown
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
                        (if (trait in grantedTraits) "$shown — from the potion" else "$shown — −${trait.cost} pts") +
                            superseded(trait, traits) +
                            (check?.let { " — 3d6 ≤ ${standing.getValue(it.trait)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (check != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    HeldCell(row)
                }
            }

            // The derived secondaries (HP, Will, Per, Basic Speed...) sit with
            // the pools rather than the skills: they are what the character IS,
            // so each prints the number PLAYED, and the disagreement with the
            // published sheet is printed beside it ([PlayedLine]).
            items(
                if (tab == SheetTab.VITALS) secondaryLines else emptyList(),
            ) { ComparisonRow(it) }

            // Row order past the secondaries is one per purchased skill, in
            // sheet order - pinned by SheetComparisonTest. That is what makes
            // this positional pairing safe.
            itemsIndexed(
                if (tab == SheetTab.SKILLS) sheet.skills else emptyList(),
            ) { i, skill ->
                val row = rows.getOrNull(SECONDARY_ROWS + i)
                if (row != null) {
                    // ONE level, printed and rolled: the character in front
                    // of the player. A Paragon Seq 9 potion takes this sheet's
                    // Research from 13 to 17, and the printed figure is a
                    // character who no longer exists.
                    //
                    // Until 6fz the row SHOWED the unedited sheet's level, on
                    // 6e's reasoning that the comparison is the engine
                    // against the book and a potion is neither. That is true
                    // of the diff, which is still the unedited sheet's and
                    // still the only thing in red. It was never a reason to
                    // print a level the Roll beside it does not roll: a Docks
                    // Bouncer on Twilight Giant 9 read Brawling 12 above a
                    // potion's Broadsword 14, and rolled Brawling at 16.
                    val level = liveLevelOf(skill.key(), skillLevels, sheet.levelFor(skill))
                    val roll = { rolling = RollableSkill(skill.label, level, skill.key()) }
                    ComparisonRow(
                        playedLine(row, level.toString()),
                        Modifier.clickable(onClick = roll),
                        onRoll = roll,
                    )
                }
            }

            // Every row on the screen, so a label can be checked against the
            // others before it is shown. Only the rows BELOW the comparison
            // table are ever suffixed: the published rows render the sheet's
            // own label, and rewriting it would edit the diff that is the
            // engine against the book (6e).
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

            // "You can attempt most skills even without training" (p. 10).
            // Last, because it is the exception: the rows above are what the
            // character can do, and this is what anyone can try.
            if (tab == SheetTab.SKILLS) {
                item {
                    TextButton(
                        onClick = { pickingUntrained = true },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) { Text("Roll a skill this sheet does not have") }
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

    if (pickingUntrained) {
        UntrainedDialog(
            rows = untrained,
            weaponNote = drunk?.let { SkillDefaults.weaponNote(it.pathway, it.sequence) },
            onPick = { row ->
                pickingUntrained = false
                // The source rides in the label, so the roll says it is a
                // default and which one: "Stealth (untrained, DX-5)".
                rolling = RollableSkill(
                    "${row.key.label()} (untrained, ${row.source})",
                    row.level,
                    row.key,
                )
            },
            onDismiss = { pickingUntrained = false },
        )
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
            // or the one skill a row names, on a character carrying a row
            // that names a circumstance. One rules list, which `modifiers`
            // also reads, so the chips cannot disagree with the dice (6ff).
            declarableClauses = TraitEffects.skillRollClauses(traits, skill.key),
            targetFor = { circumstances, declaredClauses ->
                // The trackers reach the dice here: a reeling, fatigued or
                // corrupted character no longer rolls at full skill. The GM's
                // circumstances are a separate source and simply join the list.
                SkillRoll.targetFor(
                    label = skill.label,
                    base = skill.base,
                    key = skill.key,
                    // Three sources now: what the trackers cost (Conditions),
                    // what the character IS (TraitEffects -- Alertness on a
                    // Per roll, Fit on an HT one), and what the GM says the
                    // situation is.
                    mods = conditionsFor(skill.key) +
                        TraitEffects.modifiers(traits, skill.key, declaredClauses) +
                        circumstances,
                )
            },
            roll = { SkillRoll.rollAgainst(it, appDice) },
            onBurn = burn,
            onDismiss = { rolling = null },
        )
    }
    controlling?.let { check ->
        // Null only if the trait left the list under an open dialog, which
        // no path does today: then there is no roll to show.
        standing[check.trait]?.let { target ->
            SelfControlDialog(check = check, standing = target, clauses = willClauses) { controlling = null }
        }
    }

    if (frightening) {
        // The traits, not a number: this dialog was the one roll-building
        // boundary that discarded them, so five catalogue rows that modify a
        // Fright Check could not reach the roll they name (Codex, on the
        // design). [TraitEffects] decides which apply and by how much; the
        // screen only asks which circumstances hold.
        FrightDialog(
            will = vitals.will,
            // A Fright Check IS a bare Will roll, so the rows that say
            // "Will rolls" reach it as well as the ones that name the
            // Fright Check itself. Concatenated rather than merged:
            // `TraitClause.key` is "shown / printed" and the two lists
            // share no row, so no declaration can move two clauses.
            iq = attributes.iq,
            // Row 12's fright cell counts its duration down from HT while
            // rolling Will, so the screen needs an attribute no roll here
            // takes. [FrightCheck.durationLine] is what decides which.
            //
            // From `attributes`, which is `effectiveAttributes` -- the levels
            // AFTER any potion, the same source the Major Wound's HT roll
            // takes. `will` above comes off `vitals` and is post-potion too;
            // the two read from different objects because Will is a SECONDARY
            // and HT is bought.
            ht = attributes.ht,
            clauses = remember(traits) {
                TraitEffects.frightClauses(traits) + TraitEffects.willClauses(traits)
            },
            // The recovery roll is a bare Will roll a second later, so the
            // rows that name one reach it and the ones scoped to the Fright
            // Check do not — that check is over by then.
            willOnlyClauses = willClauses,
            stunRecoveryBonus = TraitEffects.stunRecoveryBonus(traits),
            shock = shockOnAttribute,
            hurt = hurtOnAttribute,
        ) { frightening = false }
    }
    if (focusing) {
        ConcentrateDialog(vitals.will, concentrationClauses, shockOnAttribute, hurtOnAttribute) {
            focusing = false
        }
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
            stunRecoveryHt = TraitEffects.stunRecoveryHt(traits),
            // The kind is named at the call site because the two states answer
            // differently for it: shock refuses this roll (the roll creates
            // it) and the pools do not (the character carried them in).
            hurt = Conditions.penaltyFor(RollKind.MAJOR_WOUND_HT, hpNow, fpNow),
            // The recovery roll's own term, and only its own: Shock
            // refuses the Major Wound roll (that roll makes the shock)
            // and reaches every other kind, so a character already
            // shocked from an earlier wound recovers at -4.
            carriedShock = shockOnAttribute,
            onDismiss = { damaging = false },
        )
    }
    if (defending) {
        // A plain live expression, deliberately NOT remembered: `fpLost` is
        // observable state and `vitals` is keyed on the sheet, the walk and the
        // edits, so a remembered halving would freeze at whatever the pool held
        // when one of THOSE last changed. The same reason `spiAvailable` below
        // is written out rather than cached.
        val fatigue = fpNow
        DefenseDialog(
            // p. 42: "At 1/3 FP or below ... Move and Dodge halved". The sheet
            // screen has printed that sentence since 6b; until now the dialog
            // one tap away offered the whole Dodge. Halved here, at the one
            // reader of the derived score -- the published-vs-computed row in
            // SheetComparison is an audit of the DERIVATION and stays whole.
            dodge = Pools.halvedForFatigue(vitals.dodge, fatigue),
            fatigued = Pools.isFatigued(fatigue),
            // p. 40 and p. 42's "-1 to all rolls", added to every row. The
            // halving above is the fatigue row's OTHER clause and lands on the
            // Dodge score first; this lands on top, where the stun's -4 and a
            // shield's DB already do.
            poolPenalty = Conditions.penaltyFor(RollKind.ACTIVE_DEFENSE, hpNow, fatigue),
            skillLevels = skillLevels,
            // p. 37's Parry (Unarmed) formula prints DX as an operand, and the
            // Acrobatic Dodge is rolled at the default this sheet composed.
            dx = attributes.dx,
            untrained = untrained,
            // What the player says they are CARRYING, resolved against
            // chapter 8. `Character.weapons` is the published sheet's own
            // weapon rows and no sheet prints one, so reading it here showed
            // an armed parry to nobody (see SavedEdits.weapons).
            weapons = session.edits.carriedWeapons(),
            // The same line the Attack dialog lifts a weapon with, so a
            // master's weapon parries at DX on both screens (6ey).
            mastery = mastery,
            dice = appDice,
            // What the trackers cost, the first term of the sheet's own skill
            // rolls. An Acrobatic Dodge is a skill roll like any other, and
            // rolling it bare would have a reeling character roll Acrobatics at
            // two different targets depending on which screen asked (a design
            // review's finding). The dialog adds the trait term itself, from
            // `traits` below -- see acrobaticTarget.
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
            traits = traits,
            // The untrained skills this dialog can show an attack for, off
            // the same list the Skills tab offers -- one source, so the two
            // screens agree on a default wherever no weapon line applies.
            untrained = SkillDefaults.attacks(untrained),
            mastery = mastery,
            toolNote = drunk?.let { SkillDefaults.toolNote(it.pathway, it.sequence) },
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
            hurt = hurtOnAttribute,
            // The SPI branch's mirror of alertness below, and equally unable
            // to be reached by SkillKey scoping.
            spiTrait = spiTrait,
            // Ch. 3's Alertness. This is a Per roll made outside SkillKey, so
            // TraitEffects.modifiers cannot reach it; passed explicitly rather
            // than left a silent gap (found in review).
            alertness = TraitEffects.alertnessBonus(traits),
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
    if (looking && spiritVisionForm != null) {
        SpiritVisionDialog(
            form = spiritVisionForm,
            // The ATTRIBUTE, not what is left in the pool -- the reading a
            // wounded Detection roll already needed. Per is the roll; SPI is
            // only what it costs.
            per = vitals.per,
            // "+1 per level to all Per rolls": this is one, as Detection is (6fe).
            alertness = TraitEffects.alertnessBonus(traits),
            // A sight by its own name: the GM says whether the eye's acuity
            // reaches it (6fh).
            visionClause = TraitEffects.visionClause(traits),
            spiAvailable = vitals.maxSpi - session.spiSpent,
            shock = shockOnAttribute,
            hurt = hurtOnAttribute,
            dice = appDice,
            // Through the tracker, so activating actually costs the point.
            onSpendSpi = { session.spend(Pool.SPI, it, vitals.maxSpi, maxCor) },
            onDismiss = { looking = false },
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
            // Through the one reading of what a save owns, so the note
            // cannot name a language the budget does not bill.
            boughtLanguages = remember(sheet, session.edits) {
                session.edits.ownedLanguages(sheet.toCharacter().traits)
                    .filter { it.type == Languages.MYSTICAL }
                    .map { it.label }
            },
            grantedLanguages = remember(drunk) { Languages.grantedBy(drunk).map { it.label } },
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
