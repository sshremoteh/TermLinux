package com.termlinux.download

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Sha256VerifierTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun verifyMatchesExpectedHashAndRejectsMismatch() = runTest {
        val file = tempFolder.newFile("sample.bin")
        file.writeText("termlinux-sha256-check\n")
        val hash = Sha256Verifier.computeSha256(file)
        assertTrue(Sha256Verifier.verify(file, hash))
        assertFalse(Sha256Verifier.verify(file, "0".repeat(64)))
    }
}
