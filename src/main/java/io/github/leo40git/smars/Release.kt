package io.github.leo40git.smars

import java.nio.file.Files
import java.nio.file.Path

data class Release(val version: String, val changelog: String, val zip: Path, val zipName: String, val meta: Path) {
    fun delete() {
        Files.delete(zip)
        Files.delete(meta)
    }
}
