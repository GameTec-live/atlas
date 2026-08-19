package org.gtlv.core.job

import android.content.Context
import androidx.core.content.edit

class CollectedJobStore(
    context: Context
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

    fun getCollectedJobId(userId: String): String? {
        return preferences.getString(
            keyFor(userId),
            null
        )
    }

    fun setCollectedJobId(
        userId: String,
        jobId: String
    ) {
        preferences.edit {
            putString(keyFor(userId), jobId)
        }
    }

    fun clearCollectedJobId(userId: String) {
        preferences.edit {
            remove(keyFor(userId))
        }
    }
    private fun keyFor(userId: String): String {
        return "$COLLECTED_JOB_PREFIX$userId"
    }

    private companion object {
        const val PREFERENCES_NAME =
            "atlas_collected_jobs"

        const val COLLECTED_JOB_PREFIX =
            "collected_job_"
    }
}