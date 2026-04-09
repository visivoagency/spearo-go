import SwiftUI
import SwiftData

struct ContentView: View {
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \SavedLocation.createdAt, order: .reverse)
    private var savedLocations: [SavedLocation]

    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding = false
    @State private var appState = AppState()

    var body: some View {
        if hasCompletedOnboarding {
            mainContent
        } else {
            OnboardingView(hasCompletedOnboarding: $hasCompletedOnboarding)
                .environment(appState)
        }
    }

    private var mainContent: some View {
        TabView {
            VerdictPage()
                .tag(0)
            ConditionsPage()
                .tag(1)
            WaterPage()
                .tag(2)
            TidesPage()
                .tag(3)
            FishActivityPage()
                .tag(4)
        }
        .tabViewStyle(.page)
        .background(Brand.Colors.background)
        .environment(appState)
        .task {
            if let active = savedLocations.first(where: { $0.isActive }) {
                appState.activeOverrideCoordinate = active.coordinate
            }
            await appState.refresh()
        }
        .onChange(of: savedLocations) { _, updated in
            if let active = updated.first(where: { $0.isActive }) {
                appState.activeOverrideCoordinate = active.coordinate
            } else {
                appState.activeOverrideCoordinate = nil
            }
        }
    }
}

// MARK: - Previews

#Preview {
    ContentView()
        .modelContainer(for: SavedLocation.self, inMemory: true)
}
