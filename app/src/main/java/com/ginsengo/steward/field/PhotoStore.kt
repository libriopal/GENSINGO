package com.ginsengo.steward.field

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Patch photos live in app-private internal storage only (PRD §7, §8.3).
 *
 * Replaces Base44's UploadPrivateFile + CreateFileSignedUrl. Nothing here touches
 * MediaStore: a patch photo in the shared gallery is a patch location handed to every app
 * on the phone with READ_MEDIA_IMAGES, and to whatever backs up that gallery.
 *
 * Records store a path RELATIVE to filesDir so the row stays valid if the app's data
 * directory moves.
 */
class PhotoStore(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    fun newPhotoFile(): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return File(dir, "patch-$stamp.jpg")
    }

    /** Relative path to persist in the Room row. */
    fun relativePathOf(file: File): String = "$DIR/${file.name}"

    fun resolve(relativePath: String?): File? {
        if (relativePath.isNullOrBlank()) return null
        // Reject anything that tries to escape filesDir.
        if (relativePath.contains("..")) return null
        val f = File(context.filesDir, relativePath)
        return if (f.exists()) f else null
    }

    /** A time-limited grant for sharing, never a file:// path. */
    fun shareUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun delete(relativePath: String?) {
        resolve(relativePath)?.delete()
    }

    companion object {
        const val DIR = "patch_photos"
    }
}
