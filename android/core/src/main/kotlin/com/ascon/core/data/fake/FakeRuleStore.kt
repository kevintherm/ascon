package com.ascon.core.data.fake

import com.ascon.core.data.CachedRule
import com.ascon.core.data.DeviceTokenStore
import com.ascon.core.data.RuleHealthCount
import com.ascon.core.data.RuleStore

/** Rules and health counts in memory, for tests. */
class FakeRuleStore : RuleStore {
    val rules = mutableMapOf<String, CachedRule>()
    private val counts = mutableMapOf<Pair<String, Int>, RuleHealthCount>()

    override suspend fun rule(domain: String): CachedRule? = rules[domain]

    override suspend fun saveRule(rule: CachedRule) {
        rules[rule.domain] = rule
    }

    override suspend fun addHealth(count: RuleHealthCount) {
        val key = count.domain to count.version
        counts[key] = counts[key]?.plus(count, 1) ?: count
    }

    override suspend fun health(): List<RuleHealthCount> = counts.values.toList()

    override suspend fun removeHealth(sent: List<RuleHealthCount>) {
        sent.forEach { count ->
            val key = count.domain to count.version
            val left = counts[key]?.plus(count, -1) ?: return@forEach
            if (left.successes + left.emptyResults + left.backwardJumps <= 0) counts.remove(key) else counts[key] = left
        }
    }

    private fun RuleHealthCount.plus(other: RuleHealthCount, sign: Int) = copy(
        successes = successes + sign * other.successes,
        emptyResults = emptyResults + sign * other.emptyResults,
        backwardJumps = backwardJumps + sign * other.backwardJumps
    )
}

/** A device token in memory, for tests. */
class FakeDeviceToken(var value: String? = null) : DeviceTokenStore {
    override suspend fun token(): String? = value

    override suspend fun saveToken(token: String?) {
        value = token
    }
}
