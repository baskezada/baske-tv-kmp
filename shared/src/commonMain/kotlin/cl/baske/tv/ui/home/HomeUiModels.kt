package cl.baske.tv.ui.home

/** A dónde navega una card al hacer click. */
enum class NavTarget { Player, Detail, Library }

/** Card lista para pintar: la UI no sabe nada de Emby, solo muestra esto. */
data class HomeCard(
    val id: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    /** 0f..1f, solo relevante en "Continuar viendo". */
    val progress: Float = 0f,
    /** Si al hacer click debe ir directo a reproducir (episodio/resume). */
    val playDirect: Boolean = false,
    val navTarget: NavTarget = NavTarget.Detail,
    // ---- Para el banner/hero (Vitrina) ----
    val backdropUrl: String? = null,
    val logoUrl: String? = null,
    val overview: String? = null,
    /** Línea de metadatos del hero, ej. "Película · 2023". */
    val meta: String? = null,
    /** Etiqueta superior del hero, ej. "SERIE". */
    val kicker: String? = null,
    val rating: Double? = null,
    /** Clasificación por edad (OfficialRating), ej. "14". Para la meta del hero. */
    val officialRating: String? = null,
    /** Duración en ticks (para mostrar "24m" en la meta del hero). */
    val runtimeTicks: Long? = null,
    // ---- Para el menú contextual "more" (depende del tipo, como la web) ----
    /** Tipo Emby crudo: "Movie" | "Series" | "Episode" | ... o null para bibliotecas. */
    val type: String? = null,
    val played: Boolean = false,
    val isFavorite: Boolean = false,
    /** Para episodios: id de la serie ("Ir a la serie"). */
    val seriesId: String? = null,
    /** true si es una card de biblioteca (Vista/CollectionFolder) → "Escanear biblioteca". */
    val isLibrary: Boolean = false,
)

data class HomeRow(
    val id: String,
    val title: String,
    val cards: List<HomeCard>,
    /** true = poster 2:3, false = apaisado 16:9. */
    val portrait: Boolean,
)

data class HomeUiState(
    val loading: Boolean = true,
    val rows: List<HomeRow> = emptyList(),
    /** Ítems destacados que rotan en el banner (como el carrusel del web). */
    val featured: List<HomeCard> = emptyList(),
    val error: String? = null,
)
