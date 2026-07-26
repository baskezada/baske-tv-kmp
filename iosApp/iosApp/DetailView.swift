import SwiftUI
import Shared

@MainActor
final class DetailStore: ObservableObject {
    private let viewModel: DetailViewModel
    @Published private(set) var state: DetailViewModel.UiState
    private var watcher: Watcher?

    init(itemId: String) {
        let vm = IosKoin.shared.detailViewModel(itemId: itemId)
        self.viewModel = vm
        self.state = vm.state.value as! DetailViewModel.UiState
        self.watcher = vm.watchState { [weak self] newState in
            self?.state = newState
        }
    }

    deinit {
        watcher?.cancel()
        viewModel.onCleared()
    }

    func selectSeason(_ seasonId: String) { viewModel.selectSeason(seasonId: seasonId) }
}

struct DetailView: View {
    @StateObject private var store: DetailStore
    @Environment(\.dismiss) private var dismiss
    let onPlay: (String) -> Void

    init(itemId: String, onPlay: @escaping (String) -> Void) {
        _store = StateObject(wrappedValue: DetailStore(itemId: itemId))
        self.onPlay = onPlay
    }

    var body: some View {
        ZStack {
            baskeBackground.ignoresSafeArea()

            if store.state.loading {
                ProgressView().tint(.white)
            } else if let error = store.state.error {
                Text(error).foregroundStyle(.white)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        backdrop
                        playSection
                        if store.state.kind === DetailViewModel.Kind.series {
                            seasonChips
                            episodeList
                        }
                    }
                }
                .ignoresSafeArea(edges: .top)
            }
        }
        // No ocultamos la nav bar entera (toolbar(.hidden,...)) porque eso
        // también apaga el swipe-to-back de UIKit. La dejamos "visible" pero
        // transparente, y el back propio va en un toolbar item real — así
        // queda bien posicionado (no centrado) y el gesto de deslizar sigue vivo.
        .navigationBarBackButtonHidden(true)
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                FloatingIconButton(systemImage: "chevron.left") { dismiss() }
            }
        }
    }

    /// Solo la imagen + poster/título, sangrando hasta el borde superior.
    ///
    /// La imagen usa GeometryReader para fijar su ancho en un número concreto:
    /// una `Image.resizable()` con `aspectRatio(.fill)` y solo `height` en el
    /// frame no queda acotada por el ancho de pantalla (nada le impide pedir
    /// más ancho vía su aspect ratio), así que arrastraba a todo lo demás en
    /// el VStack fuera de los bordes. `geo.size.width` es un valor concreto,
    /// no una propuesta de layout que la imagen pueda ignorar.
    private var backdrop: some View {
        GeometryReader { geo in
            ZStack(alignment: .bottomLeading) {
                AsyncImage(url: URL(string: store.state.backdropUrl ?? "")) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Color.white.opacity(0.05)
                }
                .frame(width: geo.size.width, height: 300)
                .clipped()

                LinearGradient(colors: [baskeBackground.opacity(0), baskeBackground], startPoint: .top, endPoint: .bottom)
                    .frame(width: geo.size.width, height: 300)

                HStack(alignment: .bottom, spacing: 16) {
                    AsyncImage(url: URL(string: store.state.posterUrl ?? "")) { image in
                        image.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Color.white.opacity(0.08)
                    }
                    .frame(width: 100, height: 150)
                    .clipShape(RoundedRectangle(cornerRadius: 8))

                    VStack(alignment: .leading, spacing: 4) {
                        Text(store.state.title)
                            .font(.title2.bold())
                            .foregroundStyle(.white)
                            .lineLimit(2)
                        if !store.state.meta.isEmpty {
                            Text(store.state.meta)
                                .font(.caption)
                                .foregroundStyle(.white.opacity(0.7))
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(16)
                .frame(width: geo.size.width, alignment: .bottomLeading)
            }
        }
        .frame(height: 300)
    }

    /// Genres/overview/play, en flujo normal debajo del backdrop (no metido a
    /// presión con safeAreaInset, que generaba espaciados raros al hacer scroll).
    private var playSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let genres = store.state.genres {
                Text(genres).font(.footnote.bold()).foregroundStyle(.cyan)
            }
            if let overview = store.state.overview {
                Text(overview).font(.footnote).foregroundStyle(.white.opacity(0.7)).lineLimit(4)
            }
            if let playTargetId = store.state.playTargetId {
                Button {
                    onPlay(playTargetId)
                } label: {
                    Label(store.state.playLabel, systemImage: "play.fill")
                        .fontWeight(.bold)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 10)
                        .background(Color.white)
                        .foregroundStyle(.black)
                        .clipShape(Capsule())
                }
            }
        }
        .padding(16)
    }

    private var seasonChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(store.state.seasons, id: \.id) { season in
                    let selected = season.id == store.state.selectedSeasonId
                    Button(season.name) { store.selectSeason(season.id) }
                        .font(.footnote.bold())
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(selected ? Color.cyan : Color.white.opacity(0.1))
                        .foregroundStyle(selected ? .black : .white)
                        .clipShape(Capsule())
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
    }

    private var episodeList: some View {
        LazyVStack(spacing: 4) {
            if store.state.episodesLoading {
                ProgressView().tint(.white).padding(32)
            } else {
                ForEach(store.state.episodes, id: \.id) { ep in
                    EpisodeRow(ep: ep).onTapGesture { onPlay(ep.id) }
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 32)
    }
}

private struct EpisodeRow: View {
    let ep: DetailViewModel.EpisodeItem

    var body: some View {
        HStack(spacing: 12) {
            ZStack(alignment: .bottomLeading) {
                AsyncImage(url: URL(string: ep.imageUrl ?? "")) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Color.white.opacity(0.08)
                }
                .frame(width: 120, height: 68)
                .clipShape(RoundedRectangle(cornerRadius: 6))

                if ep.progress > 0 {
                    Rectangle().fill(Color.cyan).frame(width: 120 * CGFloat(ep.progress), height: 3)
                }
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(ep.title).font(.footnote.bold()).foregroundStyle(.white).lineLimit(1)
                if !ep.meta.isEmpty {
                    Text(ep.meta).font(.caption2).foregroundStyle(.white.opacity(0.5))
                }
                if let overview = ep.overview {
                    Text(overview).font(.caption2).foregroundStyle(.white.opacity(0.4)).lineLimit(2)
                }
            }
            Spacer()
        }
        .padding(8)
        .background(Color.white.opacity(0.05))
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .contentShape(Rectangle())
    }
}
