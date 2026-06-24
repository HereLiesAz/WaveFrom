package com.hereliesaz.wavefrom.signal.source.sdr

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

/**
 * Recognizes USB SDRs and wireless dongles attached to the phone over OTG. Used
 * by [UsbSdrSource] and [com.hereliesaz.wavefrom.signal.source.dualradio.DualRadioSource]
 * to report availability; the actual IQ capture / DSP is a later step (and on
 * hardware Android can't drive, the Raspberry Pi pod handles it instead).
 */
object UsbDeviceCatalog {

    data class KnownDevice(val vendorId: Int, val productId: Int, val name: String)

    /** Common USB SDRs by USB vendor:product id. */
    val KNOWN_SDRS = listOf(
        KnownDevice(0x0bda, 0x2838, "RTL-SDR (RTL2832U)"),
        KnownDevice(0x0bda, 0x2832, "RTL-SDR (RTL2832U)"),
        KnownDevice(0x1d50, 0x6089, "HackRF One"),
        KnownDevice(0x1d50, 0x60a1, "Airspy"),
        KnownDevice(0x1d50, 0x6108, "Myriad-RF LimeSDR"),
    )

    private fun manager(context: Context): UsbManager? =
        context.applicationContext.getSystemService(Context.USB_SERVICE) as? UsbManager

    /** Attached USB devices recognized as SDRs. */
    fun attachedSdrs(context: Context): List<UsbDevice> {
        val devices = manager(context)?.deviceList?.values ?: return emptyList()
        return devices.filter { dev ->
            KNOWN_SDRS.any { it.vendorId == dev.vendorId && it.productId == dev.productId }
        }
    }

    /**
     * Attached USB devices that look like a wireless radio (Wi-Fi/BT dongle),
     * usable as a second antenna for interferometry. Identified by the USB
     * wireless-controller device/interface class.
     */
    fun attachedWirelessDongles(context: Context): List<UsbDevice> {
        val devices = manager(context)?.deviceList?.values ?: return emptyList()
        return devices.filter { dev ->
            dev.deviceClass == UsbConstants.USB_CLASS_WIRELESS_CONTROLLER ||
                (0 until dev.interfaceCount).any {
                    dev.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_WIRELESS_CONTROLLER
                }
        }
    }

    fun nameFor(device: UsbDevice): String =
        KNOWN_SDRS.firstOrNull { it.vendorId == device.vendorId && it.productId == device.productId }
            ?.name ?: (device.productName ?: "USB device")
}
