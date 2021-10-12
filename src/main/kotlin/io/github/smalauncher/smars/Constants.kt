package io.github.smalauncher.smars

import dev.kord.common.entity.Snowflake
import kotlin.system.exitProcess

object Constants {
    object Env {
        val TOKEN = getEnvString("TOKEN", "your Discord bot's token")
        val APP_ID = getEnvString("GH_APP_ID", "your GitHub App's ID")
        val APP_KEY = getEnvString("GH_APP_KEY", "your GitHub App's PEM key")
        val REPO_ORG = getEnvString("GH_REPO_ORG", "the organization owning the GitHub repository to upload releases to")
        val REPO_NAME = getEnvString("GH_REPO_NAME", "the name of the GitHub repository to upload releases to")

        private fun getEnvString(name: String, desc: String): String {
            val value = System.getenv(name) ?: null
            if (value == null) {
                println("Missing environment variable \"$name\"! Please set it to $desc.")
                exitProcess(1)
            }
            return value
        }
    }

    object Guilds {
        /**
         * ADudeCalledLeo's splendid server (AKA Ass)
         */
        val ASS = Snowflake(847428895641042944)
    }

    object Channels {
        /**
         * #downloads-rolling in the Shang Mu Architect Discord.
         */
        val ROLLING = Snowflake(836080736939409418)

        /**
         * #smars-config in Leo's server.
         */
        val CONFIG = Snowflake(857240971837833226)

        /**
         * #smars-log in Leo's server.
         */
        val LOG = Snowflake(857240987214544938)
    }

    object Users {
        /**
         * It's a-me, Leo!
         */
        val OWNER = Snowflake(169456051920437248)
        /**
         * The brick receiver herself.
         */
        val LIBBIE = Snowflake(166395113570959360)
    }
}