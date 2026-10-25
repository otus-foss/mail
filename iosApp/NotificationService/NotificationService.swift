import Foundation
import UserNotifications

final class NotificationService: UNNotificationServiceExtension {
    private var completion: ((UNNotificationContent) -> Void)?
    private var content: UNMutableNotificationContent?
    private var task: URLSessionDataTask?
    private let lock = NSLock()

    override func didReceive(
        _ request: UNNotificationRequest,
        withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void
    ) {
        completion = contentHandler
        content = request.content.mutableCopy() as? UNMutableNotificationContent
        guard let keyID = request.content.userInfo["otusKey"] as? String,
              let message = request.content.userInfo["otusMessage"] as? String,
              message.range(of: "^[A-Za-z0-9_-]{32,64}$", options: .regularExpression) != nil,
              let base = Bundle.main.object(forInfoDictionaryKey: "OtusPushBridgeURL") as? String,
              let origin = URL(string: base), origin.scheme == "https",
              let saved = PushSecrets().read("key-\(keyID)"),
              let key = try? JSONDecoder().decode(WebPushKey.self, from: saved)
        else { finish(request.content); return }
        let url = origin.appendingPathComponent("v1/messages/\(message)")
        var download = URLRequest(url: url)
        download.timeoutInterval = 15
        task = URLSession.shared.dataTask(with: download) { [weak self] data, response, _ in
            guard let self else { return }
            if isStateChange(data, response: response, key: key) {
                content?.title = "Otus Mail"
                content?.body = "New mail"
                var acknowledge = URLRequest(url: url)
                acknowledge.httpMethod = "DELETE"
                URLSession.shared.dataTask(with: acknowledge).resume()
            }
            finish(request.content)
        }
        task?.resume()
    }

    private func isStateChange(_ data: Data?, response: URLResponse?, key: WebPushKey) -> Bool {
        guard (response as? HTTPURLResponse)?.statusCode == 200, let data,
              let plain = try? key.decrypt(data),
              let body = try? JSONSerialization.jsonObject(with: plain) as? [String: Any]
        else { return false }
        return body["@type"] as? String == "StateChange"
    }

    override func serviceExtensionTimeWillExpire() {
        task?.cancel()
        finish(UNMutableNotificationContent())
    }

    private func finish(_ fallback: UNNotificationContent) {
        lock.lock()
        let handler = completion
        completion = nil
        let result = content ?? fallback
        lock.unlock()
        handler?(result)
    }
}
