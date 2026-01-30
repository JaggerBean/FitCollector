import SwiftUI
import HealthKit

struct RawHealthDataScreen: View {
    @State private var samples: [HKQuantitySample] = []
    @State private var isLoading = false
    @State private var lookbackDays: Int = 0
    @State private var statusMessage: String?

    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                FilterChip(title: "Today", isSelected: lookbackDays == 0) { lookbackDays = 0 }
                FilterChip(title: "24 Hours", isSelected: lookbackDays == 1) { lookbackDays = 1 }
                FilterChip(title: "7 Days", isSelected: lookbackDays == 7) { lookbackDays = 7 }
            }
            .padding(.top)

            if let statusMessage {
                Text(statusMessage)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            if isLoading {
                ProgressView("Loading…")
                    .padding()
            } else if samples.isEmpty {
                Spacer()
                Text("No step records found for this period.")
                    .foregroundColor(.secondary)
                Spacer()
            } else {
                List(samples, id: \ .uuid) { sample in
                    VStack(alignment: .leading, spacing: 6) {
                        Text("\(Int(sample.quantity.doubleValue(for: HKUnit.count()))) steps")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                        Text("\(formatDate(sample.startDate)) → \(formatDate(sample.endDate))")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Text("Source: \(sample.sourceRevision.source.name)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .navigationTitle("Health Data Debug")
        .task {
            await loadSamples()
        }
        .onChange(of: lookbackDays) { _ in
            Task { await loadSamples() }
        }
    }

    private func loadSamples() async {
        isLoading = true
        statusMessage = nil
        let now = Date()
        let start: Date
        if lookbackDays == 0 {
            start = Calendar.current.startOfDay(for: now)
        } else if lookbackDays == 1 {
            start = now.addingTimeInterval(-24 * 60 * 60)
        } else {
            start = now.addingTimeInterval(-Double(lookbackDays) * 24 * 60 * 60)
        }

        do {
            let results = try await HealthKitManager.shared.readStepSamples(start: start, end: now)
            samples = results
            statusMessage = "Range: \(formatDate(start)) → \(formatDate(now))"
        } catch {
            statusMessage = "Error: \(error.localizedDescription)"
            samples = []
        }
        isLoading = false
    }

    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM dd, HH:mm:ss"
        return formatter.string(from: date)
    }
}

private struct FilterChip: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.caption)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(isSelected ? Color.accentColor.opacity(0.2) : Color.gray.opacity(0.15))
                .cornerRadius(12)
        }
        .buttonStyle(.plain)
    }
}
