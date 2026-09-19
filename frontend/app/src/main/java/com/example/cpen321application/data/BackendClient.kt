package com.example.cpen321application.data

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TIMEOUT_MS = 10_000

/** The three values M1 requires the back-end to supply. */
data class ServerInfo(
    val firstName: String,
    val lastName: String,
    val localTime: String,
    val ipAddress: String,
)

/**
 * Reads the three M1 APIs. Uses HttpURLConnection and org.json, both part of
 * the Android platform, so the app needs no networking dependency.
 */
object BackendClient {

    /**
     * Fetches all three endpoints concurrently. If any one fails the whole
     * call fails, because the screen is only meaningful when complete.
     */
    suspend fun fetchServerInfo(apiBaseUrl: String): Result<ServerInfo> =
        coroutineScope {
            val base = apiBaseUrl.trimEnd('/')
            runCatching {
                val name = async { getJson("$base/api/name") }
                val time = async { getJson("$base/api/time") }
                val ip = async { getJson("$base/api/ip") }

                ServerInfo(
                    firstName = name.await().getString("firstName"),
                    lastName = name.await().getString("lastName"),
                    localTime = time.await().getString("time"),
                    ipAddress = ip.await().getString("ip"),
                )
            }
        }

    private suspend fun getJson(url: String): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }

        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                error("GET $url returned HTTP $code")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }
}
