package io.github.leo40git.smars

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.commands.converters.impl.message
import com.kotlindiscord.kord.extensions.commands.parser.Arguments
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.utils.getJumpUrl
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.message.MessageCreateBuilder
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import kotlin.system.exitProcess

suspend fun Message.reply(mention: Boolean = false, builder: MessageCreateBuilder.() -> Unit): Message {
    return this.reply {
        allowedMentions {
            repliedUser = mention
        }

        builder()
    }
}

fun Throwable.stackTraceToCodeBlock(): String {
    return "```\n" + stackTraceToString() + "```"
}

fun getEnvString(name: String, desc: String): String {
    val value = System.getenv(name) ?: null
    if (value == null) {
        println("Missing environment variable \"$name\"! Please set it to $desc.")
        exitProcess(1)
    }
    return value
}

val token = getEnvString("TOKEN", "your Discord bot's token")
val appId = getEnvString("GH_APP_ID", "your GitHub App's ID")
val keyPath = getEnvString("GH_APP_KEY", "the path to your GitHub App's PEM file")
val repoOrg = getEnvString("GH_REPO_ORG", "the organization owning the GitHub repository to upload releases to")
val repoName = getEnvString("GH_REPO_NAME", "the name of the GitHub repository to upload releases to")

val client = HttpClient(CIO) {
    install(JsonFeature) {
        serializer = KotlinxSerializer()
    }
}
val generator = ReleaseGenerator(client)
val uploader = ReleaseUploader(appId, keyPath, repoOrg, repoName)

var bot: ExtensibleBot? = null

suspend fun onMessage(message: Message, commandMessage: Message? = null) {
    if (message.author?.id != Constants.USER_LIBBIE_ID) {
        commandMessage?.reply(true) { content = "Message <${message.getJumpUrl()}>: Author isn't Libbie!" }
        return
    }
    for (attach in message.attachments) {
        if (!attach.filename.startsWith("Shang_Mu_Architect_")
            || !attach.filename.endsWith(".zip"))
            continue
        val result = generator.generate(attach.url, attach.filename)
        if (result.isSuccess) {
            val release = result.getOrThrow()
            bot!!.getKoin().get<Kord>().getChannelOf<TextChannel>(Constants.CHANNEL_LOG_ID)?.createMessage {
                embed {
                    color = Colors.YELLOW
                    title = "**HMM!** New release generated!"
                    description = "Now uploading v${release.version}..."
                    footer {
                        text = "Message:" + message.getJumpUrl()
                    }
                }
            }
            val url = uploader.upload(result.getOrThrow())
            bot!!.getKoin().get<Kord>().getChannelOf<TextChannel>(Constants.CHANNEL_LOG_ID)?.createMessage {
                embed {
                    color = Colors.GREEN
                    title = "**YAY!** New release uploaded!"
                    description = "Successfully uploaded v${release.version}! Look at it [here]($url)!"
                    footer {
                        text = "Message:" + message.getJumpUrl()
                    }
                }
            }
        } else {
            bot!!.getKoin().get<Kord>().getChannelOf<TextChannel>(Constants.CHANNEL_LOG_ID)?.createMessage {
                embed {
                    color = Colors.RED
                    title = "**ACK!** Failed to generate new release!"
                    field {
                        name = "Reason"
                        value = result.exceptionOrNull()?.message ?: "Unknown"
                        inline = true
                    }
                    field {
                        name = "Stack trace"
                        value = result.exceptionOrNull()?.stackTraceToCodeBlock() ?: "<null>"
                    }
                    footer {
                        text = "Message:" + message.getJumpUrl()
                    }
                }
            }
        }

        break
    }
}

class ScraperExtension : Extension() {
    override val name: String = "scraper"

    private fun configCheck(event: MessageCreateEvent): Boolean {
        return (event.message.channelId == Constants.CHANNEL_CONFIG_ID
                && event.message.author?.id == Constants.USER_OWNER_ID)
    }

    override suspend fun setup() {
        command(::ScrapeArgs) {
            name = "scrape"
            description = "Scrapes a rolling release from a specific message."

            check { event -> configCheck(event) }

            action {
                with(arguments) {
                    onMessage(target, message)
                }
            }
        }

        command {
            name = "shutdown"
            description = "Gracefully shuts down the bot."

            check { event -> configCheck(event) }

            action {
                message.reply(true) {
                    content = "**Shutting down...**"
                }
                bot.getKoin().get<Kord>().shutdown()
                exitProcess(0)
            }
        }
    }

    class ScrapeArgs : Arguments() {
        val target by message("target", description = "Message to scrape from",
            requireGuild = true, requiredGuild = suspend { Constants.CHANNEL_ROLLING_ID })
    }
}

suspend fun main() {
    bot = ExtensibleBot(token) {
        messageCommands {
            defaultPrefix = "!"
        }
        extensions {
            add(::ScraperExtension)
        }
    }

    bot!!.on<MessageCreateEvent> {
        if (message.channelId == Constants.CHANNEL_ROLLING_ID && message.author?.id == Constants.USER_LIBBIE_ID)
            onMessage(message)
    }

    bot!!.start()

    client.close()
}
