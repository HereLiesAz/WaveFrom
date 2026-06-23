package com.hereliesaz.wavefrom.signal.model

/**
 * Which sensing pathway produced a detection. Determines the best localization
 * tier achievable and is shown in the HUD so the user knows how trustworthy a
 * marker's position is.
 */
enum class SourceType(val displayName: String) {
    WIFI("Wi-Fi"),
    BLE("Bluetooth"),
    CELLULAR("Cellular"),
    /** A phased-array SDR streaming true bearings over the network/USB. */
    EXTERNAL_SDR("SDR"),
    /** Phone radio paired with a USB dongle as a 2-element interferometer. */
    DUAL_RADIO("Dual-radio"),
    /** Synthetic source for development / projection validation. */
    SIMULATED("Simulated"),
}
