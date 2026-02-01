import Foundation
import BackgroundTasks

class BackgroundSyncManager {
    static let shared = BackgroundSyncManager()
    private let taskIdentifier = "com.example.fitcollector.backgroundfetch"

    private init() {
        // Register background task
        BGTaskScheduler.shared.register(forTaskWithIdentifier: taskIdentifier, using: nil) { task in
            self.handleAppRefresh(task: task as! BGAppRefreshTask)
        }
    }

    func scheduleBackgroundSync() {
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60) // 15 minutes
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            print("Could not schedule app refresh: \(error)")
        }
    }

    private func handleAppRefresh(task: BGAppRefreshTask) {
        scheduleBackgroundSync() // Schedule next
        // Perform your background sync here
        if let appState = (UIApplication.shared.connectedScenes.first as? UIWindowScene)?.windows.first?.rootViewController?.view?.window?.rootViewController as? AppState {
            if appState.backgroundSyncEnabled {
                // Call your sync logic here, e.g., appState.performSync()
                print("Background sync triggered.")
            }
        }
        task.setTaskCompleted(success: true)
    }
}
