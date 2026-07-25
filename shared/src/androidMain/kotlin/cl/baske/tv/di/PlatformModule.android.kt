package cl.baske.tv.di

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<Settings> {
        val prefs = androidContext().getSharedPreferences("baske_tv", Context.MODE_PRIVATE)
        SharedPreferencesSettings(prefs)
    }
}
