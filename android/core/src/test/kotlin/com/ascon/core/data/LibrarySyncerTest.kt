package com.ascon.core.data

import com.ascon.core.data.fake.FakeAccountRepository
import com.ascon.core.data.fake.FakeLibraryRepository
import com.ascon.core.model.AccountState
import com.ascon.core.model.SyncPage
import com.ascon.core.model.SyncRecord
import java.io.IOException
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibrarySyncerTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-10T08:00:00Z"), ZoneOffset.UTC)

    /** Keeps every pushed record in order; a cursor is how many were pulled. */
    private class FakeServer : SyncBackend {
        val records = mutableListOf<SyncRecord>()
        var failure: IOException? = null
        var pageSize = 2

        override suspend fun push(token: String, records: List<SyncRecord>) {
            failure?.let { throw it }
            this.records += records
        }

        override suspend fun pull(token: String, cursor: String?): SyncPage {
            failure?.let { throw it }
            val from = cursor?.toInt() ?: 0
            if (from > records.size) throw SyncCursorExpired()
            val to = minOf(records.size, from + pageSize)
            return SyncPage(records.subList(from, to).toList(), to.toString(), to < records.size)
        }
    }

    private val server = FakeServer()

    private fun phone() = FakeLibraryRepository(emptyList(), emptyList(), clock)

    private fun accounts() = FakeAccountRepository(AccountState.SignedIn("Kevin", premium = false), token = "token")

    @Test
    fun `a place read on one phone reaches the other`() = runBlocking {
        val a = phone()
        val series = a.seriesFor("Moonlit Ferry", "asurascans.com", BigDecimal(12), "https://asurascans.com/f/12")
        a.recordPageRead(series.id, BigDecimal(12), page = 5, pageCount = 20, at = clock.instant())
        val aAccounts = accounts()
        LibrarySyncer(a, aAccounts, server, clock).sync()

        val b = phone()
        LibrarySyncer(b, accounts(), server, clock).sync()

        assertEquals(BigDecimal(12), b.series.first().single().progress?.chapter)
        assertEquals(clock.instant(), (aAccounts.account.value as AccountState.SignedIn).lastSyncedAt)
    }

    @Test
    fun `nothing is pushed again when nothing changed`() = runBlocking {
        val a = phone()
        a.seriesFor("Moonlit Ferry", "asurascans.com", BigDecimal(12), "https://asurascans.com/f/12")
        val later = Clock.offset(clock, java.time.Duration.ofMinutes(1))
        val syncer = LibrarySyncer(a, accounts(), server, later)
        syncer.sync()
        val pushed = server.records.size
        syncer.sync()
        assertEquals(pushed, server.records.size)
    }

    @Test
    fun `a cursor too old pulls everything again`() = runBlocking {
        val accounts = accounts()
        accounts.synced("token", com.ascon.core.model.SyncPosition(cursor = "99"))
        val other = phone()
        other.seriesFor("Moonlit Ferry", "asurascans.com", BigDecimal(12), "https://asurascans.com/f/12")
        LibrarySyncer(other, accounts(), server, clock).sync()

        val b = phone()
        LibrarySyncer(b, accounts, server, clock).sync()
        assertEquals("Moonlit Ferry", b.series.first().single().title)
    }

    @Test
    fun `a refused token signs the phone out`() = runBlocking {
        val accounts = accounts()
        val refusing = object : SyncBackend {
            override suspend fun push(token: String, records: List<SyncRecord>) = throw AccountRefused()

            override suspend fun pull(token: String, cursor: String?) = throw AccountRefused()
        }
        val syncer = LibrarySyncer(phone(), accounts, refusing, clock)
        syncer.sync()
        assertNull(accounts.token.value)
        assertEquals(SyncStatus.Idle, syncer.status.value)
    }

    @Test
    fun `an unreachable server fails the sync and keeps the position`() = runBlocking {
        server.failure = IOException("offline")
        val accounts = accounts()
        val syncer = LibrarySyncer(phone(), accounts, server, clock)
        syncer.sync()
        assertEquals(SyncStatus.Failed, syncer.status.value)
        assertNull(accounts.syncPosition().syncedAt)
    }
}
