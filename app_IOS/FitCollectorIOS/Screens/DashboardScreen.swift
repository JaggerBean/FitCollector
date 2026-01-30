import SwiftUI

struct DashboardScreen: View {
    @EnvironmentObject private var appState: AppState
    @StateObject private var syncService = SyncService()
    @State private var rewards: [RewardTier] = []
    @State private var claimStatuses: [String: [ClaimStatusListItem]] = [:]
    @State private var stepsYesterdayByServer: [String: Int] = [:]
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

                if !unclaimedServers.isEmpty {
                    UnclaimedRewardsBanner(
                        servers: unclaimedServers,
                        stepsYesterdayByServer: stepsYesterdayByServer
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
                        message: error,
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
                    Text(errorMessage)
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
            await refreshStepsYesterday()
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
        do {
            var updated: [String: [ClaimStatusListItem]] = [:]
            for server in appState.selectedServers {
                guard let key = appState.serverKey(for: server) else { continue }
                let response = try await ApiClient.shared.getClaimStatusList(
                    deviceId: appState.deviceId,
                    playerApiKey: key
                )
                updated[server] = response.items
            }
            claimStatuses = updated
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func refreshStepsYesterday() async {
        guard appState.isConfigured() else { return }
        do {
            var updated: [String: Int] = [:]
            for server in appState.selectedServers {
                guard let key = appState.serverKey(for: server) else { continue }
                let response = try await ApiClient.shared.getStepsYesterday(
                    minecraftUsername: appState.minecraftUsername,
                    playerApiKey: key
                )
                updated[server] = response.stepsYesterday
            }
            stepsYesterdayByServer = updated
        } catch {
            errorMessage = error.localizedDescription
        }
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

    private var unclaimedServers: [String] {
        let yesterday = centralDayString(yesterday: true)
        return claimStatuses
            .mapValues { $0.filter { !$0.claimed && $0.day == yesterday } }
            .filter { !$0.value.isEmpty }
            .map { $0.key }
            .sorted()
    }

    private func centralDayString(yesterday: Bool) -> String {
        let central = TimeZone(identifier: "America/Chicago") ?? .current
        var calendar = Calendar.current
        calendar.timeZone = central
        let date = calendar.date(byAdding: .day, value: yesterday ? -1 : 0, to: Date()) ?? Date()
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = central
        return formatter.string(from: date)
    }
}

private struct SyncStatusBanner: View {
    let message: String
    let isSuccess: Bool
    let timestamp: Date?

    var body: some View {
        let bg = isSuccess ? AppColors.healthLightGreen : Color.red.opacity(0.12)
        let icon = isSuccess ? "checkmark.circle.fill" : "xmark.octagon.fill"

        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundColor(isSuccess ? AppColors.healthGreen : .red)
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
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(bg)
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
        HStack(spacing: 12) {
            if !username.isEmpty, let url = URL(string: "https://minotar.net/avatar/\(username)/48") {
                AsyncImage(url: url) { image in
                    image.resizable()
                } placeholder: {
                    Color.gray.opacity(0.2)
                }
                .frame(width: 32, height: 32)
                .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
            } else {
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .fill(Color.gray.opacity(0.2))
                    .frame(width: 32, height: 32)
            }

            HStack(spacing: 6) {
                LogoBadge()
                Text("StepCraft")
                    .font(.system(size: 24, weight: .bold, design: .rounded))
                    .foregroundColor(AppColors.healthGreen)
            }

            Spacer()

            NavigationLink(destination: SettingsScreen()) {
                Image(systemName: "gearshape")
                    .foregroundColor(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 4)
    }
}

private struct LogoBadge: View {
    var body: some View {
        VStack(spacing: 2) {
            Image(systemName: "figure.run")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(AppColors.healthGreen)
            ZStack(alignment: .top) {
                RoundedRectangle(cornerRadius: 2, style: .continuous)
                    .fill(AppColors.minecraftDirt)
                    .frame(width: 20, height: 12)
                RoundedRectangle(cornerRadius: 2, style: .continuous)
                    .fill(AppColors.minecraftGrass)
                    .frame(width: 20, height: 4)
            }
        }
        .frame(width: 28, height: 32)
    }
}

private struct ResetTimerCard: View {
    let timeRemaining: String

    var body: some View {
        HStack(spacing: 8) {
            Text("Time Until Daily Step Reset:")
                .font(.caption)
                .foregroundColor(AppColors.healthBlue)
            Text(timeRemaining)
                .font(.caption)
                .foregroundColor(AppColors.healthBlue)
                .fontWeight(.semibold)
            Spacer()
        }
        .padding(10)
        .background(Color(hex: 0xFFE6F0FF))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
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
    let servers: [String]
    let stepsYesterdayByServer: [String: Int]

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text("🎁")
                .font(.system(size: 22))
            VStack(alignment: .leading, spacing: 4) {
                Text("Unclaimed rewards for yesterday's steps:")
                    .font(.caption)
                    .foregroundColor(Color(hex: 0xFF574300))
                    .fontWeight(.bold)

                ForEach(servers, id: \.self) { server in
                    let steps = stepsYesterdayByServer[server] ?? 0
                    Text("• \(server): \(steps) steps")
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

