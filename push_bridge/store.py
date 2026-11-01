"""Bounded, persistent route and encrypted-message storage."""

import hashlib
import secrets
import sqlite3
import time
from contextlib import closing, contextmanager

# An active route not renewed within this time is deleted, so abandoned devices do not pile up.
ROUTE_LIFETIME = 8 * 86400
# A route that is not active, such as one still waiting for its verification push, lives only this
# long after its last update, so registrations nobody completes free their quota slot quickly.
INACTIVE_ROUTE_LIFETIME = 3600
MAX_ROUTES = 10000
MAX_ROUTES_PER_DEVICE = 32
MAX_MESSAGES_PER_ROUTE = 32
# An inactive route only needs room for its verification push and a few retries.
MAX_INACTIVE_MESSAGES = 4
MAX_MESSAGE_LIFETIME = 3600
# Expired rows are never served; deleting them runs at most this often per process.
PRUNE_INTERVAL = 60


class Store:
    def __init__(self, path):
        self.path = path
        self.next_prune = 0.0
        with self.connect() as database:
            database.executescript("""
                PRAGMA journal_mode=WAL;
                CREATE TABLE IF NOT EXISTS routes (
                    id TEXT PRIMARY KEY, secret TEXT, token TEXT, key_id TEXT,
                    active INTEGER DEFAULT 0, expires INTEGER
                );
                CREATE TABLE IF NOT EXISTS messages (
                    id TEXT PRIMARY KEY, route TEXT REFERENCES routes(id) ON DELETE CASCADE,
                    payload BLOB, expires INTEGER
                );
                CREATE INDEX IF NOT EXISTS messages_route ON messages(route);
                CREATE INDEX IF NOT EXISTS messages_expires ON messages(expires);
                CREATE INDEX IF NOT EXISTS routes_expires ON routes(expires);
                CREATE INDEX IF NOT EXISTS routes_token ON routes(token);
            """)

    @contextmanager
    def connect(self):
        with closing(sqlite3.connect(self.path, timeout=10)) as database:
            database.row_factory = sqlite3.Row
            database.execute("PRAGMA foreign_keys=ON")
            with database:
                yield database

    def prune(self, database):
        """Deletes expired rows through the expiry indexes, at most once per PRUNE_INTERVAL."""
        now = time.time()
        if now < self.next_prune:
            return
        self.next_prune = now + PRUNE_INTERVAL
        database.execute("DELETE FROM messages WHERE expires < ?", (now,))
        database.execute("DELETE FROM routes WHERE expires < ?", (now,))

    def create(self, token, key_id):
        route, secret = secrets.token_urlsafe(32), secrets.token_urlsafe(32)
        now = int(time.time())
        with self.connect() as database:
            database.execute("BEGIN IMMEDIATE")
            self.prune(database)
            total = database.execute(
                "SELECT count(*) FROM routes WHERE expires >= ?", (now,)
            ).fetchone()[0]
            device = database.execute(
                "SELECT count(*) FROM routes WHERE token=? AND expires >= ?", (token, now)
            ).fetchone()[0]
            if total >= MAX_ROUTES or device >= MAX_ROUTES_PER_DEVICE:
                raise OverflowError("Route quota reached")
            database.execute(
                "INSERT INTO routes VALUES (?, ?, ?, ?, 0, ?)",
                (route, digest(secret), token, key_id, now + INACTIVE_ROUTE_LIFETIME),
            )
        return {"id": route, "secret": secret}

    def route(self, route):
        with self.connect() as database:
            return database.execute(
                "SELECT * FROM routes WHERE id=? AND expires >= ?", (route, int(time.time()))
            ).fetchone()

    def update(self, route, active):
        lifetime = ROUTE_LIFETIME if active else INACTIVE_ROUTE_LIFETIME
        with self.connect() as database:
            database.execute(
                "UPDATE routes SET active=?, expires=? WHERE id=?",
                (active, int(time.time()) + lifetime, route),
            )

    def delete(self, route):
        with self.connect() as database:
            database.execute("DELETE FROM routes WHERE id=?", (route,))

    def save(self, route, payload, ttl, active=True):
        message = secrets.token_urlsafe(32)
        now = int(time.time())
        limit = MAX_MESSAGES_PER_ROUTE if active else MAX_INACTIVE_MESSAGES
        with self.connect() as database:
            database.execute("BEGIN IMMEDIATE")
            self.prune(database)
            count = database.execute(
                "SELECT count(*) FROM messages WHERE route=? AND expires >= ?", (route, now)
            ).fetchone()[0]
            if count >= limit:
                raise OverflowError("Message quota reached")
            database.execute(
                "INSERT INTO messages VALUES (?, ?, ?, ?)",
                (message, route, payload, now + min(ttl, MAX_MESSAGE_LIFETIME)),
            )
        return message

    def messages(self, route):
        now = int(time.time())
        with self.connect() as database:
            return database.execute(
                "SELECT messages.id FROM messages JOIN routes ON routes.id = messages.route"
                " WHERE messages.route=? AND messages.expires >= ? AND routes.expires >= ?",
                (route, now, now),
            ).fetchall()

    def message(self, message):
        now = int(time.time())
        with self.connect() as database:
            return database.execute(
                "SELECT payload FROM messages JOIN routes ON routes.id = messages.route"
                " WHERE messages.id=? AND messages.expires >= ? AND routes.expires >= ?",
                (message, now, now),
            ).fetchone()

    def remove_message(self, message):
        with self.connect() as database:
            database.execute("DELETE FROM messages WHERE id=?", (message,))

    def acknowledge(self, route, message):
        with self.connect() as database:
            database.execute("DELETE FROM messages WHERE id=? AND route=?", (message, route))


def digest(secret):
    return hashlib.sha256(secret.encode()).hexdigest()
