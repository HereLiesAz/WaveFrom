package com.hereliesaz.wavefrom.signal.physics

import com.hereliesaz.wavefrom.signal.model.SignalBand

/**
 * Maps a frequency band to a display color, returned as a plain ARGB Int
 * (0xAARRGGBB) so the domain layer stays free of any Android/Compose types.
 * The UI converts this to its own color representation.
 */
object BandColor {
    fun argb(band: SignalBand): Int = when (band) {
        SignalBand.WIFI_2_4 -> 0xFF4FC3F7.toInt() // light blue
        SignalBand.WIFI_5 -> 0xFF7E57C2.toInt()   // purple
        SignalBand.WIFI_6 -> 0xFFAB47BC.toInt()   // violet
        SignalBand.BLUETOOTH -> 0xFF42A5F5.toInt() // blue
        SignalBand.CELL_LOW -> 0xFF66BB6A.toInt()  // green
        SignalBand.CELL_MID -> 0xFFFFCA28.toInt()  // amber
        SignalBand.CELL_HIGH -> 0xFFFF7043.toInt() // deep orange
        SignalBand.UNKNOWN -> 0xFFBDBDBD.toInt()   // grey
    }
}
