# Build and Installation Guide

## Prerequisites

- Android SDK Platform 36 (`platforms;android-36`)
- Android NDK `28.2.13676358`
- CMake `3.31.6`
- JDK 17 (for Gradle/AGP)
- `curl`, `tar`, `make`, `nproc` (for building ARM64 PRoot & `libtalloc`)

## 1. Build PRoot ARM64 Binaries

```bash
./scripts/build-proot-arm64.sh
```

This cross-compiles `libtalloc.a` (2.4.4) and `termux/proot` + unbundled loader for `aarch64-linux-android26` and places stripped binaries into:
- `app/src/main/jniLibs/arm64-v8a/libproot.so`
- `app/src/main/jniLibs/arm64-v8a/libproot-loader.so`

## 2. Run Unit Tests & Build APKs

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:testDebugUnitTest assembleDebug assembleRelease
```

Outputs:
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

## 3. Install on ARM64 Android Device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
