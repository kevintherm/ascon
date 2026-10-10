package com.ascon.core.data.room

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import java.time.Instant

/** A site's rule from the backend. A null [json] remembers that the backend had none. */
@Entity(tableName = "rule")
internal data class RuleEntity(
    @PrimaryKey val domain: String,
    val json: String?,
    val version: Int,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Instant,
    val fingerprint: String?
)

@Entity(tableName = "rule_health", primaryKeys = ["domain", "version"])
internal data class RuleHealthEntity(
    val domain: String,
    val version: Int,
    val successes: Int,
    @ColumnInfo(name = "empty_results") val emptyResults: Int,
    @ColumnInfo(name = "backward_jumps") val backwardJumps: Int
)

@Dao
internal abstract class RuleDao {
    @Query("SELECT * FROM rule WHERE domain = :domain")
    abstract suspend fun rule(domain: String): RuleEntity?

    @Upsert
    abstract suspend fun saveRule(rule: RuleEntity)

    @Query("SELECT * FROM rule_health")
    abstract suspend fun health(): List<RuleHealthEntity>

    @Query(
        """
        INSERT INTO rule_health VALUES (:domain, :version, :successes, :emptyResults, :backwardJumps)
        ON CONFLICT (domain, version) DO UPDATE SET
            successes = successes + excluded.successes,
            empty_results = empty_results + excluded.empty_results,
            backward_jumps = backward_jumps + excluded.backward_jumps
        """
    )
    abstract suspend fun addHealth(domain: String, version: Int, successes: Int, emptyResults: Int, backwardJumps: Int)

    @Query("DELETE FROM rule_health WHERE successes <= 0 AND empty_results <= 0 AND backward_jumps <= 0")
    protected abstract suspend fun dropEmpty()

    /** Subtracts each count, then drops rows with nothing left. */
    @Transaction
    open suspend fun removeHealth(sent: List<RuleHealthEntity>) {
        sent.forEach { addHealth(it.domain, it.version, -it.successes, -it.emptyResults, -it.backwardJumps) }
        dropEmpty()
    }
}
