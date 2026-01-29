import SwiftUI

struct SettingsScreen: View {
    @EnvironmentObject private var appState: AppState
    @State private var rewardTiers: [RewardTier] = []
    @State private var errorMessage: String?
    @State private var allowNotifications = false

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Account")) {
                    Text("Server: \(appState.serverName)")
                    Text("Username: \(appState.minecraftUsername)")
                }

                Section(header: Text("Milestones")) {
                    if rewardTiers.isEmpty {
                        Text("No reward tiers loaded yet")
                            .foregroundColor(.secondary)
                    } else {
                        Picker("Track milestone", selection: trackedTierBinding()) {
                            Text("None").tag(0)
                            ForEach(rewardTiers) { tier in
                                Text("\(tier.label) – \(tier.minSteps)").tag(tier.minSteps)
                            }
                        }
                    }
                }

                Section(header: Text("Notifications")) {
                    Toggle("Enable admin updates", isOn: adminPushBinding())

                    if !rewardTiers.isEmpty {
                        ForEach(rewardTiers) { tier in
                            Toggle(
                                "Milestone: \(tier.label)",
                                isOn: milestoneNotifyBinding(minSteps: tier.minSteps)
                            )
                        }
                    }
                }

                if let errorMessage {
                    Section {
                        Text(errorMessage).foregroundColor(.red)
                    }
                }
            }
            .navigationTitle("Settings")
            .task {
                await loadRewards()
                await requestNotificationPermissions()
            }
        }
    }

    private func trackedTierBinding() -> Binding<Int> {
        let current = appState.trackedMilestonesByServer[appState.serverName] ?? 0
        return Binding(
            get: { current },
            set: { value in
                if value == 0 {
                    appState.setTrackedMilestone(server: appState.serverName, minSteps: nil)
                } else {
                    appState.setTrackedMilestone(server: appState.serverName, minSteps: value)
                }
            }
        )
    }

    private func loadRewards() async {
        guard appState.isConfigured() else { return }
        do {
            let response = try await ApiClient.shared.getRewards(
                deviceId: appState.deviceId,
                serverName: appState.serverName,
                playerApiKey: appState.playerApiKey
            )
            rewardTiers = response.tiers
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func adminPushBinding() -> Binding<Bool> {
        return Binding(
            get: { appState.isAdminPushEnabled(server: appState.serverName) },
            set: { value in
                appState.setAdminPushEnabled(server: appState.serverName, enabled: value)
            }
        )
    }

    private func milestoneNotifyBinding(minSteps: Int) -> Binding<Bool> {
        return Binding(
            get: { appState.notificationTiers(server: appState.serverName).contains(minSteps) },
            set: { value in
                appState.setNotificationTier(server: appState.serverName, minSteps: minSteps, enabled: value)
            }
        )
    }

    private func requestNotificationPermissions() async {
        do {
            allowNotifications = try await NotificationManager.shared.requestAuthorization()
        } catch {
            allowNotifications = false
        }
    }
}
