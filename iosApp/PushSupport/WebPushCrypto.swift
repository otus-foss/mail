import CryptoKit
import Foundation

struct WebPushKey: Codable {
    let privateKey: Data
    let auth: Data

    static func create() -> Self {
        let key = P256.KeyAgreement.PrivateKey()
        let secret = SymmetricKey(size: .bits128).withUnsafeBytes { Data($0) }
        return Self(privateKey: key.rawRepresentation, auth: secret)
    }

    func subscription() throws -> String {
        let key = try P256.KeyAgreement.PrivateKey(rawRepresentation: privateKey)
        let values = ["p256dh": key.publicKey.x963Representation.base64URL, "auth": auth.base64URL]
        let data = try JSONEncoder().encode(values)
        guard let text = String(data: data, encoding: .utf8) else { throw PushCryptoError.invalidRecord }
        return text
    }

    func decrypt(_ payload: Data) throws -> Data {
        guard payload.count >= 103, payload.count <= 4096, payload[20] == 65 else {
            throw PushCryptoError.invalidRecord
        }
        let recordSize = payload[16 ..< 20].reduce(UInt32(0)) { ($0 << 8) | UInt32($1) }
        let ciphertext = payload.dropFirst(86)
        guard Int(recordSize) > ciphertext.count else { throw PushCryptoError.invalidRecord }
        let local = try P256.KeyAgreement.PrivateKey(rawRepresentation: privateKey)
        let remote = try P256.KeyAgreement.PublicKey(x963Representation: payload[21 ..< 86])
        let shared = try local.sharedSecretFromKeyAgreement(with: remote)
        let info = Data("WebPush: info\0".utf8) + local.publicKey.x963Representation + remote.x963Representation
        let input = shared.hkdfDerivedSymmetricKey(
            using: SHA256.self, salt: auth, sharedInfo: info, outputByteCount: 32
        )
        let salt = Data(payload.prefix(16))
        let contentKey = HKDF<SHA256>.deriveKey(
            inputKeyMaterial: input, salt: salt,
            info: Data("Content-Encoding: aes128gcm\0".utf8), outputByteCount: 16
        )
        let nonceKey = HKDF<SHA256>.deriveKey(
            inputKeyMaterial: input, salt: salt,
            info: Data("Content-Encoding: nonce\0".utf8), outputByteCount: 12
        )
        let nonce = try AES.GCM.Nonce(data: nonceKey.withUnsafeBytes { Data($0) })
        let sealed = try AES.GCM.SealedBox(
            nonce: nonce, ciphertext: ciphertext.dropLast(16), tag: ciphertext.suffix(16)
        )
        var plain = try AES.GCM.open(sealed, using: contentKey)
        while plain.last == 0 {
            plain.removeLast()
        }
        guard plain.popLast() == 2 else { throw PushCryptoError.invalidRecord }
        return plain
    }
}

enum PushCryptoError: Error {
    case invalidRecord
}

extension Data {
    var base64URL: String {
        base64EncodedString().replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    }
}
