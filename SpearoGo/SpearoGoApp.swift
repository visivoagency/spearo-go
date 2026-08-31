import SwiftUI
import SwiftData
#if os(watchOS)
import WatchKit
#endif

@main
struct SpearoGoApp: App {
    #if os(watchOS)
    @WKApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    #endif

    var body: some Scene {
        WindowGroup {
            ContentView()
                // Type now scales with Accessibility -> Larger Text (see
                // Brand.Typography). Bounded at accessibility2 because a watch
                // face is 1.5 inches wide: past this point the verdict ring and
                // the two-column condition rows truncate rather than reflow,
                // which reads as broken rather than as large. Everything below
                // this ceiling is honoured.
                .dynamicTypeSize(...DynamicTypeSize.accessibility2)
        }
        .modelContainer(for: SavedLocation.self)
    }
}

// MARK: - App delegate (background refresh)

#if os(watchOS)
final class AppDelegate: NSObject, WKApplicationDelegate {
    func handle(_ backgroundTasks: Set<WKRefreshBackgroundTask>) {
        for task in backgroundTasks {
            switch task {
            case let refreshTask as WKApplicationRefreshBackgroundTask:
                Task {
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

private func scheduleNextRefresh() {
    WKApplication.shared().scheduleBackgroundRefresh(
        withPreferredDate: Date().addingTimeInterval(1800), // 30 min
        userInfo: nil
    ) { error in
        #if DEBUG
        if let error {
            print("[SpearoGo] Background refresh scheduling failed: \(error)")
        }
        #endif
    }
}
#endif
