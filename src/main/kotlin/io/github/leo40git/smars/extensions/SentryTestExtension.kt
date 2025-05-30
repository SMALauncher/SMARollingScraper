package io.github.leo40git.smars.extensions

import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import io.github.leo40git.smars.TEST_SERVER_ID
import io.github.leo40git.smars.i18n.Translations

class SentryTestExtension : Extension() {
	override val name: String = "sentry_test"

	override suspend fun setup() {
		publicSlashCommand {
			name = Translations.Commands.SentryTest.name
			description = Translations.Commands.SentryTest.description
			guild(TEST_SERVER_ID)

			action {
				try {
					throw RuntimeException("This is a test exception.")
				} catch (e: RuntimeException) {
					sentry.captureThrowable(e)
				}

				respond {
					content = ":ok_hand:"
				}
			}
		}
	}
}
