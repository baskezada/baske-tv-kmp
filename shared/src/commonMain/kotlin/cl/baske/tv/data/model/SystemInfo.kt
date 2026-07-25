package cl.baske.tv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Respuesta de `GET /System/Info/Public` — no requiere auth. Sirve para
 * validar que la URL ingresada es realmente un servidor Emby antes de pedir
 * credenciales.
 */
@Serializable
data class PublicSystemInfo(
    @SerialName("ServerName") val serverName: String? = null,
    @SerialName("Version") val version: String? = null,
    @SerialName("ProductName") val productName: String? = null,
    @SerialName("OperatingSystem") val operatingSystem: String? = null,
    @SerialName("Id") val id: String? = null,
    @SerialName("LocalAddress") val localAddress: String? = null,
)
