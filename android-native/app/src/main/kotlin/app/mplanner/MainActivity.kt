package app.mplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mplanner.data.ExamEntity
import app.mplanner.data.TaskEntity
import app.mplanner.domain.SchoolExamSchedule
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val model: PlannerViewModel = viewModel()
                PlannerApp(model)
            }
        }
    }
}

private enum class Tab(val title: String) { TODAY("Today"), PLAN("Plan"), REVISE("Revise"), FOCUS("Focus"), INSIGHTS("Insights") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlannerApp(model: PlannerViewModel) {
    var tab by remember { mutableStateOf(Tab.TODAY) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val tasks by model.tasks.collectAsState()
    val exams by model.exams.collectAsState()
    val sessions by model.sessions.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("mPlanner · ${tab.title}") }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == tab,
                        onClick = { tab = item },
                        icon = { Text(when(item) { Tab.TODAY -> "●"; Tab.PLAN -> "▦"; Tab.REVISE -> "↻"; Tab.FOCUS -> "◷"; Tab.INSIGHTS -> "▥" }) },
                        label = { Text(item.title) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            when (tab) {
                Tab.TODAY -> TodayScreen(selectedDate, tasks, exams, onDate = { selectedDate = it }, onToggle = model::toggleTask, onAdd = model::addTask)
                Tab.PLAN -> PlanScreen(tasks, exams, onDate = { selectedDate = it; tab = Tab.TODAY })
                Tab.REVISE -> PlaceholderScreen("Adaptive revision queue will be integrated after the legacy data importer.")
                Tab.FOCUS -> PlaceholderScreen("Reliable persisted focus sessions will be added in the timer milestone.")
                Tab.INSIGHTS -> InsightsScreen(tasks, sessions.size)
            }
        }
    }
}

@Composable
private fun TodayScreen(
    date: LocalDate,
    tasks: List<TaskEntity>,
    exams: List<ExamEntity>,
    onDate: (LocalDate) -> Unit,
    onToggle: (TaskEntity) -> Unit,
    onAdd: (LocalDate, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { onDate(date.minusDays(1)) }) { Text("Previous") }
        Text(date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")), modifier = Modifier.padding(top = 13.dp))
        TextButton(onClick = { onDate(date.plusDays(1)) }) { Text("Next") }
    }
    val dayExams = exams.filter { it.date == date.toString() }
    val dayTasks = tasks.filter { it.date == date.toString() }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
        if (dayExams.isNotEmpty()) {
            item { Text("School examinations · priority today", style = MaterialTheme.typography.titleMedium) }
            items(dayExams, key = { it.id }) { exam ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Text("${exam.subject} · ${exam.series}", Modifier.padding(16.dp))
                }
            }
        }
        item { Text("Study tasks", style = MaterialTheme.typography.titleMedium) }
        if (dayTasks.isEmpty()) item { Text("No study tasks added for this day.") }
        items(dayTasks, key = { it.id }) { task ->
            Row(Modifier.fillMaxWidth()) {
                Checkbox(checked = task.state == "COMPLETED", onCheckedChange = { onToggle(task) })
                Text(task.title, Modifier.padding(top = 14.dp))
            }
        }
    }
    Row(Modifier.fillMaxWidth()) {
        OutlinedTextField(value = title, onValueChange = { title = it.take(300) }, label = { Text("New task") }, singleLine = true, modifier = Modifier.weight(1f))
        TextButton(onClick = { if (title.isNotBlank()) { onAdd(date, title); title = "" } }, modifier = Modifier.padding(top = 10.dp)) { Text("Add") }
    }
}

@Composable
private fun PlanScreen(tasks: List<TaskEntity>, exams: List<ExamEntity>, onDate: (LocalDate) -> Unit) {
    val today = remember { LocalDate.now() }
    val upcoming = exams.filter { !LocalDate.parse(it.date).isBefore(today) }.sortedBy { it.date }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Upcoming confirmed school examinations", style = MaterialTheme.typography.titleMedium) }
        items(upcoming, key = { it.id }) { exam ->
            ElevatedCard(onClick = { onDate(LocalDate.parse(exam.date)) }) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("${exam.date} · ${exam.subject}", style = MaterialTheme.typography.titleMedium)
                    Text("${exam.series} · Read 09:15 · Start 09:30")
                }
            }
        }
        item { Text("${tasks.size} personal study tasks saved locally.") }
    }
}

@Composable
private fun InsightsScreen(tasks: List<TaskEntity>, sessions: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Completed: ${tasks.count { it.state == "COMPLETED" }} / ${tasks.size}")
        Text("Tracked sessions: $sessions")
        Text("Detailed subject analytics will follow the legacy import and timer.")
    }
}

@Composable
private fun PlaceholderScreen(description: String) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(description, style = MaterialTheme.typography.bodyLarge)
    }
}
