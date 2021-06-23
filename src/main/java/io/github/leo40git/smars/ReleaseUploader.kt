package io.github.leo40git.smars

import io.fusionauth.jwt.Signer
import io.fusionauth.jwt.domain.JWT
import io.fusionauth.jwt.rsa.RSASigner
import io.ktor.http.*
import org.kohsuke.github.*
import java.nio.file.Files
import java.nio.file.Paths
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ReleaseUploader(val appId: String, keyPath: String, val repo: String) {
    private val signer: Signer
    private val jwt = JWT()
    private var gh: GitHub

    init {
        signer = RSASigner.newSHA256Signer(String(Files.readAllBytes(Paths.get(keyPath))))

        gh = GitHubBuilder()
            .withConnector(HttpConnector.OFFLINE)
            .build()
        reauth()
    }

    private fun reauth() {
        // step 1: authenticate as app via JWT
        gh = GitHubBuilder()
            .withJwtToken(jwt())
            .build()
        // step 2: get first (and probably only) installation
        val install = gh.app.listInstallations().first()
        // step 3: create token
        val token = install.createToken().permissions(
            mapOf(
                "contents" to GHPermissionType.WRITE
            )
        ).create()
        // step 4: re-authenticate using token
        gh = GitHubBuilder()
            .withAppInstallationToken(token.token)
            .build()
    }

    private fun jwt(): String {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        jwt.setIssuer(appId)
            .setIssuedAt(now.minusSeconds(60))
            .setExpiration(now.plusMinutes(10))
        return JWT.getEncoder().encode(jwt, signer)
    }

    fun upload(release: Release): String {
        if (!gh.isCredentialValid)
            reauth()
        val ghr = gh.getRepository(repo).createRelease("v${release.version}")
            .name("Shang Mu Architect ${release.version}")
            .body("(auto-generated release!)")
            .create()
        release.zip.inputStream().use {
            ghr.uploadAsset(release.zipName, it, ContentType.Application.Zip.toString())
        }
        release.meta.inputStream().use {
            ghr.uploadAsset("meta.json", it, ContentType.Application.Json.toString())
        }
        return ghr.htmlUrl.toString()
    }
}