import SwiftUI

struct DashboardScreen: View {
    @EnvironmentObject private var appState: AppState
    @StateObject private var syncService = SyncService()
    @State private var rewards: [RewardTier] = []
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var stepsToday: Int = 0

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                HeaderRow(title: "StepCraft")

                ActivityCard(stepsToday: stepsToday, canSync: canSync) {
                    Task { await syncService.syncSteps(appState: appState, manual: true) }
                }

                if let msg = syncService.lastSyncMessage {
                    SyncStatusBanner(message: msg, isSuccess: true)
                }
                if let error = syncService.lastErrorMessage {
                    SyncStatusBanner(message: error, isSuccess: false)
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
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .navigationTitle("Dashboard")
        .task {
            await refreshSteps()
            await refreshRewards()
            if appState.autoSyncEnabled {
                await syncService.syncSteps(appState: appState, manual: false)
            }
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
}

private struct SyncStatusBanner: View {
    let message: String
    let isSuccess: Bool

    var body: some View {
        let bg = isSuccess ? AppColors.healthLightGreen : Color.red.opacity(0.12)
        let icon = isSuccess ? "checkmark.circle.fill" : "xmark.octagon.fill"

        HStack(spacing: 10) {
            Image(systemName: icon)
                .foregroundColor(isSuccess ? AppColors.healthGreen : .red)
            Text(message)
                .font(.subheadline)
                .foregroundColor(.primary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(bg)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

private struct ActivityCard: View {
    let stepsToday: Int
    let canSync: Bool
    let onSync: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Steps Today")
                .font(.headline)
                .foregroundColor(.white.opacity(0.85))

            Text(stepsToday.formatted())
                .font(.system(size: 54, weight: .black))
                .foregroundColor(.white)

            Text("HealthKit · StepCraft")
                .font(.subheadline)
                .foregroundColor(.white.opacity(0.7))

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
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(20)
        .background(
            LinearGradient(colors: [AppColors.healthGreen, Color(hex: 0xFF1B5E20)], startPoint: .topLeading, endPoint: .bottomTrailing)
        )
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
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

private struct HeaderRow: View {
    let title: String

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 28, weight: .black))
                    .foregroundColor(AppColors.healthGreen)
                Text("Collect steps. Earn rewards.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            Spacer()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 4)
    }
}
