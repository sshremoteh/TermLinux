# Known Limitations (Android & PRoot)

- **No Linux Kernel Root / Namespaces**: PRoot operates in userspace via `ptrace`. `systemd`, Docker, kernel modules, `mount`, raw sockets, and `setuid` binaries are not supported.
- **Scoped Storage**: Without `MANAGE_EXTERNAL_STORAGE` (Mode A), Android restricts direct POSIX access to `/storage/emulated/0`. SAF (Mode B) cannot be POSIX bind-mounted directly into PRoot.
- **Private App Directories**: TermLinux does not grant access to other apps' private directories (`/data/data/...`) or Android system partitions.
- **Background Process Lifecycle**: `LinuxSessionService` uses an Android Foreground Service (`specialUse`) to keep the PTY and PRoot process alive when the Activity is minimized. However, Android OS may still terminate background processes under extreme memory pressure, force-stop, or device reboot. When this happens, `SessionManager.verifyProcessLiveness()` detects the dead PID and reports `ProcessLost` honestly.
- **System Key Combinations**: Android and OEMs (e.g., Samsung One UI) reserve certain global shortcuts (such as home/recents/screenshot/DeX keys) that cannot be intercepted by applications.
- **FUSE / Shared Storage Permissions**: Files on `/sdcard` are governed by Android's FUSE/MediaProvider layer; Unix `chmod`/`chown`/symlinks on `/sdcard` have Android-imposed limitations.
