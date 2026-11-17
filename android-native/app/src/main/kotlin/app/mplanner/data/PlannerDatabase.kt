package app.mplanner.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "study_tasks", indices = [Index(value = ["date"])])
data class TaskEntity(
    @PrimaryKey val id: String,
    val date: String,
    val subject: String?,
    val title: String,
    val state: String = "PLANNED",
    val durationMinutes: Int = 0
)

@Entity(tableName = "exams", indices = [Index(value = ["date"])])
data class ExamEntity(
    @PrimaryKey val id: String,
    val date: String,
    val subject: String,
    val series: String,
    val status: String,
    val readingStart: String?,
    val writingStart: String?,
    val writingEnd: String?
)

@Entity(tableName = "study_sessions", indices = [Index(value = ["date"])])
data class SessionEntity(
    @PrimaryKey val id: String,
    val date: String,
    val subject: String?,
    val seconds: Long,
    val endedAtEpochMillis: Long
)

@Dao
interface PlannerDao {
    @Query("SELECT * FROM study_tasks ORDER BY date, id")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM exams ORDER BY date, id")
    fun observeExams(): Flow<List<ExamEntity>>

    @Query("SELECT * FROM study_sessions ORDER BY endedAtEpochMillis DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExams(exams: List<ExamEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: SessionEntity)

    @Query("SELECT COUNT(*) FROM exams")
    suspend fun examCount(): Int
}

@Database(entities = [TaskEntity::class, ExamEntity::class, SessionEntity::class], version = 1, exportSchema = true)
abstract class PlannerDatabase : RoomDatabase() {
    abstract fun dao(): PlannerDao

    companion object {
        @Volatile private var instance: PlannerDatabase? = null
        fun get(context: Context): PlannerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, PlannerDatabase::class.java, "mplanner.db")
                    .build().also { instance = it }
            }
    }
}
