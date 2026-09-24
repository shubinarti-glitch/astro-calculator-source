package ru.astrosmap.app.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import retrofit2.HttpException
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.data.api.NatalRequest
import ru.astrosmap.app.data.api.ProfileUpload
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Двусторонняя синхронизация сохранённых карт с кабинетом сайта (/api/profiles).
 *
 * У API нет правки профиля, поэтому изменение = удалить + создать заново.
 * Правила: локальные dirty-правки побеждают; иначе сервер — источник истины
 * (карта, пропавшая на сервере, удаляется и локально).
 */
@Singleton
class SyncManager @Inject constructor(
    private val api: AstroApi,
    private val dao: ChartDao,
    private val tokenStore: TokenStore,
    private val cities: CityStore,
) {
    private val mutex = Mutex()

    /** true — синхронизация прошла (или не нужна: нет входа); false — сеть/сервер недоступны. */
    suspend fun sync(): Boolean = mutex.withLock {
        tokenStore.get() ?: return true
        try {
            val local = dao.allOnce()

            // 1. Отложенные удаления.
            for (e in local.filter { it.pendingDelete }) {
                e.serverId?.let { sid ->
                    try {
                        api.deleteProfile(sid)
                    } catch (ex: HttpException) {
                        if (ex.code() != 404) throw ex // 404 = уже удалена, ок
                    }
                }
                dao.delete(e.id)
            }

            // 2. Правки синхронизированных карт: пересоздаём профиль.
            for (e in local.filter { it.dirty && !it.pendingDelete && it.serverId != null }) {
                try {
                    api.deleteProfile(e.serverId!!)
                } catch (ex: HttpException) {
                    if (ex.code() != 404) throw ex
                }
                val created = api.addProfile(upload(e))
                dao.markSynced(e.id, created.id)
            }

            // 3. Новые локальные — на сервер.
            for (e in local.filter { it.serverId == null && !it.pendingDelete }) {
                val created = api.addProfile(upload(e))
                dao.markSynced(e.id, created.id)
            }

            // 4. Забираем сервер: новое добавляем, пропавшее удаляем.
            val server = api.profiles()
            val fresh = dao.allOnce()
            val localByServerId = fresh.filter { it.serverId != null }.associateBy { it.serverId!! }
            for (p in server) {
                if (p.id !in localByServerId) {
                    fromServer(p.id, p.label, p.data)?.let { dao.upsert(it) }
                }
            }
            val serverIds = server.map { it.id }.toSet()
            for (e in fresh) {
                if (e.serverId != null && e.serverId !in serverIds && !e.dirty) dao.delete(e.id)
            }
            true
        } catch (ex: HttpException) {
            if (ex.code() == 401) tokenStore.clear()
            false
        } catch (ex: Exception) {
            false // офлайн — попробуем в следующий раз
        }
    }

    private fun upload(e: ChartEntity) = ProfileUpload(
        label = e.name,
        data = NatalRequest(
            name = e.name, year = e.year, month = e.month, day = e.day,
            hour = e.hour, minute = e.minute, lat = e.lat, lng = e.lng,
            tzStr = e.tz, city = e.city,
        ),
    )

    /** Профиль сайта -> локальная карта; кривые данные пропускаем. */
    private fun fromServer(serverId: Long, label: String, data: JsonObject): ChartEntity? {
        fun int(key: String): Int? = (data[key] as? JsonPrimitive)?.intOrNull
        fun dbl(key: String): Double? = (data[key] as? JsonPrimitive)?.doubleOrNull
        fun str(key: String): String? = (data[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        val year = int("year") ?: return null
        val month = int("month") ?: return null
        val day = int("day") ?: return null
        val lat = dbl("lat") ?: return null
        val lng = dbl("lng") ?: return null
        // Сайт может не хранить таймзону — берём зону ближайшего города из офлайн-базы.
        val tz = str("tz_str").takeUnless { it.isNullOrBlank() } ?: cities.nearestTz(lat, lng) ?: "UTC"
        return ChartEntity(
            serverId = serverId,
            name = label,
            year = year, month = month, day = day,
            hour = int("hour") ?: 12, minute = int("minute") ?: 0,
            lat = lat, lng = lng, tz = tz,
            city = str("city").orEmpty(),
        )
    }
}
