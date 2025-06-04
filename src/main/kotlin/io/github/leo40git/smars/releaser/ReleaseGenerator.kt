package io.github.leo40git.smars.releaser

import dev.kord.core.entity.Attachment
import dev.kord.core.entity.Message
import dev.kordex.core.sentry.SentryContext
import dev.kordex.core.utils.getJumpUrl
import io.github.leo40git.smars.captureNewEvent
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.sentry.SentryLevel

object ReleaseGenerator {
	suspend fun generate(
		sentry: SentryContext,
		client: HttpClient,
		message: Message
	): ReleaseInfo? {
		sentry.context("Message URL", message.getJumpUrl())

		try {
			val artifacts = findArtifacts(message)

			// TODO figure out how to log messages to Sentry
			//  WITHOUT it counting them as errors
			/*
			if (artifacts.isEmpty()) {
				sentry.captureMessage("No artifacts detected")
				return null
			} else {
				sentry.captureMessage(buildArtifactList(artifacts))
			}
			 */

			val version = extractVersionFromArtifacts(artifacts)

			val changelog: String
			try {
				changelog = fetchChangelog(
					client,
					message
				)
			} catch (e: Exception) {
				sentry.captureNewEvent("Exception occurred while fetching changelog") {
					level = SentryLevel.ERROR
					throwable = e
				}

				return null
			}

			// TODO

			return ReleaseInfo(
				version,
				changelog
			)
		} catch (e: Exception) {
			sentry.captureNewEvent("Unhandled exception occurred while generating release") {
				level = SentryLevel.ERROR
				throwable = e
			}

			return null
		}
	}

	private fun findArtifacts(message: Message): Map<TargetPlatform, List<Attachment>> {
		val result = mutableMapOf<TargetPlatform, MutableList<Attachment>>()

		for (attach in message.attachments) {
			if (attach.filename.endsWith(
					".zip",
					true
				) &&
				attach.filename.startsWith(
					"Shang_Mu_Architect_",
					true
				)
			) {
				result
					.getOrPut(TargetPlatform.Windows) { mutableListOf() }
					.add(attach)
			}

			if ((attach.filename.endsWith(
					"_i386.tar.gz",
					true
				) ||
				attach.filename.endsWith(
					"_i386.deb",
					true
				)) &&
				attach.filename.startsWith(
					"shang-mu-architect_",
					true
				)
			) {
				result
					.getOrPut(TargetPlatform.Linux) { mutableListOf() }
					.add(attach)
			}
		}

		return result
	}

	private fun extractVersionFromArtifacts(
		artifacts: Map<TargetPlatform, List<Attachment>>
	): String {
		var attach = artifacts[TargetPlatform.Windows]?.first()
		if (attach != null) {
			val lastUnderscoreIndex = attach.filename.lastIndexOf('_')
			val lastDotIndex = attach.filename.lastIndexOf('.')
			return attach.filename
				.substring((lastUnderscoreIndex + 1)..(lastDotIndex - 1))
				.replace('-', '.')
		}

		attach = artifacts[TargetPlatform.Linux]?.first()
		if (attach != null) {
			val lastUnderscoreIndex = attach.filename.lastIndexOf('_')
			val lastDotIndex = attach.filename.lastIndexOf('.')
			return attach.filename
				.substring((lastUnderscoreIndex + 1)..(lastDotIndex - 1))
		}

		throw UnsupportedOperationException("Should be unreachable...")
	}

	private fun buildArtifactList(
		artifacts: Map<TargetPlatform, List<Attachment>>
	): String {
		val builder = StringBuilder("Detected artifacts for the following platforms:")
			.appendLine()

		for (entry in artifacts) {
			builder.append(" - ", entry.key.name)

			for (attach in entry.value) {
				builder
					.appendLine()
					.append("     - ", attach.filename)
			}
		}

		return builder.toString()
	}

	private suspend fun fetchChangelog(
		client: HttpClient,
		message: Message
	): String {
		var changelogUrl: String? = null

		for (attach in message.attachments) {
			if (attach.filename.endsWith(
					".txt",
					true
				) &&
				attach.filename.startsWith(
					"changelog_",
					true
				)
			) {
				changelogUrl = attach.url
				break
			}
		}

		return if (changelogUrl == null) {
			// changelog is in message contents, probably inside a code block
			var text = message.content
			val codeBlockStartIndex = text.indexOf("```");
			if (codeBlockStartIndex >= 0) {
				var changelogStartIndex = codeBlockStartIndex + 3
				if (text[changelogStartIndex] == '\n')
					changelogStartIndex++

				val codeBlockEndIndex = text.indexOf("```", changelogStartIndex)
				if (codeBlockEndIndex >= 0)
					text = text.substring(changelogStartIndex..(codeBlockEndIndex - 1))
			}
			text
		} else {
			// changelog is in attached file
			val res = client.get(changelogUrl)
			res.bodyAsText(Charsets.UTF_8)
		}
	}
}
