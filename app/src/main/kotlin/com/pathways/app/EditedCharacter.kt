package com.pathways.app

import com.pathways.rules.CharmedObject
import com.pathways.model.Attr
import com.pathways.model.Attributes
import com.pathways.model.BASE_ATTRIBUTE
import com.pathways.model.BeyonderState
import com.pathways.model.MAX_SECONDARY_DELTA_MAGNITUDE
import com.pathways.model.SecondaryDelta
import com.pathways.model.Character
import com.pathways.model.PurchasedSkill
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.rules.Appearance
import com.pathways.rules.Corruption
import com.pathways.rules.Derive
import com.pathways.rules.DigestedCharacter
import com.pathways.rules.PotionDigestion
import com.pathways.rules.PremadeSheet
import com.pathways.rules.SecondaryCharacteristics
import com.pathways.rules.InstancedPurchase
import com.pathways.rules.LanguagePurchase
import com.pathways.rules.Languages
import com.pathways.rules.OwnedLanguage
import com.pathways.rules.PurchasableTraits
import com.pathways.rules.PurchaseCeilings
import com.pathways.rules.ScoreFloors
import com.pathways.rules.Secondaries
import com.pathways.rules.SkillLadder
import com.pathways.rules.Weapons
import com.pathways.rules.Wealth
import com.pathways.rules.content.AppearanceLevel
import com.pathways.rules.content.CataloguedWeapon
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.SkillDefinition
import com.pathways.rules.content.WealthLevel

/**
 * Turning a published premade plus a session's edits into a character.
 *
 * The 23 sheets are read-only, so nothing here writes to them: the edits are
 * a delta, and [applyTo] is where the two meet. Every screen that needs the
 * character a player is actually running goes through this, while the
 * `computed vs asPublished` diff keeps using the unedited sheet -- that
 * comparison is about the engine against the book, and a bought skill is
 * neither.
 */
fun SavedEdits.applyTo(base: Character): Character {
    if (isEmpty) return base
    val attributes = base.attributes.copy(
        st = st ?: base.attributes.st,
        dx = dx ?: base.attributes.dx,
        iq = iq ?: base.attributes.iq,
        ht = ht ?: base.attributes.ht,
    )
    return base.copy(
        attributes = attributes,
        // ADDED to what the character already carries, never substituted for
        // it: a potion grant lands in this same field, so a sparse map naming
        // one row must not zero a granted Will (Codex, on the design).
        secondaryDelta = SecondaryCharacteristics.plusSteps(base.secondaryDelta, secondaries),
        traits = chosenTraits(base.traits),
        skills = base.skills + purchases(base),
    )
}

/**
 * This character as PLAYED: [state]'s potions digested on top of what was
 * bought. Null for a mortal, who plays the purchases. The one reading of it --
 * the sheet's vitals, the builder's rows and the floors all ask here, so one
 * save cannot read HP 11 on one screen and 9 on the next (SM-T570).
 *
 * Ask it only of a character inside the floors and ceilings: a purchase at
 * the bound plus a potion's grant does not digest ([shortfallOn]).
 */
fun Character.digestedWith(state: BeyonderState?): DigestedCharacter? =
    PotionDigestion.of(copy(beyonder = state), ContentLoader.pathways())

/**
 * ST, DX, IQ and HT as played: after any potion that raised them. Damage,
 * Boxing's bonus and a Major Wound roll off these, and the gallery row
 * prints them.
 */
fun Character.attributesWith(state: BeyonderState?): Attributes =
    Derive.attributes(this, digestedWith(state))

/** The secondaries as played, by the same rule as [attributesWith]. */
fun Character.secondariesWith(state: BeyonderState?): Secondaries =
    digestedWith(state)?.secondaries ?: Derive.of(this).secondaries

/**
 * What the builder prints under a number a potion or a blessing moved: the
 * builder steps, prices and floors the BOUGHT score, and shows the played
 * one. Null when the two agree. "Bought", not "before potions", because God
 * of Combat moves a mortal's numbers too (6hf).
 */
fun boughtNote(bought: String, played: String): String? =
    if (bought == played) null else "$bought bought"

/** Also the label on a Beyonder's skill rows, which stay on bought levels. */
const val BEFORE_POTIONS = "before potions"

/**
 * How far these edits leave [sheet] below a score floor -- first as BOUGHT,
 * then with the potions of [state] digested. All zero when both hold. See
 * [ScoreFloors].
 *
 * Bought first, and only then digested: a save sold to the bound plus a
 * potion's Will -1 is a [SecondaryDelta] of -101, which digestion would throw
 * on building (Codex). A repair therefore takes two passes, and each one
 * stays in bounds.
 */
fun SavedEdits.shortfallOn(sheet: PremadeSheet, state: BeyonderState?): SecondaryDelta {
    val character = applyTo(sheet.toCharacter())
    val bought = ScoreFloors.shortfall(Derive.of(character).secondaries, character.traits)
    if (bought != SecondaryDelta()) return bought
    val digested = character.digestedWith(state) ?: return bought
    return ScoreFloors.shortfall(digested.secondaries, character.traits)
}

/**
 * The maximum CoR these edits give [sheet] with [state]'s potions digested:
 * Will, +3 with Spiritual Fortitude (book lines 1028, 3059). The one reading
 * of it -- the sheet screen, the pathway screen and the session's refusal all
 * ask here, because a potion can move Will in either direction.
 *
 * Ask it only of edits that keep every floor ([shortfallOn] first): a Will
 * sold to the bound plus Abyss's -1 does not digest.
 */
fun SavedEdits.corCeilingOn(sheet: PremadeSheet, state: BeyonderState?): Int {
    val character = applyTo(sheet.toCharacter())
    val walked = character.digestedWith(state)
    return Corruption.maxFor(walked?.secondaries?.will ?: Derive.of(character).secondaries.will, character.traits)
}

/**
 * These edits held inside what a purchase may reach: each bought attribute
 * between the book's 9 and its [PurchaseCeilings] ceiling, and each bought
 * secondary between -100 steps and its ceiling. The one statement of that
 * range: the setters build through it, and a save made before the ceilings
 * existed is brought inside it on opening -- BEFORE the floors are asked,
 * because lowering ST lowers HP.
 */
fun SavedEdits.withinCeilings(): SavedEdits {
    // The blessings among the bought names are every blessing a character
    // can hold: no premade prints one (BlessingAttributesTest).
    fun Int?.held(attr: Attr) = this?.coerceIn(BASE_ATTRIBUTE, PurchaseCeilings.attribute(attr, traits))
    return copy(
        st = st.held(Attr.ST),
        dx = dx.held(Attr.DX),
        iq = iq.held(Attr.IQ),
        ht = ht.held(Attr.HT),
        secondaries = secondaries.mapValues { (key, steps) ->
            steps.coerceIn(-MAX_SECONDARY_DELTA_MAGNITUDE, PurchaseCeilings.secondarySteps(key))
        },
    )
}

/**
 * These edits with [gap] bought back on each row it names: how a save made
 * before the floors existed is repaired. Starts from the step count
 * [SecondaryCharacteristics.plusSteps] would read, coerced, so the repair is
 * measured against the same number the shortfall was.
 */
fun SavedEdits.boughtBack(gap: SecondaryDelta): SavedEdits {
    if (gap == SecondaryDelta()) return this
    val repaired = ContentLoader.secondaryCosts().map { it.key }.fold(secondaries) { acc, key ->
        val owed = SecondaryCharacteristics.stepsOf(gap, key)
        if (owed == 0) return@fold acc
        val bought = (acc[key] ?: 0).coerceIn(-MAX_SECONDARY_DELTA_MAGNITUDE, MAX_SECONDARY_DELTA_MAGNITUDE)
        if (bought + owed == 0) acc - key else acc + (key to bought + owed)
    }
    return copy(secondaries = repaired)
}

/**
 * These edits as a session opening [sheet] holds them: inside the ceilings,
 * at every floor, and with Will bought back to one above the CoR total.
 * Reads the save and writes nothing: [BeyonderSession] persists the result
 * on opening, and the gallery reads it for a row without opening anything.
 *
 * Bought scores, then digested ones: two passes at most, since a potion
 * lowers only Will and by four at most. Then the CoR ceiling, which the
 * builder used to sell onto the total. Buying Will only raises the ceiling,
 * so no floor moves. Should the purchase ceiling stop the buy-back short,
 * the character stands at maximum CoR and is lost, which is the book's
 * answer and the session's to latch.
 */
fun SavedSession.repairedEdits(sheet: PremadeSheet): SavedEdits {
    val floored = generateSequence(edits.withinCeilings()) { e ->
        e.shortfallOn(sheet, beyonder).takeIf { it != SecondaryDelta() }?.let(e::boughtBack)
    }.last()
    val owed = if (lost || corrupted) 0 else cor + 1 - floored.corCeilingOn(sheet, beyonder)
    if (owed <= 0) return floored
    return floored
        .boughtBack(SecondaryDelta(will = owed.coerceAtMost(MAX_SECONDARY_DELTA_MAGNITUDE)))
        .withinCeilings()
}

/**
 * [printed] with each chosen point-costed level swapped in.
 *
 * The two are independent and cannot collide: [Wealth] matches on its
 * "Wealth: " prefix and [Appearance] on the eight bare level names, which
 * chapter 3's trait lists print none of. Order therefore does not matter, and
 * a level left unchosen leaves whatever the sheet printed alone.
 */
private fun SavedEdits.chosenTraits(printed: List<Trait>): List<Trait> {
    val held = traitsBeforeLanguages(printed)
    return held + ownedLanguages(printed).map { it.toTrait() }
}

/** Everything but the languages, which are priced against this list. */
private fun SavedEdits.traitsBeforeLanguages(printed: List<Trait>): List<Trait> {
    val withBought = traitsBeforeInstanced(printed)
    return withBought + instancedTraits(withBought)
}

/**
 * The languages these edits own on a sheet printing [printed], priced.
 *
 * LAST in the composition, so a Language Talent or Multilingual bought in
 * play is already held when the price is read. The builder's rows, the
 * picker's preview and the Ritual dialog's note all ask here: one reading of
 * what a language costs and whether the book prints it at all.
 */
fun SavedEdits.ownedLanguages(printed: List<Trait>): List<OwnedLanguage> = Languages.owned(
    languages.map { LanguagePurchase(it.language, it.proficiency, it.form) },
    traitsBeforeLanguages(printed),
).mapNotNull { it.second }

/**
 * [bought] as these edits would own it, or null where the book does not print
 * it. Priced in the list it would JOIN, through the same two calls a tap then
 * makes, so the picker cannot print a price the purchase does not bill.
 */
fun SavedEdits.pricedWith(bought: SavedLanguage, printed: List<Trait>): OwnedLanguage? =
    withLanguage(bought).ownedLanguages(printed)
        .firstOrNull { it.purchase.language.equals(bought.language, ignoreCase = true) }

/**
 * These edits with [bought] owned. A language is owned once, so buying an
 * owned one again changes it IN PLACE: the position is what Multilingual's
 * "first two" reads, and a re-pick must not move it.
 */
fun SavedEdits.withLanguage(bought: SavedLanguage): SavedEdits =
    if (languages.none { it.language.equals(bought.language, ignoreCase = true) }) {
        copy(languages = languages + bought)
    } else {
        copy(
            languages = languages.map {
                if (it.language.equals(bought.language, ignoreCase = true)) bought else it
            },
        )
    }

/**
 * The traits bought at a price the book leaves to the instance.
 *
 * Every decision -- whether the book licenses the purchase, what the composed
 * name is, and which purchases repeat -- belongs to
 * [PurchasableTraits.instancedTraits], in one call rather than a predicate
 * this file loops over. A screen and the composition disagreeing about which
 * purchases count is the two-copies-of-one-decision failure this project
 * records six times.
 *
 * [held] is passed so the severity rule can be enforced against what the
 * character already has: a row whose cell enumerates variants admits ONE, so a
 * sheet printing "Shyness (Mild)" must not gain a hand-edited "Severe" beside
 * it. Every other row may repeat -- Monastery Scholar carries two Vows -- and
 * there what a repeat means is the same instance, which that call refuses
 * among the purchases themselves.
 */
private fun SavedEdits.instancedTraits(held: List<Trait>): List<Trait> =
    instancedRows(held).mapNotNull { it.second }

/**
 * Each saved instanced purchase beside the trait it became, or null where the
 * book does not license it.
 *
 * The builder lists these, and it lists them through the SAME call the
 * composition uses: resolving a saved row separately for display let the
 * screen price a purchase the character does not have.
 */
internal fun SavedEdits.instancedRows(
    held: List<Trait>,
): List<Pair<SavedInstancedTrait, Trait?>> {
    val resolved = PurchasableTraits.resolved(
        instanced.map { InstancedPurchase(it.row, it.variant, it.instance, it.cost) },
        held,
    )
    return instanced.zip(resolved.map { it.second })
}

/**
 * The traits a character has before any instanced purchase is considered:
 * what the sheet printed, with the chosen levels swapped in and the
 * fixed-price purchases added. Extracted so the composition and the builder
 * ask the severity rule about the same list.
 */
internal fun SavedEdits.traitsBeforeInstanced(printed: List<Trait>): List<Trait> {
    val withWealth = wealthLevel()?.let { Wealth.applied(printed, it) } ?: printed
    val withLooks = appearanceLevel()?.let { Appearance.applied(withWealth, it) } ?: withWealth
    return withLooks + boughtTraits(withLooks)
}

/**
 * The traits bought in play, resolved against the book.
 *
 * A save stores a NAME, so the cost and kind are re-read every load exactly as
 * a bought skill's difficulty is: a save file must never be able to contradict
 * the catalogue after a content fix. A name the book no longer prints resolves
 * to null and is skipped -- not billed, not applied, and not deleted from the
 * file, because the book may print it again (6as).
 *
 * Duplicates are dropped here as well as refused at the picker, and against
 * the traits ALREADY composed rather than the printed sheet alone: a chosen
 * wealth level or appearance row is in that list by now, and a second copy of
 * something the character has would charge its points and change nothing,
 * since [com.pathways.rules.TraitEffects] reads one canonical match. Two
 * places deciding this is the drift six slices of this project each produced
 * once, so both ask [PurchasableTraits.canBuy].
 */
private fun SavedEdits.boughtTraits(held: List<Trait>): List<Trait> {
    val chosen = mutableListOf<Trait>()
    for (name in traits) {
        val row = PurchasableTraits.named(name) ?: continue
        if (!PurchasableTraits.canBuy(row, held + chosen)) continue
        chosen += charmed(row.toTrait())
    }
    return chosen
}

/**
 * A bought Charmed Object under the name that carries its chosen skill (6hk),
 * and every other trait as it is. A stored skill the book does not print
 * names nothing, so the row stays bare and says no skill is chosen.
 */
private fun SavedEdits.charmed(trait: Trait): Trait {
    val skill = charmedSkill?.takeIf { trait.name == CharmedObject.ROW } ?: return trait
    if (definitionOf(skill.name, skill.specialization) == null) return trait
    return trait.copy(name = CharmedObject.heldAs(skill.name, skill.specialization))
}

/**
 * The levels this character actually rolls at: a potion's grants folded in,
 * the sheet's printed column left out (M3).
 *
 * ONE expression for every screen that needs it. `MainActivity` has built
 * this map since M3 under a comment saying it is *"computed once for
 * defence, attack and the ritual gate rather than three times over"*, and the
 * builder then computed a DIFFERENT one from [Derive] alone — so a Beyonder
 * whose potion granted a weapon skill would read one parry beside the weapon
 * they carry and another on the defence screen, from one save. That is this
 * project's most repeated defect and it arrived in the slice that exists to
 * remove an instance of it.
 *
 * A mortal has no beyonder state, so [PotionDigestion.of] answers null and
 * the purchased levels are the live ones.
 */
fun liveSkillLevels(character: Character): Map<SkillKey, Int> =
    PotionDigestion.of(character, ContentLoader.pathways())?.skillLevels
        ?: Derive.of(character).skillLevels

/**
 * The weapons carried in play, resolved against chapter 8's catalogue.
 *
 * A name the book no longer prints is DROPPED rather than guessed at, the
 * same call a saved trait and a saved skill already take -- and [Weapons
 * .named] is deliberately not used, because it THROWS, and one stale save
 * would take the screen down rather than lose one row.
 *
 * Repeats are folded, by the catalogue's own spelling rather than the save's:
 * the nine printed rows carry nine distinct names, so a second copy is not a
 * second weapon and two identical parry rows would read as a defect. Order is
 * the save's, which is the order the player added them in.
 *
 * These are [CataloguedWeapon]s and never [com.pathways.model.Weapon]s.
 * `Weapon.parryMod` exists because a published SHEET prints a dash or a
 * number beside a weapon row, and this book prints no per-weapon parry
 * modifier anywhere (see [Weapons.parryFor]) -- building one from catalogue
 * data would claim chapter 8 supplied a modifier it never printed, and leave
 * two formulas for one fact agreeing only by construction.
 */
fun SavedEdits.carriedWeapons(): List<CataloguedWeapon> {
    val printed = Weapons.all()
    return weapons
        .mapNotNull { saved -> printed.firstOrNull { it.name.equals(saved, ignoreCase = true) } }
        .distinctBy { it.name }
}

/**
 * The saved appearance level, resolved against the book (Ch. 3, p. 13-14).
 *
 * Dropped rather than guessed at when the book prints no such level, exactly
 * as [wealthLevel] does -- there is no nearest level to fall back on here,
 * because the table prints no ordinary row to fall back TO.
 *
 * There is deliberately no `appearanceShownFor`. Wealth needs one because
 * Average costs 0 pts and leaves no trait to read back, so the choice has to
 * be asked before the character. Every level here costs something, so the
 * composed character always carries the chosen level and reading it is enough.
 */
fun SavedEdits.appearanceLevel(): AppearanceLevel? =
    appearance?.let { name ->
        Appearance.all().firstOrNull { it.level.equals(name, ignoreCase = true) }
    }

/**
 * The saved economic status, resolved against the book (Ch. 8, p. 80).
 *
 * A level the book does not print is DROPPED rather than guessed at, the same
 * way a saved skill it no longer prints is: the nearest wrong answer here is
 * Average, and taking it would move a character's budget by up to 25 points
 * without saying so.
 */
fun SavedEdits.wealthLevel(): WealthLevel? =
    wealth?.let { name -> Wealth.all().firstOrNull { it.status.equals(name, ignoreCase = true) } }

/**
 * The economic status to SHOW for [character]: the level chosen in play, or
 * the one the sheet printed.
 *
 * The choice is asked first because Average costs 0 pts and therefore leaves
 * no trait behind (see [Wealth.traitFor]). Reading the composed character
 * alone would report a player who deliberately picked Average as having no
 * wealth level at all -- and on a sheet that printed Poor, the budget would
 * have moved by 15 points with nothing on screen saying why (Codex).
 *
 * A saved level the book does not print falls through to the printed one,
 * which is what [SavedEdits.applyTo] did with it too.
 */
fun SavedEdits.wealthShownFor(character: Character): WealthLevel? =
    wealthLevel() ?: Wealth.levelOf(character.traits)

/**
 * The level of [name] on this character, or null if they do not have it.
 *
 * Matches on base name only: a caster looking for "Ritualistic Magic" does not
 * care which specialization is written beside it.
 */
fun skillLevelOf(name: String, levels: Map<SkillKey, Int>): Int? =
    levels.entries.firstOrNull { it.key.name.equals(name, ignoreCase = true) }?.value

/**
 * What [key] is rolled at NOW, falling back to [fallback].
 *
 * The published rows carry the diff of the unedited sheet -- the engine
 * against the book (6e). The level ROLLED is a different question, and it
 * was once answered from the same place: a Paragon Sequence 9 Academic
 * Researcher rolled Research at the printed 13 while the digested character
 * stood at 17. Since 6fz the row PRINTS this level too ([PlayedLine]).
 * A potion that raises IQ re-levels every bought IQ skill (M3), so the sheet's
 * own figure is the level of a character who no longer exists.
 *
 * Matched on the BOOK's own identity for a skill: name, specialization and
 * difficulty. Name alone is what made a bought Guns (Rifle) roll the pistol's
 * level (6e, 6as), and name plus specialization is not enough either --
 * p. 85 stacks a grant onto an existing skill only when the name AND the
 * difficulty match, and treats a different difficulty as a different skill.
 * Five skills carry a different difficulty in the potion tables than in
 * chapter 3's list, which is a recorded book disagreement this repo refuses to
 * repair (4.1), so a digested Barmaid Brawler really does hold Knife/E from
 * the sheet and Knife/A from the Abyss potion at once. Two entries, one label,
 * different levels -- and a lookup that stops at the first would answer the
 * published row with the potion's skill (Codex).
 *
 * [fallback] is the level the sheet's own points buy, which is the row's
 * computed side of the diff, so falling back is never a figure from
 * nowhere. No premade needs it: a test walks all 23 down every pathway to
 * Sequence 6 and finds no published skill missing from the digested map.
 */
fun liveLevelOf(key: SkillKey, levels: Map<SkillKey, Int>, fallback: Int): Int =
    liveLevelOrNull(key, levels) ?: fallback

/** [liveLevelOf] without a fallback, for a row that has no printed level. */
fun liveLevelOrNull(key: SkillKey, levels: Map<SkillKey, Int>): Int? =
    levels.entries.firstOrNull { it.key.sameSkillAs(key) }?.value

/**
 * The saved purchases as keys the book recognises, in saved order.
 *
 * A purchase stores a name and points only, so its difficulty is the book's
 * and has to be resolved -- and it must be resolved ONCE, because the bought
 * rows and [grantedSkills] have to agree on which skill a purchase is. When
 * they did not, a saved skill the catalogue no longer prints failed to
 * suppress its own row and a potion-granted skill of the same name appeared
 * twice, as bought and as granted (Codex).
 *
 * A purchase the book does not print today is absent: `purchases` already
 * declines to bill it, so no such row is billed, levelled, or rollable. The
 * saved entry itself is kept, because the book may print that skill again
 * after a content fix (6as).
 */
fun SavedEdits.purchasedKeys(): List<SkillKey> = skills.mapNotNull { saved ->
    definitionOf(saved.name, saved.specialization)?.let { definition ->
        SkillKey(definition.name, saved.specialization, definition.attr, definition.difficulty)
    }
}

/**
 * Whether these name the same skill, by the book's rule (p. 85).
 *
 * The difficulty is part of the identity, not decoration: a grant of the same
 * name at another difficulty is a NEW skill, which is exactly how the five
 * recorded difficulty disagreements come to sit beside their sheet rows.
 */
private fun SkillKey.sameSkillAs(other: SkillKey): Boolean =
    name.equals(other.name, ignoreCase = true) &&
        specialization == other.specialization &&
        difficulty == other.difficulty

/**
 * The skills a potion granted that no row on the sheet screen would show.
 *
 * The screen lists one row per PUBLISHED skill and one per skill BOUGHT in
 * play, and between them they cannot show a skill a potion handed over. That
 * was survivable while a player could buy anything; ch. 9 p. 85 forbids buying
 * the three SPI-based skills (6aq), so for exactly the skills chapter 4 prints
 * result tables for, a grant is the ONLY route and there was no row to tap.
 * 6aj found this hole for bought skills and closing the purchase route
 * re-opened it one list over.
 *
 * A skill already on the sheet is absent from this list rather than doubled:
 * the potion raises the published row (M3's re-levelling), so it is the same
 * row, not a second one.
 *
 * Suppressed by the book's identity for a skill -- name, specialization AND
 * difficulty (see [sameSkillAs]) -- because the five recorded difficulty
 * disagreements make that distinction real. A Barmaid Brawler who walks the
 * Abyss to Sequence 6 is granted Knife/Average beside the Knife/Easy her sheet
 * prints, and p. 85 makes those two different skills at two different levels.
 * Suppressing on the name alone hid the granted one entirely (Codex).
 */
fun grantedSkills(
    sheet: PremadeSheet,
    edits: SavedEdits,
    levels: Map<SkillKey, Int>,
): List<Pair<SkillKey, Int>> {
    val shown = sheet.skills.map { it.key() } + edits.purchasedKeys()
    return levels.entries
        .filterNot { entry -> shown.any { entry.key.sameSkillAs(it) } }
        .map { entry -> entry.key to entry.value }
}

/**
 * The book's definition of [name], or null when it prints no such skill.
 *
 * When a [specialization] is given it must MATCH: five names carry several
 * rows (Guns has four, Navigation three), and falling back to the first would
 * resolve a saved "Guns (Rifle)" as "Guns (Pistol)". A specializable template
 * is the one exception -- its specialization is the player's to pick, so the
 * printed row is the right answer for any of them.
 */
/**
 * [SavedEdits] with purchases the book forbids outright removed.
 *
 * Ch. 9 p. 85 bars buying the three SPI-based skills with points, and 6aq
 * dropped them during COMPOSITION. That left the raw entry in the save file
 * forever, and `SheetScreen` iterates `session.edits.skills` directly for its
 * bought-skill rows -- so once a potion granted the same skill, the stale edit
 * came back as a rollable "bought" row for a purchase that is not legal and is
 * no longer billed.
 *
 * Applied at LOAD, which is where invalid saved input belongs (6e, and the
 * repo's own rule that boundaries validate). **Only the forbidden purchases
 * are dropped, and the difference matters:** a skill the book does not print
 * TODAY may be printed again after a content fix, so `purchases` keeps
 * skipping those non-destructively rather than deleting them from the file.
 * An SPI skill is barred permanently, so there is nothing to preserve.
 */
fun SavedEdits.withoutForbiddenPurchases(): SavedEdits {
    val legal = skills.filter { saved ->
        val definition = definitionOf(saved.name, saved.specialization)
            ?: return@filter true // unknown today, possibly printed again tomorrow
        SkillLadder.isBuyableWithPoints(definition.attr)
    }
    return if (legal.size == skills.size) this else copy(skills = legal)
}

fun definitionOf(name: String, specialization: String? = null): SkillDefinition? {
    val byName = ContentLoader.skills().filter { it.name.equals(name, ignoreCase = true) }
    if (specialization == null) return byName.firstOrNull()
    return byName.firstOrNull { it.specialization == specialization }
        ?: byName.firstOrNull { it.specializable }
}

/**
 * The saved skills as purchases, resolved against the book.
 *
 * A saved skill the book no longer prints is DROPPED rather than guessed at:
 * inventing a controlling attribute and difficulty for it would make up a
 * rule, and billing points for a skill the sheet cannot show would charge for
 * nothing. A skill the premade already owns is also dropped, because
 * [Character] rejects two purchases of one key and those points belong on the
 * existing entry.
 */
private fun SavedEdits.purchases(base: Character): List<PurchasedSkill> {
    val owned = base.skills.map { it.key.name.lowercase() to it.key.specialization }.toSet()
    return skills.mapNotNull { saved ->
        val definition = definitionOf(saved.name, saved.specialization) ?: return@mapNotNull null
        // Ch. 9 p. 85 forbids buying the three SPI-based skills with points.
        // Dropped on LOAD as well as withheld from the picker, because a
        // session saved before that rule was applied would otherwise keep
        // billing for a purchase the book does not allow -- the same path a
        // skill the book no longer prints already takes.
        if (!SkillLadder.isBuyableWithPoints(definition.attr)) return@mapNotNull null
        val key = SkillKey(
            name = definition.name,
            specialization = saved.specialization ?: definition.specialization,
            attr = definition.attr,
            difficulty = definition.difficulty,
        )
        if ((key.name.lowercase() to key.specialization) in owned) null
        else PurchasedSkill(key, saved.points)
    }
}

/** [Attr] as the four nullable fields [SavedEdits] stores. */
fun SavedEdits.levelOf(attr: Attr): Int? = when (attr) {
    Attr.ST -> st
    Attr.DX -> dx
    Attr.IQ -> iq
    Attr.HT -> ht
}

fun SavedEdits.withAttribute(attr: Attr, level: Int): SavedEdits = when (attr) {
    Attr.ST -> copy(st = level)
    Attr.DX -> copy(dx = level)
    Attr.IQ -> copy(iq = level)
    Attr.HT -> copy(ht = level)
}
