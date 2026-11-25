package com.termlinux.runtime

import java.io.File

data class PRootLaunchSpec(
    val executable: String,
    val argv: List<String>,
    val envp: List<String>,
    val workingDirectory: String
)

object PRootCommandBuilder {

    /**
     * Safely builds PRoot argv and envp without shell string concatenation.
     * Includes Termux-compatible PRoot extensions (--link2symlink, --sysvipc, --ashmem-memfd)
     * and Android /proc + /dev/shm compatibility bindings.
     */
    fun build(
        prootBinary: File,
        loaderBinary: File,
        rootfsDir: File,
        tmpDir: File,
        shellPath: String,
        bindMounts: List<Pair<String, String>>,
        extraEnv: Map<String, String> = emptyMap(),
        loginShell: Boolean = true
    ): PRootLaunchSpec {
        require(prootBinary.exists() && prootBinary.canExecute()) {
            "PRoot binary is missing or not executable: ${prootBinary.absolutePath}"
        }
        require(loaderBinary.exists() && loaderBinary.canExecute()) {
            "PRoot loader binary is missing or not executable: ${loaderBinary.absolutePath}"
        }
        require(rootfsDir.exists() && rootfsDir.isDirectory) {
            "Rootfs directory does not exist: ${rootfsDir.absolutePath}"
        }
        if (!tmpDir.exists()) {
            tmpDir.mkdirs()
        }

        val shmDir = File(tmpDir, "shm").apply { mkdirs() }
        val fakeProcDir = prepareFakeProc(tmpDir)

        val sanitizedShell = sanitizePath(shellPath)
        val args = mutableListOf<String>()
        args.add(prootBinary.absolutePath)
        args.add("--link2symlink")
        args.add("--sysvipc")
        args.add("--ashmem-memfd")
        args.add("--kill-on-exit")
        args.add("-0")
        args.add("-r")
        args.add(rootfsDir.absolutePath)
        args.add("-w")
        args.add("/root")

        val defaultBinds = mutableListOf(
            "/dev" to "/dev",
            "/dev/urandom" to "/dev/random",
            "/proc" to "/proc",
            "/sys" to "/sys",
            shmDir.absolutePath to "/dev/shm",
            File(fakeProcDir, "loadavg").absolutePath to "/proc/loadavg",
            File(fakeProcDir, "stat").absolutePath to "/proc/stat",
            File(fakeProcDir, "uptime").absolutePath to "/proc/uptime",
            File(fakeProcDir, "version").absolutePath to "/proc/version",
            File(fakeProcDir, "vmstat").absolutePath to "/proc/vmstat"
        )
        if (File("/proc/self/fd").exists()) {
            defaultBinds.add("/proc/self/fd" to "/dev/fd")
        }

        for ((host, guest) in defaultBinds) {
            if (File(host).exists()) {
                args.add("-b")
                args.add("$host:$guest")
            }
        }

        for ((host, guest) in bindMounts) {
            val cleanHost = sanitizePath(host)
            val cleanGuest = sanitizePath(guest)
            if (File(cleanHost).exists()) {
                args.add("-b")
                args.add("$cleanHost:$cleanGuest")
            }
        }

        args.add("/usr/bin/env")
        args.add("-i")
        args.add("HOME=/root")
        args.add("USER=root")
        args.add("LOGNAME=root")
        args.add("SHELL=$sanitizedShell")
        args.add("TERM=xterm-256color")
        args.add("COLORTERM=truecolor")
        args.add("LANG=C.UTF-8")
        args.add("LC_ALL=C.UTF-8")
        args.add("TMPDIR=/tmp")
        args.add("DISPLAY=:1")
        args.add("PULSE_SERVER=127.0.0.1:4713")
        args.add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
        for ((k, v) in extraEnv) {
            require(!k.contains('=') && !k.contains('\u0000') && !v.contains('\u0000')) {
                "Invalid environment entry: $k"
            }
            args.add("$k=$v")
        }

        args.add(sanitizedShell)
        if (loginShell) {
            args.add("-l")
        }

        val hostEnv = listOf(
            "PROOT_TMP_DIR=${tmpDir.absolutePath}",
            "PROOT_LOADER=${loaderBinary.absolutePath}",
            "PROOT_NO_SECCOMP=1",
            "TERM=xterm-256color"
        )

        return PRootLaunchSpec(
            executable = prootBinary.absolutePath,
            argv = args,
            envp = hostEnv,
            workingDirectory = rootfsDir.absolutePath
        )
    }

    /**
     * Android 8+ SELinux blocks /proc/loadavg, /proc/stat, /proc/uptime, /proc/version, /proc/vmstat
     * for untrusted apps. Providing synthetic /proc entries prevents top, htop, ps, free, and psutil from crashing.
     */
    private fun prepareFakeProc(tmpDir: File): File {
        val dir = File(tmpDir, "proc_fake").apply { mkdirs() }
        val loadavg = File(dir, "loadavg")
        if (!loadavg.exists()) loadavg.writeText("0.12 0.08 0.04 1/128 1000\n")

        val stat = File(dir, "stat")
        if (!stat.exists()) {
            stat.writeText(
                "cpu  1000 0 500 8000 0 0 0 0 0 0\n" +
                    "cpu0 250 0 125 2000 0 0 0 0 0 0\n" +
                    "intr 0\nctxt 10000\nbtime 1700000000\nprocesses 1000\nprocs_running 1\nprocs_blocked 0\n"
            )
        }

        val uptime = File(dir, "uptime")
        if (!uptime.exists()) uptime.writeText("3600.00 3500.00\n")

        val version = File(dir, "version")
        if (!version.exists()) version.writeText("Linux version 6.1.0-termlinux (gcc 12.2.0) #1 SMP PREEMPT\n")

        val vmstat = File(dir, "vmstat")
        if (!vmstat.exists()) vmstat.writeText("nr_free_pages 262144\nnr_active_anon 65536\n")
        return dir
    }

    private fun sanitizePath(path: String): String {
        require(path.isNotBlank() && !path.contains('\u0000') && !path.contains(':')) {
            "Unsafe path for PRoot bind/exec: $path"
        }
        return path
    }
}
