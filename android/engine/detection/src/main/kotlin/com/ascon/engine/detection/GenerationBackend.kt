package com.ascon.engine.detection

import java.io.IOException
import java.time.Instant
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

/** A chapter page reduced by bridge.js, as the backend's PageSnapshot. */
@Serializable
data class PageSnapshot(val url: String, val html: String)

/** Where a generation stands, as the backend's Candidate. */
sealed interface CandidateState {
    data class Pending(val id: String, val retryAfterSeconds: Int) : CandidateState

    data class Accepted(val rule: SignedRule) : CandidateState

    data class Rejected(val reason: String) : CandidateState

    /** A rule already exists for the site, so it should be looked up instead. */
    data object RuleExists : CandidateState

    /** The account has no AI detection left until [resetsAt]. */
    data class OutOfQuota(val resetsAt: Instant?) : CandidateState
}

/** The AI detection calls of the Ascon backend, contracts/openapi.yaml. They need an account. */
interface GenerationBackend {
    /** Throws [IOException] when the call fails or the account token is refused. */
    suspend fun request(domain: String, fingerprint: String?, samples: List<PageSnapshot>): CandidateState

    /** Throws [IOException] when the call fails or the account token is refused. */
    suspend fun candidate(id: String): CandidateState
}

/** Calls the backend with the account token [accountToken] gives at the time of the call. */
class HttpGenerationBackend(
    private val baseUrl: HttpUrl,
    private val client: OkHttpClient,
    private val accountToken: () -> String?
) : GenerationBackend {
    override suspend fun request(domain: String, fingerprint: String?, samples: List<PageSnapshot>): CandidateState {
        val body = BridgeProtocol.json.encodeToString(
            CandidateRequest.serializer(),
            CandidateRequest(domain, fingerprint, samples)
        ).toRequestBody(JSON)
        return call(Request.Builder().url(baseUrl.resolve("rule-candidates")!!).post(body))
    }

    override suspend fun candidate(id: String): CandidateState =
        call(Request.Builder().url(baseUrl.newBuilder().addPathSegment("rule-candidates").addPathSegment(id).build()))

    private suspend fun call(request: Request.Builder): CandidateState {
        val token = accountToken() ?: throw IOException("no account")
        val authorized = request.header("Authorization", "Bearer $token").build()
        return withContext(Dispatchers.IO) { client.newCall(authorized).execute() }.use { response ->
            when (response.code) {
                HTTP_OK, HTTP_ACCEPTED -> response.read(Candidate.serializer()).toState()
                HTTP_CONFLICT -> CandidateState.RuleExists
                HTTP_TOO_MANY -> CandidateState.OutOfQuota(
                    runCatching { response.read(QuotaProblem.serializer()).quota?.resetsAt }.getOrNull()
                        ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                )
                else -> throw IOException("rule generation call failed with ${response.code}")
            }
        }
    }

    private fun Candidate.toState(): CandidateState = when (status) {
        "pending" -> CandidateState.Pending(candidateId, retryAfter ?: DEFAULT_POLL_SECONDS)
        "accepted" -> CandidateState.Accepted(rule ?: throw IOException("accepted without a rule"))
        "rejected" -> CandidateState.Rejected(reason.orEmpty())
        else -> throw IOException("unknown candidate status $status")
    }

    private fun <T> Response.read(serializer: KSerializer<T>): T = try {
        BridgeProtocol.json.decodeFromString(serializer, body.string())
    } catch (e: SerializationException) {
        throw IOException("unexpected response", e)
    } catch (e: IllegalArgumentException) {
        throw IOException("unexpected response", e)
    }

    @Serializable
    private data class CandidateRequest(val domain: String, val fingerprint: String?, val samples: List<PageSnapshot>)

    @Serializable
    private data class Candidate(
        val candidateId: String,
        val status: String,
        val rule: SignedRule? = null,
        val reason: String? = null,
        val retryAfter: Int? = null
    )

    @Serializable
    private data class Quota(val resetsAt: String)

    @Serializable
    private data class QuotaProblem(val quota: Quota? = null)

    private companion object {
        val JSON = "application/json".toMediaType()
        const val DEFAULT_POLL_SECONDS = 5
        const val HTTP_OK = 200
        const val HTTP_ACCEPTED = 202
        const val HTTP_CONFLICT = 409
        const val HTTP_TOO_MANY = 429
    }
}
