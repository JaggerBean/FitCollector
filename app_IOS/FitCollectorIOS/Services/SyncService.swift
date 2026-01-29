import Foundation

@MainActor
final class SyncService: ObservableObject {
    @Published var lastSyncMessage: String?
    @Published var lastErrorMessage: String?

    func syncSteps() async {
        // TODO: wire with HealthKit + backend ingest endpoint
        lastSyncMessage = "Sync completed"
        lastErrorMessage = nil
    }
}
