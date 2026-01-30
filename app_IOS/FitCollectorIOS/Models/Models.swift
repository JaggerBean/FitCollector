import Foundation

struct SyncLogEntry: Codable, Hashable, Identifiable {
    let id: UUID
    let timestamp: String
    let steps: Int
    let source: String
    let success: Bool
    let message: String

    init(timestamp: String, steps: Int, source: String, success: Bool, message: String, id: UUID = UUID()) {
        self.id = id
        self.timestamp = timestamp
        self.steps = steps
        self.source = source
        self.success = success
        self.message = message
    }
}

struct IngestPayload: Codable {
    let minecraftUsername: String
    let deviceId: String
    let stepsToday: Int
    let playerApiKey: String
    let day: String
    let source: String
    let timestamp: String

    enum CodingKeys: String, CodingKey {
        case minecraftUsername = "minecraft_username"
        case deviceId = "device_id"
        case stepsToday = "steps_today"
        case playerApiKey = "player_api_key"
        case day
        case source
        case timestamp
    }
}

struct IngestResponse: Codable {
    let ok: Bool
    let deviceId: String
    let day: String
    let stepsToday: Int

    enum CodingKeys: String, CodingKey {
        case ok
        case deviceId = "device_id"
        case day
        case stepsToday = "steps_today"
    }
}

struct ClaimStatusResponse: Codable {
    let claimed: Bool
    let claimedAt: String?
    let stepsClaimed: Int?
    let day: String?
    let minSteps: Int?

    enum CodingKeys: String, CodingKey {
        case claimed
        case claimedAt = "claimed_at"
        case stepsClaimed = "steps_claimed"
        case day
        case minSteps = "min_steps"
    }
}

struct StepsYesterdayResponse: Codable {
    let minecraftUsername: String
    let serverName: String
    let stepsYesterday: Int
    let day: String

    enum CodingKeys: String, CodingKey {
        case minecraftUsername = "minecraft_username"
        case serverName = "server_name"
        case stepsYesterday = "steps_yesterday"
        case day
    }
}

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
