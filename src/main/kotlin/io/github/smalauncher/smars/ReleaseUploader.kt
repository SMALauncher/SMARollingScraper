package io.github.smalauncher.smars

import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kohsuke.github.GitHubBuilder
import org.kohsuke.github.authorization.OrgAppInstallationAuthorizationProvider
import org.kohsuke.github.extras.authorization.JWTTokenProvider
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import kotlin.io.path.inputStream

class ReleaseUploader(appId: String, appKey: String, repoOrg: String, repoName: String) {
    private val repoPath = "$repoOrg/$repoName"

    private fun decodePrivateKey(key: String): PrivateKey {
        if (key.contains(" RSA ")) {
            throw InvalidKeySpecException(
                "Private key must be a PKCS#8 formatted string, to convert it from PKCS#1 use: "
                        + "openssl pkcs8 -topk8 -inform PEM -outform PEM -in current-key.pem -out new-key.pem -nocrypt"
            )
        }
        if (key.contains("-----BEGIN PRIVATE KEY-----") || key.contains("-----END PRIVATE KEY-----") || key.contains("\n")) {
            throw InvalidKeySpecException(
                "Private key must be only the key data itself, without any comments or whitespace"
            )
        }

        val kf = KeyFactory.getInstance("RSA")

        try {
            val decoded = Base64.getDecoder().decode(key)
            val spec = PKCS8EncodedKeySpec(decoded)
            return kf.generatePrivate(spec)
        } catch (e: Exception) {
            throw InvalidKeySpecException("Failed to decode private key", e)
        }
    }

    private val gh = GitHubBuilder()
        .withAuthorizationProvider(OrgAppInstallationAuthorizationProvider(repoOrg, JWTTokenProvider(appId, decodePrivateKey(appKey))))
        .build()

    data class Result(val url: String, val replaced: Boolean)

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun upload(release: Release): Result {
        return withContext(Dispatchers.IO) {
            var replaced = false

            val repo = gh.getRepository(repoPath)

            var ghr = repo.getReleaseByTagName("v${release.version}")
            if (ghr != null) {
                // try to delete the tag as well
                repo.getRef("tags/${ghr.tagName}")?.delete()
                ghr.delete()
                replaced = true
            }

            ghr = repo.createRelease("v${release.version}")
                .name("Shang Mu Architect ${release.version}")
                .body(release.changelog)
                .create()

            release.zip.inputStream().use {
                ghr.uploadAsset(release.zipName, it, ContentType.Application.Zip.toString())
            }
            val metaStream = ByteArrayInputStream(release.metaContents.toByteArray(Charsets.UTF_8))
            ghr.uploadAsset("meta.json", metaStream, ContentType.Application.Json.toString())

            release.delete()
            return@withContext Result(ghr.htmlUrl.toString(), replaced)
        }
    }
}