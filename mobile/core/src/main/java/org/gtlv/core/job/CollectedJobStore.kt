package org.gtlv.core.job

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface CollectedJobStateStore {
    fun getCollectedJobId(userId: String): String?

    fun setCollectedJobId(
        userId: String,
        jobId: String
    )

    fun clearCollectedJobId(userId: String)
}

class CollectedJobStore(
    context: Context
) : CollectedJobStateStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
    private val collectedJobFlows =
        mutableMapOf<String, MutableStateFlow<String?>>()

    override fun getCollectedJobId(userId: String): String? {
        return preferences.getString(
            keyFor(userId),
            null
        )
    }

    override fun setCollectedJobId(
        userId: String,
        jobId: String
    ) {
        preferences.edit {
            putString(keyFor(userId), jobId)
        }
        flowFor(userId).value = jobId
    }

    override fun clearCollectedJobId(userId: String) {
        preferences.edit {
            remove(keyFor(userId))
        }
        flowFor(userId).value = null
    }

    fun observeCollectedJobId(
        userId: String
    ): StateFlow<String?> {
        return flowFor(userId)
    }

    private fun flowFor(
        userId: String
    ): MutableStateFlow<String?> {
        return synchronized(collectedJobFlows) {
            collectedJobFlows.getOrPut(userId) {
                MutableStateFlow(
                    preferences.getString(
                        keyFor(userId),
                        null
                    )
                )
            }
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

interface CollectedJobStoreProvider {
    val collectedJobStore: CollectedJobStore
}
