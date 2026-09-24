package ru.astrosmap.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import ru.astrosmap.app.astro.BirthInput

/** Сохранённая карта. serverId — привязка к /api/profiles (заполняется синхронизацией). */
@Entity(tableName = "charts")
data class ChartEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long? = null,
    val name: String,
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val lat: Double,
    val lng: Double,
    val tz: String,
    val city: String,
    val updatedAt: Long = System.currentTimeMillis(),
    /** Кэш ответа /api/natal?svg=0 (трактовки) и язык, на котором он получен. */
    val textsJson: String? = null,
    val textsLang: String? = null,
    /** Локальные правки ещё не доехали до сервера. */
    val dirty: Boolean = false,
    /** Удалена локально, ждёт удаления на сервере (скрыта из списка). */
    val pendingDelete: Boolean = false,
) {
    fun toBirthInput() = BirthInput(year, month, day, hour, minute, lat, lng, tz)
}

@Dao
interface ChartDao {
    @Query(
        "SELECT * FROM charts WHERE pendingDelete = 0 AND (name LIKE '%'||:q||'%' OR city LIKE '%'||:q||'%') " +
            "ORDER BY updatedAt DESC",
    )
    fun search(q: String): Flow<List<ChartEntity>>

    @Query("SELECT * FROM charts")
    suspend fun allOnce(): List<ChartEntity>

    @Query("SELECT * FROM charts WHERE id = :id")
    suspend fun byId(id: Long): ChartEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChartEntity): Long

    @Query("DELETE FROM charts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE charts SET textsJson = :json, textsLang = :lang WHERE id = :id")
    suspend fun updateTexts(id: Long, json: String, lang: String)

    @Query("UPDATE charts SET pendingDelete = 1 WHERE id = :id")
    suspend fun markPendingDelete(id: Long)

    @Query("UPDATE charts SET serverId = :serverId, dirty = 0 WHERE id = :id")
    suspend fun markSynced(id: Long, serverId: Long)
}

@Database(entities = [ChartEntity::class], version = 3, exportSchema = false)
abstract class ChartDb : RoomDatabase() {
    abstract fun chartDao(): ChartDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE charts ADD COLUMN textsJson TEXT")
                db.execSQL("ALTER TABLE charts ADD COLUMN textsLang TEXT")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE charts ADD COLUMN dirty INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE charts ADD COLUMN pendingDelete INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
