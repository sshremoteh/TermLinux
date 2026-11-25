package com.termlinux.core

import android.content.Context
import java.io.File

class AppPaths(private val context: Context) {
    val filesRoot: File get() = context.filesDir

    fun rootfsDir(distroId: String): File =
        File(File(filesRoot, "rootfs"), sanitizeId(distroId))

    fun stagingDir(distroId: String): File =
        File(File(filesRoot, "staging"), sanitizeId(distroId))

    fun downloadsDir(): File = File(filesRoot, "downloads").apply { mkdirs() }

    fun tmpDir(): File = File(filesRoot, "tmp").apply { mkdirs() }

    fun logsDir(): File = File(filesRoot, "logs").apply { mkdirs() }

    fun safBridgeDir(): File = File(filesRoot, "saf_bridge").apply { mkdirs() }

    fun prootBinary(): File =
        File(context.applicationInfo.nativeLibraryDir, "libproot.so")

    fun prootLoaderBinary(): File =
        File(context.applicationInfo.nativeLibraryDir, "libproot-loader.so")

    private fun sanitizeId(id: String): String {
        require(id.matches(Regex("^[a-z0-9_-]{1,32}$"))) { "Invalid distro id: $id" }
        return id
    }
}
