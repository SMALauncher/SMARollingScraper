package io.github.leo40git.smars

import java.io.File

data class Release(val version: String, val zip: File, val zipName: String, val meta: File)
