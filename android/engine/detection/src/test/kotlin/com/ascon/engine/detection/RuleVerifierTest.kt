package com.ascon.engine.detection

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleVerifierTest {
    /** Signed by the Go signer, see contracts/fixtures/signed-rule.json. */
    private val fixture = Json.parseToJsonElement(
        File("../../../contracts/fixtures/signed-rule.json").readText()
    ) as JsonObject

    private fun field(name: String) = fixture[name]!!.jsonPrimitive.content

    private val signed = SignedRule(field("payload"), field("signature"), field("keyId"))
    private val verifier = RuleVerifier(mapOf(field("keyId") to field("publicKey")))

    @Test
    fun `a rule the server signed is read from the exact payload bytes`() {
        val rule = verifier.verify(signed)!!
        assertEquals("glasslight.example", rule["domain"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a changed payload, a forged signature or an unknown key is refused`() {
        val other = SignedRule(
            payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                """{"domain":"x"}""".toByteArray()
            ),
            signature = signed.signature,
            keyId = signed.keyId
        )
        assertNull(verifier.verify(other))
        assertNull(verifier.verify(signed.copy(signature = signed.signature.reversed())))
        assertNull(verifier.verify(signed.copy(keyId = "2027-01")))
        assertNull(verifier.verify(signed.copy(payload = "not base64!")))
        assertNull(RuleVerifier(emptyMap()).verify(signed))
    }
}
