package io.github.leo40git.smars.releaser

import dev.kordex.core.i18n.types.Key
import dev.kordex.core.i18n.withContext
import dev.kordex.core.types.TranslatableContext
import io.github.leo40git.smars.i18n.Translations

enum class TargetPlatform(val displayName: Key) {
	Windows(Translations.Platform.windows),
	Linux(Translations.Platform.linux);

	suspend fun translateDisplayName(context: TranslatableContext): String {
		return displayName
			.withContext(context)
			.translate()
	}
}
