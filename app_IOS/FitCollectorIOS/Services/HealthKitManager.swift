import Foundation

final class HealthKitManager {
    static let shared = HealthKitManager()

    private init() {}

    func requestAuthorization() async throws {
        // TODO: Implement HealthKit permissions on macOS/Xcode.
    }

    func readTodaySteps() async throws -> Int {
        // TODO: Implement HealthKit step query.
        return 0
    }
}
