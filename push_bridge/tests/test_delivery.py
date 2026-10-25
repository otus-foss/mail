import io
import json
import tempfile
import unittest
from unittest.mock import Mock

import httpx

from push_bridge.application import Application
from push_bridge.store import Store


class DeliveryTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.store = Store(self.directory.name + "/push.sqlite")
        self.apns = Mock()
        self.apns.send.return_value = True
        self.app = Application(self.store, self.apns)
        self.route = self.store.create("ab" * 32, "12345678-1234-1234-1234-123456789012")
        self.payload = b"a" * 20 + bytes([65]) + b"b" * 100

    def request(self, path, method="GET", body=b"", secret=None, **headers):
        env = {
            "REQUEST_METHOD": method,
            "PATH_INFO": path,
            "CONTENT_LENGTH": str(len(body)),
            "wsgi.input": io.BytesIO(body),
        }
        env.update(headers)
        if secret:
            env["HTTP_AUTHORIZATION"] = f"Bearer {secret}"
        status = []
        response = b"".join(self.app(env, lambda value, _: status.append(int(value.split()[0]))))
        return status[0], response

    def push(self):
        return self.request(
            "/v1/push/" + self.route["id"], "POST", self.payload, HTTP_CONTENT_ENCODING="aes128gcm"
        )

    def test_verification_is_buffered_without_an_alert(self):
        self.assertEqual(self.push()[0], 201)
        self.apns.send.assert_not_called()
        route = self.route
        status, body = self.request(f"/v1/routes/{route['id']}/messages", secret=route["secret"])
        self.assertEqual(status, 200)
        message = json.loads(body)[0]
        self.assertEqual(self.request("/v1/messages/" + message), (200, self.payload))
        self.assertEqual(self.request("/v1/messages/" + message, "DELETE")[0], 204)
        self.assertEqual(self.request("/v1/messages/" + message)[0], 404)

    def test_activation_requires_management_secret(self):
        path = "/v1/routes/" + self.route["id"]
        self.assertEqual(self.request(path, "PATCH", b'{"active":true}', secret="wrong")[0], 404)
        self.assertEqual(
            self.request(path, "PATCH", b'{"active":true}', secret=self.route["secret"])[0], 204
        )
        self.assertEqual(self.push()[0], 201)
        self.apns.send.assert_called_once()

    def test_invalid_device_revokes_route(self):
        self.store.update(self.route["id"], True)
        self.apns.send.return_value = False
        self.assertEqual(self.push()[0], 410)
        self.assertIsNone(self.store.route(self.route["id"]))

    def test_temporary_failure_is_retryable_and_does_not_fill_storage(self):
        self.store.update(self.route["id"], True)
        self.apns.send.side_effect = httpx.ConnectError("offline")
        self.assertEqual(self.push()[0], 503)
        self.assertEqual(self.store.messages(self.route["id"]), [])
        self.assertIsNotNone(self.store.route(self.route["id"]))

    def test_expired_routes_and_messages_disappear(self):
        self.push()
        with self.store.connect() as database:
            database.execute("UPDATE routes SET expires=0")
        self.assertEqual(self.push()[0], 410)
        self.assertEqual(self.store.messages(self.route["id"]), [])

    def test_restart_keeps_routes_and_ciphertext(self):
        self.push()
        reopened = Store(self.directory.name + "/push.sqlite")
        self.assertEqual(reopened.route(self.route["id"])["token"], "ab" * 32)
        self.assertEqual(len(reopened.messages(self.route["id"])), 1)

    def test_plaintext_oversized_and_malformed_pushes_are_rejected(self):
        path = "/v1/push/" + self.route["id"]
        self.assertEqual(self.request(path, "POST", b"secret mail")[0], 415)
        self.assertEqual(
            self.request(path, "POST", b"x" * 4097, HTTP_CONTENT_ENCODING="aes128gcm")[0], 413
        )
        self.assertEqual(
            self.request(path, "POST", b"invalid", HTTP_CONTENT_ENCODING="aes128gcm")[0], 400
        )
        self.assertEqual(self.store.messages(self.route["id"]), [])

    def test_delete_requires_secret_and_removes_pending_messages(self):
        self.push()
        path = "/v1/routes/" + self.route["id"]
        self.assertEqual(self.request(path, "DELETE")[0], 404)
        self.assertEqual(self.request(path, "DELETE", secret=self.route["secret"])[0], 204)
        self.assertEqual(self.push()[0], 410)

    def test_route_and_message_quotas_bound_storage(self):
        for _ in range(32):
            self.assertEqual(self.push()[0], 201)
        self.assertEqual(self.push()[0], 429)
        for _ in range(31):
            self.store.create("ab" * 32, "key")
        with self.assertRaises(OverflowError):
            self.store.create("ab" * 32, "key")

    def test_registration_rejects_tokens_used_as_paths(self):
        payload = json.dumps(
            {"deviceToken": "../secret", "keyId": "12345678-1234-1234-1234-123456789012"}
        ).encode()
        self.assertEqual(self.request("/v1/routes", "POST", payload)[0], 400)

    def test_messages_cannot_be_acknowledged_through_another_route(self):
        self.push()
        message = self.store.messages(self.route["id"])[0]["id"]
        other = self.store.create("cd" * 32, "key")
        self.request(
            f"/v1/routes/{other['id']}/messages/{message}", "DELETE", secret=other["secret"]
        )
        self.assertIsNotNone(self.store.message(message))
