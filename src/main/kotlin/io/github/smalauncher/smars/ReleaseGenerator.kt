package io.github.smalauncher.smars

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.zip.ZipInputStream
import kotlin.io.path.bufferedWriter
import kotlin.io.path.writeBytes

class ReleaseGenerator(val client: HttpClient) {
    private var digest: MessageDigest
    private val digestBuf = ByteArray(1024)

    init {
        try {
            digest = MessageDigest.getInstance("MD5")
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("MD5 not supported?!", e)
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02X".format(it) }
    }

    suspend fun generate(url: String, filename: String, changelog: String): Result<Release> {
        val res: HttpResponse = client.get(url)

        val lastUnderscore = filename.lastIndexOf('_')
        val lastDot = filename.lastIndexOf('.')
        val version = filename.substring(lastUnderscore + 1, lastDot).replace('-', '.')

        @Suppress("BlockingMethodInNonBlockingContext") // theoretically, blocking here should be fine
        return withContext(Dispatchers.IO) {
            val path = Files.createTempFile("smars_tmp_", filename)
            val responseBody: ByteArray = res.receive()
            val bais = ByteArrayInputStream(responseBody)
            path.writeBytes(responseBody)

            var exeName = ""
            bais.reset()
            val zis = ZipInputStream(bais)
            zis.use {
                var entry = it.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name.startsWith("Shang_Mu_Architect_")
                        && name.endsWith(".exe")) {
                        exeName = name
                        break
                    }
                    entry = it.nextEntry
                }
            }

            if (exeName.isEmpty())
                return@withContext Result.failure(FileNotFoundException("Could not find Shang Mu Architect EXE!"))

            digest.reset()
            bais.reset()
            bais.use {
                it.read(digestBuf)
                digest.update(digestBuf)
            }
            val hash = digest.digest().toHexString()
            digest.reset()

            val metaPath = Files.createTempFile("smars_tmp_", "meta.json")
            val metaObj = buildJsonObject {
                put("asset_md5", hash)
                put("exe_name", exeName)
            }
            metaPath.bufferedWriter(StandardCharsets.UTF_8).use {
                it.write(Json.encodeToString(JsonObject.serializer(), metaObj))
            }

            return@withContext Result.success(Release(version, changelog, path, filename, metaPath))
        }
    }
}