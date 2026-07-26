package cl.baske.tv.ios

import cl.baske.tv.data.AuthRepository
import cl.baske.tv.data.PrefsStore
import cl.baske.tv.data.SessionStore
import cl.baske.tv.di.dataModule
import cl.baske.tv.di.platformModule
import cl.baske.tv.ui.connect.ConnectViewModel
import cl.baske.tv.ui.detail.DetailViewModel
import cl.baske.tv.ui.home.HomeViewModel
import cl.baske.tv.ui.library.LibraryViewModel
import cl.baske.tv.ui.player.PlayerViewModel
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

/**
 * Los ViewModels no van en `dataModule` (compartido con Android) porque ahí
 * Android los registra con el DSL de `koin-androidx-compose`
 * (ver androidApp/di/ViewModelModule.kt), que no aplica a un host sin Compose.
 * Acá son factories de Koin normales — Swift pide una instancia nueva por
 * pantalla y la retiene en un `ObservableObject` mientras esa vista viva.
 */
private val iosViewModelModule = module {
    factory { ConnectViewModel(get()) }
    factory { HomeViewModel(get(), get()) }
    factory { (itemId: String) -> PlayerViewModel(itemId, get(), get()) }
    factory { (itemId: String) -> DetailViewModel(itemId, get(), get()) }
    factory { (libraryId: String) -> LibraryViewModel(libraryId, get(), get()) }
}

/** Punto de entrada único desde Swift: `IosKoin.shared.start()` una vez al lanzar la app. */
object IosKoin {
    private var _koin: Koin? = null
    private val koin: Koin get() = _koin ?: error("IosKoin.start() no fue llamado")

    fun start() {
        if (_koin != null) return
        _koin = startKoin {
            modules(dataModule, platformModule(), iosViewModelModule)
        }.koin
    }

    val authRepository: AuthRepository get() = koin.get()
    val prefsStore: PrefsStore get() = koin.get()
    val sessionStore: SessionStore get() = koin.get()

    fun connectViewModel(): ConnectViewModel = koin.get()
    fun homeViewModel(): HomeViewModel = koin.get()
    fun playerViewModel(itemId: String): PlayerViewModel = koin.get { parametersOf(itemId) }
    fun detailViewModel(itemId: String): DetailViewModel = koin.get { parametersOf(itemId) }
    fun libraryViewModel(libraryId: String): LibraryViewModel = koin.get { parametersOf(libraryId) }
}
