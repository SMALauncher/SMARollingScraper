package io.github.smalauncher.smars

import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import com.kotlindiscord.kord.extensions.commands.converters.impl.message
import com.kotlindiscord.kord.extensions.commands.parser.Arguments
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.utils.getJumpUrl
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.message.create.embed
import io.ktor.client.*
import io.ktor.client.engine.cio.*

class ScraperExtension : Extension() {
    override val name: String = "scraper"

    private val client = HttpClient(CIO)
    private val generator = ReleaseGenerator(client)
    private val uploader = ReleaseUploader(Constants.Env.APP_ID, Constants.Env.APP_KEY,
        Constants.Env.REPO_ORG, Constants.Env.REPO_NAME)

    private fun Throwable.stackTraceToCodeBlock(): String {
        return "```\n" + stackTraceToString() + "```"
    }

    private fun String.stripCodeBlock(): String {
        if (!this.startsWith("```\n") || !this.endsWith("```"))
            return this
        return this.substring(4..this.length - 4)
    }

    private fun CheckContext<MessageCreateEvent>.configCheck(event: MessageCreateEvent) {
        failIf(event.message.author?.id != Constants.Users.OWNER, "You're not the owner!")
        failIf(event.message.channelId != Constants.Channels.CONFIG, "This isn't the config channel!")
    }

    override suspend fun setup() {
        event<MessageCreateEvent> {
            check {
                failIf(event.message.channelId != Constants.Channels.ROLLING || event.message.author?.id != Constants.Users.LIBBIE)
            }

            action {
                onMessage(event.message, DetectionType.Automatic)
            }
        }

        command(::ScrapeArgs) {
            name = "scrape"
            description = "Scrapes a rolling release from a specific message."

            check { configCheck(event) }

            action {
                with(arguments) {
                    onMessage(target, DetectionType.Manual)
                }
            }
        }
    }

    override suspend fun unload() {
        client.close()
    }

    class ScrapeArgs : Arguments() {
        val target by message("target", description = "Message to scrape from")
    }

    private enum class DetectionType(val description: String) {
        Manual("The owner manually told the bot to scrape this message!"),
        Automatic("The bot detected a new message in the rolling releases channel!")
    }

    private suspend fun onMessage(message: Message, detectionType: DetectionType) {
        for (attach in message.attachments) {
            if (!attach.filename.startsWith("Shang_Mu_Architect_")
                || !attach.filename.endsWith(".zip"))
                continue

            val logChan = bot.getKoin().get<Kord>().getChannelOf<GuildMessageChannel>(Constants.Channels.LOG)
                ?: throw RuntimeException("Can't find log channel")

            logChan.createMessage {
                embed {
                    color = Colors.Discord.GREYPLE
                    title = "**HUH!** New release detected!"
                    description = detectionType.description
                    field {
                        name = "Message Jump URL"
                        value = message.getJumpUrl()
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
                try {
                    val uResult = uploader.upload(result.getOrThrow())
                    logChan.createMessage {
                        embed {
                            color = Colors.GREEN
                            title = "**YAY!** New release uploaded!"
                            description = "Successfully uploaded v${release.version}! Look at it [here](${uResult.url})!"
                            if (uResult.replaced) {
                                field {
                                    name = "Old version replaced"
                                    value = "This version has already been uploaded! Therefore, the old release was replaced."
                                }
                            }
                            footer {
                                text = "Message: " + message.getJumpUrl()
                            }
                        }
                    }
                } catch (e: Exception) {
                    logChan.createMessage {
                        embed {
                            color = Colors.RED
                            title = "**BOO!** Failed to upload new release!!"
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
                                text = "Message: " + message.getJumpUrl()
                            }
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
}