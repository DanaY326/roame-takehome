import SwiftUI
import MapKit
import Shared

private struct BrowsedDestination: Identifiable {
    let code: String
    var id: String { code }
}

struct FlightMapView: View {
    let store: ExplorerStore
    @State private var position: MapCameraPosition = .automatic
    @State private var browsing: BrowsedDestination?

    var body: some View {
        let state = store.state
        let selected = state.selectedFlight
        Map(position: $position) {
            if let flight = selected {
                MapPolyline(
                    coordinates: [flight.originCoordinate, flight.destinationCoordinate],
                    contourStyle: .geodesic
                )
                .stroke(Theme.accent, style: StrokeStyle(lineWidth: 3, lineCap: .round))

                Annotation(flight.origin, coordinate: flight.originCoordinate, anchor: .center) {
                    OriginPin(code: flight.origin)
                }
                .annotationTitles(.hidden)
            }

            ForEach(state.markers, id: \.airportCode) { marker in
                Annotation(marker.city, coordinate: marker.coordinate, anchor: .bottom) {
                    MarkerPill(marker: marker, isHighlighted: selected?.destination == marker.airportCode)
                        .onTapGesture { browsing = BrowsedDestination(code: marker.airportCode) }
                }
                .annotationTitles(.hidden)
            }
        }
        .mapStyle(.standard(elevation: .flat, pointsOfInterest: .excludingAll))
        .mapControls {
            MapCompass()
            MapScaleView()
        }
        .overlay {
            if state.markers.isEmpty {
                NoMatchesView(store: store)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: Theme.cardCorner))
                    .padding(32)
                    .frame(maxHeight: 320)
            }
        }
        .sheet(item: $browsing) { destination in
            DestinationFlightsSheet(store: store, code: destination.code) {
                browsing = nil
            }
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
    }
}

private struct MarkerPill: View {
    let marker: DestinationMarker
    let isHighlighted: Bool

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 5) {
                Text(marker.airportCode).font(.caption.weight(.bold))
                Text(Formatting.shared.compactPoints(value: marker.lowestPoints))
                    .font(.caption.weight(.medium))
                    .monospacedDigit()
                Text("\(marker.flightCount)")
                    .font(.caption2.weight(.bold))
                    .padding(.horizontal, 5)
                    .padding(.vertical, 1)
                    .background(Capsule().fill(isHighlighted ? Color.white.opacity(0.25) : Theme.accent.opacity(0.12)))
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 5)
            .foregroundStyle(isHighlighted ? Color.white : Color.primary)
            .background(Capsule().fill(isHighlighted ? AnyShapeStyle(Theme.accent) : AnyShapeStyle(.background)))
            .shadow(color: .black.opacity(0.18), radius: 4, y: 2)
            Triangle()
                .fill(isHighlighted ? AnyShapeStyle(Theme.accent) : AnyShapeStyle(.background))
                .frame(width: 10, height: 6)
        }
        .scaleEffect(isHighlighted ? 1.12 : 1)
        .animation(.snappy, value: isHighlighted)
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(marker.city), \(Formatting.shared.flightCount(count: marker.flightCount)), from \(Formatting.shared.points(value: marker.lowestPoints))")
        .accessibilityAddTraits(.isButton)
    }

    private struct Triangle: Shape {
        func path(in rect: CGRect) -> Path {
            var p = Path()
            p.move(to: CGPoint(x: rect.minX, y: rect.minY))
            p.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
            p.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
            p.closeSubpath()
            return p
        }
    }
}

private struct OriginPin: View {
    let code: String
    var body: some View {
        Text(code)
            .font(.caption2.weight(.bold))
            .foregroundStyle(.white)
            .padding(.horizontal, 6)
            .padding(.vertical, 3)
            .background(Capsule().fill(Color(.label)))
    }
}

/// Lists the flights behind one destination marker.
private struct DestinationFlightsSheet: View {
    let store: ExplorerStore
    let code: String
    let onPick: () -> Void

    var body: some View {
        let state = store.state
        let marker = state.markers.first { $0.airportCode == code }
        NavigationStack {
            Group {
                if let marker {
                    List(marker.flights, id: \.id) { flight in
                        let isSelected = state.selectedFlight?.id == flight.id
                        FlightRow(flight: flight, isSaved: state.savedIds.contains(flight.id), isSelected: isSelected)
                            .onTapGesture {
                                store.select(flight)
                                onPick()
                            }
                            .listRowBackground(isSelected ? Theme.accent.opacity(0.12) : nil)
                    }
                    .listStyle(.plain)
                } else {
                    ContentUnavailableView("No flights", systemImage: "airplane")
                }
            }
            .navigationTitle(marker.map { "\($0.city) (\($0.airportCode))" } ?? code)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if let marker {
                    ToolbarItem(placement: .bottomBar) {
                        Text("\(Formatting.shared.flightCount(count: marker.flightCount)) · from \(Formatting.shared.points(value: marker.lowestPoints))")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
    }
}

extension Flight {
    var originCoordinate: CLLocationCoordinate2D { .init(latitude: originLatitude, longitude: originLongitude) }
    var destinationCoordinate: CLLocationCoordinate2D { .init(latitude: destinationLatitude, longitude: destinationLongitude) }
}

extension DestinationMarker {
    var coordinate: CLLocationCoordinate2D { .init(latitude: latitude, longitude: longitude) }
}
