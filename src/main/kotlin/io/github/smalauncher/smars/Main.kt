package io.github.smalauncher.smars

import com.kotlindiscord.kord.extensions.ExtensibleBot

suspend fun main() {
    val bot = ExtensibleBot(Constants.Env.TOKEN) {
        extensions {
            add(::ScraperExtension)
        }
    }

    bot.start()
}
