# Android `/sdcard` Storage Access Guide

TermLinux implements a two-mode `StorageBridge` (`app/src/main/java/com/termlinux/storage/StorageBridge.kt`).

## Mode A — Direct Shared Storage (`/sdcard`)

1. Open **Settings -> Storage Access (/sdcard)** or select **All Files Access** during onboarding.
2. Grant **All files access** (`MANAGE_EXTERNAL_STORAGE`) in Android system settings.
3. Return to TermLinux and restart or launch the Linux session.
4. `PRootCommandBuilder` bind-mounts `/storage/emulated/0` to `/sdcard` and `/storage/emulated/0`.

Verification inside Linux:
```bash
ls -la /sdcard
ls /sdcard/Download
printf 'TermLinux test\n' > /sdcard/Download/termlinux-test.txt
cat /sdcard/Download/termlinux-test.txt
rm /sdcard/Download/termlinux-test.txt
```

## Mode B — Storage Access Framework (SAF)

If All Files Access is not granted:
1. Tap **Pick SAF Folder** and select a directory via Android's `ACTION_OPEN_DOCUMENT_TREE`.
2. TermLinux persists the URI permission via `takePersistableUriPermission` and stores it in DataStore.
3. **Important limitation**: `content://` URIs are not POSIX filesystem paths and cannot be bind-mounted by PRoot. TermLinux exposes SAF metadata to Linux via `/.termlinux_saf` and the `tlfiles` helper (`tlfiles status`, `tlfiles list`), and provides read/write/delete testing in the Settings UI.

## No Access Configured

If neither Mode A nor Mode B is configured, `/sdcard` inside rootfs remains an empty mountpoint and `tlfiles status` reports that storage access is not configured.
