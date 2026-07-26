import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        IosKoin.shared.start()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}
