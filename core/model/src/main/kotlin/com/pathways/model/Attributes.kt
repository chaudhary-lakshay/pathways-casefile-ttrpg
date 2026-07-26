package com.pathways.model

import kotlinx.serialization.Serializable

const val BASE_ATTRIBUTE = 9

/**
 * Upper bound on a purchasable [Attr] level. The rulebook's attribute
 * scale is stated as "15+" and Beyonder progression pushes higher still,
 * but nothing in the book or the 23 published sheets comes remotely close
 * to this figure -- a single attribute at level 30 alone costs tens of
 * millions of character points ([AttributeCosts]'s per-level cost doubles
 * every step). This exists only to catch malformed/corrupted input (a
 * stray extra digit, a bad import) before it reaches
 * [AttributeCosts.costOf], whose doubling ladder overflows `Int` past
 * roughly level 40 -- see KNOWN_ISSUES.
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
                    "for buying below $BASE_ATTRIBUTE, and nothing legal reaches $MAX_ATTRIBUTE); was $v"
            }
        }
    }

    operator fun get(a: Attr): Int = when (a) {
        Attr.ST -> st; Attr.DX -> dx; Attr.IQ -> iq; Attr.HT -> ht
    }
}
