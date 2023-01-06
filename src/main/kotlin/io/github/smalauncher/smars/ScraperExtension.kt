package io.github.smalauncher.smars

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.message
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.getJumpUrl
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.message.create.embed
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlin.system.exitProcess

class ScraperExtension : Extension() {
    override val name: String = "scraper"

    private val client = HttpClient(CIO)
    private val generator = ReleaseGenerator(client)
    private val uploader = ReleaseUploader(Constants.Env.APP_ID, Constants.Env.APP_KEY,
        Constants.Env.REPO_ORG, Constants.Env.REPO_NAME)

    private lateinit var logChan: GuildMessageChannel

    private fun String.truncate(maxLength: Int): String {
        return if (length > maxLength - 1)
            substring(0 until maxLength - 2) + "…"
        else
            this
    }

    private fun Throwable.messageOrEmpty(prefix: String = ": "): String {
        return if (message == null)
            ""
        else
            prefix + message
    }

    private fun Throwable.stackTraceToCodeBlock(): String {
        return "```\n" + stackTraceToString().truncate(1000) + "```"
    }

    private fun String.stripCodeBlock(): String {
        if (!this.startsWith("```\n") || !this.endsWith("```"))
            return this
        return this.substring(4..this.length - 4)
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

        publicSlashCommand(::ScrapeArgs) {
            name = "scrape"
            description = "Scrapes a rolling release from a specific message."
            guild(Constants.Guilds.ASS)

            check {
                failIf(event.interaction.user.id != Constants.Users.OWNER, "You're not the owner!")
                failIf(event.interaction.channelId != Constants.Channels.CONFIG, "This isn't the config channel!")
            }

            action {
                respond {
                    content = "Gotcha, will now try to scrape release from that message!"
                }
                onMessage(arguments.target, DetectionType.Manual)
            }
        }

        if (Constants.Env.IS_LOCAL) {
            ephemeralSlashCommand {
                name = "shutdown"
                description = "Shuts the bot down."
                guild(Constants.Guilds.ASS)

                check {
                    failIf(event.interaction.user.id != Constants.Users.OWNER, "You're not the owner!")
                    failIf(event.interaction.channelId != Constants.Channels.CONFIG, "This isn't the config channel!")
                }

                action {
                    respond {
                        content = "**okay...** :sob:"
                    }
                    this@ephemeralSlashCommand.kord.shutdown()
                    exitProcess(0)
                }
            }
        }

        logChan = bot.getKoin().get<Kord>().getChannelOf(Constants.Channels.LOG)
            ?: throw RuntimeException("Can't find log channel")
    }

    override suspend fun unload() {
        client.close()
    }

    class ScrapeArgs : Arguments() {
        val target by message {
            name = "target"
            description = "Message to scrape from"
        }
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
                    val uResult = uploader.upload(release)
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
                    System.err.println("Error while uploading new release:")
                    e.printStackTrace()

                    logChan.createMessage {
                        embed {
                            color = Colors.RED
                            title = "**BOO!** Failed to upload new release!!"

                            field {
                                name = "Reason"
                                value = "${e.javaClass.name}${e.messageOrEmpty()}"
                                inline = true
                            }

                            field {
                                name = "Stack trace"
                                value = e.stackTraceToCodeBlock()
                            }

                            footer {
                                text = "Message: " + message.getJumpUrl()
                            }
                        }
                    }
                }
            } else {
                val e = result.exceptionOrNull()
                System.err.println("Error while generating new release:")
                if (e == null) {
                    System.err.println("<unknown>")
                }
                else {
                    e.printStackTrace()
                }

                logChan.createMessage {
                    embed {
                        color = Colors.RED
                        title = "**ACK!** Failed to generate new release!"

                        field {
                            name = "Reason"
                            value = if (e == null) "<unknown>" else "${e.javaClass.name}${e.messageOrEmpty()}"
                            inline = true
                        }

                        if (e != null) {
                            field {
                                name = "Stack trace"
                                value = e.stackTraceToCodeBlock()
                            }
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