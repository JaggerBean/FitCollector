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
                ActivityCard(stepsToday: stepsToday)

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
                    Text(errorMessage).foregroundColor(.red)
                }
            }
            .padding()
        }
        .navigationTitle("Dashboard")
        .task {
            await refreshRewards()
        }
    }

    private func refreshRewards() async {
        guard appState.isConfigured() else { return }
        isLoading = true
        errorMessage = nil
        do {
            let response = try await ApiClient.shared.getRewards(
                deviceId: appState.deviceId,
                serverName: appState.serverName,
                playerApiKey: appState.playerApiKey
            )
            rewards = response.tiers
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
        HStack {
            Image(systemName: isSuccess ? "checkmark.circle.fill" : "xmark.octagon.fill")
                .foregroundColor(isSuccess ? .green : .red)
            Text(message)
                .font(.subheadline)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color.primary.opacity(0.05))
        .cornerRadius(12)
    }
}

private struct ActivityCard: View {
    let stepsToday: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Today’s steps")
                .font(.headline)
            Text("\(stepsToday)")
                .font(.system(size: 36, weight: .bold))
            Text("Sync via HealthKit soon")
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(
            LinearGradient(colors: [Color.green.opacity(0.4), Color.blue.opacity(0.4)], startPoint: .topLeading, endPoint: .bottomTrailing)
        )
        .cornerRadius(16)
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
                    Text("Keep stepping — you’re getting close!")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
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
            LinearGradient(colors: [Color(red: 0.1, green: 0.3, blue: 0.18), Color(red: 0.07, green: 0.18, blue: 0.12)], startPoint: .topLeading, endPoint: .bottomTrailing)
        )
        .cornerRadius(16)
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
                Spacer()
                if stepsToday >= minSteps {
                    Text("✅")
                } else if stepsToday >= Int(Double(minSteps) * 0.8) {
                    Text("🔥")
                }
            }

            ProgressView(value: min(1, Double(stepsToday) / Double(max(minSteps, 1))))
                .tint(.green)

            Text("\(stepsToday) / \(minSteps) steps")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding(12)
        .background(Color.black.opacity(0.15))
        .cornerRadius(12)
    }
}
