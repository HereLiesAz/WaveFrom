package com.hereliesaz.wavefrom.signal.model

/**
 * Stable identity of an emitter. [key] is what the aggregator de-duplicates on
 * across scans (BSSID / MAC / cell global id), [label] is human-friendly.
 */
data class Identity(
    val key: String,
    val label: String,
    val vendor: String? = null,
)
