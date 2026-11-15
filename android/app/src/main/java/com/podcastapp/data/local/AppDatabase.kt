package com.podcastapp.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/** Універсальний кеш відповідей сервера (JSON) для роботи без мережі. */
@Entity(tableName = "cache")
data class CacheEntry(@PrimaryKey val key: String, val json: String)

/** Остання позиція прослуховування — щоб продовжити з того ж місця навіть офлайн. */
@Entity(tableName = "progress")
data class ProgressEntity(@PrimaryKey val episodeId: String, val positionSec: Int, val updatedAt: Long)

@Dao
interface CacheDao {
    @Query("SELECT * FROM cache WHERE `key` = :key") suspend fun get(key: String): CacheEntry?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(entry: CacheEntry)
    @Query("DELETE FROM cache WHERE `key` = :key") suspend fun delete(key: String)
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE episodeId = :id") suspend fun get(id: String): ProgressEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(p: ProgressEntity)
}

@Database(entities = [CacheEntry::class, ProgressEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    abstract fun progressDao(): ProgressDao
}
