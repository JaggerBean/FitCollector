import BackgroundTasks
import SwiftUI

@main
struct StepCraftApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var appState = AppState()

    init() {
        // Register background fetch
        _ = BackgroundSyncManager.shared
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(appState)
                .onAppear {
                    BackgroundSyncManager.shared.scheduleBackgroundSync()
                }
        }
    }
}
