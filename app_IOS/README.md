# FitCollector iOS (SwiftUI)

This is a starter SwiftUI port scaffold for the FitCollector app.

## What’s included
- SwiftUI app shell with onboarding, dashboard, and settings screens
- URLSession-based API client matching the backend endpoints
- UserDefaults-backed preferences (device id, username, server selection, milestones)
- Simple data models mirroring backend JSON

## Next steps
1. Open this folder in Xcode and create a new iOS App target named **FitCollectorIOS**.
2. Drag the `FitCollectorIOS` folder into the Xcode project (check “Copy items if needed”).
3. Add HealthKit/Health Connect analogs on iOS (HealthKit + Background Tasks).
4. Wire push notifications (UNUserNotificationCenter) and background fetch.
5. Replace placeholder UI elements with finalized designs.

## Windows limitation
Building iOS apps requires macOS/Xcode. On Windows you can only edit code. Use a Mac or cloud Mac to build and run.

## Backend compatibility
The API client expects these endpoints (already in backend):
- `GET /v1/players/rewards`
- `GET /v1/players/push/next`
- `POST /v1/players/register`
- `POST /v1/players/recover-key`

You’ll need to set the `baseURL` in `ApiClient`.
