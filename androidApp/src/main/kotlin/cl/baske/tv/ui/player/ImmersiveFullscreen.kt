package cl.baske.tv.ui.player

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Mientras el player está en pantalla, oculta la status bar y la navigation bar
 * y dibuja edge-to-edge, para que el video ocupe TODA la pantalla. Sin esto, en
 * teléfono/tablet la nav bar tapa/recorta la parte de abajo del video. Se restaura
 * al salir. En Android TV no hay barras, así que es inofensivo.
 */
@Composable
fun ImmersiveFullscreen() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        if (window != null) WindowCompat.setDecorFitsSystemWindows(window, false)
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            if (window != null) WindowCompat.setDecorFitsSystemWindows(window, true)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
