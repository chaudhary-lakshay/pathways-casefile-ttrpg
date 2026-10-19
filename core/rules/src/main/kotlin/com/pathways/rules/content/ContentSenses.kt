package com.pathways.rules.content

import kotlinx.serialization.Serializable

/** A: one Astral Projection colour and the emotional state it reveals. */
@Serializable
data class AstralColor(val color: String, val meaning: String)

/**
 * B: one Ether Body region and its colour.
 *
 * The INVERSE column order to [AstralColor], and seven of the eight colours
 * appear in both tables meaning unrelated things -- Purple is "Spirituality,
 * madness" there and the BRAIN here. Nothing may join the two on the colour.
 */
@Serializable
data class EtherBodyRegion(val region: String, val color: String)

/** C: one pathway's Sequence 9 refinement of the sight. Reference only, never a gate. */
@Serializable
data class PathwayDifference(val name: String, val sequence: Int, val ability: String)

/** "Cost: 1 SPI per activation, regardless of duration." */
@Serializable
data class SpiritVisionActivation(val spi: Int, val perActivation: Boolean)

/** The Spirit Vision guide whole: the colour tables, the refinements and the price. */
@Serializable
data class SpiritVisionFile(
    val astralColors: List<AstralColor>,
    val etherBody: List<EtherBodyRegion>,
    val etherBodyNote: String,
    val pathwayDifferences: List<PathwayDifference>,
    val activation: SpiritVisionActivation,
)

/** One of ch. 4's two universal sense abilities, in the book's words. */
@Serializable
data class SpiritualSense(val ability: String, val text: String)
