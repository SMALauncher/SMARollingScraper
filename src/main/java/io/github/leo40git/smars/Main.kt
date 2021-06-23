package io.github.leo40git.smars

import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
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

suspend fun main() {
    val token = getEnvString("TOKEN", "your Discord bot's token")
    val appId = getEnvString("GH_APP_ID", "your GitHub App's ID")
    val keyPath = getEnvString("GH_APP_KEY", "the path to your GitHub App's PEM file")
    val repo = getEnvString("GH_REPO", "the GitHub repository to upload releases to")

    val client = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = KotlinxSerializer()
        }
    }
    val generator = ReleaseGenerator(client)
    val uploader = ReleaseUploader(appId, keyPath, repo)

    val kord = Kord(token)

    kord.on<MessageCreateEvent> {
        when (message.channel.id) {
            Constants.CHANNEL_CONFIG_ID -> {
                if (message.author?.id != Constants.USER_OWNER_ID)
                    return@on
                if (message.content == "!shutdown") {
                    message.reply(true) { content = "Shutting down..." }
                    kord.shutdown()
                }
            }
            Constants.CHANNEL_ROLLING_ID -> {
                if (message.author?.id != Constants.USER_LIBBIE_ID)
                    return@on
                for (attach in message.attachments) {
                    if (!attach.filename.startsWith("Shang_Mu_Architect_")
                        || !attach.filename.endsWith(".zip"))
                            continue
                    val result = generator.generate(attach.url, attach.filename)
                    if (result.isSuccess) {
                        val release = result.getOrThrow()
                        kord.getChannelOf<TextChannel>(Constants.CHANNEL_LOG_ID)?.createMessage {
                            embed {
                                color = Colors.YELLOW
                                title = "**HMM!** New release generated!"
                                description = "Now uploading v${release.version}..."
                            }
                        }
                        val url = uploader.upload(result.getOrThrow())
                        kord.getChannelOf<TextChannel>(Constants.CHANNEL_LOG_ID)?.createMessage {
                            embed {
                                color = Colors.GREEN
                                title = "**YAY!** New release uploaded!"
                                description = "Successfully uploaded v${release.version}! Look at it [here]($url)!"
                            }
                        }
                    } else {
                        kord.getChannelOf<TextChannel>(Constants.CHANNEL_LOG_ID)?.createMessage {
                            embed {
                                color = Colors.RED
                                title = "**ACK!** Failed to generate new release!"
                                field {
                                    name = "Reason"
                                    value = result.exceptionOrNull()?.message ?: "Unknown"
                                }
                                field {
                                    name = "Stack trace"
                                    value = result.exceptionOrNull()?.stackTraceToCodeBlock() ?: "<null>"
                                }
                            }
                        }
                    }

                    break
                }
            }
            else -> return@on
        }
    }

    kord.login()

    client.close()
}
