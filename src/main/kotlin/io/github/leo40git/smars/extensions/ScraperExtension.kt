package io.github.leo40git.smars.extensions

import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kordex.core.checks.isBotOwner
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.boolean
import dev.kordex.core.commands.converters.impl.message
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.withContext
import dev.kordex.core.types.TranslatableContext
import io.github.leo40git.smars.LOG_CHANNEL_ID
import io.github.leo40git.smars.SCRAPE_CHANNEL_ID
import io.github.leo40git.smars.TEST_SERVER_ID
import io.github.leo40git.smars.i18n.Translations
import io.github.leo40git.smars.releaser.ChannelReleaseLog
import io.github.leo40git.smars.releaser.ReleaseGenerator
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

class ScraperExtension : Extension() {
	override val name = "scraper"

	private val client = HttpClient(OkHttp)
	private lateinit var logChannel: MessageChannelBehavior

	override suspend fun setup() {
		kord.rest

		logChannel = kord.getChannelOf<MessageChannel>(LOG_CHANNEL_ID)
			?: error("Can't find log channel with ID '${LOG_CHANNEL_ID}.")

		event<MessageCreateEvent> {
			check {
				failIf { event.message.channelId != SCRAPE_CHANNEL_ID }
			}

			action {
				doScrape(this@action, event.message)
			}
		}

		publicSlashCommand(::ScrapeArgs) {
			name = Translations.Commands.Scrape.name
			description = Translations.Commands.Scrape.description
			guild(TEST_SERVER_ID)

			check {
				isBotOwner()
			}

			action {
				respond {
					content = Translations.Commands.Scrape.response
						.withContext(this@action)
						.translate()
				}

				doScrape(this@action, arguments.target, arguments.dryRun)
			}
		}
	}

	override suspend fun unload() {
		client.close()
	}

	private class ScrapeArgs : Arguments() {
		val target by message {
			name = Translations.Arguments.Target.name
			description = Translations.Arguments.Target.description
		}

		val dryRun by boolean {
			name = Translations.Arguments.DryRun.name
			description = Translations.Arguments.DryRun.description
		}
	}

	private suspend fun doScrape(
		context: TranslatableContext,
		message: Message,
		dryRun: Boolean = false
	) {
		val log = ChannelReleaseLog(
			context,
			logChannel
		)

		try {
			val release = ReleaseGenerator.generate(
				client,
				message,
				log
			)

			if (release == null)
				return

			// TODO
		} catch (e: Exception) {
			log.logErrorUncaughtException(
				message,
				e
			)
		}
	}
}
