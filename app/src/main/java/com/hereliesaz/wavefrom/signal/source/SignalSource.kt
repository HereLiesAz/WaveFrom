package com.hereliesaz.wavefrom.signal.source

import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.SourceType
import kotlinx.coroutines.flow.Flow

/**
 * A pluggable producer of radio [Detection]s. Concrete sources wrap the phone's
 * own radios (Wi-Fi/BLE/cellular), an external SDR over the network/USB, or a
 * dual-radio interferometer.
 *
 * [detections] returns a cold [Flow]: collection starts the underlying scan and
 * cancellation stops it, so lifecycle is automatic. Implementations must fail
 * soft — if a permission is missing or the radio is off, return an empty flow
 * rather than throwing.
 */
interface SignalSource {
    val sourceType: SourceType

    /** Whether this source can produce data on the current device + permissions. */
    fun isAvailable(): Boolean

    fun detections(): Flow<Detection>
}
