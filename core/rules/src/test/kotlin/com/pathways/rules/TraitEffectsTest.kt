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
        val byName = ContentLoader.advantages().associateBy { it.name }
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
        // Alertness is the levelled one; the other two are not.
        assertTrue(byName.getValue(TraitEffects.ALERTNESS).perLevel)
        assertTrue(!byName.getValue(TraitEffects.FIT).perLevel)
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
        // The sheet's own spelling is what the GM sees; only the LOOKUP moved.
        assertEquals(glued.name, notes.single().trait)
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
        val line = TraitEffects.knockdownLine(listOf(advantage("High Pain Threshold")))
        assertTrue(line!!.contains(TraitEffects.HIGH_PAIN_THRESHOLD), line)
        assertTrue(line.contains("+3"), line)
        assertNull(TraitEffects.knockdownLine(listOf(advantage(TraitEffects.PERFECT_BALANCE))))
        assertNull(TraitEffects.knockdownLine(emptyList()))
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
        val catalogue = ContentLoader.advantages().associateBy { it.name }
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
        val printed = ContentLoader.advantages().map { it.name }.toSet()
        assertEquals(emptySet<String>(), TraitEffects.known() - printed)
    }
}
