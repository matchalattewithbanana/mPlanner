package app.mplanner.data

import android.content.Context
import androidx.room.withTransaction
import app.mplanner.domain.Exam
import app.mplanner.domain.SchoolExamSchedule
import app.mplanner.domain.BoardExamSchedule
import app.mplanner.domain.TentativeEntranceSchedule
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class PlannerRepository(private val database: PlannerDatabase) {
    private val dao = database.dao()
    val tasks: Flow<List<TaskEntity>> = dao.observeTasks()
    val exams: Flow<List<ExamEntity>> = dao.observeExams()
    val sessions: Flow<List<SessionEntity>> = dao.observeSessions()
    val revisions: Flow<List<RevisionEntity>> = dao.observeRevisions()
    val mocks: Flow<List<MockEntity>> = dao.observeMocks()

    suspend fun seedDefaults(context: Context) {
        val preferences = context.getSharedPreferences("mplanner_seed", Context.MODE_PRIVATE)
        if (preferences.getBoolean("seeded_v1", false)) return
        val document = context.assets.open("study_plan_2026_2027.json").bufferedReader().use { it.readText() }
        val array = JSONObject(document).getJSONArray("tasks")
        val tasksToInsert = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            TaskEntity(
                item.getString("id"), item.getString("date"),
                item.optString("subject").takeUnless { item.isNull("subject") || it.isBlank() },
                item.getString("title")
            )
        }
        database.withTransaction {
            dao.insertExams((SchoolExamSchedule.exams + BoardExamSchedule.exams + TentativeEntranceSchedule.exams).map { it.toEntity() })
            dao.insertTasks(tasksToInsert)
        }
        check(preferences.edit().putBoolean("seeded_v1",true).commit()) { "Failed to save initialization status" }
    }

    suspend fun addExam(date: LocalDate, subject: String, title: String = "Personal exam") {
        val name = subject.trim().uppercase().take(100)
        require(name.isNotBlank())
        dao.upsertExam(ExamEntity(UUID.randomUUID().toString(), date.toString(), name, "PERSONAL", "USER_DEFINED", null, null, null))
    }

    suspend fun addTask(date: LocalDate, title: String, subject: String? = null) {
        val trimmed = title.trim()
        require(trimmed.isNotBlank() && trimmed.length <= 300)
        dao.upsertTask(TaskEntity(UUID.randomUUID().toString(), date.toString(), subject, trimmed))
    }

    suspend fun updateTask(task: TaskEntity) {
        require(task.title.isNotBlank() && task.title.length <= 300)
        require(task.durationMinutes in 0..1440)
        LocalDate.parse(task.date)
        dao.upsertTask(task)
    }

    suspend fun removeTask(id: String) = dao.deleteTask(id)

    suspend fun setTaskDone(task: TaskEntity, done: Boolean, today: LocalDate = LocalDate.now()) {
        database.withTransaction {
            dao.upsertTask(task.copy(state = if (done) "COMPLETED" else "PLANNED"))
            if (done) {
                if (dao.reviewCount(task.id) == 0) {
                    dao.insertRevisions(listOf(1L,3L,7L,21L).map { interval ->
                        RevisionEntity("${task.id}:$interval", task.id, task.title, today.plusDays(interval).toString())
                    })
                }
            } else {
                dao.removePendingReviews(task.id)
            }
        }
    }

    suspend fun review(event: RevisionEntity, rating: Int, today: LocalDate = LocalDate.now()) {
        require(rating in 1..4)
        database.withTransaction {
            dao.upsertRevision(event.copy(completed = true, rating = rating))
            if (rating < 3) {
                val retry = if (rating == 1) 1L else 3L
                val due = today.plusDays(retry).toString()
                dao.insertRevisions(listOf(RevisionEntity("${event.id}:retry:$due", event.taskId, event.title, due)))
            }
        }
    }

    suspend fun recordSession(id: String, date: LocalDate, seconds: Long, subject: String? = null) {
        if (seconds <= 0) return
        require(seconds <= 86_400)
        dao.insertSession(SessionEntity(id, date.toString(), subject, seconds, System.currentTimeMillis()))
    }

    suspend fun addMock(subject: String, score: Double, maximum: Double) {
        require(maximum > 0 && maximum.isFinite() && score.isFinite() && score in 0.0..maximum)
        dao.upsertMock(MockEntity(UUID.randomUUID().toString(), LocalDate.now().toString(), subject.take(100), score, maximum))
    }

    suspend fun exportBackup(): String = JSONObject().apply {
        put("schemaVersion", 2)
        put("app", "mPlanner")
        put("tasks", JSONArray().also { a -> dao.allTasks().forEach { t -> a.put(JSONObject().apply {
            put("id",t.id);put("date",t.date);put("subject",t.subject);put("title",t.title);put("state",t.state);put("durationMinutes",t.durationMinutes)
        }) } })
        put("exams", JSONArray().also { a -> dao.allExams().forEach { e -> a.put(JSONObject().apply {
            put("id",e.id);put("date",e.date);put("subject",e.subject);put("series",e.series);put("status",e.status)
            put("readingStart",e.readingStart);put("writingStart",e.writingStart);put("writingEnd",e.writingEnd)
        }) } })
        put("sessions", JSONArray().also { a -> dao.allSessions().forEach { s -> a.put(JSONObject().apply {
            put("id",s.id);put("date",s.date);put("subject",s.subject);put("seconds",s.seconds);put("endedAtEpochMillis",s.endedAtEpochMillis)
        }) } })
        put("revisions", JSONArray().also { a -> dao.allRevisions().forEach { e -> a.put(JSONObject().apply {
            put("id",e.id);put("taskId",e.taskId);put("title",e.title);put("due",e.due);put("completed",e.completed);put("rating",e.rating)
        }) } })
        put("mocks", JSONArray().also { a -> dao.allMocks().forEach { m -> a.put(JSONObject().apply {
            put("id",m.id);put("dateLabel",m.dateLabel);put("subject",m.subject);put("score",m.score);put("maximum",m.maximum)
        }) } })
    }.toString()

    /**
     * Accepts both the old Tauri pcb1/pcb2 JSON and versioned native exports.
     * Validate everything BEFORE beginning the transaction. Imports are idempotent.
     * This deliberately does not execute or render imported HTML.
     */
    suspend fun importBackup(raw: String): Int {
        require(raw.length in 2..2_000_000) { "Backup must be between 2 bytes and 2 MB" }
        val root = JSONObject(raw)
        val tasks = mutableListOf<TaskEntity>()
        val revisions = mutableListOf<RevisionEntity>()
        val sessions = mutableListOf<SessionEntity>()
        val mocks = mutableListOf<MockEntity>()
        val exams = mutableListOf<ExamEntity>()
        val native = root.optInt("schemaVersion", 0) == 2 && root.optString("app") == "mPlanner"
        require(native || (!root.has("schemaVersion") &&
            (root.has("done") || root.has("rv") || root.has("focus") || root.has("mocks") ||
             root.keys().asSequence().any { key ->
                 Regex("""^\d{4}-\d{1,2}-\d{1,2}\|[abnc]$""").matches(key)
             }))) { "Unsupported backup format; no data imported" }
        if (native) {
            fun objects(key: String, max: Int): List<JSONObject> {
                val a = root.optJSONArray(key) ?: JSONArray()
                require(a.length() <= max) { "$key contains too many entries" }
                return (0 until a.length()).map { a.getJSONObject(it) }
            }
            tasks += objects("tasks", 10_000).map {
                TaskEntity(
                    bounded(it.getString("id"), 160), validatedDate(it.getString("date")),
                    nullableString(it, "subject", 100), bounded(it.getString("title"), 300),
                    it.optString("state","PLANNED").also { state -> require(state in listOf("PLANNED","COMPLETED","DEFERRED")) },
                    it.optInt("durationMinutes",0).also { d -> require(d in 0..1440) }
                )
            }
            revisions += objects("revisions", 40_000).map {
                RevisionEntity(bounded(it.getString("id"), 200), bounded(it.getString("taskId"),160),
                    bounded(it.getString("title"),300),validatedDate(it.getString("due")),
                    it.optBoolean("completed",false), if(it.isNull("rating")) null else it.optInt("rating").also { r -> require(r in 1..4) })
            }
            sessions += objects("sessions", 50_000).map {
                SessionEntity(bounded(it.getString("id"),200), validatedDate(it.getString("date")),
                    nullableString(it,"subject",100), it.getLong("seconds").also { sec -> require(sec in 1..86400) },
                    it.getLong("endedAtEpochMillis").also { ms -> require(ms >= 0) })
            }
            exams += objects("exams", 1_000).map {
                ExamEntity(
                    bounded(it.getString("id"),160),validatedDate(it.getString("date")),
                    bounded(it.getString("subject"),100),bounded(it.getString("series"),100),
                    bounded(it.getString("status"),100),
                    nullableString(it,"readingStart",20),nullableString(it,"writingStart",20),
                    nullableString(it,"writingEnd",20)
                )
            }
            mocks += objects("mocks", 10_000).map {
                val score=it.getDouble("score");val maximum=it.getDouble("maximum")
                require(maximum>0 && maximum.isFinite() && score.isFinite() && score in 0.0..maximum)
                MockEntity(bounded(it.getString("id"),200),bounded(it.getString("dateLabel"),100),
                    bounded(it.getString("subject"),100),score,maximum)
            }
        } else {
            val oldDone = root.optJSONObject("done") ?: if (!root.has("done") && !root.has("rv") && !root.has("focus") && !root.has("mocks")) root else JSONObject()
            require(oldDone.length() <= 10_000)
            val keys = oldDone.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val m = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})\\|([abnc])$").matchEntire(key) ?: continue
                val date = validatedDate("${m.groupValues[1]}-${m.groupValues[2].padStart(2,'0')}-${m.groupValues[3].padStart(2,'0')}")
                if (oldDone.optInt(key,0) == 1) {
                    val id = "legacy:$date|${m.groupValues[4]}"
                    // Lookup by stable template id during the import transaction; do not replace titles.
                    tasks += TaskEntity(id,date,null,"",state="COMPLETED")
                }
            }
            val oldReviews = root.optJSONArray("rv") ?: JSONArray()
            require(oldReviews.length() <= 40_000)
            (0 until oldReviews.length()).forEach { i ->
                val e = oldReviews.getJSONObject(i)
                val taskId = e.optString("k").let { key ->
                    val m=Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})\\|([abnc])$").matchEntire(key)
                    if(m==null) "legacy:unknown:$i" else "legacy:${m.groupValues[1]}-${m.groupValues[2].padStart(2,'0')}-${m.groupValues[3].padStart(2,'0')}|${m.groupValues[4]}"
                }
                revisions += RevisionEntity("legacy-review:$i:${e.optString("due")}",taskId,
                    bounded(e.optString("t","Review"),300),validatedDate(e.getString("due")),
                    e.optInt("ok",0)==1)
            }
            val oldFocus = root.optJSONObject("focus") ?: JSONObject()
            require(oldFocus.length() <= 1_000)
            val focusKeys = oldFocus.keys()
            while (focusKeys.hasNext()) {
                val date=validatedDate(focusKeys.next())
                val minutes=oldFocus.optDouble(date,0.0)
                require(minutes.isFinite() && minutes in 0.0..1440.0)
                if(minutes>0) sessions += SessionEntity("legacy-focus:$date",date,null,(minutes*60).toLong(),0)
            }
            val oldMocks=root.optJSONArray("mocks") ?: JSONArray()
            require(oldMocks.length()<=10_000)
            (0 until oldMocks.length()).forEach { i ->
                val m=oldMocks.getJSONObject(i)
                val score=m.getDouble("s");val max=m.getDouble("o")
                require(max>0 && max.isFinite() && score.isFinite() && score in 0.0..max)
                mocks += MockEntity("legacy-mock:$i",bounded(m.optString("d","Legacy"),100),
                    bounded(m.getString("n"),100),score,max)
            }
        }
        database.withTransaction {
            val priorTasks = dao.allTasks().associateBy { it.id }
            for (task in tasks) {
                if (task.title.isEmpty()) {
                    val old = priorTasks[task.id]
                    if (old != null) dao.upsertTask(old.copy(state="COMPLETED"))
                } else dao.upsertTask(task)
            }
            exams.forEach { dao.upsertExam(it) }
            dao.insertRevisions(revisions)
            sessions.forEach { dao.insertSession(it) }
            dao.insertMocks(mocks)
        }
        return tasks.size + exams.size + revisions.size + sessions.size + mocks.size
    }

    private fun bounded(value:String, length:Int):String {
        require(value.isNotBlank() && value.length<=length) { "Invalid text field" }
        return value
    }
    private fun validatedDate(value:String):String = LocalDate.parse(value).toString()
    private fun nullableString(o:JSONObject,key:String,max:Int):String? =
        if (!o.has(key) || o.isNull(key)) null else bounded(o.getString(key),max)

    private fun Exam.toEntity() = ExamEntity(id,date.toString(),subject.name,series.name,dateStatus.name,
        readingStart?.toString(),writingStart?.toString(),writingEnd?.toString())
}
