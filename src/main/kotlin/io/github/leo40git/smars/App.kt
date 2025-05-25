package io.github.leo40git.smars

import dev.kordex.core.ExtensibleBot
import dev.kordex.core.utils.env
import io.github.leo40git.smars.extensions.ScraperExtension
import io.github.leo40git.smars.extensions.ShutdownExtension

private val TOKEN = env("TOKEN")

suspend fun main() {
	val bot = ExtensibleBot(TOKEN) {
		extensions {
			add(::ScraperExtension)

			if (this@ExtensibleBot.devMode)
				add(::ShutdownExtension)
		}
	}

	bot.start()
}
