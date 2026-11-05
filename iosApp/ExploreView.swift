import SwiftUI
import Shared

enum ExploreMode: String, CaseIterable, Identifiable {
    case list = "List", map = "Map"
    var id: String { rawValue }
}

struct ExploreView: View {
    @Bindable var store: ExplorerStore
    @State private var mode: ExploreMode = .list
    @State private var openSheet: FilterSheet?

    private var state: ExplorerState { store.state }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if state.status == .ready && state.totalFlightCount > 0 {
                    SearchHeader(store: store, openSheet: $openSheet)
                    StatusBanner(store: store)
                }
                content
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Flights")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Picker("View", selection: $mode) {
                        ForEach(ExploreMode.allCases) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    .frame(width: 180)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    if state.isRefreshing && state.status == .ready {
                        ProgressView()
                    } else {
                        Button { store.refresh() } label: { Image(systemName: "arrow.clockwise") }
                            .accessibilityLabel("Refresh")
                            .disabled(state.status == .loading)
                    }
                }
            }
            .sheet(item: $openSheet) { sheet in
                FilterSheetView(sheet: sheet, store: store)
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch state.status {
        case .loading:
            ProgressView("Loading flights…")
        case .error:
            ContentUnavailableView {
                Label("Can't load flights", systemImage: "wifi.exclamationmark")
            } description: {
                Text(state.errorMessage ?? "Something went wrong.")
            } actions: {
                Button("Try Again") { store.refresh() }
                    .buttonStyle(.borderedProminent)
            }
        default:
            if state.totalFlightCount == 0 {
                ContentUnavailableView {
                    Label("No flights available", systemImage: "airplane")
                } description: {
                    Text("The server responded, but there are no flights right now.")
                } actions: {
                    Button("Refresh") { store.refresh() }
                }
            } else {
                discovery
            }
        }
    }

    private var discovery: some View {
        ZStack {
            switch mode {
            case .list: FlightListView(store: store)
            case .map: FlightMapView(store: store)
            }
        }
        .safeAreaInset(edge: .bottom) {
            if let flight = state.selectedFlight {
                FlightCard(
                    flight: flight,
                    isSaved: store.isSaved(flight),
                    isPending: store.isPending(flight),
                    onToggleSave: { store.toggleSaved(flight) },
                    onClose: { store.select(nil) }
                )
                .padding(.horizontal, 12)
                .padding(.bottom, 8)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .id(flight.id)
            }
        }
        .animation(.snappy(duration: 0.25), value: state.selectedFlight?.id)
    }
}

// MARK: - Search & filters header

private struct SearchHeader: View {
    @Bindable var store: ExplorerStore
    @Binding var openSheet: FilterSheet?
    @FocusState private var focused: Field?

    private enum Field { case origin, destination }

    var body: some View {
        let state = store.state
        VStack(alignment: .leading, spacing: 10) {
            VStack(spacing: 0) {
                field("From", systemImage: "airplane.departure", text: $store.originText, field: .origin)
                Divider().padding(.leading, 40)
                field("To", systemImage: "airplane.arrival", text: $store.destinationText, field: .destination)
            }
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Color(.secondarySystemGroupedBackground)))

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    Button { openSheet = .dates } label: {
                        FilterChip(title: store.activeLabel(.dates) ?? fullRangeLabel(state), isActive: store.activeLabel(.dates) != nil, systemImage: "calendar")
                    }
                    Menu {
                        Picker("Cabin", selection: Binding(get: { state.filter.cabin }, set: { store.setCabin($0) })) {
                            Text("Any cabin").tag(String?.none)
                            ForEach(state.options.cabins, id: \.self) { Text($0).tag(Optional($0)) }
                        }
                    } label: {
                        FilterChip(title: state.filter.cabin ?? "Cabin", isActive: state.filter.cabin != nil)
                    }
                    Button { openSheet = .points } label: {
                        FilterChip(title: store.activeLabel(.maxPoints) ?? "Points", isActive: state.filter.maxPoints != nil)
                    }
                    Button { openSheet = .programs } label: {
                        FilterChip(title: store.activeLabel(.programs) ?? "Programs", isActive: !state.filter.programs.isEmpty)
                    }
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 16)
            }
            .padding(.horizontal, -16)

            HStack {
                Text(resultSummary(state))
                    .font(.subheadline.weight(.semibold))
                    .contentTransition(.numericText())
                    .animation(.snappy, value: state.resultCount)
                Spacer()
                if state.hasActiveFilters {
                    Button("Reset filters") {
                        focused = nil
                        store.resetFilters()
                    }
                    .font(.subheadline.weight(.medium))
                }
            }
            .frame(minHeight: 22)
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 10)
    }

    private func field(_ placeholder: String, systemImage: String, text: Binding<String>, field: Field) -> some View {
        HStack(spacing: 10) {
            Image(systemName: systemImage)
                .foregroundStyle(.secondary)
                .frame(width: 20)
            TextField("\(placeholder): city or airport code", text: text)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
                .focused($focused, equals: field)
            if !text.wrappedValue.isEmpty {
                Button { text.wrappedValue = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(.tertiary)
                }
                .accessibilityLabel("Clear \(placeholder)")
            }
        }
        .padding(.horizontal, 12)
        .frame(height: 44)
    }

    private func fullRangeLabel(_ state: ExplorerState) -> String {
        guard let min = state.options.minDate, let max = state.options.maxDate else { return "Dates" }
        return FlightDates.shared.formatRange(startIso: min, endIso: max)
    }

    private func resultSummary(_ state: ExplorerState) -> String {
        let count = Formatting.shared.flightCount(count: state.resultCount)
        return state.hasActiveFilters
            ? "\(count) of \(Formatting.shared.groupedNumber(value: state.totalFlightCount))"
            : count
    }
}

// MARK: - Offline / stale banner

private struct StatusBanner: View {
    let store: ExplorerStore

    var body: some View {
        let state = store.state
        if state.isStale {
            HStack(spacing: 10) {
                Image(systemName: "wifi.slash")
                VStack(alignment: .leading, spacing: 1) {
                    Text("Offline — showing saved results").font(.subheadline.weight(.semibold))
                    if let ms = state.lastUpdatedEpochMs {
                        Text("Last updated \(Date(timeIntervalSince1970: Double(ms.int64Value) / 1000).formatted(.relative(presentation: .named)))")
                            .font(.caption)
                    }
                }
                Spacer()
                if state.isRefreshing {
                    ProgressView().controlSize(.small)
                } else {
                    Button("Retry") { store.refresh() }
                        .font(.subheadline.weight(.semibold))
                }
            }
            .foregroundStyle(Color(.label))
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Color.orange.opacity(0.18)))
            .padding(.horizontal, 16)
            .padding(.bottom, 8)
            .accessibilityElement(children: .combine)
        }
    }
}
