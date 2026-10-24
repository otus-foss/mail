package com.otus.mail.feature.notifications.data

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ApplePushRoute(
    val id: String,
    val secret: String,
)

/** Uses a fixed HTTPS origin. Neither device tokens nor route secrets follow redirects. */
class ApplePushBridge(
    private val baseUrl: String,
    private val http: HttpClient,
) {
    init {
        val url = Url(baseUrl)
        require(url.protocol == URLProtocol.HTTPS && url.user == null && url.password == null)
        require(url.encodedPath == "/" || url.encodedPath.isEmpty())
        require(url.parameters.isEmpty() && url.fragment.isEmpty())
    }

    fun endpoint(route: ApplePushRoute): String = url("push/${route.id}")

    suspend fun register(
        token: String,
        keyId: String,
    ): ApplePushRoute =
        Json.decodeFromString(call("routes", HttpMethod.Post, body = Json.encodeToString(Registration(token, keyId))))

    suspend fun active(
        route: ApplePushRoute,
        value: Boolean,
    ) {
        call("routes/${route.id}", HttpMethod.Patch, route, "{\"active\":$value}")
    }

    suspend fun pending(route: ApplePushRoute): List<String> =
        Json.decodeFromString(call("routes/${route.id}/messages", HttpMethod.Get, route))

    suspend fun message(id: String): ByteArray? {
        require(id.matches(IDENTIFIER))
        val response = http.request(url("messages/$id"))
        if (response.status.value == NOT_FOUND) return null
        check(response.status.value == OK) { "Push message unavailable" }
        return response.readRawBytes()
    }

    suspend fun acknowledge(
        route: ApplePushRoute,
        id: String,
    ) {
        require(id.matches(IDENTIFIER))
        call("routes/${route.id}/messages/$id", HttpMethod.Delete, route)
    }

    suspend fun delete(route: ApplePushRoute) {
        call("routes/${route.id}", HttpMethod.Delete, route)
    }

    private suspend fun call(
        path: String,
        method: HttpMethod,
        route: ApplePushRoute? = null,
        body: String? = null,
    ): String {
        val response =
            http.request(url(path)) {
                this.method = method
                route?.let {
                    require(it.id.matches(IDENTIFIER))
                    bearerAuth(it.secret)
                }
                body?.let {
                    contentType(ContentType.Application.Json)
                    setBody(it)
                }
            }
        if (response.status.value == GONE || response.status.value == NOT_FOUND) throw ApplePushRouteExpired()
        check(response.status.value in OK until REDIRECT) { "Push bridge unavailable" }
        return response.bodyAsText()
    }

    private fun url(path: String): String = "${baseUrl.trimEnd('/')}/v1/$path"

    @Serializable
    private data class Registration(
        val deviceToken: String,
        val keyId: String,
    )

    private companion object {
        val IDENTIFIER = Regex("[A-Za-z0-9_-]{32,64}")
        const val OK = 200
        const val REDIRECT = 300
        const val NOT_FOUND = 404
        const val GONE = 410
    }
}

class ApplePushRouteExpired : IllegalStateException("Push route expired")
