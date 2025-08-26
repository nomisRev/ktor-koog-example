package org.jetbrains.demo

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.serialization.kotlinx.json.DefaultJson
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.StringFormat
import kotlinx.serialization.serializer
import kotlin.time.Duration

fun HttpClient.sseFlow(
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    request: HttpRequestBuilder.() -> Unit,
): Flow<ServerSentEvent> = flow {
    sse(
        request = request,
        reconnectionTime = reconnectionTime,
        showCommentEvents = showCommentEvents,
        showRetryEvents = showRetryEvents,
    ) { incoming.collect(this@flow) }
}

inline fun <reified A> Flow<ServerSentEvent>.deserialize(format: StringFormat = DefaultJson): Flow<A> =
    mapNotNull { it.data?.let { data -> format.decodeFromString(serializer<A>(), data) } }
