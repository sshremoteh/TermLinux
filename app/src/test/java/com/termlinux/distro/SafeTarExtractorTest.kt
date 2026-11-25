package com.termlinux.distro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SafeTarExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun normalizeEntryPathStripsLeadingDotsAndSlashes() {
        assertEquals("etc/passwd", SafeTarExtractor.normalizeEntryPath("./etc/passwd"))
        assertEquals("bin/sh", SafeTarExtractor.normalizeEntryPath("/bin/sh/"))
        assertNull(SafeTarExtractor.normalizeEntryPath("./"))
    }

    @Test(expected = SecurityException::class)
    fun normalizeEntryPathRejectsParentTraversal() {
        SafeTarExtractor.normalizeEntryPath("../../etc/shadow")
    }

    @Test(expected = SecurityException::class)
    fun normalizeEntryPathRejectsNestedTraversal() {
        SafeTarExtractor.normalizeEntryPath("usr/bin/../../../etc/passwd")
    }

    @Test
    fun resolveSafeChildKeepsPathInsideRootfs() {
        val root = tempFolder.newFolder("rootfs").canonicalFile
        val child = SafeTarExtractor.resolveSafeChild(root, "usr/local/bin/tlfiles")
        assertEquals("${root.path}/usr/local/bin/tlfiles", child.path)
    }
}
