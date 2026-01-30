import SwiftUI
import AVFoundation

struct OnboardingScreen: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.colorScheme) private var colorScheme

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
    @State private var isAuthorizingHealthKit = false
    @State private var isRequestingNotifications = false
    @State private var showPublicServers = false
    @State private var showPrivateServer = false
    @State private var serverSearch = ""
    @State private var privateInviteCode = ""
    @State private var privateInviteError: String?
    @State private var isAddingPrivate = false
    @State private var didLoad = false

    var body: some View {
        NavigationStack {
            GeometryReader { geo in
                ScrollView {
                    VStack(spacing: 20) {
                        Spacer(minLength: 0)

                        VStack(spacing: 6) {
                            Text("Welcome to StepCraft")
                                .font(.system(size: 24, weight: .bold))
                                .foregroundColor(AppColors.healthGreen)
                            Text("Complete these steps to start earning rewards.")
                                .font(.subheadline)
                                .foregroundColor(secondaryTextColor)
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

                        Spacer(minLength: 0)
                    }
                    .padding(24)
                    .frame(minHeight: geo.size.height)
                }
                .background(baseBackground)
                .task {
                    guard !didLoad else { return }
                    didLoad = true
                    await MainActor.run {
                        username = appState.minecraftUsername
                        selectedServers = Set(appState.selectedServers)
                    }
                    await loadServers(inviteCode: nil)
                }
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
                .foregroundColor(secondaryTextColor)
                .multilineTextAlignment(.center)

            Button(healthKitAuthorized ? "Authorized" : (isAuthorizingHealthKit ? "Authorizing…" : "Authorize HealthKit")) {
                Task {
                    guard !isAuthorizingHealthKit else { return }
                    isAuthorizingHealthKit = true
                    do {
                        try await HealthKitManager.shared.requestAuthorization()
                        healthKitAuthorized = true
                        step = 2
                    } catch {
                        errorMessage = error.localizedDescription
                    }
                    isAuthorizingHealthKit = false
                }
            }
            .buttonStyle(PillPrimaryButton())
            .disabled(isAuthorizingHealthKit)

            if isAuthorizingHealthKit {
                ProgressView()
            }
        }
    }

    private var notificationStep: some View {
        VStack(spacing: 16) {
            StepIcon(systemName: "bell.fill")
            Text("Step 2: Notifications")
                .font(.headline)
            Text("Enable notifications for rewards and admin updates.")
                .font(.subheadline)
                .foregroundColor(secondaryTextColor)
                .multilineTextAlignment(.center)

            Button(notificationsAuthorized ? "Enabled" : "Enable Notifications") {
                Task {
                    guard !isRequestingNotifications else { return }
                    isRequestingNotifications = true
                    do {
                        notificationsAuthorized = try await NotificationManager.shared.requestAuthorization()
                    } catch {
                        notificationsAuthorized = false
                    }
                    step = 3
                    isRequestingNotifications = false
                }
            }
            .buttonStyle(PillPrimaryButton())
            .disabled(isRequestingNotifications)

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
                .foregroundColor(secondaryTextColor)
                .multilineTextAlignment(.center)

            TextField("Minecraft Username", text: $username)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .padding(12)
                .background(inputBackground)
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
                .foregroundColor(secondaryTextColor)
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
                .foregroundColor(secondaryTextColor)
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
                    .foregroundColor(secondaryTextColor)

                if selectedServers.isEmpty {
                    Text("No servers selected.")
                        .font(.subheadline)
                        .foregroundColor(secondaryTextColor)
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
            PrivateServerSheet(
                inviteCode: $privateInviteCode,
                errorMessage: $privateInviteError,
                isLoading: $isAddingPrivate,
                onAdd: {
                    Task {
                        inviteCode = privateInviteCode
                        let success = await addInviteCode()
                        if success {
                            privateInviteCode = ""
                            showPrivateServer = false
                        } else {
                            showPrivateServer = true
                        }
                    }
                },
                onClose: {
                    showPrivateServer = false
                }
            )
        }
        .interactiveDismissDisabled(isAddingPrivate)
    }

    private func validateUsernameAndContinue() async {
        await MainActor.run {
            errorMessage = nil
            isValidating = true
        }
        let valid = await ApiClient.shared.validateMinecraftUsername(username)
        await MainActor.run {
            usernameValid = valid
            isValidating = false
            if valid {
                pendingUsername = username
                step = 4
            } else {
                errorMessage = "Minecraft username not found."
            }
        }
    }

    private func loadServers(inviteCode: String?) async {
        do {
            let response = try await ApiClient.shared.getAvailableServers(inviteCode: inviteCode)
            let sorted = response.servers.sorted { $0.serverName.lowercased() < $1.serverName.lowercased() }
            await MainActor.run {
                availableServers = sorted
            }
        } catch {
            await MainActor.run {
                errorMessage = error.localizedDescription
            }
        }
    }

    private func addInviteCode() async -> Bool {
        await MainActor.run {
            privateInviteError = nil
            isAddingPrivate = true
        }
        let code = inviteCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !code.isEmpty else {
            await MainActor.run {
                privateInviteError = "Enter an invite code."
                isAddingPrivate = false
            }
            return false
        }
        do {
            let response = try await ApiClient.shared.getAvailableServers(inviteCode: code)
            let existing = Set(availableServers.map { $0.serverName })
            let newServers = response.servers.filter { !existing.contains($0.serverName) }
            if newServers.isEmpty {
                await MainActor.run {
                    privateInviteError = "No servers found for that invite code."
                    isAddingPrivate = false
                }
                return false
            }

            await MainActor.run {
                availableServers.append(contentsOf: newServers)
                for server in newServers {
                    appState.setInviteCode(server: server.serverName, code: code)
                    selectedServers.insert(server.serverName)
                }
                inviteCode = ""
                isAddingPrivate = false
            }
            return true
        } catch {
            await MainActor.run {
                privateInviteError = error.localizedDescription
                isAddingPrivate = false
            }
            return false
        }
    }

    private func finishSetup() async {
        await MainActor.run {
            isLoading = true
            errorMessage = nil
        }

        let trimmed = username.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            await MainActor.run {
                errorMessage = "Enter a username."
                isLoading = false
            }
            return
        }

        await MainActor.run {
            appState.minecraftUsername = trimmed
            appState.selectedServers = Array(selectedServers).sorted()
        }

        for server in appState.selectedServers {
            do {
                let resp = try await ApiClient.shared.register(
                    deviceId: appState.deviceId,
                    minecraftUsername: appState.minecraftUsername,
                    serverName: server,
                    inviteCode: appState.inviteCodesByServer[server]
                )
                await MainActor.run {
                    appState.setServerKey(server: server, apiKey: resp.playerApiKey)
                }
            } catch {
                if let apiError = error as? ApiClient.APIError,
                   apiError.message.lowercased().contains("device already registered") {
                    do {
                        let recovery = try await ApiClient.shared.recoverKey(
                            deviceId: appState.deviceId,
                            minecraftUsername: appState.minecraftUsername,
                            serverName: server
                        )
                        await MainActor.run {
                            appState.setServerKey(server: server, apiKey: recovery.playerApiKey)
                        }
                        continue
                    } catch {
                        // fall through to surface error
                    }
                }
                await MainActor.run {
                    errorMessage = error.localizedDescription
                }
            }
        }
        await MainActor.run {
            appState.onboardingComplete = appState.isConfigured()
            isLoading = false
        }
    }

    private func avatarURL(for name: String) -> URL? {
        let cleaned = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty else { return nil }
        let encoded = cleaned.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? cleaned
        return URL(string: "https://minotar.net/armor/bust/\(encoded)/128")
    }

    private var baseBackground: Color {
        colorScheme == .dark ? AppColors.darkBackground : Color(.systemBackground)
    }

    private var inputBackground: Color {
        colorScheme == .dark ? AppColors.darkSurface : Color.white
    }

    private var secondaryTextColor: Color {
        colorScheme == .dark ? Color(hex: 0xFFB0B0B0) : .secondary
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
    @Binding var errorMessage: String?
    @Binding var isLoading: Bool
    var onAdd: () -> Void
    var onClose: () -> Void
    @State private var showScanner = false
    @State private var scannerError: String?

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text("Add private server")
                    .font(.headline)

                TextField("Invite Code", text: $inviteCode)
                    .padding(12)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

                if let errorMessage {
                    Text(errorMessage)
                        .foregroundColor(.red)
                        .font(.footnote)
                }

                HStack(spacing: 12) {
                    Button("Scan QR") { showScanner = true }
                        .buttonStyle(PillSecondaryButton())
                    Button(isLoading ? "Adding…" : "Add") { onAdd() }
                        .buttonStyle(PillPrimaryButton())
                        .disabled(isLoading)
                }

                if let scannerError {
                    Text(scannerError)
                        .foregroundColor(.red)
                        .font(.footnote)
                }

                Spacer()

                Button("Close") { onClose() }
                    .foregroundColor(AppColors.healthGreen)
            }
            .padding(20)
        }
        .fullScreenCover(isPresented: $showScanner) {
            NavigationStack {
                ZStack {
                    QRCodeScannerView(
                        onFound: { raw in
                            inviteCode = extractInviteCode(from: raw)
                            showScanner = false
                        },
                        onError: { message in
                            scannerError = message
                            showScanner = false
                        }
                    )
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
