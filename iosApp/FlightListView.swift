import SwiftUI
import Shared

struct FlightListView: View {
    let store: ExplorerStore

    var body: some View {
        let state = store.state
        if state.results.isEmpty {
            NoMatchesView(store: store)
        } else {
            ScrollViewReader { proxy in
                List(state.results, id: \.id) { flight in
                    let isSelected = state.selectedFlight?.id == flight.id
                    FlightRow(flight: flight, isSaved: state.savedIds.contains(flight.id), isSelected: isSelected)
                        .onTapGesture { store.toggleSelection(flight) }
                        .listRowBackground(isSelected ? Theme.accent.opacity(0.12) : Color(.secondarySystemGroupedBackground))
                        .id(flight.id)
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .scrollDismissesKeyboard(.immediately)
                .onAppear {
                    // Coming back from the map: keep the selected flight in view.
                    if let id = state.selectedFlight?.id { proxy.scrollTo(id, anchor: .center) }
                }
            }
        }
    }
}

struct NoMatchesView: View {
    let store: ExplorerStore

    var body: some View {
        ContentUnavailableView {
            Label("No matching flights", systemImage: "magnifyingglass")
        } description: {
            Text("Try a different airport or loosen your filters.")
        } actions: {
            Button("Reset filters") { store.resetFilters() }
                .buttonStyle(.borderedProminent)
        }
    }
}
