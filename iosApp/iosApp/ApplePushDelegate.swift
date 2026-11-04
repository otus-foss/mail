import BackgroundTasks
import Shared
import UIKit
import UserNotifications

final class ApplePushDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    static let shared = ApplePushDelegate()
    private let host = ApplePushHost()
    lazy var runtime = IosPushRuntime(
        platform: host,
        baseUrl: Bundle.main.object(forInfoDictionaryKey: "OtusPushBridgeURL") as? String ?? ""
    )
    private var refreshIdentifier: String {
        "\(Bundle.main.bundleIdentifier ?? "com.otus.mail").push-refresh"
    }

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = Self.shared
        BGTaskScheduler.shared.register(forTaskWithIdentifier: refreshIdentifier, using: .main) { task in
            task.expirationHandler = { Self.shared.runtime.pause() }
            Self.shared.scheduleRefresh()
            Self.shared.runtime.refresh { success in task.setTaskCompleted(success: success.boolValue) }
        }
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Self.shared.runtime.registered(deviceToken: deviceToken.map { String(format: "%02x", $0) }.joined())
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        Self.shared.runtime.registrationFailed()
    }

    func scheduleRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: refreshIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 12 * 60 * 60)
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            // Background refresh is best effort; foreground activation also renews.
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        Self.shared.runtime.resume()
        completionHandler()
    }
}
