package com.existtracker.app

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads last night's total sleep (in minutes) from Health Connect.
 * Returns -1 if Health Connect isn't available or permission isn't granted,
 * so the rest of the app degrades gracefully.
 *
 * Written in Kotlin only because Health Connect's API uses suspend functions;
 * we expose a plain blocking method that Java code can call on a background thread.
 */
object SleepReader {

    @JvmField
    val SLEEP_PERMISSION: String =
        HealthPermission.getReadPermission(SleepSessionRecord::class)

    @JvmStatic
    fun isAvailable(context: Context): Boolean {
        return HealthConnectClient.getSdkStatus(context) ==
                HealthConnectClient.SDK_AVAILABLE
    }

    /** Total minutes asleep during last night, looking back over the past day. */
    @JvmStatic
    fun readLastNightMinutes(context: Context): Int {
        return try {
            if (!isAvailable(context)) return -1
            val client = HealthConnectClient.getOrCreate(context)
            runBlocking {
                // Window: from 6pm yesterday to now, captures a normal night.
                val zone = ZoneId.systemDefault()
                val start = LocalDate.now().minusDays(1)
                    .atTime(18, 0).atZone(zone).toInstant()
                val end = Instant.now()

                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = SleepSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(start, end)
                    )
                )
                var totalMin = 0L
                for (session in response.records) {
                    totalMin += Duration.between(session.startTime, session.endTime).toMinutes()
                }
                totalMin.toInt()
            }
        } catch (e: Exception) {
            -1
        }
    }
}
