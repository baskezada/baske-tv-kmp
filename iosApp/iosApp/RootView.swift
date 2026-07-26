import SwiftUI
import Shared

@MainActor
final class SessionObservable: ObservableObject {
    private let authRepository: AuthRepository
    @Published private(set) var session: Session?
    private var watcher: Watcher?

    init(authRepository: AuthRepository = IosKoin.shared.authRepository) {
        self.authRepository = authRepository
        self.session = authRepository.session.value as? Session
        self.watcher = authRepository.watchSession { [weak self] newSession in
            self?.session = newSession
        }
    }

    deinit { watcher?.cancel() }

    func logout() { authRepository.logout() }
}

struct RootView: View {
    @StateObject private var sessionObservable = SessionObservable()

    var body: some View {
        Group {
            if sessionObservable.session != nil {
                AuthenticatedRootView(onLogout: sessionObservable.logout)
            } else {
                ConnectView()
            }
        }
        .preferredColorScheme(.dark)
    }
}

private enum Route: Hashable {
    case detail(id: String)
    case library(id: String)
}

private struct PlayingItem: Identifiable, Hashable {
    let id: String
}

private struct AuthenticatedRootView: View {
    let onLogout: () -> Void

    @State private var path = NavigationPath()
    @State private var showSettings = false
    @State private var playingItem: PlayingItem?

    var body: some View {
        NavigationStack(path: $path) {
            HomeView(
                onOpenSettings: { showSettings = true },
                onOpenCard: openCard,
                onPlayItem: playItem
            )
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .detail(let id):
                    DetailView(itemId: id, onPlay: { epId in playingItem = PlayingItem(id: epId) })
                case .library(let id):
                    LibraryView(libraryId: id, onOpenCard: openCard)
                }
            }
        }
        .sheet(isPresented: $showSettings) {
            SettingsView(onLogout: onLogout)
        }
        .fullScreenCover(item: $playingItem) { item in
            PlayerView(itemId: item.id, onExit: { playingItem = nil })
        }
    }

    /// Dispatch por `navTarget`, igual que `RootApp.openCard` en Android: se
    /// reusa tanto para los cards del Home como para los de LibraryView.
    private func openCard(_ card: HomeCard) {
        if card.navTarget === NavTarget.player {
            playingItem = PlayingItem(id: card.id)
        } else if card.navTarget === NavTarget.library {
            path.append(Route.library(id: card.id))
        } else {
            path.append(Route.detail(id: card.id))
        }
    }

    /// Botón "Reproducir" del hero: usa `playDirect`, no `navTarget` (igual que
    /// el `onPlayItem` inline de `RootApp.kt`).
    private func playItem(_ card: HomeCard) {
        if card.playDirect {
            playingItem = PlayingItem(id: card.id)
        } else {
            path.append(Route.detail(id: card.id))
        }
    }
}
