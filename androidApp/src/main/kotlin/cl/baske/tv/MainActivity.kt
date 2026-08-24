package cl.baske.tv

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cl.baske.tv.ui.RootApp
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    // Id pedido por un deep link (card de Watch Next del launcher: basketv://play/{id}).
    private val deepLinkPlay = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Estilo "dark" en ambas barras → íconos del sistema SIEMPRE claros
        // (blancos), sin importar el tema del sistema. La app es oscura y a menudo
        // hay un hero brillante detrás, así que los íconos oscuros no se leían.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        handleIntent(intent)
        setContent {
            RootApp(
                deepLinkPlay = deepLinkPlay,
                onDeepLinkConsumed = { deepLinkPlay.value = null },
            )
        }
    }

    // La app ya estaba abierta y el launcher manda otro deep link (singleTask no,
    // pero igual llega acá si la task se reusa): re-parsear.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "basketv" && data.host == "play") {
            data.lastPathSegment?.takeIf { it.isNotBlank() }?.let { deepLinkPlay.value = it }
        }
    }
}
