import base64
import json
import tempfile
import unittest
from pathlib import Path

import httpx
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, utils

from push_bridge.apns import Apns


class ApnsTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        key = ec.generate_private_key(ec.SECP256R1())
        path = Path(self.directory.name) / "test.p8"
        path.write_bytes(
            key.private_bytes(
                serialization.Encoding.PEM,
                serialization.PrivateFormat.PKCS8,
                serialization.NoEncryption(),
            )
        )
        self.public = key.public_key()
        self.apns = Apns(path, "test-key", "test-team", "com.otus-foss.mail", sandbox=True)
        self.addCleanup(self.apns.client.close)

    def test_signs_an_es256_jwt_and_reuses_it(self):
        value = self.apns.authorization()
        header, claims, signature = value.removeprefix("bearer ").split(".")

        def decode(text):
            return base64.urlsafe_b64decode(text + "=" * (-len(text) % 4))

        self.assertEqual(json.loads(decode(header))["alg"], "ES256")
        self.assertEqual(json.loads(decode(claims))["iss"], "test-team")
        raw = decode(signature)
        der = utils.encode_dss_signature(int.from_bytes(raw[:32]), int.from_bytes(raw[32:]))
        self.public.verify(der, f"{header}.{claims}".encode(), ec.ECDSA(hashes.SHA256()))
        self.assertEqual(self.apns.authorization(), value)

    def test_sends_only_generic_alert_and_opaque_message_id(self):
        captured = []

        def reply(request):
            captured.append(request)
            return httpx.Response(200)

        self.apns.client.close()
        self.apns.client = httpx.Client(
            base_url="https://api.sandbox.push.apple.com", transport=httpx.MockTransport(reply)
        )
        self.assertTrue(
            self.apns.send({"id": "route", "token": "ab" * 32, "key_id": "key"}, "message")
        )
        request = captured[0]
        self.assertEqual(request.headers["apns-topic"], "com.otus-foss.mail")
        self.assertEqual(request.headers["apns-push-type"], "alert")
        body = json.loads(request.content)
        self.assertEqual(body["otusMessage"], "message")
        self.assertEqual(body["aps"]["mutable-content"], 1)
        self.assertEqual(set(body), {"aps", "otusKey", "otusMessage"})

    def test_a_rejected_token_revokes_and_an_unreadable_error_is_temporary(self):
        replies = iter(
            [
                httpx.Response(400, json={"reason": "BadDeviceToken"}),
                httpx.Response(500, text="<html>Internal error</html>"),
                httpx.Response(500, json=["unexpected"]),
            ]
        )
        self.apns.client.close()
        self.apns.client = httpx.Client(
            base_url="https://api.sandbox.push.apple.com",
            transport=httpx.MockTransport(lambda request: next(replies)),
        )
        route = {"id": "route", "token": "ab" * 32, "key_id": "key"}
        self.assertFalse(self.apns.send(route, "message"))
        # Not a ValueError, which the bridge would answer with 400, a permanent failure.
        for _ in range(2):
            with self.assertRaises(httpx.HTTPStatusError):
                self.apns.send(route, "message")
