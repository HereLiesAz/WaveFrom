package com.hereliesaz.wavefrom.signal.record

import android.content.Context
import java.io.BufferedWriter
import java.io.File

/** An open recording: the backing file and a writer the recorder appends lines to. */
class RecordingHandle(val file: File, val writer: BufferedWriter) {
    fun close() = runCatching { writer.flush(); writer.close() }
}

/**
 * On-device storage for recorded sessions: `.wfrec` files under the app's
 * `filesDir/recordings` folder, each a newline-delimited [SessionFormat] file. Thin
 * wrapper over [File] so the recorder and the replay loader share one location and
 * naming scheme.
 */
class SessionStore(context: Context) {

    private val appContext = context.applicationContext
    private val dir: File
        get() = File(appContext.filesDir, "recordings").apply { mkdirs() }

    /** Open a new recording, writing the format header. [name] is a base file name. */
    fun create(name: String): RecordingHandle {
        val file = File(dir, "$name.wfrec")
        val writer = file.bufferedWriter()
        try {
            writer.appendLine(SessionFormat.header())
            return RecordingHandle(file, writer)
        } catch (t: Throwable) {
            runCatching { writer.close() } // don't leak the writer if the header fails
            throw t
        }
    }

    /** Recordings, newest first. */
    fun list(): List<File> =
        dir.listFiles { f -> f.isFile && f.extension == EXT }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** Load a recording into a replayable session (label defaults to the file name). */
    fun load(file: File, speed: Float = 1f, loop: Boolean = true): ReplaySession {
        val records = file.useLines { SessionFormat.parse(it) }
        return ReplaySession(records, speed = speed, loop = loop, label = file.nameWithoutExtension)
    }

    fun delete(file: File): Boolean = file.delete()

    companion object {
        const val EXT = "wfrec"
    }
}
