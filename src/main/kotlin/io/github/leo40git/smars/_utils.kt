package io.github.leo40git.smars

import dev.kord.rest.NamedFile
import dev.kord.rest.builder.message.MessageBuilder
import dev.kordex.core.sentry.SentryContext
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.utils.io.ByteReadChannel
import io.sentry.SentryEvent
import io.sentry.protocol.SentryId

/** Adds a [file][NamedFile] with [name] and [content] to [files][MessageBuilder.files]. */
fun MessageBuilder.addFile(
	name: String,
	content: ByteArray
): NamedFile {
	return addFile(
		name,
		ChannelProvider(content.size.toLong()) {
			ByteReadChannel(content)
		}
	)
}

suspend fun SentryContext.captureNewEvent(
	message: String,
	body: SentryEvent.() -> Unit
): SentryId? {
	val eventMessage = io.sentry.protocol.Message()
	eventMessage.formatted = message

	val event = SentryEvent()
	body(event)

	return captureEvent(event)
}
