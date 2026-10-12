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

/** What [TraitReport.reported] and [TraitReport.held] say is not applied, and the census behind [TraitEffects.known]. */
class TraitReportedTest {
    private fun advantage(name: String) = Trait(name, 5, TraitKind.ADVANTAGE)
    private fun skill(attr: ControllingAttribute) =
        SkillKey("Whatever", null, attr, Difficulty.AVERAGE)
    private val iq = skill(ControllingAttribute.Buyable(Attr.IQ))

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
        val notes = TraitReport.reported(listOf(advantage("Danger Sense")))
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
        val notes = TraitReport.reported(tough)
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
        assertEquals(3, TraitAttributeRolls.knockdownBonus(tough))
        assertEquals(0, TraitAttributeRolls.knockdownBonus(emptyList()))
        val cell = ContentLoader.advantages()
            .first { it.name == TraitEffects.HIGH_PAIN_THRESHOLD }.notes
        assertTrue(cell.contains("+3 on HT rolls to avoid knockdown"), cell)
        // The row that makes it a knockdown roll is still printed.
        assertTrue(
            ContentLoader.majorWound().any { it.effect.contains("Knocked down") },
            "the major wound table no longer knocks anyone down",
        )
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
        val notes = TraitReport.reported(listOf(glued))
        assertEquals(1, notes.size, "the glued trait reported nothing: $notes")
        // The row prints the effect cell underneath, so leaving it inside the
        // NAME printed the same sentence twice -- which only the device could
        // show, since every assertion about lookup and clause was already
        // green. The sheet's own name survives; its effect does not.
        assertEquals(TraitEffects.RAPID_RECOVERY, notes.single().trait)
        assertEquals(glued.name, TraitNames.displayName(glued.name) + " - " + row.notes)
        assertEquals(row.notes, notes.single().printed)
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
        val notes = TraitReport.reported(healer)
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
     * Voice's "+2 to all rolls to influence others through speech" (599) and
     * Industrial Expertise's "+2 to all relevant skill rolls" (857) each name
     * a purpose, as Healer's general clause does, and no skill or group: the
     * book's two "Influence skills" lists (563-564, 7645) differ and neither
     * is Voice's phrase. Refused on Healer's reason rather than read as
     * applying nothing (6gj).
     */
    @Test
    fun `Voice and Industrial Expertise are refused on Healer's reason`() {
        val healer = TraitRefusals.PARTLY_APPLIED.getValue("Healer").first().second
        val fastTalk = SkillKey("Fast-Talk", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.AVERAGE)
        for (name in listOf("Voice", "Industrial Expertise")) {
            val held = listOf(advantage(name))
            assertEquals(healer, TraitReport.reported(held).single().reason, name)
            assertEquals(emptyList<Any>(), TraitClauses.skillRollClauses(held, fastTalk), name)
            assertEquals(emptyList<Any>(), TraitEffects.modifiers(held, fastTalk), name)
        }
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
            val notes = TraitReport.reported(listOf(advantage(name)))
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
            TraitAttributeRolls.htRollNames() +
            TraitAttributeRolls.spiRollNames() +
            TraitEffects.categoryBonusNames() +
            // The named-skill declarations (6ff): a cell nothing walks is a
            // cell nothing checks.
            TraitClauses.skillClauseNames()
        for (trait in applied) {
            val cell = catalogue.first { it.name == trait }.notes
            val reported = TraitReport.reported(listOf(advantage(trait))).map { it.printed }
            val segments = cell.split(';', '.').map { it.trim() }.filter { it.isNotEmpty() }
            // A level the trait grants for a skill not owned, asked of the
            // list that offers it and never re-derived here (6gy).
            val granted = SkillDefaults.offered(emptyMap(), { 10 }, 0, listOf(advantage(trait)))
                .filter { trait in it.source }
                .map { it.key.name }
            for (segment in segments) {
                val cap = TraitEffects.maxLevelsFor(trait)
                val handled = segment in prose ||
                    // the applied clause names the skills it lifts
                    TraitEffects.skillsFor(trait).any { segment.contains(it) } ||
                    // ...or, for Fit and Unfit, names the roll class instead:
                    // "to all HT rolls" is the clause htRollBonus applies.
                    (trait in TraitAttributeRolls.htRollNames() && segment.contains("HT rolls")) ||
                    // ...and the same for the SPI map, whose applied clause
                    // names its roll class rather than a skill.
                    (
                        trait in TraitAttributeRolls.spiRollNames() &&
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
                    // ...and a self-control roll the row is offered, which
                    // SelfControl applies (6gj): the "roll vs" head and the
                    // "IQ to ..." trigger after it. Never an "or ..."
                    // consequence, which nothing applies (the diff review).
                    (
                        SelfControl.checksFor(listOf(Trait(trait, 5, TraitKind.DISADVANTAGE))).isNotEmpty() &&
                            (
                                segment.matches(Regex("""(?i)(must )?roll vs""")) ||
                                    segment.matches(Regex("""(IQ|Will|Per|HT)([+-]\d+)? to .*"""))
                                )
                        ) ||
                    // ...and a declared clause on one skill's roll.
                    TraitClauses.skillClauseRows().any {
                        it.trait == trait && (segment.contains(it.printed) || it.printed.contains(segment))
                    } ||
                    granted.any { segment.startsWith("Use $it at") } ||
                    reported.any { it.contains(segment) || segment.contains(it) }
                assertTrue(handled, "$trait drops the clause \"$segment\"")
            }
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
        val notes = TraitReport.reported(perfect)
        assertEquals(1, notes.size, "$notes")
        assertTrue(notes.single().printed.contains("+6 to avoid knockdown"), "$notes")
        // The applied half is NOT disclaimed, or the report would be false.
        assertTrue(notes.none { it.printed.contains("Acrobatics") }, "$notes")
        // And it reaches the one knockdown roll this app makes: nothing.
        assertEquals(0, TraitAttributeRolls.knockdownBonus(perfect))
        assertEquals(0, TraitAttributeRolls.knockdownBonus(listOf(advantage(TraitEffects.GREAT_BALANCE))))
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
            val notes = TraitReport.reported(listOf(advantage(name)))
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
        val effect = TraitAttributeRolls.majorWoundHt(listOf(advantage("High Pain Threshold")))
        assertTrue(effect.line!!.contains(TraitEffects.HIGH_PAIN_THRESHOLD), effect.line!!)
        assertTrue(effect.line!!.contains("+3"), effect.line!!)
        // The Balance rows print their own knockdown clause and reach this
        // roll with nothing, because it names no attribute (6bp).
        assertNull(TraitAttributeRolls.majorWoundHt(listOf(advantage(TraitEffects.PERFECT_BALANCE))).line)
        assertNull(TraitAttributeRolls.majorWoundHt(emptyList()).line)
    }

    /**
     * A WHOLLY applied trait is not also reported: it would read as unhandled.
     *
     * Alertness is the only one of the three. Its cell prints one rule and
     * that rule has a join, so there is nothing left over to say.
     */
    @Test
    fun `a wholly applied trait is absent from the reported list`() {
        assertEquals(emptyList<TraitNote>(), TraitReport.reported(listOf(advantage("Alertness 1"))))
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
        val notes = TraitReport.reported(listOf(advantage("Combat Reflexes"), advantage("Fit")))
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
            for (note in TraitReport.reported(listOf(advantage(trait)))) {
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
     * Phobia's "-1 to all rolls" half was REPORTED here until 6gs, because
     * "a second declared all-rolls state is its own slice". That slice is
     * [TraitClauses.allRollsClauses], so the half is applied and nothing of
     * Phobia is left to report. Fearlessness's intimidation half still needs
     * a contest between two characters' levels, which this app cannot hold.
     */
    @Test
    fun `the clauses a fright row prints beside its modifier are reported`() {
        val phobia = TraitReport.reported(
            listOf(Trait("Phobia (Cities)", 10, TraitKind.DISADVANTAGE)),
        )
        assertTrue(phobia.none { "-1 to all rolls" in it.printed }, phobia.toString())
        val fearless = TraitReport.reported(listOf(advantage("Fearlessness")))
        assertTrue(fearless.any { "intimidation" in it.printed }, fearless.toString())
    }

    /**
     * The same false sentence sat under Medical Training's Physician clause.
     * Swept over both catalogues, so a third copy reddens rather than ships.
     */
    @Test
    fun `no refusal says this app rolls no default`() {
        val stale = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .flatMap { TraitReport.reported(listOf(advantage(it.name))) }
            .filter { "no default is rolled" in it.reason }
            .map { it.trait }
        assertEquals(emptyList<String>(), stale, "Divination Arts is rolled at a default")
    }

    /**
     * The Defend dialog has kept a round since 6cp (`RoundState`, **New
     * round**), and 6eq found the Retreat note denying it. Eternal Blazing
     * Sun's refusal said the same in a string 6eq's table never reached.
     * The blessings are in neither catalogue, so this reads the registries.
     */
    @Test
    fun `no refusal says this app keeps no round`() {
        val reasons = TraitRefusals.UNAPPLIED.toList() +
            TraitRefusals.PARTLY_APPLIED.flatMap { (trait, clauses) -> clauses.map { trait to it.second } }
        val stale = reasons.filter { Regex("\\b(no|nor a|without a) rounds?\\b").containsMatchIn(it.second) }.map { it.first }
        assertEquals(emptyList<String>(), stale, "the Defend dialog keeps one")
    }

    /**
     * 6eq reworded that clause to "the Skills tab has no row for a level a
     * trait grants ... a gap, not a ruling". The row exists now (6gy), so
     * the note is gone, and the guard reads both objects: a note may return
     * only if the list stops offering the level.
     */
    @Test
    fun `Medical Training's Physician is offered, so nothing of the row is reported`() {
        val held = listOf(advantage(SkillDefaults.MEDICAL_TRAINING))
        val physician = SkillDefaults.offered(emptyMap(), { 10 }, 0, held)
            .single { it.key.name == SkillDefaults.PHYSICIAN }
        assertEquals(8, physician.level)
        assertEquals(emptyList<Any>(), TraitReport.reported(held))
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
        assertEquals(cell, TraitReport.reported(held).single().printed)
        val occultism = SkillKey(
            "Occultism", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.AVERAGE,
        )
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(held, occultism))
    }

    /**
     * **Anchored Soul is not the mirror of Incorruptible Will (6gw).** Its
     * "-2 against you" is on the attempt. Every "against you" the book prints
     * with a number is the other party's side: "All divination attempts
     * against you are at -2" (3855), "Ranged attacks against you suffer -4 to
     * hit" (8252), "their abilities against you at +1" (1460). Where the
     * holder is the one who rolls ("The target rolls Will-6 to resist", 8529;
     * a potion drawback's self-control roll, which is the cell's "pathway
     * side effects") no line turns the -2 into a +2 on that roll. A reason
     * naming the other party alone would be false of that half, so the
     * refusal says both.
     */
    @Test
    fun `Anchored Soul is refused whole, on the attempt and on the holder's own roll`() {
        val cell = ContentLoader.advantages().first { it.name == "Anchored Soul" }.notes
        assertTrue(cell.endsWith("are at -2 against you."), cell)
        val note = TraitReport.reported(listOf(advantage("Anchored Soul"))).single()
        assertEquals(cell, note.printed)
        assertTrue("other party's roll" in note.reason, note.reason)
        assertTrue("self-control roll" in note.reason, note.reason)
        // Refused whole, so not also offered as a chip on a Will roll.
        assertTrue("Anchored Soul" !in TraitClauses.willClauseNames())
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
            // Mentor replaces Acute Vision, which 6fh reads.
            "Legal Enforcement Powers", "Mentor", "Unsettling Appearance",
            // Read for a PRICE and for nothing they print: the sentence says
            // "its price aside" because of these two (the diff review).
            "Wealth: Comfortable", "Social Stigma",
        )
        assertTrue(TraitReport.UNREAD.startsWith("its price aside"), TraitReport.UNREAD)
        for (name in unread) {
            val note = TraitReport.reported(listOf(advantage(name))).single()
            assertEquals(rows.getValue(name).notes, note.printed, name)
            assertEquals(TraitReport.UNREAD, note.reason, name)
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
            assertEquals(emptyList<Any>(), TraitReport.reported(listOf(advantage(name))), name)
        }
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
        // 28 when written (6ew); Reluctant Killer left for the Attack dialog,
        // and Absent-Mindedness for every skill roll (6gj), and Reckless for
        // the declared all-rolls chips (6hg).
        assertEquals(25, swept.size)
        for (row in swept) {
            val held = Trait(row.name, 5, TraitKind.DISADVANTAGE)
            // The sentence says the roll is applied: the roll must be offered.
            assertTrue(SelfControl.checksFor(listOf(held)).isNotEmpty(), row.name)
            val note = TraitReport.reported(listOf(held)).single()
            assertEquals(row.notes, note.printed, row.name)
            assertEquals(TraitReport.ROLLED_ONLY, note.reason, row.name)
        }
        // A quirk of the same name is offered no roll, so it may not be told
        // the roll is applied: `checksFor` rolls disadvantages alone.
        // (Reckless was the example until 6hg read it; any swept row serves.)
        val quirk = Trait(swept.first().name, 1, TraitKind.QUIRK)
        assertEquals(emptyList<Any>(), SelfControl.checksFor(listOf(quirk)))
        assertEquals(TraitReport.UNREAD, TraitReport.reported(listOf(quirk)).single().reason)
        // A self-control row something else reads keeps its clauses: the
        // whole-cell sentence would be false of Loner's applied -1.
        assertEquals(
            listOf("-1 to social rolls in crowds"),
            TraitReport.reported(listOf(advantage("Loner"))).map { it.printed },
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
        // Nine sheets' spelling of the book's Struggling, which has its row's cell (6fr).
        val poverty = Trait("Poverty: Struggling", 10, TraitKind.DISADVANTAGE)
        val struggling = "Start with 15 soli; modest room; occasional luxuries"
        for (sheet in Premades.all()) {
            val traits = sheet.toCharacter().traits
            val boons = TraitReport.held(traits, TraitKind.ADVANTAGE)
            val banes = TraitReport.held(traits, TraitKind.DISADVANTAGE)
            assertEquals(traits.filter { it.kind == TraitKind.ADVANTAGE }, boons.map { it.trait })
            assertEquals(
                traits.filter { it.kind == TraitKind.DISADVANTAGE }.map { owned ->
                    val row = drawbacks[TraitNames.catalogueName(owned.name).lowercase()]?.notes
                    owned to (row ?: struggling.takeIf { owned == poverty })
                },
                banes.map { it.trait to it.cell },
                sheet.name,
            )
            for (row in (boons + banes).filter { it.trait != poverty }) {
                assertEquals(TraitReport.reported(listOf(row.trait)), row.notes, row.shown)
            }
        }
        // Beat Cop's, which the sheet never printed.
        val cop = TraitReport.held(Premades.byName("Beat Cop").toCharacter().traits, TraitKind.ADVANTAGE)
        val powers = cop.single { it.shown == "Legal Enforcement Powers" }
        assertEquals(TraitReport.UNREAD, powers.notes.single().reason)
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
     * **Every held row a character can reach prints a cell, but the sheet
     * spellings 4.1 records** (6fr). Run over every fixture, every wealth
     * and appearance level, every offered and instanced purchase, every
     * language and every potion grant. 6fq found a face by eye; this found
     * two wealth levels, nine sheets' Poverty and five potion senses. A new
     * row in one of these sources fails here; a new SOURCE of held traits
     * (the app's composition is `chosenTraits` plus `withGrantedTraits`)
     * does not, and must be added to this list by hand.
     */
    @Test
    fun `no reachable held row prints without a cell but a recorded sheet spelling`() {
        val held = reachableHeldTraits()
        val bare = held.filter { TraitReport.held(listOf(it), it.kind).single().cell == null }
        assertEquals(
            setOf(
                // Resemblance only, recorded: the book prints the row under another name.
                "Absent-Minded", "No Sense of Humour", "Wierdness Magnet", "Paranoia: Bodies of water",
                "Blacklisted: Specific Industry org or church", "Pacifism (Cannot Kill)", "Pacifism (Cannot Harm Innocents)",
                // University Librarian's, at 5 pts: no level's price, so no level (Wealth.levelOf).
                "Poverty: Struggling",
            ),
            bare.map { it.name }.toSet(),
        )
        assertEquals(listOf(5), bare.filter { it.name == "Poverty: Struggling" }.map { it.cost })
    }

    /** Fast Healer's other clause has no wound to halve (6fc). */
    @Test
    fun `Fast Healer reports the clause the recovery row cannot reach`() {
        assertTrue(TraitEffects.FAST_HEALER in TraitEffects.known())
        val note = TraitReport.reported(listOf(advantage(TraitEffects.FAST_HEALER))).single()
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
    fun `the Cleansed Spirit note is its own row, for a holder alone`() {
        val cell = ContentLoader.advantages().single { it.name == TraitEffects.CLEANSED_SPIRIT }.notes
        val held = advantage(TraitEffects.CLEANSED_SPIRIT)

        val note = TraitReport.corruptionNotes(listOf(held, advantage("Fit"))).single()
        assertEquals(cell, note.printed)
        assertTrue("Soul Debt" in note.reason && "GM" in note.reason, note.reason)
        assertEquals(note, TraitReport.reported(listOf(held)).single())

        assertTrue(TraitReport.corruptionNotes(listOf(advantage("Fit"))).isEmpty())
        // A near miss on the name is not the trait (the diff review). It must
        // report something, or a loose filter passes through reported() empty:
        // Spiritual Fortitude is applied in full and could not catch one.
        val nearMiss = listOf(advantage("Spirit Tongue"))
        assertTrue(TraitReport.reported(nearMiss).isNotEmpty())
        assertTrue(TraitReport.corruptionNotes(nearMiss).isEmpty())
        assertTrue(TraitReport.corruptionNotes(emptyList()).isEmpty())
    }
}
