# StepCraft Companion iOS (SwiftUI)

This is the SwiftUI port of the Android StepCraft Companion app.

## What’s included
- Multi-step onboarding (HealthKit, notifications, username, server selection)
- Dashboard with step count + manual sync
- Settings with server management, milestones, and notifications
- Activity log and HealthKit raw data viewer
- URLSession-based API client with StepCraft Companion backend endpoints
- UserDefaults-backed preferences (device id, username, servers, keys, milestones)

## Next steps
1. Open this folder in Xcode and ensure the app target includes the iOS source folder.
2. In the target’s **Signing & Capabilities**, add **HealthKit**.
3. Build and run on a real device (HealthKit data is limited on the simulator).
4. Optionally enable Background Modes → Background fetch if you want periodic sync.

## Windows limitation
Building iOS apps requires macOS/Xcode. On Windows you can only edit code. Use a Mac or cloud Mac to build and run.

## Backend compatibility
The API client expects these endpoints (already in backend):
- GET /v1/players/rewards
- GET /v1/players/push/next
- POST /v1/players/register
- POST /v1/players/recover-key
- POST /v1/ingest
- GET /v1/servers/available
- GET /v1/players/claim-status/{minecraft_username}
- GET /v1/players/steps-today

The base URL is set to https://api.stepcraft.org/ in the API client.
