import Foundation
import HealthKit

final class HealthKitManager {
    static let shared = HealthKitManager()

    private let healthStore = HKHealthStore()

    private init() {}

    func requestAuthorization() async throws {
        guard HKHealthStore.isHealthDataAvailable() else { return }
        guard let stepsType = HKObjectType.quantityType(forIdentifier: .stepCount) else { return }

        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            healthStore.requestAuthorization(toShare: [], read: [stepsType]) { success, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if success {
                    continuation.resume(returning: ())
                } else {
                    continuation.resume(throwing: NSError(domain: "HealthKit", code: 1, userInfo: [NSLocalizedDescriptionKey: "HealthKit authorization denied."]))
                }
            }
        }
    }

    func isStepAuthorizationGranted() -> Bool {
        guard HKHealthStore.isHealthDataAvailable(),
              let stepsType = HKObjectType.quantityType(forIdentifier: .stepCount) else {
            return false
        }
        return healthStore.authorizationStatus(for: stepsType) == .sharingAuthorized
    }

    func stepAuthorizationStatus() -> HKAuthorizationStatus {
        guard HKHealthStore.isHealthDataAvailable(),
              let stepsType = HKObjectType.quantityType(forIdentifier: .stepCount) else {
            return .sharingDenied
        }
        return healthStore.authorizationStatus(for: stepsType)
    }

    func readTodaySteps() async throws -> Int {
        guard let stepsType = HKObjectType.quantityType(forIdentifier: .stepCount) else { return 0 }
        let startOfDay = Calendar.current.startOfDay(for: Date())
        let predicate = HKQuery.predicateForSamples(withStart: startOfDay, end: Date(), options: .strictStartDate)

        return try await withCheckedThrowingContinuation { continuation in
            let query = HKStatisticsQuery(quantityType: stepsType, quantitySamplePredicate: predicate, options: .cumulativeSum) { _, result, error in
                if let error {
                    continuation.resume(throwing: error)
                    return
                }
                let sum = result?.sumQuantity()?.doubleValue(for: HKUnit.count()) ?? 0
                continuation.resume(returning: Int(sum))
            }
            healthStore.execute(query)
        }
    }

    func readStepSamples(start: Date, end: Date) async throws -> [HKQuantitySample] {
        guard let stepsType = HKObjectType.quantityType(forIdentifier: .stepCount) else { return [] }
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end, options: .strictStartDate)
        let sort = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: false)

        return try await withCheckedThrowingContinuation { continuation in
            let query = HKSampleQuery(sampleType: stepsType, predicate: predicate, limit: HKObjectQueryNoLimit, sortDescriptors: [sort]) { _, samples, error in
                if let error {
                    continuation.resume(throwing: error)
                    return
                }
                continuation.resume(returning: (samples as? [HKQuantitySample]) ?? [])
            }
            healthStore.execute(query)
        }
    }
}
