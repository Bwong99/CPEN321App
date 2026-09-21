package com.example.cpen321application.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TIMEOUT_MS = 10_000
private const val MAX_ERROR_CHARS = 200
private val HTTP_OK_RANGE = 200..299

/** The three values M1 requires the back-end to supply. */
data class ServerInfo(
    val firstName: String,
    val lastName: String,
    val localTime: String,
    val ipAddress: String,
)

/**
 * The signed-in account as the *server* verified it, which is the only version
 * the app displays. See [authenticate].
 */
data class AuthenticatedUser(
    val firstName: String,
    val lastName: String,
    val email: String,
)

/**
 * Reads the M1 APIs. Uses HttpURLConnection and org.json, both part of the
 * Android platform, so the app needs no networking dependency.
 *
 * Every call here carries the Google ID token from [GoogleAuth.signIn] in an
 * `Authorization: Bearer` header. The back-end verifies it against Google on
 * each request, so an unauthenticated caller gets a 401 rather than data.
 */
object BackendClient {

    /**
     * Exchanges a Google ID token for the identity behind it.
     *
     * The app cannot know who signed in until the server says so: the token is
     * posted to `/api/auth/google`, the server checks its signature, audience
     * and expiry with Google, and answers with the account it belongs to. A
     * failure here means the token was rejected, so sign-in has not happened.
     */
    suspend fun authenticate(
        apiBaseUrl: String,
        idToken: String,
    ): Result<AuthenticatedUser> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("idToken", idToken)
            val json = postJson("${apiBaseUrl.trimEnd('/')}/api/auth/google", body)

            AuthenticatedUser(
                firstName = json.optString("firstName"),
                lastName = json.optString("lastName"),
                email = json.optString("email"),
            )
        }
    }

    /**
     * Fetches all three endpoints concurrently, authenticated with [idToken].
     * If any one fails the whole call fails, because the screen is only
     * meaningful when complete.
     */
    suspend fun fetchServerInfo(
        apiBaseUrl: String,
        idToken: String,
    ): Result<ServerInfo> = coroutineScope {
        val base = apiBaseUrl.trimEnd('/')
        runCatching {
            val name = async { getJson("$base/api/name", idToken) }
            val time = async { getJson("$base/api/time", idToken) }
            val ip = async { getJson("$base/api/ip", idToken) }

            ServerInfo(
                firstName = name.await().getString("firstName"),
                lastName = name.await().getString("lastName"),
                localTime = time.await().getString("time"),
                ipAddress = ip.await().getString("ip"),
            )
        }
    }

    private suspend fun getJson(url: String, idToken: String): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Authorization", "Bearer $idToken")
            }

            connection.readJson()
        }

    private fun postJson(url: String, body: JSONObject): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        return connection.readJson()
    }

    /**
     * Reads the response, or throws with the server's own explanation.
     *
     * On a 4xx the body arrives on the error stream rather than the input
     * stream, and that body is where the reason lives — "Invalid or expired
     * Google ID token" is far more use on screen than "HTTP 401".
     */
    private fun HttpURLConnection.readJson(): JSONObject = try {
        val ok = responseCode in HTTP_OK_RANGE
        val text = (if (ok) inputStream else errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()

        if (!ok) {
            throw IOException(explain(responseCode, text))
        }
        JSONObject(text)
    } finally {
        disconnect()
    }

    private fun explain(code: Int, body: String): String {
        val detail = runCatching { JSONObject(body).getString("error") }
            .getOrDefault(body.take(MAX_ERROR_CHARS))

        return if (detail.isBlank()) "HTTP $code" else "HTTP $code: $detail"
    }
}
