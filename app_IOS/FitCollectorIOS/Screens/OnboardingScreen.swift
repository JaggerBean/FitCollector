import SwiftUI

struct OnboardingScreen: View {
    @EnvironmentObject private var appState: AppState

    @State private var step = 1
    @State private var username = ""
    @State private var inviteCode = ""
    @State private var availableServers: [ServerInfo] = []
    @State private var selectedServers: Set<String> = []
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var healthKitAuthorized = false
    @State private var notificationsAuthorized = false
    @State private var usernameValid: Bool?
    @State private var isValidating = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    VStack(spacing: 6) {
                        Text("Welcome to StepCraft")
                            .font(.system(size: 26, weight: .black))
                            .foregroundColor(AppColors.healthGreen)
                        Text("Complete these steps to start earning rewards.")
                            .foregroundColor(.secondary)
                    }

                    StepProgressBar(step: step, total: 4)

                    Group {
                        switch step {
                        case 1:
                            healthKitStep
                        case 2:
                            notificationStep
                        case 3:
                            usernameStep
                        case 4:
                            serversStep
                        default:
                            EmptyView()
                        }
                    }

                    if let errorMessage {
                        Text(errorMessage)
                            .foregroundColor(.red)
                            .multilineTextAlignment(.center)
                            .font(.subheadline)
                    }
                }
                .padding(16)
            }
            .navigationTitle("Onboarding")
            .task {
                username = appState.minecraftUsername
                selectedServers = Set(appState.selectedServers)
                await loadServers(inviteCode: nil)
            }
        }
    }

    private var healthKitStep: some View {
        StepCard(title: "Health Permissions", subtitle: "Allow StepCraft to read your daily step count from HealthKit.", icon: "heart.fill") {
            Button(healthKitAuthorized ? "Authorized" : "Authorize HealthKit") {
                Task {
                    do {
                        try await HealthKitManager.shared.requestAuthorization()
                        healthKitAuthorized = true
                        step = 2
                    } catch {
                        errorMessage = error.localizedDescription
                    }
                }
            }
            .buttonStyle(PrimaryButtonStyle())
        }
    }

    private var notificationStep: some View {
        StepCard(title: "Notifications", subtitle: "Get notified about rewards and admin updates.", icon: "bell.fill") {
            Button(notificationsAuthorized ? "Enabled" : "Enable Notifications") {
                Task {
                    do {
                        notificationsAuthorized = try await NotificationManager.shared.requestAuthorization()
                    } catch {
                        notificationsAuthorized = false
                    }
                    step = 3
                }
            }
            .buttonStyle(PrimaryButtonStyle())

            Button("Skip") { step = 3 }
                .buttonStyle(SecondaryButtonStyle())
        }
    }

    private var usernameStep: some View {
        StepCard(title: "Minecraft Username", subtitle: "We’ll use this to sync rewards with your server.", icon: "person.fill") {
            TextField("Username", text: $username)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .padding(12)
                .background(Color(.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

            if isValidating {
                ProgressView("Validating…")
            } else if let usernameValid {
                Text(usernameValid ? "Username looks valid" : "Username not found")
                    .foregroundColor(usernameValid ? AppColors.healthGreen : .red)
            }

            Button("Continue") { Task { await validateUsernameAndContinue() } }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
    }

    private var serversStep: some View {
        StepCard(title: "Servers", subtitle: "Select the servers you want to sync.", icon: "server.rack") {
            if availableServers.isEmpty {
                Text("No servers loaded yet.")
                    .foregroundColor(.secondary)
            }

            VStack(alignment: .leading, spacing: 8) {
                ForEach(availableServers, id: \.serverName) { server in
                    Toggle(server.serverName, isOn: Binding(
                        get: { selectedServers.contains(server.serverName) },
                        set: { enabled in
                            if enabled { selectedServers.insert(server.serverName) }
                            else { selectedServers.remove(server.serverName) }
                        }
                    ))
                }
            }

            HStack(spacing: 8) {
                TextField("Invite code", text: $inviteCode)
                    .padding(12)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                Button("Add") { Task { await addInviteCode() } }
                    .buttonStyle(SecondaryButtonStyle())
                    .disabled(inviteCode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }

            Button(isLoading ? "Finishing…" : "Finish Setup") { Task { await finishSetup() } }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(isLoading || selectedServers.isEmpty)
        }
    }

    private func validateUsernameAndContinue() async {
        errorMessage = nil
        isValidating = true
        let valid = await ApiClient.shared.validateMinecraftUsername(username)
        usernameValid = valid
        isValidating = false
        if valid {
            step = 4
        } else {
            errorMessage = "Minecraft username not found."
        }
    }

    private func loadServers(inviteCode: String?) async {
        do {
            let response = try await ApiClient.shared.getAvailableServers(inviteCode: inviteCode)
            availableServers = response.servers.sorted { $0.serverName.lowercased() < $1.serverName.lowercased() }
        } catch {
            errorMessage = error.localizedDescription
        }
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

    private func finishSetup() async {
        isLoading = true
        errorMessage = nil

        let trimmed = username.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            errorMessage = "Enter a username."
            isLoading = false
            return
        }

        appState.minecraftUsername = trimmed
        appState.selectedServers = Array(selectedServers).sorted()

        for server in appState.selectedServers {
            do {
                let resp = try await ApiClient.shared.recoverKey(
                    deviceId: appState.deviceId,
                    minecraftUsername: appState.minecraftUsername,
                    serverName: server
                )
                appState.setServerKey(server: server, apiKey: resp.playerApiKey)
            } catch {
                do {
                    let resp = try await ApiClient.shared.register(
                        deviceId: appState.deviceId,
                        minecraftUsername: appState.minecraftUsername,
                        serverName: server,
                        inviteCode: appState.inviteCodesByServer[server]
                    )
                    appState.setServerKey(server: server, apiKey: resp.playerApiKey)
                } catch {
                    errorMessage = error.localizedDescription
                }
            }
        }
        appState.onboardingComplete = appState.isConfigured()
        isLoading = false
    }
}

private struct StepProgressBar: View {
    let step: Int
    let total: Int

    var body: some View {
        HStack(spacing: 8) {
            ForEach(1...total, id: \.self) { index in
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(index <= step ? AppColors.healthGreen : Color.gray.opacity(0.25))
                    .frame(height: 6)
            }
        }
        .padding(.horizontal, 8)
    }
}

private struct StepCard<Content: View>: View {
    let title: String
    let subtitle: String
    let icon: String
    let content: Content

    init(title: String, subtitle: String, icon: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.subtitle = subtitle
        self.icon = icon
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Image(systemName: icon)
                    .foregroundColor(AppColors.healthGreen)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.headline)
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
            }
            content
        }
        .cardSurface()
    }
}
