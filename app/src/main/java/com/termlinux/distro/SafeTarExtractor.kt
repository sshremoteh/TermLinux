package com.termlinux.distro

import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files

object SafeTarExtractor {

    suspend fun extract(
        archiveFile: File,
        format: String,
        destDir: File,
        onEntryExtracted: ((Int) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        if (destDir.exists()) {
            destDir.deleteRecursively()
        }
        if (!destDir.mkdirs()) {
            throw IOException("Failed to create staging directory: ${destDir.absolutePath}")
        }
        val canonicalRoot = destDir.canonicalFile

        val buffered = BufferedInputStream(archiveFile.inputStream(), 64 * 1024)
        val decompressed = when (format.lowercase()) {
            "tar.gz", "tgz" -> GzipCompressorInputStream(buffered)
            "tar.xz", "txz" -> XZInputStream(buffered)
            "tar" -> buffered
            else -> throw IllegalArgumentException("Unsupported archive format: $format")
        }

        val deferredHardLinks = mutableListOf<Pair<File, String>>()
        val deferredDirModes = mutableListOf<Pair<File, Int>>()
        var count = 0

        TarArchiveInputStream(decompressed).use { tar ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = tar.nextEntry ?: break
                val cleanName = normalizeEntryPath(entry.name) ?: continue
                val targetFile = resolveSafeChild(canonicalRoot, cleanName)

                // Ensure no intermediate path component is a host symlink escaping rootfs
                ensureNoEscapingSymlinkParent(canonicalRoot, targetFile)

                when {
                    entry.isDirectory -> {
                        if (!targetFile.exists() && !targetFile.mkdirs()) {
                            throw IOException("Cannot create directory: ${targetFile.absolutePath}")
                        }
                        if (entry.mode > 0) {
                            deferredDirModes.add(targetFile to (entry.mode and 0b111_111_111))
                        }
                    }

                    entry.isSymbolicLink -> {
                        targetFile.parentFile?.mkdirs()
                        if (targetFile.exists() || Files.isSymbolicLink(targetFile.toPath())) {
                            targetFile.delete()
                        }
                        val linkTarget = entry.linkName
                        require(!linkTarget.contains('\u0000')) { "Invalid symlink target" }
                        Os.symlink(linkTarget, targetFile.absolutePath)
                    }

                    entry.isLink -> {
                        val cleanLink = normalizeEntryPath(entry.linkName)
                            ?: throw IOException("Invalid hardlink target: ${entry.linkName}")
                        resolveSafeChild(canonicalRoot, cleanLink)
                        deferredHardLinks.add(targetFile to cleanLink)
                    }

                    entry.isFile -> {
                        targetFile.parentFile?.mkdirs()
                        if (targetFile.exists() || Files.isSymbolicLink(targetFile.toPath())) {
                            targetFile.delete()
                        }
                        FileOutputStream(targetFile).use { out ->
                            val buf = ByteArray(32 * 1024)
                            while (true) {
                                val n = tar.read(buf)
                                if (n == -1) break
                                out.write(buf, 0, n)
                            }
                        }
                        if (entry.mode > 0) {
                            safeChmod(targetFile, entry.mode and 0b111_111_111)
                        }
                    }

                    else -> {
                        // Device nodes / FIFOs cannot be created without root on Android; skip safely
                    }
                }

                count++
                if (count % 100 == 0) {
                    onEntryExtracted?.invoke(count)
                }
            }
        }

        for ((linkFile, cleanTarget) in deferredHardLinks) {
            val target = resolveSafeChild(canonicalRoot, cleanTarget)
            linkFile.parentFile?.mkdirs()
            if (linkFile.exists() || Files.isSymbolicLink(linkFile.toPath())) {
                linkFile.delete()
            }
            if (target.exists()) {
                try {
                    Os.link(target.absolutePath, linkFile.absolutePath)
                } catch (_: Throwable) {
                    target.copyTo(linkFile, overwrite = true)
                }
            }
        }

        for ((dir, mode) in deferredDirModes.asReversed()) {
            safeChmod(dir, mode)
        }
        onEntryExtracted?.invoke(count)
    }

    internal fun normalizeEntryPath(rawName: String): String? {
        var name = rawName.trim()
        while (name.startsWith("./")) name = name.removePrefix("./")
        while (name.startsWith("/")) name = name.removePrefix("/")
        name = name.trimEnd('/')
        if (name.isEmpty() || name == ".") return null
        val segments = name.split('/')
        for (seg in segments) {
            if (seg == ".." || seg.contains('\u0000')) {
                throw SecurityException("Path traversal detected in tar entry: $rawName")
            }
        }
        return segments.filter { it.isNotEmpty() && it != "." }.joinToString("/")
    }

    internal fun resolveSafeChild(canonicalRoot: File, relativePath: String): File {
        val candidate = File(canonicalRoot, relativePath)
        val rootPath = canonicalRoot.path
        val candidateNormalized = candidate.toPath().normalize().toString()
        if (candidateNormalized != rootPath && !candidateNormalized.startsWith("$rootPath/")) {
            throw SecurityException("Entry escapes rootfs: $relativePath")
        }
        return candidate
    }

    private fun ensureNoEscapingSymlinkParent(canonicalRoot: File, target: File) {
        var cur: File? = target.parentFile
        while (cur != null && cur != canonicalRoot) {
            if (Files.isSymbolicLink(cur.toPath())) {
                val resolved = cur.canonicalFile
                val rootPath = canonicalRoot.path
                if (resolved.path != rootPath && !resolved.path.startsWith("$rootPath/")) {
                    throw SecurityException("Symlink parent escapes rootfs: ${cur.path} -> ${resolved.path}")
                }
            }
            cur = cur.parentFile
        }
    }

    private fun safeChmod(file: File, mode: Int) {
        try {
            // Ensure owner rwx on directories so unprivileged app can always manage its own rootfs
            val adjusted = if (file.isDirectory) (mode or 0b111_000_000) else (mode or 0b100_000_000)
            Os.chmod(file.absolutePath, adjusted)
        } catch (_: Throwable) {
            // Best-effort on Android filesystem
        }
    }
}
