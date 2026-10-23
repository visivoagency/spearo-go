import SwiftUI
import SwiftData
import CoreLocation
import AuthenticationServices

// Presented as a sheet from Verdict page long-press (Sprint 2 gesture).
// For now accessible via Digital Crown or a dedicated navigation entry.
struct LocationsView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(AppState.self)  private var appState
    @Environment(\.dismiss)      private var dismiss

    @Query(filter: #Predicate<SavedLocation> { !$0.isDeleted },
           sort: \SavedLocation.createdAt, order: .reverse)
    private var locations: [SavedLocation]

    @State private var showingAddSheet = false
    @State private var showingSearchSheet = false

    var body: some View {
        NavigationStack {
            List {
                // Current GPS location
                currentLocationRow

                // Saved spots
                ForEach(locations) { location in
                    LocationRow(location: location, isActive: location.isActive)
                        .onTapGesture { activate(location) }
                        .accessibilityAddTraits(.isButton)
                        // Signed in, a Vision-owned spot is read-only here: no swipe-to-delete.
                        // Signed out it can be removed from the watch (Vision still owns it).
                        .deleteDisabled(location.origin == "VISION" && SyncServices.status.state != .signedOut)
                }
                .onDelete(perform: delete)

                // Spearo account: sign in, or sync status and sign out
                SyncRows()

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
                                .brandFont(Brand.Typography.personalityCopy)
                                .foregroundStyle(Brand.Colors.textSecondary)
                        }
                    }

                    HStack(spacing: Brand.Spacing.item) {
                        Image(systemName: "info.circle")
                            .font(.system(size: 12))
                            .foregroundStyle(Brand.Colors.textSecondary)
                            .frame(width: 18)
                        Text("v\(Constants.App.version)")
                            .brandFont(Brand.Typography.caption)
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
                    Button { showingSearchSheet = true } label: {
                        Image(systemName: "magnifyingglass")
                            .foregroundStyle(Brand.Colors.primary)
                    }
                    .accessibilityLabel("Search for a place")
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button { showingAddSheet = true } label: {
                        Image(systemName: "plus")
                            .foregroundStyle(Brand.Colors.primary)
                    }
                    .accessibilityLabel("Save my current location")
                }
            }
        }
        .sheet(isPresented: $showingAddSheet) {
            AddLocationView()
        }
        .sheet(isPresented: $showingSearchSheet) {
            SearchSpotView()
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
                    .brandFont(Brand.Typography.personalityCopy)
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
        .accessibilityAddTraits(.isButton)
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
        // Signed out and never linked: removed outright. Otherwise soft-deleted until its account syncs.
        let sessionUid = SyncServices.auth.session()?.uid
        let rows = offsets.map { locations[$0] }
        for row in rows { deleteSpot(row, sessionUid: sessionUid, context: modelContext) }
        try? modelContext.save()
        syncSpotsInBackground()
    }
}

/// Fire-and-forget: never awaited by anything that renders the verdict. A no-op while signed out.
@MainActor
func syncSpotsInBackground() {
    Task { await SyncServices.sync.sync() }
}

// MARK: - Sync rows

/// The account rows: "Sync with spearo.pro" when signed out, status + "Sign out" when signed in.
struct SyncRows: View {
    private let status = SyncServices.status
    @State private var apple = SyncServices.appleSignIn()
    @State private var signingIn = false
    @State private var message: String?

    var body: some View {
        if status.state == .signedOut {
            signedOutRows
        } else {
            signedInRows
        }
    }

    private var signedOutRows: some View {
        VStack(alignment: .leading, spacing: Brand.Spacing.micro) {
            Text("Sync with spearo.pro")
                .brandFont(Brand.Typography.personalityCopy)
                .foregroundStyle(Brand.Colors.textPrimary)
            if signingIn {
                Text("Signing in…").captionStyle()
            } else {
                Text("Sign in to keep your spots in your Spearo account").captionStyle()
                SignInWithAppleButton(.signIn) { request in
                    apple.prepare(request)
                } onCompletion: { result in
                    signingIn = true
                    message = nil
                    Task { await finish(await apple.complete(result)) }
                }
            }
            if let message {
                Text(message).captionStyle()
            }
        }
    }

    @MainActor
    private func finish(_ result: SignInResult) async {
        switch result {
        case .success(let session):
            message = nil
            // Still "Signing in…" until onSignedIn has handed off to sync (.syncing), so the
            // button can't start a second sign-in during the account-switch removal, marker or
            // first sync.
            await SyncServices.sync.onSignedIn(session: session)
            signingIn = false
        case .accountExistsWithDifferentMethod:
            message = "You already have a Spearo account with this email. Sign in the way you did before, or link methods at spearo.pro."
            signingIn = false
        case .cancelled:
            message = nil
            signingIn = false
        case .failed(let text):
            message = text
            signingIn = false
        }
    }

    @ViewBuilder
    private var signedInRows: some View {
        VStack(alignment: .leading, spacing: 1) {
            Text("Sync with spearo.pro")
                .brandFont(Brand.Typography.personalityCopy)
                .foregroundStyle(Brand.Colors.textPrimary)
            TimelineView(.periodic(from: .now, by: 30)) { context in
                Text(Self.statusText(status.state, now: context.date)).captionStyle()
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { syncSpotsInBackground() }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
        .accessibilityHint("Double tap to sync now")

        VStack(alignment: .leading, spacing: 1) {
            Text("Sign out")
                .brandFont(Brand.Typography.personalityCopy)
                .foregroundStyle(Brand.Colors.textPrimary)
            Text("Your spots stay on this watch").captionStyle()
        }
        .contentShape(Rectangle())
        .onTapGesture { Task { await SyncServices.sync.signOut() } }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }

    /// "Synced · just now" / "Synced · N min ago" / "Syncing…" / the failure message.
    static func statusText(_ state: SyncState, now: Date) -> String {
        switch state {
        case .syncing: return "Syncing…"
        case .failed(let message): return message
        case .synced(let at):
            let minutes = max(0, Int(now.timeIntervalSince(at) / 60))
            if minutes < 1 { return "Synced · just now" }
            if minutes < 60 { return "Synced · \(minutes) min ago" }
            return "Synced · \(minutes / 60) h ago"
        case .signedOut, .idle: return "Tap to sync"
        }
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
                    .brandFont(Brand.Typography.personalityCopy)
                    .foregroundStyle(Brand.Colors.textPrimary)
                Text(coordString(location))
                    .captionStyle()
                if location.origin == "VISION" {
                    Text("Saved in Vision").captionStyle()
                }
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
                    .brandFont(Brand.Typography.personalityCopy)
                    .foregroundStyle(Brand.Colors.textPrimary)
                    .padding(Brand.Spacing.item)
                    .brandCard()

                HStack(spacing: Brand.Spacing.item) {
                    Button("Cancel") { dismiss() }
                        .brandFont(Brand.Typography.itemLabel)
                        .foregroundStyle(Brand.Colors.textSecondary)

                    Button("Save") { save(coord) }
                        .brandFont(Brand.Typography.itemLabel)
                        .foregroundStyle(Brand.Colors.primary)
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            } else {
                Text("GPS not available")
                    .captionStyle()
                Button("Dismiss") { dismiss() }
                    .brandFont(Brand.Typography.itemLabel)
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
        location.pendingSync = true   // lastModifiedAt is already now (init)
        location.ownerUid = SyncServices.auth.session()?.uid   // the signed-in account, if any
        modelContext.insert(location)
        try? modelContext.save()
        syncSpotsInBackground()
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

// MARK: - Search a place by name

/// Adds a spot the diver is not standing at.
///
/// Until this existed both apps could only save "here", so a spot could only be
/// added by physically going to it.
struct SearchSpotView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(AppState.self)  private var appState
    @Environment(\.dismiss)      private var dismiss

    private let geocoder = GeocodingService()

    @State private var query: String = ""
    @State private var results: [GeocodedPlace] = []
    @State private var isSearching = false
    @State private var searched = false
    @State private var failed = false

    var body: some View {
        ScrollView {
            VStack(spacing: Brand.Spacing.item) {
                Text("Find a spot")
                    .brandSectionHeader()

                TextField("Place name", text: $query)
                    .brandFont(Brand.Typography.personalityCopy)
                    .foregroundStyle(Brand.Colors.textPrimary)
                    .padding(Brand.Spacing.item)
                    .brandCard()
                    .onSubmit { Task { await search() } }
                    .submitLabel(.search)

                if isSearching {
                    Text("Searching…").captionStyle()
                } else if failed {
                    // A failed lookup is not "no such place".
                    Text("Couldn't search. Try again.").captionStyle()
                } else if searched && results.isEmpty {
                    Text("No places found").captionStyle()
                }

                ForEach(results) { place in
                    Button { save(place) } label: {
                        VStack(alignment: .leading, spacing: Brand.Spacing.micro) {
                            Text(place.name)
                                .brandFont(Brand.Typography.personalityCopy)
                                .foregroundStyle(Brand.Colors.textPrimary)
                            // Region and country are the whole point: "Lagos"
                            // matches five places and the Portuguese one is
                            // fourth.
                            Text(place.label)
                                .captionStyle()
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                    .padding(Brand.Spacing.item)
                    .brandCard()
                    .accessibilityLabel("\(place.name), \(place.label)")
                }
            }
            .padding(Brand.Spacing.page)
        }
        .brandPage()
    }

    private func search() async {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        isSearching = true
        failed = false
        defer { isSearching = false; searched = true }
        do {
            results = try await geocoder.search(trimmed)
        } catch {
            results = []
            failed = true
        }
    }

    /// Saving a searched place also switches to it, which is always the intent.
    private func save(_ place: GeocodedPlace) {
        for existing in (try? modelContext.fetch(FetchDescriptor<SavedLocation>())) ?? [] {
            existing.isActive = false
        }
        let location = SavedLocation(name: place.savedName, coordinate: place.coordinate)
        location.isActive = true
        location.pendingSync = true   // lastModifiedAt is already now (init)
        location.ownerUid = SyncServices.auth.session()?.uid   // the signed-in account, if any
        modelContext.insert(location)
        try? modelContext.save()
        syncSpotsInBackground()
        dismiss()
    }
}
