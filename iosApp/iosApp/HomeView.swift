import SwiftUI
import Shared

@MainActor
final class HomeStore: ObservableObject {
    private let viewModel: HomeViewModel
    @Published private(set) var state: HomeUiState
    private var watcher: Watcher?

    init(viewModel: HomeViewModel = IosKoin.shared.homeViewModel()) {
        self.viewModel = viewModel
        self.state = viewModel.state.value as! HomeUiState
        self.watcher = viewModel.watchState { [weak self] newState in
            self?.state = newState
        }
    }

    deinit {
        watcher?.cancel()
        viewModel.onCleared()
    }

    func refresh() { viewModel.refresh() }
}

/// Observa Prefs (accent + modo de home) — se reusa en Home y Settings.
@MainActor
final class PrefsObservable: ObservableObject {
    private let store: PrefsStore
    @Published private(set) var prefs: Prefs
    private var watcher: Watcher?

    init(store: PrefsStore = IosKoin.shared.prefsStore) {
        self.store = store
        self.prefs = store.prefs.value as! Prefs
        self.watcher = store.watchPrefs { [weak self] newPrefs in
            self?.prefs = newPrefs
        }
    }

    deinit { watcher?.cancel() }

    var accentColor: Color { Color(argb: prefs.accentColor) }

    func setAccentColor(_ argb: Int64) { store.setAccentColor(color: argb) }
    func setHomeMode(_ mode: HomeMode) { store.setHomeMode(mode: mode) }
}

struct HomeView: View {
    @StateObject private var store = HomeStore()
    @StateObject private var prefs = PrefsObservable()

    let onOpenSettings: () -> Void
    let onOpenCard: (HomeCard) -> Void
    let onPlayItem: (HomeCard) -> Void

    var body: some View {
        Group {
            if let hero {
                // Hay hero: la imagen sangra bajo el status bar y el header
                // flota encima como overlay (no reserva espacio propio).
                ZStack(alignment: .top) {
                    baskeBackground.ignoresSafeArea()
                    scrollContent(hero: hero).ignoresSafeArea(edges: .top)
                    header
                }
            } else {
                // Sin hero (o todavía cargando): el header es una barra normal
                // que sí reserva su espacio, para no tapar la primera fila.
                VStack(spacing: 0) {
                    header
                    scrollContent(hero: nil)
                }
                .background(baskeBackground.ignoresSafeArea())
            }
        }
        .toolbar(.hidden, for: .navigationBar)
    }

    private var hero: HomeCard? {
        guard prefs.prefs.homeMode !== HomeMode.sinbanner else { return nil }
        return store.state.rows.first?.cards.first
    }

    private var header: some View {
        HStack {
            Text("BaskeTV")
                .font(.system(size: 17, weight: .heavy))
                .foregroundStyle(prefs.accentColor)
            Spacer()
            FloatingIconButton(systemImage: "gearshape.fill", action: onOpenSettings)
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 10)
        .background(
            LinearGradient(colors: [.black.opacity(0.55), .clear], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea(edges: .top)
        )
    }

    private func scrollContent(hero: HomeCard?) -> some View {
        ScrollView {
            if store.state.loading && store.state.rows.isEmpty {
                ProgressView().tint(.white).padding(.top, 80)
            } else if let error = store.state.error, store.state.rows.isEmpty {
                Text(error).foregroundStyle(.white).padding(.top, 80)
            } else {
                LazyVStack(alignment: .leading, spacing: 24) {
                    if let hero {
                        HeroView(card: hero, accent: prefs.accentColor) {
                            onPlayItem(hero)
                        } onInfo: {
                            onOpenCard(hero)
                        }
                    }
                    ForEach(store.state.rows, id: \.id) { row in
                        RowSection(row: row, accent: prefs.accentColor, onCardTap: onOpenCard)
                    }
                }
                .padding(.bottom, 32)
            }
        }
        .refreshable { store.refresh() }
    }
}

private struct HeroView: View {
    let card: HomeCard
    let accent: Color
    let onPlay: () -> Void
    let onInfo: () -> Void

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .bottomLeading) {
                AsyncImage(url: URL(string: card.backdropUrl ?? card.imageUrl ?? "")) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Color.white.opacity(0.05)
                }
                .frame(width: geo.size.width, height: 340)
                .clipped()

                LinearGradient(
                    colors: [baskeBackground.opacity(0), baskeBackground],
                    startPoint: .top, endPoint: .bottom
                )
                .frame(width: geo.size.width, height: 340)

                heroText
                    .frame(width: geo.size.width, alignment: .bottomLeading)
            }
        }
        .frame(height: 340)
    }

    private var heroText: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let kicker = card.kicker {
                Text("\(kicker) · RECIÉN AÑADIDO")
                    .font(.caption.bold())
                    .foregroundStyle(accent)
            }
            Text(card.title)
                .font(.title.bold())
                .foregroundStyle(.white)
                .lineLimit(2)
            if let rating = card.rating?.doubleValue {
                HStack(spacing: 4) {
                    Image(systemName: "star.fill").foregroundStyle(.yellow)
                    Text(String(format: "%.1f", rating)).foregroundStyle(.white)
                }
                .font(.subheadline.bold())
            }
            if let overview = card.overview {
                Text(overview)
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.8))
                    .lineLimit(2)
            }
            HStack(spacing: 12) {
                Button(action: onPlay) {
                    Label("Reproducir", systemImage: "play.fill")
                        .fontWeight(.bold)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 10)
                        .background(Color.white)
                        .foregroundStyle(.black)
                        .clipShape(Capsule())
                }
                Button(action: onInfo) {
                    Image(systemName: "info.circle")
                        .font(.title2)
                        .foregroundStyle(.white)
                        .padding(10)
                        .background(Color.white.opacity(0.15))
                        .clipShape(Circle())
                }
            }
            .padding(.top, 4)
        }
        .padding(16)
    }
}

private struct RowSection: View {
    let row: HomeRow
    let accent: Color
    let onCardTap: (HomeCard) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(row.title)
                .font(.headline)
                .foregroundStyle(.white)
                .padding(.horizontal, 16)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(row.cards, id: \.id) { card in
                        MediaCardView(card: card, portrait: row.portrait, accent: accent)
                            .onTapGesture { onCardTap(card) }
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }
}

private struct MediaCardView: View {
    let card: HomeCard
    let portrait: Bool
    let accent: Color

    private var width: CGFloat { portrait ? 108 : 180 }
    private var ratio: CGFloat { portrait ? 2.0 / 3.0 : 16.0 / 9.0 }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ZStack(alignment: .bottomLeading) {
                AsyncImage(url: URL(string: card.imageUrl ?? "")) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Color.white.opacity(0.08)
                }
                .frame(width: width, height: width / ratio)
                .clipShape(RoundedRectangle(cornerRadius: 8))

                if card.progress > 0 {
                    Rectangle()
                        .fill(accent)
                        .frame(width: width * CGFloat(card.progress), height: 3)
                }
            }
            Text(card.title)
                .font(.caption)
                .foregroundStyle(.white)
                .lineLimit(1)
            if let subtitle = card.subtitle {
                Text(subtitle)
                    .font(.caption2)
                    .foregroundStyle(.white.opacity(0.6))
                    .lineLimit(1)
            }
        }
        .frame(width: width, alignment: .leading)
    }
}
