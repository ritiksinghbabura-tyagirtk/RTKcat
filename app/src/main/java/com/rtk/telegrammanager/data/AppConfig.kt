package com.rtk.telegrammanager.data

import android.content.Context
import com.rtk.telegrammanager.security.SecureStorage

class AppConfig(context: Context) {

    private val storage =
        SecureStorage(
            context.applicationContext
        )

    fun getToken(): String? =
        storage.getBotToken()

    fun getBot2Token(): String? =
        storage.getBot2Token()

    fun setBot2Token(token: String) =
        storage.saveBot2Token(token)

    fun getBot2ChatId(): String? =
        storage.getBot2ChatId()

    fun setBot2ChatId(chatId: String) =
        storage.saveBot2ChatId(chatId)

    fun getBot2UpdateOffset(): Long =
        storage.getBot2UpdateOffset()

    fun setBot2UpdateOffset(offset: Long) =
        storage.saveBot2UpdateOffset(offset)

    fun getChatId(): String? =
        storage.getChatId()

    fun getTeacherName(): String? =
        storage.getTeacherName()

    fun isSetupComplete(): Boolean =
        storage.isSetupComplete()

    fun isBotEnabled(): Boolean =
        storage.isBotEnabled()

    fun setBotEnabled(enabled: Boolean) =
        storage.saveBotEnabled(enabled)

    fun isAutomaticCallAlertsEnabled(): Boolean =
        storage.isAutomaticCallAlertsEnabled()

    fun setAutomaticCallAlertsEnabled(enabled: Boolean) =
        storage.saveAutomaticCallAlerts(enabled)

    fun getUpdateOffset(): Long =
        storage.getUpdateOffset()

    fun setUpdateOffset(offset: Long) =
        storage.saveUpdateOffset(offset)

    fun getLastProcessedCallId(): Long =
        storage.getLastProcessedCallId()

    fun setLastProcessedCallId(id: Long) =
        storage.saveLastProcessedCallId(id)

    fun isRemoteGalleryBackupEnabled(): Boolean =
        storage.isRemoteGalleryBackupEnabled()

    fun setRemoteGalleryBackupEnabled(enabled: Boolean) =
        storage.saveRemoteGalleryBackup(enabled)

    fun getGalleryBackupLimit(): Int =
        storage.getGalleryBackupLimit()

    fun setGalleryBackupLimit(limit: Int) =
        storage.saveGalleryBackupLimit(limit)
}
