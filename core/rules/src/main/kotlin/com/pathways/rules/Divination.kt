package com.pathways.rules

import com.pathways.model.Outcome
import com.pathways.model.RollResult
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.DivinationClarity
import com.pathways.rules.content.DivinationDifficulty
import com.pathways.rules.content.DivinationMethod
import com.pathways.rules.content.DivinationModifier
import com.pathways.rules.content.DivinationScope
import com.pathways.rules.content.DivinationSupport

/**
 * Asking the world a question (Ch. 6.5, Sections I-III).
 *
 * The book's four steps, in order: state the question and take its difficulty
 * modifier, choose a method and pay its SPI, roll Divination Arts with every
 * applicable modifier, and read the clarity off the margin.
 *
 * The same shape as [RitualResolution], and it borrows that object's hardest-
 * won reading: the clarity table has TWO DOORS. Five of its rows are named by
 * margin ("Success by 3-4") and the sixth by OUTCOME ("Critical Failure"), and
 * a margin-first read reports the book's own critical row as an ordinary
 * failure. The outcome is checked first for exactly that reason.
 *
 * A critical failure here is not given effects of its own: the row says "treat
 * as a ritual critical failure", so [RitualResolution.criticalFor] is the
 * second roll and this object carries no duplicate table.
 */
object Divination {

    /** The skill this resolves (SPI/Hard, p. 53). */
    const val SKILL = "Divination Arts"

    /** "cumulative but cap at +2 total from supporting skills" (p. 54). */
    const val MAX_SUPPORT_BONUS = 2

    /**
     * "A Beyonder may specialise in one method, gaining +1 when using it."
     *
     * Read, and deliberately not applied: the reason is the value in
     * [UNAPPLIED], where a sweep over Kotlin finds it. Kept as a constant so
     * the number is extracted once if the app ever gains a place to record
     * which method a diviner trained in.
     */
    const val SPECIALISATION_BONUS = 1

    /**
     * Printed divination rules this app does not apply, each beside its reason.
     *
     * The standard is [ActiveDefenses.UNPAYABLE]'s: a printed rule that reaches
     * nothing says so with its reason, in production source rather than in a
     * journal, so the omission stays a decision instead of becoming an
     * accident. **That buys discoverability in the RULES SOURCE and nothing
     * more** -- removing an entry here turns no test red, adding one does not
     * either, and the screen that renders this map could be deleted with the
     * suite still green, because nothing in this repo tests a composable. The
     * scope is stated rather than assumed: a review's finding, and the
     * alternative was an app-side assertion that cannot reach the render.
     *
     * The value is shown to the PLAYER, so it is one sentence rather than the
     * argument behind it. That argument: the pricing sentence puts it under
     * GURPS Techniques, which this book does not reprint and the builder
     * cannot buy, and nothing in this repo -- no sheet, no potion grant, no
     * saved edit -- records which method a Beyonder trained in. Every other
     * bonus on that screen is gated on something the character demonstrably
     * owns; a chip here would be gated on nothing.
     */
    val UNAPPLIED = mapOf(
        "Specialisation" to
            "the book prices it as a technique (GURPS p. B229), which this book " +
            "does not reprint, and nothing here records which method a diviner " +
            "trained in",
    )

    /** "1 SPI = +1, no cap", spent after seeing the roll (p. 55). */
    const val BURN_PER_SPI = 1

    /** The skill this one defaults from and to (p. 54). */
    const val SIBLING_SKILL = Conditions.RITUAL_SKILL

    /**
     * "Divination Arts defaults to Ritualistic Magic-2 and vice versa"
     * (p. 54).
     *
     * A SPECIFIC default, and it beats the general one:
     * [Difficulty.defaultPenalty] answers -6 for an SPI/Hard skill, which is
     * what a character with NEITHER skill would roll at. This is what one who
     * has the sibling rolls at, and the book prices that at -2.
     */
    const val CROSS_DEFAULT_PENALTY = -2

    /**
     * "A character with one skill at 12+ can attempt the other at -2."
     *
     * A GATE on the source skill, not a rounding: at 11 there is no default
     * at all, and reading the clause as advisory would hand every apprentice
     * the cheap default the sentence conditions.
     */
    const val CROSS_DEFAULT_THRESHOLD = 12

    /**
     * What a caster holding only [siblingLevel] in the other skill rolls this
     * one at -- or null where the book grants no default.
     *
     * Null is a REFUSAL, not a fallback to the general SPI/Hard -6. A caster
     * with Ritualistic Magic at 11 is not a caster with no skill: what the
     * book declines to say is whether they may attempt it at all, and
     * answering -6 would settle a question it left open. The same distinction
     * [Wounds.multiplierFor] draws between a silent cell and a key that is
     * not a damage type.
     *
     * ONE-DIRECTIONAL, despite the sentence's own "and vice versa": this
     * answers what a caster holding Ritualistic Magic attempts DIVINATION at,
     * and nothing else. Ch. 7 II p. 61 says of Ritualistic Magic, "It has no
     * default and cannot be substituted" -- and "cannot be substituted"
     * appears nowhere else in this book, so it is emphasis about this skill
     * rather than boilerplate under every Very Hard blurb. Ch. 2 p. 10 agrees
     * independently: "Very Hard skills ... have no default at all", which is
     * why [Difficulty.VERY_HARD] has carried a null default since M0.
     *
     * Two places say Ritualistic Magic has no default against one trailing
     * clause that says it has; the clause loses (4.1). The asymmetry is not
     * arbitrary either -- it falls out of the difficulty grid, because the
     * direction that survives is exactly the one whose difficulty permits a
     * default at all: Divination Arts is SPI/Hard and defaults at -6
     * generally, which p. 54 improves to -2.
     *
     * [levelsFor] is where the other direction is refused.
     */
    fun crossDefaultFor(siblingLevel: Int): Int? =
        if (siblingLevel >= CROSS_DEFAULT_THRESHOLD) {
            siblingLevel + CROSS_DEFAULT_PENALTY
        } else {
            null
        }

    /**
     * A level to roll one of the pair at, and whether it is the sibling's.
     *
     * [isDefault] is what the screen says out loud, so a caster is never
     * shown a number they do not own without being told where it came from.
     */
    data class SkillOrDefault(
        val level: Int,
        val isDefault: Boolean,
        /**
         * What the caster's OWN skill stands at, or null if they have none.
         *
         * Carried because [isDefault] alone cannot tell the two defaulting
         * cases apart, and they read very differently to a GM: a caster with
         * no divination skill at all, and a Hermit who has one and whose
         * ritual skill simply lends a better number (Codex). A screen given
         * only the flag printed "this character has no Divination Arts of
         * their own" at a character who has it.
         */
        val own: Int? = null,
    )

    /**
     * One sentence saying where [chosen]'s number came from, or null when it
     * is simply the caster's own skill.
     *
     * Authored HERE rather than in the dialog, the call [Detection] already
     * had to make for its attribute line (6ai): a screen writing its own
     * version is a second copy of a decision, and this repo's numbers have
     * twice stopped reconciling that way.
     */
    fun sourceLine(chosen: SkillOrDefault, skill: String, sibling: String): String? {
        if (!chosen.isDefault) return null
        val from = chosen.level - CROSS_DEFAULT_PENALTY
        val quote = "\"Divination Arts defaults to $SIBLING_SKILL" +
            "$CROSS_DEFAULT_PENALTY and vice versa.\""
        return if (chosen.own == null) {
            "Defaulted from $sibling $from. $quote This character has no $skill of their own."
        } else {
            "Defaulted from $sibling $from. $quote Better than their own " +
                "$skill ${chosen.own}, and the book conditions the default on " +
                "the other skill's level, not on lacking this one."
        }
    }

    /**
     * The level a caster rolls DIVINATION at, given what they [own] of
     * Divination Arts and what they own of Ritualistic Magic ([sibling]) --
     * or null if they have neither.
     *
     * There is deliberately no general "either direction" form of this: see
     * [crossDefaultFor] for why [levelsFor] refuses the other one, and note
     * that a single symmetric function is exactly what let ch. 6.5's trailing
     * clause override ch. 7's prohibition without anyone reading ch. 7.
     *
     * **The better of the two wins, and owning the skill is not enough to
     * settle it** (Codex). The sentence conditions on the SOURCE skill's
     * level and says nothing about lacking the target: "A character with one
     * skill at 12+ can attempt the other at -2." A Sequence 9 Hermit is
     * granted Ritualistic Magic at IQ+4 and Divination Arts at SPI+2, so on
     * IQ 10 / SPI 9 they own Divination Arts 11 while their Ritualistic Magic
     * 14 lends 12. Preferring what they own rolls the worse of two numbers
     * the book allows.
     *
     * This is NOT GURPS's "always use the best of skill or default" imported
     * as a convention -- those are GURPS's and this book's own rules win
     * (4.1). It is the printed permission read literally: the caster may
     * attempt it that way, and which of two permitted numbers to use is not
     * a question the book leaves open.
     *
     * A tie keeps the owned skill, because the default adds nothing there and
     * a caster who has the skill should be shown as having it.
     */
    fun divinationLevelFor(own: Int?, sibling: Int?): SkillOrDefault? {
        val defaulted = sibling?.let(::crossDefaultFor)
        return when {
            own != null && (defaulted == null || defaulted <= own) ->
                SkillOrDefault(own, isDefault = false, own = own)
            defaulted != null -> SkillOrDefault(defaulted, isDefault = true, own = own)
            else -> null
        }
    }

    /** What a caster may roll each of the pair at; null where they may not. */
    data class CasterLevels(
        val divination: SkillOrDefault?,
        val ritual: SkillOrDefault?,
    )

    /**
     * Both of a caster's rolls, from the two skills they own.
     *
     * ONE function rather than two calls, because the asymmetry IS the rule
     * and a screen making the second call itself can only be checked by
     * reading it. Here a symmetric implementation fails a test.
     *
     * The ritual half never defaults, from anything. Ch. 7 II p. 61:
     * "Ritualistic Magic (IQ/Very Hard) -- your core skill. It has no default
     * and cannot be substituted. ... Supporting skills provide complementary
     * bonuses but never replace this skill." And ch. 7 is not speaking past
     * Divination Arts when it forbids this -- its own category table sends
     * divination away to the other chapter, "Divination (1-2 SPI via
     * Divination Arts, see Ch 6.5)" (p. 67).
     */
    fun levelsFor(ownDivination: Int?, ownRitual: Int?) = CasterLevels(
        divination = divinationLevelFor(own = ownDivination, sibling = ownRitual),
        ritual = ownRitual?.let { SkillOrDefault(it, isDefault = false, own = it) },
    )

    fun difficulties(): List<DivinationDifficulty> = ContentLoader.divinationDifficulty()
    fun scopes(): List<DivinationScope> = ContentLoader.divinationScopes()
    fun methods(): List<DivinationMethod> = ContentLoader.divinationMethods()
    fun modifiers(): List<DivinationModifier> = ContentLoader.divinationModifiers()
    fun clarities(): List<DivinationClarity> = ContentLoader.divinationClarity()
    fun supportingSkills(): List<DivinationSupport> = ContentLoader.divinationSupport()

    /** The scope named [name]; refused rather than defaulted to the free one. */
    fun scopeNamed(name: String): DivinationScope =
        scopes().firstOrNull { it.scope.equals(name, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "no divination scope named '$name'; the book prints " +
                    scopes().joinToString { it.scope },
            )

    /**
     * What reaching for Divination Arts at this [scope] costs the roll.
     *
     * Zero for the two rows the book prices nothing on, and that zero is a
     * VALUE rather than an absence: "Divination Arts is sufficient" and
     * "Either skill works" are the book saying the substitution is free, not
     * declining to say what it costs. The null this reads is therefore
     * answered here, once, instead of at every call site.
     *
     * NOT composed with the question's difficulty band, which arrives through
     * [targetFor]'s own parameter: the band prices the QUESTION and this
     * prices the SKILL, so a complex question about a hard subject is charged
     * both times. That composition is a reading the book does not print --
     * see this object's UNAPPLIED neighbours and the extractor's verify_scope,
     * which pins the descriptors of the two tables staying disjoint.
     *
     * "Requires Ritualistic Magic or a very high Divination Arts roll" prints
     * no threshold for "very high", so none is enforced: the cross-skill
     * default one line below prints its own ("at 12+"), and inventing one
     * here would be app-authored.
     */
    fun penaltyFor(scope: DivinationScope): Int = scope.modifier ?: 0

    /** The method named [name]; refused rather than defaulted to the cheapest. */
    fun methodNamed(name: String): DivinationMethod =
        methods().firstOrNull { it.method.equals(name, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "no divination method named '$name'; the book prints " +
                    methods().joinToString { it.method },
            )

    /**
     * What the other [skills] the diviner has are worth, capped.
     *
     * The cap is the book's: "These bonuses are cumulative but cap at +2 total
     * from supporting skills." Four skills at +1 each would otherwise be +4,
     * which is two thirds of the hardest question's penalty.
     */
    fun supportBonus(skills: List<DivinationSupport>): Int =
        // Named twice pays once. A character has one Occultism, and a screen
        // that let a row be chosen twice would buy the cap outright -- the
        // guard [RitualMode.supportBonus] already carries for its own five.
        skills.distinctBy { it.skill }.sumOf { it.bonus }.coerceAtMost(MAX_SUPPORT_BONUS)

    /**
     * Which of the book's four supporting skills [owned] covers.
     *
     * **This is 6am's finding one chapter over.** That slice found ch. 7's
     * five supporting skills keyed on their printed string, so
     * "Hidden Lore (relevant domain)" matched nothing and its +1 vanished for
     * the four premades carrying Hidden Lore -- and it fixed the RITUAL half
     * only. Ch. 6.5 prints "Hidden Lore (relevant)" in its own list, which
     * this object has loaded since 6p and offered to nobody: [supportBonus]
     * had no production caller at all, so a diviner with Occultism and
     * Psychology rolled flat where the book gives +2. Five premades carry at
     * least one of the four.
     *
     * The bracket rule is [RitualMode.bareSupportName] rather than a second
     * copy: it is ONE rule -- the book naming a domain the GM judges relevant,
     * not part of a skill's name -- and two copies of one decision is the
     * failure this codebase has produced seven times. Only the ROW's bracket
     * is stripped, so a character's `Hidden Lore (Fae)` still matches, which
     * is what the book asks for: relevance is the GM's call, not a spelling.
     *
     * Lives here rather than in the screen for the reason 6am gives: a caller
     * filtering with its own comparison could offer a chip [supportBonus] then
     * refuses, or withhold one it would accept.
     */
    fun supportSkillsOwnedBy(owned: List<String>): List<DivinationSupport> {
        val bare = owned.map(RitualMode::bareSupportName).toSet()
        return supportingSkills().filter { RitualMode.bareSupportName(it.skill) in bare }
    }

    /**
     * The chosen [rows] as one modifier, with [gmValue] overriding any row
     * that prints a range.
     *
     * A row that carries no number is refused, not applied as zero. There is
     * one -- "Question difficulty / See Step 1 table" -- and it is not a
     * modifier at all: it points back at Step 1, which [targetFor] applies
     * separately. Adding it here would either double the difficulty or, read
     * as zero, silently drop it.
     *
     * [repeats] multiplies the one row that says "per repeat".
     */
    fun modifierTotal(
        rows: List<DivinationModifier>,
        gmValue: Map<String, Int> = emptyMap(),
        repeats: Int = 1,
    ): Int {
        require(repeats >= 1) { "a repeat count starts at 1; was $repeats" }
        return rows.sumOf { row ->
            require(!row.crossReference) {
                "'${row.source}' is Step 1's own table, not a modifier to add beside it"
            }
            val value = gmValue[row.source] ?: row.modifier
                ?: error("'${row.source}' prints no modifier and none was given")
            if (row.best != null) {
                val ends = listOf(row.modifier!!, row.best).sorted()
                require(value in ends[0]..ends[1]) {
                    "'${row.source}' prints ${row.printed}; $value is outside it"
                }
            }
            if (row.perRepeat) value * repeats else value
        }
    }

    /** The number to roll under: skill, the question's difficulty, and [extras]. */
    fun targetFor(skill: Int, difficulty: DivinationDifficulty, extras: Int = 0): Int =
        skill + difficulty.modifier + extras

    /**
     * The clarity row a [roll] lands on.
     *
     * Outcome BEFORE margin. A 17 fails a target of 18 with a positive margin,
     * so reading the margin first would call the book's critical-failure row a
     * "Success by 0-2" -- the slip [FrightCheck] and [RitualResolution] each
     * made once, and the reason this object was written after them.
     *
     * A critical SUCCESS is read by its margin like any other success. The
     * book prints a critical-failure row and no critical-success row, and that
     * omission is deliberate enough to follow: inventing "Crystal clear" for a
     * lucky 4 against a target of 5 would be inventing content.
     */
    fun clarityFor(roll: RollResult, demoted: Boolean = false): DivinationClarity {
        val rows = clarities()
        if (roll.outcome == Outcome.CRITICAL_FAILURE) return rows.first { it.isCritical }
        // A success the target's Sequence Immunity forbids is a failure, and
        // must not keep a success row: "Clear" printed beside the word Failure
        // is the defect [SkillRollOutcome.degree] avoids by reading the
        // effective outcome. Its margin is positive, so it lands on the mildest
        // failure row -- the call FrightCheck already makes for a roll that
        // overshoots its table by nothing.
        val succeeded = !demoted &&
            (roll.outcome == Outcome.SUCCESS || roll.outcome == Outcome.CRITICAL_SUCCESS)
        val side = rows.filter { !it.isCritical && it.isFailure != succeeded }
        val by = if (succeeded) roll.margin else -roll.margin
        // Bands overlap -- a margin of 5 satisfies "0-2", "3-4" and "5+" at
        // once -- so the DEEPEST match wins, as DegreeOfSuccess reads its own
        // ladder. Taking the first would report every success as "Vague".
        return side.filter { (it.margin ?: 0) <= by }.maxByOrNull { it.margin ?: 0 }
        // A 17 fails a target it beat on the dice, so `by` can be negative and
        // match no band at all. The mildest row is the answer there -- the
        // call FrightCheck makes for a roll that overshoots its table by
        // nothing -- and crashing mid-scene is not.
            ?: side.minByOrNull { it.margin ?: 0 }
            ?: error("the clarity table prints no ${if (succeeded) "success" else "failure"} row")
    }

    /**
     * One divination: roll against [skill] for [difficulty], plus [extras].
     *
     * [burned] is SPI spent after seeing the roll to push it -- "1 SPI = +1,
     * no cap" -- and is added to the TARGET rather than subtracted from the
     * total, because the book raises the number you roll under.
     */
    fun attempt(
        skill: Int,
        difficulty: DivinationDifficulty,
        source: RandomSource,
        extras: Int = 0,
        requiresCritical: Boolean = false,
    ): DivinationOutcome = rollAgainst(targetFor(skill, difficulty, extras), source, requiresCritical)

    /**
     * The roll itself, against a [target] already composed by [targetFor] --
     * what a screen that printed the number calls (6eg, 6eh).
     *
     * [requiresCritical] rides with the roll and not with the inputs that
     * composed the number: Sequence Immunity changes how the dice are READ,
     * never what they are rolled against.
     */
    fun rollAgainst(
        target: Int,
        source: RandomSource,
        requiresCritical: Boolean = false,
    ): DivinationOutcome = read(SuccessRoll.against(target, source), 0, requiresCritical)

    /**
     * Whether a burn can still move [outcome] (p. 55).
     *
     * False under Sequence Immunity's critical-only rule, and for the same
     * arithmetic reason [EffortBurn.canPush] gives: a push raises the TARGET
     * and never the total, so no amount of it reaches a rolled 3 or 4. True
     * everywhere else, a critical success included -- the clarity table has
     * no critical row and grades one by margin like any other roll, so a
     * point still buys a clearer answer.
     */
    fun canPush(outcome: DivinationOutcome): Boolean = !outcome.requiresCritical

    /**
     * The same dice read against a target [points] SPI higher (p. 55).
     *
     * "Burning SPI to improve the roll: As with rituals, you may spend
     * additional SPI (1 SPI = +1, no cap) after seeing your result." After is
     * the load-bearing word, and it is why this is a second step rather than
     * an argument to [attempt]: the dice are already cast, so the same total
     * is re-read against a higher number. [spiFor] takes the same points as
     * COST; this takes them as the bonus.
     *
     * [points] is what is spent on THIS call, and [DivinationOutcome.spiBurned]
     * accumulates -- a caller pushing twice has spent both.
     *
     * Returns [outcome] unchanged when [canPush] is false, so a caller that
     * forgets to ask cannot spend SPI on a roll the burn cannot move. SPI's
     * Soul Debt is permanent (6m), which is why that is refused here rather
     * than left to the screen.
     */
    fun pushed(outcome: DivinationOutcome, points: Int): DivinationOutcome {
        require(points >= 0) { "cannot burn a negative number of SPI; was $points" }
        if (points == 0 || !canPush(outcome)) return outcome
        val target = Math.addExact(outcome.roll.target, points * BURN_PER_SPI)
        return read(
            SuccessRoll.evaluate(outcome.roll.total, target),
            Math.addExact(outcome.spiBurned, points),
            outcome.requiresCritical,
        )
    }

    /**
     * What a resolved [roll] means, in one place.
     *
     * [attempt] and [pushed] read the same clarity table off the same roll and
     * must not answer that twice -- the demotion in particular, which is easy
     * to apply on one path and forget on the other.
     */
    private fun read(
        roll: RollResult,
        burned: Int,
        requiresCritical: Boolean,
    ): DivinationOutcome {
        // Sec. 5's "only a critical success (3-4) can succeed" against a Seq 4
        // or Seq 3 target. Not expressible as a penalty, which is the whole
        // point of it -- see [AntiDivination].
        val demoted = requiresCritical && roll.outcome == Outcome.SUCCESS
        return DivinationOutcome(roll, clarityFor(roll, demoted), burned, requiresCritical)
    }

    /**
     * What [method] costs when the GM picks [chosen] inside its printed range,
     * plus anything [burned] to push the roll.
     *
     * Dowsing Rods prints "1-2", and defaulting to the low end while the
     * screen displayed the range charged 1 SPI for a 2 SPI question (Codex).
     * Undercharging matters more than it looks: SPI below zero is Soul Debt,
     * and that corruption is permanent.
     */
    fun spiFor(method: DivinationMethod, chosen: Int = method.spi, burned: Int = 0): Int {
        val most = method.spiMax ?: method.spi
        require(chosen in method.spi..most) {
            "${method.method} costs ${method.spi}..$most SPI; $chosen is outside that"
        }
        require(burned >= 0) { "SPI burned cannot be negative; was $burned" }
        return chosen + burned
    }
}

/**
 * One divination's answer.
 *
 * [clarity] is the row the GM reads out; on a critical failure that row sends
 * them to the ritual Critical Failure Table rather than printing effects of
 * its own, so [isCritical] is what a caller checks before rolling
 * [RitualResolution.criticalFor].
 */
data class DivinationOutcome(
    val roll: RollResult,
    val clarity: DivinationClarity,
    val spiBurned: Int,
    val requiresCritical: Boolean = false,
) {
    val isCritical: Boolean get() = clarity.isCritical

    /**
     * The outcome after Sequence Immunity's critical-only rule.
     *
     * Exposed as a property so a caller cannot read [roll]'s outcome and
     * forget the demotion -- the trap [SkillRollOutcome.effectiveOutcome]
     * exists for one object over.
     */
    val effectiveOutcome: Outcome
        get() = if (requiresCritical && roll.outcome == Outcome.SUCCESS) {
            Outcome.FAILURE
        } else {
            roll.outcome
        }
}
