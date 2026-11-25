#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NDK_DIR="${ANDROID_NDK_HOME:-$HOME/Android/Sdk/ndk/28.2.13676358}"
TOOLCHAIN="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64"
TARGET="aarch64-linux-android26"
CC="$TOOLCHAIN/bin/${TARGET}-clang"
AR="$TOOLCHAIN/bin/llvm-ar"
STRIP="$TOOLCHAIN/bin/llvm-strip"
OBJCOPY="$TOOLCHAIN/bin/llvm-objcopy"
OBJDUMP="$TOOLCHAIN/bin/llvm-objdump"

if [[ ! -x "$CC" ]]; then
  echo "NDK clang not found at $CC" >&2
  exit 1
fi

BUILD_DIR="$ROOT_DIR/native/proot/build-arm64"
TALLOC_VER="2.4.4"
TALLOC_TAR="$BUILD_DIR/talloc-${TALLOC_VER}.tar.gz"
TALLOC_DIR="$BUILD_DIR/talloc-${TALLOC_VER}"
SYSROOT_INC="$BUILD_DIR/sysroot/include"
SYSROOT_LIB="$BUILD_DIR/sysroot/lib"
PROOT_SRC="$ROOT_DIR/native/proot/upstream/src"
PROOT_BUILD="$BUILD_DIR/proot-obj"
JNILIBS_OUT="$ROOT_DIR/app/src/main/jniLibs/arm64-v8a"

mkdir -p "$BUILD_DIR" "$SYSROOT_INC" "$SYSROOT_LIB" "$PROOT_BUILD" "$JNILIBS_OUT"

if [[ ! -f "$TALLOC_TAR" ]]; then
  curl -fsSL "https://www.samba.org/ftp/talloc/talloc-${TALLOC_VER}.tar.gz" -o "$TALLOC_TAR"
fi

if [[ ! -d "$TALLOC_DIR" ]]; then
  tar -xzf "$TALLOC_TAR" -C "$BUILD_DIR"
fi

# Minimal config.h for talloc on Android Bionic aarch64
cat > "$BUILD_DIR/talloc_config.h" << 'EOF'
#ifndef TALLOC_CONFIG_H
#define TALLOC_CONFIG_H
#define HAVE_STDARG_H 1
#define HAVE_STDDEF_H 1
#define HAVE_STDINT_H 1
#define HAVE_STDLIB_H 1
#define HAVE_STRING_H 1
#define HAVE_STRINGS_H 1
#define HAVE_UNISTD_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_ERRNO_H 1
#define HAVE_STRNLEN 1
#define HAVE_VA_COPY 1
#define HAVE_BOOL 1
#define HAVE_INTPTR_T 1
#define HAVE_UINTPTR_T 1
#define HAVE_PTRDIFF_T 1
#define HAVE_SIZE_T 1
#define HAVE_SSIZE_T 1
#define HAVE_STDBOOL_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_LIMITS_H 1
#define HAVE_CTYPE_H 1
#define HAVE_PRINTF 1
#define HAVE_SNPRINTF 1
#define HAVE_VSNPRINTF 1
#define HAVE_ASPRINTF 1
#define HAVE_VASPRINTF 1
#define HAVE_MEMSET_EXPLICIT 0
#define HAVE_CONSTRUCTOR_ATTRIBUTE 1
#define _PUBLIC_ __attribute__((visibility("default")))
#define TALLOC_BUILD_VERSION_MAJOR 2
#define TALLOC_BUILD_VERSION_MINOR 4
#define TALLOC_BUILD_VERSION_RELEASE 4
#endif
EOF

cat > "$BUILD_DIR/replace.h" << 'EOF'
#ifndef TERMLINUX_REPLACE_H
#define TERMLINUX_REPLACE_H
#include "talloc_config.h"
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>
#include <string.h>
#include <strings.h>
#include <unistd.h>
#include <errno.h>
#include <limits.h>
#include <ctype.h>
#include <inttypes.h>
#ifndef memset_s
#define memset_s(dest, destsz, ch, count) (memset((dest), (ch), (count)), 0)
#endif
#ifndef MIN
#define MIN(a,b) (((a)<(b))?(a):(b))
#endif
#ifndef MAX
#define MAX(a,b) (((a)>(b))?(a):(b))
#endif
#endif
EOF

echo "Building libtalloc.a for arm64-v8a..."
"$CC" -fPIC -O2 -Wall -D_GNU_SOURCE \
  -I"$BUILD_DIR" -I"$TALLOC_DIR" \
  -include "$BUILD_DIR/replace.h" \
  -c "$TALLOC_DIR/talloc.c" -o "$BUILD_DIR/talloc.o"

"$AR" rcs "$SYSROOT_LIB/libtalloc.a" "$BUILD_DIR/talloc.o"
cp "$TALLOC_DIR/talloc.h" "$SYSROOT_INC/talloc.h"

echo "Building PRoot & unbundled loader for arm64-v8a..."
rm -rf "$PROOT_BUILD"
cp -r "$PROOT_SRC" "$PROOT_BUILD"
make -C "$PROOT_BUILD" -j"$(nproc)" \
  CC="$CC" \
  LD="$CC" \
  AR="$AR" \
  STRIP="$STRIP" \
  OBJCOPY="$OBJCOPY" \
  OBJDUMP="$OBJDUMP" \
  CFLAGS="-O2 -Wall -Wextra -Wno-implicit-function-declaration -fPIE -I$SYSROOT_INC -D_FILE_OFFSET_BITS=64 -D_GNU_SOURCE -DPROOT_UNBUNDLE_LOADER=\\\"/data/local/tmp\\\"" \
  LDFLAGS="-pie -L$SYSROOT_LIB -ltalloc -Wl,-z,noexecstack" \
  PROOT_UNBUNDLE_LOADER="/data/local/tmp" \
  V=0 \
  proot loader/loader

cp "$PROOT_BUILD/proot" "$JNILIBS_OUT/libproot.so"
cp "$PROOT_BUILD/loader/loader" "$JNILIBS_OUT/libproot-loader.so"
"$STRIP" "$JNILIBS_OUT/libproot.so" "$JNILIBS_OUT/libproot-loader.so"

file "$JNILIBS_OUT/libproot.so" "$JNILIBS_OUT/libproot-loader.so"
ls -lh "$JNILIBS_OUT/libproot.so" "$JNILIBS_OUT/libproot-loader.so"
echo "PRoot ARM64 build complete."
