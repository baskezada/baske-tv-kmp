import SwiftUI
import Shared

@MainActor
final class LibraryStore: ObservableObject {
    private let viewModel: LibraryViewModel
    @Published private(set) var state: LibraryViewModel.UiState
    private var watcher: Watcher?

    init(libraryId: String) {
        let vm = IosKoin.shared.libraryViewModel(libraryId: libraryId)
        self.viewModel = vm
        self.state = vm.state.value as! LibraryViewModel.UiState
        self.watcher = vm.watchState { [weak self] newState in
            self?.state = newState
        }
    }

    deinit {
        watcher?.cancel()
        viewModel.onCleared()
    }

    func loadMore() { viewModel.loadMore() }
}

struct LibraryView: View {
    @StateObject private var store: LibraryStore
    let onOpenCard: (HomeCard) -> Void

    private let columns = [GridItem(.adaptive(minimum: 104), spacing: 12)]

    init(libraryId: String, onOpenCard: @escaping (HomeCard) -> Void) {
        _store = StateObject(wrappedValue: LibraryStore(libraryId: libraryId))
        self.onOpenCard = onOpenCard
    }

    var body: some View {
        ZStack {
            baskeBackground.ignoresSafeArea()

            if store.state.loading {
                ProgressView().tint(.white)
            } else if let error = store.state.error, store.state.items.isEmpty {
                Text(error).foregroundStyle(.white)
            } else {
                ScrollView {
                    LazyVGrid(columns: columns, spacing: 18) {
                        ForEach(Array(store.state.items.enumerated()), id: \.element.id) { index, card in
                            LibraryCardView(card: card)
                                .onTapGesture { onOpenCard(card) }
                                .onAppear {
                                    if index >= store.state.items.count - 12 { store.loadMore() }
                                }
                        }
                    }
                    .padding(16)
                    if store.state.loadingMore {
                        ProgressView().tint(.white).padding(.bottom, 24)
                    }
                }
            }
        }
        .navigationTitle(store.state.title)
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct LibraryCardView: View {
    let card: HomeCard

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            AsyncImage(url: URL(string: card.imageUrl ?? "")) { image in
                image.resizable().aspectRatio(contentMode: .fill)
            } placeholder: {
                Color.white.opacity(0.08)
            }
            .aspectRatio(2.0 / 3.0, contentMode: .fill)
            .clipShape(RoundedRectangle(cornerRadius: 8))

            Text(card.title)
                .font(.caption)
                .foregroundStyle(.white.opacity(0.85))
                .lineLimit(1)
        }
    }
}
