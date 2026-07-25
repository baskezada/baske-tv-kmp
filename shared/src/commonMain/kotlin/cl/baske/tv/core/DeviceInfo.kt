package cl.baske.tv.core

/** Identidad de este cliente frente a Emby (va en el header X-Emby-Authorization). */
data class DeviceInfo(
    val clientName: String,
    val deviceName: String,
    val deviceId: String,
    val version: String,
)

/**
 * Header `X-Emby-Authorization`. Emby lo exige incluso en el login (sin Token).
 * Una vez autenticado se reenvía con el `Token` para todos los requests.
 */
fun buildEmbyAuthHeader(device: DeviceInfo, token: String? = null): String = buildString {
    append("MediaBrowser ")
    append("Client=\"").append(device.clientName).append("\", ")
    append("Device=\"").append(device.deviceName).append("\", ")
    append("DeviceId=\"").append(device.deviceId).append("\", ")
    append("Version=\"").append(device.version).append("\"")
    if (token != null) append(", Token=\"").append(token).append("\"")
}

/**
 * Normaliza lo que tipeó el usuario: agrega esquema si falta y saca la barra
 * final, para poder concatenar rutas de forma segura (`$base/System/...`).
 */
fun normalizeServerUrl(raw: String): String {
    var url = raw.trim()
    if (url.isEmpty()) return url
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        url = "https://$url"
    }
    return url.trimEnd('/')
}
