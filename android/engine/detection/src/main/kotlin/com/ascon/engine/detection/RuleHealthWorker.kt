package com.ascon.engine.detection

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ascon.core.data.RuleStore
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Implemented by the Application, so the health worker reaches the app's rule store and backend. */
interface RuleHealthOwner {
    val ruleStore: RuleStore
    val ruleBackend: RuleBackend?
}

/** Sends the rule health counts once a day, then takes what was sent off the device. */
class RuleHealthWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val owner = applicationContext as RuleHealthOwner
        val backend = owner.ruleBackend ?: return Result.success()
        return try {
            owner.ruleStore.health().chunked(MAX_ENTRIES).forEach { batch ->
                backend.sendHealth(batch)
                owner.ruleStore.removeHealth(batch)
            }
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "rule-health"

        /** The API's limit on one batch. */
        private const val MAX_ENTRIES = 500

        /** Call at app start. An existing schedule is kept. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RuleHealthWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
