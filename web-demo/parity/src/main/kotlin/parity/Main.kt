package parity

import com.pathways.rules.RandomSource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/**
 * One vector file: each case is a function name, its arguments and either
 * the value the Kotlin engine returned or the exception it threw.
 */
class Vectors {
    val cases = mutableListOf<JsonElement>()

    fun case(fn: String, vararg args: Any?, call: () -> Any?) {
        val result = try {
            "out" to dump(call())
        } catch (e: RuntimeException) {
            "throws" to JsonPrimitive(e::class.simpleName)
        }
        cases += JsonObject(mapOf("fn" to JsonPrimitive(fn), "args" to dump(args.toList()), result))
    }
}

/** Dice that come from a list, so both engines throw the same faces. */
class ScriptedDice(private val faces: List<Int>) : RandomSource {
    private var next = 0
    val thrown = mutableListOf<Pair<List<Int>, Int?>>()

    override fun roll(sides: Int): Int {
        check(next < faces.size) { "script ran out after ${faces.size} faces" }
        val face = faces[next++]
        check(face in 1..sides) { "face $face is not on a d$sides" }
        return face
    }

    override fun thrown(faces: List<Int>, target: Int?) {
        thrown += faces to target
    }
}

private val FILES: Map<String, Vectors.() -> Unit> = linkedMapOf(
    "character" to { characterVectors() },
    "content" to { contentVectors() },
)

fun main(args: Array<String>) {
    val out = File(args.single()).apply { mkdirs() }
    for ((name, build) in FILES) {
        val v = Vectors().apply(build)
        val text = v.cases.joinToString(",\n", "[\n", "\n]\n") { it.toString() }
        File(out, "$name.json").writeText(text)
        println("$name: ${v.cases.size} cases")
    }
}
