import SwiftUI
import SwiftData

struct ContentView: View {
    @Environment(\.modelContext) private var modelContext
    @Query(filter: #Predicate<SavedLocation> { !$0.isDeleted },
           sort: \SavedLocation.createdAt, order: .reverse)
    private var savedLocations: [SavedLocation]

    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding = false
    @State private var appState = AppState()
    @State private var page = 0
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        Group {
            if hasCompletedOnboarding {
                mainContent
            } else {
                OnboardingView(hasCompletedOnboarding: $hasCompletedOnboarding)
                    .environment(appState)
            }
        }
        // Spots saved before the account-sync model get their creation time as last-modified.
        .task {
            // A failure is logged and retried by the sync's own step 0, which fails the sync instead.
            do { try backfillLastModified(context: modelContext) }
            catch { print("[ContentView] backfill failed; sync will retry") }
            syncSpotsInBackground()   // cold start: onChange(of: scenePhase) doesn't fire for the initial value
        }
        // Foreground sync: fire-and-forget, a no-op while signed out, never awaited by refresh().
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { syncSpotsInBackground() }
        }
    }

    private var activeSpotKey: String? {
        savedLocations.first(where: { $0.isActive }).map { "\($0.id)|\($0.latitude)|\($0.longitude)|\($0.name)" }
    }

    private var mainContent: some View {
        TabView(selection: $page) {
            VerdictPage(onOpenSettings: { withAnimation { page = 6 } })
                .tag(0)
            TodayPage()
                .tag(1)
            ConditionsPage()
                .tag(2)
            WaterPage()
                .tag(3)
            TidesPage()
                .tag(4)
            FishActivityPage()
                .tag(5)
            // Spots, sign-in and About (Kevin, 25 Oct: a page, not a hidden long-press).
            LocationsView(onDone: { withAnimation { page = 0 } })
                .tag(6)
        }
        .tabViewStyle(.page)
        .background(Brand.Colors.background)
        .environment(appState)
        .task {
            if let active = savedLocations.first(where: { $0.isActive }) {
                appState.activeOverrideCoordinate = active.coordinate
                appState.activeOverrideName = active.name
            }
            await appState.refresh()
        }
        // Keyed on the active spot's identity and place, not the array: SavedLocation compares by
        // identity, so an isActive flipped in place (a spot hidden on an account switch) would
        // not register as a change of the array.
        .onChange(of: activeSpotKey) { _, _ in
            if let active = savedLocations.first(where: { $0.isActive }) {
                appState.activeOverrideCoordinate = active.coordinate
                appState.activeOverrideName = active.name
            } else {
                // The active spot is gone (e.g. it belonged to another account and left on a
                // sign-in): fall back to "Use my location" and redraw from GPS.
                let hadOverride = appState.activeOverrideCoordinate != nil
                appState.activeOverrideCoordinate = nil
                appState.activeOverrideName = nil
                if hadOverride { Task { await appState.refresh() } }
            }
        }
    }
}

// MARK: - Previews

#Preview {
    ContentView()
        .modelContainer(for: SavedLocation.self, inMemory: true)
}
