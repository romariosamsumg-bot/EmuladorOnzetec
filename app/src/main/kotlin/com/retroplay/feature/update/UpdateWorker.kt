package com.retroplay.feature.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.retroplay.MainActivity
import com.retroplay.R
import com.retroplay.domain.settings.AppSettings
import com.retroplay.domain.settings.ScopeRef
import com.retroplay.domain.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Uma vez por dia: procura versão nova e avisa com uma notificação (uma vez por versão). */
@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val updates: UpdateManager,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val info = updates.check() ?: return Result.success()
        val notified = settings.current().value(AppSettings.UPDATE_NOTIFIED)
        if (notified == info.versionName) return Result.success()
        if (notify(applicationContext, info)) settings.set(AppSettings.UPDATE_NOTIFIED, ScopeRef.Global, info.versionName)
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "updates"
        private const val NOTIFICATION_ID = 4101

        /** Agenda a checagem diária (só com internet). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UpdateManager.UNIQUE_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun notify(context: Context, info: UpdateInfo): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return false
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.notif_channel_updates), NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_GO_HOME, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(com.retroplay.core.ui.R.drawable.ic_nav_transfer)
                .setContentTitle(context.getString(R.string.notif_update_title, info.versionName))
                .setContentText(context.getString(R.string.notif_update_text))
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    (info.notes.take(4).joinToString("\n") { "• $it" } + "\n" + context.getString(R.string.notif_update_text)).trim(),
                ))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            return runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }.isSuccess
        }
    }
}
