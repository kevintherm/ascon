package com.ascon.engine.adblock

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Implemented by the Application, so the update worker reaches the app's one [Adblock]. */
interface AdblockOwner {
    val adblock: Adblock
}

/** Refreshes the filter lists weekly, on Wi-Fi with battery to spare, then rebuilds the engine. */
class FilterListUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val adblock = (applicationContext as AdblockOwner).adblock
        val store = adblock.lists
        val updater = FilterListUpdater(current = store::text, fetch = ::download, save = store::save)
        if (updater.update(FilterList.All.filter(store::isEnabled))) adblock.start()
        Result.success()
    }

    private fun download(url: String): String? = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) response.body.string() else null
        }
    } catch (_: IOException) {
        null
    }

    companion object {
        private const val NAME = "filter-list-updates"
        private const val DAYS_BETWEEN = 7L
        private val client by lazy { OkHttpClient() }

        /** Call at app start. An existing schedule is kept. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FilterListUpdateWorker>(DAYS_BETWEEN, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
