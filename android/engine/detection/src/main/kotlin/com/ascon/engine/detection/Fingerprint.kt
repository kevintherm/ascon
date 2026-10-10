package com.ascon.engine.detection

/**
 * The structure fingerprint from AGENTS.md: a 64-bit simhash of the features bridge.js
 * reads from a page, such as its generator meta tag, class names and tag pairs. Pages
 * built by one theme share most features, so a new mirror's fingerprint lands within a
 * few bits of the original's and the backend can lend it the original's rule.
 */
object Fingerprint {
    private const val BITS = 64
    private const val FNV_OFFSET = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
    private const val FNV_PRIME = 0x100000001b3L
    private const val HEX = 16
    private const val BYTE = 0xff

    /** 16 lowercase hex digits, or null for a page with no features. */
    fun of(features: List<String>): String? {
        val distinct = features.distinct()
        if (distinct.isEmpty()) return null
        // Each feature votes on every bit with its own hash; the majority sets the bit.
        val votes = IntArray(BITS)
        distinct.forEach { feature ->
            val hash = fnv1a(feature)
            for (bit in 0 until BITS) votes[bit] += if ((hash ushr bit) and 1L == 1L) 1 else -1
        }
        var print = 0L
        for (bit in 0 until BITS) if (votes[bit] > 0) print = print or (1L shl bit)
        return "%016x".format(print)
    }

    /** How many bits differ between two fingerprints, as the backend counts it. */
    fun distance(a: String, b: String): Int =
        java.lang.Long.bitCount(java.lang.Long.parseUnsignedLong(a, HEX) xor java.lang.Long.parseUnsignedLong(b, HEX))

    private fun fnv1a(text: String): Long {
        var hash = FNV_OFFSET
        text.encodeToByteArray().forEach { byte ->
            hash = (hash xor (byte.toLong() and BYTE.toLong())) * FNV_PRIME
        }
        return hash
    }
}
