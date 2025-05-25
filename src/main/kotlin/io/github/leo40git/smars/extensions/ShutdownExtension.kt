package io.github.leo40git.smars.extensions

import dev.kordex.core.checks.isBotOwner
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.i18n.withContext
import io.github.leo40git.smars.Constants
import io.github.leo40git.smars.i18n.Translations
import kotlin.system.exitProcess

class ShutdownExtension : Extension() {
	override val name = "shutdown"

	override suspend fun setup() {
		ephemeralSlashCommand {
			name = Translations.Commands.Shutdown.name
			description = Translations.Commands.Shutdown.description
			guild(Constants.TEST_SERVER_ID)

			check {
				isBotOwner()
			}

			action {
				respond {
					content = Translations.Commands.Shutdown.response
						.withContext(this@action)
						.translate()
				}

				this@ShutdownExtension.bot.close()
				exitProcess(0)
			}
		}
	}
}
