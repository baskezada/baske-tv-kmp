import SwiftUI
import Shared

struct SettingsView: View {
    @StateObject private var prefs = PrefsObservable()
    @Environment(\.dismiss) private var dismiss
    let onLogout: () -> Void

    var body: some View {
        NavigationStack {
            List {
                Section("Color de acento") {
                    HStack(spacing: 14) {
                        ForEach(accentPresets, id: \.self) { argb in
                            let active = argb == prefs.prefs.accentColor
                            Button {
                                prefs.setAccentColor(argb)
                            } label: {
                                Circle()
                                    .fill(Color(argb: argb))
                                    .frame(width: 40, height: 40)
                                    .overlay {
                                        if active {
                                            Image(systemName: "checkmark")
                                                .foregroundStyle(.black)
                                        }
                                    }
                                    .overlay {
                                        Circle().stroke(.white.opacity(active ? 0.9 : 0), lineWidth: 2)
                                    }
                            }
                        }
                    }
                    .padding(.vertical, 4)
                }
                .listRowBackground(Color.white.opacity(0.05))

                Section("Inicio") {
                    ForEach(homeModes, id: \.label) { entry in
                        // "Vitrina" es un modo de TV (el hero sigue el foco del
                        // D-pad) — no existe ese concepto con touch, así que en
                        // iOS se trata como equivalente a "Con banner".
                        let active = prefs.prefs.homeMode === entry.mode
                            || (entry.mode === HomeMode.banner && prefs.prefs.homeMode === HomeMode.vitrina)
                        Button {
                            prefs.setHomeMode(entry.mode)
                        } label: {
                            HStack {
                                Text(entry.label).foregroundStyle(.white)
                                Spacer()
                                if active {
                                    Image(systemName: "checkmark").foregroundStyle(prefs.accentColor)
                                }
                            }
                        }
                    }
                }
                .listRowBackground(Color.white.opacity(0.05))

                Section {
                    Button("Cerrar sesión", role: .destructive) {
                        onLogout()
                        dismiss()
                    }
                }
                .listRowBackground(Color.white.opacity(0.05))
            }
            .scrollContentBackground(.hidden)
            .background(baskeBackground)
            .navigationTitle("Ajustes")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cerrar") { dismiss() }
                }
            }
        }
    }

    private var homeModes: [(mode: HomeMode, label: String)] {
        [
            (HomeMode.banner, "Con banner"),
            (HomeMode.sinbanner, "Sin banner"),
        ]
    }
}
