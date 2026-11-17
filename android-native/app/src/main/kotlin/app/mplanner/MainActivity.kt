package app.mplanner

import android.os.Bundle
import android.os.Build
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mplanner.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                val model: PlannerViewModel = viewModel()
                PlannerApp(model)
            }
        }
    }
}

private enum class Tab(val title: String) {
    TODAY("Today"), PLAN("Plan"), REVISE("Revise"), FOCUS("Focus"), INSIGHTS("Insights")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlannerApp(model: PlannerViewModel) {
    var tab by rememberSaveableTab()
    var date by remember { mutableStateOf(LocalDate.now()) }
    var settings by remember { mutableStateOf(false) }
    val tasks by model.tasks.collectAsState()
    val exams by model.exams.collectAsState()
    val sessions by model.sessions.collectAsState()
    val revisions by model.revisions.collectAsState()
    val mocks by model.mocks.collectAsState()
    val context = LocalContext.current
    var remindersEnabled by remember { mutableStateOf(RevisionNotifications.isEnabled(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) { RevisionNotifications.enable(context); remindersEnabled = true }
    }
    val toggleReminders = {
        if (remindersEnabled) {
            RevisionNotifications.disable(context)
            remindersEnabled = false
        } else if(Build.VERSION.SDK_INT>=33) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            RevisionNotifications.enable(context)
            remindersEnabled = true
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write((model.exportContent ?: "").toByteArray(Charsets.UTF_8))
                } ?: error("Cannot open selected file")
            }.onSuccess { model.exportSaved() }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readText().also { require(it.length <= 2_000_000) }
            } ?: error("Unable to read file")
        }.onSuccess { model.importBackup(it) }
    }
    LaunchedEffect(model.exportContent) {
        if (model.exportContent != null) exportLauncher.launch("mplanner-backup-${LocalDate.now()}.json")
    }

    if (settings) {
        AlertDialog(
            onDismissRequest = { settings = false },
            title = { Text("Backup & restore") },
            text = { Text("Export a JSON copy of your progress. To migrate from the old Tauri app, export its pcb2 JSON using Back up and import that file here. Importing is additive; make a backup first.") },
            confirmButton = { TextButton(onClick = { model.prepareExport(); settings = false }) { Text("Export") } },
            dismissButton = { Row {
                TextButton(onClick = { importLauncher.launch(arrayOf("application/json","text/plain")); settings = false }) { Text("Import") }
                TextButton(onClick = { settings = false }) { Text("Close") }
            } }
        )
    }
    model.message?.let { message ->
        AlertDialog(
            onDismissRequest = model::clearMessage,
            title = { Text("mPlanner") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = model::clearMessage) { Text("OK") } }
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("mPlanner · ${tab.title}") },
                actions = {
                    TextButton(onClick = toggleReminders) { Text(if(remindersEnabled) "Alerts on" else "Alerts off") }
                    TextButton(onClick = { settings = true }) { Text("Backup") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == tab,
                        onClick = { tab = item },
                        icon = { Text(when(item) {
                            Tab.TODAY -> "●"; Tab.PLAN -> "▦"; Tab.REVISE -> "↻"
                            Tab.FOCUS -> "◷"; Tab.INSIGHTS -> "▥"
                        }) },
                        label = { Text(item.title) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            when (tab) {
                Tab.TODAY -> TodayScreen(date,tasks,exams,{ date=it },{ model.toggleTask(it) },{ day,title,subject -> model.addTask(day,title,subject) },{ model.updateTask(it) })
                Tab.PLAN -> PlanScreen(date,tasks,exams,{ date=it;tab=Tab.TODAY }, { d,s -> model.addExam(d,s) })
                Tab.REVISE -> RevisionScreen(revisions,{ event,rating -> model.review(event,rating) })
                Tab.FOCUS -> FocusScreen(model)
                Tab.INSIGHTS -> InsightsScreen(tasks,sessions,mocks,{ subject,score,maximum -> model.addMock(subject,score,maximum) })
            }
        }
    }
}

@Composable
private fun rememberSaveableTab(): MutableState<Tab> {
    // Compose remembers this across recompositions; screens can be reopened at any time.
    return remember { mutableStateOf(Tab.TODAY) }
}

@Composable
private fun TodayScreen(
    date: LocalDate, tasks: List<TaskEntity>, exams: List<ExamEntity>,
    onDate: (LocalDate) -> Unit,
    onToggle: (TaskEntity) -> Unit,
    onAdd: (LocalDate,String,String?) -> Unit,
    onUpdate: (TaskEntity) -> Unit
) {
    var newTitle by remember(date) { mutableStateOf("") }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf("") }
    if (editing != null) {
        AlertDialog(
            onDismissRequest = { editing = null }, title = { Text("Edit study task") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value=editTitle,onValueChange={editTitle=it.take(300)},label={Text("Task")})
                OutlinedTextField(value=editDate,onValueChange={editDate=it.take(10)},label={Text("Date (YYYY-MM-DD)")})
            } },
            confirmButton = { TextButton(onClick = {
                val task=editing!!
                runCatching { LocalDate.parse(editDate) }.onSuccess {
                    if(editTitle.isNotBlank()) { onUpdate(task.copy(title=editTitle.trim(),date=it.toString())); editing=null }
                }
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing=null }) { Text("Cancel") } }
        )
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        TextButton(onClick={onDate(date.minusDays(1))}) { Text("‹") }
        Text(date.format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy")),fontWeight=FontWeight.Bold)
        TextButton(onClick={onDate(date.plusDays(1))}) { Text("›") }
        TextButton(onClick={onDate(LocalDate.now())}) { Text("Today") }
    }
    val dayExams = exams.filter { it.date == date.toString() }
    val dayTasks = tasks.filter { it.date == date.toString() }
    val completed = dayTasks.count { it.state=="COMPLETED" }
    LinearProgressIndicator(progress={ if(dayTasks.isEmpty()) 0f else completed.toFloat()/dayTasks.size },modifier=Modifier.fillMaxWidth())
    Text("$completed / ${dayTasks.size} tasks complete",style=MaterialTheme.typography.labelMedium)
    LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(dayExams.isNotEmpty()) {
            item { Text("EXAM DAY · Prioritize these exams",color=MaterialTheme.colorScheme.error,fontWeight=FontWeight.Bold) }
            items(dayExams,key={it.id}) { exam ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("${exam.subject} · ${exam.series}",fontWeight=FontWeight.Bold)
                        Text("Reading: ${exam.readingStart ?: "TBC"} · Writing: ${exam.writingStart ?: "TBC"}–${exam.writingEnd ?: "End time unverified"}")
                    }
                }
            }
            item { Text("Your original tasks are preserved. Edit their dates or move them to tomorrow to avoid conflicts.") }
        }
        items(dayTasks,key={it.id}) { task ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=task.state=="COMPLETED",onCheckedChange={onToggle(task)})
                    Column(Modifier.weight(1f)) {
                        Text(task.title)
                        Text(task.subject ?: "General",style=MaterialTheme.typography.labelSmall)
                    }
                    if(dayExams.isNotEmpty() && task.state!="COMPLETED") {
                        TextButton(onClick={onUpdate(task.copy(date=date.plusDays(1).toString()))}) { Text("Tomorrow") }
                    }
                    TextButton(onClick={ editing=task;editTitle=task.title;editDate=task.date }) { Text("Edit") }
                }
            }
        }
    }
    Row(verticalAlignment=Alignment.CenterVertically) {
        OutlinedTextField(value=newTitle,onValueChange={newTitle=it.take(300)},label={Text("Add study task")},
            singleLine=true,modifier=Modifier.weight(1f))
        TextButton(onClick={if(newTitle.isNotBlank()){onAdd(date,newTitle,null);newTitle=""}}) { Text("Add") }
    }
}

@Composable
private fun PlanScreen(
    date:LocalDate,tasks:List<TaskEntity>,exams:List<ExamEntity>,
    onDate:(LocalDate)->Unit,onAddExam:(LocalDate,String)->Unit
) {
    var filter by remember { mutableStateOf("Upcoming") }
    val today=LocalDate.now()
    var showNewExam by remember { mutableStateOf(false) }
    var examDate by remember { mutableStateOf(date.toString()) }
    var examSubject by remember { mutableStateOf("") }
    if(showNewExam) {
        AlertDialog(onDismissRequest={showNewExam=false},
            title={Text("Add examination")},
            text={Column {
                OutlinedTextField(value=examSubject,onValueChange={examSubject=it.take(100)},label={Text("Subject or exam name")})
                OutlinedTextField(value=examDate,onValueChange={examDate=it.take(10)},label={Text("Date YYYY-MM-DD")})
            }},
            confirmButton={TextButton(onClick={
                runCatching { LocalDate.parse(examDate) }.onSuccess { d ->
                    if(examSubject.isNotBlank()){onAddExam(d,examSubject);showNewExam=false}
                }
            }){Text("Save")}},
            dismissButton={TextButton(onClick={showNewExam=false}){Text("Cancel")}})
    }
    Column {
        Button(onClick={showNewExam=true}) { Text("Add exam") }
        Row {
            FilterChip(selected=filter=="Upcoming",onClick={filter="Upcoming"},label={Text("Upcoming")})
            Spacer(Modifier.width(8.dp))
            FilterChip(selected=filter=="All",onClick={filter="All"},label={Text("All exams")})
        }
        val selected = exams.filter { filter=="All" || !LocalDate.parse(it.date).isBefore(today) }.sortedBy { it.date }
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item { Text("School exam timetable",style=MaterialTheme.typography.titleMedium) }
            items(selected,key={it.id}) { exam ->
                ElevatedCard(onClick={onDate(LocalDate.parse(exam.date))}) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("${exam.date} • ${exam.subject}",fontWeight=FontWeight.Bold)
                        Text("${exam.series} · ${exam.status}")
                    }
                }
            }
            item { Text("${tasks.count { it.state=="PLANNED" }} study tasks remaining across the calendar.") }
        }
    }
}

@Composable
private fun RevisionScreen(revisions:List<RevisionEntity>,onReview:(RevisionEntity,Int)->Unit) {
    val today=LocalDate.now().toString()
    val due=revisions.filter { !it.completed && it.due<=today }
    val upcoming=revisions.filter { !it.completed && it.due>today }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item { Text("${due.size} reviews due",style=MaterialTheme.typography.headlineSmall) }
        if(due.isEmpty()) item { Text("No reviews due. Completing study tasks schedules reviews after 1, 3, 7, and 21 days.") }
        items(due,key={it.id}) { event ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(event.title,fontWeight=FontWeight.Bold)
                    Text("Due ${event.due}",style=MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                        listOf("Again","Hard","Good","Easy").forEachIndexed { index,label ->
                            TextButton(onClick={onReview(event,index+1)}) { Text(label) }
                        }
                    }
                }
            }
        }
        item { Text("Coming up: ${upcoming.size}",style=MaterialTheme.typography.titleMedium) }
        items(upcoming.take(20),key={it.id}) { event ->
            ListItem(headlineContent={Text(event.title)},supportingContent={Text(event.due)})
        }
    }
}

@Composable
private fun FocusScreen(model: PlannerViewModel) {
    val context=LocalContext.current
    val tracker=remember { FocusTracker(context) }
    var tick by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf((tracker.durationSeconds/60).toInt()) }
    var subject by remember { mutableStateOf(tracker.subject ?: "General") }
    LaunchedEffect(Unit) {
        while(true) { delay(1000);tick++ }
    }
    val remaining=tracker.remainingSeconds()
    LaunchedEffect(tick) {
        if (tracker.isRunning() && remaining==0L) {
            tracker.finish()?.let { model.recordSession(it.id,it.date,it.seconds,it.subject) }
        }
    }
    Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("Focus on what matters",style=MaterialTheme.typography.headlineSmall)
        Text("%02d:%02d".format(remaining/60,remaining%60),style=MaterialTheme.typography.displayLarge)
        Row {
            listOf(25,50,90).forEach { value ->
                FilterChip(selected=minutes==value,onClick={ tracker.setDuration(value);minutes=value;tick++ },
                    label={Text("$value min")},modifier=Modifier.padding(horizontal=4.dp))
            }
        }
        OutlinedTextField(value=subject,onValueChange={subject=it.take(60);tracker.setSubject(subject)},label={Text("Subject / activity")})
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={
                if(tracker.isRunning()) tracker.pause() else { tracker.setSubject(subject);tracker.start() }
                tick++
            }) { Text(if(tracker.isRunning()) "Pause" else "Start") }
            OutlinedButton(onClick={
                tracker.finish()?.let { model.recordSession(it.id,it.date,it.seconds,it.subject) }
                tick++
            }) { Text("Save time") }
            TextButton(onClick={tracker.clear();tick++}) { Text("Discard") }
        }
        Text("Study duration is saved even when you pause early. The running timer uses Android's monotonic clock.")
    }
}

@Composable
private fun InsightsScreen(
    tasks:List<TaskEntity>,sessions:List<SessionEntity>,mocks:List<MockEntity>,
    onAddMock:(String,Double,Double)->Unit
) {
    var subject by remember { mutableStateOf("Physics") }
    var score by remember { mutableStateOf("") }
    var maximum by remember { mutableStateOf("") }
    val completed=tasks.count { it.state=="COMPLETED" }
    val totalSeconds=sessions.sumOf { it.seconds }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("Progress overview",style=MaterialTheme.typography.headlineSmall) }
        item { ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text("$completed / ${tasks.size} study tasks complete",fontWeight=FontWeight.Bold)
            Text("${"%.1f".format(totalSeconds/3600.0)} hours tracked in ${sessions.size} sessions")
        } } }
        item { Text("Focus time by subject",style=MaterialTheme.typography.titleMedium) }
        val groups=sessions.groupBy { it.subject ?: "General" }.mapValues { (_,s)->s.sumOf { it.seconds } }.toList().sortedByDescending { it.second }
        items(groups) { (name,seconds) -> ListItem(headlineContent={Text(name)},trailingContent={Text("${(seconds/60.0).roundToInt()} min")}) }
        item { Text("Add exam score",style=MaterialTheme.typography.titleMedium) }
        item {
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value=subject,onValueChange={subject=it.take(100)},label={Text("Exam / subject")})
                Row {
                    OutlinedTextField(value=score,onValueChange={score=it},label={Text("Score")},modifier=Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(value=maximum,onValueChange={maximum=it},label={Text("Out of")},modifier=Modifier.weight(1f))
                }
                Button(onClick={
                    val s=score.toDoubleOrNull();val m=maximum.toDoubleOrNull()
                    if(s!=null && m!=null && s>=0 && m>0 && s<=m) { onAddMock(subject,s,m);score="";maximum="" }
                }) { Text("Save score") }
            }
        }
        item { Text("Recent scores",style=MaterialTheme.typography.titleMedium) }
        items(mocks.reversed().take(30),key={it.id}) { mock ->
            ListItem(headlineContent={Text(mock.subject)},supportingContent={Text(mock.dateLabel)},
                trailingContent={Text("${mock.score}/${mock.maximum}")})
        }
    }
}
