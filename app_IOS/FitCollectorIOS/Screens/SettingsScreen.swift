import SwiftUI
import UIKit
import AVFoundation
import UserNotifications

struct SettingsScreen: View {
    @EnvironmentObject private var appState: AppState
    @State private var rewardTiersByServer: [String: [RewardTier]] = [:]
    @State private var availableServers: [ServerInfo] = []
    @State private var inviteCode = ""
    @State private var isRefreshingServers = false
    @State private var isSaving = false
    @State private var usernameDraft = ""
    @State private var selectedServers: Set<String> = []
    @State private var showPrivateJoinOptions = false
    @State private var showInviteCodeEntry = false
    @State private var showPublicServerPicker = false
    @State private var showManageJoinedServers = false
    @State private var showScanner = false
    @State private var scannerSheetID = UUID()
    @State private var showTrackDialog = false
    @State private var showNotifyDialog = false
    @State private var selectedTrackServer: String?
    @State private var selectedNotifyServer: String?
    @State private var notificationsAuthorized = false
    @State private var usernameStatusMessage: (String, Bool)?
    @State private var serverStatusMessage: (String, Bool)?
    private let noServersSelectedMessage = "No servers selected. Sync is disabled until you add one."
    @State private var scannerError: String?
    @State private var timeUntilReset: String = ""
    @State private var isAutoSavingServers = false
    @State private var knownSelectedServers: Set<String> = []

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Settings")
                        .font(.largeTitle)
                        .fontWeight(.bold)

                    NavigationLink(destination: ActivityLogScreen()) {
                        HStack(spacing: 12) {
                            Image(systemName: "list.bullet")
                            Text("RECENT ACTIVITY LOG")
                                .fontWeight(.bold)
                            Spacer()
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .padding(.horizontal, 16)
                        .background(Color(hex: 0xFF64B5F6))
                        .foregroundColor(.black)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }

                    SectionHeader(title: "Account Settings")
                    SettingsCard {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Minecraft Username")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            TextField("Minecraft Username", text: $usernameDraft)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .padding(12)
                                .background(Color(.secondarySystemBackground))
                                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                        }
                        let changingUsername = usernameDraft.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                            != appState.minecraftUsername.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                        if changingUsername {
                            Button("Queue for Tomorrow") {
                                Task { await saveProfile(forceQueue: true) }
                            }
                            .buttonStyle(PillPrimaryButton())
                            .disabled(isSaving)
                        }

                        if !appState.queuedUsername.isEmpty {
                            HStack(spacing: 12) {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Next up: \(appState.queuedUsername)")
                                        .font(.caption)
                                        .fontWeight(.semibold)
                                    Text("Applying in \(timeUntilReset)")
                                        .font(.caption2)
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                                Button("Cancel") {
                                    appState.clearQueuedUsername()
                                }
                                .font(.caption)
                                .foregroundColor(.red)
                            }
                            .padding(10)
                            .background(Color(.secondarySystemBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        }

                        if let usernameStatusMessage {
                            StatusBanner(message: usernameStatusMessage.0, isSuccess: usernameStatusMessage.1)
                        }
                    }

                    SectionHeader(title: "Servers")
                    SettingsCard {
                        Text("Join or manage your server connections.")
                            .font(.caption)
                            .foregroundColor(.secondary)

                        Button("Add Private Server") {
                            showPrivateJoinOptions = true
                        }
                        .buttonStyle(PillPrimaryButton())

                        Button("All Available Servers") {
                            showPublicServerPicker = true
                        }
                        .buttonStyle(PillPrimaryButton())

                        Button(selectedServers.isEmpty ? "Manage Joined Servers" : "Manage Joined Servers (\(selectedServers.count))") {
                            showManageJoinedServers = true
                        }
                        .buttonStyle(PillSecondaryButton())

                        if isRefreshingServers {
                            ProgressView("Refreshing available servers...")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }

                        if let serverStatusMessage {
                            StatusBanner(message: serverStatusMessage.0, isSuccess: serverStatusMessage.1)
                        }

                        if let scannerError {
                            Text(scannerError)
                                .foregroundColor(.red)
                                .font(.caption)
                        }
                    }

                    SectionHeader(title: "Sync & Permissions")
                    SettingsCard {
                        SettingsToggleRow(
                            title: "Auto-Sync on Open",
                            subtitle: "Sync steps immediately when you open the app.",
                            isOn: $appState.autoSyncEnabled
                        )

                        Divider().padding(.vertical, 6)

                        SettingsToggleRow(
                            title: "Background Sync",
                            subtitle: backgroundSyncDescription,
                            isOn: $appState.backgroundSyncEnabled
                        )

                        if appState.backgroundSyncEnabled {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Sync Frequency")
                                    .font(.subheadline)
                                    .fontWeight(.semibold)
                                Text("Every \(appState.backgroundSyncIntervalMinutes) minutes")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                Slider(
                                    value: Binding(
                                        get: { Double(appState.backgroundSyncIntervalMinutes) },
                                        set: { appState.backgroundSyncIntervalMinutes = Int($0) }
                                    ),
                                    in: 15...120,
                                    step: 15
                                )
                            }
                        }

                    }

                    SectionHeader(title: "Notifications")
                    SettingsCard {
                        Text("Enable notifications")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                        Text("Get alerts about rewards and server updates.")
                            .font(.caption)
                            .foregroundColor(.secondary)

                        if !notificationsAuthorized {
                            Button("Enable notifications") {
                                Task { await requestNotificationPermissions() }
                            }
                            .buttonStyle(PillSecondaryButton())
                        }

                        Button("Manage server notifications") {
                            showNotifyDialog = true
                        }
                        .buttonStyle(PillPrimaryButton())
                    }

                    SectionHeader(title: "Milestones")
                    SettingsCard {
                        Text("Track milestones")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                        Text("Select one milestone per server to track on your dashboard.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Button("Track milestones") { showTrackDialog = true }
                            .buttonStyle(PillPrimaryButton())
                    }
                }
                .padding(16)
            }
            .task {
                usernameDraft = appState.minecraftUsername
                selectedServers = Set(appState.selectedServers)
                knownSelectedServers = selectedServers
                timeUntilReset = timeUntilNextReset()
                await loadServers()
                await loadRewards()
                await refreshNotificationStatus()
                updateServerSelectionStatus()
            }
            .onChange(of: selectedServers) { _ in
                let removedServers = knownSelectedServers.subtracting(selectedServers)
                knownSelectedServers = selectedServers
                Task {
                    if !removedServers.isEmpty {
                        await unregisterRemovedServers(removedServers)
                    }
                    await loadRewards()
                    await autoSaveServers()
                    updateServerSelectionStatus()
                }
            }
        }
        .confirmationDialog(
            "Add Private Server",
            isPresented: $showPrivateJoinOptions,
            titleVisibility: .visible
        ) {
            Button("Enter Invite Code") { showInviteCodeEntry = true }
            Button("Scan QR Code") {
                scannerSheetID = UUID()
                showScanner = true
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Choose how to join a private server.")
        }
        .sheet(isPresented: $showInviteCodeEntry) {
            PrivateInviteEntrySheet(
                inviteCode: $inviteCode,
                onAdd: { Task { await addInviteCode() } }
            )
        }
        .sheet(isPresented: $showPublicServerPicker) {
            PublicServerPickerSheet(
                availableServers: availableServers,
                privateServerNames: Set(appState.inviteCodesByServer.keys),
                selectedServers: $selectedServers
            )
        }
        .sheet(isPresented: $showManageJoinedServers) {
            ManageJoinedServersSheet(
                selectedServers: $selectedServers,
                privateServerNames: Set(appState.inviteCodesByServer.keys)
            )
        }
        .sheet(isPresented: $showNotifyDialog) {
            ServerNotificationsSheet(
                rewardTiersByServer: rewardTiersByServer,
                selectedServer: $selectedNotifyServer,
                appState: appState
            )
            .presentationDetents([.fraction(0.85)])
        }
        .sheet(isPresented: $showTrackDialog) {
            TrackMilestonesSheet(
                rewardTiersByServer: rewardTiersByServer,
                selectedServer: $selectedTrackServer,
                appState: appState
            )
            .presentationDetents([.fraction(0.85)])
        }
        .fullScreenCover(isPresented: $showScanner) {
            NavigationStack {
                ZStack {
                    QRCodeScannerView(
                        onFound: { raw in
                            inviteCode = extractInviteCode(from: raw)
                            showScanner = false
                            Task { await addInviteCode() }
                        },
                        onError: { message in
                            scannerError = message
                            showScanner = false
                        }
                    )
                    .id(scannerSheetID)
                    .ignoresSafeArea()

                    VStack {
                        Text("Scan invite QR")
                            .font(.headline)
                            .padding(8)
                            .background(.ultraThinMaterial)
                            .clipShape(Capsule())
                        Spacer()
                    }
                    .padding(.top, 20)
                }
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button("Close") { showScanner = false }
                    }
                }
            }
        }
    }

    private var hasChanges: Bool {
        let trimmed = usernameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed != appState.minecraftUsername || selectedServers != Set(appState.selectedServers)
    }

    private var backgroundSyncDescription: String {
        if appState.backgroundSyncEnabled {
            return "Periodic sync every \(appState.backgroundSyncIntervalMinutes) minutes while app is closed."
        }
        return "Disabled. No periodic sync while app is closed."
    }

    private func loadRewards() async {
        guard appState.isConfigured() else { return }
        var merged: [String: [RewardTier]] = [:]
        for server in selectedServers.sorted() {
            guard let key = appState.serverKey(for: server) else { continue }
            do {
                let response = try await ApiClient.shared.getRewards(
                    deviceId: appState.deviceId,
                    serverName: server,
                    playerApiKey: key
                )
                merged[server] = response.tiers
            } catch {
                serverStatusMessage = (error.localizedDescription, false)
            }
        }
        rewardTiersByServer = merged
    }

    private func requestNotificationPermissions() async {
        do {
            notificationsAuthorized = try await NotificationManager.shared.requestAuthorization()
        } catch {
            notificationsAuthorized = false
        }
    }

    private func refreshNotificationStatus() async {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        notificationsAuthorized = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
    }

    private func loadServers() async {
        isRefreshingServers = true
        do {
            let response = try await ApiClient.shared.getAvailableServers(inviteCode: nil)
            var merged = response.servers

            let storedInvites = appState.inviteCodesByServer
            let uniqueCodes = Set(storedInvites.values)
            for code in uniqueCodes {
                do {
                    let privateResp = try await ApiClient.shared.getAvailableServers(inviteCode: code)
                    let existing = Set(merged.map { $0.serverName })
                    let newServers = privateResp.servers.filter { !existing.contains($0.serverName) }
                    merged.append(contentsOf: newServers)
                } catch {
                    continue
                }
            }

            availableServers = merged.sorted { $0.serverName.lowercased() < $1.serverName.lowercased() }
        } catch {
            serverStatusMessage = (error.localizedDescription, false)
        }
        isRefreshingServers = false
    }

    private func addInviteCode() async {
        let code = inviteCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !code.isEmpty else { return }
        let trimmedUsername = usernameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedUsername.isEmpty else {
            usernameStatusMessage = ("Set your Minecraft username before adding private servers.", false)
            return
        }
        do {
            let response = try await ApiClient.shared.getAvailableServers(inviteCode: code)
            if response.servers.isEmpty {
                serverStatusMessage = ("Invite code not found.", false)
                return
            }

            let existing = Set(availableServers.map { $0.serverName })
            let uniqueNew = response.servers.filter { !existing.contains($0.serverName) }
            availableServers.append(contentsOf: uniqueNew)

            response.servers.forEach { server in
                appState.setInviteCode(server: server.serverName, code: code)
                selectedServers.insert(server.serverName)
            }

            inviteCode = ""
            let serverDisplayName = response.servers.count == 1 ? response.servers[0].serverName : "multiple servers"
            serverStatusMessage = ("You registered to \(serverDisplayName)", true)
            await autoSaveServers()
        } catch {
            serverStatusMessage = ("Could not add invite code: \(error.localizedDescription)", false)
        }
    }

    private func saveProfile(forceQueue: Bool = false) async {
        isSaving = true
        let trimmed = usernameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            usernameStatusMessage = ("Enter a username.", false)
            isSaving = false
            return
        }

        let changingUsername = trimmed.lowercased() != appState.minecraftUsername.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if changingUsername {
            let isValid = await ApiClient.shared.validateMinecraftUsername(trimmed)
            if !isValid {
                usernameStatusMessage = ("Minecraft username not found.", false)
                isSaving = false
                return
            }
        }
        if changingUsername && (forceQueue || !appState.canChangeUsernameToday()) {
            appState.queueUsername(trimmed)
            appState.selectedServers = selectedServers.sorted()
            usernameStatusMessage = ("Username queued for tomorrow!", true)
            isSaving = false
            return
        }

        appState.minecraftUsername = trimmed
        appState.selectedServers = selectedServers.sorted()
        if changingUsername {
            appState.markUsernameChangedToday()
            appState.clearQueuedUsername()
        }

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
                    serverStatusMessage = (error.localizedDescription, false)
                }
            }
        }
        appState.onboardingComplete = true
        isSaving = false
        serverStatusMessage = ("Settings saved & registered!", true)
    }

    private func autoSaveServers() async {
        guard !isAutoSavingServers else { return }
        isAutoSavingServers = true
        appState.selectedServers = selectedServers.sorted()
        let trimmed = appState.minecraftUsername.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !selectedServers.isEmpty else {
            isAutoSavingServers = false
            return
        }
        for server in appState.selectedServers {
            if appState.serverKey(for: server) != nil { continue }
            do {
                let resp = try await ApiClient.shared.recoverKey(
                    deviceId: appState.deviceId,
                    minecraftUsername: trimmed,
                    serverName: server
                )
                appState.setServerKey(server: server, apiKey: resp.playerApiKey)
            } catch {
                do {
                    let invite = appState.inviteCodesByServer[server]
                    let resp = try await ApiClient.shared.register(
                        deviceId: appState.deviceId,
                        minecraftUsername: trimmed,
                        serverName: server,
                        inviteCode: invite
                    )
                    appState.setServerKey(server: server, apiKey: resp.playerApiKey)
                } catch {
                    serverStatusMessage = (error.localizedDescription, false)
                }
            }
        }
        isAutoSavingServers = false
    }

    private func unregisterRemovedServers(_ removedServers: Set<String>) async {
        let pushToken = AppState.loadPushToken()
        for server in removedServers.sorted() {
            let key = appState.serverKey(for: server)
            if let key {
                do {
                    try await ApiClient.shared.unregisterPushToken(
                        deviceId: appState.deviceId,
                        playerApiKey: key,
                        token: pushToken
                    )
                } catch {
                    serverStatusMessage = ("Removed \(server), but push unregister failed: \(error.localizedDescription)", false)
                }
            }

            // Prevent removed servers from being re-used for push registration later.
            appState.removeServerKey(server: server)
            appState.setInviteCode(server: server, code: nil)
            appState.setTrackedMilestone(server: server, minSteps: nil)
            appState.adminPushEnabledByServer.removeValue(forKey: server)
            appState.notifyTiersByServer.removeValue(forKey: server)
        }
    }

    private func updateServerSelectionStatus() {
        if selectedServers.isEmpty {
            serverStatusMessage = (noServersSelectedMessage, false)
        } else if serverStatusMessage?.0 == noServersSelectedMessage {
            serverStatusMessage = nil
        }
    }

    private func timeUntilNextReset() -> String {
        let central = TimeZone(identifier: "America/Chicago") ?? .current
        var calendar = Calendar.current
        calendar.timeZone = central
        let now = Date()
        let startOfTomorrow = calendar.startOfDay(for: now).addingTimeInterval(24 * 60 * 60)
        let diff = Int(startOfTomorrow.timeIntervalSince(now))
        let hours = max(0, diff / 3600)
        let minutes = max(0, (diff % 3600) / 60)
        let seconds = max(0, diff % 60)
        return String(format: "%02d:%02d:%02d", hours, minutes, seconds)
    }

}

private struct SectionHeader: View {
    let title: String

    var body: some View {
        Text(title)
            .font(.headline)
            .fontWeight(.bold)
            .foregroundColor(.primary)
    }
}

private struct SettingsCard<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            content
        }
        .cardSurface()
    }
}

private struct StatusBanner: View {
    let message: String
    let isSuccess: Bool

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: isSuccess ? "checkmark.circle.fill" : "xmark.octagon.fill")
                .foregroundColor(isSuccess ? AppColors.healthGreen : .red)
            Text(message)
                .foregroundColor(isSuccess ? AppColors.healthGreen : .red)
                .font(.caption)
                .fontWeight(.semibold)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(isSuccess ? AppColors.healthLightGreen : Color.red.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

private struct SettingsToggleRow: View {
    let title: String
    let subtitle: String
    @Binding var isOn: Bool

    var body: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.subheadline)
                    .fontWeight(.semibold)
                Text(subtitle)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            Spacer()
            Toggle("", isOn: $isOn)
                .labelsHidden()
        }
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
            .overlay(Capsule().stroke(Color.gray.opacity(0.4), lineWidth: 1))
            .foregroundColor(.primary)
    }
}

private struct PrivateInviteEntrySheet: View {
    @Binding var inviteCode: String
    let onAdd: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                Text("Enter a private server invite code.")
                    .font(.caption)
                    .foregroundColor(.secondary)

                TextField("Invite Code", text: $inviteCode)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .padding(12)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

                Button("Add Private Server") {
                    onAdd()
                    dismiss()
                }
                .buttonStyle(PillPrimaryButton())
                .disabled(inviteCode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

                Spacer(minLength: 0)
            }
            .padding(20)
            .navigationTitle("Private Server")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }
}

private struct PublicServerPickerSheet: View {
    let availableServers: [ServerInfo]
    let privateServerNames: Set<String>
    @Binding var selectedServers: Set<String>
    @Environment(\.dismiss) private var dismiss
    @State private var searchText = ""

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                Text("Select servers to join.")
                    .font(.caption)
                    .foregroundColor(.secondary)

                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(.secondary)
                    TextField("Search Servers", text: $searchText)
                }
                .padding(12)
                .background(Color(.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        if privateServers.isEmpty && publicServers.isEmpty {
                            Text("No servers available.")
                                .font(.caption)
                                .foregroundColor(.secondary)
                                .padding(.vertical, 8)
                        } else {
                            if !privateServers.isEmpty {
                                Text("Private servers")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .padding(.top, 2)
                                ForEach(privateServers, id: \.serverName) { server in
                                    ServerRow(server: server.serverName, selected: selectedServers.contains(server.serverName)) {
                                        toggleServer(server.serverName)
                                    }
                                }
                            }

                            if !publicServers.isEmpty {
                                Text("Public servers")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .padding(.top, privateServers.isEmpty ? 2 : 8)
                                ForEach(publicServers, id: \.serverName) { server in
                                    ServerRow(server: server.serverName, selected: selectedServers.contains(server.serverName)) {
                                        toggleServer(server.serverName)
                                    }
                                }
                            }
                        }
                    }
                }

                Button("Done") { dismiss() }
                    .buttonStyle(PillPrimaryButton())
            }
            .padding(20)
            .navigationTitle("All Available Servers")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private var filteredServers: [ServerInfo] {
        if searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return availableServers
        }
        return availableServers.filter { $0.serverName.lowercased().contains(searchText.lowercased()) }
    }

    private var publicServers: [ServerInfo] {
        filteredServers
            .filter { !privateServerNames.contains($0.serverName) }
            .sorted { $0.serverName.lowercased() < $1.serverName.lowercased() }
    }

    private var privateServers: [ServerInfo] {
        filteredServers
            .filter { privateServerNames.contains($0.serverName) }
            .sorted { $0.serverName.lowercased() < $1.serverName.lowercased() }
    }

    private func toggleServer(_ server: String) {
        if selectedServers.contains(server) {
            selectedServers.remove(server)
        } else {
            selectedServers.insert(server)
        }
    }
}

private struct ManageJoinedServersSheet: View {
    @Binding var selectedServers: Set<String>
    let privateServerNames: Set<String>
    @Environment(\.dismiss) private var dismiss

    private var privateSelected: [String] {
        selectedServers.filter { privateServerNames.contains($0) }.sorted()
    }

    private var publicSelected: [String] {
        selectedServers.filter { !privateServerNames.contains($0) }.sorted()
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                Text("Remove servers you no longer want to sync with.")
                    .font(.caption)
                    .foregroundColor(.secondary)

                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        if selectedServers.isEmpty {
                            Text("You have no joined servers.")
                                .font(.caption)
                                .foregroundColor(.secondary)
                                .padding(.vertical, 8)
                        } else {
                            if !privateSelected.isEmpty {
                                Text("Private servers")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .padding(.top, 2)
                                ForEach(privateSelected, id: \.self) { server in
                                    HStack(spacing: 10) {
                                        Text(server)
                                        Spacer()
                                        Button("Remove", role: .destructive) {
                                            selectedServers.remove(server)
                                        }
                                        .font(.caption)
                                    }
                                }
                            }

                            if !publicSelected.isEmpty {
                                Text("Public servers")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .padding(.top, 6)
                                ForEach(publicSelected, id: \.self) { server in
                                    HStack(spacing: 10) {
                                        Text(server)
                                        Spacer()
                                        Button("Remove", role: .destructive) {
                                            selectedServers.remove(server)
                                        }
                                        .font(.caption)
                                    }
                                }
                            }
                        }
                    }
                }

                Button("Done") { dismiss() }
                    .buttonStyle(PillPrimaryButton())
            }
            .padding(20)
            .navigationTitle("Manage Joined Servers")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

private struct ServerSelectorSheet: View {
    let availableServers: [ServerInfo]
    let privateServerNames: Set<String>
    @Binding var selectedServers: Set<String>
    @Environment(\.dismiss) private var dismiss
    @State private var searchText = ""

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text("Select From Available Servers")
                    .font(.headline)

                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(.secondary)
                    TextField("Search Servers", text: $searchText)
                }
                .padding(12)
                .background(Color(.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        if !privateServers.isEmpty {
                            Text("Private servers")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            ForEach(privateServers, id: \.serverName) { server in
                                ServerRow(server: server.serverName, selected: selectedServers.contains(server.serverName)) {
                                    toggleServer(server.serverName)
                                }
                            }
                        }

                        if !publicServers.isEmpty {
                            Text("Public servers")
                                .font(.caption)
                                .foregroundColor(.secondary)
                                .padding(.top, 8)
                            ForEach(publicServers, id: \.serverName) { server in
                                ServerRow(server: server.serverName, selected: selectedServers.contains(server.serverName)) {
                                    toggleServer(server.serverName)
                                }
                            }
                        }
                    }
                }

                Button("Done") { dismiss() }
                    .buttonStyle(PillPrimaryButton())
            }
            .padding(20)
        }
    }

    private var filteredServers: [ServerInfo] {
        if searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return availableServers
        }
        return availableServers.filter { $0.serverName.lowercased().contains(searchText.lowercased()) }
    }

    private var privateServers: [ServerInfo] {
        filteredServers
            .filter { privateServerNames.contains($0.serverName) }
            .sorted { $0.serverName.lowercased() < $1.serverName.lowercased() }
    }

    private var publicServers: [ServerInfo] {
        filteredServers
            .filter { !privateServerNames.contains($0.serverName) }
            .sorted { $0.serverName.lowercased() < $1.serverName.lowercased() }
    }

    private func toggleServer(_ server: String) {
        if selectedServers.contains(server) {
            selectedServers.remove(server)
        } else {
            selectedServers.insert(server)
        }
    }
}

private struct ServerRow: View {
    let server: String
    let selected: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            HStack(spacing: 10) {
                Image(systemName: selected ? "checkmark.square" : "square")
                Text(server)
                Spacer()
            }
            .foregroundColor(.primary)
        }
        .buttonStyle(.plain)
    }
}

private struct ServerNotificationsSheet: View {
    let rewardTiersByServer: [String: [RewardTier]]
    @Binding var selectedServer: String?
    @ObservedObject var appState: AppState
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                Text("Server notifications")
                    .font(.title3)
                    .fontWeight(.bold)
                Text("Select a server to manage admin updates and milestone alerts.")
                    .font(.caption)
                    .foregroundColor(.secondary)

                if servers.isEmpty {
                    Text("No reward tiers found.")
                        .font(.caption)
                        .foregroundColor(.secondary)
                } else {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 10) {
                            ForEach(servers, id: \.self) { server in
                                Button {
                                    selectedServer = server
                                } label: {
                                    HStack(spacing: 10) {
                                        Image(systemName: selectedServer == server ? "checkmark.circle.fill" : "circle")
                                            .foregroundColor(AppColors.healthGreen)
                                        Text(server)
                                        Spacer()
                                    }
                                }
                                .buttonStyle(.plain)
                            }

                            if let server = selectedServer {
                                Divider().padding(.vertical, 6)

                                HStack {
                                    Text("Admin updates")
                                        .font(.subheadline)
                                        .fontWeight(.semibold)
                                    Spacer()
                                    Toggle("", isOn: Binding(
                                        get: { appState.isAdminPushEnabled(server: server) },
                                        set: { appState.setAdminPushEnabled(server: server, enabled: $0) }
                                    ))
                                    .labelsHidden()
                                }

                                if let tiers = rewardTiersByServer[server], !tiers.isEmpty {
                                    Text("Milestone alerts")
                                        .font(.subheadline)
                                        .fontWeight(.semibold)
                                        .padding(.top, 6)
                                    ForEach(tiers.sorted { $0.minSteps < $1.minSteps }) { tier in
                                        let enabled = appState.notificationTiers(server: server).contains(tier.minSteps)
                                        Button {
                                            appState.setNotificationTier(server: server, minSteps: tier.minSteps, enabled: !enabled)
                                        } label: {
                                            HStack(spacing: 10) {
                                                Image(systemName: enabled ? "checkmark.square" : "square")
                                                Text("\(tier.label) · \(tier.minSteps) steps")
                                                Spacer()
                                            }
                                            .font(.caption)
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                            }
                        }
                    }
                }

                Button("Done") { dismiss() }
                    .buttonStyle(PillPrimaryButton())
            }
            .padding(20)
            .onAppear {
                if selectedServer == nil {
                    selectedServer = servers.first
                }
            }
        }
    }

    private var servers: [String] {
        rewardTiersByServer.keys.sorted()
    }
}

private struct TrackMilestonesSheet: View {
    let rewardTiersByServer: [String: [RewardTier]]
    @Binding var selectedServer: String?
    @ObservedObject var appState: AppState
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                Text("Track milestones")
                    .font(.title3)
                    .fontWeight(.bold)
                Text("Select a server, then choose one milestone to track.")
                    .font(.caption)
                    .foregroundColor(.secondary)

                if servers.isEmpty {
                    Text("No reward tiers found.")
                        .font(.caption)
                        .foregroundColor(.secondary)
                } else {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 10) {
                            ForEach(servers, id: \.self) { server in
                                Button {
                                    selectedServer = server
                                } label: {
                                    HStack(spacing: 10) {
                                        Image(systemName: selectedServer == server ? "checkmark.circle.fill" : "circle")
                                            .foregroundColor(AppColors.healthGreen)
                                        Text(server)
                                        Spacer()
                                    }
                                }
                                .buttonStyle(.plain)
                            }

                            if let server = selectedServer {
                                Divider().padding(.vertical, 6)
                                Text("Milestones")
                                    .font(.subheadline)
                                    .fontWeight(.semibold)
                                if let tiers = rewardTiersByServer[server], !tiers.isEmpty {
                                    ForEach(tiers.sorted { $0.minSteps < $1.minSteps }) { tier in
                                        let checked = appState.trackedMilestonesByServer[server] == tier.minSteps
                                        Button {
                                            appState.setTrackedMilestone(server: server, minSteps: tier.minSteps)
                                        } label: {
                                            HStack(spacing: 10) {
                                                Image(systemName: checked ? "checkmark.circle.fill" : "circle")
                                                    .foregroundColor(AppColors.healthGreen)
                                                Text("\(tier.label) · \(tier.minSteps) steps")
                                                Spacer()
                                            }
                                            .font(.caption)
                                        }
                                        .buttonStyle(.plain)
                                    }
                                    Button("Clear selection") {
                                        appState.setTrackedMilestone(server: server, minSteps: nil)
                                    }
                                    .font(.caption)
                                    .foregroundColor(AppColors.healthGreen)
                                }
                            }
                        }
                    }
                }

                Button("Done") { dismiss() }
                    .buttonStyle(PillPrimaryButton())
            }
            .padding(20)
            .onAppear {
                if selectedServer == nil {
                    selectedServer = servers.first
                }
            }
        }
    }

    private var servers: [String] {
        rewardTiersByServer.keys.sorted()
    }
}

private struct QRCodeScannerView: UIViewRepresentable {
    var onFound: (String) -> Void
    var onError: (String) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        let session = AVCaptureSession()

        let status = AVCaptureDevice.authorizationStatus(for: .video)
        if status == .notDetermined {
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async {
                    if granted {
                        context.coordinator.configureSession(session, in: view)
                    } else {
                        onError("Camera permission denied")
                    }
                }
            }
            return view
        }

        if status == .denied || status == .restricted {
            DispatchQueue.main.async {
                onError("Camera permission denied")
            }
            return view
        }

        context.coordinator.configureSession(session, in: view)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.previewLayer?.frame = uiView.bounds
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        coordinator.session?.stopRunning()
    }

    final class Coordinator: NSObject, AVCaptureMetadataOutputObjectsDelegate {
        let parent: QRCodeScannerView
        var session: AVCaptureSession?
        var previewLayer: AVCaptureVideoPreviewLayer?
        private var didScan = false

        init(_ parent: QRCodeScannerView) {
            self.parent = parent
        }

        func configureSession(_ session: AVCaptureSession, in view: UIView) {
            guard let device = AVCaptureDevice.default(for: .video) else {
                DispatchQueue.main.async {
                    self.parent.onError("Camera not available")
                }
                return
            }

            do {
                let input = try AVCaptureDeviceInput(device: device)
                if session.canAddInput(input) {
                    session.addInput(input)
                }

                let output = AVCaptureMetadataOutput()
                if session.canAddOutput(output) {
                    session.addOutput(output)
                    output.setMetadataObjectsDelegate(self, queue: .main)
                    output.metadataObjectTypes = [.qr]
                }

                let preview = AVCaptureVideoPreviewLayer(session: session)
                preview.videoGravity = .resizeAspectFill
                preview.frame = view.bounds
                view.layer.addSublayer(preview)

                self.session = session
                self.previewLayer = preview
                session.startRunning()
            } catch {
                DispatchQueue.main.async {
                    self.parent.onError(error.localizedDescription)
                }
            }
        }

        func metadataOutput(
            _ output: AVCaptureMetadataOutput,
            didOutput metadataObjects: [AVMetadataObject],
            from connection: AVCaptureConnection
        ) {
            guard !didScan,
                  let first = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
                  first.type == .qr,
                  let value = first.stringValue else { return }
            didScan = true
            parent.onFound(value)
        }
    }
}

private func extractInviteCode(from raw: String) -> String {
    let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty else { return trimmed }
    if let components = URLComponents(string: trimmed) {
        let key = ["code", "invite", "invite_code"].first { name in
            components.queryItems?.contains { $0.name == name } == true
        }
        if let key, let value = components.queryItems?.first(where: { $0.name == key })?.value, !value.isEmpty {
            return value
        }
    }
    return trimmed
}
