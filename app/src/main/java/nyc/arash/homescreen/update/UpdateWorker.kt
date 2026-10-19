package nyc.arash.homescreen.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nyc.arash.homescreen.BuildConfig

class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!BuildConfig.AUTO_UPDATE_ENABLED || BuildConfig.DEBUG) return@withContext Result.success()
        try {
            val ready = UpdateChecker(applicationContext).checkForUpdate()
                ?: return@withContext Result.success()
            // Background jobs only prepare updates. The visible launcher offers installation.
            UpdateReadyStore(applicationContext).save(ready.first)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (runAttemptCount < 4) Result.retry() else Result.failure()
        }
    }
}

object UpdateScheduler {
    private const val WORK_NAME = "homescreen-update-check"

    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInitialDelay(15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            req,
        )
    }
}
