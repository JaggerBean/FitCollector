import Foundation
import UserNotifications
import UIKit

final class NotificationManager {
    static let shared = NotificationManager()

    private init() {}

    func requestAuthorization() async throws -> Bool {
        try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
    }

    func requestAuthorizationAndRegister() async throws -> Bool {
        let granted = try await requestAuthorization()
        guard granted else { return false }
        await MainActor.run {
            UIApplication.shared.registerForRemoteNotifications()
        }
        return true
    }

    func sendLocalNotification(title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default

        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }
}
