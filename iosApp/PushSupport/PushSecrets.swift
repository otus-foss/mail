import Foundation
import Security

struct PushSecrets {
    private let service = "com.otus-foss.mail.push"

    private func query(_ key: String) -> [String: Any]? {
        guard let group = Bundle.main.object(forInfoDictionaryKey: "OtusPushKeychainGroup") as? String,
              !group.isEmpty, !group.contains("$(")
        else { return nil }
        return [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecAttrAccessGroup as String: group
        ]
    }

    func read(_ key: String) -> Data? {
        guard var query = query(key) else { return nil }
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess else { return nil }
        return result as? Data
    }

    @discardableResult
    func write(_ key: String, value: Data?) -> Bool {
        guard var query = query(key) else { return false }
        guard let value else {
            let status = SecItemDelete(query as CFDictionary)
            return status == errSecSuccess || status == errSecItemNotFound
        }
        let attributes: [String: Any] = [kSecValueData as String: value]
        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecSuccess {
            return true
        }
        guard status == errSecItemNotFound else { return false }
        query[kSecValueData as String] = value
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        return SecItemAdd(query as CFDictionary, nil) == errSecSuccess
    }
}
