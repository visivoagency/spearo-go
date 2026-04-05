import SwiftUI
import SwiftData
import WatchKit

@main
struct SpearoGoApp: App {
    @WKApplicationDelegateAdaptor(AppDelegate.self) private var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        .modelContainer(for: SavedLocation.self)
    }
}

// MARK: - App delegate (background refresh)

final class AppDelegate: NSObject, WKApplicationDelegate {
    func handle(_ backgroundTasks: Set<WKRefreshBackgroundTask>) {
        for task in backgroundTasks {
            switch task {
            case let refreshTask as WKApplicationRefreshBackgroundTask:
                Task {
                    // Re-fetch conditions silently every 30 minutes
                    let state = AppState()
                    await state.refresh()
                    scheduleNextRefresh()
                    refreshTask.setTaskCompletedWithSnapshot(false)
                }
            case let snapshotTask as WKSnapshotRefreshBackgroundTask:
                snapshotTask.setTaskCompleted(
                    restoredDefaultState: true,
                    estimatedSnapshotExpiration: Date().addingTimeInterval(1800),
                    userInfo: nil
                )
            default:
                task.setTaskCompletedWithSnapshot(false)
            }
        }
    }

    func applicationDidFinishLaunching() {
        scheduleNextRefresh()
    }
}

// MARK: - Background refresh scheduling

private func scheduleNextRefresh() {
    WKApplication.shared().scheduleBackgroundRefresh(
        withPreferredDate: Date().addingTimeInterval(1800), // 30 min
        userInfo: nil
    ) { error in
        if let error {
            print("[SpearoGo] Background refresh scheduling failed: \(error)")
        }
    }
}
