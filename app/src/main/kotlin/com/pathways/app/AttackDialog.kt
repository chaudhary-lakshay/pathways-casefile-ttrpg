package com.pathways.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.model.ModEffect
import com.pathways.model.SkillKey
import com.pathways.rules.AttackManeuver
import com.pathways.rules.AttackPlan
import com.pathways.rules.Attacks
import com.pathways.rules.Damage
import com.pathways.rules.DamageRoll
import com.pathways.rules.RandomSource
import com.pathways.rules.Ranged
import com.pathways.rules.SkillRoll
import com.pathways.rules.SkillRollOutcome
import com.pathways.rules.Weapons
import com.pathways.rules.Wounds
import com.pathways.rules.content.CataloguedWeapon
import com.pathways.rules.content.HitLocation
import com.pathways.rules.content.RangePenalty
import com.pathways.rules.content.UnarmedAttack
import com.pathways.rules.content.WeaponMode

/** The one skill whose table pays a damage bonus for skill above DX (p. 38). */
private const val BOXING = "Boxing"

/**
 * One attack: pick the skill, then the maneuver and its options (Ch. 5).
 *
 * The skill comes FIRST because Deceptive Attack's ceiling is half of it --
 * offering a stepper before knowing the skill would mean either capping
 * against the best skill on the sheet or trimming the choice afterwards, and
 * [Attacks] refuses a trimmed plan rather than silently obeying it.
 *
 * Every skill is offered, not a curated combat list: this book tags no skill
 * as a combat skill, so which of them can attack is the GM's call, and
 * inventing the list here would be inventing content. Chapter 8's weapon
 * table names only seven of them, and [Weapons.forSkill] arms those.
 */
@Composable
fun AttackDialog(
    skillLevels: Map<SkillKey, Int>,
    conditions: (SkillKey) -> List<com.pathways.model.Modifier>,
    st: Int,
    dx: Int,
    dice: RandomSource,
    onSpendFp: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val skills = remember(skillLevels) { skillLevels.entries.sortedBy { it.key.name } }
    var picked by remember { mutableStateOf<Map.Entry<SkillKey, Int>?>(null) }
    var plan by remember { mutableStateOf(AttackPlan()) }
    // Null for every skill with no attack table -- Wrestling included, which
    // the book gives "None (grapple only)".
    var unarmed by remember { mutableStateOf<UnarmedAttack?>(null) }
    // A weapon off chapter 8's table, and which of its attacks is being made:
    // the Combat Knife and the Broadsword each print two. Mutually exclusive
    // with [unarmed] -- a Brawling character punches OR swings the truncheon,
    // and showing both damage lines at once would claim one turn does both.
    var armed by remember { mutableStateOf<Armed?>(null) }
    // Distance and circumstances, kept out of AttackPlan: they describe the
    // TARGET and the scene, not the attacker's choice of maneuver, and only a
    // ranged attack reads them.
    var band by remember { mutableStateOf<RangePenalty?>(null) }
    var circumstances by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Null is the Torso: the book's own default, and the row it prints at +0.
    // A called shot survives the reach/range toggle -- unlike distance and
    // cover, "aiming at the skull" means the same thing with a fist or a gun.
    var location by remember { mutableStateOf<HitLocation?>(null) }
    // A list, not one roll: All-Out Double and Rapid Strike each make more
    // than one attack, and rolling once for them silently drops the rest
    // (Codex). Resolving the whole maneuver here is also what keeps its FP
    // charged once -- rolling the second attack as a fresh maneuver would
    // charge the 2 FP twice.
    var outcomes by remember { mutableStateOf<List<SkillRollOutcome>>(emptyList()) }

    val chosen = picked
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (outcomes.isNotEmpty() || chosen != null) {
                TextButton(
                    onClick = {
                        // Everything the turn chose, including where the
                        // target was standing: inheriting last turn's distance
                        // or cover would modify the next roll invisibly
                        // (Codex).
                        outcomes = emptyList()
                        picked = null
                        plan = AttackPlan()
                        unarmed = null
                        armed = null
                        band = null
                        circumstances = emptySet()
                        location = null
                    },
                ) { Text("Another turn") }
            }
        },
        title = { Text(if (chosen == null) "Attack with which skill?" else "Attack") },
        text = {
            when {
                outcomes.isNotEmpty() -> LazyColumn {
                    items(outcomes) { AttackResult(it) }
                }
                chosen == null -> LazyColumn {
                    items(skills) { entry ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    picked = entry
                                    // The book's first listed attack is the
                                    // ordinary one (Punch, Jab); an unlisted
                                    // skill simply has no damage to show.
                                    unarmed = Damage.attacksFor(entry.key.name).firstOrNull()
                                    // Only when the skill has no unarmed table
                                    // of its own: Brawling has both, and the
                                    // book's own first answer there is a fist.
                                    armed = if (unarmed == null) {
                                        Weapons.forSkill(entry.key).firstOrNull()?.let(::Armed)
                                    } else {
                                        null
                                    }
                                    // A Guns skill opens on a firearm, so the
                                    // attack starts AT RANGE. Left in reach it
                                    // would take All-Out Determined's melee +4
                                    // instead of its ranged +1 and hide the
                                    // distance and Aim rows entirely (Codex).
                                    plan = AttackPlan(isRanged = armed.isFired)
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            Text(entry.key.label(), Modifier.weight(1f))
                            Text("${entry.value}", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                else -> AttackOptions(
                    label = chosen.key.label(),
                    skillLevel = chosen.value,
                    plan = plan,
                    unarmed = unarmed,
                    // Two unrelated bonuses reach damage: All-Out Attack
                    // (Strong)'s +2 and Boxing's for skill above DX. Attacks
                    // composes them so neither can be dropped here. Boxing's
                    // is a property of the bare fist, so a weapon takes only
                    // the maneuver's.
                    damage = armed?.let { Weapons.damageFor(it.weapon, it.mode, st, Attacks.damageBonus(plan, 0)) }
                        ?: unarmed?.let {
                            val fromSkill = if (it.skill == BOXING) Damage.boxingBonus(dx, chosen.value) else 0
                            Damage.forAttack(it, st, Attacks.damageBonus(plan, fromSkill))
                        },
                    attacks = Damage.attacksFor(chosen.key.name),
                    armed = armed,
                    weapons = Weapons.forSkill(chosen.key),
                    band = band,
                    circumstances = circumstances,
                    location = location,
                    onAttack = {
                        unarmed = it
                        armed = null
                        circumstances = circumstances - AIM_ROWS
                    },
                    onArmed = { held ->
                        armed = held
                        // Putting the weapon down goes back to the skill's own
                        // first attack, not to nothing: for Brawling that is
                        // the fist, and leaving both null showed no damage
                        // line at all until Punch was tapped by hand (Codex).
                        unarmed = if (held == null) {
                            Damage.attacksFor(chosen.key.name).firstOrNull()
                        } else {
                            null
                        }
                        // A firearm is fired at range and a sword is not
                        // thrown; the toggle still overrides either way.
                        plan = Attacks.atRange(plan, held.isFired)
                        // Aim is worth the WEAPON's Acc, and distance belongs
                        // to a shot. Carrying either into reach asks
                        // Ranged.modifiers for a number nothing can supply --
                        // the same shape as the distance and cover that
                        // "Another turn" used to leave behind.
                        if (!held.isFired) {
                            band = null
                            circumstances = emptySet()
                        }
                    },
                    onBand = { band = it },
                    // Tapping the chosen location again goes back to the torso
                    // rather than leaving the player stuck with a -9 eye shot.
                    onLocation = { location = if (it == location) null else it },
                    onCircumstance = { name ->
                        circumstances = when {
                            name in circumstances -> circumstances - name
                            // Aiming is one act held for one length of time.
                            // Ranged.modifiers refuses both rows together, so
                            // picking the second must REPLACE the first rather
                            // than build a plan that throws on Roll.
                            name in AIM_ROWS -> circumstances - AIM_ROWS + name
                            else -> circumstances + name
                        }
                    },
                    onPlan = {
                        // Range and circumstances describe a shot; going back
                        // into reach drops them rather than applying a
                        // distance penalty to a punch.
                        if (!it.isRanged) {
                            band = null
                            circumstances = emptySet()
                        }
                        plan = it
                    },
                    onRoll = {
                        // Rolled at the level the exertion started from, then
                        // charged: a character is not yet tired by an effort
                        // they are in the middle of making. The cost still
                        // lands, so the NEXT turn feels it.
                        val mods = conditions(chosen.key) +
                            Attacks.modifiers(plan, chosen.value, unarmed) +
                            rangedModifiers(plan, band, circumstances, armed.aimAcc) +
                            listOfNotNull(location?.let(Wounds::modifier))
                        outcomes = List(Attacks.attackCount(plan)) {
                            SkillRoll.at(
                                label = chosen.key.label(),
                                base = chosen.value,
                                key = chosen.key,
                                source = dice,
                                mods = mods,
                            )
                        }
                        Attacks.fpCost(plan).takeIf { it > 0 }?.let(onSpendFp)
                    },
                )
            }
        },
    )
}

@Composable
private fun AttackOptions(
    label: String,
    skillLevel: Int,
    plan: AttackPlan,
    unarmed: UnarmedAttack?,
    damage: DamageRoll?,
    attacks: List<UnarmedAttack>,
    armed: Armed?,
    weapons: List<CataloguedWeapon>,
    band: RangePenalty?,
    circumstances: Set<String>,
    location: HitLocation?,
    onAttack: (UnarmedAttack) -> Unit,
    onArmed: (Armed?) -> Unit,
    onBand: (RangePenalty) -> Unit,
    onCircumstance: (String) -> Unit,
    onLocation: (HitLocation) -> Unit,
    onPlan: (AttackPlan) -> Unit,
    onRoll: () -> Unit,
) {
    val maxDeceptive = Attacks.maxDeceptive(skillLevel)
    // Scrolls: with a distance band and nine conditions to choose from, the
    // panel is taller than a phone and the Roll button fell off the bottom.
    // An AlertDialog does not scroll its own text slot. Found on device.
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("$label $skillLevel", style = MaterialTheme.typography.titleMedium)
        if (attacks.isNotEmpty()) {
            FlowRow {
                attacks.forEach { option ->
                    FilterChip(
                        selected = unarmed?.attack == option.attack,
                        onClick = { onAttack(option) },
                        label = { Text(option.attack) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            unarmed?.let {
                Text(
                    // Shove prints "Special" and rolls nothing; saying so beats
                    // showing a damage line it does not have.
                    "${it.damage} — ${it.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
        ArmedWith(weapons, armed, skillLevel, onArmed)
        // Chips WRAP rather than scroll sideways: a horizontally scrolling Row
        // inside an AlertDialog never received the drag on a 1080px phone, so
        // three of the five maneuvers -- Strong, Double and Charge -- could not
        // be selected at all. Found on device, not by a test.
        FlowRow {
            AttackManeuver.entries.forEach { option ->
                FilterChip(
                    selected = plan.maneuver == option,
                    // Changing maneuver drops the options the old one allowed:
                    // Heroic Charge outside a charge is refused by Attacks, and
                    // carrying it over silently would throw on Roll.
                    onClick = { onPlan(AttackPlan(maneuver = option, isRanged = plan.isRanged)) },
                    label = { Text(option.printed) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        FilterChip(
            selected = plan.isRanged,
            // Both attack options are melee-only, so switching to range drops
            // them rather than offering a choice the book does not allow.
            onClick = { onPlan(Attacks.atRange(plan, !plan.isRanged)) },
            label = { Text(if (plan.isRanged) "At range" else "In reach") },
            modifier = Modifier.padding(vertical = 8.dp),
        )

        if (plan.isRanged) {
            RangedOptions(band, circumstances, armed, onBand, onCircumstance)
        } else {
            Stepper("Rapid Strike", plan.rapidStrikes, "${Attacks.RAPID_STRIKE_PENALTY} each") {
                onPlan(plan.copy(rapidStrikes = (plan.rapidStrikes + it).coerceAtLeast(0)))
            }
            Stepper(
                "Deceptive",
                plan.deceptive,
                if (maxDeceptive == 0) "skill too low" else "-2 each, max $maxDeceptive",
            ) {
                onPlan(plan.copy(deceptive = (plan.deceptive + it).coerceIn(0, maxDeceptive)))
            }
        }
        CalledShot(location, onLocation)
        if (plan.maneuver == AttackManeuver.CHARGE) {
            FilterChip(
                selected = plan.heroicCharge,
                onClick = { onPlan(plan.copy(heroicCharge = !plan.heroicCharge)) },
                label = { Text("Heroic Charge (2 FP)") },
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        val fp = Attacks.fpCost(plan)
        val total = Attacks.modifiers(plan, skillLevel, unarmed)
            .sumOf { (it.effect as ModEffect.Flat).n }
        val count = Attacks.attackCount(plan)
        val fromRange = rangedModifiers(plan, band, circumstances, armed.aimAcc)
            .sumOf { (it.effect as ModEffect.Flat).n }
        Text(
            "Attack options: ${if (total == 0) "no modifier" else signed(total)}" +
                (if (count > 1) ", $count attacks" else "") +
                if (fp > 0) ", costs $fp FP" else "",
            style = MaterialTheme.typography.bodySmall,
        )
        if (plan.isRanged) {
            Text(
                "Range and cover: ${if (fromRange == 0) "no modifier" else signed(fromRange)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        location?.let {
            Text(
                "Called shot: ${it.target} ${signed(it.penalty)}, ${it.printedMultiplier} — ${it.effect}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            // The multiplier is SHOWN, never applied: damage here is an
            // expression, not a rolled total, so there is no product to round
            // and this book prints no rounding rule to round it by.
            damage?.let { roll ->
                // The guard stays, and the message no longer names a chapter.
                // It used to read "chapter 5 prints no wounding multiplier for
                // pi+" — true of chapter 5 and false of the BOOK, which prints
                // it in Appendix A. Nothing in the committed content reaches
                // this branch today; it is here so an unlabelled type in future
                // content cannot crash the dialog.
                if (!Wounds.knowsType(roll.type)) {
                    "Damage on a hit: ${roll.render()} — this book prints no " +
                        "wounding multiplier for \"${roll.type}\""
                } else {
                    val times = Wounds.multiplierFor(location, roll.type)
                    "Damage on a hit: ${roll.render()}" +
                        if (times == 1.0) "" else ", ${Wounds.render(times)} there"
                }
            } ?: if (unarmed != null) "Rolls no damage; see the note above." else "",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Injury, fatigue and attrition are added when it is rolled.",
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = onRoll) { Text("Roll") }
    }
}

@Composable
private fun AttackResult(outcome: SkillRollOutcome) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(outcome.label, style = MaterialTheme.typography.titleSmall)
        Text(
            "3d6: ${outcome.roll?.total} vs ${outcome.target}",
            style = MaterialTheme.typography.titleLarge,
        )
        if (outcome.target != outcome.baseTarget) {
            Text(
                "base ${outcome.baseTarget}, modified to ${outcome.target}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            outcome.effectiveOutcome?.name?.lowercase()?.replace('_', ' ') ?: "no roll",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * What the character is holding (Ch. 8, p. 80).
 *
 * Shown for any skill the weapon table names -- Knife, Brawling, Axe/Mace,
 * Broadsword and the three Guns specializations. Tapping the selected weapon
 * again puts it down, which is how a Brawling character gets back to a fist
 * after picking up the truncheon.
 *
 * A second chip row appears only for the two weapons that print two attacks:
 * the Combat Knife and the Broadsword can cut or thrust, and the mode changes
 * both the damage and the wounding multiplier a called shot would apply.
 */
@Composable
private fun ArmedWith(
    weapons: List<CataloguedWeapon>,
    armed: Armed?,
    skillLevel: Int,
    onArmed: (Armed?) -> Unit,
) {
    if (weapons.isEmpty()) return
    Text(
        "Weapon",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    FlowRow {
        weapons.forEach { option ->
            FilterChip(
                selected = armed?.weapon?.name == option.name,
                onClick = {
                    onArmed(if (armed?.weapon?.name == option.name) null else Armed(option))
                },
                label = { Text(option.name) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
    armed?.let { held ->
        if (held.weapon.damage.size > 1) {
            FlowRow {
                held.weapon.damage.forEach { mode ->
                    FilterChip(
                        selected = held.mode == mode,
                        onClick = { onArmed(Armed(held.weapon, mode)) },
                        label = { Text(mode.printed) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }
        Text(
            // Parry is the number ParryBlock has had a formula for since M0
            // with no weapon to apply it to. A firearm gets "cannot parry"
            // rather than a 0: parrying at nothing is not parrying badly.
            "Acc ${held.weapon.acc} · reach/range ${held.weapon.range} · " +
                (Weapons.parryFor(held.weapon, skillLevel)
                    ?.let { "parry $it" } ?: "cannot parry") +
                " · ${held.weapon.cost}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        if (held.weapon.notes.isNotBlank()) {
            Text(held.weapon.notes, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Distance and the scene, for a shot (Ch. 5, p. 39-40).
 *
 * The two Aim rows are selectable only with a weapon in hand: their modifier
 * is that weapon's Acc, and the cell says so ("see weapon table"). Without one
 * there is no number, and [Ranged.modifiers] refuses rather than applying
 * zero, so offering the chip would build a plan that throws on Roll.
 */
@Composable
private fun RangedOptions(
    band: RangePenalty?,
    circumstances: Set<String>,
    armed: Armed?,
    onBand: (RangePenalty) -> Unit,
    onCircumstance: (String) -> Unit,
) {
    val bands = remember { Ranged.bands() }
    val conditions = remember { Ranged.conditions() }
    Text("Distance", style = MaterialTheme.typography.titleSmall)
    FlowRow {
        bands.forEach { option ->
            FilterChip(
                selected = band?.printed == option.printed,
                onClick = { onBand(option) },
                label = { Text("${option.printed.substringBefore(" meters")} ${signed(option.penalty)}") },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
    Text(
        "Conditions",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    FlowRow {
        conditions.filterNot { it.weaponAcc }.forEach { option ->
            FilterChip(
                selected = option.condition in circumstances,
                onClick = { onCircumstance(option.condition) },
                label = { Text("${option.condition} ${option.printed}") },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
    val acc = armed.aimAcc
    conditions.filter { it.weaponAcc }.forEach { option ->
        if (acc == null) {
            Text(
                // A FIRED weapon, not merely a held one: the toggle above is
                // the player's, so a knife can be carried into a ranged plan,
                // and the Hand Axe prints Acc 1 -- enough to aim a hatchet for
                // +3 over three turns if the chip were offered (Codex).
                "${option.condition}: ${option.printed} — choose a firearm above.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            FilterChip(
                selected = option.condition in circumstances,
                // One aim, one length of time: Ranged.modifiers refuses both
                // rows together, because each pays the weapon's Acc and two
                // would break the second row's own "max Acc +2 total".
                onClick = { onCircumstance(option.condition) },
                label = { Text("${option.condition} ${signed(acc + option.accBonus)}") },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

/**
 * Calling a body part (Ch. 5, p. 41, Optional).
 *
 * The chip carries only the to-hit cost. Two of the nine multipliers are not
 * one number -- the Neck prints "Cr x1.5 / Cut x2 / Imp x2" -- so the printed
 * cell goes in the summary line below, where it has room to be quoted whole
 * rather than trimmed to whichever number came first.
 *
 * The Torso is offered like any other row: it is the book's own +0 default, and
 * leaving it out would make "no called shot" a state with no chip to return to.
 */
@Composable
private fun CalledShot(location: HitLocation?, onLocation: (HitLocation) -> Unit) {
    val locations = remember { Wounds.locations() }
    Text(
        "Called shot",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    FlowRow {
        locations.forEach { option ->
            FilterChip(
                selected = option == location,
                onClick = { onLocation(option) },
                label = { Text("${option.target} ${signed(option.penalty)}") },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

/**
 * The range and condition modifiers for [plan], or none for a melee attack.
 *
 * Darkness is applied at the gentle end the book prints. Its cell reads "-3 to
 * -9 depending on severity", and the harsher end is the GM's call to make out
 * loud rather than the app's to assume -- [Ranged.modifiers] takes an override
 * for exactly that, and the screen does not yet offer one.
 * ponytail: a stepper here when a GM asks for it.
 */
private fun rangedModifiers(
    plan: AttackPlan,
    band: RangePenalty?,
    circumstances: Set<String>,
    weaponAcc: Int?,
): List<com.pathways.model.Modifier> {
    if (!plan.isRanged) return emptyList()
    // The chosen band is passed whole, never re-looked-up by its lower bound:
    // "100+" begins at 100 and bandFor(100) is the -5 row (Codex).
    return Ranged.modifiers(
        band = band,
        conditions = Ranged.conditions().filter { it.condition in circumstances },
        weaponAcc = weaponAcc,
    )
}

/**
 * The two rows whose modifier is the weapon's Acc.
 *
 * Read off the content rather than spelled out here, so a re-extraction that
 * renames a row cannot leave this holding a string nothing matches -- which
 * would silently stop clearing an aim the character can no longer take.
 */
private val AIM_ROWS: Set<String> by lazy {
    Ranged.conditions().filter { it.weaponAcc }.map { it.condition }.toSet()
}

/**
 * A weapon off the catalogue and which of its attacks is being made.
 *
 * [mode] defaults to the first the book prints, which is the ordinary one --
 * the Combat Knife's cut before its thrust -- matching how an unarmed skill
 * defaults to its first listed attack.
 */
internal data class Armed(val weapon: CataloguedWeapon, val mode: WeaponMode) {
    constructor(weapon: CataloguedWeapon) : this(weapon, weapon.damage.first())
}

/**
 * Whether what is being attacked with is fired at a distance.
 *
 * Null -- an empty hand -- is false: this book prints no thrown weapon and no
 * unarmed attack that reaches past arm's length.
 */
internal val Armed?.isFired: Boolean get() = this != null && Weapons.isRanged(weapon)

/**
 * The Acc to aim with, or null when nothing in hand is fired.
 *
 * Not simply the held weapon's Acc (Codex): the "At range" toggle stays the
 * player's, so a knife can be carried into a ranged plan, and the Hand Axe
 * prints **Acc 1**. Gating on the weapon merely being present let a hatchet
 * be aimed for +1, or +3 over three turns -- a modifier the book gives only
 * to something fired.
 */
internal val Armed?.aimAcc: Int? get() = if (isFired) this?.weapon?.acc else null

@Composable
private fun Stepper(label: String, value: Int, note: String, onStep: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label $value", Modifier.weight(1f))
        Text(note, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onStep(-1) }) { Text("−") }
        TextButton(onClick = { onStep(1) }) { Text("+") }
    }
}

private fun signed(n: Int) = if (n >= 0) "+$n" else "$n"

/** "Guns (Pistol)" where the book specialises, "Brawling" where it does not. */
internal fun SkillKey.label(): String =
    if (specialization == null) name else "$name ($specialization)"
