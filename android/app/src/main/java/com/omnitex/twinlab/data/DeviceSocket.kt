package com.omnitex.twinlab.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

sealed interface WsMessage {
    data class Live(val reading: Reading) : WsMessage
    data class AlertMsg(val alert: Alert) : WsMessage
}

private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Parse one WebSocket text frame from the backend.
 *  - object with `"type":"alert"`  → [WsMessage.AlertMsg]
 *  - any other object              → [WsMessage.Live]
 *  - unparseable                   → null
 */
fun parseWsMessage(json: String): WsMessage? = try {
    val obj = lenient.parseToJsonElement(json).jsonObject
    if (obj["type"]?.jsonPrimitive?.contentOrNull == "alert") {
        WsMessage.AlertMsg(lenient.decodeFromJsonElement(Alert.serializer(), obj))
    } else {
        WsMessage.Live(lenient.decodeFromJsonElement(Reading.serializer(), obj))
    }
} catch (e: Exception) {
    null
}

/**
 * Live feed for one device. `messages()` connects to `ws(s)://<host>/ws/<id>`,
 * emits parsed messages, and reconnects 3 s after any close/error. Cancel the
 * collecting coroutine to stop.
 */
class DeviceSocket(private val baseUrl: String, private val deviceId: String) {

    private val client = HttpClient(OkHttp) { install(WebSockets) }

    fun messages(): Flow<WsMessage> = flow {
        val wsUrl = baseUrl.replaceFirst("http", "ws").trimEnd('/') + "/ws/$deviceId"
        while (true) {
            try {
                client.webSocket(wsUrl) {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            parseWsMessage(frame.readText())?.let { emit(it) }
                        }
                    }
                }
            } catch (e: Exception) {
                // fall through to reconnect
            }
            delay(3_000)
        }
    }

    fun close() = client.close()
}
