package app.mplanner.domain

import java.time.LocalDate

enum class StudyTaskState { PLANNED, COMPLETED, DEFERRED }

data class StudyTask(
    val id: String,
    val date: LocalDate,
    val subject: Subject?,
    val title: String,
    val state: StudyTaskState = StudyTaskState.PLANNED
)

data class DayPlan(
    val date: LocalDate,
    val exams: List<Exam>,
    val activeTasks: List<StudyTask>,
    val deferredTasks: List<StudyTask>
)

/**
 * Non-destructive exam-first overlay. Existing completed tasks are retained,
 * and planned tasks on exam days are moved to a visible backlog rather than lost.
 * Callers decide when and where to reschedule the deferred items.
 */
class StudyReplanner(private val exams: List<Exam>) {
    fun plan(date: LocalDate, tasks: List<StudyTask>): DayPlan {
        val dayExams = exams.filter { it.date == date }
        val dayTasks = tasks.filter { it.date == date }
        if (dayExams.isEmpty()) return DayPlan(date, emptyList(), dayTasks, emptyList())
        val deferred = dayTasks.filter { it.state == StudyTaskState.PLANNED }
            .map { it.copy(state = StudyTaskState.DEFERRED) }
        val retained = dayTasks.filter { it.state != StudyTaskState.PLANNED }
        return DayPlan(date, dayExams, retained, deferred)
    }
}
