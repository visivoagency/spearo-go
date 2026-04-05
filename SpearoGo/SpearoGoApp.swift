import SwiftUI
import SwiftData

@main
struct SpearoGoApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        .modelContainer(for: SavedLocation.self)
    }
}
