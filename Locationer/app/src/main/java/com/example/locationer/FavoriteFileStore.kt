package com.example.locationer

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/** Stores the uninstall-resistant favorites backup in the public Download directory. */
class FavoriteFileStore(private val application: Application) {

    companion object {
        const val AUTO_FILE_NAME = "Locationer-favorites.json"
        const val FILE_NAME = "favorites.json"

        fun tryReadLegacyFile(app: Application): String? {
            val file = File(app.getExternalFilesDir(null), FILE_NAME)
            if (!file.exists() || file.length() == 0L) return null
            return runCatching { file.readText(StandardCharsets.UTF_8) }.getOrNull()
        }

        fun createSaveIntent() = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(android.content.Intent.EXTRA_TITLE, FILE_NAME)
        }
    }

    fun saveToPublic(json: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveViaMediaStore(json)
        else writeDirectly(json)
    } catch (_: Exception) {
        false
    }

    fun readFromFile(): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) readViaMediaStore()
        else readDirectly()
    } catch (_: Exception) {
        null
    }

    fun deleteFromFile(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) deleteViaMediaStore()
        else deleteDirectly()
    } catch (_: Exception) {
        false
    }

    fun deleteLegacyBackups(): Int = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = application.contentResolver
            findBackupUris(FILE_NAME).count { uri ->
                runCatching { resolver.delete(uri, null, null) > 0 }.getOrDefault(false)
            }
        } else {
            if (!hasLegacyStoragePermission()) 0
            else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.listFiles { file ->
                    file.name == FILE_NAME || file.name.matches(Regex("favorites \\(\\d+\\)\\.json"))
                }?.count { it.delete() } ?: 0
            }
        }
    } catch (_: Exception) {
        0
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveViaMediaStore(json: String): Boolean {
        val resolver = application.contentResolver
        val existingUris = findBackupUris(AUTO_FILE_NAME)

        // Rows left by an earlier installation may be visible but not editable. Try every row.
        existingUris.forEach { uri ->
            if (writeToUri(uri, json)) {
                existingUris.filterNot { it == uri }.forEach { duplicate ->
                    runCatching { resolver.delete(duplicate, null, null) }
                }
                return true
            }
        }

        val uri = resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, AUTO_FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            },
        ) ?: return false

        return try {
            if (!writeToUri(uri, json)) {
                runCatching { resolver.delete(uri, null, null) }
                return false
            }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null,
            )
            true
        } catch (_: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeToUri(uri: android.net.Uri, json: String): Boolean = runCatching {
        val wrote = application.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(json.toByteArray(StandardCharsets.UTF_8))
            true
        } ?: false
        if (wrote) {
            application.contentResolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000L)
                },
                null,
                null,
            )
        }
        wrote
    }.getOrDefault(false)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun readViaMediaStore(): String? {
        val resolver = application.contentResolver
        val candidates = findBackupUris(AUTO_FILE_NAME) + findBackupUris(FILE_NAME)
        return candidates.firstNotNullOfOrNull { uri ->
            runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(StandardCharsets.UTF_8)
                }
            }.getOrNull()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteViaMediaStore(): Boolean {
        val resolver = application.contentResolver
        val candidates = findBackupUris(AUTO_FILE_NAME) + findBackupUris(FILE_NAME)
        return candidates.all { uri ->
            runCatching { resolver.delete(uri, null, null) >= 0 }.getOrDefault(false)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun findBackupUris(baseName: String): List<android.net.Uri> {
        val extensionIndex = baseName.lastIndexOf('.')
        val namePrefix = if (extensionIndex > 0) baseName.substring(0, extensionIndex) else baseName
        val cursor = runCatching {
            application.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "(${MediaStore.Downloads.DISPLAY_NAME} = ? OR ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?) AND " +
                    "(${MediaStore.Downloads.RELATIVE_PATH} = ? OR " +
                    "${MediaStore.Downloads.RELATIVE_PATH} = ?)",
                arrayOf(
                    baseName,
                    "$namePrefix (%).json",
                    Environment.DIRECTORY_DOWNLOADS,
                    "${Environment.DIRECTORY_DOWNLOADS}/",
                ),
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )
        }.getOrNull() ?: return emptyList()

        return cursor.use { rows ->
            buildList {
                val idColumn = rows.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                while (rows.moveToNext()) {
                    add(
                        ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            rows.getLong(idColumn),
                        )
                    )
                }
            }
        }
    }

    private fun writeDirectly(json: String): Boolean {
        if (!hasLegacyStoragePermission()) return false
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists() && !dir.mkdirs()) return false
        FileOutputStream(File(dir, AUTO_FILE_NAME)).use { output ->
            output.write(json.toByteArray(StandardCharsets.UTF_8))
        }
        return true
    }

    private fun readDirectly(): String? {
        if (!hasLegacyStoragePermission()) return null
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return listOf(AUTO_FILE_NAME, FILE_NAME).firstNotNullOfOrNull { name ->
            val file = File(dir, name)
            if (!file.exists() || file.length() == 0L) null
            else runCatching { file.readText(StandardCharsets.UTF_8) }.getOrNull()
        }
    }

    private fun deleteDirectly(): Boolean {
        if (!hasLegacyStoragePermission()) return false
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return listOf(AUTO_FILE_NAME, FILE_NAME).all { name ->
            val file = File(dir, name)
            !file.exists() || file.delete()
        }
    }

    private fun hasLegacyStoragePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            application,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
}
