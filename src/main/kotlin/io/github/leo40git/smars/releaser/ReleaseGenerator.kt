package io.github.leo40git.smars.releaser

import dev.kord.core.entity.Attachment
import dev.kord.core.entity.Message
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

object ReleaseGenerator {
	suspend fun generate(
		client: HttpClient,
		message: Message,
		log: ReleaseLog
	): ReleaseInfo? {
		val archives = findArchives(message)

		if (archives.isEmpty()) {
			log.logErrorNoArchives(message)
			return null
		} else
			log.logInfoArchiveList(message, archives)

		val version = extractVersionFromArchives(archives)

		val changelog: String
		try {
			changelog = fetchChangelog(
				client,
				message
			)
		} catch (e: Exception) {
			log.logErrorFetchChangelogException(
				message,
				e
			)
			return null
		}

		// TODO

		return ReleaseInfo(
			version,
			changelog
		)
	}

	private fun findArchives(message: Message): Map<TargetPlatform, List<Attachment>> {
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

	private fun extractVersionFromArchives(
		archives: Map<TargetPlatform, List<Attachment>>
	): String {
		var attach = archives[TargetPlatform.Windows]?.first()
		if (attach != null) {
			val lastUnderscoreIndex = attach.filename.lastIndexOf('_')
			val lastDotIndex = attach.filename.lastIndexOf('.')
			return attach.filename
				.substring((lastUnderscoreIndex + 1)..(lastDotIndex - 1))
				.replace('-', '.')
		}

		attach = archives[TargetPlatform.Linux]?.first()
		if (attach != null) {
			val lastUnderscoreIndex = attach.filename.lastIndexOf('_')
			val lastDotIndex = attach.filename.lastIndexOf('.')
			return attach.filename
				.substring((lastUnderscoreIndex + 1)..(lastDotIndex - 1))
		}

		throw UnsupportedOperationException("Should be unreachable...")
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
