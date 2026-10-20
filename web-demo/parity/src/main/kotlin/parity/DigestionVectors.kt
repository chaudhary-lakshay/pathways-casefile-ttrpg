package parity

import com.pathways.model.BeyonderState
import com.pathways.model.BoonState
import com.pathways.rules.NonStandardPathways
import com.pathways.rules.PotionDigestion
import com.pathways.rules.Premades
import com.pathways.rules.content.ContentLoader

/** Premades the walks start from: a fighter, a scholar, a weakling. */
internal val WALKERS = listOf("Docks Bouncer", "Monastery Scholar", "Street Acrobat")

fun Vectors.digestionVectors() {
    val blocks = ContentLoader.pathways()
    val walks = blocks.map { it.pathway to it.sequence }
    for (name in WALKERS) {
        val c = Premades.all().single { it.name == name }.toCharacter().copy(beyonder = null, boon = null)
        for ((pathway, sequence) in walks) {
            case("PotionDigestion.digestThrough", name, pathway, sequence) {
                PotionDigestion.digestThrough(c, pathway, sequence, blocks)
            }
        }
        for (digestion in listOf(0, 55, 100)) {
            val walked = c.copy(beyonder = BeyonderState("Fool", 8, digestion))
            case("PotionDigestion.of", name, BeyonderState("Fool", 8, digestion), null) { PotionDigestion.of(walked, blocks) }
        }
        case("PotionDigestion.of", name, null, null) { PotionDigestion.of(c, blocks) }
        for (patron in NonStandardPathways.patrons()) {
            for (boon in NonStandardPathways.knownSequences(patron).map { BoonState(patron, it) }.take(2)) {
                val state = BeyonderState("Fool", 9, 0)
                case("PotionDigestion.of", name, state, boon) {
                    PotionDigestion.of(c.copy(beyonder = state, boon = boon), blocks)
                }
            }
        }
    }
    for (p in blocks.map { it.pathway }.distinct()) {
        case("PotionDigestion.largestGrants", p) { PotionDigestion.largestGrants(blocks.filter { it.pathway == p }) }
    }
    case("PotionDigestion.largestGrants", null) { PotionDigestion.largestGrants(blocks) }
    case("PotionDigestion.largestGrantsWithBoons") {
        PotionDigestion.largestGrants(blocks, NonStandardPathways.all())
    }
    val skills = catalogueSkills()
    for (a in skills.take(40)) for (b in skills.take(40)) {
        case("PotionDigestion.isSameSkill", a, b) { PotionDigestion.isSameSkill(a, b) }
    }
    for (p in NonStandardPathways.pathways()) {
        case("NonStandardPathways.knownSequences", p) { NonStandardPathways.knownSequences(p) }
        case("NonStandardPathways.alsoPrintedAs", p) { NonStandardPathways.alsoPrintedAs(p) }
        case("NonStandardPathways.patronOf", p) { NonStandardPathways.patronOf(p) }
        for (s in 0..9) case("NonStandardPathways.blockFor", p, s) { NonStandardPathways.blockFor(p, s)?.name }
    }
    for (patron in NonStandardPathways.patrons()) {
        case("NonStandardPathways.nextRung", null, patron) { NonStandardPathways.nextRung(null, patron) }
        for (s in NonStandardPathways.knownSequences(patron)) {
            val b = BoonState(patron, s)
            case("NonStandardPathways.nextRung", b, patron) { NonStandardPathways.nextRung(b, patron) }
            case("NonStandardPathways.chainFor", b) { NonStandardPathways.chainFor(b).map { it.name } }
        }
    }
}
