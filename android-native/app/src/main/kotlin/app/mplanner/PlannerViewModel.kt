package app.mplanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.mplanner.data.PlannerDatabase
import app.mplanner.data.PlannerRepository
import app.mplanner.data.TaskEntity
import app.mplanner.domain.SchoolExamSchedule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class PlannerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlannerRepository(PlannerDatabase.get(application).dao())
    val tasks = repository.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val exams = repository.exams.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sessions = repository.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { viewModelScope.launch { repository.seedSchoolExams() } }

    fun addTask(date: LocalDate, title: String) = viewModelScope.launch {
        repository.addTask(date, title)
    }

    fun toggleTask(task: TaskEntity) = viewModelScope.launch {
        repository.setTaskDone(task, task.state != "COMPLETED")
    }
}
