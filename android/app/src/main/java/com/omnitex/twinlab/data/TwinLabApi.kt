package com.omnitex.twinlab.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Thin REST client for the FastAPI backend. One instance per base URL —
 * [AppContainer] rebuilds it when the Settings URL changes.
 */
class TwinLabApi(private val baseUrl: String) {

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    suspend fun getDevices(): List<Device> =
        client.get("$baseUrl/devices").body()

    suspend fun getLastKnown(id: String): Map<String, Reading> =
        client.get("$baseUrl/devices/$id/last-known").body()

    suspend fun getReadings(id: String, sensor: String, limit: Int = 50, rangeHours: Int = 24): List<HistoryPoint> =
        client.get("$baseUrl/devices/$id/readings?sensor=$sensor&limit=$limit&range_hours=$rangeHours").body()

    suspend fun getAlerts(limit: Int = 50): List<Alert> =
        client.get("$baseUrl/alerts?limit=$limit").body()

    suspend fun getDeviceAlerts(id: String, limit: Int = 50): List<Alert> =
        client.get("$baseUrl/devices/$id/alerts?limit=$limit").body()

    suspend fun registerPushToken(token: String) {
        client.post("$baseUrl/push/register") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("token" to token))
        }
    }

    suspend fun unregisterPushToken(token: String) {
        client.delete("$baseUrl/push/register/$token")
    }

    fun close() = client.close()
}
