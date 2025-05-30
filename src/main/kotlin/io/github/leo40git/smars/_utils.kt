package io.github.leo40git.smars

import dev.kord.rest.NamedFile
import dev.kord.rest.builder.message.MessageBuilder
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.utils.io.ByteReadChannel

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
