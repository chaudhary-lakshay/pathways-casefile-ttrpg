package parity

import com.pathways.app.BeyonderSession
import com.pathways.app.SavedEdits
import com.pathways.app.SavedInstancedTrait
import com.pathways.app.SavedLanguage
import com.pathways.app.SavedSession
import com.pathways.app.attributesWith
import com.pathways.app.carriedWeapons
import com.pathways.app.cleared
import com.pathways.app.corCeilingOn
import com.pathways.app.grantedSkills
import com.pathways.app.liveSkillLevels
import com.pathways.app.newCharacterRefusal
import com.pathways.app.ownSheets
import com.pathways.app.ownedLanguages
import com.pathways.app.pricedWith
import com.pathways.app.secondariesWith
import com.pathways.app.shortfallOn
import com.pathways.app.takenNames
import com.pathways.app.wealthShownFor
import com.pathways.model.Attr
import com.pathways.model.BeyonderState
import com.pathways.model.BoonState
import com.pathways.model.SkillKey
import com.pathways.rules.Appearance
import com.pathways.rules.Languages
import com.pathways.rules.NonStandardPathways
import com.pathways.rules.Pool
import com.pathways.rules.Premades
import com.pathways.rules.PremadeSheet
import com.pathways.rules.PurchasableTraits
import com.pathways.rules.Rest
import com.pathways.rules.TraitGrants
import com.pathways.rules.Wealth
import com.pathways.rules.Weapons
import com.pathways.rules.content.ContentLoader
import kotlin.random.Random

/** One step of a script: its name, then its arguments, as JSON can carry them. */
private typealias Op = List<Any?>

/**
 * Random operation scripts over every premade: each step records what it
 * returned or threw, every save it emitted, and the session after it.
 */
fun Vectors.sessionVectors() {
    val sheets = Premades.all()
    for ((i, sheet) in sheets.withIndex()) for (variant in 0..3) {
        val seed = i * 10 + variant
        case("session.script", sheet.name, seed, startOf(variant, seed)) {
            script(sheet, startOf(variant, seed), Random(seed))
        }
    }
    characterVectors2(sheets)
    storeVectors()
}

/** The save a script starts from: empty, out of bounds (repaired on load), or walked. */
private fun startOf(variant: Int, seed: Int): SavedSession {
    val r = Random(seed + 1000)
    return when (variant) {
        0 -> SavedSession()
        1 -> SavedSession(
            cor = r.nextInt(0, 25),
            edits = SavedEdits(
                st = r.nextInt(5, 40), dx = r.nextInt(3, 12), ht = r.nextInt(9, 35),
                secondaries = mapOf("hp" to r.nextInt(-60, 5), "will" to r.nextInt(-12, 3), "basicMove" to 150),
                traits = PurchasableTraits.offered().shuffled(r).take(3).map { it.name },
            ),
        )
        3 -> SavedSession(basedOn = Premades.all()[0].name, hpLost = 2, edits = SavedEdits(quirks = listOf("Hums")))
        else -> {
            val pathway = ContentLoader.pathways().map { it.pathway }.distinct().random(r)
            SavedSession(beyonder = BeyonderState(pathway, r.nextInt(7, 10), r.nextInt(0, 101)), cor = r.nextInt(0, 6))
        }
    }
}

private fun script(sheet: PremadeSheet, start: SavedSession, r: Random): List<Any?> {
    val emitted = mutableListOf<SavedSession>()
    val session = BeyonderSession(start, sheet) { emitted += it }
    val steps = mutableListOf<Any?>(listOf("init", emitted.toList(), snapshot(session)))
    repeat(70) {
        emitted.clear()
        val op = randomOp(session, sheet, r)
        val result = try {
            mapOf("out" to run(session, sheet, op))
        } catch (e: RuntimeException) {
            mapOf("throws" to e::class.simpleName)
        }
        steps.add(listOf(op, result, emitted.toList(), snapshot(session)))
    }
    steps.add(listOf("derived", derived(session, sheet)))
    return steps
}

private fun snapshot(s: BeyonderSession) = SavedSession(
    s.state, s.lost, s.cor, s.corrupted, s.hpLost, s.fpLost, s.spiSpent, s.edits, s.pendingPcr, null,
)

private fun derived(s: BeyonderSession, sheet: PremadeSheet): List<Any?> {
    val c = s.characterFor(sheet)
    return listOf(
        c,
        s.traitsInPlay(sheet),
        grantedSkills(sheet, s.edits, liveSkillLevels(c)),
        s.edits.carriedWeapons(),
        s.edits.ownedLanguages(sheet.toCharacter().traits),
        s.edits.pricedWith(SavedLanguage("Intis", "Accented", Languages.SPOKEN_ONLY), sheet.toCharacter().traits),
        s.edits.wealthShownFor(c),
        c.attributesWith(s.state),
        c.secondariesWith(s.state),
        s.edits.shortfallOn(sheet, s.state),
        s.edits.corCeilingOn(sheet, s.state),
    )
}

private val QUIRKS = listOf("Hums", " Hums ", "", "Collects buttons")
private val LANGUAGES = listOf(
    SavedLanguage("Intis", "Accented", Languages.SPOKEN_ONLY),
    SavedLanguage("Feysac", "Native", Languages.BOTH_FORMS),
    SavedLanguage("Ancient Hermes", "Broken", Languages.WRITTEN_ONLY),
    SavedLanguage("intis", "Native", Languages.BOTH_FORMS),
    SavedLanguage("Nope", "Native", Languages.BOTH_FORMS),
)

private fun randomOp(s: BeyonderSession, sheet: PremadeSheet, r: Random): Op {
    val skills = ContentLoader.skills()
    val offered = PurchasableTraits.offered()
    val attr = Attr.entries.random(r)
    val secondary = (ContentLoader.secondaryCosts().map { it.key } + "spi").random(r)
    val skill = (s.edits.skills.map { it.name to it.specialization } + skills.take(60).map { it.name to it.specialization }).random(r)
    val held = s.edits.traits + s.edits.gained
    return when (r.nextInt(0, 34)) {
        0 -> listOf("setAttribute", attr, r.nextInt(5, 33))
        1 -> listOf("canSetAttribute", attr, r.nextInt(5, 33))
        2 -> listOf("setSecondary", secondary, r.nextInt(-8, 9))
        3 -> listOf("canSetSecondary", secondary, r.nextInt(-8, 9))
        4 -> listOf("carryWeapon", (Weapons.all().map { it.name } + "nope").random(r))
        5 -> listOf("dropWeapon", (s.edits.weapons + Weapons.all().first().name.lowercase()).random(r))
        6 -> listOf("setWealth", (Wealth.all().map { it.status } + listOf(null)).random(r))
        7 -> listOf("setAppearance", (Appearance.all().map { it.level } + listOf(null)).random(r))
        8, 9 -> listOf("buyTrait", offered.random(r).name)
        10 -> listOf("buyRefusal", offered.random(r).name)
        11 -> listOf("sellTrait", (held + offered.random(r).name).random(r))
        12 -> listOf("canSellTrait", (held + offered.random(r).name).random(r))
        13 -> listOf("addQuirk", QUIRKS.random(r))
        14 -> listOf("removeQuirk", (s.edits.quirks + "Hums").random(r))
        15 -> listOf("setGained", (held + offered.random(r).name).random(r), r.nextBoolean())
        16 -> skills.random(r).let { listOf("setCharmedSkill", SkillKey(it.name, it.specialization, it.attr, it.difficulty)) }
        17 -> PurchasableTraits.instantiable().random(r).let { row ->
            val variant = row.variants.randomOrNull(r)
            listOf("buyInstancedTrait", SavedInstancedTrait(row.name, variant?.first, listOf("Rats", "rats ", "Fire").random(r), variant?.second ?: row.costMin))
        }
        18 -> if (s.edits.instanced.isEmpty()) listOf("sellInstancedTrait", 0, SavedInstancedTrait("x", cost = 1))
        else r.nextInt(0, s.edits.instanced.size).let { listOf("sellInstancedTrait", it, s.edits.instanced[it]) }
        19 -> listOf("buyLanguage", LANGUAGES.random(r))
        20 -> listOf("sellLanguage", (s.edits.languages.map { it.language } + "INTIS").random(r))
        21 -> listOf("setStudied", skill.first, skill.second, r.nextInt(-1, 5))
        22 -> listOf("buySkill", skill.first, skill.second)
        23 -> listOf("stepSkill", skill.first, skill.second, r.nextBoolean())
        24 -> listOf("spendOnSkill", skill.first, skill.second, r.nextInt(-4, 5))
        25 -> NonStandardPathways.patrons().random(r).let { p ->
            listOf("grantBoon", (if (r.nextInt(4) == 0) BoonState(p, 3) else s.nextBoon(p) ?: BoonState(p, 9)))
        }
        26 -> listOf("chooseDrawback", (s.state?.let { TraitGrants.drawbackChoices(it) }.orEmpty() + "Nope").random(r))
        27 -> listOf("usePower", r.nextInt(0, 7), r.nextInt(0, 13), r.nextInt(1, 16))
        28, 29 -> listOf("spend", Pool.entries.random(r), r.nextInt(-5, 9), r.nextInt(1, 14), r.nextInt(1, 16))
        30 -> listOf("rest", Rest.rests().random(r).name, r.nextBoolean(), r.nextInt(1, 14), r.nextInt(5) == 0)
        31 -> listOf("gainCor", r.nextInt(0, 5), r.nextInt(1, 16))
        32 -> if (r.nextBoolean()) listOf("digest", r.nextInt(-30, 61))
        else listOf("act", r.nextInt(0, ContentLoader.actingMethod().size), r.nextInt(5) == 0)
        else -> {
            val faces = List(6) { r.nextInt(1, 7) }
            val current = s.state
            val next = when {
                r.nextInt(5) == 0 -> mapOf("pathway" to "Fool", "sequence" to 8)
                current == null -> mapOf("pathway" to ContentLoader.pathways().map { it.pathway }.distinct().random(r), "sequence" to 9)
                else -> current.advanced()?.let { mapOf("pathway" to it.pathway, "sequence" to it.sequence) }
                    ?: mapOf("pathway" to current.pathway, "sequence" to 6)
            }
            if (s.lost && r.nextBoolean()) listOf("rerollPcr", faces, r.nextInt(-2, 3))
            else listOf("drink", next, faces, r.nextInt(-2, 3))
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun run(s: BeyonderSession, sheet: PremadeSheet, op: Op): Any? {
    val a = op.drop(1)
    val maxCorFor = { state: BeyonderState? -> s.edits.corCeilingOn(sheet, state) }
    return when (op[0]) {
        "setAttribute" -> s.setAttribute(a[0] as Attr, a[1] as Int).let { null }
        "canSetAttribute" -> s.canSetAttribute(a[0] as Attr, a[1] as Int)
        "setSecondary" -> s.setSecondary(a[0] as String, a[1] as Int).let { null }
        "canSetSecondary" -> s.canSetSecondary(a[0] as String, a[1] as Int)
        "carryWeapon" -> s.carryWeapon(a[0] as String).let { null }
        "dropWeapon" -> s.dropWeapon(a[0] as String).let { null }
        "setWealth" -> s.setWealth(a[0] as String?).let { null }
        "setAppearance" -> s.setAppearance(a[0] as String?).let { null }
        "buyTrait" -> s.buyTrait(a[0] as String).let { null }
        "buyRefusal" -> s.buyRefusal(a[0] as String)
        "sellTrait" -> s.sellTrait(a[0] as String).let { null }
        "canSellTrait" -> s.canSellTrait(a[0] as String)
        "addQuirk" -> s.addQuirk(a[0] as String).let { null }
        "removeQuirk" -> s.removeQuirk(a[0] as String).let { null }
        "setGained" -> s.setGained(a[0] as String, a[1] as Boolean).let { null }
        "setCharmedSkill" -> s.setCharmedSkill(a[0] as SkillKey).let { null }
        "buyInstancedTrait" -> s.buyInstancedTrait(a[0] as SavedInstancedTrait).let { null }
        "sellInstancedTrait" -> s.sellInstancedTrait(a[0] as Int, a[1] as SavedInstancedTrait).let { null }
        "buyLanguage" -> s.buyLanguage(a[0] as SavedLanguage).let { null }
        "sellLanguage" -> s.sellLanguage(a[0] as String).let { null }
        "setStudied" -> s.setStudied(a[0] as String, a[1] as String?, a[2] as Int).let { null }
        "buySkill" -> s.buySkill(a[0] as String, a[1] as String?).let { null }
        "stepSkill" -> s.stepSkill(a[0] as String, a[1] as String?, a[2] as Boolean).let { null }
        "spendOnSkill" -> s.spendOnSkill(a[0] as String, a[1] as String?, a[2] as Int).let { null }
        "grantBoon" -> s.grantBoon(a[0] as BoonState).let { null }
        "chooseDrawback" -> listOf(s.canChooseDrawback(sheet, a[0] as String), s.chooseDrawback(sheet, a[0] as String).let { null })
        "usePower" -> s.usePower(a[0] as Int, a[1] as Int, a[2] as Int).let { null }
        "spend" -> s.spend(a[0] as Pool, a[1] as Int, a[2] as Int, a[3] as Int).let { null }
        "rest" -> {
            val recovery = Rest.recovery(Rest.named(a[0] as String), a[1] as Boolean, a[2] as Int)
            val drawn = if (a[3] as Boolean) (s.fpLost + 1) to s.spiSpent else s.fpLost to s.spiSpent
            s.rest(recovery, drawn)
        }
        "gainCor" -> s.gainCor(a[0] as Int, a[1] as Int).let { null }
        "digest" -> s.digest(a[0] as Int).let { null }
        "act" -> s.act(ContentLoader.actingMethod()[a[0] as Int], (s.state?.digestion ?: 0) + (if (a[1] as Boolean) 1 else 0)).let { null }
        "drink" -> {
            val n = a[0] as Map<String, Any>
            val next = BeyonderState(n["pathway"] as String, n["sequence"] as Int)
            scripted(a[1] as List<Int>) { s.drink(next, it, a[2] as Int, maxCorFor) }
        }
        "rerollPcr" -> scripted(a[0] as List<Int>) { s.rerollPcr(it, a[1] as Int, maxCorFor) }
        else -> error("unknown op ${op[0]}")
    }
}

/** Characters.kt: own characters, the names they may take, and a reset. */
private fun Vectors.characterVectors2(sheets: List<PremadeSheet>) {
    val saved = mapOf(
        "Mine" to SavedSession(basedOn = sheets[0].name),
        sheets[1].name to SavedSession(cor = 2),
        "Orphan" to SavedSession(basedOn = "Nobody"),
        "Plain" to SavedSession(),
        "Built" to SavedSession(
            basedOn = sheets[2].name, cor = 3, hpLost = 4,
            edits = SavedEdits(
                st = 12, traits = listOf("Fit", "Bad Temper"), gained = listOf("Bad Temper"),
                quirks = listOf("Hums"), studied = listOf(com.pathways.app.SavedSkill("Stealth", null, 2)),
                boon = BoonState("Zaratul", 9),
            ),
        ),
    )
    case("ownSheets", saved.toList()) { ownSheets(sheets, saved) }
    case("takenNames", saved.toList()) { takenNames(sheets, saved) }
    for (name in listOf("", "  ", "mine", " MINE ", "New One", sheets[3].name.uppercase())) {
        case("newCharacterRefusal", name, saved.toList()) { newCharacterRefusal(name, takenNames(sheets, saved)) }
    }
    for ((name, entry) in saved) case("cleared", name, saved.toList()) { entry.cleared() }
}

private val STORE_JSON = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * SessionStore's codec: what each text decodes to, or that it cannot, and
 * what the store writes back. The texts are real saves and broken ones.
 */
fun Vectors.storeVectors() {
    val played = linkedMapOf<String, SavedSession>()
    for ((i, sheet) in Premades.all().take(8).withIndex()) {
        var last = SavedSession(basedOn = if (i % 3 == 0) Premades.all()[0].name else null)
        val session = BeyonderSession(startOf(i % 3, i), sheet) { last = it }
        val r = Random(i + 500)
        repeat(40) { runCatching { run(session, sheet, randomOp(session, sheet, r)) } }
        played[if (i % 2 == 0) sheet.name else "Own $i"] = last
    }
    val texts = listOf(
        STORE_JSON.encodeToString(played),
        "", "not json", "[]", "{}", "null", "{\"A\":{}}", "{\"A\":{},}",
        "{\"A\":{\"cor\":\"3\"}}", "{\"A\":{\"cor\":3.0}}", "{\"A\":{\"cor\":3.5}}", "{\"A\":{\"cor\":null}}",
        "{\"A\":{\"cor\":2147483648}}", "{\"A\":{\"cor\":1e2}}", "{\"A\":{\"cor\":-5}}",
        "{\"A\":{\"lost\":\"true\"}}", "{\"A\":{\"lost\":1}}", "{\"A\":{\"basedOn\":5}}",
        "{\"A\":{\"beyonder\":{\"pathway\":\"Fool\",\"sequence\":5}}}",
        "{\"A\":{\"beyonder\":{\"pathway\":\"Fool\"}}}",
        "{\"A\":{\"beyonder\":{\"pathway\":\" \",\"sequence\":9}}}",
        "{\"A\":{\"beyonder\":{\"pathway\":\"Fool\",\"sequence\":9,\"digestion\":101}}}",
        "{\"A\":{\"beyonder\":{\"pathway\":\"Fool\",\"sequence\":9,\"digestion\":40,\"drawback\":null}}}",
        "{\"A\":{\"pendingPcr\":null,\"beyonder\":null}}",
        "{\"A\":{\"edits\":{\"skills\":[{\"name\":\"Stealth\",\"points\":0}]}}}",
        "{\"A\":{\"edits\":{\"skills\":[{\"name\":\"Stealth\",\"points\":2}]},\"extra\":{\"x\":[1]}}}",
        "{\"A\":{\"edits\":{\"secondaries\":{\"hp\":\"2\"}}}}",
        "{\"A\":{\"edits\":{\"secondaries\":{\"will\":2,\"hp\":-1}}}}",
        "{\"A\":{\"edits\":{\"traits\":[null]}}}",
        "{\"A\":{\"edits\":{\"wealth\":null,\"appearance\":\"Attractive\"}}}",
        "{\"A\":{\"edits\":{\"gained\":\"x\"}}}",
        "{\"A\":{\"edits\":{\"boon\":{\"pathway\":\"x\"}}}}",
        "{\"A\":{\"edits\":{\"charmedSkill\":{\"name\":\"Stealth\"}}}}",
        "{\"A\":{\"edits\":{\"instanced\":[{\"row\":\"Phobia (specify)\",\"cost\":-5}]}}}",
        "{\"A\":{\"edits\":{\"languages\":[{\"language\":\"Intis\",\"proficiency\":\"Native\"}]}}}",
        "{\"A\":{\"cor\":1},\"A\":{\"cor\":2}}",
        "{\"B\":{\"cor\":1},\"A\":{\"lost\":true,\"edits\":{\"st\":12,\"dx\":null}}}",
        "{\"A\":{\"lost\":true,\"corrupted\":false,\"cor\":3,\"edits\":{\"st\":12,\"dx\":null}}}",
        " {\n \"A\" : { \"cor\" : 4 }\n } ",
        "{\"A\":[]}", "{\"A\":null}", "{\"A\":{\"edits\":null}}",
    )
    for (text in texts) {
        case("SessionStore.decode", text) { STORE_JSON.decodeFromString<Map<String, SavedSession>>(text).toList() }
        case("SessionStore.roundTrip", text) {
            val dir = kotlin.io.path.createTempDirectory("store").toFile()
            val from = java.io.File(dir, "sessions.json").apply { writeText(text) }
            val loaded = com.pathways.app.SessionStore(from).load()
            val to = java.io.File(dir, "out/sessions.json")
            com.pathways.app.SessionStore(to).save(loaded)
            to.readText().also { dir.deleteRecursively() }
        }
    }
}
