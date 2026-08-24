package cl.baske.tv.ui.player

import android.content.Context
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.jdtech.mpv.MPVLib
import kotlin.math.roundToInt

/**
 * Motor de reproducción alternativo basado en mpv (libmpv). Más fiable que libVLC
 * en algunos equipos (p.ej. ChromeOS/ARC) y trae libass integrado (ASS completo).
 * Envuelve MPVLib: expone estado observable por Compose y métodos de control.
 *
 * El HTTP de mpv (ffmpeg) descomprime gzip/deflate bien → NO necesita el
 * HLS-rewrite que sí requiere libVLC.
 */
class MpvPlayer(context: Context) {
    // Formatos y eventos de mpv (client.h) — estables.
    private companion object {
        const val FORMAT_FLAG = 3
        const val FORMAT_INT64 = 4
        const val FORMAT_DOUBLE = 5
        const val EVENT_END_FILE = 7
        const val EVENT_FILE_LOADED = 8
        const val EVENT_PLAYBACK_RESTART = 21
    }

    private val mpv = MPVLib.create(context)!!

    // ---- Estado observable (Compose) ----
    var positionMs by mutableLongStateOf(0L); private set
    var durationMs by mutableLongStateOf(0L); private set
    var paused by mutableStateOf(false); private set
    /** Primer frame de video ya decodificado (equivalente al Vout de VLC). */
    var videoReady by mutableStateOf(false); private set
    var ended by mutableStateOf(false); private set
    var errored by mutableStateOf(false); private set

    private val observer = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) {}
        override fun eventProperty(property: String, value: Long) {
            when (property) {
                "time-pos" -> positionMs = value * 1000
                "duration" -> durationMs = value * 1000
                "width" -> if (value > 0) videoReady = true
            }
        }
        override fun eventProperty(property: String, value: Double) {
            when (property) {
                "time-pos" -> positionMs = (value * 1000).toLong()
                "duration" -> durationMs = (value * 1000).toLong()
            }
        }
        override fun eventProperty(property: String, value: Boolean) {
            when (property) {
                "pause" -> paused = value
                "eof-reached" -> if (value) ended = true
            }
        }
        override fun eventProperty(property: String, value: String) {}
        override fun event(eventId: Int) {
            when (eventId) {
                EVENT_END_FILE -> ended = true
                EVENT_PLAYBACK_RESTART, EVENT_FILE_LOADED -> { /* arrancó */ }
            }
        }
    }

    private val logObserver = object : MPVLib.LogObserver {
        override fun logMessage(prefix: String, level: Int, text: String) {
            android.util.Log.i("MPV", "[$prefix] $text")
        }
    }

    fun init() {
        // Opciones ANTES de init().
        mpv.setOptionString("config", "no")
        // No cargar los scripts Lua embebidos (ytdl_hook, auto_profiles…): no los
        // usamos y corren en CADA archivo (overhead + el hook de ytdl inútil aquí).
        mpv.setOptionString("load-scripts", "no")
        mpv.setOptionString("ytdl", "no")
        mpv.setOptionString("vo", "gpu")
        mpv.setOptionString("gpu-context", "android")
        mpv.setOptionString("opengl-es", "yes")
        mpv.setOptionString("hwdec", "mediacodec")     // HW; mpv cae a software solo si hace falta
        mpv.setOptionString("ao", "audiotrack")
        mpv.setOptionString("force-window", "no")
        mpv.setOptionString("keep-open", "yes")        // que dispare eof-reached sin descargar
        mpv.setOptionString("save-position-on-quit", "no")
        mpv.setOptionString("network-timeout", "30")
        mpv.init()
        mpv.addLogObserver(logObserver)
        mpv.addObserver(observer)
        mpv.observeProperty("time-pos", FORMAT_DOUBLE)
        mpv.observeProperty("duration", FORMAT_DOUBLE)
        mpv.observeProperty("pause", FORMAT_FLAG)
        mpv.observeProperty("eof-reached", FORMAT_FLAG)
        mpv.observeProperty("width", FORMAT_INT64)     // >0 → hay frame de video
    }

    fun attachSurface(surface: Surface, width: Int, height: Int) {
        mpv.attachSurface(surface)
        mpv.setOptionString("android-surface-size", "${width}x$height")
        mpv.setPropertyString("vo", "gpu")
    }

    fun detachSurface() {
        runCatching { mpv.setPropertyString("vo", "null") }
        runCatching { mpv.detachSurface() }
    }

    fun loadFile(url: String, startPositionMs: Long = 0) {
        ended = false; videoReady = false; errored = false
        // Sintaxis mpv: loadfile <url> <flags> <index> <options>. El resume va en
        // <options> (start=segundos); <index>=0 es obligatorio como posicional antes
        // de las options (si no, mpv interpreta "start=.." como el index → error).
        if (startPositionMs > 3_000) {
            mpv.command(arrayOf("loadfile", url, "replace", "0", "start=${startPositionMs / 1000}"))
        } else {
            mpv.command(arrayOf("loadfile", url))
        }
    }

    fun play() { runCatching { mpv.setPropertyBoolean("pause", false) } }
    fun pause() { runCatching { mpv.setPropertyBoolean("pause", true) } }
    fun togglePlayPause() { if (paused) play() else pause() }
    fun seekTo(ms: Long) { runCatching { mpv.command(arrayOf("seek", "${ms / 1000.0}", "absolute")) } }
    fun seekBy(deltaMs: Long) { runCatching { mpv.command(arrayOf("seek", "${deltaMs / 1000.0}", "relative")) } }

    /** Agrega y selecciona un subtítulo externo (ASS/SRT) — mpv lo renderiza con libass. */
    fun addSubtitle(url: String) { runCatching { mpv.command(arrayOf("sub-add", url, "select")) } }
    fun disableSubtitle() { runCatching { mpv.setPropertyString("sid", "no") } }

    /** Lista de pistas (audio/sub) del track-list de mpv, para los paneles. */
    fun trackCount(): Int = runCatching { mpv.getPropertyInt("track-list/count") ?: 0 }.getOrDefault(0)
    fun trackType(i: Int): String? = runCatching { mpv.getPropertyString("track-list/$i/type") }.getOrNull()
    fun trackTitle(i: Int): String? = runCatching {
        mpv.getPropertyString("track-list/$i/title") ?: mpv.getPropertyString("track-list/$i/lang")
    }.getOrNull()
    fun trackId(i: Int): Int = runCatching { mpv.getPropertyInt("track-list/$i/id") ?: -1 }.getOrDefault(-1)
    fun trackSelected(i: Int): Boolean = runCatching { mpv.getPropertyBoolean("track-list/$i/selected") ?: false }.getOrDefault(false)
    fun setAudioTrack(id: Int) { runCatching { mpv.setPropertyInt("aid", id) } }

    /** "Stats for nerds": propiedades en vivo de mpv (resolución, códec, decoder, fps…). */
    fun stats(): List<Pair<String, String>> {
        fun str(p: String) = runCatching { mpv.getPropertyString(p) }.getOrNull()?.takeIf { it.isNotBlank() }
        fun int(p: String) = runCatching { mpv.getPropertyInt(p) }.getOrNull()
        fun dbl(p: String) = runCatching { mpv.getPropertyDouble(p) }.getOrNull()
        return buildList {
            add("Motor" to "mpv")
            val w = int("dwidth") ?: int("width")
            val h = int("dheight") ?: int("height")
            if (w != null && h != null) add("Resolución" to "${w}×$h")
            str("video-codec")?.let { add("Códec video" to it) }
            str("audio-codec-name")?.let { add("Códec audio" to it) }
            str("hwdec-current")?.let { add("Decodificación" to if (it == "no") "software" else it) }
            (dbl("container-fps") ?: dbl("estimated-vf-fps"))?.let { add("FPS" to it.roundToInt().toString()) }
            int("video-bitrate")?.let { add("Bitrate" to "${it / 1000} kbps") }
            dbl("demuxer-cache-duration")?.let { add("Buffer" to "${it.roundToInt()} s") }
            int("frame-drop-count")?.let { if (it > 0) add("Frames caídos" to it.toString()) }
        }
    }

    fun destroy() {
        runCatching { mpv.removeObserver(observer) }
        runCatching { mpv.removeLogObserver(logObserver) }
        runCatching { mpv.command(arrayOf("stop")) }
        detachSurface()
        runCatching { mpv.destroy() }
    }
}
