package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.ModEffect
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

    // ---- the level in the name -------------------------------------------

    /**
     * A sheet prints "Alertness 1"; the catalogue prints "Alertness" and a
     * flag. An unlevelled trait counts as ONE level of itself, never zero --
     * zero would silently cancel its own bonus.
     */
    @Test
    fun `a level printed in the name is read, and its absence means one`() {
        assertEquals(1, TraitEffects.levelOf(listOf(advantage("Alertness 1")), "Alertness"))
        assertEquals(3, TraitEffects.levelOf(listOf(advantage("Alertness 3")), "Alertness"))
        assertEquals(1, TraitEffects.levelOf(listOf(advantage("Fit")), "Fit"))
        assertNull(TraitEffects.levelOf(listOf(advantage("Fit")), "Alertness"))
    }

    /**
     * A name whose last word is not a number keeps it. "Poverty: Struggling"
     * is on ten premades, and reading its tail as a level would invent one.
     */
    @Test
    fun `a trailing word that is not a number is part of the name`() {
        val poverty = Trait("Poverty: Struggling", 10, TraitKind.DISADVANTAGE)
        assertEquals(1, TraitEffects.levelOf(listOf(poverty), "Poverty: Struggling"))
        assertNull(TraitEffects.levelOf(listOf(poverty), "Poverty"))
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

    // ---- printed, real, and reaching nothing -------------------------------

    /**
     * **Shown with the reason, never silently dropped.** A GM who reads why a
     * printed effect reached nothing can apply it themselves; one who sees
     * nothing assumes it was counted. The call the Sealed Artifact grade table
     * (6af) and a technique's availability (6ai) needed.
     */
    @Test
    fun `an unapplied trait is reported with the book's own words`() {
        val notes = TraitEffects.reported(listOf(advantage("Hard to Kill 2")))
        assertEquals(listOf("Hard to Kill 2"), notes.map { it.trait })
        // The levelled one still finds its catalogue row through the level.
        assertTrue(notes[0].printed.contains("HT rolls to avoid death"), notes[0].printed)
    }

    /**
     * **High Pain Threshold's shock clause is APPLIED, so it is not disclaimed.**
     *
     * It sat in the unapplied map reading "this app models no shock penalty to
     * ignore" -- an excuse rather than a reading, since the Major Wound's own
     * success row prints one and this app rolls that table. 6al reasoned that
     * applying the trait would mean inventing a rule to justify reading
     * another; the book printed the rule all along.
     *
     * The guard reads BOTH objects, because a disclaimer expires exactly when
     * the code changes and a test that quotes it cannot notice (6bl).
     */
    @Test
    fun `the shock clause is not reported while Shock applies it`() {
        val tough = listOf(advantage("High Pain Threshold"))
        val notes = TraitEffects.reported(tough)
        if (Shock.penaltyFor(RollKind.SKILL, shocked = true, traits = tough) == 0) {
            assertTrue(notes.none { it.printed.contains("Ignore shock penalties") }, "$notes")
            assertTrue(notes.none { it.reason.contains("no shock penalty") }, "$notes")
        }
        // And the second clause is applied too, so NOTHING is disclaimed: the
        // Major Wound's HT roll decides knockdown on its fail-by-5 row, which
        // is the roll "+3 on HT rolls to avoid knockdown" names (Codex). This
        // slice was one commit from shipping a fresh excuse in place of the
        // one it removed.
        assertEquals(emptyList<TraitNote>(), notes)
        assertEquals(3, TraitEffects.knockdownBonus(tough))
        assertEquals(0, TraitEffects.knockdownBonus(emptyList()))
        val cell = ContentLoader.advantages()
            .first { it.name == TraitEffects.HIGH_PAIN_THRESHOLD }.notes
        assertTrue(cell.contains("+3 on HT rolls to avoid knockdown"), cell)
        // The row that makes it a knockdown roll is still printed.
        assertTrue(
            ContentLoader.majorWound().any { it.effect.contains("Knocked down") },
            "the major wound table no longer knocks anyone down",
        )
    }

    // ---- a sheet that prints the effect beside the name --------------------

    /**
     * **Three premades store a trait's name and the book's own effect cell as
     * ONE string**, because the published sheet printed them on one line:
     * `"Healer - +2 to all rolls to diagnose, treat, and heal; ..."`. Nothing
     * resolved those to a catalogue row, so every clause they carry was
     * invisible -- and `Charity` prints a self-control number, which means the
     * roll 6am shipped could not be made on the character that owns it.
     *
     * **The tail is not a guess: it must equal the catalogue row's own
     * notes.** That is what separates this from the unmatched spellings 6am
     * recorded ("Absent-Minded" against "Absent-Mindedness"), where the only
     * evidence is resemblance. Here the sheet quotes the book verbatim.
     */
    @Test
    fun `a name printed beside the book's own effect resolves to that row`() {
        val healer = ContentLoader.advantages().first { it.name == "Healer" }
        val glued = Trait("Healer - ${healer.notes}", 15, TraitKind.ADVANTAGE)
        assertEquals("Healer", TraitEffects.catalogueName(glued.name))
        assertEquals(1, TraitEffects.levelOf(listOf(glued), "Healer"))
        // ...and the whitespace the sheet wrapped it with does not matter.
        val wrapped = Trait("Healer - ${healer.notes.replace(" ", "\n")}", 15, TraitKind.ADVANTAGE)
        assertEquals("Healer", TraitEffects.catalogueName(wrapped.name))
    }

    /**
     * A tail that is NOT the catalogue's own words stays unmatched, so the
     * fixtures' other glued names are still recorded rather than guessed at:
     * "Sense of Duty (Flock)" is a `(specify)` instantiation and "Blacklisted:
     * Specific Industry org or church" is a spelling this book never prints.
     */
    @Test
    fun `a tail that is not the book's own words is left unmatched`() {
        for (name in listOf(
            "Healer - something the book never says",
            "Sense of Duty (Flock) - Must help/protect certain groups",
            "Blacklisted: Specific Industry org or church",
        )) {
            assertEquals(name, TraitEffects.catalogueName(name), name)
        }
        // A level in the name is still a level, not a glued effect.
        assertEquals("Alertness", TraitEffects.catalogueName("Alertness 2"))
        // And a colon-suffixed name is part of the name (6al).
        assertEquals("Poverty: Struggling", TraitEffects.catalogueName("Poverty: Struggling"))
    }

    /**
     * The resolution is SHARED, which is the whole point: a trait that is
     * applied must also be reported, or a screen shows a bonus with no
     * explanation beside a cell that vanished. Two matchers is the
     * two-copies-of-one-decision failure (6ag, 6ai, 6be, 6bp).
     */
    @Test
    fun `a glued name is reported as well as resolved`() {
        val row = ContentLoader.advantages().first { it.name == TraitEffects.RAPID_RECOVERY }
        val glued = Trait("${TraitEffects.RAPID_RECOVERY} - ${row.notes}", 5, TraitKind.ADVANTAGE)
        val notes = TraitEffects.reported(listOf(glued))
        assertEquals(1, notes.size, "the glued trait reported nothing: $notes")
        // The row prints the effect cell underneath, so leaving it inside the
        // NAME printed the same sentence twice -- which only the device could
        // show, since every assertion about lookup and clause was already
        // green. The sheet's own name survives; its effect does not.
        assertEquals(TraitEffects.RAPID_RECOVERY, notes.single().trait)
        assertEquals(glued.name, TraitEffects.displayName(glued.name) + " - " + row.notes)
        assertEquals(row.notes, notes.single().printed)
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
     * **Healer prints two bonuses and only the specific one is applied.**
     * "+2 to all rolls to diagnose, treat, and heal; **+3 to First Aid
     * specifically**" -- a First Aid roll IS a roll to treat, so reading them
     * as stacking would charge +5 on the one roll the cell singles out. The
     * word "specifically" is what settles it: the +3 is First Aid's whole
     * bonus.
     *
     * The +2 reaches nothing, because "rolls to diagnose, treat, and heal"
     * names a PURPOSE and this book tags no skill as a healing skill --
     * Physician, Diagnosis, Surgery, Herbal Medicine and Pharmacy are all
     * plausibly among them and the book never says which count. The refusal
     * `Languages` makes for "language-dependent skill rolls" (6x), and it is
     * reported rather than dropped.
     */
    @Test
    fun `Healer's general bonus is reported and its specific one applied`() {
        val iq = ControllingAttribute.Buyable(Attr.IQ)
        val healer = listOf(advantage("Healer"))
        val firstAid = SkillKey("First Aid", null, iq, Difficulty.EASY)
        assertEquals(ModEffect.Flat(3), TraitEffects.modifiers(healer, firstAid).single().effect)
        // Not +5: the specific bonus is not laid on top of the general one.
        val physician = SkillKey("Physician", null, iq, Difficulty.HARD)
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(healer, physician))
        val notes = TraitEffects.reported(healer)
        assertTrue(
            notes.any { it.printed.contains("diagnose, treat, and heal") },
            "the unapplied clause is not reported: $notes",
        )
        assertTrue(
            notes.none { it.printed.contains("First Aid") },
            "the applied clause must not be disclaimed: $notes",
        )
    }

    /**
     * **The close-quarters clause reaches nothing, and that was checked rather
     * than assumed.** 6bo found that a near-identical excuse for High Pain
     * Threshold was FALSE -- the book did print a shock penalty, on a table
     * this app already rolls. So this one was grepped: "close-quarters" occurs
     * exactly twice in the book, both inside these two trait cells. There is
     * no such penalty to ignore.
     */
    @Test
    fun `the close-quarters clause names a penalty this book never prints`() {
        for (name in listOf("Flexibility", "Double-Jointed")) {
            val cell = ContentLoader.advantages().first { it.name == name }.notes
            assertTrue(cell.contains("close-quarters penalties"), cell)
            val notes = TraitEffects.reported(listOf(advantage(name)))
            assertTrue(
                notes.any { it.printed.contains("close-quarters") },
                "$name's unapplied clause is not reported: $notes",
            )
        }
    }

    /**
     * **Every clause of a trait this object touches is either applied or
     * reported.** A reviewer caught two that were neither: Absolute
     * Direction's "Always know which way is north" and Double-Jointed's "any
     * body part bends any way" -- both real, both printed, both silently gone
     * while their siblings on the same cell were handled. That is worse than
     * an untouched trait, because the sheet then shows the cell as understood.
     *
     * The guard is general rather than two patches: it walks the catalogue's
     * own segmentation for every mapped trait. Only lead-in prose that grants
     * nothing is allowed to be absent, and it is named here so the exemption
     * cannot quietly widen.
     */
    @Test
    fun `no clause of a handled trait goes unmentioned`() {
        // Prose that grants nothing, named one sentence at a time so the
        // exemption cannot quietly widen into "anything I did not handle".
        val prose = setOf(
            "Your connection to the divine is unusually clear",
            "Unfit for sustained physical exertion",
        )
        val catalogue = ContentLoader.advantages() + ContentLoader.disadvantages()
        val applied = TraitEffects.skillBonusNames() + TraitEffects.htRollNames()
        for (trait in applied) {
            val cell = catalogue.first { it.name == trait }.notes
            val reported = TraitEffects.reported(listOf(advantage(trait))).map { it.printed }
            val segments = cell.split(';', '.').map { it.trim() }.filter { it.isNotEmpty() }
            for (segment in segments) {
                val cap = TraitEffects.maxLevelsFor(trait)
                val handled = segment in prose ||
                    // the applied clause names the skills it lifts
                    TraitEffects.skillsFor(trait).any { segment.contains(it) } ||
                    // ...or, for Fit and Unfit, names the roll class instead:
                    // "to all HT rolls" is the clause htRollBonus applies.
                    (trait in TraitEffects.htRollNames() && segment.contains("HT rolls")) ||
                    // ...and a levelled row's own ceiling, which is applied
                    // rather than reported, so it is not a dropped clause.
                    (cap != null && segment.contains("$cap levels")) ||
                    reported.any { it.contains(segment) || segment.contains(it) }
                assertTrue(handled, "$trait drops the clause \"$segment\"")
            }
        }
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
     * **The knockdown half is reported while the skill half is applied**, which
     * is the shape `Fit` already has: a cell can hold one rule this app can
     * reach and one it cannot.
     *
     * The refusal is deliberate. High Pain Threshold says "+3 on **HT rolls**
     * to avoid knockdown" and the Major Wound roll is one; the Balance rows
     * name no attribute at all, and this book prints two OTHER knockdown
     * mechanics that are DX-based (Shove's "target rolls DX or fall",
     * Takedown's opposed DX) plus three hit-location rolls whose attribute it
     * never states. Applying an unscoped bonus to the Major Wound roll would
     * also buy better shock and stun rows, which that clause never paid for.
     */
    @Test
    fun `the Balance knockdown clause is reported and reaches no roll`() {
        val perfect = listOf(advantage(TraitEffects.PERFECT_BALANCE))
        val notes = TraitEffects.reported(perfect)
        assertEquals(1, notes.size, "$notes")
        assertTrue(notes.single().printed.contains("+6 to avoid knockdown"), "$notes")
        // The applied half is NOT disclaimed, or the report would be false.
        assertTrue(notes.none { it.printed.contains("Acrobatics") }, "$notes")
        // And it reaches the one knockdown roll this app makes: nothing.
        assertEquals(0, TraitEffects.knockdownBonus(perfect))
        assertEquals(0, TraitEffects.knockdownBonus(listOf(advantage(TraitEffects.GREAT_BALANCE))))
    }

    /**
     * The two knockdown traits no premade carries are reported whole, the way
     * `Danger Sense` already is. Iron Jaw is scoped to "the head", which only a
     * hit-location roll could supply, and Rapid Recovery is a clock (6ad, 6ah,
     * 6an).
     */
    @Test
    fun `the head and recovery traits are reported whole`() {
        for (name in listOf(TraitEffects.IRON_JAW, TraitEffects.RAPID_RECOVERY)) {
            val notes = TraitEffects.reported(listOf(advantage(name)))
            assertEquals(1, notes.size, "$name: $notes")
            assertEquals(name, notes.single().trait)
        }
    }

    /**
     * **The caption is authored here, not by the screen.** `DamageDialog` spelt
     * "includes +3 from High Pain Threshold" itself, so widening the bonus to
     * another trait would have named the wrong one -- the two-copies-of-one-
     * decision failure this codebase produced three times (6ag, 6ai, 6be).
     */
    @Test
    fun `the knockdown line names the trait that paid for it`() {
        val effect = TraitEffects.majorWoundHt(listOf(advantage("High Pain Threshold")))
        assertTrue(effect.line!!.contains(TraitEffects.HIGH_PAIN_THRESHOLD), effect.line!!)
        assertTrue(effect.line!!.contains("+3"), effect.line!!)
        // The Balance rows print their own knockdown clause and reach this
        // roll with nothing, because it names no attribute (6bp).
        assertNull(TraitEffects.majorWoundHt(listOf(advantage(TraitEffects.PERFECT_BALANCE))).line)
        assertNull(TraitEffects.majorWoundHt(emptyList()).line)
    }

    /**
     * A WHOLLY applied trait is not also reported: it would read as unhandled.
     *
     * Alertness is the only one of the three. Its cell prints one rule and
     * that rule has a join, so there is nothing left over to say.
     */
    @Test
    fun `a wholly applied trait is absent from the reported list`() {
        assertEquals(emptyList<TraitNote>(), TraitEffects.reported(listOf(advantage("Alertness 1"))))
    }

    /**
     * **A notes cell can hold several rules, and this app applies one of
     * them.** Combat Reflexes prints three -- "+1 to all active defenses,
     * +6 to recover from stun, never freeze in surprise" -- and only the
     * first has a roll here to land on. Fit prints two and only the first
     * does.
     *
     * `reported` keyed on the trait NAME, and both traits are applied, so it
     * returned nothing for either: the remaining clauses were not merely
     * unenforced but INVISIBLE. That is the whole point of reporting -- "a GM
     * who sees nothing assumes it was counted" -- and it failed exactly where
     * the trait looked handled.
     *
     * **Not 6ai restated.** That sweep asked whether a FIELD has a consumer;
     * `notes` has one. This is clause-level drift inside a field that already
     * has a production reader, which no sweep in this repo could see.
     */
    @Test
    fun `a partly applied trait reports the clauses that reach nothing`() {
        val notes = TraitEffects.reported(listOf(advantage("Combat Reflexes"), advantage("Fit")))
        assertEquals(
            listOf("Combat Reflexes", "Fit"),
            notes.map { it.trait },
        )
        assertEquals(
            listOf(
                // "never freeze in surprise" was here until the surprise
                // refusal shipped (6bg) and the stun penalty followed it. What
                // is left is the RECOVERY roll, which needs a clock.
                "+6 to recover from stun",
                "recover FP at twice the normal rate",
            ),
            notes.map { it.printed },
        )
        // The applied clause is never reported -- it is not left over.
        assertTrue(notes.none { it.printed.contains("active defenses") }, notes.toString())
        assertTrue(notes.none { it.printed.contains("HT rolls") }, notes.toString())
    }

    /**
     * The clauses are quoted, so they have to be the book's own words.
     *
     * A reported clause the catalogue does not print would be this object
     * inventing a rule to disclaim. Pinned against `ContentLoader`, which is
     * the copy production reads.
     */
    @Test
    fun `every reported clause is one the book actually prints`() {
        val catalogue =
            (ContentLoader.advantages() + ContentLoader.disadvantages()).associateBy { it.name }
        for (trait in listOf("Combat Reflexes", "Fit")) {
            val printed = catalogue.getValue(trait).notes
            for (note in TraitEffects.reported(listOf(advantage(trait)))) {
                assertTrue(printed.contains(note.printed), "${note.printed} not in: $printed")
            }
        }
    }

    /**
     * A trait is wholly unapplied or partly applied, never recorded as both.
     *
     * Both maps feed one list, so an overlap would report the same trait
     * twice under two different explanations -- the two-copies-of-one-decision
     * failure this repo's screens produced three times (6ag, 6ai, 6be).
     */
    @Test
    fun `a trait is either wholly unapplied or partly, never both`() {
        val both = TraitEffects.unappliedNames() intersect TraitEffects.partlyAppliedNames()
        assertEquals(emptySet<String>(), both)
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
     * Every trait this object names is one the book actually prints. A typo
     * would make a row unreachable in silence -- and `reported` resolves its
     * text through the catalogue, so a name that misses matches nothing.
     */
    @Test
    fun `every trait named here is one the book prints`() {
        val printed = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .map { it.name }.toSet()
        assertEquals(emptySet<String>(), TraitEffects.known() - printed)
    }
}
