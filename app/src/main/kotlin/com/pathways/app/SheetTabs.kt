package com.pathways.app

import com.pathways.rules.AttributeRoll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.rules.SuccessRoll
import com.pathways.rules.TraitReport.HeldTrait
import com.pathways.rules.PoolStatus
import com.pathways.rules.SelfControlCheck
import com.pathways.rules.SheetRow
import com.pathways.rules.Shock
import com.pathways.rules.ClauseBonus
import com.pathways.rules.TraitClause
import com.pathways.rules.TraitGrants
import com.pathways.rules.PremadeSheet

// The sheet's three tabs, each the items it adds to [SheetScreen]'s list.
// Extension functions, not composables: what they emit is what the list
// emitted for that tab before they were split out.

internal fun LazyListScope.vitalsTab(
    diffSummary: String?,
    hpNow: PoolStatus,
    ht: Int,
    traits: List<Trait>,
    hurtOnAttribute: Int,
    shockOnAttribute: Int,
    shocked: Boolean,
    onClearShock: () -> Unit,
    /** Set by the Damage dialog or Defend's chip; one Stunned (play session 5). */
    stunned: Boolean,
    stunRecoveryTarget: Int,
    onClearStun: () -> Unit,
    allRolls: List<TraitClause>,
    declaredStates: Set<String>,
    onDeclareStates: (Set<String>) -> Unit,
    states: ClauseBonus,
    secondaryLines: List<PlayedLine>,
    /** A Will or Per row tapped: its bare roll (play session 4). */
    onRollAttribute: (AttributeRoll.Kind) -> Unit,
) {
    item {
        PoolTrackers(
            hp = hpNow,
            ht = ht,
            traits = traits,
            // The same number every other bare attribute roll
            // takes. A character at 0 HP has been reeling since
            // 1/3 HP, so this is not the state the roll creates.
            poolPenalty = hurtOnAttribute,
            shock = shockOnAttribute,
            states = states,
        )
    }
    // The held rows whose cell prints "-N to all rolls" under a circumstance
    // only the GM can see (6gs). Drawn whenever one is held, lit or not, so
    // lighting one cannot move the rows under a finger; every roll on every
    // screen takes what is lit, and names it.
    if (allRolls.isNotEmpty()) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                ClauseChips(allRolls, declaredStates, onDeclareStates)
                Text(
                    "Lit: on every roll it names until cleared here",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (stunned) item { StunRow(stunRecoveryTarget, onClearStun) }
    if (shocked) {
        item {
            // Only while it is in force: a control that is always
            // there would read as a state the GM must maintain, and
            // this one is latched by the roll that caused it.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Shock — \"${Shock.printed()}\"" +
                        if (Shock.ignoredBy(traits)) {
                            " — ignored by ${Shock.IGNORED_BY}"
                        } else {
                            ""
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                // Cleared by hand, because nothing here counts turns.
                TextButton(onClick = onClearShock) { Text("Clear") }
            }
        }
    }

    // The derived secondaries (HP, Will, Per, Basic Speed...) sit with
    // the pools rather than the skills: they are what the character IS,
    // so each prints the number PLAYED, and the disagreement with the
    // published sheet is printed beside it ([PlayedLine]).
    item { VitalsCard(diffSummary, secondaryLines, onRollAttribute) }
}

internal fun LazyListScope.traitsTab(
    advantages: List<HeldTrait>,
    drawbacks: List<HeldTrait>,
    grantedTraits: List<Trait>,
    /** What gave [grantedTraits]: "the potion", "the boon", or both (6hn). */
    grantedBy: String,
    bought: List<Trait>,
    traits: List<Trait>,
    selfControl: Map<String, SelfControlCheck>,
    standing: Map<String, Int>,
    expanded: Map<String, Boolean>,
    onToggle: (String) -> Unit,
    onControl: (SelfControlCheck) -> Unit,
) {
    // Every held trait, collapsed to its name, price and a word for how much
    // of it is applied (6gl). The book's cell and every reason open under it,
    // so nothing unapplied is silent: the word says so first. Keyed by kind
    // and name, never position, because a potion's grants shift the rows.
    item { TraitLegend() }
    item { SheetSection("Advantages", advantages.size) }
    items(advantages) { row ->
        val key = "a:${row.shown}:${row.trait.cost}"
        TraitRow(
            row = row,
            // A granted row costs nothing and was not bought (6fk); a bought
            // level a potion added to says both (6fm).
            priceLine = when {
                row.trait in grantedTraits -> "from $grantedBy"
                else -> "${row.trait.cost} pts" +
                    TraitGrants.grantedLevelsIn(row.trait, bought)?.let { ", +$it from $grantedBy" }.orEmpty()
            } + superseded(row.trait, traits),
            roll = null,
            expanded = expanded[key] == true,
            onToggle = { onToggle(key) },
            onRoll = {},
        )
    }
    // A quirk prints with a drawback's sign. The one this app makes is a
    // Fright Check's, gained in play at 0 (SavedEdits.quirks).
    item { SheetSection("Disadvantages", drawbacks.size) }
    items(drawbacks) { row ->
        val key = "d:${row.shown}:${row.trait.cost}"
        // Keyed the way `SelfControlCheck.trait` is, so the row and its roll
        // cannot disagree about what the trait is called. Nothing rolls on
        // its own: WHEN to call for one is the GM's.
        val check = selfControl[row.shown]
        TraitRow(
            row = row,
            // `Trait.cost` is a MAGNITUDE and the sign lives in the kind (6u).
            priceLine = drawbackPrice(row.trait, grantedTraits, grantedBy) + superseded(row.trait, traits),
            roll = check?.let { "Roll ${SuccessRoll.printed(standing.getValue(it.trait))}" },
            expanded = expanded[key] == true,
            onToggle = { onToggle(key) },
            onRoll = { check?.let(onControl) },
        )
    }
}

/**
 * A disadvantage's price line. `Trait.cost` is a MAGNITUDE and the sign
 * lives in the kind (6u). No catalogue disadvantage costs 0, so a held one
 * at 0 is one `SavedEdits.gained` billed at nothing.
 */
internal fun drawbackPrice(trait: Trait, grantedTraits: List<Trait>, grantedBy: String): String = when {
    trait in grantedTraits -> "from $grantedBy"
    trait.cost == 0 -> "gained in play"
    else -> "−${trait.cost} pts"
}

internal fun LazyListScope.skillsTab(
    sheet: PremadeSheet,
    rows: List<SheetRow>,
    skillLevels: Map<SkillKey, Int>,
    edits: SavedEdits,
    onRoll: (RollableSkill) -> Unit,
    onPickUntrained: () -> Unit,
) {
    // Row order past the secondaries is one per purchased skill, in
    // sheet order - pinned by SheetComparisonTest. That is what makes
    // this positional pairing safe.
    val published = sheet.skills.mapIndexedNotNull { i, skill ->
        rows.getOrNull(SECONDARY_ROWS + i)?.let { skill to it }
    }
    val bought = edits.purchasedKeys().mapNotNull { key -> liveLevelOrNull(key, skillLevels)?.let { key to it } }
    val granted = grantedSkills(sheet, edits, skillLevels)
    // The rows drawn below, counted from the lists that draw them.
    item { SheetSection("Skills", published.size + bought.size + granted.size) }
    // "You can attempt most skills even without training" (p. 10).
    // First and full width: as a text button below the last skill the owner
    // could not find it in play (session 1).
    item { UntrainedButton(onPickUntrained) }
    items(published) { (skill, row) ->
        // ONE level, printed and rolled: the character in front
        // of the player. A Paragon Seq 9 potion takes this sheet's
        // Research from 13 to 17, and the printed figure is a
        // character who no longer exists.
        //
        // Until 6fz the row SHOWED the unedited sheet's level, on
        // 6e's reasoning that the comparison is the engine
        // against the book and a potion is neither. That is true
        // of the diff, which is still the unedited sheet's and
        // still the only thing in red. It was never a reason to
        // print a level the Roll beside it does not roll: a Docks
        // Bouncer on Twilight Giant 9 read Brawling 12 above a
        // potion's Broadsword 14, and rolled Brawling at 16.
        val level = liveLevelOf(skill.key(), skillLevels, sheet.levelFor(skill))
        val roll = { onRoll(RollableSkill(skill.label, level, skill.key())) }
        ComparisonRow(
            playedLine(row, level.toString()),
            Modifier.clickable(onClickLabel = "Roll", role = Role.Button, onClick = roll),
        )
    }

    // Every row on the screen, so a label can be checked against the
    // others before it is shown. Only the rows BELOW the comparison
    // table are ever suffixed: the published rows render the sheet's
    // own label, and rewriting it would edit the diff that is the
    // engine against the book (6e).
    val addedKeys = sheet.skills.map { it.key() } +
        edits.purchasedKeys() + granted.map { it.first }

    // Skills bought in play. NOT comparison rows: `SheetComparison`
    // keeps using the unedited sheet, because that diff is the engine
    // against the book (6e), so a bought skill has no published
    // column to differ from. Until now it also had no way to be
    // ROLLED -- Ritualistic Magic and Divination Arts got their own
    // buttons and every other purchase was invisible, which is how a
    // chapter's result table could be wired up and still be
    // unreachable. The level comes from [skillLevels], the same
    // digested map the ritual and attack dialogs read.
    // Resolved through the book, exactly as `grantedSkills` resolves
    // them, so the two lists agree on which skill a purchase IS. When
    // they disagreed, a saved skill the catalogue no longer prints
    // failed to suppress itself and a potion's skill of the same name
    // appeared twice, once as bought and once as granted (Codex).
    items(bought) { (key, level) ->
        val label = disambiguated(key, addedKeys)
        AddedSkillRow(label, level, "bought") {
            onRoll(RollableSkill(label, level, key))
        }
    }

    // Skills a POTION granted. The two lists above are one row per
    // published skill and one per purchase, so between them they
    // cannot show a skill the book handed over -- and ch. 9 p. 85
    // forbids buying the three SPI-based ones, making a grant their
    // only route (6aq). Chapter 4 prints a result table for two of
    // them; 6aj wired those up and this is what could reach them.
    //
    // Ritualistic Magic and Divination Arts appear here as well as on
    // their own buttons, deliberately: those buttons cast and divine,
    // and a GM may still call for a plain roll of the skill.
    items(granted) { (key, level) ->
        val label = disambiguated(key, addedKeys)
        AddedSkillRow(label, level, "potion") {
            onRoll(RollableSkill(label, level, key))
        }
    }
}

/**
 * The untrained roll, as the frame draws it: a dashed box the width of the
 * list, a die and its words in capitals. Still the one target it was.
 */
@Composable
private fun UntrainedButton(onClick: () -> Unit) {
    val dash = MaterialTheme.colorScheme.outline
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .drawBehind {
                drawRect(
                    dash,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))),
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Casino, contentDescription = null, Modifier.size(20.dp))
        Caps(
            "Roll a skill this sheet does not have",
            Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
