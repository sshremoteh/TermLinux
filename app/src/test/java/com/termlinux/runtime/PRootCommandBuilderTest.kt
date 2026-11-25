package com.termlinux.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PRootCommandBuilderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun buildProducesValidArgvAndEnvp() {
        val proot = tempFolder.newFile("libproot.so").apply { setExecutable(true) }
        val loader = tempFolder.newFile("libproot-loader.so").apply { setExecutable(true) }
        val rootfs = tempFolder.newFolder("rootfs")
        val tmpDir = tempFolder.newFolder("tmp")
        val hostShared = tempFolder.newFolder("shared")

        val spec = PRootCommandBuilder.build(
            prootBinary = proot,
            loaderBinary = loader,
            rootfsDir = rootfs,
            tmpDir = tmpDir,
            shellPath = "/bin/bash",
            bindMounts = listOf(hostShared.absolutePath to "/sdcard")
        )

        assertEquals(proot.absolutePath, spec.executable)
        assertTrue(spec.argv.contains("--link2symlink"))
        assertTrue(spec.argv.contains("-0"))
        assertTrue(spec.argv.contains("${hostShared.absolutePath}:/sdcard"))
        assertTrue(spec.argv.contains("/bin/bash"))
        assertTrue(spec.argv.contains("-l"))
        assertTrue(spec.envp.any { it == "PROOT_LOADER=${loader.absolutePath}" })
        assertTrue(spec.envp.any { it == "PROOT_TMP_DIR=${tmpDir.absolutePath}" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun buildRejectsColonInBindPath() {
        val proot = tempFolder.newFile("libproot.so").apply { setExecutable(true) }
        val loader = tempFolder.newFile("libproot-loader.so").apply { setExecutable(true) }
        val rootfs = tempFolder.newFolder("rootfs")
        val tmpDir = tempFolder.newFolder("tmp")

        PRootCommandBuilder.build(
            prootBinary = proot,
            loaderBinary = loader,
            rootfsDir = rootfs,
            tmpDir = tmpDir,
            shellPath = "/bin/sh",
            bindMounts = listOf("/tmp/a:b" to "/sdcard")
        )
    }
}
