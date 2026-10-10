package com.ascon.engine.detection

import com.ascon.core.data.DeviceTokenStore
import com.ascon.core.data.RuleHealthCount
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** A rule as the backend sends it: the rule's JSON bytes and their signature, in base64url. */
@Serializable
data class SignedRule(val payload: String, val signature: String, val keyId: String)

sealed interface RuleAnswer {
    data class Found(val rule: SignedRule) : RuleAnswer

    data object Missing : RuleAnswer
}

/** The rule calls of the Ascon backend, contracts/openapi.yaml. */
interface RuleBackend {
    /** The rule for [domain], or for the closest page structure. Throws [IOException] when the call fails. */
    suspend fun lookup(domain: String, fingerprint: String?): RuleAnswer

    /** Throws [IOException] when the call fails. */
    suspend fun sendHealth(counts: List<RuleHealthCount>)
}

/**
 * Calls the backend over HTTP with the device's anonymous token, registering the device
 * on the first call and again if the server no longer knows the token.
 */
class HttpRuleBackend(
    private val baseUrl: HttpUrl,
    private val client: OkHttpClient,
    private val tokens: DeviceTokenStore,
    private val appVersion: String
) : RuleBackend {
    override suspend fun lookup(domain: String, fingerprint: String?): RuleAnswer {
        val url = baseUrl.newBuilder().addPathSegment("rules").addQueryParameter("domain", domain)
            .apply { if (fingerprint != null) addQueryParameter("fingerprint", fingerprint) }
            .build()
        return authorized(Request.Builder().url(url)).use { response ->
            when (response.code) {
                HTTP_OK -> RuleAnswer.Found(response.read(RuleMatch.serializer()).rule)
                HTTP_NOT_FOUND -> RuleAnswer.Missing
                else -> throw IOException("rule lookup failed with ${response.code}")
            }
        }
    }

    override suspend fun sendHealth(counts: List<RuleHealthCount>) {
        val batch =
            HealthBatch(
                counts.map {
                    HealthEntry(it.domain, it.version, it.successes, it.emptyResults, it.backwardJumps)
                }
            )
        val body = BridgeProtocol.json.encodeToString(HealthBatch.serializer(), batch).toRequestBody(JSON)
        authorized(Request.Builder().url(baseUrl.resolve("rules/health")!!).post(body)).use { response ->
            if (response.code != HTTP_ACCEPTED) throw IOException("health report failed with ${response.code}")
        }
    }

    private suspend fun authorized(request: Request.Builder): Response {
        val token = tokens.token() ?: register()
        val first = execute(request.header("Authorization", "Bearer $token").build())
        if (first.code != HTTP_UNAUTHORIZED) return first
        first.close()
        return execute(request.header("Authorization", "Bearer ${register()}").build())
    }

    private suspend fun register(): String {
        val body = BridgeProtocol.json.encodeToString(
            Registration.serializer(),
            Registration(appVersion)
        ).toRequestBody(JSON)
        val token = execute(Request.Builder().url(baseUrl.resolve("devices")!!).post(body).build()).use { response ->
            if (response.code != HTTP_CREATED) throw IOException("device registration failed with ${response.code}")
            response.read(Device.serializer()).token
        }
        tokens.saveToken(token)
        return token
    }

    private suspend fun execute(request: Request): Response = withContext(Dispatchers.IO) {
        client.newCall(request).execute()
    }

    private fun <T> Response.read(serializer: kotlinx.serialization.KSerializer<T>): T = try {
        BridgeProtocol.json.decodeFromString(serializer, body.string())
    } catch (e: SerializationException) {
        throw IOException("unexpected response", e)
    } catch (e: IllegalArgumentException) {
        throw IOException("unexpected response", e)
    }

    @Serializable
    private data class Registration(val appVersion: String)

    @Serializable
    private data class Device(val token: String)

    @Serializable
    private data class RuleMatch(val rule: SignedRule)

    @Serializable
    private data class HealthEntry(
        val domain: String,
        val version: Int,
        val successes: Int,
        val emptyResults: Int,
        val backwardJumps: Int
    )

    @Serializable
    private data class HealthBatch(val entries: List<HealthEntry>)

    private companion object {
        val JSON = "application/json".toMediaType()
        const val HTTP_OK = 200
        const val HTTP_CREATED = 201
        const val HTTP_ACCEPTED = 202
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
    }
}
