import UIKit
import UserNotifications

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        logApnsEnvironment()
        registerForPushNotifications()
        return true
    }

    private func logApnsEnvironment() {
        guard let profilePath = Bundle.main.path(forResource: "embedded", ofType: "mobileprovision") else {
            print("aps-environment: <embedded.mobileprovision not found>")
            return
        }
        guard let profileData = try? Data(contentsOf: URL(fileURLWithPath: profilePath)),
              let profileText = String(data: profileData, encoding: .isoLatin1) else {
            print("aps-environment: <unable to read mobileprovision>")
            return
        }
        guard let plistStart = profileText.range(of: "<plist"),
              let plistEnd = profileText.range(of: "</plist>") else {
            print("aps-environment: <plist not found>")
            return
        }
        let plistString = String(profileText[plistStart.lowerBound..<plistEnd.upperBound])
        guard let plistData = plistString.data(using: .utf8),
              let plist = try? PropertyListSerialization.propertyList(from: plistData, options: [], format: nil) as? [String: Any],
              let entitlements = plist["Entitlements"] as? [String: Any] else {
            print("aps-environment: <unable to parse plist>")
            return
        }
        if let env = entitlements["aps-environment"] as? String {
            print("aps-environment: \(env)")
        } else {
            print("aps-environment: <not set>")
        }
    }

    func registerForPushNotifications() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            guard granted else { return }
            DispatchQueue.main.async {
                UIApplication.shared.registerForRemoteNotifications()
            }
        }
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let tokenParts = deviceToken.map { data in String(format: "%02.2hhx", data) }
        let token = tokenParts.joined()
        print("Device Token: \(token)")
        AppState.storePushToken(token)
        AppState.registerPushTokenIfPossible()
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("Failed to register: \(error)")
    }

    // Handle foreground notification
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound, .badge])
    }
}
