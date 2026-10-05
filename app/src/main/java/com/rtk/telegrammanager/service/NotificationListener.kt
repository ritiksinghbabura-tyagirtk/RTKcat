package com.rtk.telegrammanager.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "RTKNotification"

        @Volatile
        private var instance: NotificationListener? = null

        fun isConnected(): Boolean {
            return instance != null
        }

        fun getCurrentNotifications(): List<String> {
            val service = instance ?: return emptyList()

            return runCatching {
                service.activeNotifications
                    ?.mapNotNull { sbn ->
                        service.formatNotification(sbn)
                    }
                    ?: emptyList()
            }.getOrElse {
                Log.e(TAG, "Failed to read active notifications", it)
                emptyList()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.d(TAG, "RTK Notification Listener created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()

        instance = this

        Log.d(TAG, "RTK Notification Listener connected")
    }

    override fun onListenerDisconnected() {
        Log.d(TAG, "RTK Notification Listener disconnected")
        instance = null

        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification?
    ) {
        /*
         * IMPORTANT:
         *
         * Notifications are NOT automatically sent to Telegram.
         *
         * They are only available through:
         *
         * /notification_status
         */
    }

    private fun formatNotification(
        sbn: StatusBarNotification
    ): String? {

        if (sbn.packageName == applicationContext.packageName) {
            return null
        }

        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null

        val title = extras
            .getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            ?.trim()
            .orEmpty()

        val text = extras
            .getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()
            ?.trim()
            .orEmpty()

        val bigText = extras
            .getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?.toString()
            ?.trim()
            .orEmpty()

        val subText = extras
            .getCharSequence(Notification.EXTRA_SUB_TEXT)
            ?.toString()
            ?.trim()
            .orEmpty()

        if (
            title.isBlank() &&
            text.isBlank() &&
            bigText.isBlank() &&
            subText.isBlank()
        ) {
            return null
        }

        val appLabel = runCatching {
            val info = packageManager.getApplicationInfo(
                sbn.packageName,
                0
            )

            packageManager
                .getApplicationLabel(info)
                .toString()
        }.getOrDefault(sbn.packageName)

        return buildString {

            appendLine("📱 $appLabel")
            appendLine("📦 ${sbn.packageName}")

            if (title.isNotBlank()) {
                appendLine("👤 $title")
            }

            if (subText.isNotBlank()) {
                appendLine("ℹ️ $subText")
            }

            val mainText =
                if (bigText.isNotBlank()) bigText else text

            if (mainText.isNotBlank()) {
                appendLine()
                appendLine(mainText)
            }
        }.trim()
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?
    ) {
        Log.d(
            TAG,
            "Notification removed: ${sbn?.packageName}"
        )
    }

    override fun onDestroy() {
        instance = null

        Log.d(
            TAG,
            "RTK Notification Listener destroyed"
        )

        super.onDestroy()
    }
}
