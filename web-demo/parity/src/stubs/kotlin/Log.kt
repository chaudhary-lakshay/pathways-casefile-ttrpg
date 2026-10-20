package android.util

object Log {
    @JvmStatic
    fun w(tag: String, msg: String, tr: Throwable? = null): Int {
        System.err.println("$tag: $msg ${tr?.message ?: ""}")
        return 0
    }
}
