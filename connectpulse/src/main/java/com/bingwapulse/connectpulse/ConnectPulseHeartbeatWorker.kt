package com.bingwapulse.connectpulse

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class ConnectPulseHeartbeatWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        val credentials = ConnectPulseSecureStore(applicationContext).load() ?: return Result.failure()
        return try {
            val status = ConnectPulseApi.heartbeat(applicationContext, credentials)
            if (status == "online") Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
