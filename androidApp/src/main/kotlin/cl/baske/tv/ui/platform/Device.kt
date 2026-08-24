package cl.baske.tv.ui.platform

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * En qué tipo de pantalla estamos corriendo. Lo que separa Tv del resto no es el
 * tamaño sino el modelo de input: en Tv no hay puntero, todo se navega con D-pad,
 * así que cada control tiene que ser enfocable y hay que pintar el anillo de foco.
 */
enum class FormFactor { Tv, Phone, Tablet }

@Immutable
data class Device(
    val formFactor: FormFactor,
    val widthDp: Int,
    val heightDp: Int,
) {
    val isTv: Boolean get() = formFactor == FormFactor.Tv

    /** Hay puntero: los controles se tocan, no se enfocan. */
    val isTouch: Boolean get() = formFactor != FormFactor.Tv

    val isPhone: Boolean get() = formFactor == FormFactor.Phone

    val isPortrait: Boolean get() = heightDp > widthDp

    val metrics: Metrics get() = Metrics.forDevice(this)
}

val LocalDevice = staticCompositionLocalOf {
    Device(FormFactor.Tv, widthDp = 960, heightDp = 540)
}

@Composable
fun rememberDevice(): Device {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp
    return remember(width, height) {
        Device(
            formFactor = when {
                context.isTelevision() -> FormFactor.Tv
                // 600dp es el corte clásico de Android entre teléfono y tablet, y
                // coincide con el breakpoint "compact" de WindowSizeClass.
                minOf(width, height) >= 600 -> FormFactor.Tablet
                else -> FormFactor.Phone
            },
            widthDp = width,
            heightDp = height,
        )
    }
}

private fun Context.isTelevision(): Boolean {
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
    // Algunos TV box no setean el uiMode pero sí declaran leanback.
    return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

/**
 * Medidas de layout por form factor. Están todas acá para que las pantallas no
 * tengan que repetir el mismo `if (isTv) 40.dp else 16.dp` en cada padding.
 */
@Immutable
data class Metrics(
    /** Padding lateral de las pantallas. En Tv es overscan, en teléfono es margen. */
    val gutter: Dp,
    val headerHeight: Dp,
    /** Alto del hero como fracción del alto disponible. */
    val heroFraction: Float,
    val heroTitleSize: TextUnit,
    val heroOverviewLines: Int,
    val heroTextMaxWidth: Dp,
    val landscapeCardWidth: Dp,
    val portraitCardWidth: Dp,
    val cardSpacing: Dp,
    val rowSpacing: Dp,
    val sectionTitleSize: TextUnit,
    /** Ancho mínimo de celda en la grilla de biblioteca. */
    val gridCellMin: Dp,
    val detailPosterWidth: Dp,
    val detailTitleSize: TextUnit,
) {
    companion object {
        fun forDevice(device: Device): Metrics = when (device.formFactor) {
            FormFactor.Tv -> Metrics(
                gutter = 40.dp,
                headerHeight = 72.dp,
                heroFraction = 0.62f,
                heroTitleSize = 40.sp,
                heroOverviewLines = 2,
                heroTextMaxWidth = 640.dp,
                landscapeCardWidth = 210.dp,
                portraitCardWidth = 116.dp,
                cardSpacing = 16.dp,
                rowSpacing = 22.dp,
                sectionTitleSize = 20.sp,
                gridCellMin = 130.dp,
                detailPosterWidth = 150.dp,
                detailTitleSize = 34.sp,
            )
            FormFactor.Tablet -> Metrics(
                gutter = 28.dp,
                headerHeight = 64.dp,
                heroFraction = 0.52f,
                heroTitleSize = 34.sp,
                heroOverviewLines = 3,
                heroTextMaxWidth = 560.dp,
                landscapeCardWidth = 220.dp,
                portraitCardWidth = 130.dp,
                cardSpacing = 14.dp,
                rowSpacing = 20.dp,
                sectionTitleSize = 18.sp,
                gridCellMin = 130.dp,
                detailPosterWidth = 180.dp,
                detailTitleSize = 40.sp,
            )
            FormFactor.Phone -> Metrics(
                gutter = 16.dp,
                headerHeight = 56.dp,
                // 56% del alto, como el hero mobile de la web (56vh).
                heroFraction = if (device.isPortrait) 0.56f else 0.72f,
                heroTitleSize = 26.sp,
                heroOverviewLines = 3,
                heroTextMaxWidth = Dp.Infinity,
                // Mismos anchos que la web mobile: apaisada 240 (16:9),
                // poster 155 (2:3). Antes 180/108 se veían más chicas.
                landscapeCardWidth = 240.dp,
                portraitCardWidth = 155.dp,
                cardSpacing = 12.dp,
                rowSpacing = 18.dp,
                sectionTitleSize = 16.sp,
                gridCellMin = 104.dp,
                detailPosterWidth = 104.dp,
                detailTitleSize = 22.sp,
            )
        }
    }
}
