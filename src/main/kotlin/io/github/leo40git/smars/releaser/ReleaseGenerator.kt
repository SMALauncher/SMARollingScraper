package io.github.leo40git.smars.releaser

import dev.kord.core.entity.Attachment
import dev.kord.core.entity.Message

object ReleaseGenerator {
	suspend fun generateFrom(
		log: ReleaseLog,
		target: Message
	) {
		val archives = detectArchives(target)

		if (archives.isEmpty())
			log.logErrorNoArchives(target)
		else
			log.logInfoArchiveList(target, archives)

		// TODO
	}

	private fun detectArchives(target: Message): Map<TargetPlatform, List<Attachment>> {
		val result = mutableMapOf<TargetPlatform, MutableList<Attachment>>()

		for (attach in target.attachments) {
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
}
