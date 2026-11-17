package app.mplanner.data

import android.content.Context
import androidx.room.*
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

@Entity(tableName = "revision_events", indices = [Index(value = ["due"])])
data class RevisionEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val title: String,
    val due: String,
    val completed: Boolean = false,
    val rating: Int? = null
)

@Entity(tableName = "mock_scores")
data class MockEntity(
    @PrimaryKey val id: String,
    val dateLabel: String,
    val subject: String,
    val score: Double,
    val maximum: Double
)

@Dao
interface PlannerDao {
    @Query("SELECT * FROM study_tasks ORDER BY date, id")
    fun observeTasks(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM exams ORDER BY date, id")
    fun observeExams(): Flow<List<ExamEntity>>
    @Query("SELECT * FROM study_sessions ORDER BY endedAtEpochMillis DESC")
    fun observeSessions(): Flow<List<SessionEntity>>
    @Query("SELECT * FROM revision_events ORDER BY due, id")
    fun observeRevisions(): Flow<List<RevisionEntity>>
    @Query("SELECT * FROM mock_scores ORDER BY dateLabel, id")
    fun observeMocks(): Flow<List<MockEntity>>

    @Query("SELECT * FROM study_tasks")
    suspend fun allTasks(): List<TaskEntity>
    @Query("SELECT * FROM exams")
    suspend fun allExams(): List<ExamEntity>
    @Query("SELECT * FROM study_sessions")
    suspend fun allSessions(): List<SessionEntity>
    @Query("SELECT * FROM revision_events")
    suspend fun allRevisions(): List<RevisionEntity>
    @Query("SELECT * FROM mock_scores")
    suspend fun allMocks(): List<MockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TaskEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTasks(tasks: List<TaskEntity>)
    @Query("DELETE FROM study_tasks WHERE id = :id")
    suspend fun deleteTask(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExams(exams: List<ExamEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExam(exam: ExamEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: SessionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRevision(event: RevisionEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRevisions(events: List<RevisionEntity>)
    @Query("DELETE FROM revision_events WHERE taskId = :taskId AND completed = 0")
    suspend fun removePendingReviews(taskId: String)
    @Query("SELECT COUNT(*) FROM revision_events WHERE taskId = :taskId")
    suspend fun reviewCount(taskId: String): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMock(mock: MockEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMocks(mocks: List<MockEntity>)
}

@Database(
    entities = [TaskEntity::class, ExamEntity::class, SessionEntity::class, RevisionEntity::class, MockEntity::class],
    version = 1, exportSchema = false
)
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
