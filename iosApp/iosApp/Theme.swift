import SwiftUI

/// Fondo estándar de toda la app (igual al `#080808` del lado Android).
let baskeBackground = Color(red: 0x08 / 255, green: 0x08 / 255, blue: 0x08 / 255)

/// Paleta de acentos ofrecida en Ajustes (ARGB). Debe reflejar exactamente los
/// mismos valores que `androidApp/ui/theme/Accent.kt` — ese archivo es
/// Android-only (no vive en `shared`), así que se repite acá a propósito.
let accentPresets: [Int64] = [
    0xFF06B6D4, // cian
    0xFF7C5CFF, // violeta
    0xFFEF4444, // rojo
    0xFFF59E0B, // ámbar
    0xFF22C55E, // verde
    0xFFEC4899, // rosa
    0xFF3B82F6, // azul
    0xFFFFFFFF, // blanco
]

/// Botón circular translúcido para flotar sobre banners/backdrops (back, ajustes, etc).
/// Se usa siempre en un overlay que SÍ respeta el safe area, mientras el fondo
/// detrás (imagen) lo ignora — así el botón queda bajo el status bar y la
/// imagen sangra hasta el borde.
struct FloatingIconButton: View {
    let systemImage: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 34, height: 34)
                .background(.black.opacity(0.45), in: Circle())
        }
    }
}

extension Color {
    /// Construye un Color desde un ARGB de 64 bits (formato usado por Prefs.accentColor).
    init(argb: Int64) {
        let v = UInt32(truncatingIfNeeded: argb)
        self.init(
            red: Double((v >> 16) & 0xFF) / 255,
            green: Double((v >> 8) & 0xFF) / 255,
            blue: Double(v & 0xFF) / 255
        )
    }
}
