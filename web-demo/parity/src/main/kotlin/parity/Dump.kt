package parity

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.reflect.KClass
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

/**
 * Any Kotlin value as the JSON the TypeScript port must produce for it.
 *
 * - A class dumps its primary-constructor properties, keys sorted. A
 *   computed getter is not a field: the port exposes it as a function and a
 *   vector of its own checks it.
 * - A subclass of a sealed type adds `type`, its simple name; an `object`
 *   dumps as `{type}` alone.
 * - An enum dumps as its name, a value class as its value.
 * - A map with non-string keys dumps as an object keyed by each key's
 *   canonical JSON, which the TS side's `canon` rebuilds the same way.
 */
fun dump(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is String -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Int, is Long, is Short, is Byte -> JsonPrimitive(v as Number)
    is Double -> JsonPrimitive(v)
    is Float -> JsonPrimitive(v.toDouble())
    is Char -> JsonPrimitive(v.toString())
    is Enum<*> -> JsonPrimitive(v.name)
    is Pair<*, *> -> JsonArray(listOf(dump(v.first), dump(v.second)))
    is Triple<*, *, *> -> JsonArray(listOf(dump(v.first), dump(v.second), dump(v.third)))
    is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> keyOf(k) to dump(x) }.toSortedMap())
    is Iterable<*> -> JsonArray(v.map(::dump))
    is Array<*> -> JsonArray(v.map(::dump))
    is IntArray -> JsonArray(v.map(::dump))
    is IntRange -> JsonObject(mapOf("first" to dump(v.first), "last" to dump(v.last)))
    else -> dumpObject(v)
}

private fun keyOf(k: Any?): String = when (k) {
    is String -> k
    is Enum<*> -> k.name
    is Number, is Boolean -> k.toString()
    else -> canonical(dump(k))
}

/** Compact JSON with sorted keys: what TS `JSON.stringify(sortDeep(x))` prints. */
fun canonical(e: JsonElement): String = when (e) {
    is JsonObject -> e.entries.sortedBy { it.key }
        .joinToString(",", "{", "}") { (k, x) -> JsonPrimitive(k).toString() + ":" + canonical(x) }
    is JsonArray -> e.joinToString(",", "[", "]") { canonical(it) }
    else -> e.toString()
}

private fun dumpObject(v: Any): JsonElement {
    val cls: KClass<out Any> = v::class
    val fields = sortedMapOf<String, JsonElement>()
    if (cls.isValue) {
        val p = cls.memberProperties.single()
        p.isAccessible = true
        return dump(p.getter.call(v))
    }
    val sealedChild = cls.allSupertypes.any { (it.classifier as? KClass<*>)?.isSealed == true }
    if (sealedChild) fields["type"] = JsonPrimitive(cls.simpleName)
    val params = if (cls.objectInstance != null) emptyList() else cls.primaryConstructor?.parameters.orEmpty()
    for (param in params) {
        val p = cls.memberProperties.firstOrNull { it.name == param.name } ?: continue
        p.isAccessible = true
        fields[p.name] = dump(p.getter.call(v))
    }
    if (fields.isEmpty() && !sealedChild && cls.objectInstance == null) {
        error("nothing to dump for ${cls.qualifiedName}; give it a mapping")
    }
    return JsonObject(fields)
}
