package com.termlinux.distro

import android.content.Context
import com.termlinux.core.AppPaths
import com.termlinux.download.DownloadProgress
import com.termlinux.download.ResumableDownloader
import com.termlinux.download.Sha256Verifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

@Serializable
data class InstalledDistroMarker(
    val distroId: String,
    val version: String,
    val installedAtEpochMs: Long,
    val sha256: String
)

sealed interface InstallState {
    data object Idle : InstallState
    data class Downloading(val distro: DistroEntry, val progress: DownloadProgress) : InstallState
    data class Verifying(val distro: DistroEntry) : InstallState
    data class Extracting(val distro: DistroEntry, val entriesProcessed: Int) : InstallState
    data class Configuring(val distro: DistroEntry) : InstallState
    data class Ready(val distro: DistroEntry) : InstallState
    data class Interrupted(val distro: DistroEntry, val reason: String) : InstallState
    data class Failed(val distro: DistroEntry?, val error: String) : InstallState
}

class DistroManager(
    private val context: Context,
    private val paths: AppPaths = AppPaths(context),
    private val downloader: ResumableDownloader = ResumableDownloader()
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState.asStateFlow()

    fun loadManifest(): DistroManifest = DistroManifestLoader.loadFromAssets(context)

    fun isInstalled(distroId: String): Boolean {
        val rootfs = paths.rootfsDir(distroId)
        val marker = File(rootfs, MARKER_FILENAME)
        return rootfs.isDirectory && marker.isFile
    }

    fun hasInterruptedInstall(distroId: String): Boolean {
        val staging = paths.stagingDir(distroId)
        val archive = archiveFileFor(distroId)
        return !isInstalled(distroId) && (staging.exists() || archive.exists())
    }

    fun getInstalledDistros(): List<InstalledDistroMarker> {
        val manifest = loadManifest()
        return manifest.distros.mapNotNull { entry ->
            val markerFile = File(paths.rootfsDir(entry.id), MARKER_FILENAME)
            if (markerFile.isFile) {
                runCatching { json.decodeFromString<InstalledDistroMarker>(markerFile.readText()) }.getOrNull()
            } else {
                null
            }
        }
    }

    fun resolveShell(distro: DistroEntry): String {
        val rootfs = paths.rootfsDir(distro.id)
        val preferred = File(rootfs, distro.defaultShell.removePrefix("/"))
        if (preferred.exists()) return distro.defaultShell
        val bash = File(rootfs, "bin/bash")
        if (bash.exists()) return "/bin/bash"
        return distro.fallbackShell
    }

    suspend fun installOrResume(
        distro: DistroEntry,
        forceReinstall: Boolean = false
    ): Result<InstalledDistroMarker> = withContext(Dispatchers.IO) {
        runCatching {
            val rootfsDir = paths.rootfsDir(distro.id)
            if (isInstalled(distro.id) && !forceReinstall) {
                val existing = json.decodeFromString<InstalledDistroMarker>(
                    File(rootfsDir, MARKER_FILENAME).readText()
                )
                _installState.value = InstallState.Ready(distro)
                return@runCatching existing
            }

            val archiveFile = archiveFileFor(distro.id)
            if (forceReinstall && archiveFile.exists()) {
                archiveFile.delete()
            }

            _installState.value = InstallState.Downloading(distro, DownloadProgress(0L, distro.approxSizeBytes))
            downloader.download(distro.url, archiveFile) { progress ->
                _installState.value = InstallState.Downloading(distro, progress)
            }

            _installState.value = InstallState.Verifying(distro)
            val valid = Sha256Verifier.verify(archiveFile, distro.sha256)
            if (!valid) {
                archiveFile.delete()
                throw SecurityException("SHA-256 verification failed for ${distro.name}")
            }

            val stagingDir = paths.stagingDir(distro.id)
            _installState.value = InstallState.Extracting(distro, 0)
            SafeTarExtractor.extract(archiveFile, distro.archiveFormat, stagingDir) { count ->
                _installState.value = InstallState.Extracting(distro, count)
            }

            _installState.value = InstallState.Configuring(distro)
            configureRootfs(stagingDir, distro)

            // Preserve user data (/root and /home) if reinstalling over an existing rootfs
            if (rootfsDir.exists()) {
                preserveUserDirs(fromRootfs = rootfsDir, toStaging = stagingDir)
                if (!rootfsDir.deleteRecursively()) {
                    throw IOException("Failed to remove previous rootfs at ${rootfsDir.absolutePath}")
                }
            }
            rootfsDir.parentFile?.mkdirs()
            if (!stagingDir.renameTo(rootfsDir)) {
                throw IOException("Failed to atomically move staging rootfs into ${rootfsDir.absolutePath}")
            }

            val marker = InstalledDistroMarker(
                distroId = distro.id,
                version = distro.version,
                installedAtEpochMs = System.currentTimeMillis(),
                sha256 = distro.sha256
            )
            File(rootfsDir, MARKER_FILENAME).writeText(json.encodeToString(marker))
            archiveFile.delete()
            _installState.value = InstallState.Ready(distro)
            marker
        }.onFailure { err ->
            if (err is kotlinx.coroutines.CancellationException) {
                _installState.value = InstallState.Interrupted(distro, "Installation cancelled")
            } else {
                _installState.value = InstallState.Failed(distro, err.message ?: err.javaClass.simpleName)
            }
        }
    }

    fun ensureRuntimePatches(distro: DistroEntry) {
        val rootfs = paths.rootfsDir(distro.id)
        if (rootfs.isDirectory) {
            configureRootfs(rootfs, distro)
        }
    }

    private fun configureRootfs(rootfs: File, distro: DistroEntry) {
        val etcDir = File(rootfs, "etc").apply { mkdirs() }
        File(rootfs, "root").mkdirs()
        File(rootfs, "tmp").mkdirs()
        File(rootfs, "dev/shm").mkdirs()
        File(rootfs, "sdcard").mkdirs()
        File(rootfs, "storage/emulated").mkdirs()

        // /etc/mtab -> /proc/mounts for df, mount, and coreutils
        val mtab = File(etcDir, "mtab")
        if (!mtab.exists() && !java.nio.file.Files.isSymbolicLink(mtab.toPath())) {
            runCatching { android.system.Os.symlink("/proc/mounts", mtab.absolutePath) }
        }

        // Suppress Perl/APT "Setting locale failed" warnings
        val defaultDir = File(etcDir, "default").apply { mkdirs() }
        File(defaultDir, "locale").writeText("LANG=C.UTF-8\nLC_ALL=C.UTF-8\n")

        // Ensure /tmp and /dev/shm have 1777 permissions
        runCatching { android.system.Os.chmod(File(rootfs, "tmp").absolutePath, 0b111_111_111) }
        runCatching { android.system.Os.chmod(File(rootfs, "dev/shm").absolutePath, 0b111_111_111) }

        // Disable APT seccomp/user-sandbox in PRoot where setgroups/setuid is emulated
        if (distro.packageManager == "apt") {
            val aptConfD = File(etcDir, "apt/apt.conf.d").apply { mkdirs() }
            File(aptConfD, "99termlinux").writeText(
                "APT::Sandbox::User \"root\";\n" +
                    "Acquire::Retries \"3\";\n"
            )
        }

        // DNS configuration without requiring root-only netd calls
        val resolv = File(etcDir, "resolv.conf")
        if (java.nio.file.Files.isSymbolicLink(resolv.toPath())) {
            resolv.delete()
        }
        resolv.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")

        val hosts = File(etcDir, "hosts")
        if (!hosts.exists()) {
            hosts.writeText("127.0.0.1 localhost\n::1 localhost\n")
        }

        // Default environment profile
        val profileD = File(etcDir, "profile.d").apply { mkdirs() }
        File(profileD, "termlinux.sh").writeText(
            """
            export TERM="${'$'}{TERM:-xterm-256color}"
            export LANG="${'$'}{LANG:-C.UTF-8}"
            export LC_ALL="${'$'}{LC_ALL:-C.UTF-8}"
            export TMPDIR="${'$'}{TMPDIR:-/tmp}"
            """.trimIndent() + "\n"
        )

        // Install tlfiles helper for SAF Storage Bridge & diagnostics
        val usrLocalBin = File(rootfs, "usr/local/bin").apply { mkdirs() }
        val tlfiles = File(usrLocalBin, "tlfiles")
        tlfiles.writeText(TLFILES_SCRIPT)
        tlfiles.setExecutable(true, false)

        // Note Android/PRoot package manager environment hint
        val motd = File(etcDir, "motd")
        motd.writeText(
            "TermLinux (${distro.name}) running via PRoot.\n" +
                "Package manager: ${distro.packageManager}. " +
                "Note: kernel-level features (systemd, Docker, raw sockets) are unavailable under Android/PRoot.\n" +
                "Run 'tlfiles help' for /sdcard and Storage Bridge status.\n"
        )
    }

    private fun preserveUserDirs(fromRootfs: File, toStaging: File) {
        for (sub in listOf("root", "home")) {
            val oldDir = File(fromRootfs, sub)
            val newDir = File(toStaging, sub)
            if (oldDir.exists() && oldDir.isDirectory) {
                oldDir.copyRecursively(newDir, overwrite = true)
            }
        }
    }

    private fun archiveFileFor(distroId: String): File =
        File(paths.downloadsDir(), "$distroId.rootfs.part")

    companion object {
        const val MARKER_FILENAME = ".termlinux-installed.json"

        private val TLFILES_SCRIPT = """
            #!/bin/sh
            set -e
            BRIDGE_DIR="/.termlinux_saf"
            case "${'$'}{1:-help}" in
              status)
                if [ -d "/sdcard" ] && [ -n "${'$'}(ls -A /sdcard 2>/dev/null || true)" ]; then
                  echo "Storage mode: Direct /sdcard bind mount (or populated directory)."
                elif [ -f "${'$'}BRIDGE_DIR/status.txt" ]; then
                  cat "${'$'}BRIDGE_DIR/status.txt"
                else
                  echo "Storage access is not configured. Open TermLinux Settings -> Storage (/sdcard)."
                fi
                ;;
              list)
                if [ -f "${'$'}BRIDGE_DIR/tree.txt" ]; then
                  cat "${'$'}BRIDGE_DIR/tree.txt"
                else
                  echo "No SAF tree index available. Configure SAF folder in TermLinux Settings." >&2
                  exit 1
                fi
                ;;
              help|*)
                echo "tlfiles - TermLinux Android Storage Bridge helper"
                echo "Usage:"
                echo "  tlfiles status   Show /sdcard and SAF Storage Bridge status"
                echo "  tlfiles list     List indexed files from SAF-selected folder (Mode B)"
                echo ""
                echo "Note: In Mode A (All Files Access), /sdcard is directly bind-mounted."
                echo "In Mode B (SAF), Android restricts access to the user-selected folder;"
                echo "POSIX bind-mounting a content:// URI is not supported by Android/PRoot."
                ;;
            esac
        """.trimIndent() + "\n"
    }
}
