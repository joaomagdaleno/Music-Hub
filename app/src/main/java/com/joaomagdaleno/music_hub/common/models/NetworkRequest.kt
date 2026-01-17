package com.joaomagdaleno.music_hub.common.models

import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A data class to represent a network request.
 *
 * @param url The URL to make the request to
 * @param headers The headers to be sent with the request
 * @param method The HTTP method to use for the request, defaults to [Method.GET]
 * @param bodyBase64 The body of the request encoded in Base64, can be null
 */
@OptIn(ExperimentalEncodingApi::class)
@Serializable
data class NetworkRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val method: Method = Method.GET,
    val bodyBase64: String? = null,
) {

    enum class Method {
        GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, TRACE, CONNECT
    }

    val body by lazy {
        bodyBase64?.let { Base64.decode(it) }
    }

    val lowerCaseHeaders by lazy {
        headers.mapKeys { it.key.lowercase() }
    }

    constructor(
        method: Method,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
    ) : this(
        url = url,
        headers = headers,
        method = method,
        bodyBase64 = body?.let { Base64.encode(it) }
    )

    companion object {
        fun toGetRequest(url: String, headers: Map<String, String> = emptyMap()): NetworkRequest {
            return NetworkRequest(url, headers)
        }
    }
}