package cl.baske.tv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body de `POST /Users/AuthenticateByName`. Emby espera `Username` + `Pw`. */
@Serializable
data class AuthenticateByNameRequest(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val pw: String,
)

/** Respuesta de la autenticación. */
@Serializable
data class AuthenticationResult(
    @SerialName("User") val user: UserDto? = null,
    @SerialName("AccessToken") val accessToken: String? = null,
    @SerialName("ServerId") val serverId: String? = null,
)

@Serializable
data class UserDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("ServerId") val serverId: String? = null,
    @SerialName("Policy") val policy: UserPolicyDto? = null,
)

@Serializable
data class UserPolicyDto(
    @SerialName("IsAdministrator") val isAdministrator: Boolean = false,
)
