package com.pathways.app

import com.pathways.model.Attr
import com.pathways.model.BeyonderState
import com.pathways.rules.PremadeSheet
import com.pathways.rules.PublishedValues
import com.pathways.rules.RandomSource

/** Always rolls [face], so a PCR outcome is a decision, not a coin toss. */
internal fun fixed(face: Int) = RandomSource { face }

internal val fool9 = BeyonderState("Fool", 9)
internal val fool8 = BeyonderState("Fool", 8)

internal fun session(
    saved: SavedSession = SavedSession(),
    sheet: PremadeSheet? = null,
): Pair<BeyonderSession, () -> SavedSession> {
    var last = saved
    return BeyonderSession(saved, sheet) { last = it } to { last }
}

/** Every attribute at the book's 9, and IQ at [iq]. */
internal fun plain(iq: Int = 9) = PremadeSheet(
    "plain",
    Attr.entries.associateWith { if (it == Attr.IQ) iq else 9 },
    PublishedValues(),
)
