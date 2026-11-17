package app.mplanner.domain

import java.time.LocalDate
import java.time.LocalTime

enum class ExamSeries { PREBOARD, REHEARSAL, BOARD, ENTRANCE }
enum class DateStatus { CONFIRMED_SCHOOL, VERIFIED_OFFICIAL, TENTATIVE }
enum class Subject { PHYSICS, CHEMISTRY, BIOLOGY, PSYCHOLOGY, ENGLISH, YOGA }

data class Exam(
    val id: String,
    val subject: Subject,
    val date: LocalDate,
    val series: ExamSeries,
    val dateStatus: DateStatus,
    val readingStart: LocalTime? = null,
    val writingStart: LocalTime? = null,
    val writingEnd: LocalTime? = null
)

object SchoolExamSchedule {
    private val readingStart = LocalTime.of(9, 15)
    private val writingStart = LocalTime.of(9, 30)

    private fun exam(series: ExamSeries, subject: Subject, date: String): Exam {
        val end = if (subject == Subject.YOGA) LocalTime.of(11, 30) else null
        return Exam(
            id = "${series.name.lowercase()}-${subject.name.lowercase()}-$date",
            subject = subject,
            date = LocalDate.parse(date),
            series = series,
            dateStatus = DateStatus.CONFIRMED_SCHOOL,
            readingStart = readingStart,
            writingStart = writingStart,
            writingEnd = end
        )
    }

    val exams: List<Exam> = listOf(
        exam(ExamSeries.PREBOARD, Subject.CHEMISTRY, "2026-11-18"),
        exam(ExamSeries.PREBOARD, Subject.ENGLISH, "2026-11-19"),
        exam(ExamSeries.PREBOARD, Subject.PHYSICS, "2026-11-23"),
        exam(ExamSeries.PREBOARD, Subject.BIOLOGY, "2026-11-25"),
        exam(ExamSeries.PREBOARD, Subject.PSYCHOLOGY, "2026-11-28"),
        exam(ExamSeries.PREBOARD, Subject.YOGA, "2026-12-01"),
        exam(ExamSeries.REHEARSAL, Subject.PHYSICS, "2026-12-15"),
        exam(ExamSeries.REHEARSAL, Subject.PSYCHOLOGY, "2026-12-17"),
        exam(ExamSeries.REHEARSAL, Subject.ENGLISH, "2026-12-18"),
        exam(ExamSeries.REHEARSAL, Subject.CHEMISTRY, "2027-01-02"),
        exam(ExamSeries.REHEARSAL, Subject.BIOLOGY, "2027-01-04"),
        exam(ExamSeries.REHEARSAL, Subject.YOGA, "2027-01-06")
    )
}
