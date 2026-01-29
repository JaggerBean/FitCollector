import SwiftUI

struct OnboardingScreen: View {
    @EnvironmentObject private var appState: AppState
    @State private var serverName = ""
    @State private var username = ""
    @State private var inviteCode = ""
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var availableServers: [ServerInfo] = []
    @State private var showRecover = false
    @State private var recoverStatus: String?

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Server")) {
                    if availableServers.isEmpty {
                        TextField("Server name", text: $serverName)
                    } else {
                        Picker("Server", selection: $serverName) {
                            Text("Select a server").tag("")
                            ForEach(availableServers, id: \.serverName) { server in
                                Text(server.serverName).tag(server.serverName)
                            }
                        }
                    }
                    TextField("Invite code (optional)", text: $inviteCode)
                }

                Section(header: Text("Minecraft")) {
                    TextField("Username", text: $username)
                }

                if let errorMessage {
                    Section {
                        Text(errorMessage).foregroundColor(.red)
                    }
                }

                if let recoverStatus {
                    Section {
                        Text(recoverStatus).foregroundColor(.secondary)
                    }
                }

                Button(isLoading ? "Registering…" : "Register") {
                    Task { await register() }
                }
                .disabled(isLoading || serverName.isEmpty || username.isEmpty)

                Button("Recover API Key") {
                    Task {
                        await recoverKey()
                        showRecover = true
                    }
                }
                .disabled(isLoading || serverName.isEmpty || username.isEmpty)
            }
            .navigationTitle("FitCollector")
            .task {
                await loadServers()
            }
            .alert("Recovered", isPresented: $showRecover, actions: {
                Button("OK") { }
            }, message: {
                Text(recoverStatus ?? "")
            })
        }
    }

    private func register() async {
        isLoading = true
        errorMessage = nil
        do {
            let response = try await ApiClient.shared.register(
                deviceId: appState.deviceId,
                minecraftUsername: username,
                serverName: serverName,
                inviteCode: inviteCode.isEmpty ? nil : inviteCode
            )
            appState.minecraftUsername = response.minecraftUsername
            appState.serverName = response.serverName
            appState.playerApiKey = response.playerApiKey
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    private func loadServers() async {
        do {
            let response = try await ApiClient.shared.getAvailableServers(inviteCode: inviteCode.isEmpty ? nil : inviteCode)
            availableServers = response.servers
        } catch {
            // Keep manual entry if fetch fails.
        }
    }

    private func recoverKey() async {
        isLoading = true
        errorMessage = nil
        do {
            let response = try await ApiClient.shared.recoverKey(
                deviceId: appState.deviceId,
                minecraftUsername: username,
                serverName: serverName
            )
            appState.minecraftUsername = response.minecraftUsername
            appState.serverName = response.serverName
            appState.playerApiKey = response.playerApiKey
            recoverStatus = response.message
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}
