package cl.baske.tv.core

/**
 * Construye URLs de imágenes de Emby. Los endpoints de imagen son públicos
 * (no requieren token), así que Coil puede pedirlas directo.
 *
 * Se piden con `maxWidth` + `quality` para no traer el master gigante — la
 * misma disciplina de peso que ya aplicábamos en baske-tv.
 */
fun embyImageUrl(
    serverUrl: String,
    itemId: String,
    type: String = "Primary",
    tag: String? = null,
    maxWidth: Int = 320,
    quality: Int = 90,
): String {
    val base = "$serverUrl/Items/$itemId/Images/$type?maxWidth=$maxWidth&quality=$quality"
    return if (tag != null) "$base&tag=$tag" else base
}
