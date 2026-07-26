import SwiftUI
import Shared

@MainActor
final class ConnectStore: ObservableObject {
    private let viewModel: ConnectViewModel
    @Published private(set) var state: ConnectViewModel.UiState
    private var watcher: Watcher?

    init(viewModel: ConnectViewModel = IosKoin.shared.connectViewModel()) {
        self.viewModel = viewModel
        self.state = viewModel.state.value as! ConnectViewModel.UiState
        self.watcher = viewModel.watchState { [weak self] newState in
            self?.state = newState
        }
    }

    deinit {
        watcher?.cancel()
        viewModel.onCleared()
    }

    func onServerUrlChange(_ v: String) { viewModel.onServerUrlChange(value: v) }
    func onUsernameChange(_ v: String) { viewModel.onUsernameChange(value: v) }
    func onPasswordChange(_ v: String) { viewModel.onPasswordChange(value: v) }
    func connectToServer() { viewModel.connectToServer() }
    func login() { viewModel.login() }
    func back() { viewModel.back() }
}

struct ConnectView: View {
    @StateObject private var store = ConnectStore()
    @FocusState private var focusedField: Field?

    private enum Field { case server, username, password }

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                Spacer(minLength: 60)

                VStack(spacing: 4) {
                    Text("BaskeTV")
                        .font(.largeTitle.bold())
                        .foregroundStyle(.white)
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.6))
                }

                switch store.state.step {
                case .enterserver: serverStep
                case .entercredentials: credentialsStep
                default: EmptyView()
                }

                if let error = store.state.error {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                }

                Spacer(minLength: 40)
            }
            .padding(.horizontal, 24)
            .frame(maxWidth: 480)
            .frame(maxWidth: .infinity)
        }
        .background(baskeBackground.ignoresSafeArea())
        .scrollDismissesKeyboard(.interactively)
    }

    private var subtitle: String {
        switch store.state.step {
        case .enterserver: return "Conéctate a tu servidor Emby"
        case .entercredentials: return store.state.serverName.map { "Conectado a \($0)" } ?? "Inicia sesión"
        default: return ""
        }
    }

    private var serverStep: some View {
        VStack(spacing: 16) {
            TextField("emby.miservidor.com", text: Binding(
                get: { store.state.serverUrl },
                set: { store.onServerUrlChange($0) }
            ))
            .textFieldStyle()
            .keyboardType(.URL)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .submitLabel(.go)
            .focused($focusedField, equals: .server)
            .disabled(store.state.busy)
            .onSubmit { store.connectToServer() }

            BaskeButton(title: "Conectar", busy: store.state.busy, enabled: !store.state.serverUrl.isEmpty) {
                store.connectToServer()
            }
        }
        .onAppear { focusedField = .server }
    }

    private var credentialsStep: some View {
        VStack(spacing: 16) {
            TextField("Usuario", text: Binding(
                get: { store.state.username },
                set: { store.onUsernameChange($0) }
            ))
            .textFieldStyle()
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .submitLabel(.next)
            .focused($focusedField, equals: .username)
            .disabled(store.state.busy)

            SecureField("Contraseña", text: Binding(
                get: { store.state.password },
                set: { store.onPasswordChange($0) }
            ))
            .textFieldStyle()
            .submitLabel(.go)
            .focused($focusedField, equals: .password)
            .disabled(store.state.busy)
            .onSubmit { store.login() }

            HStack(spacing: 12) {
                Button("Atrás") { store.back() }
                    .buttonStyle(.bordered)
                    .disabled(store.state.busy)

                BaskeButton(title: "Entrar", busy: store.state.busy, enabled: !store.state.username.isEmpty) {
                    store.login()
                }
            }
        }
        .onAppear { focusedField = .username }
    }
}

private extension View {
    func textFieldStyle() -> some View {
        self
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(Color.white.opacity(0.08))
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .foregroundStyle(.white)
    }
}

private struct BaskeButton: View {
    let title: String
    let busy: Bool
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            if busy {
                ProgressView().tint(.black)
            } else {
                Text(title).fontWeight(.semibold)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .background(Color.white)
        .foregroundStyle(.black)
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .disabled(busy || !enabled)
        .opacity(enabled ? 1 : 0.5)
    }
}
