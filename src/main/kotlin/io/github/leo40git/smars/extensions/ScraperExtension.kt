package io.github.leo40git.smars.extensions

import dev.kord.common.Color
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.entity.Attachment
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.message.EmbedBuilder
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
import io.github.leo40git.smars.Colors
import io.github.leo40git.smars.Constants
import io.github.leo40git.smars.i18n.Translations

class ScraperExtension : Extension() {
	override val name = "scraper"

	private lateinit var logChannel: GuildMessageChannel

	override suspend fun setup() {
		logChannel = kord.getChannelOf<GuildMessageChannel>(Constants.LOG_CHANNEL_ID)
			?: error("Can't find log channel with ID '${Constants.LOG_CHANNEL_ID}.")

		event<MessageCreateEvent> {
			check {
				failIf { event.message.channelId != Constants.SCRAPE_CHANNEL_ID }
			}

			action {
				doScrape(this@action, event.message)
			}
		}

		publicSlashCommand(::ScrapeArgs) {
			name = Translations.Commands.Scrape.name
			description = Translations.Commands.Scrape.description
			guild(Constants.TEST_SERVER_ID)

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
		val archiveAttachments = mutableMapOf<Platform, Attachment>()
		var changelogAttachment: Attachment? = null

		for (attachment in target.attachments) {
			if (attachment.filename.endsWith(
					".zip",
					true
				) &&
				attachment.filename.startsWith(
					"Shang_Mu_Architect_",
					true
				)
			) {
				if (archiveAttachments.put(Platform.Windows, attachment) != null) {
					createLogEmbed(
						context,
						target,
						LogLevel.Error,
						Translations.Log.Error.multipleArchives,
						"platform" to Platform.Windows.displayName
					)
					return
				}

				continue
			}

			if (attachment.filename.endsWith(
					"_i386.tar.gz",
					true
				) &&
				attachment.filename.startsWith(
					"shang-mu-architect_",
					true
				)
			) {
				if (archiveAttachments.put(Platform.Linux, attachment) != null) {
					createLogEmbed(
						context,
						target,
						LogLevel.Error,
						Translations.Log.Error.multipleArchives,
						"platform" to Platform.Linux.displayName
					)
					return
				}

				continue
			}

			if (
				attachment.filename.endsWith(
					".txt",
					true
				) &&
				attachment.filename.startsWith(
					"changelog_",
					true
				)
			) {
				changelogAttachment = attachment
				continue
			}
		}

		if (archiveAttachments.isEmpty()) {
			createLogEmbed(
				context,
				target,
				LogLevel.Error,
				Translations.Log.Error.noArchives
			)
			return
		} else {
			val archiveListBuilder = StringBuilder(
				Translations.Log.Info.ArchiveList.header
					.withContext(context)
					.translate()
			)
			for (entry in archiveAttachments) {
				archiveListBuilder
					.appendLine()
					.append(
						Translations.Log.Info.ArchiveList.item
							.withContext(context)
							.translateNamed(
								"platform" to entry.key.displayName,
								"filename" to entry.value.filename
							)
					)
			}

			createLogEmbed(
				context,
				target,
				LogLevel.Info) {
				description = archiveListBuilder.toString()
			}
		}

		TODO("Release generation not yet implemented")
	}

	private enum class Platform(val displayName: Key) {
		Windows(Translations.Platform.windows),
		Linux(Translations.Platform.linux);

		suspend fun translateDisplayName(context: TranslatableContext): String {
			return displayName
				.withContext(context)
				.translate()
		}
	}

	private suspend inline fun createLogEmbed(
		context: TranslatableContext,
		target: Message,
		level: LogLevel,
		block: EmbedBuilder.() -> Unit
	) {
		logChannel.createEmbed {
			color = level.color
			title = level.title
				.withContext(context)
				.translate()

			field {
				name = Translations.Log.footerMessageUrl
					.withContext(context)
					.translate()
				value = target.getJumpUrl()
			}

			apply(block)
		}
	}

	private suspend inline fun createLogEmbed(
		context: TranslatableContext,
		target: Message,
		level: LogLevel,
		message: Key,
		vararg messageReplacements: Pair<String, Any?>
	) {
		val messageString = message
			.withContext(context)
			.translateNamed(messageReplacements.toMap())

		createLogEmbed(context, target, level) {
			description = messageString
		}
	}

	private enum class LogLevel(val title: Key, val color: Color) {
		Info(Translations.Log.Title.info, Colors.Discord.BLURPLE),
		Warn(Translations.Log.Title.warn, Colors.YELLOW),
		Error(Translations.Log.Title.error, Colors.RED)
	}
}
