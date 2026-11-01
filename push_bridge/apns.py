"""APNs HTTP/2 transport. Signing credentials never leave this process."""

import base64
import json
import time
from pathlib import Path

import httpx
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, utils


def encoded(value):
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode()


class Apns:
    def __init__(self, key_file, key_id, team, topic, sandbox=False):
        self.key = serialization.load_pem_private_key(Path(key_file).read_bytes(), None)
        if not isinstance(self.key, ec.EllipticCurvePrivateKey) or not isinstance(
            self.key.curve, ec.SECP256R1
        ):
            raise ValueError("APNs requires a P-256 signing key")
        self.key_id, self.team, self.topic = key_id, team, topic
        host = "api.sandbox.push.apple.com" if sandbox else "api.push.apple.com"
        self.client = httpx.Client(
            base_url=f"https://{host}", http2=True, timeout=10, trust_env=False
        )
        self.token, self.issued = "", 0

    def authorization(self):
        now = int(time.time())
        if now - self.issued > 1800:
            header = encoded(json.dumps({"alg": "ES256", "kid": self.key_id}).encode())
            claims = encoded(json.dumps({"iss": self.team, "iat": now}).encode())
            data = f"{header}.{claims}"
            signature = self.key.sign(data.encode(), ec.ECDSA(hashes.SHA256()))
            first, second = utils.decode_dss_signature(signature)
            self.token = f"{data}.{encoded(first.to_bytes(32, 'big') + second.to_bytes(32, 'big'))}"
            self.issued = now
        return f"bearer {self.token}"

    def send(self, route, message_id):
        response = self.client.post(
            f"/3/device/{route['token']}",
            headers={
                "authorization": self.authorization(),
                "apns-topic": self.topic,
                "apns-push-type": "alert",
                "apns-priority": "10",
                "apns-expiration": str(int(time.time()) + 3600),
                "apns-collapse-id": route["id"],
            },
            json={
                "aps": {
                    "alert": {"title": "Otus Mail", "body": "New mail"},
                    "mutable-content": 1,
                    "sound": "default",
                },
                "otusKey": route["key_id"],
                "otusMessage": message_id,
            },
        )
        reason = rejection(response) if response.status_code != 200 else None
        if response.status_code == 410 or reason in ("BadDeviceToken", "DeviceTokenNotForTopic"):
            return False
        response.raise_for_status()
        return True


def rejection(response):
    """The `reason` of an APNs error response; None when its body is not the documented JSON."""
    try:
        body = response.json()
    except ValueError:
        return None
    return body.get("reason") if isinstance(body, dict) else None
