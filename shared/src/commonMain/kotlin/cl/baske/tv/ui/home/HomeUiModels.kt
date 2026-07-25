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
    val error: String? = null,
)
