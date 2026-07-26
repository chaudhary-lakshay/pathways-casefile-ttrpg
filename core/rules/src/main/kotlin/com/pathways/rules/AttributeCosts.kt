package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.Attributes
import com.pathways.model.BASE_ATTRIBUTE
import com.pathways.model.MAX_ATTRIBUTE

/**
 * Ch. 3: all attributes begin at 9 for free, and each level above costs
 * twice the one before it. ST and HT are cheap; DX and IQ are not.
 */
object AttributeCosts {

    private fun perLevelBase(a: Attr): Int = when (a) {
        Attr.ST, Attr.HT -> 10
        Attr.DX, Attr.IQ -> 20
    }

    fun costOf(attr: Attr, level: Int): Int {
        require(level >= BASE_ATTRIBUTE) {
            "attributes cannot be bought below $BASE_ATTRIBUTE (was $level); the book gives no refund"
        }
        // Guards the doubling ladder below, which overflows Int past roughly
        // level 40 -- see MAX_ATTRIBUTE's KDoc for why this bound is safe.
        require(level <= MAX_ATTRIBUTE) {
            "attribute level $level exceeds the supported maximum of $MAX_ATTRIBUTE"
        }
        val base = perLevelBase(attr)
        return (BASE_ATTRIBUTE + 1..level).sumOf { base shl (it - BASE_ATTRIBUTE - 1) }
    }

    fun totalCost(a: Attributes): Int = Attr.entries.sumOf { costOf(it, a[it]) }
}
