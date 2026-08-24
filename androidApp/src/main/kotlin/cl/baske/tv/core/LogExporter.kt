package cl.baske.tv.core

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Vuelca los logs del PROPIO proceso (logcat -d — incluye mpv, VLC y los Log de la
 * app; en Android una app lee solo sus propios logs sin permiso especial) a un
 * archivo y abre el share sheet para mandarlo. Sirve para diagnosticar en equipos
 * sin adb (p.ej. tablets ChromeOS).
 */
object LogExporter {
    fun export(context: Context) {
        val logs = runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", "6000"))
            process.inputStream.bufferedReader().use { it.readText() }
        }.getOrElse { "No se pudo leer logcat: ${it.message}" }

        val file = File(context.cacheDir, "basketv-log.txt")
        runCatching { file.writeText(logs) }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "BaskeTV logs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(
                Intent.createChooser(share, "Exportar logs").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
