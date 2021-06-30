package io.github.smalauncher.smars

import io.ktor.http.*
import org.kohsuke.github.GitHubBuilder
import org.kohsuke.github.authorization.OrgAppInstallationAuthorizationProvider
import org.kohsuke.github.extras.authorization.JWTTokenProvider
import java.nio.file.Path
import kotlin.io.path.inputStream

class ReleaseUploader(appId: String, keyPath: String, repoOrg: String, repoName: String) {
    private val repo = "$repoOrg/$repoName"

    private val gh = GitHubBuilder()
        .withAuthorizationProvider(OrgAppInstallationAuthorizationProvider(repoOrg, JWTTokenProvider(appId, Path.of(keyPath))))
        .build()

    fun upload(release: Release): String {
        val ghr = gh.getRepository(repo).createRelease("v${release.version}")
            .name("Shang Mu Architect ${release.version}")
            .body(release.changelog)
            .create()
        release.zip.inputStream().use {
            ghr.uploadAsset(release.zipName, it, ContentType.Application.Zip.toString())
        }
        release.meta.inputStream().use {
            ghr.uploadAsset("meta.json", it, ContentType.Application.Json.toString())
        }
        release.delete()
        return ghr.htmlUrl.toString()
    }
}