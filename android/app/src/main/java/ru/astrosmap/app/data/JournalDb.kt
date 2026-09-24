package ru.astrosmap.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "journal_entries", primaryKeys = ["ownerKey", "epochDay"])
data class JournalEntry(
    val ownerKey: String,
    val epochDay: Long,
    val mood: Int,
    val energy: Int,
    val relationships: Int,
    val work: Int,
    val wellbeing: Int,
    val event: String,
    val gratitude: String,
    val note: String,
    val tags: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries WHERE ownerKey = :ownerKey AND epochDay >= :fromEpochDay ORDER BY epochDay DESC")
    suspend fun since(ownerKey: String, fromEpochDay: Long): List<JournalEntry>

    @Query("SELECT * FROM journal_entries WHERE ownerKey = :ownerKey ORDER BY epochDay DESC")
    suspend fun all(ownerKey: String): List<JournalEntry>

    @Query("SELECT * FROM journal_entries WHERE ownerKey = :ownerKey AND epochDay = :epochDay LIMIT 1")
    suspend fun byDate(ownerKey: String, epochDay: Long): JournalEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: JournalEntry)

    @Query("DELETE FROM journal_entries WHERE ownerKey = :ownerKey AND epochDay = :epochDay")
    suspend fun delete(ownerKey: String, epochDay: Long)
}

@Database(entities = [JournalEntry::class], version = 1, exportSchema = false)
abstract class JournalDb : RoomDatabase() {
    abstract fun journalDao(): JournalDao
}
