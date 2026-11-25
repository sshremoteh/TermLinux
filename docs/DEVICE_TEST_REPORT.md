# Device & Build Test Report

## Host Build & Unit Tests

- **PRoot ARM64 cross-compile (`scripts/build-proot-arm64.sh`)**:
  - `libproot.so`: ELF 64-bit LSB pie executable, ARM aarch64, Android 26, stripped (296 KB).
  - `libproot-loader.so`: ELF 64-bit LSB executable, ARM aarch64, statically linked, stripped (18 KB).
- **Host Unit Tests (`./gradlew :app:testDebugUnitTest`)**:
  - `PRootCommandBuilderTest`
  - `SafeTarExtractorTest`
  - `DistroManifestTest`
  - `ThreeFingerTripleTapDetectorTest`
  - `Sha256VerifierTest`
- **APK Builds**: Debug and Release APKs built via Gradle (`assembleDebug`, `assembleRelease`).

## Physical Device Verification Status

No physical Android ARM64 device was attached via `adb devices -l` during this build session. The following checks must be executed on the target device (**Samsung Galaxy S22+ / Android 16 ARM64**):

- [ ] Clean install of `app-debug.apk` / `app-release.apk` without Termux or root.
- [ ] Debian 12, Ubuntu 24.04.5, and Alpine 3.24.4 download, SHA-256 check, extraction, and shell launch.
- [ ] Interrupted download resume and corrupted SHA-256 rejection.
- [ ] Mode A `/sdcard` read/write test (`ls /sdcard`, `printf ... > /sdcard/Download/termlinux-test.txt`, verify in Android Files, delete).
- [ ] Mode B SAF folder picker, `tlfiles status`, `tlfiles list`, and Settings SAF read/write test.
- [ ] Physical keyboard (Ctrl+C, Ctrl+D, Ctrl+Z, arrows, Home/End, F1–F12, NumPad) and physical mouse (hover, click, wheel scrollback, mouse reporting).
- [ ] 3-finger triple-tap gesture opens TermLinux Menu without killing Linux processes.
- [ ] Backgrounding Activity keeps `LinuxSessionService` notification and shell alive; OS kill is detected and reported as `ProcessLost`.
