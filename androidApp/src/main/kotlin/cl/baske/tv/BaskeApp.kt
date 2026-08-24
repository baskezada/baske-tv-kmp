package cl.baske.tv

import android.app.Application
import cl.baske.tv.data.PlayerEngine
import cl.baske.tv.data.PrefsStore
import cl.baske.tv.di.dataModule
import cl.baske.tv.di.platformModule
import cl.baske.tv.di.playerModule
import cl.baske.tv.di.viewModelModule
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.videolan.libvlc.LibVLC

class BaskeApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            // El logger de Koin loguea en cada resolución de dependencia — solo en debug.
            if (BuildConfig.DEBUG) androidLogger()
            androidContext(this@BaskeApp)
            modules(dataModule, platformModule(), viewModelModule, playerModule)
        }.koin

        // Pre-calienta el motor de libVLC en segundo plano (init caro: nativas +
        // plugin cache) SOLO si VLC es el motor elegido. Con mpv por defecto, la
        // mayoría no usa VLC, así que no se paga ese costo al arranque de gratis.
        Thread {
            runCatching {
                if (koin.get<PrefsStore>().prefs.value.playerEngine == PlayerEngine.Vlc) {
                    koin.get<LibVLC>()
                }
            }
        }.start()
    }

    /** ImageLoader global de Coil con fetcher de red vía OkHttp. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory())
                add(coil3.svg.SvgDecoder.Factory())
            }
            .build()
}
