package com.rtk.telegrammanager.telegram

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TelegramApi {

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(
                15,
                TimeUnit.SECONDS
            )
            .readTimeout(
                35,
                TimeUnit.SECONDS
            )
            .writeTimeout(
                30,
                TimeUnit.SECONDS
            )
            .retryOnConnectionFailure(true)
            .build()

    suspend fun getMe(
        token: String
    ): Result<TelegramBotInfo> =
        withContext(Dispatchers.IO) {

            runCatching {

                val request =
                    Request.Builder()
                        .url(
                            "https://api.telegram.org/bot" +
                                "${token.trim()}/getMe"
                        )
                        .get()
                        .build()

                client.newCall(
                    request
                ).execute().use { response ->

                    val body =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (!response.isSuccessful) {
                        error(
                            "Telegram HTTP ${response.code}"
                        )
                    }

                    val root =
                        JSONObject(body)

                    if (!root.optBoolean("ok")) {
                        error(
                            root.optString(
                                "description",
                                "Telegram API error"
                            )
                        )
                    }

                    val result =
                        root.getJSONObject("result")

                    TelegramBotInfo(
                        username =
                            result.optString(
                                "username",
                                "unknown"
                            ),
                        firstName =
                            result.optString(
                                "first_name",
                                "Bot"
                            )
                    )
                }
            }
        }

    suspend fun sendMessage(
        token: String,
        chatId: String,
        text: String
    ): Result<Unit> =
        withContext(Dispatchers.IO) {

            runCatching {

                val body =
                    FormBody.Builder()
                        .add(
                            "chat_id",
                            chatId
                        )
                        .add(
                            "text",
                            text
                        )
                        .build()

                val request =
                    Request.Builder()
                        .url(
                            "https://api.telegram.org/bot" +
                                "${token.trim()}/sendMessage"
                        )
                        .post(body)
                        .build()

                client.newCall(
                    request
                ).execute().use { response ->

                    val responseBody =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (!response.isSuccessful) {
                        error(
                            "Telegram HTTP ${response.code}"
                        )
                    }

                    val root =
                        JSONObject(responseBody)

                    if (!root.optBoolean("ok")) {
                        error(
                            root.optString(
                                "description",
                                "Telegram sendMessage failed"
                            )
                        )
                    }
                }
            }
        }

    suspend fun sendPhoto(
        token: String,
        chatId: String,
        photoUri: android.net.Uri,
        contentResolver: android.content.ContentResolver,
        caption: String? = null
    ): Result<Unit> =
        withContext(Dispatchers.IO) {

            runCatching {

                val inputStream =
                    contentResolver.openInputStream(photoUri)
                        ?: error("Unable to open gallery image")

                inputStream.use { input ->

                    val fileName =
                        photoUri.lastPathSegment
                            ?.substringAfterLast("/")
                            ?.ifBlank { "photo.jpg" }
                            ?: "photo.jpg"

                    val mediaType =
                        contentResolver
                            .getType(photoUri)
                            ?.let {
                                it.toMediaType()
                            }
                            ?: "image/jpeg".toMediaType()

                    val photoBody =
                        object : okhttp3.RequestBody() {

                            override fun contentType() =
                                mediaType

                            override fun writeTo(
                                sink: okio.BufferedSink
                            ) {
                                input.copyTo(
                                    sink.outputStream()
                                )
                            }
                        }

                    val multipart =
                        okhttp3.MultipartBody.Builder()
                            .setType(
                                okhttp3.MultipartBody.FORM
                            )
                            .addFormDataPart(
                                "chat_id",
                                chatId
                            )
                            .addFormDataPart(
                                "photo",
                                fileName,
                                photoBody
                            )
                            .apply {

                                if (
                                    !caption.isNullOrBlank()
                                ) {
                                    addFormDataPart(
                                        "caption",
                                        caption
                                    )
                                }
                            }
                            .build()

                    val request =
                        okhttp3.Request.Builder()
                            .url(
                                "https://api.telegram.org/bot" +
                                    "${token.trim()}/sendPhoto"
                            )
                            .post(multipart)
                            .build()

                    client.newCall(
                        request
                    ).execute().use { response ->

                        val responseBody =
                            response.body
                                ?.string()
                                .orEmpty()

                        if (!response.isSuccessful) {
                            error(
                                "Telegram HTTP ${response.code}"
                            )
                        }

                        val root =
                            JSONObject(responseBody)

                        if (!root.optBoolean("ok")) {
                            error(
                                root.optString(
                                    "description",
                                    "Telegram sendPhoto failed"
                                )
                            )
                        }
                    }
                }
            }
        }

    suspend fun getUpdates(
        token: String,
        offset: Long
    ): Result<List<TelegramUpdate>> =
        withContext(Dispatchers.IO) {

            runCatching {

                val url =
                    "https://api.telegram.org/bot" +
                        "${token.trim()}/getUpdates" +
                        "?timeout=20" +
                        "&offset=$offset" +
                        "&allowed_updates=%5B%22message%22%5D"

                val request =
                    Request.Builder()
                        .url(url)
                        .get()
                        .build()

                client.newCall(
                    request
                ).execute().use { response ->

                    val body =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (!response.isSuccessful) {
                        error(
                            "Telegram HTTP ${response.code}"
                        )
                    }

                    val root =
                        JSONObject(body)

                    if (!root.optBoolean("ok")) {
                        error(
                            root.optString(
                                "description",
                                "Telegram getUpdates failed"
                            )
                        )
                    }

                    val result =
                        root.optJSONArray("result")
                            ?: return@use emptyList()

                    buildList {

                        for (
                            i in 0 until result.length()
                        ) {

                            val update =
                                result.optJSONObject(i)
                                    ?: continue

                            val updateId =
                                update.optLong(
                                    "update_id",
                                    -1L
                                )

                            if (updateId < 0) {
                                continue
                            }

                            val message =
                                update.optJSONObject(
                                    "message"
                                )

                            val chat =
                                message?.optJSONObject(
                                    "chat"
                                )

                            val chatId =
                                chat?.optLong(
                                    "id",
                                    Long.MIN_VALUE
                                )

                            val text =
                                if (
                                    message != null &&
                                    message.has("text")
                                ) {
                                    message.optString(
                                        "text"
                                    )
                                } else {
                                    null
                                }

                            val parsedMessage =
                                if (
                                    chatId != null &&
                                    chatId != Long.MIN_VALUE &&
                                    !text.isNullOrBlank()
                                ) {

                                    TelegramMessage(
                                        chatId =
                                            chatId.toString(),
                                        text = text
                                    )

                                } else {
                                    null
                                }

                            add(
                                TelegramUpdate(
                                    updateId =
                                        updateId,
                                    message =
                                        parsedMessage
                                )
                            )
                        }
                    }
                }
            }
        }
}
