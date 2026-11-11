import Foundation
import UserNotifications

final class NotificationService: UNNotificationServiceExtension {
    private var completion: ((UNNotificationContent) -> Void)?
    private var task: URLSessionDataTask?
    private let lock = NSLock()

    override func didReceive(
        _ request: UNNotificationRequest,
        withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void
    ) {
        completion = contentHandler
        guard let keyID = request.content.userInfo["otusKey"] as? String,
              let message = request.content.userInfo["otusMessage"] as? String,
              message.range(of: "^[A-Za-z0-9_-]{32,64}$", options: .regularExpression) != nil,
              let base = Bundle.main.object(forInfoDictionaryKey: "OtusPushBridgeURL") as? String,
              let origin = URL(string: base), origin.scheme == "https",
              let saved = PushSecrets().read("key-\(keyID)"),
              let key = try? JSONDecoder().decode(WebPushKey.self, from: saved)
        else { finish(nil); return }
        let url = origin.appendingPathComponent("v1/messages/\(message)")
        var download = URLRequest(url: url)
        download.timeoutInterval = 15
        task = URLSession.shared.dataTask(with: download) { [weak self] data, response, _ in
            guard let self else { return }
            guard (response as? HTTPURLResponse)?.statusCode == 200, let data, key.isStateChange(data)
            else { finish(nil); return }
            var acknowledge = URLRequest(url: url)
            acknowledge.httpMethod = "DELETE"
            URLSession.shared.dataTask(with: acknowledge).resume()
            let content = request.content.mutableCopy() as? UNMutableNotificationContent
            content?.title = "Otus Mail"
            content?.body = "New mail"
            finish(content)
        }
        task?.resume()
    }

    override func serviceExtensionTimeWillExpire() {
        task?.cancel()
        finish(nil)
    }

    /// Anyone holding a route URL can trigger the APNs alert, so only a verified notice shows it.
    /// Empty content shows nothing; see docs/apple-push.md for the cases iOS still shows it.
    private func finish(_ content: UNNotificationContent?) {
        lock.lock()
        let handler = completion
        completion = nil
        lock.unlock()
        handler?(content ?? UNNotificationContent())
    }
}
