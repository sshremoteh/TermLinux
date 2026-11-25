package com.termlinux.distro

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DistroManifestTest {

    @Test
    fun manifestContainsWideCatalogWithValidHttpsAndSha256() {
        val manifestFile = File("src/main/assets/distros.json")
        assertTrue("assets/distros.json should exist", manifestFile.exists())
        val manifest = DistroManifestLoader.parse(manifestFile.readText())
        val ids = manifest.distros.map { it.id }.toSet()
        assertTrue(ids.containsAll(setOf("debian", "ubuntu", "alpine", "archlinux", "kali")))
        assertTrue("Expected at least 12 distros, got ${ids.size}", ids.size >= 12)
        manifest.distros.forEach { distro ->
            assertTrue("${distro.id} must use https", distro.url.startsWith("https://"))
            assertTrue(
                "${distro.id} sha256 must be 64 hex chars",
                distro.sha256.matches(Regex("^[0-9a-f]{64}$"))
            )
        }
    }
}
