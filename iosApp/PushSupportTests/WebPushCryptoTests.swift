import Foundation
@testable import PushSupport
import XCTest

final class WebPushCryptoTests: XCTestCase {
    private func decoded(_ value: String) throws -> Data {
        let base64 = value.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        return try XCTUnwrap(Data(base64Encoded: base64 + String(repeating: "=", count: (4 - base64.count % 4) % 4)))
    }

    private func example() throws -> (WebPushKey, Data) {
        // Published interoperability vector from RFC 8291, section 5 and appendix A.
        let key = try WebPushKey(
            privateKey: decoded("q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94"),
            auth: decoded("BTBZMqHH6r4Tts7J_aSIgg")
        )
        let header = try decoded(
            "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml"
                + "mlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"
        )
        let body = try decoded("8pfeW0KbunFT06SuDKoJH9Ql87S1QUrdirN6GcG7sFz1y1sqLgVi1VhjVkHsUoEsbI_0LpXMuGvnzQ")
        return (key, header + body)
    }

    func testRFC8291Vector() throws {
        let (key, body) = try example()
        XCTAssertEqual(
            try String(data: key.decrypt(body), encoding: .utf8),
            "When I grow up, I want to be a watermelon"
        )
    }

    func testTamperingAndWrongKeyAreRejected() throws {
        let (key, original) = try example()
        var changed = original
        changed[changed.count - 1] ^= 1
        XCTAssertThrowsError(try key.decrypt(changed))
        XCTAssertThrowsError(try WebPushKey.create().decrypt(original))
        XCTAssertThrowsError(try key.decrypt(Data(original.prefix(40))))
    }

    func testExportContainsOnlyPublicKeyAndAuthSecret() throws {
        let key = WebPushKey.create()
        let exported = try JSONDecoder().decode([String: String].self, from: Data(key.subscription().utf8))
        XCTAssertEqual(Set(exported.keys), ["p256dh", "auth"])
        XCTAssertEqual(try decoded(XCTUnwrap(exported["p256dh"])).count, 65)
        XCTAssertEqual(try decoded(XCTUnwrap(exported["auth"])).count, 16)
    }
}
