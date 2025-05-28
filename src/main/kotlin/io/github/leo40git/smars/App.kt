@file:Suppress("MagicNumber")

package io.github.leo40git.smars

import dev.kord.common.entity.Snowflake
import dev.kordex.core.ExtensibleBot
import dev.kordex.core.utils.env
import io.github.leo40git.smars.extensions.ScraperExtension
import io.github.leo40git.smars.extensions.ShutdownExtension
import java.io.File

private val TOKEN = env("TOKEN")

val GITHUB_APP_ID = env("GITHUB_APP_ID")

val GITHUB_APP_KEY = env("GITHUB_APP_KEY")

val GITHUB_REPO = env("GITHUB_REPO")

val TEST_SERVER_ID = Snowflake(847428895641042944)

val SCRAPE_CHANNEL_ID = Snowflake(836080736939409418)

val LOG_CHANNEL_ID = Snowflake(857240987214544938)

suspend fun main() {
	val bot = ExtensibleBot(TOKEN) {
		extensions {
			add(::ScraperExtension)
		}

		if (devMode) {
			extensions {
				add(::ShutdownExtension)
			}

			// In development mode, load any plugins from `src/main/dist/plugin` if it exists.
			plugins {
				if (File("src/main/dist/plugins").isDirectory) {
					pluginPath("src/main/dist/plugins")
				}
			}
		}
	}

	bot.start()
}
