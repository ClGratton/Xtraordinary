package com.xteink.companion.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.xteink.companion.MainActivity
import com.xteink.companion.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** In-process control plane shared by the task-removal boundary and the transfer owner. */
object BookTransferRuntimeControl {
    private val _cancelRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val cancelRequests = _cancelRequests.asSharedFlow()

    internal fun requestCancel() {
        _cancelRequests.tryEmit(Unit)
    }
}

/** Keeps an explicitly requested book transfer alive while the activity is backgrounded. */
class BookTransferForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(ChannelId, "Book transfers", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        startForeground(
            NotificationId,
            NotificationCompat.Builder(this, ChannelId)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(getString(R.string.book_transfer_notification_title))
                .setContentText(getString(R.string.book_transfer_notification_text))
                .setContentIntent(openApp)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build(),
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Removing the app from recents is an explicit exit, not a temporary
        // background transition. It cancels durable auto-resume as well.
        BookUploadPersistence.clear(this)
        BookTransferRuntimeControl.requestCancel()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        private const val ChannelId = "book_transfers"
        private const val NotificationId = 3103

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, BookTransferForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BookTransferForegroundService::class.java))
        }
    }
}
