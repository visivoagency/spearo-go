import SwiftUI
import SwiftData

struct ContentView: View {
    @Environment(\.modelContext) private var modelContext
    @Query(filter: #Predicate<SavedLocation> { !$0.isDeleted },
           sort: \SavedLocation.createdAt, order: .reverse)
    private var savedLocations: [SavedLocation]

    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding = false
    @State private var appState = AppState()

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
        .task { backfillLastModified(context: modelContext) }
    }

    private var mainContent: some View {
        TabView {
            VerdictPage()
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
        .onChange(of: savedLocations) { _, updated in
            if let active = updated.first(where: { $0.isActive }) {
                appState.activeOverrideCoordinate = active.coordinate
                appState.activeOverrideName = active.name
            } else {
                appState.activeOverrideCoordinate = nil
                appState.activeOverrideName = nil
            }
        }
    }
}

// MARK: - Previews

#Preview {
    ContentView()
        .modelContainer(for: SavedLocation.self, inMemory: true)
}
