package com.samfoy.batomon.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val mode: String = "Unknown",
    val result: String = "In progress",
    val rounds: Int = 0,
    val source: String = "manual",
    val board: String = "",
    val lives: Int = 3,
    val roundResults: String = "",
    val notes: String = ""
)

@Entity(tableName = "observations")
data class ObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val scene: String,
    val confidence: Float,
    val observedAt: Long,
    val details: String = ""
)

@Dao
interface RunDao {
    @Query("SELECT * FROM runs ORDER BY startedAt DESC") fun observeRuns(): Flow<List<RunEntity>>
    @Query("SELECT * FROM runs ORDER BY startedAt DESC") fun getRuns(): List<RunEntity>
    @Insert suspend fun insert(run: RunEntity): Long
    @Update suspend fun update(run: RunEntity)
    @Update fun updateBlocking(run: RunEntity)
    @Insert fun insertBlocking(run: RunEntity): Long
    @Insert suspend fun insertObservation(observation: ObservationEntity)
}

@Database(entities = [RunEntity::class, ObservationEntity::class], version = 2, exportSchema = false)
abstract class BatomonDatabase : RoomDatabase() {
    abstract fun runs(): RunDao
    companion object { @Volatile private var instance: BatomonDatabase? = null
        fun get(context: android.content.Context): BatomonDatabase = instance ?: synchronized(this) { instance ?: Room.databaseBuilder(context.applicationContext, BatomonDatabase::class.java, "batomon.db").fallbackToDestructiveMigration().build().also { instance = it } }
    }
}
