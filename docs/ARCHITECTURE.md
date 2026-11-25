# TermLinux Architecture

## Layers

1. **Presentation (`ui/`, `screens/`, `MainActivity.kt`)**: Jetpack Compose screens (`OnboardingScreen`, `TerminalScreen`, `SettingsScreen`) and `MainViewModel`. No long-lived Linux process or PTY belongs to Activity/Compose lifecycle.
2. **Input (`input/`, `terminal/TerminalHostView.kt`)**: Physical keyboard (`KeyEvent`), mouse (`MotionEvent`), Extra Keys bar, and `ThreeFingerTripleTapDetector` (3 consecutive simultaneous 3-finger taps open the TermLinux menu without sending any termination signal to the shell).
3. **Application (`distro/`, `download/`, `session/`, `settings/`)**:
   - `DistroManager`: manages versioned `distros.json` manifest, resumable HTTPS downloads, SHA-256 verification, atomic staging extraction (`SafeTarExtractor`), and user data preservation.
   - `SessionManager` & `LinuxSessionService`: foreground service (`FOREGROUND_SERVICE_TYPE_SPECIAL_USE`) owning the active `TerminalSession`, reattaching UI clients across Activity background/foreground transitions, and verifying process liveness via `NativeBridge.nativeIsProcessAlive`.
   - `SettingsRepository`: DataStore Preferences with JSON export/import.
4. **Terminal (`native/terminal/`)**: `terminal-emulator` and `terminal-view` from `termux/termux-app` (Apache-2.0) providing ANSI/VT parsing, 16/256/truecolor, UTF-8, scrollback (up to 50,000 lines), selection, copy/paste, bracketed paste, and mouse reporting.
5. **Native Runtime (`app/src/main/cpp/`, `runtime/`)**:
   - `libtermlinux_native.so`: C++17 JNI bridge for PTY (`posix_openpt`, `grantpt`, `unlockpt`, `ptsname_r`, `TIOCSWINSZ`), process spawning, `waitpid`, signals, and liveness checks.
   - `libproot.so` & `libproot-loader.so`: ARM64 PRoot + unbundled loader cross-compiled from `termux/proot` with `libtalloc.a`, packaged in `jniLibs/arm64-v8a/` so SELinux permits execution from `nativeLibraryDir`.
6. **Storage Bridge (`storage/StorageBridge.kt`)**:
   - **Mode A**: Direct `/sdcard` and `/storage/emulated/0` PRoot bind mount when `MANAGE_EXTERNAL_STORAGE` is granted.
   - **Mode B**: Scoped SAF (`ACTION_OPEN_DOCUMENT_TREE`) with `DocumentFile`/`ContentResolver` and `tlfiles` status/index bridge in `/.termlinux_saf`.
