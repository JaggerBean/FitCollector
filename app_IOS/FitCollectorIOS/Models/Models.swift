import Foundation

struct RewardTier: Codable, Hashable, Identifiable {
    var id: String { "\(minSteps)-\(label)" }
    let minSteps: Int
    let label: String
    let rewards: [String]

    enum CodingKeys: String, CodingKey {
        case minSteps = "min_steps"
        case label
        case rewards
    }
}

struct RewardsResponse: Codable {
    let serverName: String
    let tiers: [RewardTier]
    let isDefault: Bool

    enum CodingKeys: String, CodingKey {
        case serverName = "server_name"
        case tiers
        case isDefault = "is_default"
    }
}

struct PlayerApiKeyResponse: Codable {
    let playerApiKey: String
    let minecraftUsername: String
    let deviceId: String
    let serverName: String
    let message: String

    enum CodingKeys: String, CodingKey {
        case playerApiKey = "player_api_key"
        case minecraftUsername = "minecraft_username"
        case deviceId = "device_id"
        case serverName = "server_name"
        case message
    }
}

struct PushNotificationResponse: Codable {
    let message: String?
    let serverName: String?
    let scheduledAt: String?
    let id: Int?

    enum CodingKeys: String, CodingKey {
        case message
        case serverName = "server_name"
        case scheduledAt = "scheduled_at"
        case id
    }
}

struct ServerInfo: Codable, Hashable {
    let serverName: String
    let createdAt: String

    enum CodingKeys: String, CodingKey {
        case serverName = "server_name"
        case createdAt = "created_at"
    }
}

struct AvailableServersResponse: Codable {
    let totalServers: Int
    let servers: [ServerInfo]

    enum CodingKeys: String, CodingKey {
        case totalServers = "total_servers"
        case servers
    }
}
