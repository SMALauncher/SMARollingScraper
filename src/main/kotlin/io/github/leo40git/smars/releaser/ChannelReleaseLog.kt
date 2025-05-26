package io.github.leo40git.smars.releaser

import dev.kord.common.Color
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.entity.Attachment
import dev.kord.core.entity.Message
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kordex.core.DISCORD_BLURPLE
import dev.kordex.core.DISCORD_RED
import dev.kordex.core.DISCORD_YELLOW
import dev.kordex.core.i18n.types.Key
import dev.kordex.core.i18n.withContext
import dev.kordex.core.types.TranslatableContext
import dev.kordex.core.utils.getJumpUrl
import io.github.leo40git.smars.i18n.Translations

class ChannelReleaseLog(
	val context: TranslatableContext,
	val channel: MessageChannelBehavior
) : ReleaseLog {
	override suspend fun logErrorNoArchives(target: Message) {
		createLogEmbed(
			target,
			LogLevel.Error,
			Translations.Log.Error.noArchives
		)
	}

	override suspend fun logInfoArchiveList(
		target: Message,
		archiveList: Map<TargetPlatform, List<Attachment>>
	) {
		val archiveListBuilder = StringBuilder(
			Translations.Log.Info.ArchiveList.header
				.withContext(context)
				.translate()
		)
			.appendLine()

		for (entry in archiveList) {
			archiveListBuilder
				.append(
					"- ",
					entry.key.translateDisplayName(context)
				)

			for (attach in entry.value) {
				archiveListBuilder
					.appendLine()
					.append(
						"    - `",
						attach.filename,
						"`"
					)
			}
		}

		createLogEmbed(
			target,
			LogLevel.Info) {
			description = archiveListBuilder.toString()
		}
	}

	private suspend inline fun createLogEmbed(
		target: Message,
		level: LogLevel,
		block: EmbedBuilder.() -> Unit
	) {
		channel.createEmbed {
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
		target: Message,
		level: LogLevel,
		message: Key,
		vararg messageReplacements: Pair<String, Any?>
	) {
		val messageString = message
			.withContext(context)
			.translateNamed(messageReplacements.toMap())

		createLogEmbed(target, level) {
			description = messageString
		}
	}

	private enum class LogLevel(val title: Key, val color: Color) {
		Info(Translations.Log.Title.info, DISCORD_BLURPLE),
		Warn(Translations.Log.Title.warn, DISCORD_YELLOW),
		Error(Translations.Log.Title.error, DISCORD_RED)
	}
}
