import Foundation
import Observation
import Shared

/// Thin SwiftUI adapter over the shared `FlightExplorer`. All logic (fetching, caching, filtering,
/// map aggregation, saving) lives in Kotlin; this class only forwards intents and republishes state.
@MainActor
@Observable
final class ExplorerStore {
    private(set) var state: ExplorerState

    /// Search text is owned here so typing never waits on a state round-trip.
    var originText = "" {
        didSet { if originText != oldValue { explorer.setOriginQuery(query: originText) } }
    }
    var destinationText = "" {
        didSet { if destinationText != oldValue { explorer.setDestinationQuery(query: destinationText) } }
    }

    @ObservationIgnored private let explorer: FlightExplorer
    @ObservationIgnored private var observation: StateObservation?

    init(baseURL: String = "http://localhost:8080") {
        let storage = FileManager.default
            .urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("FlightExplorer", isDirectory: true)
        explorer = FlightExplorerFactory.shared.create(storageDirectory: storage.path, baseUrl: baseURL)
        state = ExplorerState.companion.INITIAL
        // The shared layer delivers updates on the main thread.
        observation = explorer.observe { [weak self] newState in
            MainActor.assumeIsolated { self?.state = newState }
        }
        explorer.start()
    }

    // MARK: Data

    func refresh() { explorer.refresh() }

    // MARK: Filters

    func setDateRange(start: String?, end: String?) { explorer.setDateRange(startDate: start, endDate: end) }
    func setCabin(_ cabin: String?) { explorer.setCabin(cabin: cabin) }
    func setMaxPoints(_ points: Int?) { explorer.setMaxPoints(maxPoints: points.map { KotlinInt(int: Int32($0)) }) }
    func toggleProgram(_ program: String) { explorer.toggleProgram(program: program) }
    func setPrograms(_ programs: Set<String>) { explorer.setPrograms(programs: programs) }

    func clear(_ kind: FilterKind) {
        if kind == .origin { originText = "" }
        if kind == .destination { destinationText = "" }
        explorer.clearFilter(kind: kind)
    }

    func resetFilters() {
        originText = ""
        destinationText = ""
        explorer.resetFilters()
    }

    func activeLabel(_ kind: FilterKind) -> String? {
        state.activeFilters.first { $0.kind == kind }?.label
    }

    // MARK: Selection & saving

    func toggleSelection(_ flight: Flight) {
        explorer.select(flightId: state.selectedFlight?.id == flight.id ? nil : flight.id)
    }

    func select(_ flight: Flight?) { explorer.select(flightId: flight?.id) }
    func toggleSaved(_ flight: Flight) { explorer.toggleSaved(flight: flight) }
    func remove(_ flight: Flight) { explorer.remove(flightId: flight.id) }

    func isSaved(_ flight: Flight) -> Bool { state.isSaved(flightId: flight.id) }
    func isPending(_ flight: Flight) -> Bool { state.isPending(flightId: flight.id) }

    func dismissMessage() {
        if let message = state.message { explorer.dismissMessage(id: message.id) }
    }
}

/// ISO `yyyy-MM-dd` <-> `Date` in the user's calendar, so pickers show the same day the data means.
enum ISODate {
    static func date(from iso: String) -> Date? {
        let parts = iso.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        return Calendar.current.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2]))
    }

    static func string(from date: Date) -> String {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }
}
