import SwiftUI

struct DashboardScreen: View {
    @EnvironmentObject private var appState: AppState
    @StateObject private var syncService = SyncService()
    @State private var rewards: [RewardTier] = []
    @State private var claimStatuses: [String: [ClaimStatusListItem]] = [:]
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var stepsToday: Int = 0
    @State private var timeUntilReset: String = ""

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                StepCraftHeader(username: appState.minecraftUsername)

                if !timeUntilReset.isEmpty {
                    ResetTimerCard(timeRemaining: timeUntilReset)
                }

                if !unclaimedGroups.isEmpty {
                    UnclaimedRewardsBanner(groups: unclaimedGroups)
                }

                if !claimedGroups.isEmpty {
                    ClaimedRewardsBanner(groups: claimedGroups)
                }

                if appState.selectedServers.isEmpty {
                    SyncStatusBanner(
                        message: "No servers selected. Add a server in Settings to enable sync.",
                        isSuccess: false,
                        timestamp: nil
                    )
                }

                ActivityCard(stepsToday: stepsToday, canSync: canSync) {
                    Task { await syncService.syncSteps(appState: appState, manual: true) }
                }

                if let msg = syncService.lastSyncMessage {
                    SyncStatusBanner(
                        message: msg,
                        isSuccess: true,
                        timestamp: syncService.lastSyncDate
                    )
                }
                if let error = syncService.lastErrorMessage {
                    SyncStatusBanner(
                        message: friendlyMessage(error),
                        isSuccess: false,
                        timestamp: syncService.lastSyncDate
                    )
                }

                if !appState.trackedMilestonesByServer.isEmpty {
                    TrackedMilestonesCard(
                        stepsToday: stepsToday,
                        trackedMilestones: appState.trackedMilestonesByServer,
                        rewardTiers: rewards
                    )
                }

                if let errorMessage {
                    Text(friendlyMessage(errorMessage))
                        .foregroundColor(.red)
                        .font(.subheadline)
                }

                Text("Auto-sync & Background-sync enabled (\(appState.selectedServers.count) servers).")
                    .font(.footnote)
                    .foregroundColor(.secondary)
                    .padding(.top, 8)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .task {
            await refreshSteps()
            await refreshClaimStatuses()
            await refreshRewards()
            if appState.autoSyncEnabled {
                await syncService.syncSteps(appState: appState, manual: false)
            }
            startResetTimer()
        }
    }

    private var canSync: Bool {
        appState.isConfigured()
    }

    private func refreshSteps() async {
        do {
            let steps = try await HealthKitManager.shared.readTodaySteps()
            stepsToday = steps
        } catch {
            stepsToday = appState.lastKnownSteps ?? 0
            errorMessage = friendlyMessage(error.localizedDescription)
        }
    }

    private func refreshRewards() async {
        guard appState.isConfigured() else { return }
        isLoading = true
        errorMessage = nil
        do {
            var merged: [RewardTier] = []
            for server in appState.selectedServers {
                guard let key = appState.serverKey(for: server) else { continue }
                let response = try await ApiClient.shared.getRewards(
                    deviceId: appState.deviceId,
                    serverName: server,
                    playerApiKey: key
                )
                merged.append(contentsOf: response.tiers)
            }
            rewards = merged
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    private func refreshClaimStatuses() async {
        guard appState.isConfigured() else { return }
        var updated: [String: [ClaimStatusListItem]] = [:]
        for server in appState.selectedServers {
            guard let key = appState.serverKey(for: server) else { continue }
            do {
                let response = try await ApiClient.shared.getClaimStatusList(
                    deviceId: appState.deviceId,
                    playerApiKey: key
                )
                let resolved = response.serverName.isEmpty ? server : response.serverName
                updated[resolved] = response.items
            } catch {
                if !shouldSuppress(error: error) {
                    errorMessage = error.localizedDescription
                }
            }
        }
        claimStatuses = updated
    }

    private func startResetTimer() {
        updateResetTimer()
        Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { _ in
            updateResetTimer()
        }
    }

    private func updateResetTimer() {
        let central = TimeZone(identifier: "America/Chicago") ?? .current
        var calendar = Calendar.current
        calendar.timeZone = central
        let now = Date()
        let startOfTomorrow = calendar.startOfDay(for: now).addingTimeInterval(24 * 60 * 60)
        let diff = Int(startOfTomorrow.timeIntervalSince(now))
        let hours = max(0, diff / 3600)
        let minutes = max(0, (diff % 3600) / 60)
        let seconds = max(0, diff % 60)
        timeUntilReset = String(format: "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private var unclaimedGroups: [UnclaimedServerGroup] {
        claimStatuses
            .compactMap { server, items in
                let unclaimed = items.filter { !$0.claimed }
                guard !unclaimed.isEmpty else { return nil }
                return UnclaimedServerGroup(server: server, items: unclaimed)
            }
            .sorted { $0.server.lowercased() < $1.server.lowercased() }
    }

    private var claimedGroups: [UnclaimedServerGroup] {
        claimStatuses
            .compactMap { server, items in
                let claimed = items.filter { $0.claimed }
                guard !claimed.isEmpty else { return nil }
                return UnclaimedServerGroup(server: server, items: claimed)
            }
            .sorted { $0.server.lowercased() < $1.server.lowercased() }
    }

    private func shouldSuppress(error: Error) -> Bool {
        let message = error.localizedDescription.lowercased()
        if message.contains("no data available for the specified predicate") { return true }
        if message.contains("data couldn't be read because it is missing") { return true }
        if message.contains("data couldn’t be read because it is missing") { return true }
        return false
    }

    private func friendlyMessage(_ message: String) -> String {
        let lowered = message.lowercased()
        if lowered.contains("data couldn't be read because it is missing")
            || lowered.contains("data couldn’t be read because it is missing") {
            return "No step data yet."
        }
        return message
    }
}

private struct SyncStatusBanner: View {
    let message: String
    let isSuccess: Bool
    let timestamp: Date?

    var body: some View {
        let accent = isSuccess ? AppColors.healthGreen : Color.red
        let icon = isSuccess ? "checkmark.circle.fill" : "xmark.octagon.fill"

        HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(accent.opacity(0.18))
                    .frame(width: 36, height: 36)
                Image(systemName: icon)
                    .foregroundColor(accent)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(message)
                    .font(.subheadline)
                    .foregroundColor(.primary)
                if let timestamp {
                    Text(timeAgo(from: timestamp))
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color(.secondarySystemBackground))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(accent.opacity(0.35), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func timeAgo(from date: Date) -> String {
        let seconds = Int(Date().timeIntervalSince(date))
        if seconds < 60 { return "Just now" }
        if seconds < 3600 { return "\(seconds / 60) minutes ago" }
        return "\(seconds / 3600) hours ago"
    }
}

private struct StepCraftHeader: View {
    let username: String

    var body: some View {
        HStack(spacing: 10) {
            HStack {
                StepCraftLogo()
                    .frame(width: 34, height: 34)
            }
            .frame(width: 44, alignment: .center)

            Text("StepCraft")
                .font(titleFont)
                .foregroundColor(Color(hex: 0xFF8BC98B))
                .minimumScaleFactor(0.85)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .center)

            HStack {
                PlayerAvatar(username: username)
                    .frame(width: 36, height: 36)
            }
            .frame(width: 44, alignment: .center)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, minHeight: 62)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color(hex: 0xFF1E2128))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
    }

    private var titleFont: Font {
        if UIFont(name: "Roboto-Bold", size: 33) != nil {
            return .custom("Roboto-Bold", size: 33)
        }
        return .system(size: 33, weight: .black, design: .rounded)
    }
}

private struct PlayerAvatar: View {
    let username: String

    var body: some View {
        if !username.isEmpty, let url = URL(string: "https://minotar.net/avatar/\(username)/48") {
            AsyncImage(url: url) { image in
                image
                    .resizable()
                    .interpolation(.none)
            } placeholder: {
                avatarFallback
            }
            .frame(width: 34, height: 34)
            .clipShape(RoundedRectangle(cornerRadius: 7, style: .continuous))
        } else {
            avatarFallback
                .frame(width: 34, height: 34)
                .clipShape(RoundedRectangle(cornerRadius: 7, style: .continuous))
        }
    }

    private var avatarFallback: some View {
        RoundedRectangle(cornerRadius: 7, style: .continuous)
            .fill(Color(hex: 0xFF31343C))
    }
}

private struct StepCraftLogo: View {
    var body: some View {
        if UIImage(named: "StepCraftLogo") != nil {
            Image("StepCraftLogo")
                .resizable()
                .renderingMode(.original)
                .scaledToFit()
        } else {
            fallbackLogo
        }
    }

    private var fallbackLogo: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(Color(hex: 0xFF6B4634))
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(Color(hex: 0xFF3F8D45))
                .frame(height: 11)
        }
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
    }
}

private struct ResetTimerCard: View {
    let timeRemaining: String

    var body: some View {
        HStack(spacing: 6) {
            Text("Time Until Daily Step Reset:")
                .font(.headline)
                .foregroundColor(AppColors.healthBlue)
                .fontWeight(.semibold)
            Text(timeRemaining)
                .font(.headline)
                .fontWeight(.bold)
                .fontDesign(.monospaced)
                .foregroundColor(timerColor())
        }
        .frame(maxWidth: .infinity, alignment: .center)
        .multilineTextAlignment(.center)
        .padding(.vertical, 10)
        .padding(.horizontal, 12)
        .background(Color(hex: 0xFF2A2938))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private func timerColor() -> Color {
        let totalSeconds = max(0, parseSeconds(timeRemaining))
        let maxSeconds = 6 * 60 * 60
        let progress = CGFloat(maxSeconds - min(totalSeconds, maxSeconds)) / CGFloat(maxSeconds)

        let blue = (r: CGFloat(33.0 / 255.0), g: CGFloat(150.0 / 255.0), b: CGFloat(243.0 / 255.0))
        let orange = (r: CGFloat(1.0), g: CGFloat(152.0 / 255.0), b: CGFloat(0.0))
        let red = (r: CGFloat(244.0 / 255.0), g: CGFloat(67.0 / 255.0), b: CGFloat(54.0 / 255.0))

        if progress < 0.5 {
            let t = progress * 2.0
            return Color(
                red: lerp(blue.r, orange.r, t),
                green: lerp(blue.g, orange.g, t),
                blue: lerp(blue.b, orange.b, t)
            )
        } else {
            let t = (progress - 0.5) * 2.0
            return Color(
                red: lerp(orange.r, red.r, t),
                green: lerp(orange.g, red.g, t),
                blue: lerp(orange.b, red.b, t)
            )
        }
    }

    private func parseSeconds(_ text: String) -> Int {
        let parts = text.split(separator: ":").compactMap { Int($0) }
        guard parts.count == 3 else { return 0 }
        return parts[0] * 3600 + parts[1] * 60 + parts[2]
    }

    private func lerp(_ a: CGFloat, _ b: CGFloat, _ t: CGFloat) -> CGFloat {
        a + (b - a) * t
    }
}

private struct ActivityCard: View {
    let stepsToday: Int
    let canSync: Bool
    let onSync: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            HStack(spacing: 8) {
                Image(systemName: "figure.run")
                    .foregroundColor(.white)
                Text("Steps Today")
                    .font(.headline)
                    .foregroundColor(.white)
            }

            Text(stepsToday.formatted())
                .font(.system(size: 54, weight: .black))
                .foregroundColor(.white)

            Text("Small steps lead to big accomplishments.")
                .font(.subheadline)
                .foregroundColor(.white.opacity(0.85))

            Button(action: onSync) {
                HStack(spacing: 10) {
                    Image(systemName: "arrow.triangle.2.circlepath")
                    Text("SYNC NOW")
                        .fontWeight(.black)
                        .tracking(1)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
            }
            .buttonStyle(.plain)
            .background(Color.white)
            .foregroundColor(AppColors.healthGreen)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .opacity(canSync ? 1 : 0.5)
            .disabled(!canSync)
        }
        .frame(maxWidth: .infinity)
        .padding(20)
        .background(
            LinearGradient(colors: [Color(hex: 0xFF2E7D32), Color(hex: 0xFF1B5E20)], startPoint: .topLeading, endPoint: .bottomTrailing)
        )
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}

private struct UnclaimedRewardsBanner: View {
    let groups: [UnclaimedServerGroup]

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text("🎁")
                .font(.system(size: 22))
            VStack(alignment: .leading, spacing: 4) {
                Text("Unclaimed rewards:")
                    .font(.caption)
                    .foregroundColor(Color(hex: 0xFF574300))
                    .fontWeight(.bold)

                ForEach(groups) { group in
                    let summary = summaryText(for: group.items)
                    Text("• \(group.server): \(summary)")
                        .font(.caption)
                        .foregroundColor(Color(hex: 0xFF574300))
                }

                Text("Join the server to claim rewards!")
                    .font(.caption)
                    .foregroundColor(Color(hex: 0xFF574300))
                    .fontWeight(.bold)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(hex: 0xFFFFF4C4))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func summaryText(for items: [ClaimStatusListItem]) -> String {
        let grouped = Dictionary(grouping: items) { item in
            item.label.isEmpty ? "\(item.minSteps) steps" : item.label
        }
        return grouped
            .sorted { $0.key.lowercased() < $1.key.lowercased() }
            .map { label, list in
                list.count > 1 ? "\(label) x\(list.count)" : label
            }
            .joined(separator: ", ")
    }
}

private struct ClaimedRewardsBanner: View {
    let groups: [UnclaimedServerGroup]

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text("🎉")
                .font(.system(size: 22))
            VStack(alignment: .leading, spacing: 4) {
                Text("Rewards claimed:")
                    .font(.caption)
                    .foregroundColor(AppColors.healthGreen)
                    .fontWeight(.bold)

                ForEach(groups) { group in
                    let summary = summaryText(for: group.items)
                    Text("• \(group.server): \(summary)")
                        .font(.caption)
                        .foregroundColor(AppColors.healthGreen)
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppColors.healthLightGreen)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func summaryText(for items: [ClaimStatusListItem]) -> String {
        let grouped = Dictionary(grouping: items) { item in
            item.label.isEmpty ? "\(item.minSteps) steps" : item.label
        }
        return grouped
            .sorted { $0.key.lowercased() < $1.key.lowercased() }
            .map { label, list in
                list.count > 1 ? "\(label) x\(list.count)" : label
            }
            .joined(separator: ", ")
    }
}

private struct UnclaimedServerGroup: Identifiable {
    let server: String
    let items: [ClaimStatusListItem]
    var id: String { server }
}

private struct TrackedMilestonesCard: View {
    let stepsToday: Int
    let trackedMilestones: [String: Int]
    let rewardTiers: [RewardTier]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("👣")
                VStack(alignment: .leading) {
                    Text("Tracked milestones")
                        .font(.headline)
                        .foregroundColor(Color(hex: 0xFFE8F5E9))
                    Text("Keep stepping — you’re getting close!")
                        .font(.subheadline)
                        .foregroundColor(Color(hex: 0xFFB7D5BB))
                }
            }

            ForEach(trackedMilestones.sorted(by: { $0.key < $1.key }), id: \.key) { server, minSteps in
                let label = rewardTiers.first(where: { $0.minSteps == minSteps })?.label ?? "Milestone"
                MilestoneRow(server: server, label: label, stepsToday: stepsToday, minSteps: minSteps)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(
            LinearGradient(colors: [Color(hex: 0xFF1B3A20), Color(hex: 0xFF0F2414)], startPoint: .topLeading, endPoint: .bottomTrailing)
        )
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

private struct MilestoneRow: View {
    let server: String
    let label: String
    let stepsToday: Int
    let minSteps: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("🏁")
                Text("\(server) · \(label)")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(Color(hex: 0xFFEAE7D6))
                Spacer()
                if stepsToday >= minSteps {
                    Text("✅")
                } else if stepsToday >= Int(Double(minSteps) * 0.8) {
                    Text("🔥")
                }
            }

            GeometryReader { geo in
                let progress = CGFloat(min(1, Double(stepsToday) / Double(max(minSteps, 1))))
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(Color(hex: 0xFF2A3326))
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(LinearGradient(colors: [Color(hex: 0xFF7BE07B), Color(hex: 0xFF47C1FF)], startPoint: .leading, endPoint: .trailing))
                        .frame(width: max(10, geo.size.width * progress))
                        .opacity(minSteps > 0 ? 1 : 0)
                }
            }
            .frame(height: 14)

            Text("\(stepsToday) / \(minSteps) steps")
                .font(.caption)
                .foregroundColor(Color(hex: 0xFFBDB7A6))
        }
        .padding(12)
        .background(Color.black.opacity(0.2))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

