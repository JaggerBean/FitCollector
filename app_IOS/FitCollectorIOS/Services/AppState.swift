import Foundation
import SwiftUI

final class AppState: ObservableObject {
    @Published var deviceId: String = UserDefaults.standard.string(forKey: Keys.deviceId) ?? UUID().uuidString {
        didSet { UserDefaults.standard.set(deviceId, forKey: Keys.deviceId) }
    }
    @Published var minecraftUsername: String = UserDefaults.standard.string(forKey: Keys.minecraftUsername) ?? "" {
        didSet { UserDefaults.standard.set(minecraftUsername, forKey: Keys.minecraftUsername) }
    }
    @Published var serverName: String = UserDefaults.standard.string(forKey: Keys.serverName) ?? "" {
        didSet { UserDefaults.standard.set(serverName, forKey: Keys.serverName) }
    }
    @Published var playerApiKey: String = UserDefaults.standard.string(forKey: Keys.playerApiKey) ?? "" {
        didSet { UserDefaults.standard.set(playerApiKey, forKey: Keys.playerApiKey) }
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
        !minecraftUsername.isEmpty && !serverName.isEmpty && !playerApiKey.isEmpty
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

    private enum Keys {
        static let deviceId = "device_id"
        static let minecraftUsername = "minecraft_username"
        static let serverName = "server_name"
        static let playerApiKey = "player_api_key"
        static let trackedMilestones = "tracked_milestones_by_server"
        static let adminPushEnabled = "admin_push_by_server"
        static let notifyTiers = "notify_tiers_by_server"
        static let milestoneNotified = "milestone_notified_by_key"
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
