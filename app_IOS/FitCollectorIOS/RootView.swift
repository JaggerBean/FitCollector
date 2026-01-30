import SwiftUI

struct RootView: View {
    @EnvironmentObject private var appState: AppState

    var body: some View {
        if appState.onboardingComplete && appState.isConfigured() {
            TabView {
                NavigationStack {
                    DashboardScreen()
                }
                .tabItem {
                    Label("Dashboard", systemImage: "figure.walk")
                }

                NavigationStack {
                    SettingsScreen()
                }
                .tabItem {
                    Label("Settings", systemImage: "gearshape")
                }
            }
        } else {
            OnboardingScreen()
        }
    }
}
