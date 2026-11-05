import SwiftUI
import Shared

@main
struct RoameStarterApp: App {
    @State private var store = ExplorerStore()

    var body: some Scene {
        WindowGroup {
            RootView(store: store)
        }
    }
}

struct RootView: View {
    @Bindable var store: ExplorerStore

    var body: some View {
        TabView {
            ExploreView(store: store)
                .tabItem { Label("Explore", systemImage: "airplane.departure") }
            SavedView(store: store)
                .tabItem { Label("Saved", systemImage: "bookmark") }
                .badge(store.state.saved.count)
        }
        .tint(Theme.accent)
        .alert(
            store.state.message?.text ?? "",
            isPresented: Binding(
                get: { store.state.message != nil },
                set: { if !$0 { store.dismissMessage() } }
            )
        ) {
            Button("OK", role: .cancel) {}
        }
    }
}
