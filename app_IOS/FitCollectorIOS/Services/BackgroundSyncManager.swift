import Foundation
import BackgroundTasks

class BackgroundSyncManager {
    static let shared = BackgroundSyncManager()
    private let taskIdentifier = "com.fitcollector.backgroundsync"

    private init() {
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

        let queue = OperationQueue()
        queue.maxConcurrentOperationCount = 1

        let syncOperation = BlockOperation {
            self.performSync()
        }

        task.expirationHandler = {
            queue.cancelAllOperations()
        }

        syncOperation.completionBlock = {
            task.setTaskCompleted(success: !syncOperation.isCancelled)
        }

        queue.addOperation(syncOperation)
    }

    private func performSync() {
        // TODO: Implement your sync logic here (e.g., network requests, data updates)
        print("Background sync performed.")
    }
}
