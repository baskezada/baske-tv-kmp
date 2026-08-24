package cl.baske.tv.core

import android.media.MediaCodecList
import android.os.Build
import cl.baske.tv.data.model.DeviceProfile

/**
 * Arma el DeviceProfile según lo que ESTE dispositivo puede decodificar por
 * hardware (vía MediaCodec). Así un teléfono que decodifica HEVC 4K sigue en
 * direct-play, y un Chromecast que no puede con cierto códec/resolución hace que
 * Emby transcodifique — sin regresiones cruzadas, porque el perfil se adapta al
 * equipo.
 *
 * En API 29+ exigimos decoder por hardware (`isHardwareAccelerated`): un decoder
 * solo-software se omite del direct-play porque en equipos débiles no da abasto
 * (era justo el síntoma: buffer eterno al caer a software).
 */
class AndroidDeviceProfileProvider : DeviceProfileProvider {

    // MediaCodec mime -> nombre de códec que entiende Emby.
    private val mimeToEmby = mapOf(
        "video/avc" to "h264",
        "video/hevc" to "hevc",
        "video/x-vnd.on2.vp9" to "vp9",
        "video/x-vnd.on2.vp8" to "vp8",
        "video/av01" to "av1",
        "video/mp4v-es" to "mpeg4",
        "video/mpeg2" to "mpeg2video",
        "video/wvc1" to "vc1",
    )

    override fun deviceProfile(): DeviceProfile {
        val caps = detectVideoDecoders()
        return buildDeviceProfile(
            directVideoCodecs = caps.keys.toList(),
            resolutionCaps = caps,
        )
    }

    /** códec Emby -> (maxWidth, maxHeight) que el hardware puede decodificar. */
    private fun detectVideoDecoders(): Map<String, Pair<Int, Int>> {
        val result = mutableMapOf<String, Pair<Int, Int>>()
        val list = runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS) }.getOrNull() ?: return result
        val infos = runCatching { list.codecInfos }.getOrNull() ?: return result

        for (info in infos) {
            if (info.isEncoder) continue
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !info.isHardwareAccelerated) continue
            for (mime in info.supportedTypes) {
                val emby = mimeToEmby[mime.lowercase()] ?: continue
                val vcaps = runCatching {
                    info.getCapabilitiesForType(mime).videoCapabilities
                }.getOrNull() ?: continue
                val w = runCatching { vcaps.supportedWidths.upper }.getOrDefault(1920)
                val h = runCatching { vcaps.supportedHeights.upper }.getOrDefault(1080)
                val prev = result[emby]
                // Nos quedamos con el decoder que soporta la mayor resolución.
                if (prev == null || w.toLong() * h > prev.first.toLong() * prev.second) {
                    result[emby] = w to h
                }
            }
        }
        // Garantía mínima: siempre H264 (todo Android lo decodifica).
        if ("h264" !in result) result["h264"] = 1920 to 1080
        return result
    }
}
