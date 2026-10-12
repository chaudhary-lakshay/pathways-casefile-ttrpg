package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.BeyonderState
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** God of Combat's "+3 ST, +3 DX, +3 HT" as a played gain (6hf). */
class BlessingAttributesTest {
    private val combat = Trait(TraitEffects.GOD_OF_COMBAT, 75, TraitKind.ADVANTAGE)
    // Any premade owning a DX skill, so the re-levelling has something to show.
    private val mortal = Premades.all().map { it.toCharacter() }
        .first { c -> c.skills.any { it.key.attr == com.pathways.model.ControllingAttribute.Buyable(Attr.DX) } }
    private val holder = mortal.copy(traits = mortal.traits + combat)

    /**
     * Both ways against the extracted cell. An attribute is "+N ST" ending
     * the clause; Lord of Storms' "+3 HT vs pressure" is a scoped roll, the
     * one near-miss, and the lookahead is what keeps it out.
     */
    @Test
    fun `the gains table is every blessing that prints an attribute`() {
        val printed = Regex("""\+(\d+) (ST|DX|IQ|HT)(?=[,.])""")
        val fromBook = ContentLoader.blessings()
            .associate { b -> b.deity to printed.findAll(b.effect).associate { Attr.valueOf(it.groupValues[2]) to it.groupValues[1].toInt() } }
            .filterValues { it.isNotEmpty() }
        assertEquals(fromBook, Blessings.attributeGainTable())
        assertEquals(mapOf(Attr.ST to 3, Attr.DX to 3, Attr.HT to 3), fromBook[TraitEffects.GOD_OF_COMBAT])
        assertTrue("+3 HT vs pressure" in ContentLoader.blessings().single { it.deity == "Lord of Storms" }.effect)
    }

    /** M3's boundary: the 75 points bought the +3, so the budget never bills it again. */
    @Test
    fun `a mortal holder plays three higher and is billed the trait alone`() {
        val before = Derive.of(mortal)
        val after = Derive.of(holder)
        assertEquals(before.secondaries.maxHp + 3, after.secondaries.maxHp)
        assertEquals(before.secondaries.maxFp + 3, after.secondaries.maxFp)
        assertEquals(before.secondaries.basicSpeedQuarters + 6, after.secondaries.basicSpeedQuarters)
        assertEquals(before.budget.spent + 75, after.budget.spent)
        val dxSkill = before.skillLevels.keys.first { it.attr == com.pathways.model.ControllingAttribute.Buyable(Attr.DX) }
        assertEquals(before.skillLevels.getValue(dxSkill) + 3, after.skillLevels.getValue(dxSkill))
        assertEquals(mortal.attributes.st + 3, Derive.attributes(holder, null).st)
        assertEquals(mortal.attributes, holder.attributes)
    }

    @Test
    fun `a Beyonder holder takes the potion's grants on top`() {
        val state = BeyonderState("Fool", 8)
        val plain = PotionDigestion.of(mortal.copy(beyonder = state), ContentLoader.pathways())!!
        val blessed = PotionDigestion.of(holder.copy(beyonder = state), ContentLoader.pathways())!!
        assertEquals(plain.effectiveAttributes.dx + 3, blessed.effectiveAttributes.dx)
        assertEquals(plain.effectiveAttributes.iq, blessed.effectiveAttributes.iq)
        assertEquals(plain.secondaries.maxHp + 3, blessed.secondaries.maxHp)
        assertEquals(blessed.effectiveAttributes, Derive.attributes(holder, blessed))
    }

    /** Bought + the blessing + the largest walk stays inside ST 30. */
    @Test
    fun `a holder's purchase ceilings are three lower`() {
        val held = listOf(TraitEffects.GOD_OF_COMBAT)
        for (attr in listOf(Attr.ST, Attr.DX, Attr.HT)) {
            assertEquals(PurchaseCeilings.attribute(attr, emptyList()) - 3, PurchaseCeilings.attribute(attr, held))
        }
        assertEquals(PurchaseCeilings.attribute(Attr.IQ, emptyList()), PurchaseCeilings.attribute(Attr.IQ, held))
    }

    /** What lets the ceilings read a save's bought names alone, and keeps the sheet diff still. */
    @Test
    fun `no premade prints a blessing`() {
        val names = PurchasableTraits.blessingNames()
        assertTrue(Premades.all().none { sheet -> sheet.toCharacter().traits.any { it.name in names } })
    }

    /** Applied now, so known() holds it and the report says nothing unapplied. */
    @Test
    fun `the report knows it and disclaims nothing`() {
        assertTrue(TraitEffects.GOD_OF_COMBAT in TraitEffects.known())
        assertTrue(TraitEffects.GOD_OF_COMBAT !in TraitEffects.unappliedNames())
        assertTrue(TraitReport.reported(listOf(combat)).isEmpty())
    }
}
