package cl.baske.tv.di

import cl.baske.tv.core.DeviceInfo
import cl.baske.tv.core.createHttpClient
import cl.baske.tv.data.AuthRepository
import cl.baske.tv.data.HomeRepository
import cl.baske.tv.data.PrefsStore
import cl.baske.tv.data.SessionStore
import cl.baske.tv.data.remote.EmbyApi
import cl.baske.tv.getPlatform
import org.koin.core.module.Module
import org.koin.dsl.module

const val APP_CLIENT_NAME = "BaskeTV"
const val APP_VERSION = "1.0"

/** Wiring común (red + datos). La `Settings` la aporta cada plataforma. */
val dataModule = module {
    single { createHttpClient() }
    single { SessionStore(get()) }
    single {
        DeviceInfo(
            clientName = APP_CLIENT_NAME,
            deviceName = getPlatform().name,
            deviceId = get<SessionStore>().deviceId(),
            version = APP_VERSION,
        )
    }
    single { EmbyApi(get(), get(), get(), get()) }
    single { AuthRepository(get(), get()) }
    single { HomeRepository(get()) }
    single { PrefsStore(get()) }
}

/** Provee la implementación nativa de `Settings` (SharedPreferences / NSUserDefaults). */
expect fun platformModule(): Module
