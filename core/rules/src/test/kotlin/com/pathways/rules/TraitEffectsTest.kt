package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.ModEffect
import com.pathways.model.ModifierSource
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Ch. 3's advantages, where their printed numbers reach a roll. */
class TraitEffectsTest {
    private fun advantage(name: String) = Trait(name, 5, TraitKind.ADVANTAGE)
    private fun skill(attr: ControllingAttribute) =
        SkillKey("Whatever", null, attr, Difficulty.AVERAGE)
    private val per = skill(ControllingAttribute.Per)
    private val ht = skill(ControllingAttribute.Buyable(Attr.HT))
    private val iq = skill(ControllingAttribute.Buyable(Attr.IQ))
    private val spi = skill(ControllingAttribute.Spi)
    private fun etherBodyDamage() = Trait("Ether Body Damage", 10, TraitKind.DISADVANTAGE)
    private val intimidation = SkillKey(
        "Intimidation", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.AVERAGE,
    )
    private fun social(name: String, cost: Int = 10) =
        listOf(Trait(name, cost, TraitKind.DISADVANTAGE))

    // ---- Ether Body Damage: the third controlling-attribute join ----------


    /**
     * **"All SPI-based rolls at -1"** is the controlling-attribute join
     * Alertness makes for Per and Fit for HT, on the one attribute this app
     * scopes a condition to already: [Conditions] keeps Spiritual Attrition on
     * SPI rolls by the same test.
     */
    @Test
    fun `Ether Body Damage lowers an SPI roll and no other`() {
        val traits = listOf(etherBodyDamage())
        assertEquals(ModEffect.Flat(-1), TraitEffects.modifiers(traits, spi).single().effect)
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(traits, per))
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(traits, ht))
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(traits, iq))
    }

    /**
     * **It is a DISADVANTAGE, and the source kind has to say so.**
     * `ModifierSource.FromTrait` maps the flag to a `SourceKind`, which is
     * what a `Cancel` budget is measured against -- so a penalty tagged as an
     * advantage would let a cancel aimed at one bucket eat the other. Nothing
     * reaches that today, which is why it is pinned now rather than found the
     * day something does (6bs's reading, on the first trait to use it).
     */
    @Test
    fun `its penalty is sourced from a disadvantage, not an advantage`() {
        val source = TraitEffects.modifiers(listOf(etherBodyDamage()), spi).single().source
        assertEquals(ModifierSource.FromTrait("Ether Body Damage", advantage = false), source)
    }

    /**
     * **The bare SPI rolls a `SkillKey` cannot reach.** Alertness had to be
     * threaded explicitly into the detection roll because "all Per rolls" is
     * not "all Per-controlled SKILL rolls" (6al); this cell says "all
     * SPI-based rolls" and has the same gap. [spiRollBonus] is what a caller
     * holding an attribute rather than a skill asks.
     */
    @Test
    fun `the bare SPI rolls take it through their own accessor`() {
        assertEquals(-1, TraitEffects.spiRollBonus(listOf(etherBodyDamage())))
        assertEquals(0, TraitEffects.spiRollBonus(listOf(advantage("Fit"))))
    }

    /**
     * **The book draws the skill/no-skill distinction on this very attribute,
     * which is why the bare rolls are evidence rather than assumption.**
     * Chapter 5 prints Spiritual Attrition as "-3 to all SPI-based SKILL
     * rolls" and this cell says "All SPI-based rolls" -- the same one-word
     * difference the book makes between "social skill rolls" and "social
     * rolls", and settles in a bracket of its own (6bt). Pinned because the
     * threading into the detection and divination targets rests on it: the day
     * this cell gains the word "skill", that threading has to be argued again
     * rather than inherited.
     */
    @Test
    fun `the cell says SPI-based rolls, not SPI-based skill rolls`() {
        val cell = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .first { it.name == TraitEffects.ETHER_BODY_DAMAGE }.notes
        assertTrue(
            cell.contains("All SPI-based rolls at -1"),
            "Ether Body Damage's printed scope changed: $cell",
        )
        assertTrue(
            !cell.contains("SPI-based skill rolls"),
            "the cell narrowed to skill rolls, so the bare-roll threading needs re-reading",
        )
    }

    /**
     * **The sentences these numbers were read from, against the copy
     * production reads.** They live in Kotlin because each is a value inside
     * a prose cell, and committing one as a column would make the book look
     * like it printed one -- the split the maneuver table's +4/-4 (6i) and
     * the orthodox -2 (6z) already take. So the pin has to be here.
     */
    @Test
    fun `every applied number is still the one its row prints`() {
        // Both catalogues: Unfit is a drawback, and a pin that could not see
        // it would leave the one number this object reads from that list
        // unguarded.
        val byName =
            (ContentLoader.advantages() + ContentLoader.disadvantages()).associateBy { it.name }
        assertTrue(
            byName.getValue(TraitEffects.ALERTNESS).notes
                .startsWith("+${TraitEffects.ALERTNESS_PER_LEVEL} per level to all Per rolls"),
            byName.getValue(TraitEffects.ALERTNESS).notes,
        )
        assertTrue(
            byName.getValue(TraitEffects.FIT).notes
                .startsWith("+${TraitEffects.FIT_BONUS} to all HT rolls"),
            byName.getValue(TraitEffects.FIT).notes,
        )
        assertTrue(
            byName.getValue(TraitEffects.COMBAT_REFLEXES).notes
                .startsWith("+${TraitEffects.COMBAT_REFLEXES_BONUS} to all active defenses"),
            byName.getValue(TraitEffects.COMBAT_REFLEXES).notes,
        )
        assertTrue(
            byName.getValue(TraitEffects.UNFIT).notes
                .startsWith("-${TraitEffects.UNFIT_PENALTY} to all HT rolls"),
            byName.getValue(TraitEffects.UNFIT).notes,
        )
        // Alertness is the levelled one; the other two are not.
        assertTrue(byName.getValue(TraitEffects.ALERTNESS).perLevel)
        assertTrue(!byName.getValue(TraitEffects.FIT).perLevel)
        // The two levelled skill rows, each with its own printed ceiling. The
        // catalogue agrees they scale, which is what stops a flat reading.
        for (name in listOf(TraitEffects.CHARISMA, TraitEffects.OUTDOORSMAN)) {
            val cell = byName.getValue(name)
            assertTrue(cell.perLevel, name)
            assertTrue(
                cell.notes.contains("${TraitEffects.CAPPED_LEVELS} levels"),
                "$name no longer prints its ceiling: ${cell.notes}",
            )
        }
    }

    /**
     * **Charisma prints two clauses and one of them is a reaction
     * modifier.** This book prints no reaction roll procedure and at least
     * five sources hand one out -- this trait, a wealth level's Status
     * column, Reputation, Appearance and Unsettling Appearance -- so with no
     * printed rule for combining them, 6w established that each is shown
     * beside its source and never summed. It is reported for that reason and
     * not because nothing could be done with it.
     */
    @Test
    fun `Charisma's reaction half is reported and its skill half applied`() {
        val owned = listOf(advantage("${TraitEffects.CHARISMA} 2"))
        val reported = TraitEffects.reported(owned)
        assertEquals(1, reported.size, reported.toString())
        assertTrue(reported.single().printed.contains("reaction rolls"), reported.toString())
        val cell = ContentLoader.advantages().first { it.name == TraitEffects.CHARISMA }.notes
        assertTrue(cell.contains(reported.single().printed), cell)
        // ...while the named skills still take the bonus.
        val key = SkillKey(
            "Leadership", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.AVERAGE,
        )
        assertEquals(ModEffect.Flat(2), TraitEffects.modifiers(owned, key).single().effect)
    }

    // ---- reaching a roll --------------------------------------------------

    /**
     * **Alertness reaches a Per roll and nothing else.** It is on eight of
     * the 23 premades and reached no roll at all until this slice -- the
     * defect class 6h fixed for the trackers. Scoped by controlling
     * attribute, the same test that keeps Spiritual Attrition on SPI rolls.
     */
    @Test
    fun `Alertness lifts a Per roll by its level`() {
        val traits = listOf(advantage("Alertness 2"))
        val mods = TraitEffects.modifiers(traits, per)
        assertEquals(1, mods.size)
        assertEquals(ModEffect.Flat(2), mods.single().effect)
        // ...and touches neither of the other two roll classes.
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(traits, ht))
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(traits, iq))
    }

    @Test
    fun `Fit lifts an HT roll and not a Per one`() {
        val traits = listOf(advantage("Fit"))
        assertEquals(ModEffect.Flat(1), TraitEffects.modifiers(traits, ht).single().effect)
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(traits, per))
    }

    @Test
    fun `a character with neither gets nothing`() {
        val traits = listOf(advantage("Overconfidence"), advantage("Stubborn"))
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(traits, per))
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(traits, ht))
    }

    /**
     * **"+1 to all active defenses" means ALL of them.** Block is the only
     * defence whose formula names a modifier, which makes Block-only the easy
     * wrong reading -- and it would understate a Dodge by one. The same trap
     * a shield's DB already had to avoid (6o).
     */
    @Test
    fun `Combat Reflexes lifts every active defence, not just Block`() {
        val traits = listOf(advantage("Combat Reflexes"))
        assertEquals(1, TraitEffects.defenseBonus(traits))
        assertEquals(0, TraitEffects.defenseBonus(listOf(advantage("Fit"))))
        // It is a defence bonus and NOT a skill bonus: the same character
        // gets nothing on a Per or HT roll from it.
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(traits, per))
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(traits, ht))
    }

    // ---- the balance rows -------------------------------------------------

    /**
     * **A skill NAME is the strongest join there is, and both Balance rows
     * print one.** "+6 to avoid knockdown; **+2 to Acrobatics, Climbing,
     * Piloting**" -- the second clause names three skills outright, where
     * Alertness only names an attribute. It reached nothing, on a trait one
     * premade carries beside a skill it names.
     */
    @Test
    fun `the Balance rows lift the skills they name`() {
        val dx = ControllingAttribute.Buyable(Attr.DX)
        val climbing = SkillKey("Climbing", null, dx, Difficulty.AVERAGE)
        val acrobatics = SkillKey("Acrobatics", null, dx, Difficulty.HARD)
        val perfect = listOf(advantage(TraitEffects.PERFECT_BALANCE))
        val great = listOf(advantage(TraitEffects.GREAT_BALANCE))
        assertEquals(ModEffect.Flat(2), TraitEffects.modifiers(perfect, climbing).single().effect)
        assertEquals(ModEffect.Flat(2), TraitEffects.modifiers(perfect, acrobatics).single().effect)
        assertEquals(ModEffect.Flat(1), TraitEffects.modifiers(great, climbing).single().effect)
        // ...and a skill neither row names gets nothing, whatever its attribute.
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(perfect, per))
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(perfect, ht))
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(great, iq))
    }

    /**
     * **Six cells name a skill, and 6bp took one.** The clause sweep that
     * found Perfect Balance listed seventeen advantages; my triage read one of
     * them. Flexibility says "+3 to Climbing and Escape" and **Pickpocket
     * carries it beside Perfect Balance**, which is the character 6bp was
     * device-verified on -- its Climbing roll was reported there as
     * "modified to 13, trait:perfect balance: 2" when three more were owed.
     * 6bj's lesson for the third time: the sweep survived, the triage did not.
     */
    @Test
    fun `every trait that names a skill lifts it`() {
        val dx = ControllingAttribute.Buyable(Attr.DX)
        val iq = ControllingAttribute.Buyable(Attr.IQ)
        fun key(name: String, attr: ControllingAttribute = dx) =
            SkillKey(name, null, attr, Difficulty.AVERAGE)
        val cases = listOf(
            Triple("Flexibility", key("Climbing"), 3),
            Triple("Flexibility", key("Escape"), 3),
            Triple("Double-Jointed", key("Climbing"), 5),
            Triple("Healer", key("First Aid", iq), 3),
            Triple("Medical Training (Informal)", key("First Aid", iq), 1),
            Triple("Sanctity", key("Theology", iq), 2),
            Triple("Sanctity", key("Religious Ritual", iq), 2),
            Triple("Absolute Direction", key("Navigation", iq), 3),
            // Three more, found from the catalogue's side (6es): every guard
            // above walks the maps, so a cell no map names was checked by
            // nothing. None prints a circumstance.
            Triple("Acute Social Awareness", key("Body Language", iq), 2),
            Triple("Acute Social Awareness", key("Detect Lies", iq), 2),
            Triple("Former Military Officer", key("Leadership", iq), 1),
            Triple("Former Military Officer", key("Tactics", iq), 1),
            Triple("Photographic Instinct", key("Photography", iq), 2),
        )
        for ((trait, skill, bonus) in cases) {
            val mods = TraitEffects.modifiers(listOf(advantage(trait)), skill)
            assertEquals(ModEffect.Flat(bonus), mods.single().effect, "$trait on ${skill.name}")
        }
        // ...and none of them reaches a skill its cell does not name.
        for ((trait, _, _) in cases) {
            assertEquals(
                emptyList<Any>(),
                TraitEffects.modifiers(listOf(advantage(trait)), key("Stealth")),
                trait,
            )
        }
    }

    /**
     * **"All HT rolls", not "all HT-controlled SKILL rolls."** [modifiers] is
     * scoped by a [SkillKey]'s controlling attribute, so Fit reached only
     * skills -- and this app makes exactly one bare HT roll that is not a
     * skill roll, the Major Wound's. The book asks for it by name ("make an
     * immediate HT roll", p. 41-42) and Fit is on six of the 23 premades, so
     * six characters have been rolling it a point low since 6n shipped it.
     *
     * The same gap 6al had to thread for Alertness, whose "all Per rolls"
     * could not reach the detection roll for the same structural reason.
     * Found while checking whether Unfit was reachable at all -- its only
     * carrier owns no HT skill, so the mirror rule would have landed on
     * nothing without this.
     */
    @Test
    fun `Fit lifts the one bare HT roll this app makes`() {
        val fit = listOf(advantage(TraitEffects.FIT))
        assertEquals(TraitEffects.FIT_BONUS, TraitEffects.majorWoundHt(fit).bonus)
        assertEquals(0, TraitEffects.majorWoundHt(emptyList()).bonus)
        // ...and it composes with the knockdown bonus rather than replacing
        // it: one roll, two traits, and a breakdown that has to sum.
        val both = fit + advantage(TraitEffects.HIGH_PAIN_THRESHOLD)
        val effect = TraitEffects.majorWoundHt(both)
        assertEquals(TraitEffects.FIT_BONUS + TraitEffects.KNOCKDOWN_BONUS, effect.bonus)
        assertTrue(effect.line!!.contains(TraitEffects.FIT), effect.line!!)
        assertTrue(effect.line!!.contains(TraitEffects.HIGH_PAIN_THRESHOLD), effect.line!!)
    }

    /**
     * The Major Wound roll moved when `Very Fit` joined the map, and that is
     * the point rather than a side effect.
     *
     * "+2 to all HT rolls" includes the one bare HT roll this app already
     * made, so 6de's addition reaches BOTH that roll and the HP table's two.
     * Pinned here because the slice that added it tested only the new rolls,
     * and a behaviour change nothing asserts is one nobody chose (a review's
     * finding).
     */
    @Test
    fun `Very Fit lifts the Major Wound roll too, and by two`() {
        val veryFit = listOf(advantage(TraitEffects.VERY_FIT))
        val effect = TraitEffects.majorWoundHt(veryFit)
        assertEquals(TraitEffects.VERY_FIT_BONUS, effect.bonus)
        assertTrue(effect.line!!.contains(TraitEffects.VERY_FIT), effect.line!!)
        // Not the same number as Fit's: a test asserting "some bonus" would
        // pass with the wrong row wired.
        assertTrue(TraitEffects.VERY_FIT_BONUS != TraitEffects.FIT_BONUS)
    }

    /**
     * **Unfit is Fit's sentence with the sign flipped**, and it took the HT
     * join above to be reachable at all: Occult Scholar is its only carrier
     * and owns eleven skills, every one IQ-based, so the skill path could
     * never have shown it.
     *
     * It is a DISADVANTAGE, so the modifier carries `advantage = false`.
     * `ModifierSource.FromTrait` maps that to `SourceKind.DISADVANTAGE`, and
     * a `ModEffect.Cancel` budget is measured against `SourceKind` -- so
     * tagging a penalty as an advantage would let a cancel aimed at one bucket
     * eat the other. Nothing reaches that today, which is the 6al precedent
     * for pinning it now rather than finding it later.
     */
    @Test
    fun `Unfit lowers an HT roll the way Fit raises it`() {
        val unfit = listOf(Trait(TraitEffects.UNFIT, 5, TraitKind.DISADVANTAGE))
        assertEquals(-TraitEffects.UNFIT_PENALTY, TraitEffects.majorWoundHt(unfit).bonus)
        assertTrue(TraitEffects.majorWoundHt(unfit).line!!.contains(TraitEffects.UNFIT))
        // The HT-skill path takes it too, so the two rolls cannot disagree.
        val mod = TraitEffects.modifiers(unfit, ht).single()
        assertEquals(ModEffect.Flat(-TraitEffects.UNFIT_PENALTY), mod.effect)
        assertEquals(
            com.pathways.model.ModifierSource.FromTrait(TraitEffects.UNFIT, advantage = false),
            mod.source,
        )
        assertEquals(com.pathways.model.SourceKind.DISADVANTAGE, mod.source.kind)
        // ...and it reaches no roll its cell does not name.
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(unfit, per))
        // Fit's own source is unchanged, which is what makes the flag a real
        // distinction rather than a field nobody sets differently.
        assertEquals(
            com.pathways.model.SourceKind.ADVANTAGE,
            TraitEffects.modifiers(listOf(advantage(TraitEffects.FIT)), ht).single().source.kind,
        )
    }

    /**
     * **The caption names every trait that moved the target.** A screen
     * showing "Roll HT 14" over a sentence crediting one of the two traits
     * that got it there is a breakdown that does not sum -- the invariant 6ag
     * added a 300-case test for, and that 6ai and 6bp each reproduced once.
     * 6bp authored this sentence in rules for that reason; it stayed named
     * after the knockdown alone until a second trait reached the roll.
     */
    @Test
    fun `the major wound caption sums to the target it sits under`() {
        val cases = listOf(
            listOf(advantage(TraitEffects.FIT)),
            listOf(advantage(TraitEffects.HIGH_PAIN_THRESHOLD)),
            listOf(advantage(TraitEffects.FIT), advantage(TraitEffects.HIGH_PAIN_THRESHOLD)),
        )
        for (traits in cases) {
            val effect = TraitEffects.majorWoundHt(traits)
            val named = traits.count { effect.line!!.contains(it.name) }
            assertEquals(traits.size, named, "${effect.line} for $traits")
            // Every contribution is explicitly signed "+n" or "−n", so an
            // ASCII hyphen is never one -- the trailing sentence says
            // "fail-by-5", which a looser pattern reads as a −5 contribution.
            val signed = Regex("[+−]\\d+").findAll(effect.line!!)
                .map { it.value.replace('−', '-').replace("+", "").toInt() }
                .sum()
            assertEquals(effect.bonus, signed, effect.line)
        }
        assertNull(TraitEffects.majorWoundHt(emptyList()).line)
    }

    /**
     * **The two levelled rows, on the premades that carry them.** Fortune
     * Teller prints `Charisma 2` and Hunter-Tracker `Outdoorsman 1`, so the
     * multiplier is not a test-only path: the Fortune Teller's own Public
     * Speaking is owed +2, not +1.
     *
     * My census claimed Journalist carried Charisma and that both rows were
     * level 1. The fixtures say otherwise, and a design review caught it
     * (Codex) -- **a count in prose is a finding** (6as), here about a
     * carrier's identity rather than a number.
     */
    @Test
    fun `the levelled rows multiply their bonus by the level owned`() {
        val iq = ControllingAttribute.Buyable(Attr.IQ)
        fun key(name: String, attr: ControllingAttribute = iq) =
            SkillKey(name, null, attr, Difficulty.AVERAGE)
        val charisma2 = listOf(advantage("${TraitEffects.CHARISMA} 2"))
        assertEquals(
            ModEffect.Flat(2),
            TraitEffects.modifiers(charisma2, key("Public Speaking")).single().effect,
        )
        assertEquals(
            ModEffect.Flat(1),
            TraitEffects.modifiers(listOf(advantage(TraitEffects.CHARISMA)), key("Savoir-Faire"))
                .single().effect,
        )
        val outdoorsman = listOf(advantage("${TraitEffects.OUTDOORSMAN} 1"))
        assertEquals(
            ModEffect.Flat(1),
            TraitEffects.modifiers(outdoorsman, key("Tracking", ControllingAttribute.Per))
                .single().effect,
        )
        assertEquals(
            ModEffect.Flat(3),
            TraitEffects.modifiers(listOf(advantage("${TraitEffects.OUTDOORSMAN} 3")), key("Fishing"))
                .single().effect,
        )
        // A flat row is untouched by a level printed beside it: the Balance
        // rows print no "per level" and must not start scaling.
        assertEquals(
            ModEffect.Flat(2),
            TraitEffects.modifiers(
                listOf(advantage("${TraitEffects.PERFECT_BALANCE} 3")),
                key("Climbing", ControllingAttribute.Buyable(Attr.DX)),
            ).single().effect,
        )
    }

    /**
     * **Both cells print their own ceiling** -- "[max 4 levels]" and "Max 4
     * levels." -- so a fifth level buys nothing. Unreachable from the 23
     * fixtures, whose highest is 2, which is exactly why it is pinned: the
     * permissive path is what the next caller finds (6l).
     */
    @Test
    fun `a levelled row stops at the ceiling its own cell prints`() {
        val iq = ControllingAttribute.Buyable(Attr.IQ)
        fun key(name: String) = SkillKey(name, null, iq, Difficulty.AVERAGE)
        val cap = TraitEffects.CAPPED_LEVELS
        assertEquals(
            ModEffect.Flat(cap),
            TraitEffects.modifiers(listOf(advantage("${TraitEffects.CHARISMA} 9")), key("Streetwise"))
                .single().effect,
        )
        assertEquals(
            ModEffect.Flat(cap),
            TraitEffects.modifiers(
                listOf(advantage("${TraitEffects.OUTDOORSMAN} 9")),
                key("Naturalist"),
            ).single().effect,
        )
    }

    /**
     * **The join is to the BRACKET, not to the phrase above it.** "Influence
     * skills" reads like a category, and this book does print five skill
     * categories as banners -- but Influence is not one of them, and
     * **Streetwise, inside Charisma's own bracket, is not under the SOCIAL
     * banner**. Decisive on its own; and the same phrase encloses a DIFFERENT
     * list one content file over, in a Witch ability that names Diplomacy,
     * Fast-Talk and Sex Appeal. Two enumerations, one phrase, so each cell's
     * bracket is its own list.
     *
     * Pinned here because a later reading that modelled "Influence skills" as
     * a group would quietly widen Charisma onto every social skill and narrow
     * it off Streetwise in the same change.
     */
    @Test
    fun `the same phrase encloses a different list elsewhere in the book`() {
        val elsewhere = ContentLoader.pathways()
            .mapNotNull { Regex("Influence skill rolls \\(([^)]*)\\)").find(it.reinforcedAbilities) }
            .map { it.groupValues[1].split(", ").toSet() }
            .firstOrNull()
        assertTrue(elsewhere != null, "the Witch's Influence bracket is no longer in pathways.json")
        assertTrue(
            elsewhere != TraitEffects.skillsFor(TraitEffects.CHARISMA),
            "one phrase, one list -- the per-cell reading would lose its evidence",
        )
        // Every name either bracket prints is a skill this book prints, which
        // is what makes the name join reach anything at all.
        val printed = ContentLoader.skills().map { it.name }.toSet()
        for (name in TraitEffects.skillsFor(TraitEffects.CHARISMA) + elsewhere!!) {
            assertTrue(name in printed, "$name is named by a bracket and printed by no skill list")
        }
    }

    /**
     * **Two named-skill bonuses ADD.** Pickpocket owns Perfect Balance and
     * Flexibility and buys Climbing; nothing in the book makes two
     * independently purchased advantages exclusive, so a Climbing roll takes
     * both. The literal reading, pinned because the tidy alternative -- taking
     * the better of the two -- is arithmetic the book never asks for.
     */
    @Test
    fun `two traits that name the same skill both apply`() {
        val climbing =
            SkillKey("Climbing", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.AVERAGE)
        val both = listOf(advantage(TraitEffects.PERFECT_BALANCE), advantage("Flexibility"))
        val flats = TraitEffects.modifiers(both, climbing).map { (it.effect as ModEffect.Flat).n }
        assertEquals(5, flats.sum(), "$flats")
        assertEquals(2, flats.size, "each trait contributes its own row: $flats")
    }

    /**
     * **Both rows name Piloting and this book prints no such skill.** The
     * shape "Wrestling Master" already has (6bf) and `Dead Language Fluency`
     * naming "Ancient Loen" before it (6x): a trait naming something no list
     * prints. Recorded, never repaired -- and matching by name means the day
     * the skill appears the bonus reaches it without a second decision.
     */
    @Test
    fun `Piloting is named by both rows and printed by no skill list`() {
        val printed = ContentLoader.skills().map { it.name }.toSet()
        assertTrue("Acrobatics" in printed)
        assertTrue("Climbing" in printed)
        assertTrue("Piloting" !in printed, "the book now prints Piloting; the record is stale")
        assertTrue("Piloting" in TraitEffects.BALANCE_SKILLS)
        for (name in listOf(TraitEffects.PERFECT_BALANCE, TraitEffects.GREAT_BALANCE)) {
            val cell = ContentLoader.advantages().first { it.name == name }.notes
            assertTrue(cell.contains("Acrobatics, Climbing, Piloting"), cell)
        }
    }

    /**
     * **An advantage is what a character IS, not what the GM says this turn.**
     * `ModifierSource.FromTrait` and `Layer.ADVANTAGE` have existed since M0
     * with no production caller, and this is the first -- so tagging these as
     * `Situational` would have put a permanent trait in the same `SourceKind`
     * bucket a Cancel budget is measured against. Nothing can reach that
     * today, which is exactly why it is pinned now (caught in review).
     */
    @Test
    fun `a trait bonus is sourced as a trait, not as a circumstance`() {
        val mod = TraitEffects.modifiers(listOf(advantage("Alertness 1")), per).single()
        assertEquals(
            com.pathways.model.ModifierSource.FromTrait("Alertness", advantage = true),
            mod.source,
        )
        assertEquals(com.pathways.model.SourceKind.ADVANTAGE, mod.source.kind)
        assertEquals(com.pathways.model.Layer.ADVANTAGE, mod.layer)
    }

    /**
     * **Empathy's "+3 to social skill rolls" reaches the banner the book
     * prints.** The join is the CATEGORY, which is a weaker key than a skill
     * name and a much stronger one than a controlling attribute: Fast-Talk is
     * IQ-based and so are Research and Alchemy, so an attribute scope would
     * lift a chemistry roll.
     *
     * The category is read out of the content, never restated here: a second
     * copy of a set the book prints is what 4.1 forbids, and 6bg's forfeit
     * list is the precedent for reading one out of its own file.
     */
    @Test
    fun `Empathy lifts a social skill and nothing else`() {
        val iq = ControllingAttribute.Buyable(Attr.IQ)
        val empathy = listOf(advantage(TraitEffects.EMPATHY))
        val fastTalk = SkillKey("Fast-Talk", null, iq, Difficulty.AVERAGE)
        assertEquals(
            ModEffect.Flat(3),
            TraitEffects.modifiers(empathy, fastTalk).single().effect,
        )
        // Same controlling attribute, a different banner: KNOWLEDGE.
        val research = SkillKey("Research", null, iq, Difficulty.AVERAGE)
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(empathy, research))
    }

    /**
     * **The whole SOCIAL banner, not a sample of it.** A membership test that
     * checks one skill passes for any join that happens to agree there; this
     * one walks all sixteen and asserts every other skill in the book is
     * untouched, which is what fails if the category lookup goes wrong in
     * either direction.
     */
    @Test
    fun `every social skill and no other takes the social bonus`() {
        val empathy = listOf(advantage(TraitEffects.EMPATHY))
        val social = ContentLoader.skills().filter { it.category == TraitEffects.SOCIAL_SKILLS }
        assertEquals(16, social.size, social.map { it.name }.toString())
        for (skill in ContentLoader.skills()) {
            val key = SkillKey(skill.name, null, skill.attr, skill.difficulty)
            val flats = TraitEffects.modifiers(empathy, key)
            val expected = if (skill.category == TraitEffects.SOCIAL_SKILLS) 1 else 0
            assertEquals(expected, flats.size, "${skill.name} (${skill.category}): $flats")
        }
    }

    /**
     * **A conditional social row still reaches nothing UNDECLARED**, which is
     * the half of the old refusal that survives. Callous is "-3 to social
     * skill rolls **when warmth or empathy is required**" and Social Anxiety
     * "-2 ... in groups of 6+"; Barmaid Brawler and Docks Bouncer own Callous
     * beside Intimidation, so a flat penalty charges them for warmth nobody
     * asked for.
     *
     * What changed is that there is a control now, so the trait is no longer
     * [TraitEffects.reported] as unreachable — a disclaimer expires exactly
     * when the code changes (6ba). The rest of that reading is unmoved and is
     * pinned by `a social penalty needs its circumstance declared`.
     */
    @Test
    fun `an undeclared social penalty still reaches no roll`() {
        val iq = ControllingAttribute.Buyable(Attr.IQ)
        val intimidation = SkillKey("Intimidation", null, iq, Difficulty.AVERAGE)
        for (name in listOf("Callous", "Shyness", "Social Anxiety")) {
            val owned = listOf(Trait(name, 5, TraitKind.DISADVANTAGE))
            assertEquals(
                emptyList<Any>(),
                TraitEffects.modifiers(owned, intimidation),
                "$name is conditional and must not reach a roll undeclared",
            )
            // ...and it is no longer disclaimed as unreachable, because it is
            // not: the declaration control shipped with this test.
            assertTrue(
                TraitEffects.reported(owned).none { it.trait == name },
                "$name has a control now and must not still say it has none",
            )
        }
    }

    /**
     * **Clueless is unconditional, and that is why it applies where Callous
     * does not.** Its cell says "-3 to **all** social skill rolls" -- the word
     * "all" is the book strengthening it, where the other three social rows
     * name a circumstance. Neither carrier owns a social skill today, so it
     * reaches a roll once a potion grants one, which is the book's own route
     * (6aq) and a path the app already rolls (6be).
     */
    @Test
    fun `Clueless lowers a social skill roll by three`() {
        val printed = Premades.all()
            .flatMap { it.disadvantages }
            .first { it.name.startsWith("Clueless") }
            .name
        val owned = listOf(Trait(printed, 10, TraitKind.DISADVANTAGE))
        val diplomacy = SkillKey(
            "Diplomacy", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.HARD,
        )
        assertEquals(ModEffect.Flat(-3), TraitEffects.modifiers(owned, diplomacy).single().effect)
    }

    /**
     * Resolving a row is not applying it. "Resistant (specify)" reads "HT
     * rolls to resist a specific category at +3 (3 pts) or +8 (5 pts)", and
     * no roll this app offers resists disease, poison or temperature, so
     * the Major Wound's HT roll must NOT pick this bonus up beside Fit's and
     * Unfit's. Church Healer owns it, so the day it
     * leaked the number would be on a real character's screen.
     */
    @Test
    fun `a category-scoped HT bonus does not reach the major wound roll`() {
        val resistant = listOf(Trait("Resistant (Disease +3)", 3, TraitKind.ADVANTAGE))
        assertEquals(0, TraitEffects.majorWoundHt(resistant).bonus)
        assertNull(TraitEffects.majorWoundHt(resistant).line)
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(resistant, ht))
        // ...and it says so on the sheet rather than going quiet.
        assertTrue(TraitEffects.reported(resistant).any { "+3 (3 pts)" in it.printed })
    }

    // ---- Versatile ---------------------------------------------------------

    /**
     * **This was an UNAPPLIED entry reading "every skill on a sheet is bought,
     * so no default is rolled here".** `Divination.divinationLevelFor` rolls
     * Divination Arts at Ritualistic Magic-2 and the dialog prints "Defaulted
     * from", so that was a claim about the app and a false one.
     */
    @Test
    fun `Versatile lifts a roll made at a default`() {
        val versatile = listOf(advantage(TraitEffects.VERSATILE))
        // The literal, and the cell it was read from: asserting the
        // constant against itself passes with the constant at zero.
        val printed = ContentLoader.advantages().first { it.name == TraitEffects.VERSATILE }.notes
        assertTrue(printed.startsWith("+1 to defaults skill rolls"), printed)
        assertEquals(1, TraitEffects.defaultBonus(versatile))
        assertEquals(0, TraitEffects.defaultBonus(emptyList()))
        // Through to the roll: own 10 ties a plain default and loses to this one.
        val lifted = Divination.divinationLevelFor(
            own = 10, sibling = 12, defaultBonus = TraitEffects.defaultBonus(versatile),
        )!!
        assertEquals(11, lifted.level)
        assertTrue(TraitEffects.VERSATILE !in TraitEffects.unappliedNames())
        assertTrue(TraitEffects.VERSATILE in TraitEffects.known())
        // Applied on every roll this app makes at a default, so there is
        // nothing left to report. Until 6et the sheet said "this app offers no
        // other roll at a default", and the Skills tab's untrained roll made
        // that the second false sentence this one trait has carried.
        assertEquals(emptyList<Any>(), TraitEffects.reported(versatile))
        val untrained = SkillDefaults.offered(emptyMap(), { 10 }, TraitEffects.defaultBonus(versatile))
        assertEquals(6, untrained.single { it.key.name == "Stealth" }.level)
    }

    /**
     * Knows the Acting Method: "all Digestion Gain Per Session is doubled
     * (e.g. Exemplary +15-20% becomes +30-40%)" (6ez). The tracker prints
     * the Acting Method table beside its stepper, so a holder sees the
     * doubled ranges, and the cell's own example is the check. Losing
     * progress is not a gain, so "None" stays a loss.
     */
    @Test
    fun `Knows the Acting Method doubles every printed gain and not a loss`() {
        val cell = ContentLoader.advantages().single { it.name == TraitEffects.ACTING_METHOD }.notes
        assertTrue("all Digestion Gain Per Session is doubled" in cell, cell)
        assertEquals(ContentLoader.actingMethod(), TraitEffects.actingGains(emptyList()))

        val holder = TraitEffects.actingGains(listOf(advantage(TraitEffects.ACTING_METHOD)))
        val exemplary = holder.single { it.quality == "Exemplary" }
        assertEquals(30 to 40, exemplary.minGain to exemplary.maxGain)
        val none = holder.single { it.quality == "None" }
        assertTrue(none.losesProgress)
        assertNull(none.maxGain)
        assertTrue(TraitEffects.ACTING_METHOD in TraitEffects.known())
        assertTrue(TraitEffects.doublesActingGains(listOf(advantage(TraitEffects.ACTING_METHOD))))
        assertTrue(!TraitEffects.doublesActingGains(emptyList()))
    }

    /** One answer for the two bare Per rolls, Detection and Spirit Vision (6fe). */
    @Test
    fun `the Alertness bonus is its level times one`() {
        assertEquals(2, TraitEffects.alertnessBonus(listOf(advantage("Alertness 2"))))
        assertEquals(1, TraitEffects.alertnessBonus(listOf(advantage(TraitEffects.ALERTNESS))))
        assertEquals(0, TraitEffects.alertnessBonus(listOf(advantage("Fit"))))
    }

    /**
     * Who may reroll a failed roll (6fg): Luck, Beckoning Luck unless Fate
     * Debt is held ("Beckoning Luck never applies", line 1449), and the
     * Lucky One's "5 rerolls per session" (line 7766) from Sequence 7 of
     * Wheel of Fortune down, since sequences are cumulative.
     */
    @Test
    fun `a reroll comes from Luck, Beckoning Luck or the Lucky One`() {
        val debt = Trait("Fate Debt", 15, TraitKind.DISADVANTAGE)
        val wheel = { seq: Int -> com.pathways.model.BeyonderState("Wheel of Fortune", seq) }
        assertEquals("Luck", TraitEffects.rerollSource(listOf(advantage("Luck")), null))
        assertEquals("Luck", TraitEffects.rerollSource(listOf(advantage("Luck"), debt), null))
        assertEquals("Beckoning Luck", TraitEffects.rerollSource(listOf(advantage("Beckoning Luck")), null))
        assertNull(TraitEffects.rerollSource(listOf(advantage("Beckoning Luck"), debt), null))
        assertEquals("Lucky One", TraitEffects.rerollSource(emptyList(), wheel(7)))
        assertEquals("Lucky One", TraitEffects.rerollSource(listOf(debt), wheel(6)))
        assertNull(TraitEffects.rerollSource(emptyList(), wheel(8)))
        assertNull(TraitEffects.rerollSource(emptyList(), com.pathways.model.BeyonderState("Fool", 7)))
        assertNull(TraitEffects.rerollSource(listOf(advantage("Fit")), null))
        for (name in listOf("Luck", "Beckoning Luck", "Fate Debt")) {
            assertTrue(name in TraitEffects.known(), name)
        }
    }
}
