package ru.astrosmap.app.data

/** Only runtime symbol names and line numbers. Never Throwable.message/toString. */
object CrashSummary {
    private fun symbol(value: String) = value.take(120).replace(Regex("[^A-Za-z0-9_.$<>-]"), "_")

    fun format(error: Throwable): String {
        val lines = mutableListOf("Unhandled JVM exception", symbol(error.javaClass.name))
        error.stackTrace.take(10).forEach {
            lines += "${symbol(it.className)}.${symbol(it.methodName)}:${it.lineNumber}"
        }
        return lines.joinToString("\n").take(1900)
    }
}
