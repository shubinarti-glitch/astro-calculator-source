package ru.astrosmap.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "saved_materials")
data class SavedMaterial(
    @androidx.room.PrimaryKey val id: String = UUID.randomUUID().toString(),
    val ownerKey: String = "local",
    val sourceType: String,
    val sourceId: String = "",
    val title: String,
    val body: String,
    val note: String = "",
    val tags: String = "",
    val folder: String = "",
    val includeInPdf: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

object SavedMaterialPolicy {
    const val FREE_LIMIT = 10
    fun canAdd(currentCount: Int, premium: Boolean): Boolean = premium || currentCount < FREE_LIMIT
    fun matches(value: SavedMaterial, query: String): Boolean = query.isBlank() ||
        listOf(value.title, value.body, value.note, value.tags, value.folder)
            .any { it.contains(query.trim(), ignoreCase = true) }
}

@Dao
interface SavedMaterialDao {
    @Query("SELECT * FROM saved_materials WHERE ownerKey = :owner ORDER BY updatedAt DESC")
    fun observe(owner: String): Flow<List<SavedMaterial>>

    @Query("SELECT * FROM saved_materials WHERE ownerKey = :owner ORDER BY updatedAt DESC")
    suspend fun all(owner: String): List<SavedMaterial>

    @Query("SELECT COUNT(*) FROM saved_materials WHERE ownerKey = :owner")
    suspend fun count(owner: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: SavedMaterial)

    @Query("DELETE FROM saved_materials WHERE id = :id AND ownerKey = :owner")
    suspend fun delete(id: String, owner: String)
}

@Database(entities = [SavedMaterial::class], version = 1, exportSchema = false)
abstract class SavedMaterialDb : RoomDatabase() {
    abstract fun dao(): SavedMaterialDao

    companion object {
        @Volatile private var instance: SavedMaterialDb? = null

        fun get(context: Context): SavedMaterialDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SavedMaterialDb::class.java,
                "saved_materials.db",
            ).build().also { instance = it }
        }
    }
}
