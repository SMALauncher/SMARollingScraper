package io.github.leo40git.smars.extensions

import dev.kord.common.Color
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.entity.Attachment
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kordex.core.DISCORD_BLURPLE
import dev.kordex.core.DISCORD_RED
import dev.kordex.core.DISCORD_YELLOW
import dev.kordex.core.checks.isBotOwner
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.boolean
import dev.kordex.core.commands.converters.impl.message
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.types.Key
import dev.kordex.core.i18n.withContext
import dev.kordex.core.types.TranslatableContext
import dev.kordex.core.utils.getJumpUrl
import io.github.leo40git.smars.LOG_CHANNEL_ID
import io.github.leo40git.smars.SCRAPE_CHANNEL_ID
import io.github.leo40git.smars.TEST_SERVER_ID
import io.github.leo40git.smars.i18n.Translations
import io.github.leo40git.smars.releaser.ChannelReleaseLog
import io.github.leo40git.smars.releaser.ReleaseGenerator

class ScraperExtension : Extension() {
	override val name = "scraper"

	private lateinit var logChannel: MessageChannelBehavior

	override suspend fun setup() {
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
		target: Message,
		dryRun: Boolean = false
	) {
		ReleaseGenerator.generateFrom(
			ChannelReleaseLog(
				context,
				logChannel
			),
			target
		)
	}
}
