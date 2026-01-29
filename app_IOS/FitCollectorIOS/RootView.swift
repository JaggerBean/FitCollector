import SwiftUI

struct RootView: View {
    @EnvironmentObject private var appState: AppState

    var body: some View {
        if appState.isConfigured() {
            TabView {
                DashboardScreen()
                    .tabItem {
                        Label("Dashboard", systemImage: "figure.walk")
                    }

                SettingsScreen()
                    .tabItem {
                        Label("Settings", systemImage: "gearshape")
                    }
            }
        } else {
            OnboardingScreen()
        }
    }
}
