package com.ascon.engine.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CosmeticProtocolTest {
    private val page = "https://reader.example/chapter-1"

    @Test
    fun `decodes the page and names messages`() {
        assertEquals(CosmeticMessage.Page(page), CosmeticProtocol.decode("""{"type":"page","url":"$page"}"""))
        assertEquals(
            CosmeticMessage.Names(page, listOf("ad-slot"), listOf("top"), listOf(".x")),
            CosmeticProtocol.decode(
                """{"type":"names","url":"$page","classes":["ad-slot"],"ids":["top"],"exceptions":[".x"]}"""
            )
        )
    }

    @Test
    fun `rejects what the script never sends`() {
        assertNull(CosmeticProtocol.decode("not json"))
        assertNull(CosmeticProtocol.decode("""{"type":"other","url":"$page"}"""))
        assertNull(CosmeticProtocol.decode("""{"type":"page"}"""))
        val many = (1..501).joinToString(",") { "\"c$it\"" }
        assertNull(CosmeticProtocol.decode("""{"type":"names","url":"$page","classes":[$many]}"""))
        val long = "x".repeat(101)
        assertNull(CosmeticProtocol.decode("""{"type":"names","url":"$page","ids":["$long"]}"""))
    }

    @Test
    fun `encodes the reply the script reads`() {
        assertEquals(
            """{"hide":[".banner"],"page":true,"generichide":true,"exceptions":[".x"]}""",
            CosmeticProtocol.encode(
                CosmeticReply(listOf(".banner"), page = true, generichide = true, exceptions = listOf(".x"))
            )
        )
        assertEquals("""{"hide":[]}""", CosmeticProtocol.encode(CosmeticReply(emptyList())))
    }
}
