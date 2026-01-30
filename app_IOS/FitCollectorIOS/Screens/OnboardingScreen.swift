import SwiftUI

struct OnboardingScreen: View {
    @EnvironmentObject private var appState: AppState

    @State private var step = 1
    @State private var username = ""
    @State private var pendingUsername = ""
    @State private var inviteCode = ""
    @State private var availableServers: [ServerInfo] = []
    @State private var selectedServers: Set<String> = []
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var healthKitAuthorized = false
    @State private var notificationsAuthorized = false
    @State private var usernameValid: Bool?
    @State private var isValidating = false
    @State private var showPublicServers = false
    @State private var showPrivateServer = false
    @State private var serverSearch = ""
    @State private var privateInviteCode = ""

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

                    StepProgressBar(step: step, total: 5)

                    Group {
                        switch step {
                        case 1:
                            healthKitStep
                        case 2:
                            notificationStep
                        case 3:
                            usernameEntryStep
                        case 4:
                            confirmUsernameStep
                        case 5:
                            serversSelectionStep
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

    private var usernameEntryStep: some View {
        VStack(spacing: 16) {
            StepIcon(systemName: "person.fill")
            Text("Step 3: Minecraft Username")
                .font(.headline)
            Text("Enter your exact Minecraft username.")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            TextField("Minecraft Username", text: $username)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .padding(12)
                .background(Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .stroke(Color.gray.opacity(0.4), lineWidth: 1)
                )

            if isValidating {
                ProgressView("Validating…")
            }

            Button("Next") { Task { await validateUsernameAndContinue() } }
                .buttonStyle(PillPrimaryButton())
                .disabled(username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .opacity(username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.4 : 1)
        }
    }

    private var confirmUsernameStep: some View {
        VStack(spacing: 16) {
            StepIcon(systemName: "person.fill")
            Text("Step 4: Confirm Username")
                .font(.headline)
            Text("Is this the correct Minecraft username?")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            if let url = avatarURL(for: pendingUsername) {
                AsyncImage(url: url) { image in
                    image.resizable()
                } placeholder: {
                    Color.gray.opacity(0.2)
                }
                .frame(width: 90, height: 90)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            }

            Text(pendingUsername)
                .font(.title3)
                .fontWeight(.bold)

            HStack(spacing: 12) {
                Button("Back") { step = 3 }
                    .buttonStyle(PillSecondaryButton())
                Button("Confirm") { step = 5 }
                    .buttonStyle(PillPrimaryButton())
            }
        }
    }

    private var serversSelectionStep: some View {
        VStack(spacing: 16) {
            StepIcon(systemName: "server.rack")
            Text("Step 5: Select Servers")
                .font(.headline)
            Text("Choose which servers to sync with.")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            HStack(spacing: 12) {
                Button("Browse public") { showPublicServers = true }
                    .buttonStyle(PillSecondaryButton())
                Button("Add private") { showPrivateServer = true }
                    .buttonStyle(PillSecondaryButton())
            }

            VStack(alignment: .leading, spacing: 10) {
                Text("Selected servers:")
                    .font(.footnote)
                    .foregroundColor(.secondary)

                if selectedServers.isEmpty {
                    Text("No servers selected.")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                } else {
                    ForEach(selectedServers.sorted(), id: \.self) { server in
                        Button {
                            if selectedServers.contains(server) {
                                selectedServers.remove(server)
                            } else {
                                selectedServers.insert(server)
                            }
                        } label: {
                            HStack(spacing: 10) {
                                Image(systemName: "checkmark.square.fill")
                                    .foregroundColor(AppColors.healthGreen)
                                Text(server)
                                    .foregroundColor(.primary)
                                Spacer()
                            }
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Button(isLoading ? "Finishing…" : "Complete setup") { Task { await finishSetup() } }
                .buttonStyle(PillPrimaryButton())
                .disabled(isLoading || selectedServers.isEmpty)
                .opacity(selectedServers.isEmpty ? 0.4 : 1)
        }
        .sheet(isPresented: $showPublicServers) {
            PublicServersSheet(
                servers: availableServers,
                selectedServers: $selectedServers,
                searchText: $serverSearch,
                onDone: { showPublicServers = false }
            )
        }
        .sheet(isPresented: $showPrivateServer) {
            PrivateServerSheet(inviteCode: $privateInviteCode, onAdd: {
                inviteCode = privateInviteCode
                Task { await addInviteCode() }
                privateInviteCode = ""
                showPrivateServer = false
            }, onClose: {
                showPrivateServer = false
            })
        }
    }

    private func validateUsernameAndContinue() async {
        errorMessage = nil
        isValidating = true
        let valid = await ApiClient.shared.validateMinecraftUsername(username)
        usernameValid = valid
        isValidating = false
        if valid {
            pendingUsername = username
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
            if !newServers.isEmpty {
                availableServers.append(contentsOf: newServers)
            }

            if response.servers.isEmpty {
                errorMessage = "No servers found for that invite code."
            } else {
                for server in response.servers {
                    appState.setInviteCode(server: server.serverName, code: code)
                    selectedServers.insert(server.serverName)
                }
            }
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

    private func avatarURL(for name: String) -> URL? {
        let cleaned = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty else { return nil }
        let encoded = cleaned.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? cleaned
        return URL(string: "https://minotar.net/armor/bust/\(encoded)/128")
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

private struct PublicServersSheet: View {
    let servers: [ServerInfo]
    @Binding var selectedServers: Set<String>
    @Binding var searchText: String
    let onDone: () -> Void

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text("Public servers")
                    .font(.headline)

                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(.secondary)
                    TextField("Search Servers", text: $searchText)
                }
                .padding(12)
                .background(Color(.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

                VStack(alignment: .leading, spacing: 12) {
                    ForEach(filteredServers, id: \.serverName) { server in
                        Button {
                            toggleServer(server.serverName)
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: selectedServers.contains(server.serverName) ? "checkmark.square" : "square")
                                    .foregroundColor(.primary)
                                Text(server.serverName)
                                    .foregroundColor(.primary)
                                Spacer()
                            }
                        }
                    }
                }

                Spacer()

                Button("Done") { onDone() }
                    .buttonStyle(PillPrimaryButton())
            }
            .padding(20)
        }
    }

    private var filteredServers: [ServerInfo] {
        if searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return servers
        }
        return servers.filter { $0.serverName.lowercased().contains(searchText.lowercased()) }
    }

    private func toggleServer(_ server: String) {
        if selectedServers.contains(server) {
            selectedServers.remove(server)
        } else {
            selectedServers.insert(server)
        }
    }
}

private struct PrivateServerSheet: View {
    @Binding var inviteCode: String
    var onAdd: () -> Void
    var onClose: () -> Void

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text("Add private server")
                    .font(.headline)

                TextField("Invite Code", text: $inviteCode)
                    .padding(12)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

                HStack(spacing: 12) {
                    Button("Scan QR") { }
                        .buttonStyle(PillSecondaryButton())
                    Button("Add") { onAdd() }
                        .buttonStyle(PillPrimaryButton())
                }

                Spacer()

                Button("Close") { onClose() }
                    .foregroundColor(AppColors.healthGreen)
            }
            .padding(20)
        }
    }
}
