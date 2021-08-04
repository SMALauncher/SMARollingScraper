package io.github.smalauncher.smars

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import com.kotlindiscord.kord.extensions.commands.converters.impl.message
import com.kotlindiscord.kord.extensions.commands.parser.Arguments
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.utils.getJumpUrl
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.message.create.MessageCreateBuilder
import dev.kord.rest.builder.message.create.allowedMentions
import dev.kord.rest.builder.message.create.embed
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

fun String.stripCodeBlock(): String {
    if (!this.startsWith("```\n") || !this.endsWith("```"))
        return this
    return this.substring(4..this.length - 4)
}

fun getEnvString(name: String, desc: String): String {
    val value = System.getenv(name) ?: null
    if (value == null) {
        println("Missing environment variable \"$name\"! Please set it to $desc.")
        exitProcess(1)
    }
    return value
}

fun shouldRegisterShutdownCommand(): Boolean {
    val value = System.getenv("SHUTDOWN_COMMAND") ?: null ?: return false
    return value.toBoolean()
}

val token = getEnvString("TOKEN", "your Discord bot's token")
val appId = getEnvString("GH_APP_ID", "your GitHub App's ID")
val appKey = getEnvString("GH_APP_KEY", "your GitHub App's PEM key")
val repoOrg = getEnvString("GH_REPO_ORG", "the organization owning the GitHub repository to upload releases to")
val repoName = getEnvString("GH_REPO_NAME", "the name of the GitHub repository to upload releases to")
val shutdownCommand = shouldRegisterShutdownCommand()

val client = HttpClient(CIO) {
    install(JsonFeature) {
        serializer = KotlinxSerializer()
    }
}
val generator = ReleaseGenerator(client)
val uploader = ReleaseUploader(appId, appKey, repoOrg, repoName)

var bot: ExtensibleBot? = null

suspend fun onMessage(message: Message) {
    for (attach in message.attachments) {
        if (!attach.filename.startsWith("Shang_Mu_Architect_")
            || !attach.filename.endsWith(".zip"))
            continue
        
        val logChan = bot!!.getKoin().get<Kord>().getChannelOf<GuildMessageChannel>(Constants.CHANNEL_LOG_ID)
            ?: throw RuntimeException("Can't find log channel")
        
        logChan.createMessage {
            embed {
                color = Colors.Discord.GREYPLE
                title = "**HUH!** New release detected!"
                footer {
                    text = "Message: " + message.getJumpUrl()
                }
            }
        }

        val result = generator.generate(attach.url, attach.filename, message.content.stripCodeBlock())
        if (result.isSuccess) {
            val release = result.getOrThrow()
            logChan.createMessage {
                embed {
                    color = Colors.YELLOW
                    title = "**HMM!** New release generated!"
                    description = "Now uploading v${release.version}..."
                    footer {
                        text = "Message: " + message.getJumpUrl()
                    }
                }
            }
            val url = uploader.upload(result.getOrThrow())
            logChan.createMessage {
                embed {
                    color = Colors.GREEN
                    title = "**YAY!** New release uploaded!"
                    description = "Successfully uploaded v${release.version}! Look at it [here]($url)!"
                    footer {
                        text = "Message: " + message.getJumpUrl()
                    }
                }
            }
        } else {
            logChan.createMessage {
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

    private fun CheckContext<MessageCreateEvent>.configCheck(event: MessageCreateEvent) {
        if (event.message.author?.id != Constants.USER_OWNER_ID)
            fail("You're not the owner!")
        if (event.message.channelId != Constants.CHANNEL_CONFIG_ID)
            fail("This isn't the config channel!")
    }

    override suspend fun setup() {
        command(::ScrapeArgs) {
            name = "scrape"
            description = "Scrapes a rolling release from a specific message."

            check { configCheck(event) }

            action {
                with(arguments) {
                    onMessage(target)
                }
            }
        }

        if (shutdownCommand) {
            command {
                name = "shutdown"
                aliases = arrayOf("goaway")
                description = "Gracefully shuts down the bot."

                check { configCheck(event) }

                action {
                    message.reply(true) {
                        content = "**okay...** :sob:"
                    }
                    bot.getKoin().get<Kord>().shutdown()
                    exitProcess(0)
                }
            }
        }
    }

    class ScrapeArgs : Arguments() {
        val target by message("target", description = "Message to scrape from")
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
