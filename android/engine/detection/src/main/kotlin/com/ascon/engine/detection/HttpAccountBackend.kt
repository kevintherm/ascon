package com.ascon.engine.detection

import com.ascon.core.data.AccountBackend
import com.ascon.core.data.AccountRefused
import com.ascon.core.model.AccountSession
import com.ascon.core.model.Quota
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Sign-in, sign-out and quota calls of the Ascon backend, contracts/openapi.yaml. */
class HttpAccountBackend(private val baseUrl: HttpUrl, private val client: OkHttpClient) : AccountBackend {
    override suspend fun signIn(idToken: String): AccountSession {
        val body = BridgeProtocol.json.encodeToString(SignIn.serializer(), SignIn(idToken)).toRequestBody(JSON)
        return call(Request.Builder().url(baseUrl.resolve("sessions")!!).post(body).build()) { response ->
            val session = response.read(Session.serializer())
            AccountSession(session.token, displayName = "", email = null, premium = session.tier == PREMIUM)
        }
    }

    override suspend fun signOut(token: String) {
        val request = Request.Builder().url(baseUrl.resolve("sessions/current")!!).delete()
            .header("Authorization", "Bearer $token").build()
        // A token the backend no longer knows is already signed out.
        withContext(Dispatchers.IO) { client.newCall(request).execute() }.use { response ->
            if (!response.isSuccessful && response.code != HTTP_UNAUTHORIZED) {
                throw IOException("sign-out failed with ${response.code}")
            }
        }
    }

    override suspend fun quota(token: String): Quota {
        val request = Request.Builder().url(baseUrl.resolve("quota")!!).header("Authorization", "Bearer $token").build()
        return call(request) { response ->
            val quota = response.read(QuotaDto.serializer())
            val resetsAt = try {
                Instant.parse(quota.resetsAt)
            } catch (e: DateTimeParseException) {
                throw IOException("unexpected response", e)
            }
            Quota(quota.limit, quota.remaining, resetsAt, premium = quota.tier == PREMIUM)
        }
    }

    private suspend fun <T> call(request: Request, read: (Response) -> T): T =
        withContext(Dispatchers.IO) { client.newCall(request).execute() }.use { response ->
            when {
                response.isSuccessful -> read(response)
                response.code == HTTP_UNAUTHORIZED -> throw AccountRefused()
                else -> throw IOException("account call failed with ${response.code}")
            }
        }

    private fun <T> Response.read(serializer: KSerializer<T>): T = try {
        BridgeProtocol.json.decodeFromString(serializer, body.string())
    } catch (e: SerializationException) {
        throw IOException("unexpected response", e)
    } catch (e: IllegalArgumentException) {
        throw IOException("unexpected response", e)
    }

    @Serializable
    private data class SignIn(val idToken: String)

    @Serializable
    private data class Session(val accountId: String, val token: String, val tier: String)

    @Serializable
    private data class QuotaDto(val tier: String, val limit: Int, val remaining: Int, val resetsAt: String)

    private companion object {
        val JSON = "application/json".toMediaType()
        const val PREMIUM = "premium"
        const val HTTP_UNAUTHORIZED = 401
    }
}
