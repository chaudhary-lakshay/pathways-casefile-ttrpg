package com.pathways.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What attribute controls a skill's level.
 *
 * The rulebook's Complete Skill List (Ch. 3) declares controlling attributes
 * that go beyond the four buyable [Attr]s: `Will`, `Per`, and `SPI` all
 * appear as controlling stats. Do NOT widen [Attr] to cover this — [Attr]
 * means exactly "the four attributes that can be raised with character
 * points", and [com.pathways.rules.AttributeCosts] / point-budget semantics
 * depend on that meaning staying narrow. SPI in particular can never be an
 * [Attr]: it starts at 0 for mortals and cannot be raised by points at all.
 */
@Serializable
sealed interface ControllingAttribute {

    /** Short code exactly as the rulebook writes it: ST, DX, IQ, HT, Will, Per, SPI. */
    val code: String

    /** One of the four attributes purchasable with character points. */
    @Serializable
    @SerialName("Buyable")
    data class Buyable(val attr: Attr) : ControllingAttribute {
        override val code: String get() = attr.name
    }

    /** Will, a secondary characteristic derived from IQ + delta. */
    @Serializable
    @SerialName("Will")
    data object Will : ControllingAttribute {
        override val code: String get() = "Will"
    }

    /** Perception, a secondary characteristic derived from IQ + delta. */
    @Serializable
    @SerialName("Per")
    data object Per : ControllingAttribute {
        override val code: String get() = "Per"
    }

    /** Spirit. Purchased/awarded state on Character, never derived from Attr. */
    @Serializable
    @SerialName("SPI")
    data object Spi : ControllingAttribute {
        override val code: String get() = "SPI"
    }

    companion object {
        /**
         * Accepts exactly the seven rulebook spellings, case-insensitively:
         * ST, DX, IQ, HT, Will, Per, SPI.
         */
        fun fromCode(code: String): ControllingAttribute = when (code.trim().uppercase()) {
            "ST" -> Buyable(Attr.ST)
            "DX" -> Buyable(Attr.DX)
            "IQ" -> Buyable(Attr.IQ)
            "HT" -> Buyable(Attr.HT)
            "WILL" -> Will
            "PER" -> Per
            "SPI" -> Spi
            else -> throw IllegalArgumentException("unknown controlling attribute code '$code'")
        }
    }
}
