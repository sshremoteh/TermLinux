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

        // Install tlfiles, tl-service, systemctl, tl-bootstrap, tl-desktop, tl-doctor, tl-clip, tl-open, tl-notify
        val usrLocalBin = File(rootfs, "usr/local/bin").apply { mkdirs() }
        writeExecutable(File(usrLocalBin, "tlfiles"), TLFILES_SCRIPT)
        writeExecutable(File(usrLocalBin, "tl-service"), TL_SERVICE_SCRIPT)
        writeExecutable(File(usrLocalBin, "systemctl"), SYSTEMCTL_WRAPPER_SCRIPT)
        writeExecutable(File(usrLocalBin, "tl-bootstrap"), TL_BOOTSTRAP_SCRIPT)
        writeExecutable(File(usrLocalBin, "tl-desktop"), TL_DESKTOP_SCRIPT)
        writeExecutable(File(usrLocalBin, "tl-doctor"), TL_DOCTOR_SCRIPT)
        writeExecutable(File(usrLocalBin, "tl-clip"), TL_CLIP_SCRIPT)
        writeExecutable(File(usrLocalBin, "tl-open"), TL_OPEN_SCRIPT)
        writeExecutable(File(usrLocalBin, "tl-notify"), TL_NOTIFY_SCRIPT)

        // PulseAudio client/daemon config for TermLinux Audio Bridge (127.0.0.1:14713)
        val pulseDir = File(etcDir, "pulse").apply { mkdirs() }
        val pulseDefault = File(pulseDir, "default.pa")
        if (!pulseDefault.exists()) {
            pulseDefault.writeText(
                "load-module module-simple-protocol-tcp rate=44100 format=s16le channels=2 source=auto_null.monitor record=false port=14713\n" +
                    "load-module module-native-protocol-tcp auth-ip-acl=127.0.0.1 port=4713\n"
            )
        }

        // Note Android/PRoot package manager environment hint
        val motd = File(etcDir, "motd")
        motd.writeText(
            "TermLinux (${distro.name}) running via PRoot.\n" +
                "Package manager: ${distro.packageManager}.\n" +
                "Helpers: tl-bootstrap (dev tools), tl-service / systemctl (daemons), tl-desktop (GUI), tl-doctor (check), tlfiles (/sdcard).\n"
        )
    }

    private fun writeExecutable(file: File, content: String) {
        file.writeText(content)
        file.setExecutable(true, false)
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

        private val TL_SERVICE_SCRIPT = """
            #!/bin/sh
            set -e
            RUN_DIR="/var/run/tl-services"
            LOG_DIR="/var/log/tl-services"
            mkdir -p "${'$'}RUN_DIR" "${'$'}LOG_DIR"

            cmd="${'$'}{1:-list}"
            svc="${'$'}{2:-}"

            start_service() {
              name="${'$'}1"
              pidfile="${'$'}RUN_DIR/${'$'}name.pid"
              logfile="${'$'}LOG_DIR/${'$'}name.log"
              if [ -f "${'$'}pidfile" ] && kill -0 "${'$'}(cat "${'$'}pidfile")" 2>/dev/null; then
                echo "[tl-service] ${'$'}name already running (PID ${'$'}(cat "${'$'}pidfile"))"
                return 0
              fi
              case "${'$'}name" in
                ssh|sshd)
                  mkdir -p /run/sshd
                  [ -f /etc/ssh/ssh_host_rsa_key ] || ssh-keygen -A 2>/dev/null || true
                  nohup /usr/sbin/sshd -D -p 2222 >"${'$'}logfile" 2>&1 &
                  ;;
                cron|crond)
                  if command -v cron >/dev/null 2>&1; then
                    nohup cron -f >"${'$'}logfile" 2>&1 &
                  else
                    nohup crond -f >"${'$'}logfile" 2>&1 &
                  fi
                  ;;
                nginx)
                  nohup nginx -g "daemon off;" >"${'$'}logfile" 2>&1 &
                  ;;
                redis|redis-server)
                  nohup redis-server --daemonize no >"${'$'}logfile" 2>&1 &
                  ;;
                *)
                  if [ -x "/etc/init.d/${'$'}name" ]; then
                    "/etc/init.d/${'$'}name" start >"${'$'}logfile" 2>&1
                    return 0
                  fi
                  echo "[tl-service] Unknown service '${'$'}name'. Supported: sshd (port 2222), cron, nginx, redis-server, or /etc/init.d/<name>." >&2
                  exit 1
                  ;;
              esac
              echo ${'$'}! > "${'$'}pidfile"
              echo "[tl-service] Started ${'$'}name (PID ${'$'}!)"
            }

            stop_service() {
              name="${'$'}1"
              pidfile="${'$'}RUN_DIR/${'$'}name.pid"
              if [ -f "${'$'}pidfile" ]; then
                pid="${'$'}(cat "${'$'}pidfile")"
                kill "${'$'}pid" 2>/dev/null || true
                rm -f "${'$'}pidfile"
                echo "[tl-service] Stopped ${'$'}name"
              elif [ -x "/etc/init.d/${'$'}name" ]; then
                "/etc/init.d/${'$'}name" stop || true
              else
                echo "[tl-service] ${'$'}name is not running"
              fi
            }

            case "${'$'}cmd" in
              start)
                [ -n "${'$'}svc" ] || { echo "Usage: tl-service start <name>"; exit 1; }
                start_service "${'$'}svc"
                ;;
              stop)
                [ -n "${'$'}svc" ] || { echo "Usage: tl-service stop <name>"; exit 1; }
                stop_service "${'$'}svc"
                ;;
              restart)
                [ -n "${'$'}svc" ] || { echo "Usage: tl-service restart <name>"; exit 1; }
                stop_service "${'$'}svc"
                start_service "${'$'}svc"
                ;;
              status)
                [ -n "${'$'}svc" ] || { echo "Usage: tl-service status <name>"; exit 1; }
                pidfile="${'$'}RUN_DIR/${'$'}svc.pid"
                if [ -f "${'$'}pidfile" ] && kill -0 "${'$'}(cat "${'$'}pidfile")" 2>/dev/null; then
                  echo "${'$'}svc: running (PID ${'$'}(cat "${'$'}pidfile"))"
                else
                  echo "${'$'}svc: stopped"
                  exit 3
                fi
                ;;
              logs)
                [ -n "${'$'}svc" ] || { echo "Usage: tl-service logs <name>"; exit 1; }
                cat "${'$'}LOG_DIR/${'$'}svc.log" 2>/dev/null || echo "No logs for ${'$'}svc"
                ;;
              list|*)
                echo "TermLinux user-space services (${'$'}RUN_DIR):"
                for f in "${'$'}RUN_DIR"/*.pid; do
                  [ -e "${'$'}f" ] || { echo "  (none running)"; break; }
                  n="${'$'}(basename "${'$'}f" .pid)"
                  p="${'$'}(cat "${'$'}f")"
                  if kill -0 "${'$'}p" 2>/dev/null; then
                    echo "  [UP]   ${'$'}n (PID ${'$'}p)"
                  else
                    echo "  [DEAD] ${'$'}n (stale PID ${'$'}p)"
                  fi
                done
                ;;
            esac
        """.trimIndent() + "\n"

        private val SYSTEMCTL_WRAPPER_SCRIPT = """
            #!/bin/sh
            # TermLinux PRoot systemctl compatibility wrapper (no systemd kernel PID 1 in PRoot)
            action="${'$'}{1:-}"
            unit="${'$'}{2:-}"
            unit="${'$'}{unit%.service}"
            case "${'$'}action" in
              start|stop|restart|status)
                exec /usr/local/bin/tl-service "${'$'}action" "${'$'}unit"
                ;;
              is-active)
                /usr/local/bin/tl-service status "${'$'}unit" >/dev/null 2>&1 && echo "active" || { echo "inactive"; exit 3; }
                ;;
              enable|disable|daemon-reload)
                echo "[systemctl-pr] '${'$'}action ${'$'}unit' acknowledged (PRoot user-space mode)."
                ;;
              *)
                exec /usr/local/bin/tl-service list
                ;;
            esac
        """.trimIndent() + "\n"

        private val TL_BOOTSTRAP_SCRIPT = """
            #!/bin/sh
            set -e
            echo "[tl-bootstrap] Installing core Linux developer tools..."
            if command -v apt-get >/dev/null 2>&1; then
              apt-get update
              DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
                ca-certificates curl wget git vim nano htop tmux sudo openssh-client python3 locales
            elif command -v apk >/dev/null 2>&1; then
              apk update
              apk add --no-cache ca-certificates curl wget git vim nano htop tmux sudo openssh-client python3 bash
            elif command -v pacman >/dev/null 2>&1; then
              pacman -Sy --noconfirm ca-certificates curl wget git vim nano htop tmux sudo openssh python
            elif command -v dnf >/dev/null 2>&1; then
              dnf install -y ca-certificates curl wget git vim nano htop tmux sudo openssh-clients python3
            elif command -v zypper >/dev/null 2>&1; then
              zypper --non-interactive install ca-certificates curl wget git vim nano htop tmux sudo openssh python3
            elif command -v xbps-install >/dev/null 2>&1; then
              xbps-install -Suy ca-certificates curl wget git vim nano htop tmux sudo openssh python3
            else
              echo "Unsupported package manager." >&2
              exit 1
            fi

            if [ "${'$'}{TL_BOOTSTRAP_ZSH:-0}" = "1" ]; then
              echo "[tl-bootstrap] Installing Zsh + Tmux preset..."
              if command -v apt-get >/dev/null 2>&1; then
                DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends zsh
              elif command -v apk >/dev/null 2>&1; then
                apk add --no-cache zsh
              elif command -v pacman >/dev/null 2>&1; then
                pacman -S --noconfirm zsh
              elif command -v dnf >/dev/null 2>&1; then
                dnf install -y zsh
              fi
              cat > /root/.zshrc << 'ZRC'
            export TERM="${'$'}{TERM:-xterm-256color}"
            autoload -Uz vcs_info
            precmd() { vcs_info }
            zstyle ':vcs_info:git:*' formats ' (%b)'
            setopt PROMPT_SUBST
            PROMPT='%F{green}%n@termlinux%f:%F{cyan}%~%f%F{yellow}${'$'}vcs_info_msg_0_%f %# '
            HISTFILE=~/.zsh_history
            HISTSIZE=5000
            SAVEHIST=5000
            ZRC
            fi

            if [ "${'$'}{TL_BOOTSTRAP_CREATE_USER:-0}" = "1" ]; then
              uname="${'$'}{TL_BOOTSTRAP_USER:-linux}"
              echo "[tl-bootstrap] Creating sudo user '${'$'}uname'..."
              if ! id "${'$'}uname" >/dev/null 2>&1; then
                if command -v useradd >/dev/null 2>&1; then
                  useradd -m -s /bin/bash "${'$'}uname"
                elif command -v adduser >/dev/null 2>&1; then
                  adduser -D "${'$'}uname"
                fi
              fi
              mkdir -p /etc/sudoers.d
              echo "${'$'}uname ALL=(ALL) NOPASSWD:ALL" > "/etc/sudoers.d/${'$'}uname"
              chmod 0440 "/etc/sudoers.d/${'$'}uname"
            fi
            echo "[tl-bootstrap] Done."
        """.trimIndent() + "\n"

        private val TL_DESKTOP_SCRIPT = """
            #!/bin/sh
            set -e
            cmd="${'$'}{1:-help}"
            case "${'$'}cmd" in
              setup)
                echo "[tl-desktop] Installing XFCE4 + VNC server for TermLinux GUI Desktop..."
                if command -v apt-get >/dev/null 2>&1; then
                  apt-get update
                  DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
                    xfce4 xfce4-terminal tigervnc-standalone-server dbus-x11 xfonts-base
                elif command -v apk >/dev/null 2>&1; then
                  apk update
                  apk add --no-cache xfce4 xfce4-terminal xvfb x11vnc dbus ttf-dejavu
                elif command -v pacman >/dev/null 2>&1; then
                  pacman -Sy --noconfirm xfce4 xfce4-terminal tigervnc dbus
                else
                  echo "Automatic setup supports apt/apk/pacman. Install xfce4 + tigervnc manually." >&2
                  exit 1
                fi
                mkdir -p /root/.vnc
                printf '#!/bin/sh\nunset SESSION_MANAGER\nunset DBUS_SESSION_BUS_ADDRESS\nexec startxfce4\n' > /root/.vnc/xstartup
                chmod +x /root/.vnc/xstartup
                echo "[tl-desktop] Setup complete. Run 'tl-desktop start' then open Desktop (GUI) in TermLinux."
                ;;
              start)
                mkdir -p /tmp/.X11-unix /root/.vnc
                export DISPLAY=:1
                if command -v vncserver >/dev/null 2>&1; then
                  vncserver :1 -geometry 1280x720 -depth 24 -SecurityTypes None -localhost yes
                elif command -v Xvnc >/dev/null 2>&1; then
                  nohup Xvnc :1 -geometry 1280x720 -depth 24 -SecurityTypes None -localhost yes >/tmp/xvnc.log 2>&1 &
                  sleep 1
                  nohup dbus-run-session startxfce4 >/tmp/xfce.log 2>&1 &
                elif command -v Xvfb >/dev/null 2>&1 && command -v x11vnc >/dev/null 2>&1; then
                  nohup Xvfb :1 -screen 0 1280x720x24 >/tmp/xvfb.log 2>&1 &
                  sleep 1
                  nohup x11vnc -display :1 -nopw -listen 127.0.0.1 -rfbport 5901 -forever >/tmp/x11vnc.log 2>&1 &
                  nohup dbus-run-session startxfce4 >/tmp/xfce.log 2>&1 &
                else
                  echo "No VNC server found. Run 'tl-desktop setup' first." >&2
                  exit 1
                fi
                echo "[tl-desktop] GUI Desktop listening on 127.0.0.1:5901. Open Desktop (GUI) from TermLinux menu."
                ;;
              stop)
                vncserver -kill :1 2>/dev/null || pkill -f "Xvnc :1|x11vnc|startxfce4" 2>/dev/null || true
                echo "[tl-desktop] Desktop stopped."
                ;;
              help|*)
                echo "tl-desktop - TermLinux GUI Desktop manager"
                echo "  tl-desktop setup   Install XFCE4 + VNC server"
                echo "  tl-desktop start   Start GUI desktop on 127.0.0.1:5901"
                echo "  tl-desktop stop    Stop GUI desktop"
                ;;
            esac
        """.trimIndent() + "\n"

        private val TL_DOCTOR_SCRIPT = """
            #!/bin/sh
            echo "=== TermLinux PRoot Doctor ==="
            echo "Kernel: ${'$'}(uname -a)"
            echo "User:   ${'$'}(id)"
            echo "Shell:  ${'$'}SHELL"
            echo "Locale: LANG=${'$'}LANG LC_ALL=${'$'}LC_ALL"
            echo -n "/dev/shm writable: "
            if (touch /dev/shm/.tl_test && rm -f /dev/shm/.tl_test) 2>/dev/null; then echo "OK"; else echo "FAIL"; fi
            echo -n "/proc/loadavg: "
            cat /proc/loadavg 2>/dev/null || echo "UNAVAILABLE"
            echo -n "DNS /etc/resolv.conf: "
            tr '\n' ' ' < /etc/resolv.conf 2>/dev/null || echo "MISSING"
            echo ""
            /usr/local/bin/tlfiles status
            /usr/local/bin/tl-service list
        """.trimIndent() + "\n"

        private val TL_CLIP_SCRIPT = """
            #!/bin/sh
            set -e
            PORT="${'$'}{TL_BRIDGE_PORT:-14714}"
            TOKEN="${'$'}{TL_BRIDGE_TOKEN:-}"
            [ -n "${'$'}TOKEN" ] || { echo "TL_BRIDGE_TOKEN missing; restart TermLinux session." >&2; exit 1; }
            b64_dec() { base64 -d 2>/dev/null || base64 --decode; }
            b64_enc() { base64 | tr -d '\n'; }
            send_cmd() {
              printf "AUTH %s\n%s\n" "${'$'}TOKEN" "${'$'}1" | nc -w 3 127.0.0.1 "${'$'}PORT"
            }
            case "${'$'}{1:-get}" in
              get)
                resp="${'$'}(send_cmd "CLIP_GET")"
                case "${'$'}resp" in
                  OK\ *) printf "%s" "${'$'}{resp#OK }" | b64_dec ;;
                  *) echo "${'$'}resp" >&2; exit 1 ;;
                esac
                ;;
              set)
                payload="${'$'}(cat | b64_enc)"
                resp="${'$'}(send_cmd "CLIP_SET ${'$'}payload")"
                [ "${'$'}resp" = "OK" ] || { echo "${'$'}resp" >&2; exit 1; }
                ;;
              *)
                echo "Usage: tl-clip get | echo text | tl-clip set" >&2
                exit 1
                ;;
            esac
        """.trimIndent() + "\n"

        private val TL_OPEN_SCRIPT = """
            #!/bin/sh
            set -e
            [ -n "${'$'}{1:-}" ] || { echo "Usage: tl-open <url>" >&2; exit 1; }
            PORT="${'$'}{TL_BRIDGE_PORT:-14714}"
            TOKEN="${'$'}{TL_BRIDGE_TOKEN:-}"
            payload="${'$'}(printf "%s" "${'$'}1" | base64 | tr -d '\n')"
            resp="${'$'}(printf "AUTH %s\nOPEN %s\n" "${'$'}TOKEN" "${'$'}payload" | nc -w 3 127.0.0.1 "${'$'}PORT")"
            [ "${'$'}resp" = "OK" ] || { echo "${'$'}resp" >&2; exit 1; }
        """.trimIndent() + "\n"

        private val TL_NOTIFY_SCRIPT = """
            #!/bin/sh
            set -e
            title="${'$'}{1:-TermLinux}"
            body="${'$'}{2:-${'$'}title}"
            [ "${'$'}#" -eq 1 ] && title="TermLinux"
            PORT="${'$'}{TL_BRIDGE_PORT:-14714}"
            TOKEN="${'$'}{TL_BRIDGE_TOKEN:-}"
            t64="${'$'}(printf "%s" "${'$'}title" | base64 | tr -d '\n')"
            b64="${'$'}(printf "%s" "${'$'}body" | base64 | tr -d '\n')"
            resp="${'$'}(printf "AUTH %s\nNOTIFY %s %s\n" "${'$'}TOKEN" "${'$'}t64" "${'$'}b64" | nc -w 3 127.0.0.1 "${'$'}PORT")"
            [ "${'$'}resp" = "OK" ] || { echo "${'$'}resp" >&2; exit 1; }
        """.trimIndent() + "\n"
    }
}
