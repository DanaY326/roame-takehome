import SwiftUI
import Shared

enum FilterSheet: String, Identifiable {
    case dates, points, programs
    var id: String { rawValue }
}

struct FilterSheetView: View {
    let sheet: FilterSheet
    let store: ExplorerStore

    var body: some View {
        switch sheet {
        case .dates: DateRangeSheet(store: store).presentationDetents([.medium])
        case .points: PointsSheet(store: store).presentationDetents([.height(260)])
        case .programs: ProgramsSheet(store: store).presentationDetents([.medium])
        }
    }
}

private struct SheetScaffold<Content: View>: View {
    let title: String
    let onReset: () -> Void
    @ViewBuilder let content: Content
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            content
                .navigationTitle(title)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) { Button("Reset", action: onReset) }
                    ToolbarItem(placement: .topBarTrailing) { Button("Done") { dismiss() }.fontWeight(.semibold) }
                }
        }
    }
}

private struct DateRangeSheet: View {
    let store: ExplorerStore
    @State private var start = Date()
    @State private var end = Date()
    @State private var ready = false

    var body: some View {
        let options = store.state.options
        let lower = options.minDate.flatMap(ISODate.date(from:)) ?? Date()
        let upper = options.maxDate.flatMap(ISODate.date(from:)) ?? Date()
        SheetScaffold(title: "Departure dates", onReset: { store.clear(.dates); load() }) {
            Form {
                DatePicker("From", selection: $start, in: lower...upper, displayedComponents: .date)
                DatePicker("To", selection: $end, in: max(start, lower)...upper, displayedComponents: .date)
                Section {
                    Text("Both dates are included.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .onAppear(perform: load)
        .onChange(of: start) { apply() }
        .onChange(of: end) { apply() }
    }

    private func load() {
        let state = store.state
        let s = state.filter.startDate ?? state.options.minDate
        let e = state.filter.endDate ?? state.options.maxDate
        start = s.flatMap(ISODate.date(from:)) ?? Date()
        end = e.flatMap(ISODate.date(from:)) ?? Date()
        ready = true
    }

    private func apply() {
        guard ready else { return }
        if end < start { end = start }
        store.setDateRange(start: ISODate.string(from: start), end: ISODate.string(from: end))
    }
}

private struct PointsSheet: View {
    let store: ExplorerStore
    @State private var value: Double = 0

    var body: some View {
        let options = store.state.options
        let step = Double(options.pointsStep)
        let lower = (Double(options.minPoints) / step).rounded(.down) * step
        let upper = (Double(options.maxPoints) / step).rounded(.up) * step
        SheetScaffold(title: "Maximum points", onReset: { store.setMaxPoints(nil); value = upper }) {
            VStack(spacing: 18) {
                Text(value >= upper ? "Any price" : "Up to \(Formatting.shared.points(value: Int32(value)))")
                    .font(.title3.weight(.semibold))
                    .monospacedDigit()
                    .contentTransition(.numericText())
                Slider(value: $value, in: lower...max(upper, lower + step), step: step) {
                    Text("Maximum points")
                } minimumValueLabel: {
                    Text(Formatting.shared.compactPoints(value: Int32(lower))).font(.caption)
                } maximumValueLabel: {
                    Text(Formatting.shared.compactPoints(value: Int32(upper))).font(.caption)
                }
                .onChange(of: value) { store.setMaxPoints(value >= upper ? nil : Int(value)) }
                Text("Includes flights priced exactly at the limit.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Spacer()
            }
            .padding(20)
        }
        .onAppear {
            value = store.state.filter.maxPoints.map { Double($0.int32Value) } ?? upper
        }
    }
}

private struct ProgramsSheet: View {
    let store: ExplorerStore

    var body: some View {
        let state = store.state
        SheetScaffold(title: "Loyalty programs", onReset: { store.setPrograms([]) }) {
            List {
                Section {
                    ForEach(state.options.programs, id: \.self) { program in
                        let isOn = state.filter.programs.contains(program)
                        Button { store.toggleProgram(program) } label: {
                            HStack {
                                Text(program).foregroundStyle(Color.primary)
                                Spacer()
                                Image(systemName: isOn ? "checkmark.circle.fill" : "circle")
                                    .foregroundStyle(isOn ? Theme.accent : Color.secondary)
                                    .font(.title3)
                            }
                        }
                        .accessibilityAddTraits(isOn ? .isSelected : [])
                    }
                } footer: {
                    Text("Show flights bookable with any selected program. Select none to include all.")
                }
            }
        }
    }
}
