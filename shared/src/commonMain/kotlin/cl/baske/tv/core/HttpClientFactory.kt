package cl.baske.tv.core

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Json tolerante: Emby manda montones de campos que no modelamos. */
val embyJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

/**
 * Un único HttpClient para toda la app. No fija baseUrl porque la URL del
 * servidor la elige el usuario en tiempo de ejecución — cada request pasa la
 * URL completa.
 */
fun createHttpClient(): HttpClient = HttpClient {
    expectSuccess = true
    install(ContentNegotiation) {
        json(embyJson)
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
    }
    install(Logging) {
        level = LogLevel.INFO
    }
}
