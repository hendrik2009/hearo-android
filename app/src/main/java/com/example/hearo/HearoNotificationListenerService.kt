package com.example.hearo

import android.service.notification.NotificationListenerService

/**
 * Notification listener used only to satisfy [android.media.session.MediaSessionManager.getActiveSessions]
 * so we can obtain a [android.media.session.MediaController] for Spotify and send play/stop/skip
 * commands. The user must enable "Notification access" for this app in system settings.
 */
class HearoNotificationListenerService : NotificationListenerService()
