@file:Suppress("MagicNumber")

package io.github.leo40git.smars

import dev.kord.common.entity.Snowflake
import dev.kordex.core.utils.env

object Constants {
	const val GH_REPO_ORG = "SMALauncher"

	const val GH_REPO_NAME = "SMARolling"

	val GH_APP_ID = env("GH_APP_ID")

	val GH_APP_KEY = env("GH_APP_KEY")

	val TEST_SERVER_ID = Snowflake(847_428_895_641_042_944)

	val SCRAPE_CHANNEL_ID = Snowflake(836_080_736_939_409_418)

	val LOG_CHANNEL_ID = Snowflake(857_240_987_214_544_938)
}
