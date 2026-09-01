package org.gtlv.core.job

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

data class JobMileageSnapshots(
    val jobId: String,
    val startedOdometerKilometers: Double?,
    val passengerOdometerKilometers: Double?
)

interface JobMileageStateStore {
    fun getSnapshots(userId: String): JobMileageSnapshots?

    fun recordJobStarted(
        userId: String,
        jobId: String,
        odometerKilometers: Double?
    )

    fun recordPersonCollected(
        userId: String,
        jobId: String,
        odometerKilometers: Double?
    )

    fun clear(userId: String)

    fun clearIfJobMatches(
        userId: String,
        jobId: String
    )
}

class JobMileageStore(
    context: Context
) : JobMileageStateStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

    override fun getSnapshots(
        userId: String
    ): JobMileageSnapshots? {
        val jobId = preferences.getString(
            jobKey(userId),
            null
        ) ?: return null

        return JobMileageSnapshots(
            jobId = jobId,
            startedOdometerKilometers =
                readOdometer(startedKey(userId)),
            passengerOdometerKilometers =
                readOdometer(passengerKey(userId))
        )
    }

    override fun recordJobStarted(
        userId: String,
        jobId: String,
        odometerKilometers: Double?
    ) {
        preferences.edit {
            putString(jobKey(userId), jobId)
            putOdometer(
                startedKey(userId),
                odometerKilometers
            )
            remove(passengerKey(userId))
        }
    }

    override fun recordPersonCollected(
        userId: String,
        jobId: String,
        odometerKilometers: Double?
    ) {
        val existing = getSnapshots(userId)

        preferences.edit {
            putString(jobKey(userId), jobId)
            if (existing?.jobId != jobId) {
                remove(startedKey(userId))
            }
            putOdometer(
                passengerKey(userId),
                odometerKilometers
            )
        }
    }

    override fun clear(userId: String) {
        preferences.edit {
            remove(jobKey(userId))
            remove(startedKey(userId))
            remove(passengerKey(userId))
        }
    }

    override fun clearIfJobMatches(
        userId: String,
        jobId: String
    ) {
        if (getSnapshots(userId)?.jobId == jobId) {
            clear(userId)
        }
    }

    private fun readOdometer(key: String): Double? {
        if (!preferences.contains(key)) return null

        return Double.fromBits(
            preferences.getLong(key, 0L)
        ).takeIf { it.isFinite() && it >= 0.0 }
    }

    private fun SharedPreferences.Editor.putOdometer(
        key: String,
        value: Double?
    ) {
        if (value != null && value.isFinite() && value >= 0.0) {
            putLong(key, value.toRawBits())
        } else {
            remove(key)
        }
    }

    private fun jobKey(userId: String) =
        "$JOB_PREFIX$userId"

    private fun startedKey(userId: String) =
        "$STARTED_PREFIX$userId"

    private fun passengerKey(userId: String) =
        "$PASSENGER_PREFIX$userId"

    private companion object {
        const val PREFERENCES_NAME =
            "atlas_job_mileage"
        const val JOB_PREFIX = "job_"
        const val STARTED_PREFIX = "started_"
        const val PASSENGER_PREFIX = "passenger_"
    }
}

interface JobMileageStoreProvider {
    val jobMileageStore: JobMileageStore
}
