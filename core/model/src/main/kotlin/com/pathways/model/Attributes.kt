package com.pathways.model

import kotlinx.serialization.Serializable

const val BASE_ATTRIBUTE = 9

/** Purchased attribute levels. SPI is deliberately absent — it cannot be bought. */
@Serializable
data class Attributes(
    val st: Int = BASE_ATTRIBUTE,
    val dx: Int = BASE_ATTRIBUTE,
    val iq: Int = BASE_ATTRIBUTE,
    val ht: Int = BASE_ATTRIBUTE,
) {
    operator fun get(a: Attr): Int = when (a) {
        Attr.ST -> st; Attr.DX -> dx; Attr.IQ -> iq; Attr.HT -> ht
    }
}
