package ru.astrosmap.app.data

import org.junit.Assert.*
import org.junit.Test

class CrashSummaryTest {
    @Test fun `messages causes filenames and suppressed exceptions are excluded`() {
        val error = IllegalStateException("password=private", RuntimeException("email=private"))
        error.addSuppressed(RuntimeException("birth=private"))
        error.stackTrace = arrayOf(StackTraceElement("app.Test", "run", "private.kt", 42))
        val result = CrashSummary.format(error)
        assertFalse(result.contains("private"))
        assertTrue(result.contains("app.Test.run:42"))
        assertTrue(result.contains("IllegalStateException"))
    }

    @Test fun `report length and frame count are bounded`() {
        val error = RuntimeException()
        error.stackTrace = Array(1000) { StackTraceElement("a".repeat(500), "b".repeat(500), null, 1) }
        assertTrue(CrashSummary.format(error).length <= 1900)
        assertTrue(CrashSummary.format(error).lines().size <= 12)
    }
}
