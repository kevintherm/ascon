package com.ascon.engine.detection

import com.ascon.core.data.AccountRefused
import com.ascon.core.data.SyncBackend
import com.ascon.core.data.SyncCursorExpired
import com.ascon.core.model.SyncPage
import com.ascon.core.model.SyncRecord
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** The sync calls of the Ascon backend, `/sync/changes` in contracts/openapi.yaml. */
class HttpSyncBackend(baseUrl: HttpUrl, private val client: OkHttpClient) : SyncBackend {
    private val changesUrl = baseUrl.resolve("sync/changes")!!

    override suspend fun push(token: String, records: List<SyncRecord>) {
        val body = buildJsonObject { put("changes", JsonArray(records.map { it.toJson() })) }
        call(token, Request.Builder().url(changesUrl).post(body.toString().toRequestBody(JSON)))
    }

    override suspend fun pull(token: String, cursor: String?): SyncPage {
        val url = changesUrl.newBuilder().apply { cursor?.let { addQueryParameter("since", it) } }
            .addQueryParameter("limit", PULL_LIMIT.toString()).build()
        val page = parse(call(token, Request.Builder().url(url).get()))
        val next = page["cursor"]?.jsonPrimitive?.contentOrNull ?: throw IOException("pull without a cursor")
        return SyncPage(page.changes(), next, page["hasMore"]?.jsonPrimitive?.booleanOrNull ?: false)
    }

    private fun parse(text: String): JsonObject = try {
        BridgeProtocol.json.parseToJsonElement(text).jsonObject
    } catch (e: SerializationException) {
        throw IOException("unexpected response", e)
    } catch (e: IllegalArgumentException) {
        throw IOException("unexpected response", e)
    }

    private suspend fun call(token: String, request: Request.Builder): String {
        val built = request.header("Authorization", "Bearer $token").build()
        return withContext(Dispatchers.IO) { client.newCall(built).execute() }.use { response ->
            if (!response.isSuccessful) {
                throw when (response.code) {
                    HTTP_UNAUTHORIZED -> AccountRefused()
                    HTTP_GONE -> SyncCursorExpired()
                    else -> IOException("sync failed with ${response.code}")
                }
            }
            response.body.string()
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
        const val PULL_LIMIT = 500
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_GONE = 410
    }
}
