package app.mplanner.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudyReplannerTest {
    @Test fun schoolDatesAreCompleteAndUnique() {
        val exams = SchoolExamSchedule.exams
        assertEquals(12, exams.size)
        assertEquals(12, exams.map { it.id }.toSet().size)
        assertTrue(exams.all { it.dateStatus == DateStatus.CONFIRMED_SCHOOL })
    }

    @Test fun examDayDefersPlannedButNotCompletedWork() {
        val date = LocalDate.parse("2026-11-18")
        val tasks = listOf(
            StudyTask("a", date, Subject.BIOLOGY, "Biology revision"),
            StudyTask("b", date, Subject.CHEMISTRY, "Chemistry notes", StudyTaskState.COMPLETED)
        )
        val result = StudyReplanner(SchoolExamSchedule.exams).plan(date, tasks)
        assertEquals(1, result.exams.size)
        assertEquals(1, result.deferredTasks.size)
        assertEquals("a", result.deferredTasks.single().id)
        assertEquals(StudyTaskState.DEFERRED, result.deferredTasks.single().state)
        assertEquals("b", result.activeTasks.single().id)
        assertEquals(StudyTaskState.PLANNED, tasks.first().state)
    }

    @Test fun ordinaryDayDoesNotModifyTasks() {
        val date = LocalDate.parse("2026-12-10")
        val task = StudyTask("a", date, Subject.PHYSICS, "Review")
        val result = StudyReplanner(SchoolExamSchedule.exams).plan(date, listOf(task))
        assertTrue(result.exams.isEmpty())
        assertEquals(listOf(task), result.activeTasks)
        assertTrue(result.deferredTasks.isEmpty())
    }

    @Test fun yogaEndTimeIsExplicitButOtherEndTimesRemainUnverified() {
        val yoga = SchoolExamSchedule.exams.first { it.subject == Subject.YOGA }
        val physics = SchoolExamSchedule.exams.first { it.subject == Subject.PHYSICS }
        assertEquals("11:30", yoga.writingEnd.toString())
        assertEquals(null, physics.writingEnd)
    }
}
