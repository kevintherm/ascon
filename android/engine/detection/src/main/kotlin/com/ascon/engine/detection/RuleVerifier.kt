package com.ascon.engine.detection

import java.util.Base64
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * Checks a rule from the backend against the public keys the app pins, given as
 * base64 by key id. The signature covers the payload's exact bytes, so they are
 * verified before they are parsed.
 */
class RuleVerifier(publicKeys: Map<String, String>) {
    private val keys = publicKeys.mapValues { (_, key) -> Ed25519PublicKeyParameters(Base64.getDecoder().decode(key)) }

    /** The rule, or null if its key is unknown, its signature fails or it isn't a JSON object. */
    fun verify(signed: SignedRule): JsonObject? {
        val key = keys[signed.keyId] ?: return null
        return decode(signed.payload)?.takeIf { verifies(key, it, decode(signed.signature)) }?.let(::parse)
    }

    private fun verifies(key: Ed25519PublicKeyParameters, payload: ByteArray, signature: ByteArray?): Boolean {
        if (signature == null) return false
        val signer = Ed25519Signer()
        signer.init(false, key)
        signer.update(payload, 0, payload.size)
        return signer.verifySignature(signature)
    }

    private fun decode(base64url: String): ByteArray? = try {
        Base64.getUrlDecoder().decode(base64url)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun parse(payload: ByteArray): JsonObject? = try {
        BridgeProtocol.json.parseToJsonElement(payload.decodeToString()) as? JsonObject
    } catch (_: SerializationException) {
        null
    }
}
