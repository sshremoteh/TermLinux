package com.termlinux.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.documentfile.provider.DocumentFile
import com.termlinux.core.AppPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

enum class StorageAccessMode {
    DIRECT_SHARED_STORAGE,
    SAF_TREE_LIMITED,
    NOT_CONFIGURED
}

data class StorageStatus(
    val mode: StorageAccessMode,
    val hasAllFilesAccess: Boolean,
    val directPath: String?,
    val directPathReadable: Boolean,
    val directPathWritable: Boolean,
    val safTreeUri: String?,
    val safPermissionValid: Boolean,
    val summary: String
)

data class SafEntry(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val uri: Uri
)

class StorageBridge(
    private val context: Context,
    private val paths: AppPaths = AppPaths(context)
) {
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun buildManageAllFilesIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun buildOpenDocumentTreeIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
        }
    }

    fun persistSafPermission(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
    }

    fun isSafPermissionValid(uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        val target = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        val persisted = context.contentResolver.persistedUriPermissions
        val hasPerm = persisted.any { it.uri == target && it.isReadPermission }
        if (!hasPerm) return false
        val doc = DocumentFile.fromTreeUri(context, target)
        return doc != null && doc.exists() && doc.canRead()
    }

    fun inspectStatus(safTreeUri: String?): StorageStatus {
        val allFiles = hasAllFilesAccess()
        val sharedRoot = Environment.getExternalStorageDirectory()
        val directPath = sharedRoot?.absolutePath
        val readable = allFiles && sharedRoot != null && sharedRoot.exists() && sharedRoot.canRead()
        val writable = allFiles && sharedRoot != null && sharedRoot.exists() && sharedRoot.canWrite()

        if (allFiles && readable) {
            return StorageStatus(
                mode = StorageAccessMode.DIRECT_SHARED_STORAGE,
                hasAllFilesAccess = true,
                directPath = directPath,
                directPathReadable = true,
                directPathWritable = writable,
                safTreeUri = safTreeUri,
                safPermissionValid = isSafPermissionValid(safTreeUri),
                summary = "Mode A (Direct): $directPath bind-mounted to /sdcard"
            )
        }

        val safValid = isSafPermissionValid(safTreeUri)
        if (safValid) {
            return StorageStatus(
                mode = StorageAccessMode.SAF_TREE_LIMITED,
                hasAllFilesAccess = allFiles,
                directPath = directPath,
                directPathReadable = false,
                directPathWritable = false,
                safTreeUri = safTreeUri,
                safPermissionValid = true,
                summary = "Mode B (SAF Bridge): Limited access to user-selected folder via tlfiles (no direct POSIX /sdcard mount)"
            )
        }

        return StorageStatus(
            mode = StorageAccessMode.NOT_CONFIGURED,
            hasAllFilesAccess = allFiles,
            directPath = directPath,
            directPathReadable = false,
            directPathWritable = false,
            safTreeUri = safTreeUri,
            safPermissionValid = false,
            summary = "Not configured: Grant All Files Access for /sdcard or select a folder via SAF."
        )
    }

    /**
     * Returns PRoot bind mounts for storage and updates the SAF status metadata inside the bridge dir.
     */
    suspend fun preparePRootBindings(safTreeUri: String?): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            val status = inspectStatus(safTreeUri)
            val bridgeDir = paths.safBridgeDir()
            val statusFile = File(bridgeDir, "status.txt")
            val treeFile = File(bridgeDir, "tree.txt")

            val binds = mutableListOf<Pair<String, String>>()
            binds.add(bridgeDir.absolutePath to "/.termlinux_saf")

            when (status.mode) {
                StorageAccessMode.DIRECT_SHARED_STORAGE -> {
                    val hostDir = status.directPath ?: "/storage/emulated/0"
                    binds.add(hostDir to "/sdcard")
                    binds.add(hostDir to "/storage/emulated/0")
                    statusFile.writeText(
                        "mode=DIRECT_SHARED_STORAGE\n" +
                            "host_path=$hostDir\n" +
                            "guest_path=/sdcard\n" +
                            "writable=${status.directPathWritable}\n"
                    )
                    treeFile.delete()
                }

                StorageAccessMode.SAF_TREE_LIMITED -> {
                    statusFile.writeText(
                        "mode=SAF_TREE_LIMITED\n" +
                            "saf_uri=${status.safTreeUri}\n" +
                            "note=SAF content:// URIs cannot be POSIX bind-mounted directly in PRoot. Use TermLinux SAF manager or tlfiles list.\n"
                    )
                    val entries = safTreeUri?.let { listSafTree(it) }.orEmpty()
                    treeFile.writeText(
                        entries.joinToString("\n") { entry ->
                            val type = if (entry.isDirectory) "dir" else "file"
                            "$type\t${entry.sizeBytes}\t${entry.name}"
                        } + "\n"
                    )
                }

                StorageAccessMode.NOT_CONFIGURED -> {
                    statusFile.writeText(
                        "mode=NOT_CONFIGURED\n" +
                            "note=Open TermLinux Settings -> Storage (/sdcard) to configure access.\n"
                    )
                    treeFile.delete()
                }
            }
            binds
        }

    suspend fun listSafTree(uriString: String): List<SafEntry> = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        val root = DocumentFile.fromTreeUri(context, uri) ?: return@withContext emptyList()
        if (!root.exists() || !root.canRead()) return@withContext emptyList()
        root.listFiles().mapNotNull { doc ->
            val name = doc.name ?: return@mapNotNull null
            SafEntry(
                name = name,
                isDirectory = doc.isDirectory,
                sizeBytes = doc.length(),
                uri = doc.uri
            )
        }.sortedWith(compareBy<SafEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    suspend fun writeSafFile(
        treeUriString: String,
        fileName: String,
        mimeType: String = "text/plain",
        bytes: ByteArray
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val treeUri = Uri.parse(treeUriString)
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: throw IOException("Invalid SAF tree URI")
            val existing = root.findFile(fileName)
            val target = existing ?: root.createFile(mimeType, fileName)
                ?: throw IOException("Failed to create SAF file: $fileName")
            context.contentResolver.openOutputStream(target.uri, "wt")?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: throw IOException("Cannot open SAF output stream for $fileName")
            target.uri
        }
    }

    suspend fun readSafFile(fileUri: Uri): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver: ContentResolver = context.contentResolver
            resolver.openInputStream(fileUri)?.use { it.readBytes() }
                ?: throw IOException("Cannot open SAF input stream")
        }
    }

    suspend fun deleteSafEntry(fileUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val doc = DocumentFile.fromSingleUri(context, fileUri) ?: return@withContext false
        doc.delete()
    }
}
