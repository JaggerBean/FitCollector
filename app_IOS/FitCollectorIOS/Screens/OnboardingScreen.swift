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
            VStack(spacing: 20) {
                Text("Welcome to StepCraft")
                    .font(.largeTitle)
                    .fontWeight(.bold)

                Text("Complete setup to start syncing steps.")
                    .foregroundColor(.secondary)

                ProgressView(value: Double(step), total: 4)
                    .padding(.horizontal)

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
                }
            }
            .padding()
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
            Text("Step 1 · HealthKit")
                .font(.headline)
            Text("Allow StepCraft to read your step count.")
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
            .buttonStyle(.borderedProminent)
        }
    }

    private var notificationStep: some View {
        VStack(spacing: 16) {
            Text("Step 2 · Notifications")
                .font(.headline)
            Text("Enable notifications for rewards and admin updates.")
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
            .buttonStyle(.borderedProminent)

            Button("Skip") {
                step = 3
            }
        }
    }

    private var usernameStep: some View {
        VStack(spacing: 16) {
            Text("Step 3 · Minecraft Username")
                .font(.headline)

            TextField("Username", text: $username)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .textFieldStyle(.roundedBorder)

            if isValidating {
                ProgressView("Validating…")
            } else if let usernameValid {
                Text(usernameValid ? "Username looks valid" : "Username not found")
                    .foregroundColor(usernameValid ? .green : .red)
            }

            Button("Continue") {
                Task {
                    await validateUsernameAndContinue()
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
    }

    private var serversStep: some View {
        VStack(spacing: 16) {
            Text("Step 4 · Servers")
                .font(.headline)

            if availableServers.isEmpty {
                Text("No servers loaded yet.")
                    .foregroundColor(.secondary)
            }

            ScrollView {
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
            }
            .frame(maxHeight: 240)

            HStack {
                TextField("Invite code", text: $inviteCode)
                    .textFieldStyle(.roundedBorder)
                Button("Add") { Task { await addInviteCode() } }
                    .disabled(inviteCode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }

            Button(isLoading ? "Finishing…" : "Finish Setup") {
                Task { await finishSetup() }
            }
            .buttonStyle(.borderedProminent)
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
