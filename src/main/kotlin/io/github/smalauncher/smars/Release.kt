package io.github.smalauncher.smars

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

data class Release(val version: String, val changelog: String, val zip: Path, val zipName: String,
                   val exeName: String, val zipHash: String) {
    val metaContents: String

    init {
        val metaObj = buildJsonObject {
            put("asset_md5", zipHash)
            put("exe_name", exeName)
        }
        metaContents = Json.encodeToString(JsonObject.serializer(), metaObj)
    }

    fun delete() {
        try {
            Files.delete(zip)
        } catch (ignored: IOException) { }
    }
}
