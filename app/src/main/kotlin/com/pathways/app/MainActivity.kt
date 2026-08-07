package com.pathways.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.ModEffect
import com.pathways.model.Outcome
import com.pathways.model.SkillKey
import com.pathways.rules.Conditions
import com.pathways.rules.Corruption
import com.pathways.rules.Derive
import com.pathways.rules.Divination
import com.pathways.rules.FrightCheck
import com.pathways.rules.FrightKind
import com.pathways.rules.FrightOutcome
import com.pathways.rules.content.FrightTrigger
import com.pathways.rules.PenaltyScope
import com.pathways.rules.Pool
import com.pathways.rules.PoolStatus
import com.pathways.rules.Pools
import com.pathways.rules.PotionDigestion
import com.pathways.rules.PremadeSheet
import com.pathways.rules.Premades
import com.pathways.rules.RandomSource
import com.pathways.rules.SheetComparison
import com.pathways.rules.SheetRow
import com.pathways.rules.SkillRoll
import com.pathways.rules.SkillRollOutcome
import com.pathways.rules.SuccessDegree
import com.pathways.rules.CommonModifiers
import com.pathways.rules.PremadeSkill
import com.pathways.rules.SpiritualSenses
import com.pathways.rules.content.CommonModifier
import com.pathways.rules.content.ContentLoader
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ponytail: loaded on the main thread. 23 small JSONs off the APK
        // classpath; move behind a ViewModel when the sheet screen needs state
        // that survives rotation, not before.
        val store = SessionStore(File(filesDir, "sessions.json"))
        setContent { MaterialTheme { App(Premades.all(), store) } }
    }
}

/**
 * ponytail: four destinations, one enum, no navigation library. Add one at the
 * first deep link or saved back stack.
 */
@Composable
fun App(sheets: List<PremadeSheet>, store: SessionStore) {
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
        null -> PremadeList(sheets) { selected = it }
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

@Composable
fun PremadeList(sheets: List<PremadeSheet>, onSelect: (PremadeSheet) -> Unit) {
    Scaffold { insets ->
        LazyColumn(Modifier.padding(insets).fillMaxSize()) {
            items(sheets) { sheet ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(sheet) }
                        .padding(16.dp),
                ) {
                    Text(sheet.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${sheet.skills.size} skills",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider()
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
    var rolling by remember(sheet) { mutableStateOf<PremadeSkill?>(null) }
    var frightening by remember(sheet) { mutableStateOf(false) }
    var casting by remember(sheet) { mutableStateOf(false) }
    var defending by remember(sheet) { mutableStateOf(false) }
    var attacking by remember(sheet) { mutableStateOf(false) }
    var damaging by remember(sheet) { mutableStateOf(false) }
    var divining by remember(sheet) { mutableStateOf(false) }
    var supplicating by remember(sheet) { mutableStateOf(false) }
    var handling by remember(sheet) { mutableStateOf(false) }
    var sensing by remember(sheet) { mutableStateOf(false) }
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
    val maxCor = remember(sheet, vitals.will, session.edits) {
        Corruption.maxFor(vitals.will, session.characterFor(sheet).traits)
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
    // Only a caster can cast. No premade prints Ritualistic Magic, so this is
    // null until someone buys it in the builder.
    val ritualLevel = skillLevelOf(RITUAL_SKILL, skillLevels)
    // Same gate, same reason: no premade prints Divination Arts either, so it
    // is bought in the builder before a question can be asked.
    val divinationLevel = skillLevelOf(Divination.SKILL, skillLevels)

    // What the trackers are costing a roll of [key]. [cor] is a parameter
    // because RitualResolution applies the corruption ladder itself, and
    // passing it here as well would charge a corrupted caster twice.
    fun conditionsFor(key: SkillKey, cor: Int = session.cor) = Conditions.forSkill(
        skill = key,
        hp = Pools.hp(vitals.maxHp - session.hpLost, vitals.maxHp),
        fp = Pools.fp(vitals.maxFp - session.fpLost, vitals.maxFp),
        spi = if (vitals.maxSpi > 0) Pools.spi(vitals.maxSpi - session.spiSpent, vitals.maxSpi) else null,
        cor = cor,
    )

    Scaffold { insets ->
        LazyColumn(Modifier.padding(insets).fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    // Four destinations no longer fit a phone's width; the
                    // fourth was clipped to a sliver on a 1080px screen.
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = onBack) { Text("Back to premades") }
                        TextButton(onClick = onPathways) {
                            Text(if (drunk == null) "Drink a potion" else "Pathway")
                        }
                        TextButton(onClick = onBuild) { Text("Spend points") }
                        TextButton(onClick = { frightening = true }) { Text("Fright") }
                        if (ritualLevel != null) {
                            TextButton(onClick = { casting = true }) { Text("Ritual") }
                        }
                        if (divinationLevel != null) {
                            TextButton(onClick = { divining = true }) { Text("Divine") }
                        }
                        // Not gated. Divination Arts is the DIVINER's skill;
                        // being divined needs no skill at all, and the book's
                        // detection roll falls back to Per, which a mortal
                        // has. Who suspects is the GM's call (Detection).
                        TextButton(onClick = { sensing = true }) { Text("Detect") }
                        if (ritualLevel != null) {
                            // Same gate as the ritual: Chapter 13 rolls the
                            // same skill, because a boon IS a ritual.
                            TextButton(onClick = { supplicating = true }) { Text("Boon") }
                        }
                        // Not gated on anything. A Sealed Artifact is found,
                        // not bought or awarded by Sequence: the grade table's
                        // only gate is church rank, which this app does not
                        // model. See SealedArtifacts.
                        TextButton(onClick = { handling = true }) { Text("Artifacts") }
                        TextButton(onClick = { attacking = true }) { Text("Attack") }
                        TextButton(onClick = { defending = true }) { Text("Defend") }
                        TextButton(onClick = { damaging = true }) { Text("Damage") }
                    }
                    Text(sheet.name, style = MaterialTheme.typography.headlineSmall)
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
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                HorizontalDivider()
            }

            item { PoolTrackers(session, vitals, maxCor) }

            items(rows.take(SECONDARY_ROWS)) { ComparisonRow(it) }

            // Row order past the secondaries is one per purchased skill, in
            // sheet order - pinned by SheetComparisonTest. That is what makes
            // this positional pairing safe.
            itemsIndexed(sheet.skills) { i, skill ->
                val row = rows.getOrNull(SECONDARY_ROWS + i)
                if (row != null) {
                    ComparisonRow(
                        row,
                        Modifier.clickable { rolling = skill },
                    )
                }
            }
        }
    }

    rolling?.let { skill ->
        RollDialog(
            label = skill.label,
            base = sheet.levelFor(skill),
            // Null for every skill but two. Chapter 4 prints a result table
            // for Spiritual Intuition and Spiritual Perception and for
            // nothing else, so the rest keep Appendix A's general ladder.
            sense = SpiritualSenses.forSkill(skill.key()),
            onRoll = { circumstances ->
                // The trackers reach the dice here: a reeling, fatigued or
                // corrupted character no longer rolls at full skill. The GM's
                // circumstances are a separate source and simply join the list.
                SkillRoll.of(
                    sheet,
                    skill,
                    appDice,
                    mods = conditionsFor(skill.key()) + circumstances,
                )
            },
            onDismiss = { rolling = null },
        )
    }
    if (frightening) {
        FrightDialog(vitals.will) { frightening = false }
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
            onDismiss = { damaging = false },
        )
    }
    if (defending) {
        DefenseDialog(
            dodge = vitals.dodge,
            skillLevels = skillLevels,
            weapons = session.characterFor(sheet).weapons,
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
            onSpendFp = { session.spend(Pool.FP, it, vitals.maxFp, maxCor) },
            onDismiss = { attacking = false },
        )
    }
    if (divining && divinationLevel != null) {
        DivinationDialog(
            skillLevel = divinationLevel,
            // Divination Arts is SPI/Hard, so Spiritual Attrition reaches it.
            // The CoR ladder does not: it is scoped to ritual rolls (p. 68).
            conditionPenalty = conditionsFor(
                key = SkillKey(
                    Divination.SKILL,
                    null,
                    ControllingAttribute.Spi,
                    Difficulty.HARD,
                ),
                cor = 0,
            ).sumOf { (it.effect as ModEffect.Flat).n },
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
            ).sumOf { (it.effect as ModEffect.Flat).n },
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
            ).sumOf { (it.effect as ModEffect.Flat).n },
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
private fun FrightDialog(will: Int, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(FrightKind.FRIGHT) }
    var outcome by remember { mutableStateOf<FrightOutcome?>(null) }
    var picked by remember { mutableStateOf<FrightTrigger?>(null) }
    var modifier by remember { mutableIntStateOf(0) }
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
                    Text("3d6 vs ${will + modifier}", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { outcome = FrightCheck.against(will, modifier, appDice) }) {
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
        TextButton(onClick = { onSpend(1) }) { Text("−1") }
        TextButton(onClick = { onSpend(-1) }) { Text("+1") }
    }
}

@Composable
private fun ComparisonRow(row: SheetRow, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
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
    onRoll: (List<com.pathways.model.Modifier>) -> SkillRollOutcome,
    onDismiss: () -> Unit,
) {
    var out by remember { mutableStateOf<SkillRollOutcome?>(null) }
    val chosen = remember { mutableStateListOf<CommonModifier>() }
    // Chapter 4's own circumstances, for the two skills that print them.
    val senseChosen = remember { mutableStateListOf<SenseModifier>() }
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
                    CircumstanceRow(chosen, gmValue, withheld)
                    return@Column
                }
                RollResult(shownOut, sense)
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
    // Both printed ends, for the four rows that print two. The gentle end is
    // the default; nothing here can offer a value the book does not print.
    chosen.filter { it.otherEnd != null || it.isOpenEnded }.forEach { row ->
        val ends = listOfNotNull(row.modifier, row.otherEnd)
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
private fun label(outcome: Outcome, degree: SuccessDegree? = null) = when (outcome) {
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
