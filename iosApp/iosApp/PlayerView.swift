import SwiftUI
import Shared
import VLCKit

/// Envuelve VLCMediaPlayer y el PlayerViewModel de Kotlin. Vive en un
/// ObservableObject (no en el UIViewRepresentable) para que los controles de
/// SwiftUI puedan manejar play/pause/seek sin pasar por la vista de video.
@MainActor
final class PlayerStore: NSObject, ObservableObject {
    private let viewModel: PlayerViewModel
    private var watcher: Watcher?
    let mediaPlayer = VLCMediaPlayer(options: ["--no-drop-late-frames", "--no-skip-frames"])

    @Published private(set) var uiState: PlayerViewModel.UiState
    @Published private(set) var isPlaying = true
    @Published private(set) var positionMs: Int64 = 0
    @Published private(set) var durationMs: Int64 = 0
    @Published private(set) var currentSubtitleUrl: String?
    @Published var playbackError: String?

    var onEnded: (() -> Void)?

    private var startReported = false
    private var resumeSeeked = false
    private var subtitleAdded = false
    private var addedSubtitles: [String: VLCMediaPlayer.Track] = [:]
    private var pendingSubtitleUrl: String?
    private var knownTextTrackIds: Set<String> = []
    private var isTearingDown = false
    private var positionTimer: Timer?
    private var progressReportTimer: Timer?

    init(itemId: String) {
        self.viewModel = IosKoin.shared.playerViewModel(itemId: itemId)
        self.uiState = viewModel.state.value as! PlayerViewModel.UiState
        super.init()
        mediaPlayer.delegate = self
        watcher = viewModel.watchState { [weak self] state in
            self?.apply(state)
        }
        apply(uiState)

        positionTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            self?.tick()
        }
        progressReportTimer = Timer.scheduledTimer(withTimeInterval: 10, repeats: true) { [weak self] _ in
            guard let self, self.mediaPlayer.isPlaying else { return }
            self.viewModel.reportProgress(positionMs: self.currentTimeMs(), paused: false)
        }
    }

    private func apply(_ state: PlayerViewModel.UiState) {
        uiState = state
        guard mediaPlayer.media == nil, let urlString = state.streamUrl, let url = URL(string: urlString) else { return }
        mediaPlayer.media = VLCMedia(url: url)
        mediaPlayer.play()
    }

    private func tick() {
        positionMs = currentTimeMs()
        durationMs = Int64(mediaPlayer.media?.length.intValue ?? 0)

        if let pending = pendingSubtitleUrl {
            if let newTrack = mediaPlayer.textTracks.first(where: { !knownTextTrackIds.contains($0.trackId) }) {
                addedSubtitles[pending] = newTrack
                newTrack.isSelectedExclusively = true
                pendingSubtitleUrl = nil
                knownTextTrackIds = Set(mediaPlayer.textTracks.map(\.trackId))
            }
        }
    }

    private func currentTimeMs() -> Int64 { Int64(mediaPlayer.time.intValue) }

    func togglePlayPause() {
        if mediaPlayer.isPlaying { mediaPlayer.pause() } else { mediaPlayer.play() }
    }

    func seek(by deltaMs: Int64) {
        let duration = durationMs
        let target = (currentTimeMs() + deltaMs).clamped(to: 0...(duration > 0 ? duration : Int64.max))
        mediaPlayer.time = VLCTime(int: Int32(truncatingIfNeeded: target))
        positionMs = target
    }

    func seek(toFraction fraction: Double) {
        guard durationMs > 0 else { return }
        let target = Int64(fraction * Double(durationMs))
        mediaPlayer.time = VLCTime(int: Int32(truncatingIfNeeded: target))
        positionMs = target
    }

    // MARK: Audio / subtítulos

    var audioTracks: [VLCMediaPlayer.Track] { mediaPlayer.audioTracks }

    func selectAudioTrack(_ track: VLCMediaPlayer.Track) { track.isSelectedExclusively = true }

    func selectSubtitle(_ subtitle: PlayerViewModel.SubtitleTrack) {
        currentSubtitleUrl = subtitle.url
        if let existing = addedSubtitles[subtitle.url] {
            existing.isSelectedExclusively = true
        } else if let url = URL(string: subtitle.url) {
            knownTextTrackIds = Set(mediaPlayer.textTracks.map(\.trackId))
            pendingSubtitleUrl = subtitle.url
            mediaPlayer.addPlaybackSlave(url, type: .subtitle, enforce: true)
        }
    }

    func disableSubtitle() {
        currentSubtitleUrl = nil
        mediaPlayer.deselectAllTextTracks()
    }

    func tearDown() {
        guard !isTearingDown else { return }
        isTearingDown = true
        positionTimer?.invalidate()
        progressReportTimer?.invalidate()
        watcher?.cancel()
        viewModel.reportStopped(positionMs: currentTimeMs())
        mediaPlayer.stop()
        viewModel.onCleared()
    }
}

extension PlayerStore: VLCMediaPlayerDelegate {
    nonisolated func mediaPlayerStateChanged(_ newState: VLCMediaPlayerState) {
        Task { @MainActor in
            switch newState {
            case .playing:
                isPlaying = true
                if !subtitleAdded, let subUrl = uiState.subtitleUrl {
                    subtitleAdded = true
                    knownTextTrackIds = Set(mediaPlayer.textTracks.map(\.trackId))
                    pendingSubtitleUrl = subUrl
                    if let url = URL(string: subUrl) {
                        mediaPlayer.addPlaybackSlave(url, type: .subtitle, enforce: true)
                    }
                }
                if !resumeSeeked {
                    resumeSeeked = true
                    if uiState.startPositionMs > 3_000 {
                        mediaPlayer.time = VLCTime(int: Int32(truncatingIfNeeded: uiState.startPositionMs))
                    }
                }
                if !startReported {
                    startReported = true
                    viewModel.reportStart(positionMs: currentTimeMs())
                }
            case .paused:
                isPlaying = false
                viewModel.reportProgress(positionMs: currentTimeMs(), paused: true)
            case .stopped, .stopping:
                isPlaying = false
                if !isTearingDown { onEnded?() }
            case .error:
                playbackError = "Error al reproducir el video"
            default:
                break
            }
        }
    }
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self { min(max(self, range.lowerBound), range.upperBound) }
}

// MARK: - Vista

struct PlayerView: View {
    @StateObject private var store: PlayerStore
    let onExit: () -> Void

    @State private var controlsVisible = true
    @State private var hideTask: Task<Void, Never>?
    @State private var scrubbing = false
    @State private var scrubFraction: Double = 0
    @State private var activePanel: Panel?

    private enum Panel { case audio, subtitles }

    init(itemId: String, onExit: @escaping () -> Void) {
        _store = StateObject(wrappedValue: PlayerStore(itemId: itemId))
        self.onExit = onExit
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VLCVideoSurface(player: store.mediaPlayer)
                .ignoresSafeArea()

            // Capa transparente puramente SwiftUI para capturar el tap. Ponerlo
            // en la UIView de VLCKit no basta: apenas arranca la reproducción,
            // VLCKit le agrega subvistas de render propias que se quedan con el
            // touch antes de que llegue al gesture recognizer de esa vista.
            Color.clear
                .contentShape(Rectangle())
                .ignoresSafeArea()
                .onTapGesture { toggleControls() }

            if store.uiState.loading {
                ProgressView().tint(.white)
            }

            if let error = store.playbackError ?? store.uiState.error {
                Text(error).foregroundStyle(.white).padding()
            }

            if controlsVisible {
                VStack {
                    topBar
                    Spacer()
                    bottomControls
                }
                .transition(.opacity)
            }

            if let panel = activePanel {
                HStack {
                    Spacer()
                    panelView(for: panel)
                        .padding(.trailing, 24)
                        .padding(.bottom, 140)
                }
                .frame(maxHeight: .infinity, alignment: .bottom)
            }
        }
        .statusBarHidden()
        .onAppear {
            store.onEnded = onExit
            scheduleAutoHide()
        }
        .onDisappear { store.tearDown() }
    }

    private func toggleControls() {
        withAnimation { controlsVisible.toggle() }
        if controlsVisible { scheduleAutoHide() }
    }

    private func scheduleAutoHide() {
        hideTask?.cancel()
        hideTask = Task {
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            guard !Task.isCancelled, activePanel == nil else { return }
            withAnimation { controlsVisible = false }
        }
    }

    private func bumpInteraction() { scheduleAutoHide() }

    private var topBar: some View {
        HStack {
            Button(action: onExit) {
                Label("Volver", systemImage: "chevron.left")
                    .font(.subheadline.bold())
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(Color.black.opacity(0.6))
                    .foregroundStyle(.white)
                    .clipShape(Capsule())
            }
            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .background(LinearGradient(colors: [.black.opacity(0.7), .clear], startPoint: .top, endPoint: .bottom))
    }

    private var bottomControls: some View {
        VStack(spacing: 14) {
            Text(store.uiState.title)
                .font(.headline)
                .foregroundStyle(.white)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)

            HStack(spacing: 10) {
                Text(formatTime(store.positionMs)).font(.caption).foregroundStyle(.white)
                Slider(
                    value: Binding(
                        get: { scrubbing ? scrubFraction : fraction(store.positionMs, store.durationMs) },
                        set: { scrubFraction = $0 }
                    ),
                    in: 0...1,
                    onEditingChanged: { editing in
                        scrubbing = editing
                        bumpInteraction()
                        if !editing { store.seek(toFraction: scrubFraction) }
                    }
                )
                .tint(.cyan)
                Text(formatTime(store.durationMs)).font(.caption).foregroundStyle(.white.opacity(0.7))
            }

            HStack(spacing: 28) {
                Button { store.seek(by: -10_000); bumpInteraction() } label: {
                    Image(systemName: "gobackward.10").font(.title2)
                }
                Button { store.togglePlayPause(); bumpInteraction() } label: {
                    Image(systemName: store.isPlaying ? "pause.fill" : "play.fill").font(.system(size: 42))
                }
                Button { store.seek(by: 10_000); bumpInteraction() } label: {
                    Image(systemName: "goforward.10").font(.title2)
                }
                Spacer()
                Button { togglePanel(.audio) } label: {
                    Image(systemName: "waveform").font(.title3)
                }
                if !store.uiState.subtitles.isEmpty {
                    Button { togglePanel(.subtitles) } label: {
                        Image(systemName: store.currentSubtitleUrl != nil ? "captions.bubble.fill" : "captions.bubble")
                            .font(.title3)
                    }
                }
            }
            .foregroundStyle(.white)
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 24)
        .padding(.top, 40)
        .background(LinearGradient(colors: [.clear, .black.opacity(0.85)], startPoint: .top, endPoint: .bottom))
    }

    private func togglePanel(_ panel: Panel) {
        activePanel = activePanel == panel ? nil : panel
        bumpInteraction()
    }

    @ViewBuilder
    private func panelView(for panel: Panel) -> some View {
        switch panel {
        case .audio:
            PlayerPanelList(title: "Audio", items: store.audioTracks.map { track in
                PlayerPanelItem(label: track.trackName, active: track.isSelected) {
                    store.selectAudioTrack(track)
                    activePanel = nil
                }
            })
        case .subtitles:
            let items: [PlayerPanelItem] = [
                PlayerPanelItem(label: "Desactivado", active: store.currentSubtitleUrl == nil) {
                    store.disableSubtitle(); activePanel = nil
                }
            ] + store.uiState.subtitles.map { sub in
                PlayerPanelItem(label: sub.label, active: store.currentSubtitleUrl == sub.url) {
                    store.selectSubtitle(sub); activePanel = nil
                }
            }
            PlayerPanelList(title: "Subtítulos", items: items)
        }
    }

    private func fraction(_ position: Int64, _ duration: Int64) -> Double {
        guard duration > 0 else { return 0 }
        return min(max(Double(position) / Double(duration), 0), 1)
    }

    private func formatTime(_ ms: Int64) -> String {
        guard ms > 0 else { return "0:00" }
        let totalSec = ms / 1000
        let h = totalSec / 3600
        let m = (totalSec % 3600) / 60
        let s = totalSec % 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%d:%02d", m, s)
    }
}

private struct PlayerPanelItem {
    let label: String
    let active: Bool
    let onSelect: () -> Void
}

private struct PlayerPanelList: View {
    let title: String
    let items: [PlayerPanelItem]

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title.uppercased())
                .font(.caption2.bold())
                .foregroundStyle(.white.opacity(0.4))
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
            ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                Button(action: item.onSelect) {
                    HStack {
                        Text(item.label)
                            .font(.subheadline.weight(item.active ? .semibold : .regular))
                            .foregroundStyle(item.active ? .cyan : .white)
                        Spacer()
                        if item.active { Image(systemName: "checkmark").foregroundStyle(.cyan) }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 9)
                }
            }
        }
        .frame(width: 260)
        .background(Color(white: 0.1).opacity(0.97))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(.white.opacity(0.08)))
    }
}

private struct VLCVideoSurface: UIViewRepresentable {
    let player: VLCMediaPlayer

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .black
        player.drawable = view
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {}
}
