package app.mplanner

import android.content.Context
import android.os.SystemClock
import java.time.LocalDate
import java.util.UUID
import kotlin.math.min

/**
 * The monotonic clock survives app process termination, backgrounding, and screen-off
 * while the device remains on. A reboot or a substantial clock-anchor change safely
 * pauses the session instead of charging hours of nonexistent study time.
 */
class FocusTracker(context: Context) {
    private val preferences = context.getSharedPreferences("mplanner_focus", Context.MODE_PRIVATE)
    private val anchorToleranceMs = 120_000L

    val durationSeconds: Long get() = preferences.getLong("duration", 25 * 60)
    val subject: String? get() = preferences.getString("subject", null)

    fun setDuration(minutes: Int) {
        require(minutes in 1..180)
        clear()
        preferences.edit().putLong("duration", minutes * 60L).apply()
    }
    fun setSubject(subject: String?) { preferences.edit().putString("subject", subject).apply() }

    fun isRunning(): Boolean = preferences.getBoolean("running", false)

    fun elapsedSeconds(): Long {
        val saved = preferences.getLong("accumulated", 0L)
        if (!isRunning()) return saved.coerceAtLeast(0L)
        val previousElapsed = preferences.getLong("startElapsed", 0L)
        val previousWall = preferences.getLong("startWall", 0L)
        val mono = SystemClock.elapsedRealtime() - previousElapsed
        val wall = System.currentTimeMillis() - previousWall
        if (mono < 0L || kotlin.math.abs(wall - mono) > anchorToleranceMs) {
            preferences.edit().putBoolean("running", false).apply()
            return saved.coerceAtLeast(0L)
        }
        return (saved + mono / 1000).coerceIn(0L,durationSeconds)
    }

    fun start() {
        if (isRunning()) return
        preferences.edit()
            .putBoolean("running", true)
            .putLong("startElapsed", SystemClock.elapsedRealtime())
            .putLong("startWall", System.currentTimeMillis())
            .apply()
    }
    fun pause() {
        val elapsed = elapsedSeconds()
        preferences.edit().putBoolean("running",false).putLong("accumulated",elapsed).apply()
    }
    fun clear() {
        preferences.edit().putBoolean("running",false).putLong("accumulated",0).apply()
    }
    fun finish(): CompletedFocus? {
        val seconds = elapsedSeconds()
        if (seconds <= 0) { clear(); return null }
        val id = preferences.getString("sessionId",null) ?: UUID.randomUUID().toString()
        val date = preferences.getString("sessionDate",null)?.let(LocalDate::parse) ?: LocalDate.now()
        val selectedSubject = subject
        // Reuse the id after process death, so Room's INSERT IGNORE avoids duplicates.
        preferences.edit().putString("sessionId",id).putString("sessionDate",date.toString()).apply()
        clear()
        preferences.edit().remove("sessionId").remove("sessionDate").apply()
        return CompletedFocus(id, date, seconds, selectedSubject)
    }
    fun remainingSeconds():Long = (durationSeconds-elapsedSeconds()).coerceAtLeast(0)
}

data class CompletedFocus(val id:String,val date:LocalDate,val seconds:Long,val subject:String?)
