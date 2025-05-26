package io.github.leo40git.smars.releaser

import dev.kord.common.Color
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.Attachment
import dev.kord.core.entity.Message
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.embed
import dev.kordex.core.DISCORD_BLURPLE
import dev.kordex.core.DISCORD_RED
import dev.kordex.core.DISCORD_YELLOW
import dev.kordex.core.i18n.types.Key
import dev.kordex.core.i18n.withContext
import dev.kordex.core.types.TranslatableContext
import dev.kordex.core.utils.getJumpUrl
import io.github.leo40git.smars.addFile
import io.github.leo40git.smars.i18n.Translations

class ChannelReleaseLog(
	val context: TranslatableContext,
	val channel: MessageChannelBehavior
) : ReleaseLog {
	override suspend fun logErrorNoArchives(message: Message) {
		createLogEmbed(
			message,
			LogLevel.Error,
			Translations.Log.Error.noArchives
		)
	}

	override suspend fun logInfoArchiveList(
		message: Message,
		archives: Map<TargetPlatform, List<Attachment>>
	) {
		val descriptionBuilder = StringBuilder(
			Translations.Log.Info.ArchiveList.header
				.withContext(context)
				.translate()
		)
			.appendLine()

		for (entry in archives) {
			descriptionBuilder
				.append(
					"- ",
					entry.key.translateDisplayName(context)
				)

			for (attach in entry.value) {
				descriptionBuilder
					.appendLine()
					.append(
						"    - `",
						attach.filename,
						"`"
					)
			}
		}

		createLogEmbed(
			message,
			LogLevel.Info,
			descriptionBuilder.toString()
		)
	}

	override suspend fun logErrorFetchChangelogException(
		message: Message,
		exception: Exception
	) {
		createExceptionLogEmbed(
			message,
			LogLevel.Error,
			Translations.Log.Error.fetchChangelogException,
			exception
		)
	}

	override suspend fun logErrorUncaughtException(
		message: Message,
		exception: Exception
	) {
		createExceptionLogEmbed(
			message,
			LogLevel.Error,
			Translations.Log.Error.uncaughtException,
			exception
		)
	}

	private suspend inline fun configureLogEmbed(
		builder: EmbedBuilder,
		message: Message,
		level: LogLevel
	) {
		with (builder) {
			color = level.color
			title = level.title
				.withContext(context)
				.translate()

			field {
				name = Translations.Log.footerMessageUrl
					.withContext(context)
					.translate()
				value = message.getJumpUrl()
			}
		}
	}

	private suspend inline fun createLogEmbed(
		message: Message,
		level: LogLevel,
		description: String
	) {
		channel.createEmbed {
			configureLogEmbed(
				this,
				message,
				level
			)

			this.description = description
		}
	}

	private suspend inline fun createLogEmbed(
		message: Message,
		level: LogLevel,
		description: Key,
		vararg descriptionReplacements: Pair<String, Any?>
	) {
		createLogEmbed(
			message,
			level,
			description
				.withContext(context)
				.translateNamed(descriptionReplacements.toMap())
		)
	}

	private suspend inline fun createExceptionLogEmbed(
		message: Message,
		level: LogLevel,
		description: Key,
		exception: Exception
	) {
		val stackTraceBytes = exception
			.toString()
			.toByteArray(Charsets.UTF_8)

		channel.createMessage {
			embed {
				configureLogEmbed(
					this,
					message,
					level
				)

				this.description = description
					.withContext(context)
					.translate()
			}

			addFile("stacktrace.txt", stackTraceBytes)
		}
	}

	private enum class LogLevel(val title: Key, val color: Color) {
		Info(Translations.Log.Title.info, DISCORD_BLURPLE),
		Warn(Translations.Log.Title.warn, DISCORD_YELLOW),
		Error(Translations.Log.Title.error, DISCORD_RED)
	}
}
