import Foundation

@MainActor
final class SyncService: ObservableObject {
    @Published var lastSyncMessage: String?
    @Published var lastErrorMessage: String?
    @Published var lastSyncDate: Date?

    func syncSteps(appState: AppState, manual: Bool = true) async {
        lastSyncMessage = nil
        lastErrorMessage = nil

        guard appState.autoSyncEnabled || manual else { return }
        guard appState.isConfigured() else {
            lastErrorMessage = "Complete onboarding before syncing."
            return
        }

        do {
            let stepsToday: Int
            do {
                stepsToday = try await HealthKitManager.shared.readTodaySteps()
            } catch {
                if shouldSuppress(error: error) {
                    return
                }
                throw error
            }
            appState.lastKnownSteps = stepsToday

            let dayKey = Self.centralDayKey()
            let timestamp = ISO8601DateFormatter().string(from: Date())

            var successServers: [String] = []
            var errorGroups: [String: [String]] = [:]

            for server in appState.selectedServers {
                do {
                    let key = try await getOrRecoverKey(appState: appState, server: server)
                    let payload = IngestPayload(
                        minecraftUsername: appState.minecraftUsername,
                        deviceId: appState.deviceId,
                        stepsToday: stepsToday,
                        playerApiKey: key,
                        day: dayKey,
                        source: "healthkit",
                        timestamp: timestamp
                    )
                    _ = try await ApiClient.shared.ingest(payload)
                    successServers.append(server)
                } catch {
                    let message = error.localizedDescription
                    errorGroups[message, default: []].append(server)
                }
            }

            let successMsg = successServers.isEmpty ? nil : "Synced to \(successServers.joined(separator: ", "))"
            let errorMsg = errorGroups.isEmpty ? nil : errorGroups.map { "Failed for \($0.value.joined(separator: ", ")): \($0.key)" }.joined(separator: " | ")

            if let successMsg {
                lastSyncMessage = successMsg
                lastSyncDate = Date()
            }
            if let errorMsg { lastErrorMessage = errorMsg }

            let logMessage = successMsg ?? errorMsg ?? "Unknown failure"
            let logEntry = SyncLogEntry(
                timestamp: Self.logTimestamp(),
                steps: stepsToday,
                source: manual ? "Manual" : "Auto",
                success: !successServers.isEmpty,
                message: logMessage
            )
            appState.addSyncLogEntry(logEntry)
        } catch {
            if shouldSuppress(error: error) { return }
            lastErrorMessage = error.localizedDescription
        }
    }

    private func shouldSuppress(error: Error) -> Bool {
        let message = error.localizedDescription.lowercased()
        if message.contains("no data available for the specified predicate") { return true }
        if message.contains("data couldn't be read because it is missing") { return true }
        if message.contains("data couldn’t be read because it is missing") { return true }
        return false
    }

    private func getOrRecoverKey(appState: AppState, server: String) async throws -> String {
        if let key = appState.serverKey(for: server) { return key }
        do {
            let resp = try await ApiClient.shared.recoverKey(
                deviceId: appState.deviceId,
                minecraftUsername: appState.minecraftUsername,
                serverName: server
            )
            appState.setServerKey(server: server, apiKey: resp.playerApiKey)
            return resp.playerApiKey
        } catch {
            let invite = appState.inviteCodesByServer[server]
            let resp = try await ApiClient.shared.register(
                deviceId: appState.deviceId,
                minecraftUsername: appState.minecraftUsername,
                serverName: server,
                inviteCode: invite
            )
            appState.setServerKey(server: server, apiKey: resp.playerApiKey)
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
