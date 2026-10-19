package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.Attributes
import com.pathways.model.BASE_ATTRIBUTE
import com.pathways.model.MAX_ATTRIBUTE
import com.pathways.rules.content.ContentLoader

/**
 * Ch. 2: all attributes begin at 9 for free, and "each level above the
 * baseline costs a flat rate that does not increase" (v6.9i 540-541): ST 10,
 * DX, IQ and HT 20, read from `attribute_costs.json`.
 */
object AttributeCosts {

    private val perLevel: Map<Attr, Int> by lazy {
        ContentLoader.attributeCosts().associate { Attr.valueOf(it.attr) to it.costPerLevel }
    }

    fun costOf(attr: Attr, level: Int): Int {
        require(level >= BASE_ATTRIBUTE) {
            "attributes cannot be bought below $BASE_ATTRIBUTE (was $level); the book gives no refund"
        }
        require(level <= MAX_ATTRIBUTE) {
            "attribute level $level exceeds the supported maximum of $MAX_ATTRIBUTE"
        }
        return perLevel.getValue(attr) * (level - BASE_ATTRIBUTE)
    }

    fun totalCost(a: Attributes): Int = Attr.entries.sumOf { costOf(it, a[it]) }
}
