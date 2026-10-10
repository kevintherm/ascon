package com.ascon.core.data.room

import com.ascon.core.data.CachedRule
import com.ascon.core.data.RuleHealthCount
import com.ascon.core.data.RuleStore

class RoomRuleStore(database: AsconDatabase) : RuleStore {
    private val dao = database.rules()

    override suspend fun rule(domain: String): CachedRule? = dao.rule(domain)?.let {
        CachedRule(it.domain, it.json, it.version, it.fetchedAt, it.fingerprint)
    }

    override suspend fun saveRule(rule: CachedRule) = dao.saveRule(
        RuleEntity(rule.domain, rule.json, rule.version, rule.fetchedAt, rule.fingerprint)
    )

    override suspend fun addHealth(count: RuleHealthCount) =
        dao.addHealth(count.domain, count.version, count.successes, count.emptyResults, count.backwardJumps)

    override suspend fun health(): List<RuleHealthCount> = dao.health().map {
        RuleHealthCount(it.domain, it.version, it.successes, it.emptyResults, it.backwardJumps)
    }

    override suspend fun removeHealth(sent: List<RuleHealthCount>) = dao.removeHealth(
        sent.map { RuleHealthEntity(it.domain, it.version, it.successes, it.emptyResults, it.backwardJumps) }
    )
}
