package com.pathways.rules

import com.pathways.model.Trait

/**
 * Every held trait a character can reach (6fr's census): every fixture,
 * every wealth and appearance level, every offered and instanced purchase,
 * every language and every potion grant. A new SOURCE of held traits (the
 * app's composition is `chosenTraits` plus `withGrantedTraits`) must be
 * added here by hand.
 */
internal fun reachableHeldTraits(): List<Trait> {
    val held = mutableListOf<Trait>()
    Premades.all().forEach { held += it.toCharacter().traits }
    held += Wealth.all().mapNotNull(Wealth::traitFor)
    held += Appearance.all().map(Appearance::traitFor)
    held += PurchasableTraits.offered().map { it.toTrait() }
    held += TraitGrants.grantedTraitTable().values.flatten()
    for (lang in Languages.all()) for (p in Languages.proficiencies(lang.type)) for (form in Languages.forms()) {
        held += Languages.traits(listOf(LanguagePurchase(lang.name, p.proficiency, form)), emptyList())
    }
    for (row in PurchasableTraits.instantiable()) {
        val purchases = row.variants.map { (v, cost) -> InstancedPurchase(row.name, variant = v, cost = cost) }
            .ifEmpty { listOf(InstancedPurchase(row.name, instance = "Census", cost = row.costMin)) }
        // One at a time: a row's variants are severities, and it admits one.
        held += purchases.map { PurchasableTraits.resolved(listOf(it)).single().second!! }
    }
    return held
}
