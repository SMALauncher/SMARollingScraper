package io.github.smalauncher.smars

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.defaultingBoolean
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
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.utils.io.jvm.javaio.*
import java.io.ByteArrayInputStream
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
                performScrape(event.message, ScrapeMode.Automatic)
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
                performScrape(arguments.target, if (arguments.dry) ScrapeMode.ManualDry else ScrapeMode.Manual)
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

        val dry by defaultingBoolean {
            name = "dry"
            description = "Whether to perform a dry run (without uploading) or not"
            defaultValue = false
        }
    }

    private enum class ScrapeMode(val description: String) {
        Manual("The owner manually told the bot to scrape this message!"),
        ManualDry("The owner manually told the bot to dry-run this message!"),
        Automatic("The bot detected a new message in the rolling releases channel!")
    }

    private suspend fun performScrape(message: Message, mode: ScrapeMode) {
        var gameUrl: String? = null
        var gameFilename: String? = null
        var changelogUrl: String? = null

        for (attach in message.attachments) {
            if (attach.filename.startsWith("Shang_Mu_Architect_")
                && attach.filename.endsWith(".zip", true)) {
                gameUrl = attach.url
                gameFilename = attach.filename
            } else if (attach.filename.startsWith("changelog_", true)
                && attach.filename.endsWith(".txt", true)) {
                changelogUrl = attach.url
            }
        }

        if (gameUrl == null || gameFilename == null) {
            if (mode != ScrapeMode.Automatic) {
                logChan.createMessage {
                    embed {
                        color = Colors.Discord.RED
                        title = "**BAH!** No game ZIP in this message!"
                        description = mode.description
                        field {
                            name = "Message Jump URL"
                            value = message.getJumpUrl()
                        }
                    }
                }
            }

            return
        }

        logChan.createMessage {
            embed {
                color = Colors.Discord.GREYPLE
                title = "**HUH!** New release detected!"
                description = mode.description
                field {
                    name = "Message Jump URL"
                    value = message.getJumpUrl()
                }
            }
        }

        val changelog = if (changelogUrl != null) {
            try {
                val res = client.get(changelogUrl)
                res.bodyAsText(Charsets.UTF_8)
            } catch (e: Exception) {
                System.err.println("Error while getting changelog:")
                e.printStackTrace()

                logChan.createMessage {
                    embed {
                        color = Colors.RED
                        title = "**GAH!** Failed to get changelog!"

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

                return
            }
        } else {
            message.content.stripCodeBlock()
        }

        val result = generator.generate(gameUrl, gameFilename, changelog)
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

            if (mode == ScrapeMode.ManualDry) {
                logChan.createMessage {
                    embed {
                        color = Colors.BLUE
                        title = "**WOO!** New release generated!"
                        description = "Version is v${release.version}, changelog is attached."
                        footer {
                            text = "Message: " + message.getJumpUrl()
                        }
                    }

                    addFile("changelog.txt", ChannelProvider { ByteArrayInputStream(release.changelog.toByteArray()).toByteReadChannel() })
                }
            } else {
                try {
                    val uResult = uploader.upload(release)
                    logChan.createMessage {
                        embed {
                            color = Colors.GREEN
                            title = "**YAY!** New release uploaded!"
                            description =
                                "Successfully uploaded v${release.version}! Look at it [here](${uResult.url})!"
                            if (uResult.replaced) {
                                field {
                                    name = "Old version replaced"
                                    value =
                                        "This version has already been uploaded! Therefore, the old release was replaced."
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
                            title = "**BOO!** Failed to upload new release!"

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
            }

            release.delete()
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
    }
}