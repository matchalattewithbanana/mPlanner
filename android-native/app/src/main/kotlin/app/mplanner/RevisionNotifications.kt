package app.mplanner

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import app.mplanner.data.PlannerDatabase
import java.time.LocalDate
import java.util.concurrent.TimeUnit

object RevisionNotifications {
    private const val channel = "mplanner_revision"
    private const val uniqueWork = "mplanner_daily_revisions"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences("mplanner_settings",Context.MODE_PRIVATE).getBoolean("reminders",false)

    fun enable(context: Context) {
        context.getSharedPreferences("mplanner_settings",Context.MODE_PRIVATE).edit().putBoolean("reminders",true).apply()
        val periodic = PeriodicWorkRequestBuilder<RevisionReminderWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            uniqueWork,ExistingPeriodicWorkPolicy.KEEP,periodic
        )
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<RevisionReminderWorker>().build())
    }

    fun disable(context: Context) {
        context.getSharedPreferences("mplanner_settings",Context.MODE_PRIVATE).edit().putBoolean("reminders",false).apply()
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWork)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(1001)
    }

    fun send(context: Context, due: Int) {
        if(due<=0 || !isEnabled(context)) return
        if(Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(channel,"Study revision reminders",NotificationManager.IMPORTANCE_DEFAULT))
        val intent=Intent(context,MainActivity::class.java)
        val pending=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notice=Notification.Builder(context,channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("mPlanner · Revision due")
            .setContentText("$due topic${if(due==1) "" else "s"} ready for review")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(1001,notice)
    }
}

class RevisionReminderWorker(context: Context,parameters: WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork(): Result {
        if(!RevisionNotifications.isEnabled(applicationContext)) return Result.success()
        return try {
            val today=LocalDate.now().toString()
            val pending=PlannerDatabase.get(applicationContext).dao().allRevisions()
                .count { !it.completed && it.due<=today }
            RevisionNotifications.send(applicationContext,pending)
            Result.success()
        } catch(_:Exception) { Result.retry() }
    }
}
