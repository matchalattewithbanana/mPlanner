package app.mplanner

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.mplanner.data.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class PlannerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlannerRepository(PlannerDatabase.get(application))
    val tasks = repository.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val exams = repository.exams.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sessions = repository.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val revisions = repository.revisions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val mocks = repository.mocks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var message by mutableStateOf<String?>(null)
        private set
    var exportContent by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            runCatching { repository.seedDefaults(application) }
                .onFailure { message = "Couldn't initialize study plan: ${it.message}" }
        }
    }

    fun clearMessage() { message = null }
    fun addExam(date: LocalDate, subject: String) = execute {
        repository.addExam(date, subject)
    }
    fun addTask(date: LocalDate, title: String, subject: String? = null) = execute {
        repository.addTask(date, title, subject)
    }
    fun updateTask(task: TaskEntity) = execute { repository.updateTask(task) }
    fun removeTask(task: TaskEntity) = execute { repository.removeTask(task.id) }
    fun toggleTask(task: TaskEntity) = execute { repository.setTaskDone(task, task.state != "COMPLETED") }
    fun review(event: RevisionEntity, rating: Int) = execute { repository.review(event, rating) }
    fun recordSession(id: String, date: LocalDate, seconds: Long, subject: String?, onSaved: () -> Unit = {}) = execute {
        repository.recordSession(id,date,seconds,subject)
        onSaved()
    }
    fun addMock(subject: String, score: Double, maximum: Double) = execute {
        repository.addMock(subject,score,maximum)
    }
    fun importBackup(json: String) = execute {
        val imported = repository.importBackup(json)
        message = "Imported $imported records; existing records were preserved."
    }
    fun prepareExport() = execute {
        exportContent = repository.exportBackup()
    }
    fun exportSaved() { exportContent = null; message = "Backup exported." }

    private fun execute(action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }.onFailure { message = it.message ?: "Operation failed" }
        }
    }
}
