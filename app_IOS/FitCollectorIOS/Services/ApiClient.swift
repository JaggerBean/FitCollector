import Foundation

final class ApiClient {
    static let shared = ApiClient()

    var baseURL: URL = URL(string: "https://api.stepcraft.org/")!
    var globalApiKey: String = "fc_live_7f3c9b2a7b2c4a2f9c8d1d0d9b3a"
    var mojangBaseURL: URL = URL(string: "https://api.mojang.com/users/profiles/minecraft/")!

    private init() {}

    struct APIError: LocalizedError {
        let message: String
        var errorDescription: String? { message }
    }

    func getRewards(deviceId: String, serverName: String, playerApiKey: String) async throws -> RewardsResponse {
        var components = URLComponents(url: baseURL.appendingPathComponent("v1/players/rewards"), resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "device_id", value: deviceId),
            URLQueryItem(name: "server_name", value: serverName),
            URLQueryItem(name: "player_api_key", value: playerApiKey)
        ]
        var request = URLRequest(url: components.url!)
        request.setValue(globalApiKey, forHTTPHeaderField: "X-API-Key")
        let (data, _) = try await URLSession.shared.data(for: request)
        return try JSONDecoder().decode(RewardsResponse.self, from: data)
    }

    func getClaimStatus(minecraftUsername: String, serverName: String, minSteps: Int, day: String?) async throws -> ClaimStatusResponse {
        var components = URLComponents(url: baseURL.appendingPathComponent("v1/players/claim-status/\(minecraftUsername)"), resolvingAgainstBaseURL: false)!
        var items = [
            URLQueryItem(name: "server_name", value: serverName),
            URLQueryItem(name: "min_steps", value: String(minSteps))
        ]
        if let day, !day.isEmpty {
            items.append(URLQueryItem(name: "day", value: day))
        }
        components.queryItems = items

        var request = URLRequest(url: components.url!)
        request.setValue(globalApiKey, forHTTPHeaderField: "X-API-Key")
        let (data, _) = try await URLSession.shared.data(for: request)
        return try JSONDecoder().decode(ClaimStatusResponse.self, from: data)
    }

    func getStepsToday(minecraftUsername: String, playerApiKey: String) async throws -> StepsTodayResponse {
        var components = URLComponents(url: baseURL.appendingPathComponent("v1/players/steps-today"), resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "minecraft_username", value: minecraftUsername),
            URLQueryItem(name: "player_api_key", value: playerApiKey)
        ]
        var request = URLRequest(url: components.url!)
        request.setValue(globalApiKey, forHTTPHeaderField: "X-API-Key")
        let (data, _) = try await URLSession.shared.data(for: request)
        return try JSONDecoder().decode(StepsTodayResponse.self, from: data)
    }

    // Backward-compatible wrapper for older call sites.
    func getStepsYesterday(minecraftUsername: String, playerApiKey: String) async throws -> StepsTodayResponse {
        try await getStepsToday(minecraftUsername: minecraftUsername, playerApiKey: playerApiKey)
    }

    func getClaimStatusList(deviceId: String, playerApiKey: String) async throws -> ClaimStatusListResponse {
        var components = URLComponents(url: baseURL.appendingPathComponent("v1/players/claim-status-list"), resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "device_id", value: deviceId),
            URLQueryItem(name: "player_api_key", value: playerApiKey)
        ]
        var request = URLRequest(url: components.url!)
        request.setValue(globalApiKey, forHTTPHeaderField: "X-API-Key")
        let (data, _) = try await URLSession.shared.data(for: request)
        return try JSONDecoder().decode(ClaimStatusListResponse.self, from: data)
    }

    func register(deviceId: String, minecraftUsername: String, serverName: String, inviteCode: String?) async throws -> PlayerApiKeyResponse {
        let url = baseURL.appendingPathComponent("v1/players/register")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(globalApiKey, forHTTPHeaderField: "X-API-Key")

        let body: [String: Any] = [
            "device_id": deviceId,
            "minecraft_username": minecraftUsername,
            "server_name": serverName,
            "invite_code": inviteCode as Any
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await URLSession.shared.data(for: request)
        let http = response as? HTTPURLResponse
        if let http, !(200...299).contains(http.statusCode) {
            let detail = decodeErrorDetail(data) ?? "Server error (status \(http.statusCode))."
            throw APIError(message: detail)
        }
        guard !data.isEmpty else {
            throw APIError(message: "Server returned empty response.")
        }
        return try JSONDecoder().decode(PlayerApiKeyResponse.self, from: data)
    }

    func recoverKey(deviceId: String, minecraftUsername: String, serverName: String) async throws -> PlayerApiKeyResponse {
        let url = baseURL.appendingPathComponent("v1/players/recover-key")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(globalApiKey, forHTTPHeaderField: "X-API-Key")

        let body: [String: Any] = [
            "device_id": deviceId,
            "minecraft_username": minecraftUsername,
            "server_name": serverName
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await URLSession.shared.data(for: request)
        let http = response as? HTTPURLResponse
        if let http, !(200...299).contains(http.statusCode) {
            let detail = decodeErrorDetail(data) ?? "Server error (status \(http.statusCode))."
            throw APIError(message: detail)
        }
        guard !data.isEmpty else {
            throw APIError(message: "Server returned empty response.")
        }
        return try JSONDecoder().decode(PlayerApiKeyResponse.self, from: data)
    }

    func registerPushToken(deviceId: String, playerApiKey: String, token: String, isSandbox: Bool) async throws {
        let url = baseURL.appendingPathComponent("v1/players/push/register-device")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(globalApiKey, forHTTPHeaderField: "X-API-Key")

        let body: [String: Any] = [
            "device_id": deviceId,
            "player_api_key": playerApiKey,
            "apns_token": token,
            "sandbox": isSandbox,
            "platform": "ios"
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await URLSession.shared.data(for: request)
        let http = response as? HTTPURLResponse
        if let http, !(200...299).contains(http.statusCode) {
            let detail = decodeErrorDetail(data) ?? "Server error (status \(http.statusCode))."
            throw APIError(message: detail)
        }
    }

    func unregisterPushToken(deviceId: String, playerApiKey: String, token: String? = nil) async throws {
        let url = baseURL.appendingPathComponent("v1/players/push/unregister-device")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(globalApiKey, forHTTPHeaderField: "X-API-Key")

        var body: [String: Any] = [
            "device_id": deviceId,
            "player_api_key": playerApiKey,
            "platform": "ios"
        ]
        if let token, !token.isEmpty {
            body["apns_token"] = token
        }
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await URLSession.shared.data(for: request)
        let http = response as? HTTPURLResponse
        if let http, !(200...299).contains(http.statusCode) {
            let detail = decodeErrorDetail(data) ?? "Server error (status \(http.statusCode))."
            throw APIError(message: detail)
        }
    }

    private func decodeErrorDetail(_ data: Data) -> String? {
        guard !data.isEmpty else { return nil }
        if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            if let detail = json["detail"] as? String { return detail }
            if let message = json["message"] as? String { return message }
            if let error = json["error"] as? String { return error }
        }
        if let text = String(data: data, encoding: .utf8), !text.isEmpty {
            return text
        }
        return nil
    }

    func getAvailableServers(inviteCode: String?) async throws -> AvailableServersResponse {
        var components = URLComponents(url: baseURL.appendingPathComponent("v1/servers/available"), resolvingAgainstBaseURL: false)!
        var queryItems: [URLQueryItem] = [
            URLQueryItem(name: "ts", value: String(Int(Date().timeIntervalSince1970)))
        ]
        if let inviteCode, !inviteCode.isEmpty {
            queryItems.append(URLQueryItem(name: "invite_code", value: inviteCode))
        }
        components.queryItems = queryItems
        var request = URLRequest(url: components.url!)
        request.setValue(globalApiKey, forHTTPHeaderField: "X-API-Key")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.cachePolicy = .reloadIgnoringLocalCacheData
        let config = URLSessionConfiguration.ephemeral
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        let session = URLSession(configuration: config)
        let (data, _) = try await session.data(for: request)
        return try JSONDecoder().decode(AvailableServersResponse.self, from: data)
    }

    func getDeviceUsername(deviceId: String, serverName: String) async throws -> DeviceUsernameResponse {
        var components = URLComponents(url: baseURL.appendingPathComponent("v1/players/device-username"), resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "device_id", value: deviceId),
            URLQueryItem(name: "server_name", value: serverName)
        ]
        var request = URLRequest(url: components.url!)
        request.setValue(globalApiKey, forHTTPHeaderField: "X-API-Key")
        let (data, response) = try await URLSession.shared.data(for: request)
        let http = response as? HTTPURLResponse
        if let http, !(200...299).contains(http.statusCode) {
            let detail = decodeErrorDetail(data) ?? "Server error (status \(http.statusCode))."
            throw APIError(message: detail)
        }
        guard !data.isEmpty else {
            throw APIError(message: "Server returned empty response.")
        }
        return try JSONDecoder().decode(DeviceUsernameResponse.self, from: data)
    }

    func ingest(_ payload: IngestPayload) async throws -> IngestResponse {
        let url = baseURL.appendingPathComponent("v1/ingest")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(globalApiKey, forHTTPHeaderField: "X-API-Key")
        request.httpBody = try JSONEncoder().encode(payload)

        let (data, _) = try await URLSession.shared.data(for: request)
        return try JSONDecoder().decode(IngestResponse.self, from: data)
    }

    func validateMinecraftUsername(_ username: String) async -> Bool {
        let trimmed = username.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }
        let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? trimmed
        let url = mojangBaseURL.appendingPathComponent(encoded)
        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            guard let http = response as? HTTPURLResponse, (200...399).contains(http.statusCode) else { return false }
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            return json?["id"] != nil
        } catch {
            return false
        }
    }
}
