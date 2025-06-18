package io.github.leo40git.smars.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.UnsynchronizedAppenderBase
import io.sentry.Sentry
import io.sentry.SentryInstantDate
import io.sentry.SentryLogLevel

class SentryLogbackAppender : UnsynchronizedAppenderBase<ILoggingEvent>() {
	override fun append(eventObject: ILoggingEvent?) {
		if (eventObject == null || !Sentry.isEnabled())
			return

		@Suppress("UnstableApiUsage")
		Sentry.logger().log(
			mapLevel(eventObject.level),
			SentryInstantDate(eventObject.instant),
			eventObject.message,
			eventObject.argumentArray)
	}

	private fun mapLevel(level: Level): SentryLogLevel {
		return when (level) {
			Level.TRACE -> SentryLogLevel.TRACE
			Level.DEBUG -> SentryLogLevel.DEBUG
			Level.INFO -> SentryLogLevel.INFO
			Level.WARN -> SentryLogLevel.WARN
			Level.ERROR -> SentryLogLevel.ERROR
			// Logback doesn't have "fatal/critical" level
			else -> throw RuntimeException("Unknown Logback level $level")
		}
	}
}
