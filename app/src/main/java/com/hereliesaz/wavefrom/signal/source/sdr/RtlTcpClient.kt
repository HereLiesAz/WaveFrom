package com.hereliesaz.wavefrom.signal.source.sdr

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Minimal `rtl_tcp` protocol client. Talks to a no-root `rtl_tcp_andro` driver
 * serving an RTL-SDR attached to the phone over USB (or any remote `rtl_tcp`).
 * On connect the server sends a 12-byte dongle header; commands are 5 bytes
 * (1 opcode + big-endian uint32); the IQ stream is interleaved unsigned 8-bit
 * I/Q centred at 127.5.
 */
class RtlTcpClient(private val host: String, private val port: Int) {

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: OutputStream? = null

    fun connect(timeoutMs: Int = 5000) {
        val s = Socket()
        s.connect(InetSocketAddress(host, port), timeoutMs)
        socket = s
        input = DataInputStream(BufferedInputStream(s.getInputStream()))
        output = s.getOutputStream()
        // Consume the 12-byte dongle info header ("RTL0" + tuner type + gain count).
        val header = ByteArray(12)
        input!!.readFully(header)
    }

    fun setCenterFreqHz(hz: Int) = send(CMD_SET_FREQ, hz)
    fun setSampleRateHz(hz: Int) = send(CMD_SET_SAMPLE_RATE, hz)
    fun setGainModeManual(manual: Boolean) = send(CMD_SET_GAIN_MODE, if (manual) 1 else 0)
    fun setAgcMode(on: Boolean) = send(CMD_SET_AGC_MODE, if (on) 1 else 0)

    /** Read [n] complex samples into [re]/[im], normalised to roughly [-1, 1]. */
    fun readSamples(n: Int, re: FloatArray, im: FloatArray) {
        val buf = ByteArray(n * 2)
        input!!.readFully(buf)
        for (i in 0 until n) {
            re[i] = ((buf[2 * i].toInt() and 0xFF) - 127.5f) / 127.5f
            im[i] = ((buf[2 * i + 1].toInt() and 0xFF) - 127.5f) / 127.5f
        }
    }

    private fun send(cmd: Int, param: Int) {
        output?.write(command(cmd, param))
        output?.flush()
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
    }

    companion object {
        const val CMD_SET_FREQ = 0x01
        const val CMD_SET_SAMPLE_RATE = 0x02
        const val CMD_SET_GAIN_MODE = 0x03
        const val CMD_SET_AGC_MODE = 0x08

        /** Encode a 5-byte rtl_tcp command (opcode + big-endian uint32). */
        fun command(cmd: Int, param: Int): ByteArray = byteArrayOf(
            cmd.toByte(),
            (param ushr 24).toByte(),
            (param ushr 16).toByte(),
            (param ushr 8).toByte(),
            param.toByte(),
        )
    }
}
