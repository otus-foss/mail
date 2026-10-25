import Shared
import SwiftUI

@main
struct OtusApp: App {
    @UIApplicationDelegateAdaptor(ApplePushDelegate.self) private var delegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(.dark)
                .onChange(of: scenePhase) { phase in
                    if phase == .active {
                        ApplePushDelegate.shared.runtime.resume()
                    } else {
                        ApplePushDelegate.shared.runtime.pause()
                        ApplePushDelegate.shared.scheduleRefresh()
                    }
                }
        }
    }
}
