package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.rules.BurnKind
import com.pathways.rules.Pools
import com.pathways.rules.AntiDivination
import com.pathways.rules.AntiDivinationResult
import com.pathways.rules.AntiDivinationTechniques
import com.pathways.rules.Divination
import com.pathways.rules.DivinationOutcome
import com.pathways.rules.RandomSource
import com.pathways.rules.RitualMode
import com.pathways.rules.RitualResolution
import com.pathways.rules.TraitEffects
import com.pathways.rules.content.DivinationModifier
import com.pathways.rules.content.DivinationMethod
import com.pathways.rules.content.DivinationSupport

/**
 * One divination, in the book's four steps (Ch. 6.5, Sec. III).
 *
 * State the question's difficulty, choose a method, tick the circumstances
 * that apply, and roll. [Divination] does the arithmetic; what this adds is
 * the order, and the fact that the SPI is actually SPENT -- a divination that
 * costs nothing is the same defect the Soul Debt slice fixed one pool over.
 *
 * The Step 3 table's first row is deliberately not offered: "Question
 * difficulty / See Step 1 table" is a pointer at Step 1, which the chips above
 * already apply, and [Divination.modifierTotal] refuses it rather than letting
 * a caller charge the question twice.
 *
 * Rows printing a range ("+0 to +4") START at the end nearer zero and offer
 * the rest of the span as chips (6dz). This comment said "applied at the end
 * nearer zero ... ponytail: a stepper when a GM asks" for five slices after
 * the picker shipped.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DivinationDialog(
    skillLevel: Int,
    // Where [skillLevel] came from, when it is not simply this character's
    // own Divination Arts -- p. 54's default, lent by their Ritualistic
    // Magic. Authored by Divination.sourceLine rather than assembled here:
    // the flag alone cannot tell "has no divination skill" apart from "has
    // one, and the sibling lends a better number", and a screen writing its
    // own version printed the first at a character in the second (Codex).
    sourceLine: String? = null,
    // What the trackers cost this roll, GIVEN the SPI this divination is
    // about to spend. A function rather than a number because the book pays
    // BEFORE it rolls -- Step 2 "Choose Method & Pay SPI", Step 3 "Roll &
    // Apply Modifiers" (p. 55) -- so a method whose cost pushes the diviner
    // across an attrition threshold must roll under that penalty. A flat Int
    // was computed before the payment and could only ever describe the
    // caster who had not paid yet.
    conditionPenaltyAfterSpending: (Int) -> Int,
    /**
     * The book's four supporting skills this diviner actually has.
     *
     * Resolved by [Divination.supportSkillsOwnedBy] at the call site, never
     * here: a screen comparing names itself could offer a chip the rules
     * refuse, or withhold one they would accept (6am). Empty is a real answer
     * and says so on screen rather than rendering an empty row.
     */
    ownedSupportSkills: List<DivinationSupport> = emptyList(),
    /** The Major Wound's shock: not a circumstance, so it is named on its own. */
    shock: Int = 0,
    /**
     * Ether Body Damage's "-1 to all SPI-based rolls". Divination Arts is
     * SPI/Hard, so this roll is one -- and it reaches its target through
     * [Divination.targetFor] rather than a [com.pathways.model.SkillKey], so
     * the attribute-scoped join in `TraitEffects.modifiers` cannot see it.
     * Named on its own for the same reason shock is: a target that moves
     * without the breakdown saying why is how these stopped reconciling (6ag).
     */
    spiTrait: Int = 0,
    spiAvailable: Int,
    // The diviner's own Sequence, or null for a mortal. Sec. 6's Sequence
    // Advantage compares the two parties, so withholding this silently picked
    // the side that always applies the full penalty.
    divinerSequence: Int? = null,
    dice: RandomSource,
    onSpendSpi: (Int) -> Unit,
    onSpendHp: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val difficulties = remember { Divination.difficulties() }
    val scopes = remember { Divination.scopes() }
    val methods = remember { Divination.methods() }
    // The cross-reference row is not a modifier; Divination refuses it.
    val rows = remember { Divination.modifiers().filterNot { it.crossReference } }
    val immunityRows = remember { AntiDivination.rows() }
    val allTechniques = remember { AntiDivinationTechniques.all() }

    var difficulty by remember { mutableStateOf(difficulties.first()) }
    // Sec. I's boundary table: which skill the question needs. Defaults to the
    // first row like the band above it, which is also the row the book prices
    // nothing on -- a default that charges is a rule nobody declared.
    var scope by remember { mutableStateOf(scopes.first()) }
    var method by remember { mutableStateOf(methods.first()) }
    var chosen by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Which of the diviner's own supporting skills they are bringing to bear.
    // A choice rather than an automatic bonus, because the book's own row is
    // "Hidden Lore (relevant)" -- whether the domain is relevant to THIS
    // question is the GM's call, and granting it unasked would answer that
    // question for them.
    var supporting by remember { mutableStateOf<Set<String>>(emptySet()) }
    var outcome by remember { mutableStateOf<DivinationOutcome?>(null) }
    var backlash by remember { mutableStateOf<String?>(null) }
    // Dowsing Rods prints "1-2 SPI", so the end is the GM's to pick. Reset
    // with the method, or a 2 chosen for the rods would follow them onto a
    // method that costs 1 (Codex).
    var spiChoice by remember(method) { mutableIntStateOf(method.spi) }
    // What the GM picked inside a printed range, by the row's own `source`.
    // Four of this table's rows print a span and the screen applied the first
    // figure of each, so ticking "Method match +0 to +2" added +0 -- a control
    // whose only effect was nothing. `BoonDialog` has had the picker since
    // 6bk; this table never got it.
    val picked = remember { mutableStateMapOf<String, Int>() }
    // "Previous divination about the same subject (cumulative) -2 per repeat".
    // The row prices one repeat and the engine multiplies; without this the
    // second attempt cost what the first did.
    var repeats by remember { mutableIntStateOf(1) }
    // p. 55: "As with rituals, you may spend additional SPI (1 SPI = +1, no
    // cap) after seeing your result." Accumulated here and charged at [finish].
    var burned by remember { mutableIntStateOf(0) }
    // null = an ordinary target. The two counters reset with it, so a link
    // ticked against a demigod cannot survive onto a shopkeeper -- the stale
    // pick the boon and ritual dialogs were each fixed for.
    var targetSequence by remember { mutableStateOf<Int?>(null) }
    var directLink by remember(targetSequence) { mutableStateOf(false) }
    var sacrificeHp by remember(targetSequence) { mutableStateOf(false) }

    // Ch. 6.5 Sec. 5-6. The HP is a real cost, so it is only offered when the
    // GM has named a protected target -- burning 4 HP against a shopkeeper
    // would buy nothing and the tracker would still be down 4.
    val hpForSacrifice = remember {
        AntiDivination.counterNamed("HP Sacrifice").hpRequired ?: 0
    }
    // Ch. 6.5 Sec. 3. What the TARGET has raised against being found -- a
    // declaration here, because the target has no sheet in this dialog. Not
    // reset with targetSequence: a ward is up whether or not the GM has named
    // the Sequence behind it.
    var techniqueIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val techniques = remember(techniqueIds) {
        allTechniques.filter { it.id in techniqueIds }
    }
    // Summed BEFORE the counters halve it: "their penalties add together when
    // a divination is attempted", which is the moment Sec. 6 acts.
    val techniquePenalty = AntiDivinationTechniques.penaltyTotal(techniques)

    val protection = remember(
        targetSequence,
        directLink,
        sacrificeHp,
        techniquePenalty,
        divinerSequence,
    ) {
        targetSequence?.let {
            AntiDivination.against(
                targetSequence = it,
                techniquePenalty = techniquePenalty,
                // A Beyonder's own Sequence, which `BeyonderState` has carried
                // since M3. This was hardcoded null with a comment saying the
                // sheet does not model it -- it does, and the effect was that
                // Sec. 6's Sequence Advantage could never fire: a Seq 2
                // diviner reading a Seq 4 target still took the full -10 and
                // the critical-only rule that the section forgives outright.
                // Null stays right for a mortal, who has no Sequence at all.
                divinerSequence = divinerSequence,
                directLink = directLink,
                hpSacrificed = if (sacrificeHp) hpForSacrifice else 0,
            )
        } ?: techniquesOnly(techniquePenalty)
    }

    // Kept apart from the protection penalty, because the breakdown line names
    // them separately: folding them and printing both showed "circumstances
    // -10, Seq 4 (Demigod) -10" for one -10, which does not add up to the
    // target printed directly above it (found on the emulator).
    // The cost is known before the penalty, because the penalty depends on
    // it: the book pays in Step 2 and rolls in Step 3.
    val cost = Divination.spiFor(method, spiChoice)
    val picks = rows.filter { it.source in chosen }
    val overrides = overridesFor(picks, picked)
    val circumstances = Divination.modifierTotal(picks, overrides, repeats) +
        conditionPenaltyAfterSpending(cost)
    // Ch. 6.5 p. 55's supporting skills. Named on their own rather than folded
    // into `circumstances`, because the breakdown line has to add up to the
    // target printed above it -- the invariant 6ag added a 300-case test for
    // and 6ai reintroduced once. The cap is Divination's own +2, NOT ch. 7's
    // +3: two printed rules, and straightening them is the 4.1 sin (6at).
    val support = Divination.supportBonus(
        ownedSupportSkills.filter { it.skill in supporting },
    )
    // What reaching for Divination Arts at this scope costs. Named on its own
    // rather than folded into `circumstances`, for the reason the support
    // bonus is: the signed numbers on the breakdown line have to sum to the
    // target printed above them. It is NOT the question's band -- that prices
    // how hard the question is and this prices which skill is asking.
    val scopePenalty = Divination.penaltyFor(scope)
    val extras =
        circumstances + protection.penalty + shock + spiTrait + support + scopePenalty
    val target = Divination.targetFor(skillLevel, difficulty, extras)

    // Charged by zeroing what it accumulated, so paying is idempotent and
    // every exit from one question pays exactly once -- Close, a back press,
    // the scrim, and "Ask again", which ends a question as surely as closing
    // does. Nothing else is deferred: the backlash is a sample already taken,
    // shown only while the outcome it belongs to is still a critical one.
    val settleBurn = {
        if (burned > 0) {
            onSpendSpi(burned)
            burned = 0
        }
    }
    // See RitualDialog: leaving composition is not a dismiss request, and
    // `remember` does not survive it.
    DisposableEffect(Unit) { onDispose { settleBurn() } }
    val finish = {
        settleBurn()
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = finish,
        confirmButton = { TextButton(onClick = finish) { Text("Close") } },
        dismissButton = {
            if (outcome != null) {
                TextButton(
                    onClick = {
                        // The push bought the question that is ending, so it
                        // is paid for here rather than carried into the next.
                        settleBurn()
                        // The backlash belongs to the roll that caused it;
                        // carrying it into the next question would report a
                        // critical the diviner never rolled.
                        outcome = null
                        backlash = null
                    },
                ) { Text("Ask again") }
            }
        },
        title = { Text("Divination Arts $skillLevel") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val answer = outcome
                if (answer != null) {
                    // The backlash is shown only while the roll it came from
                    // is still critical: a push can take a margin-based
                    // critical failure off that row, and printing the ritual
                    // table's answer beside a merely-failed reading would
                    // report a disaster the diviner spent SPI to avoid.
                    Result(answer, backlash?.takeIf { answer.isCritical })
                    if (Divination.canPush(answer)) {
                        BurnRow(
                            kind = BurnKind.MYSTICAL,
                            burned = burned,
                            spiAvailable = spiAvailable - burned,
                            note = "Separate from the method's cost, which is " +
                                "already paid. Charged when this question ends.",
                        ) { points ->
                            // See RitualDialog: the row was drawn against a
                            // value two taps can outrun.
                            val live = outcome ?: return@BurnRow
                            if (!Pools.canAffordSpi(burned + points, spiAvailable)) {
                                return@BurnRow
                            }
                            burned += points
                            outcome = Divination.pushed(live, points)
                        }
                    }
                    return@Column
                }
                sourceLine?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text("Question", style = MaterialTheme.typography.titleSmall)
                FlowRow {
                    difficulties.forEach { option ->
                        FilterChip(
                            selected = option == difficulty,
                            onClick = { difficulty = option },
                            label = {
                                Text("${option.band.substringBefore(" ")} ${option.printedModifier}")
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                Text(difficulty.example, style = MaterialTheme.typography.bodySmall)

                Text(
                    "Scope",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FlowRow {
                    scopes.forEach { option ->
                        FilterChip(
                            selected = option == scope,
                            onClick = { scope = option },
                            label = {
                                // The two free rows carry no number to show,
                                // and showing "+0" would print a modifier the
                                // book does not.
                                Text(
                                    listOfNotNull(option.scope, option.printedModifier)
                                        .joinToString(" "),
                                )
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                Text(
                    "${scope.examples} — ${scope.guidance}",
                    style = MaterialTheme.typography.bodySmall,
                )

                Text(
                    "Method",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FlowRow {
                    methods.forEach { option ->
                        FilterChip(
                            selected = option == method,
                            onClick = { method = option },
                            label = { Text("${option.method} ${spiLabel(option)}") },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                Text(
                    "${method.time}, ${spiLabel(method)} SPI — ${method.bestFor}",
                    style = MaterialTheme.typography.bodySmall,
                )
                method.spiMax?.let { most ->
                    // The one method whose cost is a range. Showing it while
                    // always charging the low end is how the app undercharges
                    // a debt the book makes permanent.
                    FlowRow {
                        (method.spi..most).forEach { option ->
                            FilterChip(
                                selected = option == spiChoice,
                                onClick = { spiChoice = option },
                                label = { Text("$option SPI") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }

                Text(
                    "Circumstances",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FlowRow {
                    rows.forEach { option ->
                        FilterChip(
                            selected = option.source in chosen,
                            onClick = {
                                chosen = if (option.source in chosen) {
                                    chosen - option.source
                                } else {
                                    chosen + option.source
                                }
                            },
                            label = {
                                Text("${option.source.substringBefore(" (")} ${option.printed}")
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                // The second row of chips appears only for a span that is
                // ticked: an untouched table reads as it always did, and a
                // row printing one number gets no picker at all.
                picks.filter { it.best != null }.forEach { row ->
                    Text(
                        "${row.source.substringBefore(" (")} — pick a value",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    FlowRow {
                        endsBetween(row.modifier!!, row.best).forEach { value ->
                            FilterChip(
                                // The row's own figure is what the engine
                                // applies when nothing is picked, so the chip
                                // showing selected is the one being used.
                                selected = (picked[row.source] ?: row.modifier) == value,
                                onClick = { picked[row.source] = value },
                                label = { Text(signed(value)) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }
                if (picks.any { it.perRepeat }) {
                    Text(
                        "How many times this subject has been divined before",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    FlowRow {
                        repeatChoices().forEach { count ->
                            FilterChip(
                                selected = repeats == count,
                                onClick = { repeats = count },
                                label = { Text("x$count") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }

                Text(
                    "Supporting skills",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (ownedSupportSkills.isEmpty()) {
                    // Naming the four is the point: a bare absence reads as a
                    // screen that forgot to render, which is the call 6am made
                    // for the ritual dialog's own empty branch.
                    Text(
                        "This diviner has none of the book's " +
                            "${Divination.supportingSkills().size} supporting skills " +
                            "(${Divination.supportingSkills().joinToString { it.skill }}).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        // What each skill is worth is on its own chip below, so
                        // this line states only the cap. "+1 each" was a third
                        // copy of a number the rows already print.
                        "Capped at +${Divination.MAX_SUPPORT_BONUS} — the book's own cap " +
                            "for divination, which is not ch. 7's " +
                            "+${RitualMode.SUPPORT_CAP}.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FlowRow {
                        ownedSupportSkills.forEach { option ->
                            FilterChip(
                                selected = option.skill in supporting,
                                onClick = {
                                    supporting = if (option.skill in supporting) {
                                        supporting - option.skill
                                    } else {
                                        supporting + option.skill
                                    }
                                },
                                label = { Text("${option.skill} ${signed(option.bonus)}") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }
                // A printed bonus this screen does not offer, said out loud
                // rather than left silent, with its reason read from the rules
                // rather than paraphrased here. OUTSIDE the branch above: the
                // refusal has nothing to do with owning a supporting skill, and
                // a diviner who owns none is the likelier reader of it.
                Divination.UNAPPLIED.forEach { (rule, reason) ->
                    Text(
                        "$rule ${signed(Divination.SPECIALISATION_BONUS)} is not " +
                            "applied here — $reason.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Target's protection", style = MaterialTheme.typography.titleSmall)
                Text(
                    "A Beyonder resists being divined. Leave this off for an " +
                        "ordinary target.",
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow {
                    FilterChip(
                        selected = targetSequence == null,
                        onClick = { targetSequence = null },
                        label = { Text("Not a Beyonder") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    // The book's table covers 2..9 and stops; AntiDivination
                    // throws below that rather than inventing a god's row, so
                    // the chips stop where the table does.
                    immunityRows.forEach { row ->
                        FilterChip(
                            selected = targetSequence
                                ?.let { it in row.sequenceLow..row.sequenceHigh } == true,
                            onClick = { targetSequence = row.sequenceHigh },
                            label = { Text(row.label.substringBefore(" (")) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                targetSequence?.let { seq ->
                    Text(
                        AntiDivination.rowFor(seq).text,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FlowRow {
                        FilterChip(
                            selected = directLink,
                            onClick = { directLink = !directLink },
                            label = { Text("True name + personal item") },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        FilterChip(
                            selected = sacrificeHp,
                            onClick = { sacrificeHp = !sacrificeHp },
                            label = { Text("Burn $hpForSacrifice HP") },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }

                Text(
                    "Active techniques (Sec. 3)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "What the target has raised, and paid SPI for. All active " +
                        "techniques stack.",
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow {
                    allTechniques.forEach { option ->
                        FilterChip(
                            selected = option.id in techniqueIds,
                            onClick = {
                                techniqueIds =
                                    if (option.id in techniqueIds) techniqueIds - option.id
                                    else techniqueIds + option.id
                            },
                            label = {
                                Text(
                                    option.name + (
                                        option.divinationPenalty
                                            ?.let { " ${signed(it)}" } ?: ""
                                        ),
                                )
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                techniques.forEach { chosen ->
                    // Four of the six price no roll at all -- they divert,
                    // redirect or answer falsely. Saying what one DOES is the
                    // only honest thing to show beside a chip worth +0.
                    //
                    // Every printed line, not just the Effect. Each technique
                    // also prints who may use it ("Fool Pathway, Seq 6 --
                    // Faceless") and its own caveats -- 3A's ward breaks on a
                    // wound over HP/3, 3D fails against a diviner 2+ Sequences
                    // stronger, 3B extends to another target for +1 SPI. The
                    // target has no sheet here, so none of it can be checked;
                    // showing it is the call 6af made for the Sealed Artifact
                    // grade table, and a caveat that is extracted but never
                    // rendered is one the GM cannot apply either.
                    Text(
                        "${chosen.name} — ${chosen.availability}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    chosen.bullets.forEach { line ->
                        Text(
                            "${line.label}: ${line.text}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Roll 3d6 ≤ $target", style = MaterialTheme.typography.titleMedium)
                if (protection.requiresCritical) {
                    // Not expressible as a penalty, so it cannot be left to the
                    // target number: the book forbids the ordinary success a
                    // high skill would otherwise buy.
                    Text(
                        "Only a critical success (3–4) can succeed — the book says " +
                            "this penalty cannot be offset by skill alone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    "Skill $skillLevel, question ${signed(difficulty.modifier)}" +
                        (
                            if (scopePenalty != 0) {
                                ", ${scope.scope.lowercase()} scope ${signed(scopePenalty)}"
                            } else ""
                            ) +
                        (if (shock != 0) ", shock ${signed(shock)}" else "") +
                        (
                            if (spiTrait != 0) {
                                ", ${signed(spiTrait)} ${TraitEffects.ETHER_BODY_DAMAGE}"
                            } else ""
                            ) +
                        (
                            if (circumstances != 0) ", circumstances ${signed(circumstances)}"
                            else ""
                            ) +
                        // Named rather than folded into `circumstances`: the
                        // signed numbers on this line must sum to the target
                        // printed above it (6ag, 6ai), and the cap means the
                        // total is not the sum of the chips a GM ticked.
                        (
                            if (support != 0) {
                                ", ${signed(support)} from supporting skills"
                            } else ""
                            ) +
                        (
                            if (protection.parts.isEmpty()) ""
                            else ", " + protection.parts.joinToString(", ")
                            ) +
                        // What will actually be CHARGED, not the printed
                        // range: the range is above, beside the chips that
                        // choose inside it.
                        ". Costs $cost SPI.",
                    style = MaterialTheme.typography.bodySmall,
                )
                // At 0 SPI the pool table says a Beyonder "cannot activate any
                // SPI-cost abilities". Withdrawn rather than refused on tap.
                if (!Pools.canAffordSpi(cost, spiAvailable)) {
                    Text(
                        "No question — this method costs $cost SPI and only " +
                            "$spiAvailable is left. SPI cannot be spent below 0.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else TextButton(
                    onClick = {
                        // One Ask spends once. Two taps can reach this handler
                        // before the button leaves the tree, and the second
                        // would charge the SPI again -- which below 0 buys
                        // Soul Debt, and that CoR never comes back (Codex).
                        if (outcome != null) return@TextButton
                        val result = Divination.attempt(
                            skill = skillLevel,
                            difficulty = difficulty,
                            source = dice,
                            extras = extras,
                            requiresCritical = protection.requiresCritical,
                        )
                        outcome = result
                        // Spent whatever the roll says, like the SPI: it is
                        // burned to cast, not paid on success. But only what
                        // the sacrifice actually BOUGHT -- against a target
                        // with no penalty to forgive, AntiDivination reports 0
                        // and the offer costs nothing.
                        if (protection.hpSpent > 0) onSpendHp(protection.hpSpent)
                        // The clarity row prints no effects of its own: it says
                        // "treat as a ritual critical failure", so the ritual
                        // table is ROLLED rather than copied -- off the same
                        // injected source, never Random directly.
                        backlash = if (result.isCritical) {
                            RitualResolution.criticalFor((1..3).sumOf { dice.roll(6) }).outcome
                        } else {
                            null
                        }
                        onSpendSpi(cost)
                    },
                ) { Text("Ask") }
            }
        },
    )
}

@Composable
private fun Result(outcome: DivinationOutcome, backlash: String?) {
    Text(
        "3d6: ${outcome.roll.total} vs ${outcome.roll.target}",
        style = MaterialTheme.typography.titleLarge,
    )
    Text(
        "${outcome.clarity.result} — ${outcome.clarity.clarity}",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(outcome.clarity.effect, style = MaterialTheme.typography.bodySmall)
    backlash?.let {
        Text(
            "Critical Failure Table: $it",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** "1", or "1-2" for the one method whose cost the GM picks inside. */
private fun spiLabel(method: DivinationMethod): String =
    method.spiMax?.let { "${method.spi}-$it" } ?: "${method.spi}"

private fun signed(n: Int) = if (n >= 0) "+$n" else "$n"

/**
 * A target with techniques up but no Sequence declared.
 *
 * [AntiDivination.against] needs a row from the immunity table and there is
 * none; the techniques still apply, because a ward does not stop working
 * because the GM has not said how strong its owner is. No counter is offered
 * without a Sequence either, so nothing is dropped here.
 */
private fun techniquesOnly(penalty: Int) = AntiDivinationResult(
    penalty = penalty,
    requiresCritical = false,
    parts = if (penalty == 0) emptyList()
    else listOf("active techniques ${signed(penalty)}"),
)

/**
 * What the GM picked, narrowed to the rows ticked RIGHT NOW.
 *
 * Filtering at use rather than pruning [picked] on every change is what makes
 * a left-behind value unable to reach a later question: it can only come back
 * by re-ticking the row it belongs to, where it renders as the selected chip.
 * That is the defect the attack dialog's "Another turn" shipped with, avoided
 * by construction instead of by remembering to clean up, and it is the shape
 * `BoonDialog` already uses.
 */
internal fun overridesFor(
    picks: List<DivinationModifier>,
    picked: Map<String, Int>,
): Map<String, Int> =
    picks.mapNotNull { row -> picked[row.source]?.let { row.source to it } }.toMap()
