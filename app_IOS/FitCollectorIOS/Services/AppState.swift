import Foundation
import SwiftUI

final class AppState: ObservableObject {
    @Published var deviceId: String = AppState.loadDeviceId() {
        didSet {
            UserDefaults.standard.set(deviceId, forKey: Keys.deviceId)
            KeychainHelper.shared.saveString(deviceId, forKey: Keys.deviceId)
        }
    }
    @Published var minecraftUsername: String = UserDefaults.standard.string(forKey: Keys.minecraftUsername) ?? "" {
        didSet { UserDefaults.standard.set(minecraftUsername, forKey: Keys.minecraftUsername) }
    }
    @Published var queuedUsername: String = UserDefaults.standard.string(forKey: Keys.queuedUsername) ?? "" {
        didSet { UserDefaults.standard.set(queuedUsername, forKey: Keys.queuedUsername) }
    }
    @Published var queuedUsernameDay: String = UserDefaults.standard.string(forKey: Keys.queuedUsernameDay) ?? "" {
        didSet { UserDefaults.standard.set(queuedUsernameDay, forKey: Keys.queuedUsernameDay) }
    }
    @Published var lastUsernameChangeDay: String = UserDefaults.standard.string(forKey: Keys.lastUsernameChangeDay) ?? "" {
        didSet { UserDefaults.standard.set(lastUsernameChangeDay, forKey: Keys.lastUsernameChangeDay) }
    }
    @Published var selectedServers: [String] = AppState.loadSelectedServers() {
        didSet { AppState.saveSelectedServers(selectedServers) }
    }
    @Published var serverKeysByUserServer: [String: String] = AppState.loadServerKeys() {
        didSet {
            AppState.saveServerKeys(serverKeysByUserServer)
            AppState.registerPushTokenIfPossible()
        }
    }
    @Published var inviteCodesByServer: [String: String] = AppState.loadInviteCodes() {
        didSet { AppState.saveInviteCodes(inviteCodesByServer) }
    }
    @Published var onboardingComplete: Bool = UserDefaults.standard.bool(forKey: Keys.onboardingComplete) {
        didSet { UserDefaults.standard.set(onboardingComplete, forKey: Keys.onboardingComplete) }
    }
    @Published var autoSyncEnabled: Bool = UserDefaults.standard.object(forKey: Keys.autoSyncEnabled) as? Bool ?? true {
        didSet { UserDefaults.standard.set(autoSyncEnabled, forKey: Keys.autoSyncEnabled) }
    }
    @Published var backgroundSyncEnabled: Bool = UserDefaults.standard.object(forKey: Keys.backgroundSyncEnabled) as? Bool ?? false {
        didSet { UserDefaults.standard.set(backgroundSyncEnabled, forKey: Keys.backgroundSyncEnabled) }
    }
    @Published var backgroundSyncIntervalMinutes: Int = UserDefaults.standard.object(forKey: Keys.backgroundSyncIntervalMinutes) as? Int ?? 15 {
        didSet { UserDefaults.standard.set(backgroundSyncIntervalMinutes, forKey: Keys.backgroundSyncIntervalMinutes) }
    }
    @Published var lastKnownSteps: Int? = AppState.loadLastKnownSteps().steps {
        didSet { AppState.saveLastKnownSteps(steps: lastKnownSteps, dayKey: AppState.dayKey()) }
    }
    @Published var syncLog: [SyncLogEntry] = AppState.loadSyncLog() {
        didSet { AppState.saveSyncLog(syncLog) }
    }

    @Published var trackedMilestonesByServer: [String: Int] = AppState.loadTrackedMilestones() {
        didSet { AppState.saveTrackedMilestones(trackedMilestonesByServer) }
    }

    @Published var adminPushEnabledByServer: [String: Bool] = AppState.loadAdminPushEnabled() {
        didSet { AppState.saveAdminPushEnabled(adminPushEnabledByServer) }
    }

    @Published var notifyTiersByServer: [String: [Int]] = AppState.loadNotifyTiers() {
        didSet { AppState.saveNotifyTiers(notifyTiersByServer) }
    }

    @Published var milestoneNotifiedByKey: [String: String] = AppState.loadMilestoneNotified() {
        didSet { AppState.saveMilestoneNotified(milestoneNotifiedByKey) }
    }

    func isConfigured() -> Bool {
        guard !minecraftUsername.isEmpty else { return false }
        guard !selectedServers.isEmpty else { return false }
        return selectedServers.allSatisfy { serverKey(for: $0) != nil }
    }

    func canChangeUsernameToday() -> Bool {
        lastUsernameChangeDay != AppState.dayKey()
    }

    func markUsernameChangedToday() {
        lastUsernameChangeDay = AppState.dayKey()
    }

    func queueUsername(_ name: String) {
        queuedUsername = name
        queuedUsernameDay = AppState.dayKey()
    }

    func clearQueuedUsername() {
        queuedUsername = ""
        queuedUsernameDay = ""
    }

    @discardableResult
    func applyQueuedUsernameIfReady() -> Bool {
        let queued = queuedUsername.trimmingCharacters(in: .whitespacesAndNewlines)
        let queuedDay = queuedUsernameDay.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !queued.isEmpty, !queuedDay.isEmpty else { return false }
        let today = AppState.dayKey()
        guard queuedDay != today, canChangeUsernameToday() else { return false }
        let previousUsername = minecraftUsername
        if !previousUsername.isEmpty, previousUsername != queued {
            migrateServerKeys(from: previousUsername, to: queued)
        }
        minecraftUsername = queued
        clearQueuedUsername()
        markUsernameChangedToday()
        return true
    }

    func serverKey(for server: String) -> String? {
        let key = makeServerKey(username: minecraftUsername, server: server)
        return serverKeysByUserServer[key]
    }

    func setServerKey(server: String, apiKey: String) {
        let key = makeServerKey(username: minecraftUsername, server: server)
        serverKeysByUserServer[key] = apiKey
    }

    func removeServerKey(server: String) {
        let key = makeServerKey(username: minecraftUsername, server: server)
        serverKeysByUserServer.removeValue(forKey: key)
    }

    func setInviteCode(server: String, code: String?) {
        if let code, !code.isEmpty {
            inviteCodesByServer[server] = code
        } else {
            inviteCodesByServer.removeValue(forKey: server)
        }
    }

    func addSyncLogEntry(_ entry: SyncLogEntry) {
        var updated = syncLog
        updated.insert(entry, at: 0)
        if updated.count > 25 { updated.removeLast(updated.count - 25) }
        syncLog = updated
    }

    func setTrackedMilestone(server: String, minSteps: Int?) {
        if let minSteps {
            trackedMilestonesByServer[server] = minSteps
        } else {
            trackedMilestonesByServer.removeValue(forKey: server)
        }
    }

    func isAdminPushEnabled(server: String) -> Bool {
        adminPushEnabledByServer[server] ?? true
    }

    func setAdminPushEnabled(server: String, enabled: Bool) {
        adminPushEnabledByServer[server] = enabled
    }

    func notificationTiers(server: String) -> Set<Int> {
        Set(notifyTiersByServer[server] ?? [])
    }

    func setNotificationTier(server: String, minSteps: Int, enabled: Bool) {
        var set = notificationTiers(server: server)
        if enabled {
            set.insert(minSteps)
        } else {
            set.remove(minSteps)
        }
        notifyTiersByServer[server] = Array(set).sorted()
    }

    func hasNotifiedMilestone(server: String, minSteps: Int, dayKey: String) -> Bool {
        milestoneNotifiedByKey["\(server)|\(minSteps)"] == dayKey
    }

    func markNotifiedMilestone(server: String, minSteps: Int, dayKey: String) {
        milestoneNotifiedByKey["\(server)|\(minSteps)"] = dayKey
    }

    func resetDeviceId() {
        let newId = "ios-" + UUID().uuidString
        deviceId = newId
        serverKeysByUserServer = [:]
    }

    private static func loadDeviceId() -> String {
        if let keychain = KeychainHelper.shared.readString(forKey: Keys.deviceId), !keychain.isEmpty {
            return keychain
        }
        if let stored = UserDefaults.standard.string(forKey: Keys.deviceId), !stored.isEmpty {
            KeychainHelper.shared.saveString(stored, forKey: Keys.deviceId)
            return stored
        }
        let newId = "ios-" + UUID().uuidString
        KeychainHelper.shared.saveString(newId, forKey: Keys.deviceId)
        UserDefaults.standard.set(newId, forKey: Keys.deviceId)
        return newId
    }

    static func currentDeviceId() -> String {
        loadDeviceId()
    }

    static func storePushToken(_ token: String) {
        UserDefaults.standard.set(token, forKey: Keys.pushToken)
    }

    static func loadPushToken() -> String? {
        UserDefaults.standard.string(forKey: Keys.pushToken)
    }

    static func anyPlayerApiKey() -> String? {
        let keys = loadServerKeys()
        return keys.values.first
    }

    static func registerPushTokenIfPossible() {
        guard let token = loadPushToken(), !token.isEmpty else { return }
        guard let apiKey = anyPlayerApiKey() else { return }

        let deviceId = currentDeviceId()
        let isSandbox: Bool = {
#if DEBUG
            return true
#else
            return false
#endif
        }()

        Task {
            do {
                try await ApiClient.shared.registerPushToken(
                    deviceId: deviceId,
                    playerApiKey: apiKey,
                    token: token,
                    isSandbox: isSandbox
                )
            } catch {
                print("Failed to register push token: \(error)")
            }
        }
    }

    private enum Keys {
        static let deviceId = "device_id"
        static let minecraftUsername = "minecraft_username"
        static let queuedUsername = "queued_minecraft_username"
        static let queuedUsernameDay = "queued_minecraft_username_day"
        static let lastUsernameChangeDay = "last_username_change_day"
        static let selectedServers = "selected_servers"
        static let serverKeys = "server_keys"
        static let inviteCodes = "invite_codes_by_server"
        static let onboardingComplete = "onboarding_complete"
        static let autoSyncEnabled = "auto_sync_enabled"
        static let backgroundSyncEnabled = "background_sync_enabled"
        static let backgroundSyncIntervalMinutes = "background_sync_interval_minutes"
        static let lastKnownSteps = "last_known_steps"
        static let lastKnownStepsDay = "last_known_steps_day"
        static let syncLog = "sync_log"
        static let trackedMilestones = "tracked_milestones_by_server"
        static let adminPushEnabled = "admin_push_by_server"
        static let notifyTiers = "notify_tiers_by_server"
        static let milestoneNotified = "milestone_notified_by_key"
        static let pushToken = "apns_push_token"
    }
    private static func loadSelectedServers() -> [String] {
        guard let data = UserDefaults.standard.data(forKey: Keys.selectedServers) else { return [] }
        return (try? JSONDecoder().decode([String].self, from: data)) ?? []
    }

    private static func saveSelectedServers(_ value: [String]) {
        if let data = try? JSONEncoder().encode(value) {
            UserDefaults.standard.set(data, forKey: Keys.selectedServers)
        }
    }

    static func loadServerKeys() -> [String: String] {
        guard let data = UserDefaults.standard.data(forKey: Keys.serverKeys) else { return [:] }
        return (try? JSONDecoder().decode([String: String].self, from: data)) ?? [:]
    }

    private static func saveServerKeys(_ value: [String: String]) {
        if let data = try? JSONEncoder().encode(value) {
            UserDefaults.standard.set(data, forKey: Keys.serverKeys)
        }
    }

    private static func loadInviteCodes() -> [String: String] {
        guard let data = UserDefaults.standard.data(forKey: Keys.inviteCodes) else { return [:] }
        return (try? JSONDecoder().decode([String: String].self, from: data)) ?? [:]
    }

    private static func saveInviteCodes(_ value: [String: String]) {
        if let data = try? JSONEncoder().encode(value) {
            UserDefaults.standard.set(data, forKey: Keys.inviteCodes)
        }
    }

    private static func loadLastKnownSteps() -> (steps: Int?, dayKey: String?) {
        let day = UserDefaults.standard.string(forKey: Keys.lastKnownStepsDay)
        let stored = UserDefaults.standard.object(forKey: Keys.lastKnownSteps) as? Int
        guard day == dayKey() else { return (nil, day) }
        return (stored, day)
    }

    private static func saveLastKnownSteps(steps: Int?, dayKey: String) {
        UserDefaults.standard.set(dayKey, forKey: Keys.lastKnownStepsDay)
        if let steps {
            UserDefaults.standard.set(steps, forKey: Keys.lastKnownSteps)
        } else {
            UserDefaults.standard.removeObject(forKey: Keys.lastKnownSteps)
        }
    }

    private static func loadSyncLog() -> [SyncLogEntry] {
        guard let data = UserDefaults.standard.data(forKey: Keys.syncLog) else { return [] }
        return (try? JSONDecoder().decode([SyncLogEntry].self, from: data)) ?? []
    }

    private static func saveSyncLog(_ value: [SyncLogEntry]) {
        if let data = try? JSONEncoder().encode(value) {
            UserDefaults.standard.set(data, forKey: Keys.syncLog)
        }
    }

    private static func dayKey() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = TimeZone(identifier: "America/Chicago")
        return formatter.string(from: Date())
    }

    private func makeServerKey(username: String, server: String) -> String {
        "\(username)|\(server)"
    }

    private func migrateServerKeys(from oldUsername: String, to newUsername: String) {
        guard oldUsername != newUsername else { return }
        var migrated: [String: String] = serverKeysByUserServer
        let prefix = "\(oldUsername)|"
        let keysToMove = serverKeysByUserServer.keys.filter { $0.hasPrefix(prefix) }
        for oldKey in keysToMove {
            guard let value = serverKeysByUserServer[oldKey] else { continue }
            let server = String(oldKey.dropFirst(prefix.count))
            let newKey = makeServerKey(username: newUsername, server: server)
            migrated.removeValue(forKey: oldKey)
            migrated[newKey] = value
        }
        serverKeysByUserServer = migrated
    }


    private static func loadTrackedMilestones() -> [String: Int] {
        guard let data = UserDefaults.standard.data(forKey: Keys.trackedMilestones) else { return [:] }
        return (try? JSONDecoder().decode([String: Int].self, from: data)) ?? [:]
    }

    private static func saveTrackedMilestones(_ value: [String: Int]) {
        if let data = try? JSONEncoder().encode(value) {
            UserDefaults.standard.set(data, forKey: Keys.trackedMilestones)
        }
    }

    private static func loadAdminPushEnabled() -> [String: Bool] {
        guard let data = UserDefaults.standard.data(forKey: Keys.adminPushEnabled) else { return [:] }
        return (try? JSONDecoder().decode([String: Bool].self, from: data)) ?? [:]
    }

    private static func saveAdminPushEnabled(_ value: [String: Bool]) {
        if let data = try? JSONEncoder().encode(value) {
            UserDefaults.standard.set(data, forKey: Keys.adminPushEnabled)
        }
    }

    private static func loadNotifyTiers() -> [String: [Int]] {
        guard let data = UserDefaults.standard.data(forKey: Keys.notifyTiers) else { return [:] }
        return (try? JSONDecoder().decode([String: [Int]].self, from: data)) ?? [:]
    }

    private static func saveNotifyTiers(_ value: [String: [Int]]) {
        if let data = try? JSONEncoder().encode(value) {
            UserDefaults.standard.set(data, forKey: Keys.notifyTiers)
        }
    }

    private static func loadMilestoneNotified() -> [String: String] {
        guard let data = UserDefaults.standard.data(forKey: Keys.milestoneNotified) else { return [:] }
        return (try? JSONDecoder().decode([String: String].self, from: data)) ?? [:]
    }

    private static func saveMilestoneNotified(_ value: [String: String]) {
        if let data = try? JSONEncoder().encode(value) {
            UserDefaults.standard.set(data, forKey: Keys.milestoneNotified)
        }
    }
}

