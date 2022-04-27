package io.github.smalauncher.smars.test

import io.github.smalauncher.smars.ReleaseGenerator
import kotlin.system.exitProcess

fun main() {
    val original = """
        # Changelog
        -Item
          -Sub item 1
          -Sub item 2
            -Sub sub item?!
    """.trimIndent()
    val expected = """
        # Changelog
        - Item
          - Sub item 1
          - Sub item 2
            - Sub sub item?!
    """.trimIndent()
    val actual = ReleaseGenerator.fixChangelogFormatting(original)
    if (expected != actual) {
        System.err.println("fixChangelogFormatting returned unexpected result!")
        System.err.println("Expected:")
        System.err.println(expected)
        System.err.println()
        System.err.println("Actual:")
        System.err.println(actual);
        exitProcess(1)
    } else {
        println(actual)
    }
}