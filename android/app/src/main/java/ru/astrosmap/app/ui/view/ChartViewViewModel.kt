package ru.astrosmap.app.ui.view

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.astrosmap.app.astro.AstroEngine
import ru.astrosmap.app.astro.NatalChart
import ru.astrosmap.app.data.ChartDao
import ru.astrosmap.app.data.ChartEntity
import ru.astrosmap.app.data.ChartTexts
import ru.astrosmap.app.data.SyncManager
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.data.api.NatalRequest
import ru.astrosmap.app.data.access.AccessState
import ru.astrosmap.app.ui.AstroLabels
import ru.astrosmap.app.ui.form.ChartDraftHolder
import javax.inject.Inject

data class ChartViewState(
    val entity: ChartEntity? = null,
    val chart: NatalChart? = null,
    val savedId: Long? = null,      // null — черновик ещё не сохранён
    val texts: ChartTexts? = null,
    val textsOffline: Boolean = false, // трактовки не загрузились (нет сети)
    val access: AccessState = AccessState(),
)

@HiltViewModel
class ChartViewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dao: ChartDao,
    private val engine: AstroEngine,
    private val api: AstroApi,
    private val syncManager: SyncManager,
    private val analytics: ru.astrosmap.app.data.Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(ChartViewState())
    val state: StateFlow<ChartViewState> = _state

    /** Сырой JSON трактовок — сохраняется вместе с картой. */
    private var textsRaw: String? = null
    private val lang = if (AstroLabels.isRu()) "ru" else "en"

    init {
        val id = savedStateHandle.get<String>("id")?.toLongOrNull() ?: 0L
        viewModelScope.launch {
            val entity = if (id > 0) dao.byId(id) else ChartDraftHolder.pending
            if (entity == null) return@launch
            val chart = withContext(Dispatchers.Default) { engine.natal(entity.toBirthInput()) }
            _state.value = ChartViewState(
                entity = entity,
                chart = chart,
                savedId = entity.id.takeIf { it > 0 },
            )
            _state.value = _state.value.copy(
                access = runCatching { api.me().accessState() }.getOrDefault(AccessState()),
            )
            loadTexts(entity)
            // Без трения: самую первую построенную карту сразу считаем «моей» — авто-сохраняем,
            // чтобы личные транзиты появились на «Сегодня» без отдельного нажатия. Дальнейшие
            // карты (друзей) сохраняются вручную. PrimaryChart.resolve сам возьмёт её как раннюю.
            if (id == 0L && _state.value.savedId == null && dao.allOnce().none { !it.pendingDelete }) {
                save()
            }
        }
    }

    private suspend fun loadTexts(entity: ChartEntity) {
        // 1) кэш на нужном языке
        val cachedRaw = entity.textsJson?.takeIf { entity.textsLang == lang }
        val cachedTexts = cachedRaw?.let(::parseSafe)
        if (cachedTexts != null) {
            textsRaw = cachedRaw
            _state.value = _state.value.copy(texts = cachedTexts, textsOffline = false)
        }
        // 2) сервер
        try {
            val body = NatalRequest(
                name = entity.name, year = entity.year, month = entity.month, day = entity.day,
                hour = entity.hour, minute = entity.minute, lat = entity.lat, lng = entity.lng,
                tzStr = entity.tz, city = entity.city, lang = lang,
            )
            val raw = api.natal(body).toString()
            val freshTexts = parseSafe(raw)
                ?: throw IllegalStateException("Server returned invalid natal interpretation")
            textsRaw = raw
            _state.value = _state.value.copy(texts = freshTexts, textsOffline = false)
            _state.value.savedId?.let { dao.updateTexts(it, raw, lang) }
        } catch (e: Exception) {
            _state.value = _state.value.copy(texts = cachedTexts, textsOffline = true)
        }
    }

    private fun parseSafe(raw: String): ChartTexts? =
        runCatching { ChartTexts.parse(raw) }.getOrNull()

    fun save() {
        val e = _state.value.entity ?: return
        analytics.track("chart_saved")
        viewModelScope.launch {
            var toSave = if (textsRaw != null) e.copy(textsJson = textsRaw, textsLang = lang) else e
            // Правка уже синхронизированной карты — пометить для пересоздания на сервере.
            if (toSave.serverId != null) toSave = toSave.copy(dirty = true)
            val newId = dao.upsert(toSave.copy(updatedAt = System.currentTimeMillis()))
            val saved = toSave.copy(id = if (e.id > 0) e.id else newId)
            _state.value = _state.value.copy(entity = saved, savedId = saved.id)
            syncManager.sync()
        }
    }

    fun delete() {
        val entity = _state.value.entity ?: return
        val id = _state.value.savedId ?: return
        viewModelScope.launch {
            if (entity.serverId != null) dao.markPendingDelete(id) else dao.delete(id)
            syncManager.sync()
        }
    }

    fun edit() {
        ChartDraftHolder.editRequest = _state.value.entity
    }
}
