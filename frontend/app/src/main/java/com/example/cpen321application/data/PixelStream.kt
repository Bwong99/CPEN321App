package com.example.cpen321application.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/** One cell update from the course pixel stream. */
data class PixelUpdate(
    val x: Int,
    val y: Int,
    val color: String,
)

private const val RELAY_PATH = "/ws/pixels"
private const val NORMAL_CLOSURE = 1000

/**
 * Subscribes to the pixel stream our back-end relays from the course server.
 * The app never talks to the course server directly, as M1 requires.
 */
object PixelStream {

    /**
     * Emits each pixel update as it arrives. The socket is closed when the
     * collecting coroutine is cancelled, e.g. on leaving the screen.
     */
    fun connect(apiBaseUrl: String): Flow<PixelUpdate> = callbackFlow {
        val request = Request.Builder().url(toWebSocketUrl(apiBaseUrl)).build()

        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                parse(text)?.let(::trySend)
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?,
            ) {
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }
        }

        val client = OkHttpClient()
        val socket = client.newWebSocket(request, listener)

        awaitClose {
            socket.close(NORMAL_CLOSURE, null)
            client.dispatcher.executorService.shutdown()
        }
    }

    private fun parse(text: String): PixelUpdate? = runCatching {
        val json = JSONObject(text)
        PixelUpdate(
            x = json.getInt("x"),
            y = json.getInt("y"),
            color = json.getString("color"),
        )
    }.getOrNull()

    // http(s) base URL -> ws(s) relay URL. https is replaced first so the
    // http prefix cannot match inside it.
    private fun toWebSocketUrl(apiBaseUrl: String): String =
        apiBaseUrl.trimEnd('/')
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") + RELAY_PATH
}
