"""Bounded, persistent route and encrypted-message storage."""

import hashlib
import secrets
import sqlite3
import time
from contextlib import closing, contextmanager


class Store:
    def __init__(self, path):
        self.path = path
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
            """)

    @contextmanager
    def connect(self):
        with closing(sqlite3.connect(self.path, timeout=10)) as database:
            database.row_factory = sqlite3.Row
            database.execute("PRAGMA foreign_keys=ON")
            with database:
                yield database

    def prune(self, database):
        database.execute("DELETE FROM messages WHERE expires < ?", (time.time(),))
        database.execute("DELETE FROM routes WHERE expires < ?", (time.time(),))

    def create(self, token, key_id):
        route, secret = secrets.token_urlsafe(32), secrets.token_urlsafe(32)
        with self.connect() as database:
            database.execute("BEGIN IMMEDIATE")
            self.prune(database)
            total = database.execute("SELECT count(*) FROM routes").fetchone()[0]
            device = database.execute(
                "SELECT count(*) FROM routes WHERE token=?", (token,)
            ).fetchone()[0]
            if total >= 10000 or device >= 32:
                raise OverflowError("Route quota reached")
            database.execute(
                "INSERT INTO routes VALUES (?, ?, ?, ?, 0, ?)",
                (route, digest(secret), token, key_id, int(time.time()) + 86400 * 8),
            )
        return {"id": route, "secret": secret}

    def route(self, route):
        with self.connect() as database:
            self.prune(database)
            return database.execute("SELECT * FROM routes WHERE id=?", (route,)).fetchone()

    def update(self, route, active):
        with self.connect() as database:
            database.execute(
                "UPDATE routes SET active=?, expires=? WHERE id=?",
                (active, int(time.time()) + 86400 * 8, route),
            )

    def delete(self, route):
        with self.connect() as database:
            database.execute("DELETE FROM routes WHERE id=?", (route,))

    def save(self, route, payload, ttl):
        message = secrets.token_urlsafe(32)
        with self.connect() as database:
            database.execute("BEGIN IMMEDIATE")
            self.prune(database)
            count = database.execute(
                "SELECT count(*) FROM messages WHERE route=?", (route,)
            ).fetchone()[0]
            if count >= 32:
                raise OverflowError("Message quota reached")
            database.execute(
                "INSERT INTO messages VALUES (?, ?, ?, ?)",
                (message, route, payload, int(time.time()) + min(ttl, 3600)),
            )
        return message

    def messages(self, route):
        with self.connect() as database:
            self.prune(database)
            return database.execute("SELECT id FROM messages WHERE route=?", (route,)).fetchall()

    def message(self, message):
        with self.connect() as database:
            self.prune(database)
            return database.execute(
                "SELECT payload FROM messages WHERE id=?", (message,)
            ).fetchone()

    def remove_message(self, message):
        with self.connect() as database:
            database.execute("DELETE FROM messages WHERE id=?", (message,))

    def acknowledge(self, route, message):
        with self.connect() as database:
            database.execute("DELETE FROM messages WHERE id=? AND route=?", (message, route))


def digest(secret):
    return hashlib.sha256(secret.encode()).hexdigest()
