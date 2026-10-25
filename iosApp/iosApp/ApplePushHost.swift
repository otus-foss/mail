import Foundation
import Shared
import UIKit
import UserNotifications

final class ApplePushHost: NSObject, ApplePushPlatform {
    private let secrets = PushSecrets()

    func newIdentifier() -> String {
        UUID().uuidString
    }

    func requestRegistration() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            DispatchQueue.main.async {
                if granted {
                    UIApplication.shared.registerForRemoteNotifications()
                } else {
                    ApplePushDelegate.shared.runtime.registrationFailed()
                }
            }
        }
    }

    func stopRegistration() {
        DispatchQueue.main.async { UIApplication.shared.unregisterForRemoteNotifications() }
        UNUserNotificationCenter.current().removeAllDeliveredNotifications()
    }

    func read(key: String) -> String? {
        secrets.read(key).flatMap { String(data: $0, encoding: .utf8) }
    }

    func write(key: String, value: String?) -> Bool {
        secrets.write(key, value: value.map { Data($0.utf8) })
    }

    func createKey(id: String) -> String? {
        let key = WebPushKey.create()
        guard let data = try? JSONEncoder().encode(key), secrets.write("key-\(id)", value: data) else { return nil }
        return try? key.subscription()
    }

    func decrypt(id: String, payload: String) -> String? {
        guard let saved = secrets.read("key-\(id)"),
              let key = try? JSONDecoder().decode(WebPushKey.self, from: saved),
              let data = Data(base64Encoded: payload), let plain = try? key.decrypt(data)
        else { return nil }
        return String(data: plain, encoding: .utf8)
    }

    func removeKey(id: String) {
        secrets.write("key-\(id)", value: nil)
    }

    func showNewMail() {
        let content = UNMutableNotificationContent()
        content.title = "Otus Mail"
        content.body = "New mail"
        content.sound = .default
        UNUserNotificationCenter.current().add(
            UNNotificationRequest(identifier: "otus-new-mail", content: content, trigger: nil)
        )
    }
}
