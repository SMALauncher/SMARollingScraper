package io.github.leo40git.smars

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
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.zip.ZipInputStream

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
        return joinToString { it.toString(16).uppercase(Locale.ROOT) }
    }

    suspend fun generate(url: String, filename: String, changelog: String): Result<Release> {
        val res: HttpResponse = client.get(url)

        val lastUnderscore = filename.lastIndexOf('_')
        val lastDot = filename.lastIndexOf('.')
        val version = filename.substring(lastUnderscore + 1, lastDot).replace('-', '.')

        @Suppress("BlockingMethodInNonBlockingContext") // theoretically, blocking here should be fine
        return withContext(Dispatchers.IO) {
            val file = File.createTempFile("smars_tmp_", filename)
            val responseBody: ByteArray = res.receive()
            file.writeBytes(responseBody)

            var exeName = ""
            val zis = ZipInputStream(FileInputStream(file))
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
            val fis = FileInputStream(file)
            fis.use {
                it.read(digestBuf)
                digest.update(digestBuf)
            }
            val hash = digest.digest().toHexString()
            digest.reset()

            val metaFile = File.createTempFile("smars_tmp_", "meta.json")
            val metaObj = buildJsonObject {
                put("asset_md5", hash)
                put("exe_name", exeName)
            }
            metaFile.bufferedWriter(StandardCharsets.UTF_8).use {
                it.write(Json.encodeToString(JsonObject.serializer(), metaObj))
            }

            return@withContext Result.success(Release(version, changelog, file, filename, metaFile))
        }
    }
}