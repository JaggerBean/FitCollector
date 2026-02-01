import SwiftUI
import BackgroundTasks
// Import the BackgroundSyncManager from Services folder

import BackgroundTasks

@main
struct StepCraftApp: App {
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
