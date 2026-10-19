package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.rules.content.AstralColor
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.EtherBodyRegion
import com.pathways.rules.content.PathwayDifference

/**
 * Reading a soul (ch. 4's universal ability, ch. 5's "A Complete Guide").
 *
 * "Cost: 1 SPI per activation, regardless of duration." v6.9i prints no roll
 * and no result table: the sight shows what it shows, and the two colour
 * tables say what a colour means.
 *
 * **Held through the advantage, never a pathway.** "Pathway and Sequence do
 * not grant access to it." It is the 10-point Spirit Vision advantage, or
 * the technique learned in play, which "is recorded on the character sheet
 * exactly as if it had been bought" -- so both arrive as the same trait.
 */
object SpiritVision {

    /** The advantage's catalogue name. */
    const val TRAIT = "Spirit Vision"

    private fun file() = ContentLoader.spiritVision()

    /** A: the eight Astral Projection colours and what each reveals. */
    fun astralColors(): List<AstralColor> = file().astralColors

    /** B: the eight Ether Body regions and their colours. */
    fun etherBody(): List<EtherBodyRegion> = file().etherBody

    /** B's closing sentence, which is a rule about reading the table. */
    fun etherBodyNote(): String = file().etherBodyNote

    /** C: how a pathway's Sequence 9 shapes the sight. Reference, never a gate. */
    fun pathwayDifferences(): List<PathwayDifference> = file().pathwayDifferences

    /** SPI to switch it on, once per activation. */
    fun activation(): Int = file().activation.spi

    /** Whether [traits] hold the ability. */
    fun isHeldBy(traits: List<Trait>): Boolean = TraitNames.levelOf(traits, TRAIT) != null
}
