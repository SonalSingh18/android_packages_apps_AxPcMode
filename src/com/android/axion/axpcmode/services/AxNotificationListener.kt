package com.android.axion.axpcmode.services

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSession
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AxNotificationListener : NotificationListenerService() {

    var mediaRepository: MediaRepository? = null
    var scope: CoroutineScope? = null

    private fun updateMediaTokens() {
        scope?.launch {
            try {
                val tokens =
                    activeNotifications?.mapNotNull { sbn ->
                        sbn.notification.extras.getParcelable(
                            Notification.EXTRA_MEDIA_SESSION,
                            MediaSession.Token::class.java,
                        )
                    } ?: emptyList()
                mediaRepository?.onMediaTokensUpdated(tokens)
            } catch (e: Exception) {
                Log.e("AxNotificationListener", "Failed to update media tokens", e)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationRepository.listener = this
        try {
            activeNotifications?.forEach { NotificationRepository.addOrUpdate(it) }
            updateMediaTokens()
        } catch (e: Exception) {
            Log.e("AxNotificationListener", "Failed to update media tokens", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NotificationRepository.addOrUpdate(sbn)
        updateMediaTokens()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationRepository.remove(sbn)
        updateMediaTokens()
    }

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context, AxNotificationListener::class.java)
    }
}
