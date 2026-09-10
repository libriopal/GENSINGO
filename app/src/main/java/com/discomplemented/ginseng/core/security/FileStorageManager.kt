package com.discomplemented.ginseng.core.security

import android.content.Context
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages secure file storage within the application's internal scoped storage.
 * Ensures that sensitive files like landowner permission scans are isolated and protected.
 */
@Singleton
class FileStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Saves a byte array to a secure file in the app'filesDir.
     * @param fileName The name of the file to create.
     * @param data The content to write.
     * @return The File object representing the saved content.
     */
    suspend fun saveFile(fileName: String, data: ByteArray): File = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "permissions/$fileName")
        file.parentFile?.mkdirs()

        FileOutputStream(file).use { output ->
            output.write(data)
        }
        file
    }

    /**
     * Retrieves a File object for a given filename.
     */
    fun getFile(fileName: String): File? {
        val file = File(context.filesDir, "permissions/$fileName")
        return if (file.exists()) file else null
    }

    /**
     * Deletes a secure file.
     */
    fun deleteFile(fileName: String): Boolean {
        val file = File(context.filesDir, "permissions/$fileName")
        return if (file.exists()) file.delete() else false
    }
}
