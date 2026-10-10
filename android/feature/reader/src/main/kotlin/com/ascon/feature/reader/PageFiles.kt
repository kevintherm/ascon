package com.ascon.feature.reader

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A page image's bytes and type, as the site sent them. */
internal class PageFile(val bytes: ByteArray, val mimeType: String) {
    val extension: String get() = mimeType.substringAfter('/').substringBefore(';').replace("jpeg", "jpg")
}

/** Saving a page to the gallery needs no permission from Android 10. Before that only sharing is offered. */
internal val canSavePages: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

/** Saves [file] to Pictures/Ascon. False when the gallery refuses it. */
@RequiresApi(Build.VERSION_CODES.Q)
internal suspend fun savePage(context: Context, file: PageFile, name: String): Boolean = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$name.${file.extension}")
        put(MediaStore.Images.Media.MIME_TYPE, file.mimeType)
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Ascon")
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
    runCatching { resolver.openOutputStream(uri)?.use { it.write(file.bytes) } }.getOrNull() != null ||
        run {
            resolver.delete(uri, null, null)
            false
        }
}

/** Opens the share sheet for [file], kept in the cache until the next share replaces it. */
internal suspend fun sharePage(context: Context, file: PageFile, name: String) {
    val uri = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, SHARED_FOLDER).apply {
            deleteRecursively()
            mkdirs()
        }
        val copy = File(folder, "$name.${file.extension}").apply { writeBytes(file.bytes) }
        FileProvider.getUriForFile(context, "${context.packageName}.pages", copy)
    }
    val send = Intent(Intent.ACTION_SEND)
        .setType(file.mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Matches res/xml/shared_pages.xml. */
private const val SHARED_FOLDER = "shared-pages"
