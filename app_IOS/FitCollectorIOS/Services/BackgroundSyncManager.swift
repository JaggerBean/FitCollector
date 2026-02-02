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
        let semaphore = DispatchSemaphore(value: 0)
        Task {
            await self.performSyncAsync()
            semaphore.signal()
        }
        _ = semaphore.wait(timeout: .now() + 25)
    }

    private func performSyncAsync() async {
        guard AppState.isBackgroundSyncEnabled() else { return }

        _ = AppState.applyQueuedUsernameIfReadyFromStorage()

        let username = AppState.currentMinecraftUsername().trimmingCharacters(in: .whitespacesAndNewlines)
        let selectedServers = AppState.selectedServersSnapshot()
        guard !username.isEmpty, !selectedServers.isEmpty else { return }

        do {
            let stepsToday = try await HealthKitManager.shared.readTodaySteps()
            let dayKey = Self.centralDayKey()
            let timestamp = ISO8601DateFormatter().string(from: Date())
            let inviteCodes = AppState.inviteCodesSnapshot()

            var successServers: [String] = []
            var errorGroups: [String: [String]] = [:]

            for server in selectedServers {
                do {
                    let key = try await getOrRecoverKey(
                        username: username,
                        server: server,
                        deviceId: AppState.currentDeviceId(),
                        inviteCode: inviteCodes[server]
                    )
                    let payload = IngestPayload(
                        minecraftUsername: username,
                        deviceId: AppState.currentDeviceId(),
                        stepsToday: stepsToday,
                        playerApiKey: key,
                        day: dayKey,
                        source: "healthkit",
                        timestamp: timestamp
                    )
                    _ = try await ApiClient.shared.ingest(payload)
                    successServers.append(server)
                } catch {
                    errorGroups[error.localizedDescription, default: []].append(server)
                }
            }

            let successMsg = successServers.isEmpty ? nil : "Synced to \(successServers.joined(separator: ", "))"
            let errorMsg = errorGroups.isEmpty ? nil : errorGroups.map { "Failed for \($0.value.joined(separator: ", ")): \($0.key)" }.joined(separator: " | ")
            let logMessage = successMsg ?? errorMsg ?? "Unknown failure"

            let entry = SyncLogEntry(
                timestamp: Self.logTimestamp(),
                steps: stepsToday,
                source: "Background",
                success: !successServers.isEmpty,
                message: logMessage
            )
            AppState.appendSyncLogEntry(entry)
        } catch {
            let entry = SyncLogEntry(
                timestamp: Self.logTimestamp(),
                steps: 0,
                source: "Background",
                success: false,
                message: error.localizedDescription
            )
            AppState.appendSyncLogEntry(entry)
        }
    }

    private func getOrRecoverKey(
        username: String,
        server: String,
        deviceId: String,
        inviteCode: String?
    ) async throws -> String {
        if let key = AppState.serverKeyFor(username: username, server: server) {
            return key
        }

        do {
            let resp = try await ApiClient.shared.recoverKey(
                deviceId: deviceId,
                minecraftUsername: username,
                serverName: server
            )
            AppState.saveServerKey(username: username, server: server, apiKey: resp.playerApiKey)
            return resp.playerApiKey
        } catch {
            let resp = try await ApiClient.shared.register(
                deviceId: deviceId,
                minecraftUsername: username,
                serverName: server,
                inviteCode: inviteCode
            )
            AppState.saveServerKey(username: username, server: server, apiKey: resp.playerApiKey)
            return resp.playerApiKey
        }
    }

    private static func centralDayKey() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = TimeZone(identifier: "America/Chicago")
        return formatter.string(from: Date())
    }

    private static func logTimestamp() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        formatter.timeZone = TimeZone.current
        return formatter.string(from: Date())
    }
}
