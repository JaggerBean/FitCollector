import SwiftUI
import UIKit

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
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(AppColors.healthGreen)
                        Text("Complete these steps to start earning rewards.")
                            .font(.subheadline)
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
                .padding(24)
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
        VStack(spacing: 16) {
            StepIcon(systemName: "heart.fill")
            Text("Step 1: HealthKit Permissions")
                .font(.headline)
            Text("Allow StepCraft to read your daily step count from HealthKit.")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

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
            .buttonStyle(PillPrimaryButton())
        }
    }

    private var notificationStep: some View {
        VStack(spacing: 16) {
            StepIcon(systemName: "bell.fill")
            Text("Step 2: Notifications")
                .font(.headline)
            Text("Enable notifications for rewards and admin updates.")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

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
            .buttonStyle(PillPrimaryButton())

            Button("Skip") { step = 3 }
                .buttonStyle(PillSecondaryButton())
        }
    }

    private var usernameStep: some View {
        VStack(spacing: 16) {
            StepIcon(systemName: "person.fill")
            Text("Step 3: Minecraft Username")
                .font(.headline)
            Text("We’ll use this to sync rewards with your server.")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

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
                .buttonStyle(PillPrimaryButton())
                .disabled(username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
    }

    private var serversStep: some View {
        VStack(spacing: 16) {
            StepIcon(systemName: "server.rack")
            Text("Step 4: Servers")
                .font(.headline)
            Text("Select the servers you want to sync.")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

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
                    .buttonStyle(PillSecondaryButton())
                    .disabled(inviteCode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }

            Button(isLoading ? "Finishing…" : "Finish Setup") { Task { await finishSetup() } }
                .buttonStyle(PillPrimaryButton())
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
                Capsule()
                    .fill(index <= step ? AppColors.healthGreen : Color.gray.opacity(0.25))
                    .frame(height: 4)
            }
        }
        .padding(.horizontal, 8)
    }
}

private struct StepIcon: View {
    let systemName: String

    var body: some View {
        Image(systemName: systemName)
            .font(.system(size: 34, weight: .bold))
            .foregroundColor(AppColors.healthGreen)
            .padding(.bottom, 4)
    }
}

private struct PillPrimaryButton: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(AppColors.healthGreen.opacity(configuration.isPressed ? 0.9 : 1))
            .foregroundColor(.white)
            .clipShape(Capsule())
    }
}

private struct PillSecondaryButton: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(Color.clear)
            .overlay(
                Capsule().stroke(Color.gray.opacity(0.5), lineWidth: 1)
            )
            .foregroundColor(.primary)
    }
}
