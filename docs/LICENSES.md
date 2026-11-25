# Third-Party Licenses

| Component | Source | License | Usage in TermLinux |
| --- | --- | --- | --- |
| PRoot (Termux fork) | `https://github.com/termux/proot` | GPL-2.0-or-later | Executed as standalone ARM64 binary (`libproot.so`, `libproot-loader.so`) |
| libtalloc 2.4.4 | `https://www.samba.org/ftp/talloc/` | LGPL-3.0-or-later | Statically linked into `libproot.so` |
| Termux `terminal-emulator` | `https://github.com/termux/termux-app` | Apache-2.0 | VT parser, terminal buffer, PTY session |
| Termux `terminal-view` | `https://github.com/termux/termux-app` | Apache-2.0 | Terminal canvas renderer, keyboard/mouse input |
| OkHttp | `com.squareup.okhttp3:okhttp` | Apache-2.0 | HTTPS resumable rootfs downloads |
| Apache Commons Compress | `org.apache.commons:commons-compress` | Apache-2.0 | Safe `.tar` / `.tar.gz` extraction |
| XZ for Java | `org.tukaani:xz` | 0BSD / Public Domain | `.tar.xz` decompression |
| Kotlinx Serialization / Coroutines | JetBrains | Apache-2.0 | Manifest/settings JSON & async |
| AndroidX / Jetpack Compose | Google | Apache-2.0 | UI, DataStore, Lifecycle, DocumentFile |
