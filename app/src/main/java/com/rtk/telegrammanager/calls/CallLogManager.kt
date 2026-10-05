package com.rtk.telegrammanager.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CallEntry(
    val name: String,
    val number: String,
    val type: String,
    val date: String,
    val duration: String,
    val status: String
)

class CallLogManager(
    private val context: Context
) {

    fun getRecentCalls(
        limit: Int = 300
    ): Result<List<CallEntry>> {

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALL_LOG
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.failure(
                SecurityException(
                    "READ_CALL_LOG permission required"
                )
            )
        }

        return runCatching {

            val result = mutableListOf<CallEntry>()

            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            )

            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->

                val numberIndex =
                    cursor.getColumnIndex(
                        CallLog.Calls.NUMBER
                    )

                val typeIndex =
                    cursor.getColumnIndex(
                        CallLog.Calls.TYPE
                    )

                val dateIndex =
                    cursor.getColumnIndex(
                        CallLog.Calls.DATE
                    )

                val durationIndex =
                    cursor.getColumnIndex(
                        CallLog.Calls.DURATION
                    )

                while (
                    cursor.moveToNext() &&
                    result.size < limit
                ) {

                    val number =
                        if (numberIndex >= 0)
                            cursor.getString(numberIndex)
                        else
                            ""

                    val typeValue =
                        if (typeIndex >= 0)
                            cursor.getInt(typeIndex)
                        else
                            CallLog.Calls.MISSED_TYPE

                    val date =
                        if (dateIndex >= 0)
                            cursor.getLong(dateIndex)
                        else
                            0L

                    val duration =
                        if (durationIndex >= 0)
                            cursor.getLong(durationIndex)
                        else
                            0L

                    val type =
                        when (typeValue) {
                            CallLog.Calls.INCOMING_TYPE ->
                                "Incoming"

                            CallLog.Calls.OUTGOING_TYPE ->
                                "Outgoing"

                            CallLog.Calls.MISSED_TYPE ->
                                "Missed"

                            CallLog.Calls.REJECTED_TYPE ->
                                "Rejected"

                            CallLog.Calls.BLOCKED_TYPE ->
                                "Blocked"

                            else ->
                                "Other"
                        }

                    val status =
                        when {
                            typeValue ==
                                CallLog.Calls.MISSED_TYPE ->
                                "Missed"

                            typeValue ==
                                CallLog.Calls.REJECTED_TYPE ->
                                "Rejected"

                            duration > 0 ->
                                "Answered"

                            else ->
                                "Not answered"
                        }

                    val formattedDate =
                        if (date > 0) {
                            SimpleDateFormat(
                                "dd-MM-yyyy HH:mm",
                                Locale.getDefault()
                            ).format(Date(date))
                        } else {
                            "Unknown"
                        }

                    val formattedDuration =
                        formatDuration(duration)

                    val name =
                        lookupContactName(number)

                    result += CallEntry(
                        name =
                            name ?: "Unknown",
                        number = number,
                        type = type,
                        date = formattedDate,
                        duration = formattedDuration,
                        status = status
                    )
                }
            }

            result
        }
    }

    private fun lookupContactName(
        number: String
    ): String? {

        if (
            number.isBlank()
        ) {
            return null
        }

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        return runCatching {

            val uri =
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI
                    .buildUpon()
                    .appendPath(number)
                    .build()

            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->

                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun formatDuration(
        seconds: Long
    ): String {

        val minutes =
            seconds / 60

        val remainingSeconds =
            seconds % 60

        return String.format(
            Locale.getDefault(),
            "%02d:%02d",
            minutes,
            remainingSeconds
        )
    }

    /*
     * Returns the newest Call Log row ID.
     *
     * Used by the foreground-service call observer to establish
     * a baseline and detect genuinely newer call records.
     */
    fun getLatestCallId(): Long? {

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALL_LOG
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        return runCatching {

            val projection =
                arrayOf(CallLog.Calls._ID)

            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls._ID} DESC"
            )?.use { cursor ->

                if (cursor.moveToFirst()) {
                    cursor.getLong(0)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    /*
     * Reads exactly one Call Log row by its stable _ID.
     * This avoids rescanning the entire call log for each event.
     */
    fun getCallById(
        id: Long
    ): CallEntry? {

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALL_LOG
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        return runCatching {

            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            )

            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                "${CallLog.Calls._ID} = ?",
                arrayOf(id.toString()),
                null
            )?.use { cursor ->

                if (!cursor.moveToFirst()) {
                    return@use null
                }

                val number =
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            CallLog.Calls.NUMBER
                        )
                    ) ?: ""

                val typeValue =
                    cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                            CallLog.Calls.TYPE
                        )
                    )

                val date =
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            CallLog.Calls.DATE
                        )
                    )

                val duration =
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            CallLog.Calls.DURATION
                        )
                    )

                val type =
                    when (typeValue) {
                        CallLog.Calls.INCOMING_TYPE ->
                            "Incoming"

                        CallLog.Calls.OUTGOING_TYPE ->
                            "Outgoing"

                        CallLog.Calls.MISSED_TYPE ->
                            "Missed"

                        CallLog.Calls.REJECTED_TYPE ->
                            "Rejected"

                        CallLog.Calls.BLOCKED_TYPE ->
                            "Blocked"

                        else ->
                            "Other"
                    }

                val status =
                    when {
                        typeValue ==
                            CallLog.Calls.MISSED_TYPE ->
                            "Missed"

                        typeValue ==
                            CallLog.Calls.REJECTED_TYPE ->
                            "Rejected"

                        duration > 0 ->
                            "Answered"

                        else ->
                            "Not answered"
                    }

                val formattedDate =
                    if (date > 0) {
                        SimpleDateFormat(
                            "dd-MM-yyyy HH:mm",
                            Locale.getDefault()
                        ).format(Date(date))
                    } else {
                        "Unknown"
                    }

                CallEntry(
                    name =
                        lookupContactName(number)
                            ?: "Unknown",
                    number = number,
                    type = type,
                    date = formattedDate,
                    duration = formatDuration(duration),
                    status = status
                )
            }
        }.getOrNull()
    }

    fun formattedText(
        limit: Int = 300
    ): String {

        return getRecentCalls(limit)
            .fold(
                onSuccess = { calls ->

                    if (calls.isEmpty()) {
                        return "📞 CALL LOG\n\nNo call records found."
                    }

                    buildString {

                        appendLine("📞 RECENT CALLS")
                        appendLine()

                        calls.forEachIndexed { index, call ->

                            appendLine(
                                "${index + 1}. ${call.name}"
                            )

                            appendLine(
                                "📱 ${call.number}"
                            )

                            appendLine(
                                "↔ ${call.type} • ${call.status}"
                            )

                            appendLine(
                                "🕒 ${call.date}"
                            )

                            appendLine(
                                "⏱ ${call.duration}"
                            )

                            appendLine()
                        }
                    }.trim()
                },
                onFailure = {
                    "📞 CALL LOG\n\nPermission required:\nREAD_CALL_LOG"
                }
            )
    }
}
