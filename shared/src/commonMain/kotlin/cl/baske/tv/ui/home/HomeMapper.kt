package cl.baske.tv.ui.home

import cl.baske.tv.core.embyImageUrl
import cl.baske.tv.data.model.BaseItemDto

/**
 * Convierte un ítem de Emby en una card lista para la UI, resolviendo las URLs
 * de imagen (poster/apaisada + backdrop y logo para el hero) contra el servidor.
 */
fun BaseItemDto.toHomeCard(serverUrl: String, wide: Boolean): HomeCard {
    val isEpisode = type == "Episode"
    val title = if (isEpisode) (seriesName ?: name ?: "") else (name ?: "")
    val subtitle = when {
        isEpisode -> buildString {
            append("T").append(parentIndexNumber ?: "?")
            append(":E").append(indexNumber ?: "?")
            name?.let { append(" · ").append(it) }
        }
        type == "Movie" -> "Película" + (productionYear?.let { " · $it" } ?: "")
        else -> productionYear?.toString()
    }

    val primaryTag = imageTags?.get("Primary")
    val backdropTag = backdropImageTags?.firstOrNull()
    val logoTag = imageTags?.get("Logo")

    val (imageType, tag) =
        if (wide && backdropTag != null) "Backdrop" to backdropTag else "Primary" to primaryTag
    val maxWidth = if (wide) 480 else 320
    val imageUrl = embyImageUrl(serverUrl, id, imageType, tag, maxWidth)

    // Backdrop grande para el hero (episodios: cae al still Primary 16:9).
    val backdropUrl = when {
        backdropTag != null -> embyImageUrl(serverUrl, id, "Backdrop", backdropTag, 1280)
        isEpisode && primaryTag != null -> embyImageUrl(serverUrl, id, "Primary", primaryTag, 1280)
        else -> null
    }
    val logoUrl = logoTag?.let { embyImageUrl(serverUrl, id, "Logo", it, 480) }

    val meta = when (type) {
        "Movie" -> listOfNotNull("Película", productionYear?.toString(), officialRating).joinToString(" · ")
        "Series" -> listOfNotNull("Serie", productionYear?.toString(), officialRating).joinToString(" · ")
        "Episode" -> listOfNotNull(seriesName, subtitle).joinToString(" · ")
        else -> listOfNotNull(productionYear?.toString()).joinToString(" · ").ifEmpty { null }
    }

    val progress = if ((runTimeTicks ?: 0) > 0) {
        (userData?.playbackPositionTicks ?: 0).toFloat() / runTimeTicks!!.toFloat()
    } else 0f

    val kicker = when (type) {
        "Movie" -> "PELÍCULA"
        "Series" -> "SERIE"
        "Episode" -> "EPISODIO"
        else -> null
    }

    val navTarget = when {
        collectionType != null -> NavTarget.Library // ítem de "Vistas" = biblioteca
        isEpisode -> NavTarget.Player
        else -> NavTarget.Detail
    }

    return HomeCard(
        id = id,
        title = title,
        subtitle = subtitle,
        imageUrl = imageUrl,
        progress = progress.coerceIn(0f, 1f),
        playDirect = isEpisode || type == "Movie",
        navTarget = navTarget,
        backdropUrl = backdropUrl,
        logoUrl = logoUrl,
        overview = overview,
        meta = meta,
        kicker = kicker,
        rating = communityRating,
        officialRating = officialRating,
        runtimeTicks = runTimeTicks,
        type = type,
        played = userData?.played ?: false,
        isFavorite = userData?.isFavorite ?: false,
        seriesId = seriesId,
        isLibrary = collectionType != null,
    )
}
