import SwiftUI
import Shared

enum Theme {
    static let accent = Color(red: 0.29, green: 0.25, blue: 0.86)
    static let cardCorner: CGFloat = 20
}

extension Flight {
    var routeTitle: String { "\(origin) → \(destination)" }
    var pointsText: String { Formatting.shared.points(value: points) }
}

/// One row in the flight list.
struct FlightRow: View {
    let flight: Flight
    var isSaved = false
    var isSelected = false
    var footnote: String? = nil

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(flight.origin)
                    Image(systemName: "arrow.right")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.tertiary)
                    Text(flight.destination)
                    if isSaved {
                        Image(systemName: "bookmark.fill")
                            .font(.caption)
                            .foregroundStyle(Theme.accent)
                            .accessibilityLabel("Saved")
                    }
                }
                .font(.headline)

                Text("\(flight.originCity) to \(flight.destinationCity)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                Text("\(FlightDates.shared.formatMedium(iso: flight.departureDate)) · \(flight.cabin) · \(flight.program)")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                if let footnote {
                    Label(footnote, systemImage: "exclamationmark.circle")
                        .font(.caption2)
                        .foregroundStyle(.orange)
                }
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 2) {
                Text(Formatting.shared.groupedNumber(value: flight.points))
                    .font(.headline)
                    .monospacedDigit()
                Text("points")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 6)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// Origin and destination joined by a dashed connector with a plane.
struct RouteConnector: View {
    let flight: Flight

    var body: some View {
        HStack(alignment: .center, spacing: 10) {
            endpoint(code: flight.origin, city: flight.originCity, alignment: .leading)
            ZStack {
                Line()
                    .stroke(style: StrokeStyle(lineWidth: 1.5, lineCap: .round, dash: [3, 4]))
                    .foregroundStyle(Theme.accent.opacity(0.5))
                    .frame(height: 1.5)
                HStack {
                    Circle().fill(Theme.accent).frame(width: 7, height: 7)
                    Spacer()
                    Circle().stroke(Theme.accent, lineWidth: 2).frame(width: 7, height: 7)
                }
                Image(systemName: "airplane")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.accent)
                    .padding(4)
                    .background(Circle().fill(.background))
            }
            .frame(maxWidth: .infinity)
            endpoint(code: flight.destination, city: flight.destinationCity, alignment: .trailing)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(flight.originCity) to \(flight.destinationCity)")
    }

    private func endpoint(code: String, city: String, alignment: HorizontalAlignment) -> some View {
        VStack(alignment: alignment, spacing: 2) {
            Text(code).font(.title2.weight(.bold))
            Text(city).font(.caption).foregroundStyle(.secondary).lineLimit(1)
        }
        .fixedSize()
    }

    private struct Line: Shape {
        func path(in rect: CGRect) -> Path {
            var p = Path()
            p.move(to: CGPoint(x: 0, y: rect.midY))
            p.addLine(to: CGPoint(x: rect.maxX, y: rect.midY))
            return p
        }
    }
}

/// The selected-flight card: route connector, date, cabin, points, program, and Save/Remove.
struct FlightCard: View {
    let flight: Flight
    let isSaved: Bool
    let isPending: Bool
    let onToggleSave: () -> Void
    var onClose: (() -> Void)? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text(flight.program.uppercased())
                    .font(.caption.weight(.semibold))
                    .kerning(0.6)
                    .foregroundStyle(Theme.accent)
                Spacer()
                if let onClose {
                    Button(action: onClose) {
                        Image(systemName: "xmark")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.secondary)
                            .frame(width: 28, height: 28)
                            .background(Circle().fill(Color(.tertiarySystemFill)))
                    }
                    .accessibilityLabel("Close")
                }
            }

            RouteConnector(flight: flight)

            HStack(spacing: 0) {
                detail("Departs", FlightDates.shared.formatLong(iso: flight.departureDate))
                detail("Cabin", flight.cabin)
            }

            Divider()

            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(Formatting.shared.groupedNumber(value: flight.points))
                        .font(.title2.weight(.bold))
                        .monospacedDigit()
                    Text("points · \(flight.program)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                SaveButton(isSaved: isSaved, isPending: isPending, action: onToggleSave)
            }
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: Theme.cardCorner, style: .continuous)
                .fill(.background)
                .shadow(color: .black.opacity(0.12), radius: 18, y: 6)
        )
    }

    private func detail(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.subheadline.weight(.medium))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct SaveButton: View {
    let isSaved: Bool
    let isPending: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if isPending {
                    ProgressView().controlSize(.small)
                } else {
                    Image(systemName: isSaved ? "bookmark.slash" : "bookmark")
                }
                Text(isSaved ? "Remove" : "Save")
            }
            .font(.subheadline.weight(.semibold))
            .frame(minWidth: 96)
        }
        .buttonStyle(.borderedProminent)
        .tint(isSaved ? Color(.systemGray) : Theme.accent)
        .disabled(isPending)
        .sensoryFeedback(.success, trigger: isSaved)
        .animation(.snappy, value: isSaved)
    }
}

/// A pill-shaped filter button. Filled when the filter is active.
struct FilterChip: View {
    let title: String
    let isActive: Bool
    var systemImage: String? = nil

    var body: some View {
        HStack(spacing: 4) {
            if let systemImage { Image(systemName: systemImage).font(.caption) }
            Text(title).lineLimit(1)
            Image(systemName: "chevron.down").font(.caption2.weight(.bold)).opacity(0.6)
        }
        .font(.subheadline.weight(.medium))
        .padding(.horizontal, 12)
        .padding(.vertical, 7)
        .foregroundStyle(isActive ? Color.white : Color.primary)
        .background(
            Capsule().fill(isActive ? AnyShapeStyle(Theme.accent) : AnyShapeStyle(Color(.secondarySystemFill)))
        )
        .contentShape(Capsule())
    }
}
