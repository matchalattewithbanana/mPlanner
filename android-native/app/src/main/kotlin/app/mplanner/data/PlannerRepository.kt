package app.mplanner.data

import app.mplanner.domain.Exam
import app.mplanner.domain.SchoolExamSchedule
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

class PlannerRepository(private val dao: PlannerDao) {
    val tasks: Flow<List<TaskEntity>> = dao.observeTasks()
    val exams: Flow<List<ExamEntity>> = dao.observeExams()
    val sessions: Flow<List<SessionEntity>> = dao.observeSessions()

    suspend fun seedSchoolExams() {
        dao.insertExams(SchoolExamSchedule.exams.map(Exam::toEntity))
    }

    suspend fun addTask(date: LocalDate, title: String, subject: String? = null) {
        val trimmed = title.trim()
        require(trimmed.isNotBlank() && trimmed.length <= 300)
        dao.upsertTask(TaskEntity(UUID.randomUUID().toString(), date.toString(), subject, trimmed))
    }

    suspend fun setTaskDone(task: TaskEntity, done: Boolean) {
        dao.upsertTask(task.copy(state = if (done) "COMPLETED" else "PLANNED"))
    }

    private fun Exam.toEntity() = ExamEntity(
        id, date.toString(), subject.name, series.name, dateStatus.name,
        readingStart?.toString(), writingStart?.toString(), writingEnd?.toString()
    )
}
