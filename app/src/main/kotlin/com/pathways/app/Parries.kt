package com.pathways.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.pathways.model.SkillKey
import com.pathways.rules.ActiveDefenses
import com.pathways.rules.DefenseBoost
import com.pathways.rules.Facing
import com.pathways.rules.ParryBlock
import com.pathways.rules.Weapons
import com.pathways.rules.content.CataloguedWeapon
import com.pathways.rules.content.Defense
import com.pathways.rules.ShieldCoverage
import com.pathways.rules.Shields
import com.pathways.rules.SkillDefaults
import com.pathways.rules.UnarmedStyle
import com.pathways.rules.content.Shield

/**
 * The skills the book gives a parry formula of their own (Ch. 5, p. 39).
 *
 * Brawling and Boxing parry at skill/2 + 3 like a weapon; Wrestling is
 * (skill - 3)/2 + 3, with the -3 INSIDE the halving, which is why the three
 * cannot share one branch.
 */
private val UNARMED = mapOf(
    "Brawling" to UnarmedStyle.BRAWLING,
    "Boxing" to UnarmedStyle.BOXING,
    "Wrestling" to UnarmedStyle.WRESTLING,
)

/**
 * The name of the row for the Parry (Unarmed) formula's other operand:
 * "Brawling or DX ÷ 2 + 3". Rendered as "Parry (DX, bare hands)".
 */
internal const val BARE_HANDS = "DX, bare hands"

/**
 * Every unarmed parry row, as (name, style, parry): the styles the sheet
 * holds, then the hand at DX wherever [ParryBlock.bareHandParry] offers it.
 *
 * The STYLE travels with the row, because the Notes cell's rules are
 * per-style: Boxing alone ignores the weapon penalty, and Wrestling's own -3
 * is already inside the number.
 *
 * [dx] is the character's DX as played. The DX row is one of the rows and
 * not a line drawn beside them, so the shot filter, the cumulative penalty
 * and the boost picker reach it without being told to (the design review's
 * HIGH, on a decomposition that built it apart).
 */
internal fun unarmedParries(
    skillLevels: Map<SkillKey, Int>,
    dx: Int,
): List<Triple<String, UnarmedStyle, Int>> {
    val held = skillLevels.entries
        .mapNotNull { (key, level) -> UNARMED[key.name]?.let { Triple(key.name, it, level) } }
        .sortedBy { it.first }
    val brawling = held.filter { it.second == UnarmedStyle.BRAWLING }.maxOfOrNull { it.third }
    val bare = ParryBlock.bareHandParry(dx, brawling)
        ?.let { Triple(BARE_HANDS, UnarmedStyle.BARE_HANDS, it) }
    return held.map { (name, style, level) ->
        Triple(name, style, ParryBlock.unarmedParry(style, level))
    } + listOfNotNull(bare)
}

/**
 * The carried weapons that could parry and have no skill behind them, by
 * name -- none in a master's hand, which wields every one at DX. [armedParries] skips them, and a row that is simply absent reads as
 * an oversight beside a hand that now parries at DX -- so the screen names
 * them and says why.
 */
internal fun unskilledParriers(
    weapons: List<CataloguedWeapon>,
    skillLevels: Map<SkillKey, Int>,
    mastery: SkillDefaults.WeaponMastery?,
    dx: Int,
): List<String> = weapons
    .filter { Weapons.canParry(it) && SkillDefaults.wieldedAt(it, skillLevels, mastery, dx) == null }
    .map { it.name }

/**
 * What the screen says of [unskilledParriers], or null for none: the
 * refusal, which stays open, and the cells behind it, which sit closed in a
 * `RuleNote` as the bare-hand row's reading does.
 *
 * A refusal with the book's reason, where this said "a gap here and not a
 * ruling" (6gz; the design review: keep refusing, say why). The one untrained
 * parry operand the book prints is the unarmed cell's DX, and Brawling's
 * "Default: DX-2" (2540) is not what it prints there, so "default ÷ 2 + 3"
 * is a formula no line gives. Line 424's "attempt most skills" is why the
 * same weapon SWINGS at its default: an attack rolls the skill, and this is
 * a cell. Not argued from a master's "treated as known": that sentence says
 * "no default penalty" in the same breath (5797-5798).
 */
internal fun unskilledParryNote(names: List<String>, cells: List<Defense>): Pair<String, String>? {
    if (names.isEmpty()) return null
    val armed = cells.firstOrNull { it.defense.startsWith("Parry (Armed)") }?.formula ?: return null
    val unarmed = cells.firstOrNull { it.defense.startsWith("Parry (Unarmed)") }?.formula ?: return null
    val line = "No parry offered with ${names.joinToString(", ")}: no skill on this " +
        "sheet wields it, and an untrained armed parry is the GM's call."
    val why = "The Parry (Armed) cell prints \"$armed\" and names no untrained " +
        "operand, where the unarmed cell prints \"$unarmed\": DX itself, not " +
        "Brawling's default. So the book gives an untrained armed parry no number."
    return line to why
}

/** The shield skill Block is rolled off (Ch. 5, p. 37). */
internal const val SHIELD_SKILL = "Shield"

/**
 * A defence with the shield's DB shown as the conditional it is: "7 → 10".
 *
 * The bonus applies only "against attacks from the front or shield side". This
 * KDoc used to add "and this app does not model facing -- the book prints no
 * arc to test against", which was false: the arc is printed in the modifier
 * list this same dialog renders. Facing is now declared and
 * [Shields.coverageAt] decides -- [shield] arrives null when the DB is
 * withheld, so no caller adds it by hand.
 *
 * Both numbers are shown at the SIDE, where the book settles nothing: baking
 * the total into one number would let a GM roll a shielded Dodge against an
 * attack the shield does not face (Codex), and hiding it would withhold a
 * bonus the book may well grant. At the front the book grants it, so the
 * shielded figure is the only one ([shieldPair]; play session 7).
 */
internal fun shieldedTargets(base: Int, shield: Shield?, coverage: ShieldCoverage, everyDefence: Int = 0): List<Int> {
    // What lands on EVERY row is unconditional -- Combat Reflexes' "+1 to all
    // active defenses" and the stun's "-4 to Active Defense" -- so it is
    // folded into the base, where the shield's conditional DB cannot be. Every
    // row goes through this one function for exactly that reason: two places
    // adding the same figure is how these numbers stop reconciling (6ai).
    val own = base + everyDefence
    return shieldPair(own, shield?.let { Shields.withBonus(own, it) }, coverage)
}

/**
 * Block's figures behind [shield]: bare, and with its DB and its own penalty
 * against the attack coming in, so a light shield's "-1 to Block vs. ranged"
 * is the roll against a shot and not a note beside it (Codex). [extra] is
 * what lands on both: the trait term, a paid boost, and Retreat, which the
 * caller passes as 0 for a shot.
 */
internal fun blockTargets(
    shield: Shield,
    shieldSkill: Int,
    coverage: ShieldCoverage,
    isRanged: Boolean,
    extra: Int = 0,
): List<Int> = shieldPair(
    ParryBlock.blockFor(shieldSkill, shieldDb = 0) + extra,
    Shields.blockFor(shield, shieldSkill, isRanged) + extra,
    coverage,
)

/**
 * The figures a row rolls at: [shielded] alone where [coverage] grants the DB,
 * both where it is the GM's call. The one answer for [shieldedTargets] and
 * [blockTargets].
 */
internal fun shieldPair(own: Int, shielded: Int?, coverage: ShieldCoverage): List<Int> = when {
    shielded == null -> listOf(own)
    coverage == ShieldCoverage.COUNTS -> listOf(shielded)
    else -> listOf(own, shielded)
}

/**
 * The shield whose DB the rows may add, which is not always the one Readied.
 *
 * One function rather than the predicate written at each site: the Dodge and
 * Parry rows go through `shieldedTargets()` and the Block row builds its arrow by
 * hand, so the same test written twice is how this codebase's numbers stopped
 * reconciling three times (6ag, 6ai, 6be). A design review named the Block row
 * specifically -- gating only `shieldedTargets()` would have left the bonus standing
 * on the row the change exists for.
 *
 * Null means "add no DB", which the caller must not read as "no shield
 * Readied": at the rear both are null and only [carried] tells them apart.
 */
internal fun shieldForDb(carried: Shield?, facing: Facing): Shield? =
    carried?.takeIf { Shields.coverageAt(facing) != ShieldCoverage.WITHHELD }

/**
 * What a paid boost adds to the row named [label], and nothing to the rest.
 *
 * Both printed sentences say "+2 to ONE defence", so the bonus is keyed on
 * the row the GM named rather than folded into [shieldedTargets] with Combat
 * Reflexes -- which really does say "all active defenses" (6al).
 */
internal fun boostOn(label: String, boost: Pair<DefenseBoost, String>?): Int =
    if (boost?.second == label) ActiveDefenses.BOOST_BONUS else 0

/**
 * The rows a boost may be spent on: exactly the ones rendered above it.
 *
 * A parry withdrawn against a shot is not offered here either, so no payment
 * can buy a defence the book has already refused.
 */
internal fun defenceLabels(
    dodge: Boolean,
    armed: List<Pair<String, Int>>,
    unarmed: List<Triple<String, UnarmedStyle, Int>>,
    block: Boolean,
): List<String> = buildList {
    if (dodge) add("Dodge")
    armed.forEach { add("Parry (${it.first})") }
    unarmed.forEach { add("Parry (${it.first})") }
    if (block) add("Block")
}

/**
 * What a parry row prints: the weapon's or the style's own number, plus every
 * term that moves it.
 *
 * ONE function for both row types on purpose. They differ by exactly one
 * term -- an unarmed style parries a WEAPON at its own penalty, and a
 * catalogue weapon does not -- and a term added to one composition and left
 * out of the other is this project's most repeated defect: one question
 * answered at two sites, taught at one. A reviewer found that deleting
 * `parryPenalty` from the armed rows alone left every test green.
 *
 * No test pins the sum, because a test of `a + b + c` restates it. What
 * stops the drift is that there is one expression; whether the screen calls
 * it with the right arguments is only reachable on a device.
 */
internal fun parryScore(base: Int, penalty: Int, boost: Int, retreat: Int, vsWeapon: Int = 0) =
    base + penalty + vsWeapon + boost + retreat

/**
 * Each carried weapon's parry, at the level [SkillDefaults.wieldedAt] wields
 * it: the best skill that arms it, and no less than DX in a master's hand.
 *
 * Chapter 8's catalogue, never [com.pathways.model.Weapon]: the book prints
 * one formula for every weapon and no per-weapon modifier (see
 * [Weapons.parryFor]), so a catalogue row has nothing a `parryMod` could be
 * set from. [ParryBlock.parryFor] stays for the published sheets, which is
 * the only provenance that prints one -- see its own KDoc.
 *
 * Two rows are skipped, for two different reasons. A weapon no skill of
 * theirs arms, out of a master's hand, has no level to halve, and it stays CARRIED -- possession is
 * not training, and a borrowed broadsword is still in hand. A firearm cannot
 * parry at all, which is not the same as parrying at +0.
 */
internal fun armedParries(
    weapons: List<CataloguedWeapon>,
    skillLevels: Map<SkillKey, Int>,
    mastery: SkillDefaults.WeaponMastery?,
    dx: Int,
): List<Pair<String, Int>> = weapons.mapNotNull { weapon ->
    val level = SkillDefaults.wieldedAt(weapon, skillLevels, mastery, dx) ?: return@mapNotNull null
    Weapons.parryFor(weapon, level)?.let { weapon.name to it }
}
