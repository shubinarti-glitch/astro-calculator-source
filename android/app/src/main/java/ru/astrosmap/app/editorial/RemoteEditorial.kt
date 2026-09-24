package ru.astrosmap.app.editorial

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import ru.astrosmap.app.data.api.AstroApi

@Serializable
data class EditorialPackage(val schemaVersion: Int, val cards: List<List<String>>,
                            val phaseAdvice: List<List<String>>, val moonMood: List<List<String>>)

/** Memory only. No assets, database, preferences or disk HTTP cache. */
object RemoteEditorial {
    private var content by mutableStateOf<EditorialPackage?>(null)
    private val mutex = Mutex()
    val cardIds: List<String> = listOf("fool", "magician", "priestess", "empress", "emperor",
        "hierophant", "lovers", "chariot", "strength", "hermit", "wheel", "justice", "hanged",
        "death", "temperance", "devil", "tower", "star", "moon", "sun", "judgement", "world")
        .mapIndexed { i, name -> "major_${i.toString().padStart(2, '0')}_$name" } +
        listOf("wands", "cups", "swords", "pents").flatMap { suit -> (1..14).map { "${suit}_${it.toString().padStart(2, '0')}" } }

    fun clear() { content = null }
    fun accept(value: EditorialPackage) {
        require(value.schemaVersion == 1)
        require(value.cards.size == 78 && value.cards.all { it.size == 7 && it.all(String::isNotBlank) })
        require(value.cards.map { it[0] } == cardIds)
        for ((rows, size) in listOf(value.phaseAdvice to 8, value.moonMood to 12)) {
            require(rows.size == size && rows.all { it.size == 3 && it.all(String::isNotBlank) })
            require(rows.map { it[0] }.distinct().size == size)
        }
        content = value
    }
    suspend fun refresh(api: AstroApi) = mutex.withLock {
        try { withTimeout(8000) { accept(api.editorial()) } }
        catch (e: kotlinx.coroutines.TimeoutCancellationException) { clear() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { clear() }
    }
    fun unavailable(ru: Boolean) = if (ru) "Текст недоступен. Подключитесь к интернету и повторите загрузку." else "Text unavailable. Connect to the internet and reload."
    fun card(id: String, column: Int, ru: Boolean): String =
        content?.cards?.firstOrNull { it[0] == id }?.getOrNull(column) ?: unavailable(ru)
    fun lunar(key: String, mood: Boolean, ru: Boolean): String {
        val rows = if (mood) content?.moonMood else content?.phaseAdvice
        return rows?.firstOrNull { it[0] == key }?.getOrNull(if (ru) 1 else 2) ?: unavailable(ru)
    }
}
