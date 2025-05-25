@file:Suppress("MagicNumber", "UnderscoresInNumericLiterals")

package io.github.leo40git.smars

import dev.kord.common.entity.Snowflake
import dev.kordex.core.utils.env

object Constants {
	const val GH_REPO_ORG = "SMALauncher"

	const val GH_REPO_NAME = "SMARolling"

	val GH_APP_ID = env("GH_APP_ID")

	val GH_APP_KEY = env("GH_APP_KEY")

	val TEST_SERVER_ID = Snowflake(847428895641042944)

	val SCRAPE_CHANNEL_ID = Snowflake(836080736939409418)

	val LOG_CHANNEL_ID = Snowflake(857240987214544938)
}
