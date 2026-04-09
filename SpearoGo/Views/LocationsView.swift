import SwiftUI
import SwiftData
import CoreLocation

// Presented as a sheet from Verdict page long-press (Sprint 2 gesture).
// For now accessible via Digital Crown or a dedicated navigation entry.
struct LocationsView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(AppState.self)  private var appState
    @Environment(\.dismiss)      private var dismiss

    @Query(sort: \SavedLocation.createdAt, order: .reverse)
    private var locations: [SavedLocation]

    @State private var showingAddSheet = false

    var body: some View {
        NavigationStack {
            List {
                // Current GPS location
                currentLocationRow

                // Saved spots
                ForEach(locations) { location in
                    LocationRow(location: location, isActive: location.isActive)
                        .onTapGesture { activate(location) }
                }
                .onDelete(perform: delete)

                // App info & privacy
                Section {
                    NavigationLink {
                        PrivacyPolicyView()
                    } label: {
                        HStack(spacing: Brand.Spacing.item) {
                            Image(systemName: "hand.raised.fill")
                                .font(.system(size: 12))
                                .foregroundStyle(Brand.Colors.textSecondary)
                                .frame(width: 18)
                            Text("Privacy Policy")
                                .font(Brand.Typography.personalityCopy)
                                .foregroundStyle(Brand.Colors.textSecondary)
                        }
                    }

                    HStack(spacing: Brand.Spacing.item) {
                        Image(systemName: "info.circle")
                            .font(.system(size: 12))
                            .foregroundStyle(Brand.Colors.textSecondary)
                            .frame(width: 18)
                        Text("v\(Constants.App.version)")
                            .font(Brand.Typography.caption)
                            .foregroundStyle(Brand.Colors.textSecondary)
                    }
                }
            }
            .listStyle(.plain)
            .background(Brand.Colors.background)
            .navigationTitle("Locations")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { showingAddSheet = true } label: {
                        Image(systemName: "plus")
                            .foregroundStyle(Brand.Colors.primary)
                    }
                }
            }
        }
        .sheet(isPresented: $showingAddSheet) {
            AddLocationView()
        }
    }

    // MARK: - Current GPS row

    private var currentLocationRow: some View {
        HStack(spacing: Brand.Spacing.item) {
            Image(systemName: "location.fill")
                .font(.system(size: 12))
                .foregroundStyle(Brand.Colors.secondary)
                .frame(width: 18)

            VStack(alignment: .leading, spacing: 1) {
                Text("Current Location")
                    .font(Brand.Typography.personalityCopy)
                    .foregroundStyle(Brand.Colors.textPrimary)
                Text("GPS")
                    .captionStyle()
            }

            Spacer()

            if appState.locationService.currentCoordinate != nil
                && !locations.contains(where: { $0.isActive }) {
                activeIndicator
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { deactivateAll() }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Current GPS location")
        .accessibilityHint("Double tap to use current location")
    }

    // MARK: - Helpers

    private var activeIndicator: some View {
        Circle()
            .fill(Brand.Colors.go)
            .frame(width: 7, height: 7)
    }

    private func activate(_ location: SavedLocation) {
        for loc in locations { loc.isActive = false }
        location.isActive = true
        try? modelContext.save()
        Task { await appState.refresh() }
        dismiss()
    }

    private func deactivateAll() {
        for loc in locations { loc.isActive = false }
        try? modelContext.save()
        Task { await appState.refresh() }
        dismiss()
    }

    private func delete(at offsets: IndexSet) {
        for i in offsets { modelContext.delete(locations[i]) }
        try? modelContext.save()
    }
}

// MARK: - Location row

struct LocationRow: View {
    let location: SavedLocation
    let isActive: Bool

    var body: some View {
        HStack(spacing: Brand.Spacing.item) {
            Image(systemName: "mappin.circle.fill")
                .font(.system(size: 12))
                .foregroundStyle(Brand.Colors.primary)
                .frame(width: 18)

            VStack(alignment: .leading, spacing: 1) {
                Text(location.name)
                    .font(Brand.Typography.personalityCopy)
                    .foregroundStyle(Brand.Colors.textPrimary)
                Text(coordString(location))
                    .captionStyle()
            }

            Spacer()

            if isActive {
                Circle()
                    .fill(Brand.Colors.go)
                    .frame(width: 7, height: 7)
            }
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(location.name), \(coordString(location))")
        .accessibilityHint(isActive ? "Currently active" : "Double tap to activate")
    }

    private func coordString(_ loc: SavedLocation) -> String {
        String(format: "%.1f°, %.1f°", loc.latitude, loc.longitude)
    }
}

// MARK: - Add location sheet

struct AddLocationView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(AppState.self)  private var appState
    @Environment(\.dismiss)      private var dismiss

    @State private var name: String = ""

    var body: some View {
        VStack(spacing: Brand.Spacing.section) {
            Text("Save Spot")
                .brandSectionHeader()

            if let coord = appState.locationService.currentCoordinate {
                Text(String(format: "%.4f°, %.4f°", coord.latitude, coord.longitude))
                    .captionStyle()

                TextField("Spot name", text: $name)
                    .font(Brand.Typography.personalityCopy)
                    .foregroundStyle(Brand.Colors.textPrimary)
                    .padding(Brand.Spacing.item)
                    .brandCard()

                HStack(spacing: Brand.Spacing.item) {
                    Button("Cancel") { dismiss() }
                        .font(Brand.Typography.itemLabel)
                        .foregroundStyle(Brand.Colors.textSecondary)

                    Button("Save") { save(coord) }
                        .font(Brand.Typography.itemLabel)
                        .foregroundStyle(Brand.Colors.primary)
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            } else {
                Text("GPS not available")
                    .captionStyle()
                Button("Dismiss") { dismiss() }
                    .font(Brand.Typography.itemLabel)
                    .foregroundStyle(Brand.Colors.textSecondary)
            }
        }
        .padding(Brand.Spacing.page)
        .brandPage()
    }

    private func save(_ coord: CLLocationCoordinate2D) {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        let location = SavedLocation(name: trimmed, coordinate: coord)
        modelContext.insert(location)
        try? modelContext.save()
        dismiss()
    }
}

// MARK: - Previews

#Preview("Locations") {
    LocationsView()
        .previewAsWatch()
        .environment(AppState.preview())
        .modelContainer(for: SavedLocation.self, inMemory: true)
}
