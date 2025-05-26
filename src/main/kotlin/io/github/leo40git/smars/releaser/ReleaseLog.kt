package io.github.leo40git.smars.releaser

import dev.kord.core.entity.Attachment
import dev.kord.core.entity.Message

interface ReleaseLog {
	suspend fun logErrorNoArchives(target: Message)

	suspend fun logInfoArchiveList(
		target: Message,
		archiveList: Map<TargetPlatform, List<Attachment>>
	)
}
