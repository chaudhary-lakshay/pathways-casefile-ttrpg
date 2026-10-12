package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.rules.ClauseBonus
import com.pathways.rules.BurnKind
import com.pathways.rules.Pools
import com.pathways.rules.Corruption
import com.pathways.rules.EntityAnswer
import com.pathways.rules.Incantations
import com.pathways.rules.Invocation
import com.pathways.rules.Languages
import com.pathways.rules.PowerSourceDeclaration
import com.pathways.rules.PowerSources
import com.pathways.rules.RandomSource
import com.pathways.rules.RitualMode
import com.pathways.rules.RitualOutcome
import com.pathways.rules.RitualPacing
import com.pathways.rules.RitualResolution
import com.pathways.rules.content.LanguageProficiency
import com.pathways.rules.content.RitualPreparation
import com.pathways.rules.content.SacrificeTier
import com.pathways.rules.content.TargetLink

/** The skill every ritual is rolled against (Ch. 7). */
const val RITUAL_SKILL = "Ritualistic Magic"


/**
 * Cast a ritual (Ch. 7, p. 67-68).
 *
 * The caster's skill level is the character's own, so this dialog is only
 * reachable when they have the skill -- no premade prints it, which is why
 * buying it in the builder had to come first.
 *
 * Injury, fatigue and spiritual attrition arrive as [conditionPenalty] --
 * the same [com.pathways.rules.Conditions] the sheet's skill rolls use, minus
 * the corruption penalty, which [RitualResolution] applies itself.
 *
 * CoR is charged automatically on the three critical rows that print a cost
 * ("Corruption. Gain 1 CoR."), read by the extractor rather than scraped from
 * the text here. The rest of a critical result stays the GM's: an entity
 * noticing you is not a number.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RitualDialog(
    // Always this caster's OWN Ritualistic Magic. There is deliberately no
    // sourceLine here, unlike the divination dialog: ch. 7 II p. 61 says the
    // skill "has no default and cannot be substituted", so there is no other
    // number this could have come from and a nullable parameter would imply
    // there were.
    skillLevel: Int,
    conditionPenalty: Int,
    /** The Major Wound's shock, named separately so the breakdown reconciles. */
    shock: Int = 0,
    /** The held rows' declared "-N to all rolls" (6gs), named for the same reason. */
    states: ClauseBonus = ClauseBonus(0, null),
    cor: Int,
    spiAvailable: Int,
    /**
     * The Section II supporting skills this character actually owns.
     *
     * Not the book's five: those are skills the CASTER brings, and this app
     * knows the sheet, so offering a skill the character has never bought
     * would let a GM claim an Occultism that is not there. Computed at the
     * call site against [RitualMode.supportSkills].
     */
    ownedSupportSkills: List<String>,
    /**
     * The mystical languages this character BOUGHT, each as "Hermes, Native,
     * written only". Printed beside the picker and never fed to it: owning a
     * language does not say which one this ritual is cast in, and a trait
     * (Official Beyonder, Dead Language Fluency) can grant one this list
     * cannot see.
     */
    boughtLanguages: List<String>,
    /** The languages a potion drunk granted, the same way (6fo). */
    grantedLanguages: List<String>,
    dice: RandomSource,
    onCorGained: (Int) -> Unit,
    onSpendSpi: (Int) -> Unit,
    onSpendHp: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val weights = remember { RitualResolution.weights() }
    var weight by remember { mutableStateOf(weights.first()) }
    // Full was the only mode the app had, and it applied Phase I's table to
    // every ritual -- including the routine ones the book resolves with its
    // own, gentler table. Kept as the default so nothing silently moves.
    var mode by remember { mutableStateOf(RitualMode.FULL) }
    val preparations = mode.preparations()
    // Adequate (+0) is the default, not Excellent: a ritual nobody described
    // is an ordinary one, and starting at +3 would hand out the book's best
    // preparation for free.
    //
    // Keyed on the MODE: the two tables are different objects, so a row picked
    // in one is not a row in the other, and letting it survive the switch
    // would apply Phase I's +3 inside a quick ritual -- exactly the stale
    // state the ranged dialog's "Another turn" reset once left behind. Both
    // tables print an Adequate +0, so the default itself is stable.
    var preparation by remember(mode) { mutableStateOf(preparations.first { it.modifier == 0 }) }
    // Reset with the mode too, and for a harder reason: modifierTotal REFUSES
    // a link in full mode, so a link left over from a quick ritual would throw
    // rather than merely mislead.
    var link by remember(mode) { mutableStateOf<TargetLink?>(null) }
    // The GM's pick inside a cell that prints more than a number. Null until
    // a chip is tapped, so the engine stays the only authority on the default.
    //
    // Held WITH its row and read back through pickInside, which does two
    // jobs. It is what keeps Good's +2 from reaching `targetFor` beside
    // Excellent -- that throws while this dialog DRAWS, and nothing else
    // resets the pair now that it is not keyed on the row. And it brings a
    // pick back, shown selected, when the GM returns to its row.
    var heldPreparationPick by remember { mutableStateOf<Pair<RitualPreparation, Int>?>(null) }
    var heldLinkPick by remember { mutableStateOf<Pair<TargetLink, Int>?>(null) }
    val preparationPick = pickInside(preparation, heldPreparationPick)
    val linkPick = pickInside(link, heldLinkPick)
    // Phase II's Flourish Bonus, and the mirror of the link in every respect
    // including this one: keyed on the mode because modifierTotal REFUSES it
    // in quick mode, so an award left over from a full ritual would throw
    // rather than merely mislead. False by default because the book has the GM
    // award it -- a bonus that started ticked would be one nobody granted.
    var flourish by remember(mode) { mutableStateOf(false) }
    // Section IV.A's axis, and the one the fuel comes off. NOT the weight:
    // the weight gives the base modifier above, this gives the cost, and the
    // book's own worked rituals charge outside the weight column three times
    // in six. See RitualComplexity.
    var complexity by remember { mutableStateOf(PowerSources.complexities().first()) }
    // Reset with the complexity, or a Major ritual's 11 SPI would follow the
    // caster onto a Light one that costs 2.
    var spiChoice by remember(complexity) { mutableIntStateOf(complexity.spi) }
    // Reset with the FUEL, and for the harder reason the quick mode's link
    // had: spiOwed REFUSES HP that covers more than the ritual costs, so a
    // sacrifice left over from an 11 SPI ritual would throw on a 2 SPI one
    // rather than merely mislead.
    // Keyed on the complexity TOO, not on the fuel's value alone: two rungs
    // can coincide on a base cost, and the stated intent is that a sacrifice
    // does not outlive the ritual it was offered for.
    var hpSacrificed by remember(complexity, spiChoice) { mutableIntStateOf(0) }
    // p. 63 and p. 66: "you may spend additional SPI (1 SPI = +1, no cap)
    // after seeing your result, separate from the ritual's fuel cost."
    // Accumulated rather than charged per tap, so the balance the chips are
    // offered against cannot go stale between two of them.
    var burned by remember { mutableIntStateOf(0) }
    var settled by remember { mutableStateOf(false) }
    // Section II's supporting skills: +1 each, capped at +3 by a sentence
    // rather than by the table (6y). Extracted, implemented and tested in
    // that slice and reachable from no screen until now -- `targetFor` has
    // taken a `support` list since then and this dialog never passed one, so
    // the bonus was unreachable. The same shape as 6ai's `freeRoll`.
    // NOT keyed on the mode: the book prints these above the Quick/Full
    // split and scopes them to neither, which is the opposite call to the
    // target link and made for the same reason -- scope follows the text.
    var support by remember { mutableStateOf(emptyList<String>()) }
    var pacing by remember { mutableStateOf(RitualPacing.NORMAL) }
    var assistants by remember { mutableIntStateOf(0) }
    var sacrifice by remember { mutableStateOf<SacrificeTier?>(null) }
    // Reset with the tier for the same reason: sacrificeBonus refuses sentient
    // beings offered with no sacrifice declared.
    var sentientBeings by remember(sacrifice) { mutableIntStateOf(0) }
    var catalyst by remember { mutableIntStateOf(0) }
    var suspended by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<RitualOutcome?>(null) }
    // Null by default -- "not stated" -- so a ritual described without a
    // language rolls exactly where it did before Ch. 3's table was read. The
    // builder sells a language now, and this is still the GM's declaration:
    // which language a ritual is cast in is a fact about the ritual.
    var language by remember { mutableStateOf<LanguageProficiency?>(null) }
    val canSpeak = language?.let { Languages.canPerformRituals(it) } != false
    // The entity is asked once, before the ritual roll: "The GM rolls secretly
    // on the Entity Response Table first, then applies the entity's modifier".
    // Null means the caster is praying to themselves, which is the safest
    // power source and the one that needs no table.
    var invoked by remember { mutableStateOf(false) }
    var goodStanding by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf<EntityAnswer?>(null) }
    // Cleared whenever the declaration changes: an answer belongs to the
    // entity it was asked of, the same way a Major Wound's HT roll belongs to
    // the hit it was rolled for.
    LaunchedEffect(invoked, goodStanding) { answer = null }
    // Injury and fatigue reach a ritual like any other roll; the CoR penalty
    // is NOT included in [conditionPenalty] because RitualResolution applies
    // it itself, and counting it twice would penalise a corrupted caster
    // once for the ladder and once again for the same ladder.
    val effectiveSkill = skillLevel + conditionPenalty + shock + states.total
    // Not computed at all when the book forbids the attempt: Broken prints
    // "Cannot perform rituals in the language", and targetFor refuses it
    // rather than inventing a penalty to roll at.
    // An entity that actively rejects the ritual fails it outright, so there
    // is no target to show -- the same shape as a language the caster cannot
    // speak. An entity not yet asked contributes nothing.
    val rejected = answer?.failsRitual == true
    val declaration = PowerSourceDeclaration(
        pacing = pacing,
        assistants = assistants,
        sacrifice = sacrifice,
        sentientBeings = sentientBeings,
        catalyst = catalyst,
        suspended = suspended,
    )
    val target = if (canSpeak && !rejected) {
        RitualResolution.targetFor(
            effectiveSkill + (answer?.ritualModifier ?: 0),
            weight, cor, preparation, language, mode, link, flourish, support,
            complexity = complexity, powerSources = declaration,
            preparationValue = preparationPick, linkValue = linkPick,
        )
    } else {
        null
    }
    val cost = PowerSources.spiFor(complexity, spiChoice)
    // B: HP is fuel and nothing else. It never reaches `declaration`, because
    // the book says in its own sentence that it "does not grant a bonus to
    // the roll" -- and a fuel rule printed one line above a list of roll
    // modifiers is exactly the sentence that gets turned into one.
    val declaredSpi = PowerSources.spiOwed(cost, hpSacrificed)
    // F: an entity that does not answer prices the attempt itself -- "treat as
    // 2 SPI personal" -- and the app charged the declared cost on every row,
    // so an unanswered Heavy invocation took 8+ where the book prints 2. The
    // decision lives in [Invocation]; re-deriving it here is how this
    // breakdown stopped reconciling in 6ag and 6ai.
    val residual = answer?.response?.let(Invocation::drawsOnResidualPower) == true
    val spiOwed = answer?.takeIf { !it.failsRitual }
        ?.let { Invocation.fuelFor(it.response, declaredSpi) }
        ?: declaredSpi
    // The row says the ritual draws ONLY on residual power, so a declared HP
    // sacrifice is displaced with the rest of the fuel rather than charged
    // beside a cost it no longer covers.
    val hpOwed = if (residual) 0 else hpSacrificed

    // The push window closes when the dialog does, and that is the ONE place
    // the pushed SPI and the outcome's consequences are charged. Both are read
    // off the FINAL outcome, because a push can take a ritual off the Critical
    // Failure Table and the CoR that row charges must go with it -- charging
    // at Roll, as this screen used to, left corruption paid for a consequence
    // the caster spent SPI to avoid.
    val settle = {
        if (!settled) {
            settled = true
            if (burned > 0) onSpendSpi(burned)
            outcome?.critical?.corGained?.let(onCorGained)
        }
    }
    // Close is the usual way out, but not the only one: a recreated activity
    // destroys this composable without a dismiss request, and `remember` goes
    // with it. Settling on disposal is what makes the deferral safe --
    // without it, a rotated ritual lost the CoR it owed, which the
    // pre-deferral version charged at the roll and never lost. A rotation no
    // longer recreates the activity (the manifest, 6fv); a dark-mode switch
    // still does, so this stays.
    DisposableEffect(Unit) { onDispose { settle() } }
    val finish = {
        settle()
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = finish,
        confirmButton = { TextButton(onClick = finish) { Text("Close") } },
        title = {
            Column {
                Text("Cast a ritual")
                Text(
                    "$RITUAL_SKILL $skillLevel",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        text = {
            val result = outcome
            if (result != null) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    RitualResultCard(result)
                    if (RitualResolution.canPush(result)) {
                        BurnRow(
                            kind = BurnKind.MYSTICAL,
                            burned = burned,
                            spiAvailable = spiAvailable - burned,
                            note = "Separate from the ritual's fuel, which is " +
                                "already paid. Charged when this closes.",
                        ) { points ->
                            // The LIVE outcome, not the one this row was drawn
                            // against: two taps land before a recomposition,
                            // and pushing the captured value would throw the
                            // first away while charging for it.
                            val live = outcome ?: return@BurnRow
                            if (!Pools.canAffordSpi(burned + points, spiAvailable)) {
                                return@BurnRow
                            }
                            burned += points
                            outcome = RitualResolution.pushed(live, points)
                        }
                    }
                }
                return@AlertDialog
            }
            // The choices scroll and the target with Cast is pinned beneath
            // them: with every declaration open the chips run three screens,
            // and Cast sat below all of them.
            Column {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    // The mode comes first, because it decides which
                    // preparation table the chips below are drawn from.
                    SectionLabel("Mode")
                    FlowRow {
                        RitualMode.entries.forEach { option ->
                            FilterChip(
                                selected = option == mode,
                                onClick = { mode = option },
                                label = { Text(if (option == RitualMode.QUICK) "Quick" else "Full") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    Text(
                        if (mode == RitualMode.QUICK) {
                            "Routine work — four steps. Preparation reaches +2 " +
                                "and the target link is counted separately."
                        } else {
                            "Consequential magic — three phases. Preparation " +
                                "reaches +3 and already includes the link."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SectionLabel("Weight")
                    Row {
                        weights.forEach { option ->
                            FilterChip(
                                selected = option.weight == weight.weight,
                                onClick = { weight = option },
                                label = { Text(option.weight) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    Text(weight.categories, style = MaterialTheme.typography.bodySmall)
                    RitualPowerSourceChips(
                        complexity = complexity,
                        spiChoice = spiChoice,
                        cost = cost,
                        hpSacrificed = hpSacrificed,
                        pacing = pacing,
                        assistants = assistants,
                        sacrifice = sacrifice,
                        sentientBeings = sentientBeings,
                        catalyst = catalyst,
                        suspended = suspended,
                        onComplexity = { complexity = it },
                        onSpiChoice = { spiChoice = it },
                        onHpSacrificed = { hpSacrificed = it },
                        onPacing = { pacing = it },
                        onAssistants = { assistants = it },
                        onSacrifice = { sacrifice = it },
                        onSentientBeings = { sentientBeings = it },
                        onCatalyst = { catalyst = it },
                        // Defined here, not in the child: it reads the state live, so two
                        // taps in one frame flip twice, as they did before the split.
                        onToggleSuspended = { suspended = !suspended },
                    )
                    SectionLabel(if (mode == RitualMode.QUICK) "Preparation" else "Preparation (Phase I)")
                    FlowRow {
                        preparations.forEach { option ->
                            FilterChip(
                                selected = option.quality == preparation.quality,
                                onClick = { preparation = option },
                                label = { Text("${option.quality} ${option.printed}") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    Text(preparation.looksLike, style = MaterialTheme.typography.bodySmall)
                    CellValueChips(
                        printed = preparation.printed,
                        values = valuesOf(preparation.modifier, preparation.best, preparation.openEnded),
                        applied = RitualMode.valueFor(preparation, preparationPick),
                        onPick = { heldPreparationPick = preparation to it },
                    )

                    // Quick mode only. In a full ritual the preparation row
                    // above already priced the link -- its own text names one
                    // -- so offering it here would pay a perfect link twice.
                    if (mode.usesTargetLink) {
                        SectionLabel("Link to target")
                        FlowRow {
                            RitualMode.links().forEach { option ->
                                FilterChip(
                                    selected = link?.link == option.link,
                                    onClick = { link = if (link?.link == option.link) null else option },
                                    label = { Text("${option.link} ${option.printed}") },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                        link?.let { chosen ->
                            Text(chosen.looksLike, style = MaterialTheme.typography.bodySmall)
                            CellValueChips(
                                printed = chosen.printed,
                                values = valuesOf(chosen.modifier, chosen.best, openEnded = false),
                                applied = RitualMode.valueFor(chosen, linkPick),
                                onPick = { heldLinkPick = chosen to it },
                            )
                        }
                    }

                    // Full mode only, and the exact mirror of the row above:
                    // Phase II prints the Flourish Bonus and the Quick
                    // Ritual's Step 3 sums exactly the two modifiers it names.
                    // Withheld rather than offered-and-refused -- a chip that
                    // throws on Roll is not a choice (the two Aim rows, 6t).
                    if (mode.usesFlourish) {
                        SectionLabel("Delivery (Phase II)")
                        FlowRow {
                            FilterChip(
                                selected = flourish,
                                onClick = { flourish = !flourish },
                                label = {
                                    Text("Flourish ${signed(RitualMode.FLOURISH_BONUS)}")
                                },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        Text(
                            "\"The GM may award a Flourish Bonus " +
                                "(${signed(RitualMode.FLOURISH_BONUS)}) for good " +
                                "in-character delivery.\" Awarded, not earned — the " +
                                "app does not judge the performance.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    // Ch. 7 IV.F. Invoking is optional -- praying to oneself
                    // needs no entity and no table.
                    SectionLabel("Power source")
                    FlowRow {
                        FilterChip(
                            selected = !invoked,
                            onClick = { invoked = false },
                            label = { Text("Personal SPI") },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        FilterChip(
                            selected = invoked,
                            onClick = { invoked = true },
                            label = { Text("Invocation") },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    if (invoked) {
                        FilterChip(
                            selected = goodStanding,
                            onClick = { goodStanding = !goodStanding },
                            label = { Text("Orthodox deity, in good standing") },
                        )
                        Text(
                            // Spelled out because the -2 is the slice's whole
                            // trap: it moves the GM's table roll toward the
                            // good rows, and would be a penalty on the ritual.
                            "Shifts the entity's 3d6 by −2, toward a better response.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val given = answer
                        if (given == null) {
                            TextButton(onClick = {
                                // Guarded inline, not by the button leaving
                                // the tree: two taps can both reach the
                                // handler before a recomposition, and the
                                // book has the GM roll this once. The same
                                // guard Divination's Ask needed (6p).
                                if (answer != null) return@TextButton
                                answer = Invocation.ask(dice, goodStanding)
                            }) { Text("Ask the entity") }
                        } else {
                            Text(
                                "Rolled ${given.roll}" +
                                    (if (given.shifted != given.roll) " → ${given.shifted}" else "") +
                                    ": ${given.response.response}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    // The reference Phase II points at: "(4-part Hermes
                    // structure — see reference below)". The Flourish chip
                    // above quoted the rest of that sentence and dropped the
                    // parenthetical, so the app awarded +1 for narrating a
                    // structure it never showed. Rendered HERE rather than
                    // beside the chip because the book's own note picks the
                    // structure off the power source chosen just above, and a
                    // reference that changes with a control below it reads as
                    // a glitch. Full mode only, on the same warrant as the
                    // chip: the Quick Ritual's four steps print no narration.
                    if (mode.usesFlourish) {
                        val incantation = Incantations.forPowerSource(invoked)
                        // What to say, read once: closed until asked (6gl).
                        RuleNote("Incantation (Phase II)") {
                        Text(
                            "${incantation.structure} — ${incantation.text}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        incantation.parts.forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        incantation.note?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        // Shown, never chosen: nothing here can say a ritual IS
                        // a summoning, and gating on Heavy would say it of
                        // binding and affliction too.
                        Incantations.all().filter { it.referenceOnly }.forEach {
                            Text(
                                "${it.scope}: ${it.text}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        }
                    }

                    RitualSupportAndLanguage(
                        ownedSupportSkills = ownedSupportSkills,
                        support = support,
                        language = language,
                        boughtLanguages = boughtLanguages,
                        grantedLanguages = grantedLanguages,
                        // Reads `support` live, for the same reason as Suspended's toggle.
                        onToggleSupport = { name ->
                            support = if (name in support) {
                                support - name
                            } else {
                                support + name
                            }
                        },
                        onLanguage = { language = it },
                    )

                }
                // Every term by name, because the breakdown and the target
                // must reconcile -- the invariant 6ag added 300 cases for and
                // 6ak broke once. CoR is read from the same function
                // targetFor calls: a literal here was a second copy of one
                // rule, which is how the breakdown stopped reconciling in 6ag/6ai.
                val terms = if (target == null) emptyList() else listOfNotNull(
                    "$RITUAL_SKILL $skillLevel",
                    "${weight.weight} ${weight.modifier}",
                    "preparation ${signed(RitualMode.valueFor(preparation, preparationPick))}",
                    link?.let { "${signed(RitualMode.valueFor(it, linkPick))} ${it.link.lowercase()} link" },
                    if (flourish) "${signed(RitualMode.FLOURISH_BONUS)} flourish" else null,
                    answer?.ritualModifier?.takeIf { it != 0 }?.let { "${signed(it)} from the entity" },
                    language?.ritualModifier?.takeIf { it != 0 }?.let { "$it for an accented tongue" },
                    if (conditionPenalty != 0) "$conditionPenalty hurt or tired" else null,
                    if (shock != 0) "$shock shock" else null,
                    states.line,
                    Corruption.ritualPenalty(cor).takeIf { it != 0 }?.let { "$it for $cor CoR" },
                    RitualMode.supportBonus(support).takeIf { it != 0 }
                        ?.let { "${signed(it)} from supporting skills" },
                    PowerSources.pacingModifier(complexity, pacing).takeIf { it != 0 }
                        ?.let { "${signed(it)} ${pacing.name.lowercase()}" },
                    PowerSources.participantBonus(assistants).takeIf { it != 0 }
                        ?.let { "${signed(it)} from $assistants assisting" },
                    sacrifice?.let { "${signed(PowerSources.sacrificeBonus(it, sentientBeings))} sacrifice" },
                    catalyst.takeIf { it != 0 }?.let { "${signed(it)} catalyst" },
                    if (suspended) "${signed(PowerSources.SUSPENSION_PENALTY)} suspended" else null,
                )
                val refusal = when {
                    // Broken is offered as a chip and then refuses, rather
                    // than being hidden: a caster who only speaks it needs
                    // to see WHY there is no roll.
                    target == null && rejected -> "No roll — the entity rejected the ritual."
                    target == null -> "No roll — the book forbids this attempt."
                    // "The GM rolls secretly on the Entity Response Table
                    // FIRST, then applies the entity's modifier to the
                    // Ritualistic Magic roll" (p. 66). The answer also prices
                    // the attempt (Invocation.fuelFor), so the charge is not
                    // known until it is given. Withdrawn rather than left to
                    // refuse on tap, the shape 6x and 6ar both took.
                    invoked && answer == null ->
                        "No roll yet — ask the entity first. The book rolls the " +
                            "Entity Response Table before the ritual, and its answer " +
                            "sets both the modifier and what this attempt costs."
                    // Ch. 7's Power Source A: "SPI cannot be reduced below 0."
                    // The book's own answer is the rest of Section IV: bleed
                    // for it, bring assistants, offer a sacrifice.
                    !Pools.canAffordSpi(spiOwed, spiAvailable) ->
                        "No roll — $spiOwed SPI is more than the $spiAvailable this " +
                            "caster has, and personal spirituality cannot be spent " +
                            "below 0. Pay in HP, bring assistants, or offer a sacrifice."
                    else -> null
                }
                RitualFooter(
                    terms = terms,
                    target = target,
                    cost = "costs " + (if (hpOwed > 0) "$spiOwed SPI and $hpOwed HP" else "$spiOwed SPI") +
                        (if (residual) ", residual power only — the entity did not answer" else ""),
                    refusal = refusal,
                    onCast = {
                        // One Cast spends once: a second tap before the
                        // button leaves the tree would charge the fuel
                        // again, and below 0 that buys permanent CoR.
                        if (outcome != null || target == null || refusal != null) return@RitualFooter
                        // Against the number PRINTED above, and nothing read
                        // here: this handler belongs to the composition that
                        // drew it, so a state it read now could be a row, a
                        // mode or a complexity that number never saw -- which
                        // targetFor refuses by throwing.
                        outcome = RitualResolution.rollAgainst(target, dice)
                        // The fuel is spent whether or not it works: "No
                        // effect. SPI and materials spent" (p. 62). HP goes
                        // through the tracker too -- printing a sacrifice the
                        // app then fails to charge is how a tracker starts
                        // lying.
                        //
                        // The CoR is NOT charged here: a push can move this
                        // ritual off the Critical Failure Table altogether,
                        // so the row's price is read once, at [finish].
                        onSpendSpi(spiOwed)
                        if (hpOwed > 0) onSpendHp(hpOwed)
                    },
                )
            }
        },
    )
}
