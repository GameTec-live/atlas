package org.gtlv.core.location

import kotlinx.coroutines.flow.StateFlow

interface LocationProvider {

    val state: StateFlow<LocationState>

    fun start()

    fun stop()
}