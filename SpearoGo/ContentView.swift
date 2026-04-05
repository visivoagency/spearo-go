import SwiftUI
import SwiftData

struct ContentView: View {
    @Environment(\.modelContext) private var modelContext
    @State private var appState = AppState()

    var body: some View {
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
        .background(Color(hex: Constants.Colors.background))
        .environment(appState)
        .task {
            await appState.refresh()
        }
    }
}
