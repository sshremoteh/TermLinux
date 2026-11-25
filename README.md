# TermLinux

Standalone ARM64 Android Linux terminal environment powered by **PRoot** and **Termux terminal-emulator/terminal-view** — no root, no Termux, and no SSH server required.

## Features

- **14 ARM64 Linux Distributions**: Debian 12/13, Ubuntu 24.04/22.04, Alpine 3.24/Edge, Kali Linux, Arch Linux ARM, Rocky Linux 9, AlmaLinux 9, CentOS Stream 9, openSUSE Tumbleweed, Void Linux.
- **Multi-session Termux-style UI**: Swipe-out session drawer, 3-finger triple-tap menu gesture, `☰` quick menu key, 2-row compact extra keys bar, pinch-to-zoom font scaling, 4 App UI themes & 10 Terminal color schemes.
- **PRoot Runtime**: Cross-compiled `libproot.so` + `libproot-loader.so` (`arm64-v8a`) with `--link2symlink`, `--sysvipc`, `--ashmem-memfd`, `--kill-on-exit`, `/dev/shm`, and synthetic `/proc` compatibility.
- **Storage Bridge (`/sdcard`)**: Mode A direct bind mount (`MANAGE_EXTERNAL_STORAGE`) and Mode B scoped SAF (`ACTION_OPEN_DOCUMENT_TREE` + `tlfiles`).
- **Background Sessions**: Foreground Service (`specialUse`) with optional CPU WakeLock and honest process-loss detection.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Build & Install](docs/BUILD_AND_INSTALL.md)
- [`/sdcard` Storage Access](docs/SDCARD_ACCESS.md)
- [Limitations](docs/LIMITATIONS.md)
- [Third-Party Licenses](docs/LICENSES.md)
