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
    private fun fixed(face: Int) = RandomSource { face }
    private fun skill(attr: ControllingAttribute) =
        SkillKey("Whatever", null, attr, Difficulty.AVERAGE)

    private val per = skill(ControllingAttribute.Per)
    private val ht = skill(ControllingAttribute.Buyable(Attr.HT))
    private val iq = skill(ControllingAttribute.Buyable(Attr.IQ))
    private val spi = skill(ControllingAttribute.Spi)

    // ---- Ether Body Damage: the third controlling-attribute join ----------

    private fun etherBodyDamage() = Trait("Ether Body Damage", 10, TraitKind.DISADVANTAGE)

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
        // This used to read "Hard to Kill 2", which 6de applied: the HP
        // table's death roll now exists, so its refusal expired and it left
        // the map. No unapplied trait is levelled any more, and the levelled
        // resolution it used to pin here lives in `VitalRollsTest` instead,
        // against the accessor that now reads the level.
        val notes = TraitEffects.reported(listOf(advantage("Danger Sense")))
        assertEquals(listOf("Danger Sense"), notes.map { it.trait })
        assertTrue(notes[0].reason.contains("GM"), notes[0].reason)
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
     * A tail that is NOT the catalogue's own words stays unmatched, so
     * "Blacklisted: Specific Industry org or church" -- a spelling this book
     * never prints, whose qualifier paraphrases the row rather than quoting
     * it -- is still recorded rather than guessed at. The truncated Sense of
     * Duty below is the near-miss: its tail stops short of the row's own
     * words, and only the COMPLETE quotation licenses that bracket.
     */
    @Test
    fun `a tail that is not the book's own words is left unmatched`() {
        for (name in listOf(
            "Healer - something the book never says",
            "Blacklisted: Specific Industry org or church",
            // The near-miss: a bracket this app DOES read, behind a tail that
            // stops short of the row's own words. Only the complete quotation
            // licenses it, so a partial one is a disagreement to record.
            "Sense of Duty (Flock) - Must help/protect certain groups",
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
            // Ether Body Damage's lead-in is backstory pointing at Chapter 5,
            // not a rule: it says where the damage came FROM.
            "Prior spirit body damage (see Spirit Body Damage, Chapter 5) that never fully healed",
            // The four the CATEGORY_BONUSES rows carry. Each is a description
            // of what the trait FEELS like and grants nothing on its own; the
            // clause beside it names the banner and is applied.
            "Sense emotions",
            "generally miss social cues and subtlety",
            "You do not understand subtext, sarcasm, or implication",
            "Expert in mysticism",
            // Acute Social Awareness's tail: what the +2 looks like at the
            // table, with no rule of its own.
            "You read a room before you enter it",
        )
        val catalogue = ContentLoader.advantages() + ContentLoader.disadvantages() +
            ContentLoader.blessings().map { it.asTrait(ContentLoader.blessingCost()) }
        // Every applied map, not a list of the ones that existed when this was
        // written: the guard was blind to SKILL_BONUSES once already (6bs),
        // and a map missing from here is a cell nothing checks. It was blind
        // to CATEGORY_BONUSES in exactly the same way from the slice that
        // added it until the one that added a third row to it -- so Empathy's
        // and Clueless's cells went unchecked, which is why four lead-ins had
        // to be named above the moment this line was corrected.
        val applied = TraitEffects.skillBonusNames() +
            TraitEffects.htRollNames() +
            TraitEffects.spiRollNames() +
            TraitEffects.categoryBonusNames()
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
                    // ...and the same for the SPI map, whose applied clause
                    // names its roll class rather than a skill.
                    (
                        trait in TraitEffects.spiRollNames() &&
                            segment.contains("SPI-based rolls")
                        ) ||
                    // ...and a levelled row's own ceiling, which is applied
                    // rather than reported, so it is not a dropped clause.
                    (cap != null && segment.contains("$cap levels")) ||
                    // ...and a category row's applied clause names the banner
                    // rather than a skill: "social skill rolls",
                    // "occult/knowledge skills".
                    TraitEffects.categoryFor(trait)?.let { banner ->
                        segment.lowercase().contains(banner.substringBefore(' ').lowercase())
                    } == true ||
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
     * **A notes cell can hold several rules, and this app applies some of
     * them.** Combat Reflexes prints three -- "+1 to all active defenses,
     * +6 to recover from stun, never freeze in surprise" -- and Fit prints
     * two of which only the first lands.
     *
     * **Combat Reflexes was the subject of this test and is not any more.**
     * All three of its clauses are applied as of 6dr: the defence bonus, the
     * surprise refusal (6bg), and the stun recovery roll ([StunRecovery]) --
     * whose refusal read "this app models no stun recovery roll to make"
     * while `DamageDialog` rendered the row that prints one. A test can
     * encode a reason and expire with it (6do), so the trait stays in the
     * INPUT here: what is asserted is that a wholly applied trait drops OUT
     * of the report, which is the failure this test exists to catch running
     * in the other direction.
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
        assertEquals(listOf("Fit"), notes.map { it.trait })
        assertEquals(listOf("recover FP at twice the normal rate"), notes.map { it.printed })
        // Every applied clause is absent -- none is left over. The last of the
        // three, "+6 to recover from stun", left this list in 6dr; the other
        // two went in 6bg and 6bf.
        assertTrue(notes.none { it.printed.contains("active defenses") }, notes.toString())
        assertTrue(notes.none { it.printed.contains("HT rolls") }, notes.toString())
        assertTrue(notes.none { it.printed.contains("recover from stun") }, notes.toString())
        assertTrue(notes.none { it.printed.contains("surprise") }, notes.toString())
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
        // Every partly-applied row the catalogue prints, not two named ones:
        // the two-name list let a paraphrase through (6fa, Codex).
        val rows = TraitEffects.partlyAppliedNames().filter { it in catalogue }
        assertTrue(rows.size >= 2, rows.toString())
        for (trait in rows) {
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
        // The blessings are a third printed list, in their own file because
        // the book prints their price once in prose rather than as a column.
        val printed = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .map { it.name }.toSet() + ContentLoader.blessings().map { it.deity }
        assertEquals(emptySet<String>(), TraitEffects.known() - printed)
    }

    /**
     * **Two traits production matches by name were outside the census** (6es):
     * Night Eyes, which [TraitEffects.darknessReduction] applies from this
     * very object, and Spiritual Fortitude, which [Corruption.maxFor] has
     * read since M4's first slice. The fifth and sixth instances of the
     * failure `known()`'s KDoc records, found by listing the complement
     * rather than by waiting for the next one: 148 of 198 rows, and these two
     * are the ones a grep for name matches found applying an EFFECT. Not
     * "the only readers": [Wealth] matches four rows by prefix and
     * `pricedVariants` reads Social Stigma's cell, both for a price, and the
     * gallery card prints names. This list is what was found, and it cannot
     * see the next reader.
     */
    @Test
    fun `a trait applied by name elsewhere is one the census can see`() {
        assertEquals(
            emptySet<String>(),
            setOf(TraitEffects.NIGHT_EYES, Corruption.FORTITUDE) - TraitEffects.known(),
        )
    }

    /**
     * **Every mechanic this book has its own NAME for is either applied or
     * said out loud.**
     *
     * Five censuses were run across two slices and each was too narrow in a
     * new way: a category word plus "skill" missed the cells saying "social
     * rolls"; "at Will-N" missed three self-control spellings; and the bare
     * word **"roll"** missed three of the four Fright Check rows — not through
     * a regex slip, but because **a Fright Check is a roll the book names
     * rather than describes**. A morale roll and a death roll are the same
     * shape, on the advantage side, and a review found them by asking that
     * question rather than by grepping.
     *
     * So the enumeration is a test rather than a paragraph in a design
     * document: every catalogue row naming one of these mechanics must be
     * applied here, reported here, or parsed as a roll elsewhere. A row that
     * is none of the three is a mechanic dropped in silence — and the ones
     * this test would have caught were dropped for four milestones.
     */
    @Test
    fun `every named mechanic is applied, reported, or rolled`() {
        val named = listOf("Fright Check", "morale roll", "death roll", "knockdown roll")
        val handled = TraitEffects.known()
        val rows = ContentLoader.advantages() + ContentLoader.disadvantages()
        val dropped = rows.filter { row ->
            named.any { it.lowercase() in row.notes.lowercase() } &&
                row.name !in handled &&
                row.selfControl == null
        }
        assertEquals(
            emptyList<String>(),
            dropped.map { it.name },
            "these name a mechanic the book has a name for and reach nothing, silently",
        )
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
     * **"social rolls" and "social skill rolls" are two different things, in
     * the book's own sentence.** Line 6522 prints "(-1 to all emotional
     * reaction rolls and social skill rolls)", so the bare phrase is not an
     * abbreviation of the other -- and it is never defined, never a banner,
     * and used by rows that read like reactions ("first-impression", "people
     * look out for you"). This book prints no reaction roll procedure at all
     * (6w), so which rolls it touches is settled by nothing.
     *
     * Loner is the carried case: Hunter-Tracker owns it, its Will roll is
     * already made by [SelfControl], and this clause is refused beside it.
     */
    /**
     * **Two premades glue Clueless's name to the book's effect cell with a
     * bare space, and resolving only a " - " separator left them unmatched.**
     * Church Healer and Monastery Scholar print
     * "Clueless -3 to all social skill rolls; ..." as one string, so the trait
     * resolved to no catalogue row at all -- no effect line on the sheet, and
     * no way for this slice's -3 to reach the social skills it names.
     *
     * **The join is still exact, which is what keeps it inside 4.1.** The tail
     * is 130 characters of the book's own sentence reproduced verbatim; what
     * differs is the sheet's typography -- its line wrapping, U+2212 where the
     * catalogue writes an ASCII hyphen (the two-kinds-of-minus trap 6u set
     * first), and a trailing full stop that one of the two carriers drops.
     * None of those can make it a different trait. That is what separates it
     * from the resemblances 6am refused, where a NAME was similar and nothing
     * else agreed.
     *
     * Read off the real sheets rather than a copy of their strings, because
     * the claim is about the fixtures and not about this test's spelling.
     */
    @Test
    fun `a bare-space glued name resolves when the tail is the book's own cell`() {
        val carriers = Premades.all().filter { sheet ->
            sheet.disadvantages.any { it.name.startsWith("Clueless") }
        }
        assertEquals(2, carriers.size, carriers.map { it.name }.toString())
        for (sheet in carriers) {
            val printed = sheet.disadvantages.first { it.name.startsWith("Clueless") }.name
            assertTrue(printed.length > 40, "not the glued spelling: $printed")
            assertEquals("Clueless", TraitEffects.catalogueName(printed), printed)
            // ...and the sheet shows the name alone, because the row prints
            // the cell underneath anyway (6br's device-only defect).
            assertEquals("Clueless", TraitEffects.displayName(printed), printed)
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
     * **A name the book never prints stays unmatched, glue or no glue.**
     * University Librarian's "Blacklisted: Specific Industry org or church"
     * paraphrases the row rather than quoting it, so the tail reproduces no
     * catalogue cell and the resolution must refuse it. 4.1: the disagreement
     * is recorded, never guessed away -- and the sheet keeps every character
     * it printed so the record stays visible.
     *
     * Church Healer's "Sense of Duty (Flock) - ..." used to be listed here
     * beside it. It is NOT the same case and never was: its tail is the row's
     * own words verbatim, and what refused it was the head. That quotation is
     * now the warrant for the bracket, so it resolves.
     */
    @Test
    fun `an instantiated name the book never prints stays unmatched`() {
        val printed = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .map { it.name }.toSet()
        val glued = Premades.all()
            .flatMap { it.advantages + it.disadvantages }
            .map { it.name }
            .filter { it.length >= 40 }
        val unmatched = glued.filter { TraitEffects.catalogueName(it) !in printed }
        assertEquals(
            listOf("Blacklisted: Specific Industry org or church"),
            unmatched,
            "the set of unmatched sheet spellings changed",
        )
        // An unmatched name is shown whole: collapsing it would hide the
        // disagreement, and there is no verified cell to collapse it to.
        for (name in unmatched) assertEquals(name, TraitEffects.displayName(name))
    }

    /**
     * **Every sheet spelling this app cannot resolve, with why.**
     *
     * There was no such census until a spelling turned out to hide a live
     * defect: nine sheets print "Poverty: Struggling" for the level the book
     * calls "Wealth: Struggling", and the miss was not a blank line but a
     * double-counted point total. The only pin that existed covered names of
     * 40 characters or more, so every short spelling was unwatched.
     *
     * The reasons are not decoration. Three shapes hide in one list:
     *
     * - a **disagreement** the book settles against the sheet, which stays
     *   recorded forever (4.1);
     * - a **paraphrase**, where the sheet instantiates a row without quoting
     *   it, so only resemblance is left;
     * - a **gap**, where the data plainly marks something and this app cannot
     *   see it. A gap is work, not a decision, and calling one a refusal is
     *   how [TraitEffects] shipped four milestones of unreachable rules.
     *
     * The map is the assertion: a spelling that appears without an entry
     * FAILS, so a new one is a finding rather than a silent addition.
     */
    @Test
    fun `every sheet spelling this app cannot resolve is named here`() {
        val printed = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .map { it.name }.toSet()
        val unmatched = Premades.all()
            .flatMap { it.advantages + it.disadvantages }
            .map { it.name }
            .filter { TraitEffects.catalogueName(it) !in printed }
            .distinct()
        assertEquals(
            UNRESOLVED_SHEET_SPELLINGS.keys,
            unmatched.toSortedSet(),
            "the set of sheet spellings this app cannot resolve changed",
        )
        // Every one of them is shown whole. Collapsing an unresolved name to
        // the row it resembles is the repair 4.1 forbids, and there is no
        // verified cell to collapse it to.
        for (name in unmatched) assertEquals(name, TraitEffects.displayName(name))
    }

    /**
     * Sixteen spellings, and the reason each is here. Keep the wording of the
     * REASON honest: it is the thing a later slice will check first, and a
     * false one has already cost this project two slices (a shock penalty the
     * book did print, and a knockdown roll that was never unreachable).
     */
    private val UNRESOLVED_SHEET_SPELLINGS = sortedMapOf(
        // --- the book prints no such row, under any spelling -------------
        "No Sense of Humour" to
            "the book prints no such trait; a text search finds the phrase nowhere",
        "Pacifism (Cannot Harm Innocents)" to
            "the book prints no Pacifism row; \"Pacifis\" occurs nowhere in it",
        "Pacifism (Cannot Kill)" to
            "the book prints no Pacifism row; \"Pacifis\" occurs nowhere in it",

        // --- resembles a printed row, and the price disagrees -------------
        "Absent-Minded" to
            "the book prints \"Absent-Mindedness\" at 15 and these sheets pay 5, " +
            "so only the name resembles -- the refusal 6am recorded",
        "Paranoia: Bodies of water" to
            "the book prints a bare \"Paranoia\" at 10 and this sheet pays 5, so " +
            "the qualifier is unprinted AND the price disagrees",
        "Wierdness Magnet" to
            "the book prints \"Weirdness Magnet\" once, at this sheet's own 15 -- " +
            "so the price agrees and the spelling is a transposition. Resolving " +
            "it needs typo tolerance, which is the guessing 4.1 forbids, and the " +
            "row prints no mechanical clause so nothing is lost by refusing",

        // --- an instantiation that paraphrases rather than quotes ---------
        "Blacklisted: Specific Industry org or church" to
            "\"Blacklisted\" prints a FIXED 10 and no placeholder, so nothing " +
            "marks it instantiable -- and the qualifier paraphrases the row's own " +
            "notes rather than quoting them, which is what 6br's exact-tail rule " +
            "refuses",

        // --- resolved elsewhere, by design --------------------------------
        "Poverty: Struggling" to
            "resolved by [Wealth], not here: that resolution reads the trait's " +
            "cost and kind, and this API takes a name alone (a design review's " +
            "call in 6bx). The builder's wealth row shows the level",

        // --- a GAP: the data marks an instantiation and this app cannot see it
        "Secret (Courier network)" to SEE_RANGED_TEMPLATE_GAP,
        "Secret (Racist Sympathizer)" to SEE_RANGED_TEMPLATE_GAP,
    )

    /**
     * Why seven instantiations of two rows are a GAP and not a refusal.
     *
     * The book asks for an instance two ways: explicitly, by putting
     * "(specify)" in the row's NAME, which this app resolves; and implicitly,
     * by printing a cost RANGE that the instance settles -- "Sense of Duty"
     * 2-15, "Secret" 5-30. Nothing here reads the second.
     *
     * **Reading the range alone would be inference, and it over-matches.**
     * Nineteen rows print a range, and two of them are "Shyness" and
     * "Social Stigma" -- the exact pair 6bw resolved through a separate,
     * carefully argued warrant, because their cells ENUMERATE their variants
     * with prices. If a range by itself marked a template, that warrant was
     * never needed. "Reputation" is variable-cost too, and 6bx's review used
     * "Reputation: Wealthy" as its over-match example.
     *
     * **The narrower warrant was taken instead, and it closed half of this.**
     * Church Healer prints "Sense of Duty (Flock) - " followed by that row's
     * own notes VERBATIM -- the sheet asserting the instantiated name IS that
     * row -- so those six spellings resolve now and are gone from the map
     * above. `Secret` stays, because no sheet quotes its cell.
     *
     * That asymmetry is the whole point, and it is what a design review
     * insisted on: **a cost inside the printed interval corroborates PRICING,
     * never catalogue IDENTITY.** Secret prints an interval and enumerates
     * nothing, exactly as Sense of Duty does, so any rule keyed on that shape
     * resolves both -- and excluding the counter-examples (Shyness, Social
     * Stigma, Reputation) is not the same as producing evidence for these.
     *
     * **A census of the fixtures, for whoever builds it** (facts, not
     * recollection -- and the thing 6by did not have):
     *
     *  - Nine of the nineteen withheld rows are instantiated by the 23
     *    published sheets, across 19 instances. (I counted 18 by hand and the
     *    test said 19 -- which is why it is a test.)
     *  - EVERY one of the 19 pays a cost INSIDE its row's printed range. None
     *    outside. That is the book's own cast doing what a bare range implies,
     *    and it is a claim about PRICING, which is a different question from
     *    the NAME RESOLUTION refused above.
     *  - The ranged rows are three shapes, not one: two enumerate named
     *    variants (Shyness, Social Stigma); two print a SCALE with worked
     *    points (Mystical Item, Duty) and say the value moves; the rest print
     *    a bare interval. "Reputation" alone prints no bounds at all, so it
     *    has no interval to pick inside and is not the same case as the other
     *    thirteen.
     *  - "Compulsion: Collection (pathway)" carries a Will-2 self-control
     *    roll, so offering these rows is not a cosmetic change.
     */
    /**
     * **The pricing warrant, checked over every sheet rather than asserted.**
     *
     * A row printing "-2 to -15" instead of a price is withheld from the trait
     * picker today, and the question the next slice has to answer is whether a
     * player may buy one at a cost they choose. The book's own cast already
     * does: nine withheld rows are instantiated across the 23 sheets, and all
     * 18 instances pay INSIDE their row's printed interval.
     *
     * That is why this is a test and not a sentence (6bu). The day a sheet
     * pays outside its row's range, the warrant is gone and the reading has to
     * be made again rather than inherited (6ba). It also fails if a row stops
     * printing bounds, which is the other half of the same claim.
     *
     * This reaches names production deliberately refuses -- an audit is a
     * strict SUPERSET of the resolver, free to reach further so it can report
     * on a near-match, never permitted to reach less (6bz).
     */
    @Test
    fun `every instance of a ranged row pays inside the range the book prints`() {
        val ranged = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .filter { it.cost == null && it.costMin != null && it.costMax != null }
            .associateBy { it.name }
        val checked = Premades.all()
            .flatMap { sheet -> (sheet.advantages + sheet.disadvantages).map { sheet.name to it } }
            .mapNotNull { (sheet, owned) ->
                val head = owned.name.substringBefore(" (").substringBefore(" - ")
                val row = ranged[head] ?: ranged["$head${TraitEffects.PLACEHOLDER}"]
                    ?: return@mapNotNull null
                assertTrue(
                    owned.cost >= row.costMin!! && owned.cost <= row.costMax!!,
                    "$sheet pays ${owned.cost} for ${owned.name}, outside " +
                        "${row.name}'s printed ${row.costMin}..${row.costMax}",
                )
                owned.name
            }
        assertEquals(19, checked.size, "the fixtures instantiate 19 ranged rows")
        assertEquals(9, checked.map { it.substringBefore(" (") }.distinct().size)
    }

    private companion object {
        const val SEE_RANGED_TEMPLATE_GAP =
            "the book prints a cost RANGE an instance settles, and no sheet " +
                "quotes this row's cell -- so nothing proves the bracket names " +
                "THIS row rather than resembling it"
    }

    // ---- the placeholder the book tells the sheet to fill ------------------

    /**
     * **Five catalogue rows print a placeholder in their NAME**, and eight
     * fixture traits across eight premades are instances of four of them:
     * "Phobia (Cities)", three Duties, three Vows, "Resistant (Disease +3)".
     * None resolved before this join, so every clause they carry was
     * invisible -- and one of them is the only Fright Check modifier any
     * premade owns.
     *
     * **The warrant is an INSTRUCTION, not a resemblance.** The book writes
     * "(specify)" where the reader is to substitute, and Duty's own cell says
     * so in words: "Specify organization and nature at creation." A sheet
     * writing "Duty (Regiment)" is obeying that, exactly as
     * `"Hidden Lore (relevant domain)"` was read as an instruction and matched
     * one section over (6am).
     */
    @Test
    fun `an instance of a placeholder row resolves to that row`() {
        val expected = mapOf(
            "Phobia (Cities)" to "Phobia (specify)",
            "Duty (Nighthawks)" to "Duty (specify)",
            "Duty (PMC)" to "Duty (specify)",
            "Duty (Regiment)" to "Duty (specify)",
            "Vow (Poverty)" to "Vow (specify)",
            "Vow (Partial Silence)" to "Vow (specify)",
            "Vow (Vegetarian)" to "Vow (specify)",
            "Resistant (Disease +3)" to "Resistant (specify)",
        )
        // Every one is really on a sheet: a join no premade exercises is a
        // rule nothing can reach (6bc, 6bs).
        val owned = Premades.all()
            .flatMap { it.advantages + it.disadvantages }
            .map { it.name }
            .toSet()
        for ((printed, row) in expected) {
            assertTrue(printed in owned, "$printed is on no sheet any more")
            assertEquals(row, TraitEffects.catalogueName(printed), printed)
        }
        // The row itself is not an instance of itself.
        assertEquals("Phobia (specify)", TraitEffects.catalogueName("Phobia (specify)"))
        // The instantiation is CONTENT, so the name a screen prints keeps it:
        // "Phobia" alone would drop which fear this character has.
        assertEquals("Phobia (Cities)", TraitEffects.displayName("Phobia (Cities)"))
    }

    /**
     * **A qualifier the book never asked for stays unmatched.** These sheets
     * add a bracket or a colon to a catalogue name that prints NO placeholder,
     * so the only evidence is resemblance -- and 4.1 says the disagreement is
     * recorded, never guessed away (6am refused these first).
     *
     * That boundary is the whole reason the join above is legitimate: it turns
     * on the book printing the word, not on the sheet printing a bracket.
     */
    @Test
    fun `a qualifier the book never asked for stays unmatched`() {
        val placeholders = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .map { it.name }
            .filter { it.endsWith(TraitEffects.PLACEHOLDER) }
        assertEquals(
            listOf(
                "Language (specify)", "Resistant (specify)",
                "Duty (specify)", "Vow (specify)", "Phobia (specify)",
            ),
            placeholders,
            "the set of rows printing a placeholder changed",
        )
        // Duty says the instruction out loud; the others print the word alone.
        assertTrue(
            ContentLoader.disadvantages().single { it.name == "Duty (specify)" }
                .notes.contains("Specify organization and nature at creation"),
        )
        for (name in listOf(
            "Paranoia: Bodies of water",     // the row is a bare "Paranoia"
            "Secret (Courier network)",       // no sheet quotes its cell
            "Blacklisted: Specific Industry org or church",
            "Absent-Minded",                 // "Absent-Mindedness" in the book
            "Pacifism (Cannot Kill)",        // no such row at all
        )) {
            assertEquals(name, TraitEffects.catalogueName(name), name)
        }
    }

    /**
     * **A cell that ENUMERATES its own variants, with their prices, names them
     * as surely as a placeholder invites one.** Two rows do:
     *
     *  - Shyness: "Mild (-5): -1 in groups of 6+; Severe (-10): -2 in groups
     *    of 3+."
     *  - Social Stigma: "Society discriminates: Criminal Record -5, Servant
     *    Class -5"
     *
     * and two sheets buy exactly one of the priced options -- Academic
     * Researcher's "Shyness (Mild)" at 5 pts, Pickpocket's "Social Stigma
     * (Criminal)" at 5 pts. The qualifier is named by the cell AND the cost
     * agrees with the price the cell prints for it: two independent
     * agreements, which is what separates this from resemblance.
     *
     * **This corrects a stated reason, which is not the same as repairing a
     * disagreement (4.1).** 6bv refused these two because "the sheet added a
     * qualifier the book never asked for, so the only evidence is
     * resemblance". That sentence is false for exactly these two, and 6bf's
     * lesson is that a sweep's excuse is the thing to check. The nine
     * spellings whose reason still holds are pinned unmatched above.
     */
    @Test
    fun `a qualifier the cell itself prices resolves to that row`() {
        assertEquals("Shyness", TraitEffects.catalogueName("Shyness (Mild)"))
        assertEquals("Social Stigma", TraitEffects.catalogueName("Social Stigma (Criminal)"))
        // The sheet's own spelling is what a screen prints: which variant was
        // bought is content, and it is also what decides Shyness's value.
        assertEquals("Shyness (Mild)", TraitEffects.displayName("Shyness (Mild)"))
    }

    /**
     * **An enumeration has at least two members.** Restricted to one, the
     * parse reads "Must roll vs. Will-3" as a variant called "Will" priced at
     * 3, and eleven rows print a number in prose that way. A cell that
     * enumerates variants is offering a CHOICE, so the book prints more than
     * one -- and that alone reduces 198 rows to the two this join reads.
     *
     * Pinned by value: the day a third row starts enumerating, this fails and
     * the reading is made again rather than inherited (6ba).
     */
    @Test
    fun `only the rows that print a choice enumerate variants`() {
        val enumerating = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .filter { TraitEffects.pricedVariants(it.notes).isNotEmpty() }
            .map { it.name }
        assertEquals(listOf("Social Stigma", "Shyness"), enumerating)
        assertEquals(
            listOf("Mild" to 5, "Severe" to 10),
            TraitEffects.pricedVariants(
                ContentLoader.disadvantages().single { it.name == "Shyness" }.notes,
            ),
        )
        // A lone number in prose is not a choice: Stubborn's "Will-3" would
        // otherwise read as a variant named "Will" costing 3.
        assertTrue(
            TraitEffects.pricedVariants(
                ContentLoader.disadvantages().single { it.name == "Stubborn" }.notes,
            ).isEmpty(),
        )
    }

    /**
     * **The quotation that licenses one row's bracket, checked in the fixtures
     * rather than recalled.**
     *
     * Church Healer prints "Sense of Duty (Flock) - " followed by that row's
     * own notes VERBATIM. That is the sheet asserting the instantiated name IS
     * the bare row, and it is the whole warrant for resolving the five other
     * "Sense of Duty (group)" spellings -- so if the sheet is ever re-extracted
     * without it, this fails and the reading has to be made again rather than
     * inherited (6ba).
     */
    @Test
    fun `one sheet quotes the row its bracket instantiates, and that is the warrant`() {
        val row = ContentLoader.disadvantages().single { it.name == "Sense of Duty" }
        val glued = Premades.all()
            .flatMap { it.disadvantages }
            .map { it.name }
            .single { it.startsWith("Sense of Duty (") && it.contains(" - ") }
        val tail = glued.substringAfter(" - ")
        assertEquals(
            row.notes.replace(Regex("\\s+"), " ").trim(),
            tail.replace(Regex("\\s+"), " ").trim(),
            "the quotation this row's resolution rests on is gone",
        )
    }

    /**
     * The bracket resolves for the row a sheet quoted, and for no other.
     *
     * **"Secret" is the discriminating case**, and it stays unmatched on
     * purpose: it prints a cost interval and enumerates nothing, exactly like
     * Sense of Duty, so a rule keyed on that shape would resolve it too. No
     * sheet quotes its cell, and every instance paying inside its printed
     * interval is evidence about PRICING rather than catalogue IDENTITY
     * (Codex, on the design) -- which makes the broader rule the plausible
     * match 4.1 calls repair.
     */
    @Test
    fun `a bracket resolves only on the row a sheet proved it instantiates`() {
        assertEquals("Sense of Duty", TraitEffects.catalogueName("Sense of Duty (Patients)"))
        assertEquals("Sense of Duty", TraitEffects.catalogueName("Sense of Duty (Workers)"))
        assertEquals(
            "Sense of Duty",
            TraitEffects.catalogueName(
                "Sense of Duty (Flock) - Must help/protect certain groups even at personal risk",
            ),
            "the glued spelling that carries the quotation",
        )
        // The sheet's own spelling still shows, because which group it is is
        // content (6e) -- and the glued one shows the NAME alone, or the sheet
        // would print the effect cell as a title and repeat it underneath.
        assertEquals(
            "Sense of Duty (Patients)",
            TraitEffects.displayName("Sense of Duty (Patients)"),
        )
        assertEquals(
            "Sense of Duty (Flock)",
            TraitEffects.displayName(
                "Sense of Duty (Flock) - Must help/protect certain groups even at personal risk",
            ),
        )
        // ...and the row nothing quoted keeps its recorded gap.
        assertEquals(
            "Secret (Courier network)",
            TraitEffects.catalogueName("Secret (Courier network)"),
        )
        assertEquals("Obsession (A rival)", TraitEffects.catalogueName("Obsession (A rival)"))
    }

    /**
     * **A scale with worked points is not a set of variants**, and this is the
     * condition the filter above does NOT explain.
     *
     * `pricedVariants` is safe because of two things, and its KDoc credits one.
     * The "at least two members" rule keeps Stubborn's "Will-3" out, as the
     * test above pins. What keeps a PROSE SCALE out is that a variant is a
     * NAME and a scale point is not:
     *
     *  - Mystical Item: "Cost scales with power: a minor charm (5 pts), a
     *    useful tool (10 pts), or a significant piece of equipment (15 pts)."
     *  - Duty (specify): "Value reflects frequency and danger: -5 for light
     *    duty, -10 for hazardous duty, -15 for extremely hazardous."
     *
     * Both print three priced options spanning their own cost range, so the
     * member count cannot separate them -- only the capital letter does.
     * Mystical Item is the near-miss that shows it: read as an enumeration it
     * offers a variant literally called "or a significant piece of equipment",
     * and its own sentence says the cost SCALES, so the row admits 7 as
     * readily as 5.
     *
     * Pinned because widening the parse is the obvious next move on this
     * object and the two rows look identical to a member count. Nothing can
     * reach the wrong answer today -- no premade owns either row -- which is
     * exactly when this repo pins a permissive path rather than after the next
     * caller finds it (6l).
     */
    @Test
    fun `a prose scale that prices its own examples is not a choice of variants`() {
        val catalogue = ContentLoader.advantages() + ContentLoader.disadvantages()
        val scales = listOf("Mystical Item", "Duty (specify)", "Blood Feud", "Contact")
        for (name in scales) {
            val row = catalogue.single { it.name == name }
            assertTrue(
                TraitEffects.pricedVariants(row.notes).isEmpty(),
                "$name prints a scale, not a set of named variants",
            )
        }
        // The evidence, quoted: each says the cost MOVES with something, and
        // names its points in lower case where a variant is capitalised.
        val mystical = catalogue.single { it.name == "Mystical Item" }
        assertTrue(mystical.notes.contains("Cost scales with power"))
        assertTrue(mystical.notes.contains("a minor charm (5 pts)"))
        val duty = catalogue.single { it.name == "Duty (specify)" }
        assertTrue(duty.notes.contains("Value reflects frequency and danger"))
        // ...against a real enumeration, whose members are names.
        assertEquals(
            listOf("Mild" to 5, "Severe" to 10),
            TraitEffects.pricedVariants(
                ContentLoader.disadvantages().single { it.name == "Shyness" }.notes,
            ),
        )
    }

    /**
     * The qualifier must select EXACTLY ONE priced variant, and the cost must
     * agree. Social Stigma prices both its variants at 5, so the cost alone
     * cannot say which was bought -- the qualifier does, and the cost only
     * corroborates (Codex, on the design). A qualifier matching two, or one
     * whose price the sheet did not pay, is refused rather than guessed.
     */
    /**
     * A qualifier naming no printed option, or naming two, is refused rather
     * than guessed. The sheet prints "Criminal" where the book prints
     * "Criminal Record", so a qualifier that PREFIXES exactly one option is
     * the join -- never a substring search over the cell (Codex, on the
     * design: "selects exactly one priced variant named by the row").
     */
    @Test
    fun `a qualifier naming no option or several is refused`() {
        assertEquals("Shyness (Crippling)", TraitEffects.catalogueName("Shyness (Crippling)"))
        // "S" prefixes both "Servant Class" and nothing else here, but on
        // Shyness it would prefix "Severe" alone; the guard is the count, so
        // a cell whose options shared a prefix could not answer at all.
        assertEquals(
            listOf("Criminal Record" to 5, "Servant Class" to 5),
            TraitEffects.pricedVariants(
                ContentLoader.disadvantages().single { it.name == "Social Stigma" }.notes,
            ),
        )
    }

    /**
     * **The WARRANT, checked over every sheet rather than asserted.** The join
     * itself runs on the qualifier alone, because [TraitEffects.catalogueName]
     * is handed a name and threading a cost through it would give this
     * decision a second matcher -- the failure this file records six times
     * (6ag, 6ai, 6be, 6bp, 6bs, 6bv). What licenses reading the qualifier at
     * all is that the price agrees too, and that is a fact about the fixtures,
     * so it is proven here across all 23.
     *
     * The day a sheet buys a variant at a price the cell does not print for
     * it, this fails and the disagreement is recorded (4.1) rather than
     * silently resolved on the name alone.
     */
    @Test
    fun `every variant a sheet bought is priced as the cell prints it`() {
        val catalogue = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .associateBy { it.name }
        val checked = Premades.all()
            .flatMap { it.advantages + it.disadvantages }
            .mapNotNull { owned ->
                val row = catalogue[TraitEffects.catalogueName(owned.name)] ?: return@mapNotNull null
                val variants = TraitEffects.pricedVariants(row.notes)
                if (variants.isEmpty() || owned.name == row.name) return@mapNotNull null
                val qualifier = owned.name.substringAfterLast('(').removeSuffix(")")
                val named = variants.single { it.first.startsWith(qualifier, ignoreCase = true) }
                assertEquals(named.second, owned.cost, "${owned.name} pays ${owned.cost}")
                owned.name
            }
        assertEquals(listOf("Shyness (Mild)", "Social Stigma (Criminal)"), checked.sorted())
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

    // ---- the five rows that modify a Fright Check --------------------------

    private fun frightOf(vararg names: String) =
        TraitEffects.frightClauses(names.map { Trait(it, 5, TraitKind.ADVANTAGE) })

    /**
     * **Five catalogue rows modify a roll this app has made since 6c**, and
     * `FrightCheck.against` has taken a modifier the whole time. What was
     * missing was the join, which is why the reason those rows carried was a
     * GAP rather than a refusal.
     *
     * Every value is pinned against the copy production reads, so a re-extract
     * that reworded a cell fails here rather than moving a number in silence.
     */
    @Test
    fun `every fright number is still the one its row prints`() {
        val byName =
            (ContentLoader.advantages() + ContentLoader.disadvantages()).associateBy { it.name }
        for (clause in TraitEffects.frightClauseRows()) {
            val notes = byName.getValue(clause.trait).notes
            assertTrue(clause.printed in notes, "${clause.trait}: ${clause.printed}")
            // The clause names its own number, so the two cannot drift apart.
            val sign = if (clause.value < 0) "-" else "+"
            assertTrue(
                "$sign${kotlin.math.abs(clause.value)}" in clause.printed,
                "${clause.trait} prints no ${sign}${kotlin.math.abs(clause.value)}",
            )
        }
    }

    /**
     * **Fearlessness is the only unconditional row**, so it is the only one a
     * screen may add without asking. The other four name a circumstance the GM
     * declares -- being in a church, being exposed to the phobic thing -- and
     * this app holds one sheet and no scene, so it cannot know.
     */
    @Test
    fun `only the row that names no circumstance is unconditional`() {
        val unconditional = TraitEffects.frightClauseRows()
            .filter { !it.conditional }
            .map { it.trait }
        assertEquals(listOf("Fearlessness"), unconditional)
    }

    /** "+1 per level to Fright Checks", and the cell prints no ceiling. */
    @Test
    fun `fearlessness scales with its level`() {
        assertEquals(1, frightOf("Fearlessness").single().value)
        assertEquals(3, TraitEffects.frightClauses(
            listOf(Trait("Fearlessness 3", 6, TraitKind.ADVANTAGE)),
        ).single().value)
        assertNull(TraitEffects.maxLevelsFor("Fearlessness"))
    }

    /**
     * **Sacred Symbols prints TWO circumstances and both are offered.** "-2 on
     * direct exposure" is a relic in view and "-1 in consecrated buildings" is
     * standing inside one; a cathedral holding a relic is both at once, the
     * book prints no exclusion, and two consecutive sentences of one cell were
     * read as adding once already (6aa's Precious altar and its sentient
     * offering). So they sum to -3 when both are declared.
     */
    @Test
    fun `two circumstances on one cell are two clauses`() {
        val clauses = frightOf("Phobia: Sacred Symbols")
        assertEquals(2, clauses.size)
        assertEquals(listOf(-2, -1), clauses.map { it.value })
        // Each is separately declarable, so the key has to tell them apart.
        assertEquals(2, clauses.map { it.key }.toSet().size)
        assertEquals(
            -3,
            TraitEffects.clauseBonus(clauses, clauses.map { it.key }.toSet()).total,
        )
    }

    /**
     * The number and the sentence come from one call, because a screen that
     * computes one and prints the other is how these breakdowns stopped
     * reconciling (6ag, 6ai, 6be, 6bp, 6bs). The parts must sum to the total
     * across every combination a GM can declare.
     */
    @Test
    fun `the breakdown always sums to the bonus it explains`() {
        val clauses = frightOf("Fearlessness", "Faithful Grounding", "Phobia: Sacred Symbols")
        val keys = clauses.map { it.key }
        // Every subset of the declarable clauses.
        for (mask in 0 until (1 shl keys.size)) {
            val declared = keys.filterIndexed { i, _ -> (mask shr i) and 1 == 1 }.toSet()
            val bonus = TraitEffects.clauseBonus(clauses, declared)
            val line = bonus.line
            if (bonus.total == 0 && line == null) continue
            val summed = Regex("[+−-]\\d+").findAll(line.orEmpty())
                .sumOf { it.value.replace('−', '-').toInt() }
            assertEquals(bonus.total, summed, line)
        }
    }

    /**
     * An unconditional clause is counted whether or not it was declared: the
     * book attaches no circumstance to it, so a screen that forgot to pass it
     * must not silently withdraw a bonus the character always has.
     */
    @Test
    fun `an unconditional clause needs no declaration`() {
        val clauses = frightOf("Fearlessness")
        assertEquals(1, TraitEffects.clauseBonus(clauses, emptySet()).total)
        // ...and a conditional one is worth nothing until it is declared.
        val phobia = frightOf("Phobia (Cities)")
        assertEquals(0, TraitEffects.clauseBonus(phobia, emptySet()).total)
        assertEquals(-2, TraitEffects.clauseBonus(phobia, setOf(phobia.single().key)).total)
    }

    /**
     * **Hunter-Tracker is the only premade that reaches any of this**, through
     * the placeholder join above -- "Phobia (Cities)" against the catalogue's
     * "Phobia (specify)". Before that join the trait resolved to no row at
     * all, so the two halves really are one slice.
     */
    @Test
    fun `the one premade carrying a fright row reaches it`() {
        val carriers = Premades.all().filter {
            TraitEffects.frightClauses(it.toCharacter().traits).isNotEmpty()
        }
        assertEquals(listOf("Hunter-Tracker"), carriers.map { it.name })
        val clause = TraitEffects.frightClauses(carriers.single().toCharacter().traits).single()
        assertEquals("Phobia (specify)", clause.trait)
        // The screen names the sheet's own spelling, not the placeholder row:
        // which fear this character has is content the GM needs.
        assertEquals("Phobia (Cities)", clause.shown)
        assertEquals(-2, clause.value)
        assertTrue(clause.conditional)
    }

    /**
     * Phobia's "-1 to all rolls" half is REPORTED, not applied. It is a
     * declared all-rolls state, which this app models for exactly one thing --
     * shock, through an enum tested per roll class (6bo) -- and a second is its
     * own slice. Fearlessness's intimidation half needs a contest between two
     * characters' levels, which this app cannot hold.
     */
    @Test
    fun `the clauses a fright row prints beside its modifier are reported`() {
        val phobia = TraitEffects.reported(
            listOf(Trait("Phobia (Cities)", 10, TraitKind.DISADVANTAGE)),
        )
        assertTrue(phobia.any { "-1 to all rolls" in it.printed }, phobia.toString())
        // ...under the sheet's own spelling.
        assertTrue(phobia.all { it.trait == "Phobia (Cities)" })
        val fearless = TraitEffects.reported(listOf(advantage("Fearlessness")))
        assertTrue(fearless.any { "intimidation" in it.printed }, fearless.toString())
    }

    // ---- the social rows whose penalty needs a circumstance ----------------

    private val intimidation = SkillKey(
        "Intimidation", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.AVERAGE,
    )

    private fun social(name: String, cost: Int = 10) =
        listOf(Trait(name, cost, TraitKind.DISADVANTAGE))

    /** Every value is still the one its row prints, against the live copy. */
    @Test
    fun `every social number is still the one its row prints`() {
        val byName = ContentLoader.disadvantages().associateBy { it.name }
        for (clause in TraitEffects.socialClauseRows()) {
            val notes = byName.getValue(clause.trait).notes
            assertTrue(clause.printed in notes, "${clause.trait}: ${clause.printed}")
            assertTrue("-${-clause.value}" in clause.printed, clause.printed)
        }
        // All three rows are conditional; none may ever be counted unasked.
        assertTrue(TraitEffects.socialClauseRows().all { it.conditional })
    }

    /**
     * **Nothing reaches the roll until the GM says the circumstance holds.**
     * Barmaid Brawler and Docks Bouncer both own Callous BESIDE Intimidation,
     * so a flat penalty would charge them for warmth nobody asked for.
     */
    @Test
    fun `a social penalty needs its circumstance declared`() {
        val callous = social("Callous")
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(callous, intimidation))
        val clause = TraitEffects.socialClauses(callous).single()
        val applied = TraitEffects.modifiers(callous, intimidation, setOf(clause.key))
        assertEquals(ModEffect.Flat(-3), applied.single().effect)
        // ...and only on a SOCIAL skill: the cell says "social skill rolls".
        val climbing = SkillKey(
            "Climbing", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.AVERAGE,
        )
        assertEquals(
            emptyList<Any>(),
            TraitEffects.modifiers(callous, climbing, setOf(clause.key)),
        )
    }

    /**
     * **Shyness's value comes from the SHEET, not from the GM.** Its cell
     * prices two severities and a character bought one; only the variant join
     * can tell which, so a Mild shyness must never offer Severe's -2.
     */
    @Test
    fun `a priced variant decides which clause is on offer`() {
        val mild = TraitEffects.socialClauses(social("Shyness (Mild)", 5)).single()
        assertEquals(-1, mild.value)
        assertTrue("Mild" in mild.printed)
        val severe = TraitEffects.socialClauses(social("Shyness (Severe)", 10)).single()
        assertEquals(-2, severe.value)
        // The sheet's own spelling is what the GM sees.
        assertEquals("Shyness (Mild)", mild.shown)
        // **A bare "Shyness" names no severity, so it offers NEITHER.** An
        // empty qualifier prefixes every option, which would put Mild and
        // Severe on screen together and let a GM declare both -- and severity
        // is the sheet's to state, not a choice (Codex, on the diff).
        assertEquals(emptyList<Any>(), TraitEffects.socialClauses(social("Shyness", 5)))
    }

    /**
     * Social Anxiety prints two circumstances that can both hold — a large
     * gathering of strangers who outrank you — and the book prints no
     * exclusion, so they are separate declarations that SUM. Two modifiers,
     * two distinct ids: sharing one would be a collision the resolver should
     * never be handed.
     */
    @Test
    fun `two circumstances on one social cell sum and stay distinct`() {
        val anxious = social("Social Anxiety")
        val keys = TraitEffects.socialClauses(anxious).map { it.key }.toSet()
        assertEquals(2, keys.size)
        val mods = TraitEffects.modifiers(anxious, intimidation, keys)
        assertEquals(2, mods.map { it.id }.toSet().size, "two clauses shared one id")
        assertEquals(-5, mods.sumOf { (it.effect as ModEffect.Flat).n })
    }

    /**
     * A trait modifier is `FromTrait`, never `Situational`: a cancel budget is
     * measured by `SourceKind`, and a drawback the character permanently has
     * is not something a GM said about this turn. Nothing can reach the
     * difference today, which is why it is pinned now rather than found the
     * day something does (6al's precedent).
     */
    @Test
    fun `a declared social penalty is sourced from the trait`() {
        val callous = social("Callous")
        val key = TraitEffects.socialClauses(callous).single().key
        val mod = TraitEffects.modifiers(callous, intimidation, setOf(key)).single()
        val source = mod.source
        assertTrue(source is ModifierSource.FromTrait, source.toString())
        assertEquals(false, (source as ModifierSource.FromTrait).advantage)
    }

    /**
     * **The rows are reachable on the premades that carry them**, which is
     * what a census is for (6bv). Academic Researcher's is reachable only
     * through the priced-variant join in this same slice.
     */
    @Test
    fun `the premades carrying a social row reach it`() {
        val carriers = Premades.all()
            .filter { TraitEffects.socialClauses(it.toCharacter().traits).isNotEmpty() }
            .map { it.name }
        assertEquals(
            listOf("Academic Researcher", "Barmaid Brawler", "Docks Bouncer"),
            carriers.sorted(),
        )
        // Social Anxiety is on no sheet and ships anyway: the list is every
        // conditional social clause the book prints, not a premade census.
        assertTrue("Social Anxiety" in TraitEffects.socialClauseNames())
    }

    @Test
    fun `a bare social rolls clause reaches no skill`() {
        val loner = listOf(Trait("Loner", 5, TraitKind.DISADVANTAGE))
        val diplomacy = SkillKey(
            "Diplomacy", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.HARD,
        )
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(loner, diplomacy))
        assertTrue(TraitEffects.reported(loner).any { "social rolls" in it.printed })
    }

    // ---- the three rows that modify a bare Will roll -----------------------

    private fun willOf(vararg names: String) =
        TraitEffects.willClauses(names.map { Trait(it, 5, TraitKind.ADVANTAGE) })

    /**
     * **The census, with the query that reproduces it.** 6de recorded "four
     * rows name Will rolls" and stated no filter, so the number could not be
     * checked -- and `Will roll` singular returns five. The filter is `Will
     * rolls`, PLURAL: it drops `Compulsive Gambling`, whose "requires Will
     * roll at -2" is already read as a self-control check.
     *
     * A count without its query is unreproducible, which is what this test
     * exists to stop happening a second time.
     */
    @Test
    fun `four catalogue rows name Will rolls and two of them are already read`() {
        val catalogue = ContentLoader.advantages() + ContentLoader.disadvantages()
        val plural = catalogue.filter { "Will rolls" in it.notes.orEmpty() }.map { it.name }
        assertEquals(
            setOf("Incorruptible Will", "Grief-Stricken", "Reluctant Killer", "Evil Eye Fear"),
            plural.toSet(),
        )
        // The singular row is the one the plural query drops, and it is read.
        val gambling = ContentLoader.disadvantages().single { it.name == "Compulsive Gambling" }
        assertTrue("Will roll at -2" in gambling.notes.orEmpty())
        assertTrue("Will rolls" !in gambling.notes.orEmpty())
        assertTrue("Compulsive Gambling" !in TraitEffects.willClauseNames())
        // Reluctant Killer IS returned by the plural query and still carries
        // no number, so it is read as a self-control check and not as a
        // clause. A row NAMING a roll is not a row MODIFYING one.
        assertTrue("Reluctant Killer" !in TraitEffects.willClauseNames())
        // ...and "already read" is asserted, not asserted-about: both rows
        // still produce a self-control check, at the offset their cell prints
        // (Codex -- the name claimed a reader the test never called).
        for ((name, offset) in listOf("Compulsive Gambling" to -2, "Reluctant Killer" to 0)) {
            val check = SelfControl.checksFor(
                listOf(Trait(name, 10, TraitKind.DISADVANTAGE)),
            ).single()
            assertEquals(name, check.trait)
            assertEquals(offset, check.spec.offset, name)
        }
    }

    /**
     * Every value is pinned against the copy production reads, so a
     * re-extract that reworded a cell fails here rather than moving a number
     * in silence -- the guard the Fright Check's rows already have.
     */
    @Test
    fun `every will number is still the one its row prints`() {
        val byName =
            (ContentLoader.advantages() + ContentLoader.disadvantages()).associateBy { it.name }
        assertEquals(3, TraitEffects.willClauseRows().size)
        for (clause in TraitEffects.willClauseRows()) {
            val notes = byName.getValue(clause.trait).notes.orEmpty()
            assertTrue(clause.printed in notes, "${clause.trait}: ${clause.printed}")
            val sign = if (clause.value < 0) "-" else "+"
            assertTrue(
                "$sign${kotlin.math.abs(clause.value)}" in clause.printed,
                "${clause.trait} prints no $sign${kotlin.math.abs(clause.value)}",
            )
        }
    }

    /**
     * **All three name a circumstance, so none may be counted unasked.**
     * Unlike the Fright Check's rows, where Fearlessness is what the
     * character always is, there is no unconditional Will clause: every cell
     * says "to resist", "if interrupted" or "in situations that echo".
     */
    @Test
    fun `no will clause may be counted without a declaration`() {
        assertTrue(TraitEffects.willClauseRows().all { it.conditional })
        val carrier = willOf("Incorruptible Will", "Evil Eye Fear")
        assertEquals(0, TraitEffects.clauseBonus(carrier, emptySet()).total)
        assertNull(TraitEffects.clauseBonus(carrier, emptySet()).line)
    }

    /**
     * The arithmetic names both terms. +3 and -1 were chosen as the carrier
     * pair precisely because 3, -1, 0 and 2 are four different answers: no
     * assertion here survives dropping either clause, which is the vacuity
     * 6de's own targetFor test shipped (12 + 1 - 1 == 12).
     */
    @Test
    fun `declared will clauses sum and undeclared ones do not`() {
        val clauses = willOf("Incorruptible Will", "Evil Eye Fear")
        assertEquals(2, clauses.size)
        val incorruptible = clauses.single { it.trait == "Incorruptible Will" }
        val evilEye = clauses.single { it.trait == "Evil Eye Fear" }
        assertEquals(3, TraitEffects.clauseBonus(clauses, setOf(incorruptible.key)).total)
        assertEquals(-1, TraitEffects.clauseBonus(clauses, setOf(evilEye.key)).total)
        assertEquals(
            2,
            TraitEffects.clauseBonus(clauses, setOf(incorruptible.key, evilEye.key)).total,
        )
        // The sentence and the number come from one call, so they cannot drift.
        val both = TraitEffects.clauseBonus(clauses, clauses.map { it.key }.toSet())
        assertTrue("Incorruptible Will +3" in both.line.orEmpty(), both.line.orEmpty())
        assertTrue("Evil Eye Fear -1" in both.line.orEmpty(), both.line.orEmpty())
    }

    /**
     * **The suite knew and production did not.** SelfControlTest has recorded
     * since 6am that Grief-Stricken's offset "is attached TO a roll ... so it
     * is a modifier and `TraitEffects`' business" -- and `TraitEffects` had
     * never taken the business. Both halves are asserted here: it is NOT a
     * self-control check, and it IS a Will clause.
     */
    @Test
    fun `the row a test routed here is now collected`() {
        val grief = Trait("Grief-Stricken", 5, TraitKind.DISADVANTAGE)
        assertTrue(SelfControl.checksFor(listOf(grief)).isEmpty())
        val clause = TraitEffects.willClauses(listOf(grief)).single()
        assertEquals(-2, clause.value)
        // Its qualifier names no roll this book prints, which is why it
        // reaches every bare Will roll rather than one of them: contrast
        // Hard to Kill's "to avoid death", which picks one of the HP table's
        // PRINTED pair.
        assertTrue("to act clearly" in clause.printed)
        assertEquals(3, TraitEffects.willClauseNames().size)
    }

    /**
     * One list answers all three sites, so the same declaration moves the
     * same number wherever it is made -- and Concentrate takes it through its
     * OWN parameter, never through the one named for reeling and fatigue.
     */
    @Test
    fun `one clause list answers every bare will roll`() {
        val clauses = willOf("Grief-Stricken")
        val bonus = TraitEffects.clauseBonus(clauses, clauses.map { it.key }.toSet()).total
        assertEquals(-2, bonus)
        assertEquals(
            Concentration.targetFor(will = 12) + bonus,
            Concentration.targetFor(will = 12, fromTraits = bonus),
        )
        // Distinct from the fatigue term: passing it as `hurt` would land the
        // same number under someone else's label on screen, so the two
        // parameters must both exist and both count.
        assertEquals(
            Concentration.targetFor(will = 12, hurt = -1, fromTraits = bonus),
            Concentration.targetFor(will = 12) - 1 + bonus,
        )
        val spec = ContentLoader.disadvantages()
            .first { it.selfControl != null }.selfControl!!
        assertEquals(
            SelfControl.targetFor(spec, will = 12, iq = 10, modifier = 0) + bonus,
            SelfControl.targetFor(spec, will = 12, iq = 10, modifier = bonus),
        )
        // The third site. A Fright Check IS a bare Will roll, so the same
        // declaration moves its target too -- and the two clause lists it now
        // takes cannot collide, because [TraitClause.key] is "shown /
        // printed" and no row appears in both (Codex: the name claimed three
        // sites and the body proved two).
        // The invariant the concatenation rests on is that no TRAIT is in
        // both lists -- a same-trait pair is the only way two keys could
        // collide, and it is what a future slice adding a row to the wrong
        // list would break.
        assertEquals(
            emptySet<String>(),
            TraitEffects.frightClauseNames() intersect TraitEffects.willClauseNames(),
        )
        val fright = TraitEffects.frightClauses(listOf(advantage("Fearlessness"))) + clauses
        assertEquals(fright.size, fright.map { it.key }.toSet().size)
        val declaredAll = clauses.map { it.key }.toSet()
        assertEquals(
            1 + bonus,
            TraitEffects.clauseBonus(fright, declaredAll).total,
        )
        assertEquals(
            FrightCheck.against(will = 12, modifier = 0, source = fixed(10)).target + bonus,
            FrightCheck.against(will = 12, modifier = bonus, source = fixed(10)).target,
        )
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
     * The same false sentence sat under Medical Training's Physician clause.
     * Swept over both catalogues, so a third copy reddens rather than ships.
     */
    @Test
    fun `no refusal says this app rolls no default`() {
        val stale = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .flatMap { TraitEffects.reported(listOf(advantage(it.name))) }
            .filter { "no default is rolled" in it.reason }
            .map { it.trait }
        assertEquals(emptyList<String>(), stale, "Divination Arts is rolled at a default")
    }

    // ---- 6es: the complement of known() ------------------------------------

    /**
     * **The one cell that lists `<Skill> +N`, refused whole.** Chapter 3's
     * idiom for a roll bonus is "+N to X"; chapter 6 defines a bare +N as
     * "Attribute + N", and says so of a POTION's entry. This cell is neither
     * table, so which it means is not printed -- and that the two readings
     * agree for a skill already owned is an argument, not a rule (the design
     * review's HIGH). The pin is the idiom: a cell that learns to say "+2 to
     * Occultism" has settled it, and this reddens.
     */
    @Test
    fun `Official Beyonder is refused whole and lifts no skill`() {
        val cell = ContentLoader.advantages().first { it.name == "Official Beyonder" }.notes
        assertTrue(
            cell.contains(
                "Revolver +2, Ritualistic Magic +1, Hidden Lore (Beyonders) +3, Occultism +2",
            ),
            cell,
        )
        val held = listOf(advantage("Official Beyonder"))
        assertTrue("Official Beyonder" in TraitEffects.unappliedNames())
        assertEquals(cell, TraitEffects.reported(held).single().printed)
        val occultism = SkillKey(
            "Occultism", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.AVERAGE,
        )
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(held, occultism))
    }

    /**
     * **A held trait no collection names said nothing at all.** `reported`
     * answered only for [TraitEffects.known], so 65 of the 99 advantages --
     * `Legal Enforcement Powers` on three premades among them -- printed
     * nowhere on the sheet, and a drawback such as `Unsettling Appearance`
     * printed its "-1 to all reaction rolls" with nothing to say whether it
     * had been counted. One sentence for all of them, because a reason per
     * row is sixty reasons to go stale (6eq found seven of 39 had).
     */
    @Test
    fun `a held trait nothing here reads says so`() {
        val rows = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .associateBy { it.name }
        val unread = listOf(
            "Legal Enforcement Powers", "Acute Vision", "Unsettling Appearance",
            // Read for a PRICE and for nothing they print: the sentence says
            // "its price aside" because of these two (the diff review).
            "Wealth: Comfortable", "Social Stigma",
        )
        assertTrue(TraitEffects.UNREAD.startsWith("its price aside"), TraitEffects.UNREAD)
        for (name in unread) {
            val note = TraitEffects.reported(listOf(advantage(name))).single()
            assertEquals(rows.getValue(name).notes, note.printed, name)
            assertEquals(TraitEffects.UNREAD, note.reason, name)
        }
    }

    /**
     * ...and the two ways a row says nothing: applied in full with nothing
     * left over, or printed by no catalogue, where there is no cell to make
     * a claim about.
     */
    @Test
    fun `a trait applied in full or unprinted is not called unread`() {
        val quiet = listOf(
            "${TraitEffects.ALERTNESS} 1", TraitEffects.OUTDOORSMAN, TraitEffects.NIGHT_EYES,
            Corruption.FORTITUDE, "${TraitEffects.HARD_TO_KILL} 1", TraitEffects.ACTING_METHOD,
            "A Name No Catalogue Prints",
        )
        for (name in quiet) {
            assertEquals(emptyList<Any>(), TraitEffects.reported(listOf(advantage(name))), name)
        }
    }

    /**
     * **A row's own name must resolve to that row.** The level strip read
     * `Compulsive Behavior (Indulge Evil Desires) SC 6`'s trailing self-control
     * number as a level, so the builder sold a drawback whose cell, roll and
     * note the sheet then could not find (6ew's sweep, the one row that ends
     * in a number).
     */
    @Test
    fun `every catalogue row name resolves to itself`() {
        val lost = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .map { it.name }.filter { TraitEffects.catalogueName(it) != it }
        assertEquals(emptyList<String>(), lost)
        // ...in the ROW's spelling, whatever the sheet's casing: `reported`
        // checks `known()` case-sensitively, so a name returned as printed
        // told `PHOBIA (SPECIFY)` that a rule it applies was unread (Codex).
        val recased = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .map { it.name }.filter { TraitEffects.catalogueName(it.uppercase()) != it }
        assertEquals(emptyList<String>(), recased)
        assertEquals(
            TraitEffects.reported(listOf(advantage("Phobia (specify)"))),
            TraitEffects.reported(listOf(advantage("PHOBIA (SPECIFY)")))
                .map { it.copy(trait = "Phobia (specify)") },
        )
        assertEquals("Alertness", TraitEffects.catalogueName("Alertness 1"))
    }

    /**
     * **A row that offers its roll was taken as applied in full**, and 28
     * of the 30 self-control rows then said nothing beside cells such as
     * Reckless's *"-1 to any roll where caution would be smarter"* and
     * Reluctant Killer's *"-4 to hit recognizable people ... cannot Aim"*
     * (6es's own successor). One sentence again, the roll excepted. Curious
     * is in the sweep on purpose: its *"or investigate any mystery"* is the
     * table's too, so the sentence is true of a row that prints only its roll.
     */
    @Test
    fun `a self-control row nothing else reads says its roll is all that is applied`() {
        val swept = ContentLoader.disadvantages()
            .filter { it.selfControl != null && it.name !in TraitEffects.known() }
        // 28 when written (6ew); Reluctant Killer left for the Attack dialog.
        assertEquals(27, swept.size)
        for (row in swept) {
            val held = Trait(row.name, 5, TraitKind.DISADVANTAGE)
            // The sentence says the roll is applied: the roll must be offered.
            assertTrue(SelfControl.checksFor(listOf(held)).isNotEmpty(), row.name)
            val note = TraitEffects.reported(listOf(held)).single()
            assertEquals(row.notes, note.printed, row.name)
            assertEquals(TraitEffects.ROLLED_ONLY, note.reason, row.name)
        }
        // A quirk of the same name is offered no roll, so it may not be told
        // the roll is applied: `checksFor` rolls disadvantages alone.
        val quirk = Trait("Reckless", 1, TraitKind.QUIRK)
        assertEquals(emptyList<Any>(), SelfControl.checksFor(listOf(quirk)))
        assertEquals(TraitEffects.UNREAD, TraitEffects.reported(listOf(quirk)).single().reason)
        // A self-control row something else reads keeps its clauses: the
        // whole-cell sentence would be false of Loner's applied -1.
        assertEquals(
            listOf("-1 to social rolls in crowds"),
            TraitEffects.reported(listOf(advantage("Loner"))).map { it.printed },
        )
    }

    /**
     * **One row per held trait, with its cell and what of it is not applied**,
     * which is what the sheet prints. The drawback half is the lookup the
     * sheet did inline, asked through the one resolution: a second catalogue
     * merged in must not change a cell it already found.
     */
    @Test
    fun `every held advantage and drawback is a row with its cell`() {
        val drawbacks = ContentLoader.disadvantages().associateBy { it.name.lowercase() }
        for (sheet in Premades.all()) {
            val traits = sheet.toCharacter().traits
            val boons = TraitEffects.held(traits, TraitKind.ADVANTAGE)
            val banes = TraitEffects.held(traits, TraitKind.DISADVANTAGE)
            assertEquals(traits.filter { it.kind == TraitKind.ADVANTAGE }, boons.map { it.trait })
            assertEquals(
                traits.filter { it.kind == TraitKind.DISADVANTAGE }
                    .map { it to drawbacks[TraitEffects.catalogueName(it.name).lowercase()]?.notes },
                banes.map { it.trait to it.cell },
                sheet.name,
            )
            for (row in boons + banes) {
                assertEquals(TraitEffects.reported(listOf(row.trait)), row.notes, row.shown)
            }
        }
        // Beat Cop's, which the sheet never printed.
        val cop = TraitEffects.held(Premades.byName("Beat Cop").toCharacter().traits, TraitKind.ADVANTAGE)
        val powers = cop.single { it.shown == "Legal Enforcement Powers" }
        assertEquals(TraitEffects.UNREAD, powers.notes.single().reason)
        assertTrue(powers.cell!!.startsWith("Official authority to investigate"), powers.cell)
        // Stated here, not asked of `reported`: applied in full says nothing,
        // and a cell with one clause left over names that clause alone.
        assertEquals(emptyList<Any>(), cop.single { it.shown == "Alertness 1" }.notes)
        assertEquals(
            listOf("recover FP at twice the normal rate"),
            cop.single { it.shown == TraitEffects.FIT }.notes.map { it.printed },
        )
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

    /**
     * Thinned Veil: "Concentration in spiritually active environments at -2"
     * (book line 1472; 6fa). It reaches the Concentrate maneuver's roll, the
     * app's one concentration check, as a declaration the GM makes, and never
     * a Fright Check or a self-control roll, which a Will clause would reach.
     * Capitalisation is no warrant, because the word starts a sentence (the
     * design review). Every other roll that needs focus is said to be the
     * GM's call.
     */
    @Test
    fun `Thinned Veil reaches the Concentrate roll alone, as a declaration`() {
        val cell = ContentLoader.disadvantages().single { it.name == TraitEffects.THINNED_VEIL }.notes
        assertTrue("Concentration in spiritually active environments at -2" in cell, cell)

        val veil = Trait(TraitEffects.THINNED_VEIL, 10, TraitKind.DISADVANTAGE)
        val clause = TraitEffects.concentrationClauses(listOf(veil)).single()
        assertEquals(-2, clause.value)
        assertTrue(clause.conditional)
        assertTrue(clause.printed in cell, clause.printed)
        assertTrue(TraitEffects.willClauses(listOf(veil)).isEmpty())

        // The bare Will clauses still reach the Concentrate roll.
        val grief = Trait("Grief-Stricken", 5, TraitKind.DISADVANTAGE)
        assertEquals(TraitEffects.willClauses(listOf(grief)), TraitEffects.concentrationClauses(listOf(grief)))

        assertTrue(TraitEffects.THINNED_VEIL in TraitEffects.known())
        val note = TraitEffects.reported(listOf(veil)).single()
        assertTrue("GM" in note.reason, note.reason)
        assertEquals(clause.printed, note.printed)
    }

    /**
     * Chronic Insomnia: "rolls requiring sustained concentration at -1"
     * (book lines 1255-1256; 6fb). Declared on the Concentrate roll, because
     * whether THIS concentration is sustained is the GM's to say; the book
     * sets no duration. Its other clause, the FP lost each morning, is the
     * table's to enter on the FP row.
     */
    @Test
    fun `Chronic Insomnia reaches the Concentrate roll alone, as a declaration`() {
        val cell = ContentLoader.disadvantages().single { it.name == TraitEffects.CHRONIC_INSOMNIA }.notes
        val insomnia = Trait(TraitEffects.CHRONIC_INSOMNIA, 5, TraitKind.DISADVANTAGE)

        val clause = TraitEffects.concentrationClauses(listOf(insomnia)).single()
        assertEquals(-1, clause.value)
        assertTrue(clause.conditional)
        assertEquals("rolls requiring sustained concentration at -1", clause.printed)
        assertTrue(clause.printed in cell, cell)
        assertTrue(TraitEffects.willClauses(listOf(insomnia)).isEmpty())

        // Held beside Thinned Veil, each is its own declaration.
        val veil = Trait(TraitEffects.THINNED_VEIL, 10, TraitKind.DISADVANTAGE)
        assertEquals(
            listOf(-1, -2),
            TraitEffects.concentrationClauses(listOf(insomnia, veil)).map { it.value },
        )

        assertTrue(TraitEffects.CHRONIC_INSOMNIA in TraitEffects.known())
        val notes = TraitEffects.reported(listOf(insomnia)).associateBy { it.printed }
        assertEquals(
            setOf(clause.printed, "Lose 1 FP each morning that cannot be recovered through rest"),
            notes.keys,
        )
        assertTrue("GM" in notes.getValue(clause.printed).reason)
        assertTrue("FP row" in notes.getValue("Lose 1 FP each morning that cannot be recovered through rest").reason)
    }

    /** Fast Healer's other clause has no wound to halve (6fc). */
    @Test
    fun `Fast Healer reports the clause the recovery row cannot reach`() {
        assertTrue(TraitEffects.FAST_HEALER in TraitEffects.known())
        val note = TraitEffects.reported(listOf(advantage(TraitEffects.FAST_HEALER))).single()
        assertEquals("Injuries that would leave others bedridden leave you functional in half the time", note.printed)
        assertTrue("wounds" in note.reason, note.reason)
    }

    /**
     * Cleansed Spirit: "CoR gained from passive exposure are halved" (book
     * lines 1012-1013; 6fd). The book lists no CoR source as passive, and this
     * app charges Soul Debt for SPI spent below 0, so it halves nothing and
     * says so beside the CoR stepper, where the GM can.
     */
    @Test
    fun `Cleansed Spirit is said beside the CoR stepper and halves nothing`() {
        val cell = ContentLoader.advantages().single { it.name == TraitEffects.CLEANSED_SPIRIT }.notes
        val held = advantage(TraitEffects.CLEANSED_SPIRIT)

        val note = TraitEffects.corruptionNotes(listOf(held, advantage("Fit"))).single()
        assertEquals(cell, note.printed)
        assertTrue("Soul Debt" in note.reason && "GM" in note.reason, note.reason)
        assertEquals(note, TraitEffects.reported(listOf(held)).single())

        assertTrue(TraitEffects.corruptionNotes(listOf(advantage("Fit"))).isEmpty())
        // A near miss on the name is not the trait (the diff review).
        assertTrue(TraitEffects.corruptionNotes(listOf(advantage("Spiritual Fortitude"))).isEmpty())
        assertTrue(TraitEffects.corruptionNotes(emptyList()).isEmpty())
    }
}
