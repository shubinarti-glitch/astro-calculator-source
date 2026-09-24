package ru.astrosmap.app.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import ru.astrosmap.app.data.api.RegisterRequest

class RegisterRequestTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test fun `required consent fields survive default serializer settings`() {
        val request = RegisterRequest("test", "not-a-real-password", "test@example.invalid", "ru",
            true, true, "2026-09-01", "2026-09-01", "android")
        val body = json.parseToJsonElement(json.encodeToString(request)).jsonObject
        assertEquals(true, body.getValue("privacy_accepted").jsonPrimitive.boolean)
        assertEquals(true, body.getValue("terms_accepted").jsonPrimitive.boolean)
        assertEquals("2026-09-01", body.getValue("privacy_version").jsonPrimitive.content)
        assertEquals("2026-09-01", body.getValue("terms_version").jsonPrimitive.content)
        assertEquals("android", body.getValue("consent_source").jsonPrimitive.content)
    }

    @Test fun `false consent is never silently upgraded`() {
        val request = RegisterRequest("test", "not-a-real-password", "test@example.invalid", "ru",
            false, false, "2026-09-01", "2026-09-01", "android")
        val body = json.parseToJsonElement(json.encodeToString(request)).jsonObject
        assertFalse(body.getValue("privacy_accepted").jsonPrimitive.boolean)
        assertFalse(body.getValue("terms_accepted").jsonPrimitive.boolean)
    }
}
