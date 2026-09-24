package ru.astrosmap.app.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.astrosmap.app.data.api.MobileReportRequest
import java.io.File

class PendingCrashStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private fun report(id: String = "one") = MobileReportRequest(id, "Technical crash", "1.7.3", 11, "googleplay", 36, true)

    @Test fun `disabled by default and cannot save without consent`() {
        val store = PendingCrashStore(temp.root)
        assertFalse(store.enabled())
        store.save(report())
        assertNull(store.read())
        assertFalse(File(temp.root, "crash-pending-v1.json").exists())
    }

    @Test fun `consent persists and disabling erases pending report`() {
        val store = PendingCrashStore(temp.root)
        store.setEnabled(true)
        store.save(report())
        assertEquals("one", PendingCrashStore(temp.root).read()?.id)
        store.setEnabled(false)
        assertFalse(PendingCrashStore(temp.root).enabled())
        assertNull(store.read())
        assertFalse(File(temp.root, "crash-pending-v1.json").exists())
    }

    @Test fun `retries keep id and stale acknowledgement cannot erase a newer crash`() {
        val store = PendingCrashStore(temp.root)
        store.setEnabled(true)
        store.save(report())
        assertEquals(store.read(), store.read())
        store.save(report("two"))
        store.acknowledge("one")
        assertEquals("two", store.read()?.id)
        store.acknowledge("two")
        assertNull(store.read())
    }

    @Test fun `expired report is removed`() {
        val store = PendingCrashStore(temp.root) { System.currentTimeMillis() + 8 * 86400000L }
        store.setEnabled(true)
        store.save(report())
        assertNull(store.read())
        assertFalse(File(temp.root, "crash-pending-v1.json").exists())
    }

    @Test fun `malformed and oversized files are discarded`() {
        val store = PendingCrashStore(temp.root)
        store.setEnabled(true)
        val file = File(temp.root, "crash-pending-v1.json")
        for (body in listOf("{", "x".repeat(16385))) {
            file.writeText(body)
            assertNull(store.read())
            assertFalse(file.exists())
        }
    }
}
