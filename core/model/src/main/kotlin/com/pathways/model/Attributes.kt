package com.pathways.model

import kotlinx.serialization.Serializable

const val BASE_ATTRIBUTE = 9

/**
 * Upper bound on an [Attributes] level, bought OR digested. The rulebook's
 * attribute scale is stated as "15+" and Beyonder progression pushes higher
 * still, and a potion adds to what was bought: ST 17, the highest a purchase
 * may reach ([com.pathways.rules.PurchaseCeilings]), plus Twilight Giant's
 * +11 and the Patriarch boon's +2 is exactly 30. It guards the thrust/swing
 * table, which stops at ST 30.
 */
const val MAX_ATTRIBUTE = 30

/** Purchased attribute levels. SPI is deliberately absent — it cannot be bought. */
@Serializable
data class Attributes(
    val st: Int = BASE_ATTRIBUTE,
    val dx: Int = BASE_ATTRIBUTE,
    val iq: Int = BASE_ATTRIBUTE,
    val ht: Int = BASE_ATTRIBUTE,
) {
    init {
        for ((label, v) in listOf("ST" to st, "DX" to dx, "IQ" to iq, "HT" to ht)) {
            require(v in BASE_ATTRIBUTE..MAX_ATTRIBUTE) {
                "$label must be between $BASE_ATTRIBUTE and $MAX_ATTRIBUTE (the book gives no refund " +
                    "for buying below $BASE_ATTRIBUTE, and nothing legal passes $MAX_ATTRIBUTE); was $v"
            }
        }
    }

    operator fun get(a: Attr): Int = when (a) {
        Attr.ST -> st; Attr.DX -> dx; Attr.IQ -> iq; Attr.HT -> ht
    }
}
