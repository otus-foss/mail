"""WSGI API. Push URLs and message URLs are unguessable bearer capabilities."""

import hmac
import json
import re
from http import HTTPStatus

import httpx

from push_bridge.store import digest

MAX_BODY = 4096
# An aes128gcm body (RFC 8188) starts with a 16-byte salt, a 4-byte record size, and the key-id
# length, which RFC 8291 fixes at 65 for the sender's P-256 key. The record that follows holds at
# least its 1-byte padding delimiter and 16-byte authentication tag.
KEY_ID_LENGTH_OFFSET = 20
SENDER_KEY_LENGTH = 65
MIN_PUSH_BODY = KEY_ID_LENGTH_OFFSET + 1 + SENDER_KEY_LENGTH + 1 + 16


class Problem(Exception):
    def __init__(self, status):
        self.status = status


class Application:
    def __init__(self, store, apns):
        self.store, self.apns = store, apns

    def __call__(self, environ, start_response):
        try:
            status, body, content_type = self.dispatch(environ)
        except Problem as error:
            status, body, content_type = error.status, b"", "text/plain"
        except (ValueError, KeyError, TypeError):
            status, body, content_type = 400, b"", "text/plain"
        except OverflowError:
            status, body, content_type = 429, b"", "text/plain"
        except httpx.HTTPError:
            status, body, content_type = 503, b"", "text/plain"
        headers = [("Content-Type", content_type), ("Cache-Control", "no-store")]
        if status in (429, 503):
            headers.append(("Retry-After", "60"))
        start_response(f"{status} {HTTPStatus(status).phrase}", headers)
        return [body]

    def dispatch(self, env):
        method, path = env["REQUEST_METHOD"], env["PATH_INFO"]
        if method == "GET" and path == "/health":
            return self.reply(200)
        if method == "POST" and path == "/v1/routes":
            request = json.loads(self.body(env))
            token, key_id = request["deviceToken"], request["keyId"]
            if not re.fullmatch(r"[a-f0-9]{32,256}", token) or not re.fullmatch(
                r"[A-Za-z0-9-]{32,64}", key_id
            ):
                raise Problem(400)
            return self.reply(201, self.store.create(token, key_id))
        if method in ("GET", "DELETE") and path.startswith("/v1/messages/"):
            message_id = path.removeprefix("/v1/messages/")
            message = self.store.message(message_id)
            if message is None:
                raise Problem(404)
            if method == "DELETE":
                self.store.remove_message(message_id)
                return self.reply(204)
            return 200, message["payload"], "application/octet-stream"
        if method == "POST" and path.startswith("/v1/push/"):
            return self.push(env, path.removeprefix("/v1/push/"))
        match = re.fullmatch(
            r"/v1/routes/([A-Za-z0-9_-]+)(?:/messages(?:/([A-Za-z0-9_-]+))?)?", path
        )
        if not match:
            raise Problem(404)
        route_id, message_id = match.groups()
        route = self.store.route(route_id)
        supplied = env.get("HTTP_AUTHORIZATION", "").removeprefix("Bearer ")
        if route is None or not hmac.compare_digest(route["secret"], digest(supplied)):
            raise Problem(404)
        if "/messages" in path:
            if method == "GET" and message_id is None:
                return self.reply(200, [row["id"] for row in self.store.messages(route_id)])
            if method == "DELETE" and message_id:
                self.store.acknowledge(route_id, message_id)
                return self.reply(204)
        elif method == "PATCH":
            active = json.loads(self.body(env))["active"]
            if not isinstance(active, bool):
                raise Problem(400)
            self.store.update(route_id, active)
            return self.reply(204)
        elif method == "DELETE":
            self.store.delete(route_id)
            return self.reply(204)
        raise Problem(405)

    def push(self, env, route_id):
        route = self.store.route(route_id)
        if route is None:
            raise Problem(410)
        if env.get("HTTP_CONTENT_ENCODING") != "aes128gcm":
            raise Problem(415)
        ttl = int(env.get("HTTP_TTL", "3600"))
        if ttl < 0:
            raise Problem(400)
        payload = self.body(env)
        if len(payload) < MIN_PUSH_BODY or payload[KEY_ID_LENGTH_OFFSET] != SENDER_KEY_LENGTH:
            raise Problem(400)
        message = self.store.save(route_id, payload, max(1, ttl), active=bool(route["active"]))
        if route["active"]:
            try:
                accepted = self.apns.send(route, message)
            except httpx.HTTPError:
                self.store.acknowledge(route_id, message)
                raise
            if not accepted:
                self.store.delete(route_id)
                raise Problem(410)
        return self.reply(201)

    @staticmethod
    def body(env):
        length = int(env.get("CONTENT_LENGTH") or "0")
        if length > MAX_BODY:
            raise Problem(413)
        if length <= 0:
            raise Problem(400)
        result = env["wsgi.input"].read(length)
        if len(result) != length:
            raise Problem(400)
        return result

    @staticmethod
    def reply(status, value=None):
        return status, json.dumps(value).encode() if value is not None else b"", "application/json"
