package com.hereliesaz.wavefrom.signal.model

/**
 * Coarse frequency band an emitter lives in. Drives the HUD color and gives
 * the path-loss model a frequency to work with. [approxCenterHz] is a
 * representative center used when the exact channel frequency is unknown.
 */
enum class SignalBand(val displayName: String, val approxCenterHz: Long) {
    WIFI_2_4("Wi-Fi 2.4 GHz", 2_437_000_000L),
    WIFI_5("Wi-Fi 5 GHz", 5_500_000_000L),
    WIFI_6("Wi-Fi 6 GHz", 6_000_000_000L),
    BLUETOOTH("Bluetooth", 2_440_000_000L),
    CELL_LOW("Cellular (low)", 850_000_000L),
    CELL_MID("Cellular (mid)", 1_900_000_000L),
    CELL_HIGH("Cellular (high)", 3_500_000_000L),
    UNKNOWN("Unknown", 0L);

    /** Representative frequency in Hz, falling back to the band center. */
    fun frequencyHz(exactHz: Long?): Long =
        exactHz?.takeIf { it > 0 } ?: approxCenterHz

    companion object {
        /** Classify a Wi-Fi channel frequency (as reported in MHz). */
        fun fromWifiFrequencyMhz(mhz: Int): SignalBand = when {
            mhz in 2_400..2_500 -> WIFI_2_4
            mhz in 4_900..5_900 -> WIFI_5
            mhz in 5_925..7_125 -> WIFI_6
            else -> UNKNOWN
        }

        /** Classify any emitter from an absolute frequency in Hz (used for SDR bearings). */
        fun fromFrequencyHz(hz: Long): SignalBand = when {
            hz <= 0L -> UNKNOWN
            hz < 1_000_000_000L -> CELL_LOW
            hz in 2_400_000_000L..2_500_000_000L -> WIFI_2_4
            hz < 3_000_000_000L -> CELL_MID
            hz in 4_900_000_000L..5_900_000_000L -> WIFI_5
            hz in 5_925_000_000L..7_125_000_000L -> WIFI_6
            else -> CELL_HIGH
        }

        /** Classify a cellular frequency given a downlink frequency in kHz (EARFCN/NRARFCN-derived). */
        fun fromCellFrequencyKhz(khz: Int?): SignalBand {
            if (khz == null || khz <= 0) return CELL_MID
            val hz = khz.toLong() * 1_000L
            return when {
                hz < 1_000_000_000L -> CELL_LOW
                hz < 3_000_000_000L -> CELL_MID
                else -> CELL_HIGH
            }
        }
    }
}
