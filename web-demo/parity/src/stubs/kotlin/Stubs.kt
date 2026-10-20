// The two Android APIs the app's pure files touch, reduced to what the JVM
// needs to run them: state holders that hold, and a log that prints.
@file:Suppress("unused")

package androidx.compose.runtime

import kotlin.reflect.KProperty

class MutableState<T>(var value: T)

fun <T> mutableStateOf(value: T) = MutableState(value)
fun mutableIntStateOf(value: Int) = MutableState(value)

operator fun <T> MutableState<T>.getValue(thisObj: Any?, property: KProperty<*>): T = value
operator fun <T> MutableState<T>.setValue(thisObj: Any?, property: KProperty<*>, v: T) {
    value = v
}
