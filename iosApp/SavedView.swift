import SwiftUI
import Shared

private struct DetailItem: Identifiable {
    let flight: Flight
    var id: String { flight.id }
}

/// Saved flights, independent of the discovery filters and available offline.
struct SavedView: View {
    let store: ExplorerStore
    @State private var detail: DetailItem?

    var body: some View {
        let state = store.state
        NavigationStack {
            Group {
                if state.saved.isEmpty {
                    ContentUnavailableView {
                        Label("No saved flights", systemImage: "bookmark")
                    } description: {
                        Text(state.savedError ?? "Save flights from Explore and they'll be here — even offline.")
                    }
                } else {
                    List {
                        Section {
                            ForEach(state.saved, id: \.flight.id) { item in
                                FlightRow(
                                    flight: item.flight,
                                    footnote: item.isInLatestCatalog ? nil : "Not in the latest results"
                                )
                                .onTapGesture { detail = DetailItem(flight: item.flight) }
                                .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                    Button { store.remove(item.flight) } label: {
                                        Label("Remove", systemImage: "bookmark.slash")
                                    }
                                    .tint(.red)
                                }
                            }
                        } footer: {
                            Text("Saved flights are stored on this device.")
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle("Saved")
            .sheet(item: $detail) { item in
                SavedDetail(store: store, flight: item.flight)
                    .presentationDetents([.height(330)])
                    .presentationDragIndicator(.visible)
            }
        }
    }
}

private struct SavedDetail: View {
    let store: ExplorerStore
    let flight: Flight
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        FlightCard(
            flight: flight,
            isSaved: store.isSaved(flight),
            isPending: store.isPending(flight),
            onToggleSave: { store.toggleSaved(flight) }
        )
        .padding(16)
        .onChange(of: store.isSaved(flight)) { _, saved in
            if !saved { dismiss() }
        }
    }
}
