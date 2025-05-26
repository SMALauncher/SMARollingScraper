package io.github.leo40git.smars.releaser

import dev.kord.core.entity.Attachment
import dev.kord.core.entity.Message

interface ReleaseLog {
	suspend fun logErrorNoArchives(message: Message)

	suspend fun logInfoArchiveList(
		message: Message,
		archives: Map<TargetPlatform, List<Attachment>>
	)

	suspend fun logErrorFetchChangelogException(
		message: Message,
		exception: Exception
	)

	suspend fun logErrorUncaughtException(
		message: Message,
		exception: Exception
	)
}
