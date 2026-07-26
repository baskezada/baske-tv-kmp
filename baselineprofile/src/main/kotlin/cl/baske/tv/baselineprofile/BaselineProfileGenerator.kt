package cl.baske.tv.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Genera el baseline profile "recorriendo" la app: la abre y espera a que
 * cargue el Home. Lo que se ejecuta en ese recorrido se compila AOT al instalar,
 * reduciendo el jank del arranque y del primer scroll.
 *
 * Para generarlo (con un device/emulador API 28+ conectado):
 *   ./gradlew :androidApp:generateReleaseBaselineProfile
 * El resultado queda en androidApp/src/release/generated/baselineProfiles/.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "tv.baske.app",
        // También genera el "startup profile": optimiza específicamente el arranque.
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        // Dar tiempo a que cargue el Home (login persistido → entra directo).
        device.waitForIdle()
        Thread.sleep(3_000)
    }
}
