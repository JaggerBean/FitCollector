import SwiftUI

struct ActivityLogScreen: View {
    @EnvironmentObject private var appState: AppState

    var body: some View {
        List {
            if appState.syncLog.isEmpty {
                Text("No activity logged yet.")
                    .foregroundColor(.secondary)
            } else {
                ForEach(appState.syncLog) { entry in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Circle()
                                .fill(entry.success ? Color.green : Color.red)
                                .frame(width: 8, height: 8)
                            Text(entry.message)
                                .font(.subheadline)
                                .fontWeight(.semibold)
                        }
                        Text(entry.timestamp)
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Text("Steps: \(entry.steps) · \(entry.source)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(.vertical, 4)
                }
            }
        }
    }
}
