package com.pathways.app

import com.pathways.rules.Revelation
import com.pathways.rules.content.RitualRevelation
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.rules.ClauseBonus
import com.pathways.rules.Corruption
import com.pathways.rules.Incantations
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
import com.pathways.rules.content.TargetLink

/** The skill every ritual is rolled against (Ch. 7). */
const val RITUAL_SKILL = "Ritualistic Magic"

/**
 * What stands between a ritual and its cast; null when nothing does.
 *
 * Broken is offered as a chip and then refuses, rather than being hidden: a
 * caster who only speaks it needs to see WHY there is no roll. v6.9i prints
 * no floor on spending SPI (owner's ruling 11), so fuel never refuses: an
 * overspend is Soul Debt, charged by the tracker.
 */
internal fun ritualRefusal(canSpeak: Boolean): String? =
    if (canSpeak) null else "No roll — the book forbids this attempt."


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
    /**
     * The mystical languages this character BOUGHT, each as "Hermes, Native,
     * written only". Printed beside the picker and never fed to it: owning a
     * language does not say which one this ritual is cast in, and a trait
     * (Official Beyonder) can grant one this list cannot see. Dead Language
     * Fluency's is bought, free, so it is here (6hh).
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
    // The time row on the weight's own name: v6.9i prints the weight and the
    // Time & Complexity table on the same four names, and the fuel is the
    // weight's.
    val complexity = PowerSources.complexityFor(weight)
    // Reset with the weight, or a Major ritual's 12 SPI would follow the
    // caster onto a Light one that costs 2.
    var spiChoice by remember(weight) { mutableIntStateOf(weight.spi) }
    // Reset with the FUEL: spiOwed REFUSES HP that covers more than the
    // ritual costs, so a sacrifice left over from a 12 SPI ritual would throw
    // on a 2 SPI one rather than merely mislead.
    var hpSacrificed by remember(weight, spiChoice) { mutableIntStateOf(0) }
    // "Before rolling, spend additional SPI (1 SPI = +1, no cap) to improve
    // the roll. This is separate from the ritual's fuel cost." (v6.9i 3960)
    var burned by remember { mutableIntStateOf(0) }
    var settled by remember { mutableStateOf(false) }
    var pacing by remember { mutableStateOf(RitualPacing.NORMAL) }
    var assistants by remember { mutableIntStateOf(0) }
    // External Sacrifice/Catalysts: the GM's -40..+40.
    var sacrifice by remember { mutableIntStateOf(0) }
    var suspended by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<RitualOutcome?>(null) }
    // Null by default -- "not stated" -- so a ritual described without a
    // language rolls exactly where it did before Ch. 3's table was read.
    var language by remember { mutableStateOf<LanguageProficiency?>(null) }
    val canSpeak = language?.let { Languages.canPerformRituals(it) } != false
    // Invocation: "Adds +1 to +5 to the ritual roll (GM discretion ...)".
    // Praying to oneself is the default and adds nothing.
    var invoked by remember { mutableStateOf(false) }
    var orthodox by remember { mutableStateOf(false) }
    var invocation by remember { mutableIntStateOf(PowerSources.INVOCATION_LOWEST) }
    var revelation by remember { mutableStateOf<RitualRevelation?>(null) }
    var sacrifices by remember { mutableIntStateOf(0) }
    // Injury and fatigue reach a ritual like any other roll; the CoR penalty
    // is NOT included in [conditionPenalty] because RitualResolution applies
    // it itself, and counting it twice would penalise a corrupted caster
    // once for the ladder and once again for the same ladder.
    val hurt = conditionPenalty
    val effectiveSkill = skillLevel + hurt + shock + states.total
    val declaration = PowerSourceDeclaration(
        pacing = pacing,
        assistants = assistants,
        sacrifice = sacrifice,
        suspended = suspended,
        invocation = invocationApplied(invoked, orthodox, invocation),
        revelation = revelation?.let { Revelation(it, if (it.perSacrifice != null) sacrifices else 0) },
    )
    // Not computed at all when the book forbids the attempt: Broken prints
    // "Cannot perform rituals in the language", and targetFor refuses it
    // rather than inventing a penalty to roll at.
    val target = if (canSpeak) {
        RitualResolution.targetFor(
            effectiveSkill,
            weight, cor, preparation, language, mode, link, flourish,
            complexity = complexity, powerSources = declaration,
            preparationValue = preparationPick, linkValue = linkPick,
            burned = burned,
        )
    } else {
        null
    }
    val cost = RitualResolution.spiFor(weight, spiChoice)
    // HP is fuel and nothing else. It never reaches `declaration`, because
    // the book says in its own sentence that it "does not grant a roll
    // bonus".
    val declaredSpi = PowerSources.spiOwed(cost, hpSacrificed)
    // The HP as DRAWN beside that SPI: the state read in the handler could be
    // a newer pick the printed cost never saw (Codex).
    val declaredHp = hpSacrificed
    val declaredBurn = burned

    // The outcome's CoR is charged once, when the dialog closes.
    val settle = {
        if (!settled) {
            settled = true
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
        confirmButton = { CloseButton(finish) },
        title = { FormTitle(FORM_RITUAL) {
            Column {
                Text("Cast a ritual")
                Text(
                    "$RITUAL_SKILL $skillLevel",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } },
        text = steadyBody {
            val result = outcome
            if (result != null) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    RitualResultCard(result)
                }
                return@steadyBody
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
                            CaseChip(
                                selected = option == mode,
                                onClick = { mode = option },
                                label = { Text(if (option == RitualMode.QUICK) "Quick" else "Full") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    SectionLabel("Weight")
                    Row {
                        weights.forEach { option ->
                            CaseChip(
                                selected = option.weight == weight.weight,
                                onClick = { weight = option },
                                label = { Text(option.weight) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    RitualPowerSourceChips(
                        weight = weight,
                        complexity = complexity,
                        spiChoice = spiChoice,
                        cost = cost,
                        hpSacrificed = hpSacrificed,
                        pacing = pacing,
                        assistants = assistants,
                        sacrifice = sacrifice,
                        suspended = suspended,
                        invoked = invoked,
                        orthodox = orthodox,
                        invocation = invocation,
                        onSpiChoice = { spiChoice = it },
                        onHpSacrificed = { hpSacrificed = it },
                        onPacing = { pacing = it },
                        onAssistants = { assistants = it },
                        // Stepped from the LIVE value and held to the printed
                        // span, so two taps in one frame step twice, in range.
                        onSacrificeStep = {
                            sacrifice = (sacrifice + it)
                                .coerceIn(PowerSources.SACRIFICE_LOWEST, PowerSources.SACRIFICE_HIGHEST)
                        },
                        // Defined here, not in the child: they read the state
                        // live, so two taps in one frame flip twice.
                        onToggleSuspended = { suspended = !suspended },
                        onInvoked = { invoked = it },
                        onToggleOrthodox = { orthodox = !orthodox },
                        onInvocationStep = {
                            invocation = (invocation + it)
                                .coerceIn(PowerSources.INVOCATION_LOWEST, PowerSources.INVOCATION_HIGHEST)
                        },
                    )
                    // Out of combat too: ch. 9's own burn (owner's ruling 2).
                    OptionStepper(
                        label = "Burn SPI",
                        value = burned,
                        shown = "+$burned",
                        note = "before rolling: 1 SPI buys +1, no cap, separate from the fuel",
                    ) { burned = (burned + it).coerceAtLeast(0) }
                    RevelationPicker(
                        revelation = revelation,
                        sacrifices = sacrifices,
                        onRevelation = { revelation = it },
                        // Stepped from the live value, so two taps step twice.
                        onSacrificeStep = { sacrifices = (sacrifices + it).coerceAtLeast(0) },
                    )
                    SectionLabel(if (mode == RitualMode.QUICK) "Preparation" else "Preparation (Phase I)")
                    FlowRow {
                        preparations.forEach { option ->
                            CaseChip(
                                selected = option.quality == preparation.quality,
                                onClick = { preparation = option },
                                label = { Text("${option.quality} ${option.printed}") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
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
                                CaseChip(
                                    selected = link?.link == option.link,
                                    onClick = { link = if (link?.link == option.link) null else option },
                                    label = { Text("${option.link} ${option.printed}") },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                        link?.let { chosen ->
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
                            CaseChip(
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

                    // The reference Phase II names: "Narrate incantation (4-part
                    // Hermes structure: Invocation, Grace, Request,
                    // Empowerment)". Full mode only, on the same warrant as
                    // the Flourish chip: the Quick line prints no narration.
                    if (mode.usesFlourish) {
                        val incantation = Incantations.standard()
                        // What to say, read once: closed until asked (6gl).
                        RuleNote("Incantation (Phase II)") {
                            Text(incantation.text, style = MaterialTheme.typography.bodySmall)
                            // Shown, never chosen: nothing here can say a
                            // ritual IS a summoning.
                            Incantations.all().filter { it.referenceOnly }.forEach { ref ->
                                Text(
                                    "${ref.scope}: ${ref.text}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                ref.parts.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                                ref.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }

                    MysticalLanguagePicker(
                        language, boughtLanguages, grantedLanguages,
                        onLanguage = { language = it },
                    )
                    // What each pick means, read once: closed (the owner,
                    // 2026-10-18, on frames 18-19, which print none of it
                    // under the chips). Refusals, caps and "the GM picks"
                    // stay where they are.
                    RuleNote("What these picks mean") {
                        listOfNotNull(
                            "Mode: " + if (mode == RitualMode.QUICK) {
                                "Routine work — four steps. Preparation reaches +2 " +
                                    "and the target link is counted separately."
                            } else {
                                "Consequential magic — three phases. Preparation " +
                                    "reaches +3 and already includes the link."
                            },
                            weight.categories?.let { "${weight.weight}: $it" },
                            "${weight.weight}: ${weight.printedCost}, ${weight.baseTime} base time",
                            link?.let { l -> l.looksLike?.let { "${l.link} link: $it" } },
                            language?.let { "${it.proficiency}: ${it.effect}" },
                        ).forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                    }
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
                    invocationApplied(invoked, orthodox, invocation)
                        ?.let { "${signed(it)} invoked" },
                    language?.ritualModifier?.takeIf { it != 0 }?.let { "$it for an accented tongue" },
                    if (hurt != 0) "$hurt hurt or tired" else null,
                    if (shock != 0) "$shock shock" else null,
                    states.line,
                    Corruption.ritualPenalty(cor).takeIf { it != 0 }?.let { "$it for $cor CoR" },
                    PowerSources.pacingModifier(complexity, pacing).takeIf { it != 0 }
                        ?.let { "${signed(it)} ${pacing.name.lowercase()}" },
                    PowerSources.participantBonus(assistants).takeIf { it != 0 }
                        ?.let { "${signed(it)} from $assistants assisting" },
                    sacrifice.takeIf { it != 0 }?.let { "${signed(it)} sacrifice" },
                    if (suspended) "${signed(PowerSources.SUSPENSION_PENALTY)} suspended" else null,
                    if (burned > 0) "+$burned SPI burned" else null,
                )
                val refusal = ritualRefusal(canSpeak)
                RitualFooter(
                    terms = terms,
                    target = target,
                    cost = "costs " + fuelLine(declaredSpi + declaredBurn, hpSacrificed),
                    refusal = refusal,
                    onCast = {
                        // One Cast spends once: a second tap before the
                        // button leaves the tree would charge the fuel
                        // again, and below 0 that buys permanent CoR.
                        if (outcome != null || target == null || refusal != null) return@RitualFooter
                        // Against the number PRINTED above, and nothing read
                        // here: this handler belongs to the composition that
                        // drew it, so a state it read now could be a row or a
                        // mode that number never saw -- which targetFor
                        // refuses by throwing.
                        outcome = RitualResolution.rollAgainst(target, dice)
                        // The fuel is spent whether or not it works: "No
                        // effect. SPI and materials spent." HP goes through
                        // the tracker too.
                        //
                        // The burn with it: both are spent before the dice.
                        onSpendSpi(declaredSpi + declaredBurn)
                        if (declaredHp > 0) onSpendHp(declaredHp)
                    },
                )
            }
        },
    )
}
