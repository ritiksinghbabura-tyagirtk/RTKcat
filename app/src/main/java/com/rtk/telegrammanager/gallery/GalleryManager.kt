package com.rtk.telegrammanager.gallery

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

data class GalleryImage(
    val uri: Uri,
    val id: Long,
    val name: String,
    val mimeType: String?,
    val dateAdded: Long,
    val size: Long
)

class GalleryManager(
    context: Context
) {

    private val appContext =
        context.applicationContext

    fun hasPermission(): Boolean {

        val permission =
            if (Build.VERSION.SDK_INT >= 33) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

        return ContextCompat.checkSelfPermission(
            appContext,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getRecentImages(
        limit: Int = 3000
    ): List<GalleryImage> {

        if (!hasPermission()) {
            return emptyList()
        }

        val safeLimit =
            limit.coerceIn(1, 3000)

        val collection =
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val projection =
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE
            )

        return runCatching {

            appContext.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->

                val idIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Images.Media._ID
                    )

                val nameIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Images.Media.DISPLAY_NAME
                    )

                val mimeIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Images.Media.MIME_TYPE
                    )

                val dateIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Images.Media.DATE_ADDED
                    )

                val sizeIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Images.Media.SIZE
                    )

                buildList {

                    while (
                        cursor.moveToNext() &&
                        size < safeLimit
                    ) {

                        val id =
                            cursor.getLong(idIndex)

                        add(
                            GalleryImage(
                                uri =
                                    Uri.withAppendedPath(
                                        collection,
                                        id.toString()
                                    ),
                                id = id,
                                name =
                                    cursor.getString(
                                        nameIndex
                                    ).orEmpty(),
                                mimeType =
                                    cursor.getString(
                                        mimeIndex
                                    ),
                                dateAdded =
                                    cursor.getLong(
                                        dateIndex
                                    ),
                                size =
                                    cursor.getLong(
                                        sizeIndex
                                    )
                            )
                        )
                    }
                }

            } ?: emptyList()

        }.getOrElse {
            emptyList()
        }
    }
}
