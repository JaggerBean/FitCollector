import Foundation

final class ApiClient {
    static let shared = ApiClient()

    var baseURL: URL = URL(string: "https://YOUR_SERVER_HERE")!

    private init() {}

    func getRewards(deviceId: String, serverName: String, playerApiKey: String) async throws -> RewardsResponse {
        var components = URLComponents(url: baseURL.appendingPathComponent("/v1/players/rewards"), resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "device_id", value: deviceId),
            URLQueryItem(name: "server_name", value: serverName),
            URLQueryItem(name: "player_api_key", value: playerApiKey)
        ]
        let (data, _) = try await URLSession.shared.data(from: components.url!)
        return try JSONDecoder().decode(RewardsResponse.self, from: data)
    }

    func getNextPush(minecraftUsername: String, deviceId: String, serverName: String, playerApiKey: String) async throws -> PushNotificationResponse {
        var components = URLComponents(url: baseURL.appendingPathComponent("/v1/players/push/next"), resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "minecraft_username", value: minecraftUsername),
            URLQueryItem(name: "device_id", value: deviceId),
            URLQueryItem(name: "server_name", value: serverName),
            URLQueryItem(name: "player_api_key", value: playerApiKey)
        ]
        let (data, _) = try await URLSession.shared.data(from: components.url!)
        return try JSONDecoder().decode(PushNotificationResponse.self, from: data)
    }

    func register(deviceId: String, minecraftUsername: String, serverName: String, inviteCode: String?) async throws -> PlayerApiKeyResponse {
        let url = baseURL.appendingPathComponent("/v1/players/register")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = [
            "device_id": deviceId,
            "minecraft_username": minecraftUsername,
            "server_name": serverName,
            "invite_code": inviteCode as Any
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, _) = try await URLSession.shared.data(for: request)
        return try JSONDecoder().decode(PlayerApiKeyResponse.self, from: data)
    }

    func recoverKey(deviceId: String, minecraftUsername: String, serverName: String) async throws -> PlayerApiKeyResponse {
        let url = baseURL.appendingPathComponent("/v1/players/recover-key")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = [
            "device_id": deviceId,
            "minecraft_username": minecraftUsername,
            "server_name": serverName
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, _) = try await URLSession.shared.data(for: request)
        return try JSONDecoder().decode(PlayerApiKeyResponse.self, from: data)
    }

    func getAvailableServers(inviteCode: String?) async throws -> AvailableServersResponse {
        var components = URLComponents(url: baseURL.appendingPathComponent("/v1/servers/available"), resolvingAgainstBaseURL: false)!
        if let inviteCode, !inviteCode.isEmpty {
            components.queryItems = [URLQueryItem(name: "invite_code", value: inviteCode)]
        }
        let (data, _) = try await URLSession.shared.data(from: components.url!)
        return try JSONDecoder().decode(AvailableServersResponse.self, from: data)
    }
}
