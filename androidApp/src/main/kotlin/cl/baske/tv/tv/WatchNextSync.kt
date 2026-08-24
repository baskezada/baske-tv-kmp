package cl.baske.tv.tv

import android.content.Context
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram

/** Un ítem de "Continuar viendo" listo para la fila Watch Next del launcher. */
data class WatchNextEntry(
    val id: String,
    val title: String,
    val description: String?,
    val posterUri: String?,
    val durationMs: Int,
    val positionMs: Int,
    val isEpisode: Boolean,
)

/**
 * Sincroniza la fila "Continuar viendo" (Watch Next) del home de Android TV /
 * Google TV con el resume del usuario. Inserta/actualiza cada ítem como
 * WATCH_NEXT_TYPE_CONTINUE (barra de progreso incluida) y borra los que ya no
 * están. Al tocar una card, el launcher abre `basketv://play/{id}` → MainActivity.
 *
 * Solo tiene efecto en dispositivos con TvProvider (Android TV); en teléfono la
 * consulta falla y se ignora (runCatching).
 */
object WatchNextSync {

    fun sync(context: Context, entries: List<WatchNextEntry>) {
        runCatching {
            val resolver = context.contentResolver
            // Existentes nuestros, indexados por internalProviderId (= item id Emby).
            val existing = HashMap<String, Long>()
            resolver.query(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                WatchNextProgram.PROJECTION,
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val p = WatchNextProgram.fromCursor(c)
                    p.internalProviderId?.let { existing[it] = p.id }
                }
            }

            val keep = entries.mapTo(HashSet()) { it.id }
            // Borrar los que salieron de "Continuar viendo".
            existing.forEach { (providerId, wnId) ->
                if (providerId !in keep) {
                    resolver.delete(TvContractCompat.buildWatchNextProgramUri(wnId), null, null)
                }
            }

            val now = System.currentTimeMillis()
            // Insertar/actualizar. lastEngagementTime decrece por orden para que el
            // launcher mantenga el orden del resume (más reciente primero).
            entries.forEachIndexed { index, e ->
                val values = WatchNextProgram.Builder()
                    .setType(
                        if (e.isEpisode) TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE
                        else TvContractCompat.WatchNextPrograms.TYPE_MOVIE,
                    )
                    .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                    .setLastEngagementTimeUtcMillis(now - index)
                    .setTitle(e.title)
                    .apply { e.description?.let { setDescription(it) } }
                    .apply { e.posterUri?.let { setPosterArtUri(Uri.parse(it)) } }
                    .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                    .setDurationMillis(e.durationMs)
                    .setLastPlaybackPositionMillis(e.positionMs.coerceIn(0, e.durationMs))
                    .setInternalProviderId(e.id)
                    .setIntentUri(Uri.parse("basketv://play/${e.id}"))
                    .build()
                    .toContentValues()

                val wnId = existing[e.id]
                if (wnId != null) {
                    resolver.update(TvContractCompat.buildWatchNextProgramUri(wnId), values, null, null)
                } else {
                    resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, values)
                }
            }
        }
    }
}
