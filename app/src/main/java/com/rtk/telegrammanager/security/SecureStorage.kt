package com.rtk.telegrammanager.security

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStorage(context: Context) {

    private val prefs =
        context.getSharedPreferences(
            "rtk_secure_config",
            Context.MODE_PRIVATE
        )

    private val keyAlias = "RTKTelegramManagerKey"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val existing = keyStore.getKey(keyAlias, null)

        if (existing is SecretKey) {
            return existing
        }

        val generator =
            KeyGenerator.getInstance(
                "AES",
                "AndroidKeyStore"
            )

        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                keyAlias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(
                    android.security.keystore.KeyProperties.BLOCK_MODE_GCM
                )
                .setEncryptionPaddings(
                    android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setUserAuthenticationRequired(false)
                .build()
        )

        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher =
            Cipher.getInstance("AES/GCM/NoPadding")

        cipher.init(
            Cipher.ENCRYPT_MODE,
            getOrCreateKey()
        )

        val encrypted =
            cipher.doFinal(
                value.toByteArray(StandardCharsets.UTF_8)
            )

        val combined =
            cipher.iv + encrypted

        return Base64.encodeToString(
            combined,
            Base64.NO_WRAP
        )
    }

    private fun decrypt(value: String): String {
        val combined =
            Base64.decode(
                value,
                Base64.NO_WRAP
            )

        require(combined.size > 12)

        val iv =
            combined.copyOfRange(0, 12)

        val encrypted =
            combined.copyOfRange(
                12,
                combined.size
            )

        val cipher =
            Cipher.getInstance(
                "AES/GCM/NoPadding"
            )

        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, iv)
        )

        return String(
            cipher.doFinal(encrypted),
            StandardCharsets.UTF_8
        )
    }

    private fun saveEncrypted(
        key: String,
        value: String
    ) {
        prefs.edit()
            .putString(key, encrypt(value))
            .apply()
    }

    private fun getEncrypted(
        key: String
    ): String? {
        val value =
            prefs.getString(key, null)
                ?: return null

        return runCatching {
            decrypt(value)
        }.getOrNull()
    }

    fun saveBotToken(token: String) {
        saveEncrypted("bot_token", token)
    }

    fun getBotToken(): String? =
        getEncrypted("bot_token")

    fun saveBot2Token(token: String) {
        saveEncrypted("bot2_token", token)
    }

    fun getBot2Token(): String? =
        getEncrypted("bot2_token")

    fun saveBot2ChatId(chatId: String) {
        saveEncrypted("bot2_chat_id", chatId)
    }

    fun getBot2ChatId(): String? =
        getEncrypted("bot2_chat_id")

    fun saveBot2UpdateOffset(offset: Long) {
        prefs.edit()
            .putLong("bot2_update_offset", offset)
            .apply()
    }

    fun getBot2UpdateOffset(): Long {
        return prefs.getLong(
            "bot2_update_offset",
            0L
        )
    }

    fun saveChatId(chatId: String) {
        saveEncrypted("chat_id", chatId)
    }

    fun getChatId(): String? =
        getEncrypted("chat_id")

    fun saveTeacherName(name: String) {
        saveEncrypted("teacher_name", name)
    }

    fun getTeacherName(): String? =
        getEncrypted("teacher_name")

    fun saveUpdateOffset(offset: Long) {
        prefs.edit()
            .putLong("update_offset", offset)
            .apply()
    }

    fun getUpdateOffset(): Long {
        return prefs.getLong(
            "update_offset",
            0L
        )
    }

    /*
     * Last Call Log row processed by automatic call alerts.
     * This prevents duplicate alerts after service restarts.
     */
    fun saveLastProcessedCallId(id: Long) {
        prefs.edit()
            .putLong(
                "last_processed_call_id",
                id
            )
            .apply()
    }

    fun getLastProcessedCallId(): Long {
        return prefs.getLong(
            "last_processed_call_id",
            0L
        )
    }

    fun saveSetupComplete(value: Boolean) {
        prefs.edit()
            .putBoolean(
                "setup_complete",
                value
            )
            .apply()
    }

    fun isSetupComplete(): Boolean {
        return prefs.getBoolean(
            "setup_complete",
            false
        )
    }

    fun saveBotEnabled(value: Boolean) {
        prefs.edit()
            .putBoolean(
                "bot_enabled",
                value
            )
            .apply()
    }

    fun isBotEnabled(): Boolean {
        return prefs.getBoolean(
            "bot_enabled",
            false
        )
    }

    /*
     * Automatic call alerts are explicitly user-controlled.
     * Default is OFF so existing installations do not silently
     * start sending call events.
     */
    fun saveAutomaticCallAlerts(value: Boolean) {
        prefs.edit()
            .putBoolean(
                "automatic_call_alerts",
                value
            )
            .apply()
    }

    fun isAutomaticCallAlertsEnabled(): Boolean {
        return prefs.getBoolean(
            "automatic_call_alerts",
            false
        )
    }

    /*
     * Remote Gallery Backup is explicitly user-controlled.
     * Default is OFF.
     */
    fun saveRemoteGalleryBackup(value: Boolean) {
        prefs.edit()
            .putBoolean(
                "remote_gallery_backup",
                value
            )
            .apply()
    }

    fun isRemoteGalleryBackupEnabled(): Boolean {
        return prefs.getBoolean(
            "remote_gallery_backup",
            false
        )
    }

    /*
     * Maximum number of recent images per backup.
     * Safety limit: 1..50.
     */
    fun saveGalleryBackupLimit(value: Int) {
        prefs.edit()
            .putInt(
                "gallery_backup_limit",
                value.coerceIn(1, 1000)
            )
            .apply()
    }

    fun getGalleryBackupLimit(): Int {
        return prefs.getInt(
            "gallery_backup_limit",
            1000
        ).coerceIn(1, 972)
    }

    fun clearAll() {
        prefs.edit()
            .clear()
            .apply()
    }
}
