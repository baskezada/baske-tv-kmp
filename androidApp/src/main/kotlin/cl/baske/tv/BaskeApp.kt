package cl.baske.tv

import android.app.Application
import cl.baske.tv.di.dataModule
import cl.baske.tv.di.platformModule
import cl.baske.tv.di.viewModelModule
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class BaskeApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            // El logger de Koin loguea en cada resolución de dependencia — solo en debug.
            if (BuildConfig.DEBUG) androidLogger()
            androidContext(this@BaskeApp)
            modules(dataModule, platformModule(), viewModelModule)
        }
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
