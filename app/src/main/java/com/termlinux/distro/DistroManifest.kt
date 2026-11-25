package com.termlinux.distro

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DistroManifest(
    val schemaVersion: Int,
    val updatedAt: String,
    val arch: String,
    val distros: List<DistroEntry>
)

@Serializable
data class DistroEntry(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val url: String,
    val sha256: String,
    val archiveFormat: String,
    val packageManager: String,
    val defaultShell: String,
    val fallbackShell: String,
    val approxSizeBytes: Long
)

object DistroManifestLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadFromAssets(context: Context): DistroManifest {
        val raw = context.assets.open("distros.json").bufferedReader().use { it.readText() }
        return parse(raw)
    }

    fun parse(rawJson: String): DistroManifest = json.decodeFromString(rawJson)
}
