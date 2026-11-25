package com.termlinux.runtime

object NativeBridge {
    init {
        System.loadLibrary("termlinux_native")
    }

    /**
     * Spawns a process attached to a new PTY.
     * Returns int[2] = { masterFd, pid }, or null on failure.
     */
    external fun nativeSpawnSession(
        executable: String,
        argv: Array<String>,
        envp: Array<String>,
        cwd: String,
        cols: Int,
        rows: Int
    ): IntArray?

    external fun nativeResizePty(masterFd: Int, cols: Int, rows: Int): Int

    /**
     * Calls waitpid(pid, WNOHANG if noHang).
     * Returns:
     *  >= 0 : exit code (or 128 + signal if signaled)
     *  -1000: process still running (when noHang == true)
     *  < 0  : -errno
     */
    external fun nativeWaitPid(pid: Int, noHang: Boolean): Int

    external fun nativeKillProcess(pid: Int, signal: Int): Int

    external fun nativeIsProcessAlive(pid: Int): Boolean

    external fun nativeCloseFd(fd: Int)
}
