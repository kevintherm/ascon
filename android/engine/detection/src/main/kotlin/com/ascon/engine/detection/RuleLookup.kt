package com.ascon.engine.detection

import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject

/** Gives the rules a page on [host] may try, in lookup order. */
fun interface RuleSource {
    suspend fun candidatesFor(host: String): List<RuleCandidate>
}

/** Rules for single domains kept on the device. Room backs it later. */
interface RuleCache {
    suspend fun ruleFor(domain: String): JsonObject?

    suspend fun put(domain: String, rule: JsonObject)
}

class InMemoryRuleCache : RuleCache {
    private val rules = ConcurrentHashMap<String, JsonObject>()

    override suspend fun ruleFor(domain: String): JsonObject? = rules[domain]

    override suspend fun put(domain: String, rule: JsonObject) {
        rules[domain] = rule
    }
}

/**
 * The lookup order from AGENTS.md, as far as it exists on the device: the rule cached
 * for the exact host, then the built-in theme rules. Heuristics run in the page when
 * nothing matches. The backend lookup slots in after the cache once the app talks to
 * the server.
 */
class RuleLookup(private val cache: RuleCache, private val builtIn: List<RuleCandidate>) : RuleSource {
    override suspend fun candidatesFor(host: String): List<RuleCandidate> {
        val own = cache.ruleFor(host.lowercase())
        return listOfNotNull(own?.let(RuleCandidate::forDomain)) + builtIn
    }
}

/** One entry of assets/rules/builtin.json. */
@Serializable
private data class BuiltInRule(val name: String, val `when`: String, val rule: JsonObject)

object BuiltInRules {
    const val ASSET = "rules/builtin.json"

    fun parse(json: String): List<RuleCandidate> =
        BridgeProtocol.json.decodeFromString(ListSerializer(BuiltInRule.serializer()), json)
            .map { RuleCandidate.builtIn(it.rule, it.`when`) }
}
