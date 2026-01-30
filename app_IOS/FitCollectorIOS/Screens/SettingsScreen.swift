import SwiftUI

struct SettingsScreen: View {
    @EnvironmentObject private var appState: AppState
    @State private var rewardTiersByServer: [String: [RewardTier]] = [:]
    @State private var errorMessage: String?
    @State private var allowNotifications = false
    @State private var availableServers: [ServerInfo] = []
    @State private var inviteCode = ""
    @State private var isRefreshingServers = false
    @State private var isSaving = false
    @State private var usernameDraft = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    SettingsSection(title: "Account") {
                        TextField("Minecraft Username", text: $usernameDraft)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .padding(12)
                            .background(Color(.secondarySystemBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

                        Toggle("Auto-sync", isOn: $appState.autoSyncEnabled)

                        Button(isSaving ? "Saving…" : "Save Profile") {
                            Task { await saveProfile() }
                        }
                        .buttonStyle(PrimaryButtonStyle())
                        .disabled(isSaving || usernameDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }

                    SettingsSection(title: "Servers") {
                        if isRefreshingServers {
                            ProgressView("Loading servers…")
                        }

                        ForEach(availableServers, id: \.serverName) { server in
                            Toggle(server.serverName, isOn: bindingForServer(server.serverName))
                        }

                        HStack(spacing: 8) {
                            TextField("Invite code", text: $inviteCode)
                                .padding(10)
                                .background(Color(.secondarySystemBackground))
                                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                            Button("Add") { Task { await addInviteCode() } }
                                .buttonStyle(SecondaryButtonStyle())
                                .disabled(inviteCode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        }

                        Button("Refresh Servers") { Task { await loadServers() } }
                            .buttonStyle(SecondaryButtonStyle())
                    }

                    SettingsSection(title: "Milestones") {
                        if rewardTiersByServer.isEmpty {
                            Text("No reward tiers loaded yet")
                                .foregroundColor(.secondary)
                        } else {
                            ForEach(appState.selectedServers, id: \.self) { server in
                                if let tiers = rewardTiersByServer[server] {
                                    Picker("Track for \(server)", selection: trackedTierBinding(server: server)) {
                                        Text("None").tag(0)
                                        ForEach(tiers) { tier in
                                            Text("\(tier.label) – \(tier.minSteps)").tag(tier.minSteps)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SettingsSection(title: "Notifications") {
                        Toggle("Enable admin updates", isOn: adminPushBinding())

                        if !rewardTiersByServer.isEmpty {
                            ForEach(appState.selectedServers, id: \.self) { server in
                                if let tiers = rewardTiersByServer[server] {
                                    ForEach(tiers) { tier in
                                        Toggle(
                                            "\(server): \(tier.label)",
                                            isOn: milestoneNotifyBinding(server: server, minSteps: tier.minSteps)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SettingsSection(title: "Diagnostics") {
                        NavigationLink("Recent Activity Log") {
                            ActivityLogScreen()
                        }
                        NavigationLink("Raw Health Data") {
                            RawHealthDataScreen()
                        }
                    }

                    if let errorMessage {
                        Text(errorMessage)
                            .foregroundColor(.red)
                            .font(.subheadline)
                    }
                }
                .padding(16)
            }
            .navigationTitle("Settings")
            .task {
                usernameDraft = appState.minecraftUsername
                await loadServers()
                await loadRewards()
                await requestNotificationPermissions()
            }
        }
    }

    private func bindingForServer(_ server: String) -> Binding<Bool> {
        return Binding(
            get: { appState.selectedServers.contains(server) },
            set: { enabled in
                if enabled {
                    if !appState.selectedServers.contains(server) {
                        appState.selectedServers.append(server)
                    }
                } else {
                    appState.selectedServers.removeAll { $0 == server }
                }
            }
        )
    }

    private func trackedTierBinding(server: String) -> Binding<Int> {
        let current = appState.trackedMilestonesByServer[server] ?? 0
        return Binding(
            get: { current },
            set: { value in
                if value == 0 {
                    appState.setTrackedMilestone(server: server, minSteps: nil)
                } else {
                    appState.setTrackedMilestone(server: server, minSteps: value)
                }
            }
        )
    }

    private func loadRewards() async {
        guard appState.isConfigured() else { return }
        var merged: [String: [RewardTier]] = [:]
        for server in appState.selectedServers {
            guard let key = appState.serverKey(for: server) else { continue }
            do {
                let response = try await ApiClient.shared.getRewards(
                    deviceId: appState.deviceId,
                    serverName: server,
                    playerApiKey: key
                )
                merged[server] = response.tiers
            } catch {
                errorMessage = error.localizedDescription
            }
        }
        rewardTiersByServer = merged
    }

    private func adminPushBinding() -> Binding<Bool> {
        return Binding(
            get: { appState.selectedServers.allSatisfy { appState.isAdminPushEnabled(server: $0) } },
            set: { value in
                appState.selectedServers.forEach { appState.setAdminPushEnabled(server: $0, enabled: value) }
            }
        )
    }

    private func milestoneNotifyBinding(server: String, minSteps: Int) -> Binding<Bool> {
        return Binding(
            get: { appState.notificationTiers(server: server).contains(minSteps) },
            set: { value in
                appState.setNotificationTier(server: server, minSteps: minSteps, enabled: value)
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

    private func loadServers() async {
        isRefreshingServers = true
        do {
            let response = try await ApiClient.shared.getAvailableServers(inviteCode: nil)
            availableServers = response.servers.sorted { $0.serverName.lowercased() < $1.serverName.lowercased() }
        } catch {
            errorMessage = error.localizedDescription
        }
        isRefreshingServers = false
    }

    private func addInviteCode() async {
        let code = inviteCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !code.isEmpty else { return }
        do {
            let response = try await ApiClient.shared.getAvailableServers(inviteCode: code)
            let existing = Set(availableServers.map { $0.serverName })
            let newServers = response.servers.filter { !existing.contains($0.serverName) }
            availableServers.append(contentsOf: newServers)
            newServers.forEach { appState.setInviteCode(server: $0.serverName, code: code) }
            inviteCode = ""
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func saveProfile() async {
        isSaving = true
        let trimmed = usernameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { isSaving = false; return }

        appState.minecraftUsername = trimmed
        for server in appState.selectedServers {
            do {
                let resp = try await ApiClient.shared.recoverKey(
                    deviceId: appState.deviceId,
                    minecraftUsername: appState.minecraftUsername,
                    serverName: server
                )
                appState.setServerKey(server: server, apiKey: resp.playerApiKey)
            } catch {
                let invite = appState.inviteCodesByServer[server]
                do {
                    let resp = try await ApiClient.shared.register(
                        deviceId: appState.deviceId,
                        minecraftUsername: appState.minecraftUsername,
                        serverName: server,
                        inviteCode: invite
                    )
                    appState.setServerKey(server: server, apiKey: resp.playerApiKey)
                } catch {
                    errorMessage = error.localizedDescription
                }
            }
        }
        appState.onboardingComplete = true
        isSaving = false
    }
}

private struct SettingsSection<Content: View>: View {
    let title: String
    let content: Content

    init(title: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.headline)
                .foregroundColor(AppColors.healthGreen)
            content
        }
        .cardSurface()
    }
}
