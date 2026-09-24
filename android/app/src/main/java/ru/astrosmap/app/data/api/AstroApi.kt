package ru.astrosmap.app.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.nullable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

@Serializable
data class EventIn(
    val name: String,
    val props: Map<String, String>? = null,
)

@Serializable
data class EventBatch(
    @SerialName("device_id") val deviceId: String,
    val events: List<EventIn>,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val lang: String = "ru",
)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val email: String,
    val lang: String = "ru",
    @SerialName("privacy_accepted") val privacyAccepted: Boolean,
    @SerialName("terms_accepted") val termsAccepted: Boolean,
    @SerialName("privacy_version") val privacyVersion: String,
    @SerialName("terms_version") val termsVersion: String,
    @SerialName("consent_source") val consentSource: String,
)

@Serializable
data class DeleteAccountRequest(val password: String)

@Serializable
data class ForgotPasswordRequest(val email: String, val lang: String = "ru")

@Serializable
data class AuthResponse(
    val token: String,
    val username: String,
    @SerialName("is_admin") val isAdmin: Boolean = false,
)

@Serializable
data class MeResponse(
    val username: String,
    val email: String? = null,
    @SerialName("email_verified") val emailVerified: Boolean = false,
    val premium: Boolean = false,
    val consultation: Boolean = false,
    // Сервер отдаёт unix-время числом (а исторически мог и строкой) — принимаем любое.
    @SerialName("premium_until")
    @Serializable(with = LenientStringSerializer::class)
    val premiumUntil: String? = null,
    @SerialName("report_credits") val reportCredits: Int = 0,
    /** Новые поля необязательны: старый сервер и сохранённые ответы остаются совместимыми. */
    val plan: String? = null,
    @SerialName("subscription_source") val subscriptionSource: String? = null,
    // null = ответ старого сервера; [] = новый сервер явно не выдал ни одного права.
    // Различие обязательно, иначе пустой серверный список можно ошибочно заменить
    // полным набором Premium-прав.
    val entitlements: List<String>? = null,
) {
    fun accessState(): ru.astrosmap.app.data.access.AccessState =
        ru.astrosmap.app.data.access.AccessState.from(this)

    /** Был премиум, но срок вышел (для мягкого баннера «продлите»). Никогда не имел — false. */
    fun premiumExpired(): Boolean {
        if (premium) return false
        val ts = premiumUntil?.toLongOrNull() ?: return false
        return java.time.Instant.ofEpochSecond(ts).isBefore(java.time.Instant.now())
    }

    /** «до 12.08.2026» из unix-времени или ISO-строки. */
    fun premiumUntilDate(): String? {
        val raw = premiumUntil ?: return null
        raw.toLongOrNull()?.let { ts ->
            val d = java.time.Instant.ofEpochSecond(ts).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            return "%02d.%02d.%d".format(d.dayOfMonth, d.monthValue, d.year)
        }
        return raw.take(10)
    }
}

/** Строка из любого JSON-примитива (число, bool, строка) или null. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object LenientStringSerializer : kotlinx.serialization.KSerializer<String?> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
        "LenientString", kotlinx.serialization.descriptors.PrimitiveKind.STRING,
    ).nullable

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): String? {
        val input = decoder as? kotlinx.serialization.json.JsonDecoder
            ?: return if (decoder.decodeNotNullMark()) decoder.decodeString() else null
        val el = input.decodeJsonElement()
        val primitive = el as? kotlinx.serialization.json.JsonPrimitive ?: return null
        return if (primitive is kotlinx.serialization.json.JsonNull) null else primitive.content
    }

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

/** Тело POST /api/natal — как BirthData на бэке. */
@Serializable
data class NatalRequest(
    val name: String,
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val lat: Double,
    val lng: Double,
    @SerialName("tz_str") val tzStr: String,
    val city: String = "",
    val lang: String = "ru",
)

/** Одна планета в общих транзитах: знак, ретро, период и значение. */
@Serializable
data class PlanetTransit(
    @SerialName("planet_ru") val planetRu: String = "",
    val sign: String = "",
    @SerialName("sign_ru") val signRu: String = "",
    @SerialName("sign_symbol") val signSymbol: String = "",
    val retrograde: Boolean = false,
    val since: String? = null,
    val until: String? = null,
    val meaning: String = "",
)

@Serializable
data class TransitsResponse(
    val date: String = "",
    val transits: List<PlanetTransit> = emptyList(),
)

/** REST-клиент бэкенда astrosmap.ru (backend/main.py). */
interface AstroApi {
    @GET("api/mobile/editorial/v1")
    suspend fun editorial(): ru.astrosmap.app.editorial.EditorialPackage
    @POST("api/mobile/reports")
    suspend fun sendReport(@Body report: MobileReportRequest): MobileReportReceipt
    /** Общий небесный фон: где сейчас планеты, период и значение. Без авторизации. */
    @GET("api/transits/current")
    suspend fun currentTransits(
        @retrofit2.http.Query("lang") lang: String,
        @retrofit2.http.Query("tz") timezone: String? = null,
    ): TransitsResponse

    @POST("api/auth/forgot")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): kotlinx.serialization.json.JsonObject

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @GET("api/auth/me")
    suspend fun me(): MeResponse

    @POST("api/auth/logout")
    suspend fun logout()

    @retrofit2.http.HTTP(method = "DELETE", path = "api/auth/account", hasBody = true)
    suspend fun deleteAccount(@Body body: DeleteAccountRequest)

    /** Полный отчёт с текстами, без SVG (колесо приложение рисует само). */
    @POST("api/natal")
    suspend fun natal(
        @Body body: NatalRequest,
        @retrofit2.http.Query("svg") svg: Int = 0,
    ): kotlinx.serialization.json.JsonObject

    /** Транзиты на дату (бесплатно): тексты аспектов транзит→натал. */
    @POST("api/transit")
    suspend fun transit(
        @Body body: TransitApiRequest,
        @retrofit2.http.Query("svg") svg: Int = 0,
    ): kotlinx.serialization.json.JsonObject

    /** Соляр/лунар — премиум (402 без подписки). */
    @POST("api/return")
    suspend fun solarReturn(
        @Body body: ReturnApiRequest,
        @retrofit2.http.Query("svg") svg: Int = 0,
    ): kotlinx.serialization.json.JsonObject

    @POST("api/progression")
    suspend fun progression(
        @Body body: ProgressionApiRequest,
        @retrofit2.http.Query("svg") svg: Int = 0,
    ): kotlinx.serialization.json.JsonObject

    @POST("api/forecast")
    suspend fun forecast(@Body body: ForecastApiRequest): kotlinx.serialization.json.JsonObject

    /** История расчётов на сайте (для кабинета). */
    @POST("api/events")
    suspend fun events(@Body body: EventBatch)

    @GET("api/history")
    suspend fun history(): List<kotlinx.serialization.json.JsonObject>

    /** Лунный (ведический) календарь — Панчанг. Бесплатный. */
    @POST("api/vedic-calendar")
    suspend fun vedic(@Body body: VedicApiRequest): kotlinx.serialization.json.JsonObject

    /** Полная синастрия — премиум (402 без подписки). */
    @POST("api/synastry")
    suspend fun synastry(
        @Body body: SynastryApiRequest,
        @retrofit2.http.Query("svg") svg: Int = 0,
    ): kotlinx.serialization.json.JsonObject

    @POST("api/synastry/preview")
    suspend fun synastryPreview(@Body body: SynastryApiRequest): kotlinx.serialization.json.JsonObject

    @GET("api/profiles")
    suspend fun profiles(): List<ProfileDto>

    @POST("api/profiles")
    suspend fun addProfile(@Body body: ProfileUpload): ProfileDto

    @retrofit2.http.DELETE("api/profiles/{id}")
    suspend fun deleteProfile(@retrofit2.http.Path("id") id: Long)
}

/** Профиль кабинета сайта: label + свободный data (данные формы рождения). */
@Serializable
data class ProfileDto(
    val id: Long,
    val label: String,
    val data: kotlinx.serialization.json.JsonObject,
    val note: String? = null,
)

@Serializable
data class ProfileUpload(
    val label: String,
    val data: NatalRequest,
)

@Serializable
data class TransitDateDto(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int = 12,
    val minute: Int = 0,
)

@Serializable
data class TransitLocationDto(
    val lat: Double,
    val lng: Double,
    @SerialName("tz_str") val tzStr: String? = null,
    val city: String = "",
)

@Serializable
data class TransitApiRequest(
    val natal: NatalRequest,
    @SerialName("transit_date") val transitDate: TransitDateDto,
    @SerialName("transit_location") val transitLocation: TransitLocationDto? = null,
)

@Serializable
data class ReturnApiRequest(
    val natal: NatalRequest,
    val year: Int,
    val month: Int? = null, // для лунара
    @SerialName("return_type") val returnType: String = "Solar",
    val location: TransitLocationDto? = null,
)

@Serializable
data class ProgressionApiRequest(
    val natal: NatalRequest,
    @SerialName("target_date") val targetDate: TransitDateDto,
)

@Serializable
data class DateDto(val year: Int, val month: Int, val day: Int)

@Serializable
data class ForecastApiRequest(
    val natal: NatalRequest,
    val start: DateDto,
    val end: DateDto,
    val location: TransitLocationDto? = null,
)

/** Данные рождения сохранённой карты в формате запросов бэкенда. */
fun ru.astrosmap.app.data.ChartEntity.toNatalRequest(): NatalRequest = NatalRequest(
    name = name, year = year, month = month, day = day, hour = hour, minute = minute,
    lat = lat, lng = lng, tzStr = tz, city = city,
    lang = if (java.util.Locale.getDefault().language == "ru") "ru" else "en",
)

@Serializable
data class VedicApiRequest(
    val year: Int,
    val month: Int,
    val lat: Double,
    val lng: Double,
    @SerialName("tz_str") val tzStr: String? = null,
    val natal: NatalRequest? = null, // персонализация (Тарабала)
    val lang: String = "ru",
)

@Serializable
data class SynastryApiRequest(
    @SerialName("person_a") val personA: NatalRequest,
    @SerialName("person_b") val personB: NatalRequest,
)
