package cl.baske.tv.di

import cl.baske.tv.core.DefaultDeviceProfileProvider
import cl.baske.tv.core.DeviceProfileProvider
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

actual fun platformModule(): Module = module {
    single<Settings> {
        NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
    }
    single<DeviceProfileProvider> { DefaultDeviceProfileProvider() }
}
