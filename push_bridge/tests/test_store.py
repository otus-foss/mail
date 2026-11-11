import sqlite3
import tempfile
import time
import unittest
from unittest.mock import patch

from push_bridge import store
from push_bridge.store import Store

TOKEN = "ab" * 32
KEY = "12345678-1234-1234-1234-123456789012"


def activate(target, route):
    """Delivers and acknowledges a verification push, then activates the route."""
    target.acknowledge(route, target.save(route, b"x", 60, active=False))
    target.update(route, True)


class StoreTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = self.directory.name + "/push.sqlite"
        self.store = Store(self.path)

    def later(self, seconds):
        return patch("push_bridge.store.time.time", return_value=time.time() + seconds)

    def test_unverified_registrations_free_the_quota_within_an_hour(self):
        with patch.object(store, "MAX_ROUTES", 3):
            kept = self.store.create(TOKEN, KEY)
            activate(self.store, kept["id"])
            self.store.create("cd" * 32, KEY)
            self.store.create("ef" * 32, KEY)
            with self.assertRaises(OverflowError):
                self.store.create("01" * 32, KEY)
            with self.later(store.INACTIVE_ROUTE_LIFETIME + 1):
                self.store.create("01" * 32, KEY)
                self.assertIsNotNone(self.store.route(kept["id"]), "An active route lives on")

    def test_an_active_route_lives_until_it_is_renewed(self):
        route = self.store.create(TOKEN, KEY)["id"]
        activate(self.store, route)
        with self.later(store.ROUTE_LIFETIME - 60):
            self.assertIsNotNone(self.store.route(route))
        with self.later(store.ROUTE_LIFETIME + 1):
            self.assertIsNone(self.store.route(route))

    def test_a_route_without_a_delivered_push_stays_inactive(self):
        route = self.store.create(TOKEN, KEY)["id"]
        self.store.update(route, True)
        self.assertFalse(self.store.route(route)["active"])
        with self.later(store.INACTIVE_ROUTE_LIFETIME + 1):
            self.assertIsNone(self.store.route(route))

    def test_routes_active_before_delivery_tracking_stay_active(self):
        path = self.directory.name + "/old.sqlite"
        with sqlite3.connect(path) as database:
            database.execute(
                "CREATE TABLE routes (id TEXT PRIMARY KEY, secret TEXT, token TEXT, key_id TEXT,"
                " active INTEGER DEFAULT 0, expires INTEGER)"
            )
            database.execute("INSERT INTO routes VALUES ('kept', '', ?, ?, 1, 0)", (TOKEN, KEY))
        database.close()
        upgraded = Store(path)
        upgraded.update("kept", True)
        self.assertTrue(upgraded.route("kept")["active"])

    def test_an_inactive_route_holds_only_a_few_messages(self):
        route = self.store.create(TOKEN, KEY)["id"]
        for _ in range(store.MAX_INACTIVE_MESSAGES):
            self.store.save(route, b"x", 60, active=False)
        with self.assertRaises(OverflowError):
            self.store.save(route, b"x", 60, active=False)

    def test_expired_rows_are_never_served_before_they_are_pruned(self):
        route = self.store.create(TOKEN, KEY)["id"]
        message = self.store.save(route, b"x", 60, active=False)
        with self.later(120):
            self.assertEqual(self.store.messages(route), [])
            self.assertIsNone(self.store.message(message))
        with self.store.connect() as database:
            count = database.execute("SELECT count(*) FROM messages").fetchone()[0]
        self.assertEqual(count, 1, "Reading deletes nothing")

    def test_pruning_runs_at_most_once_per_interval_and_uses_the_expiry_index(self):
        route = self.store.create(TOKEN, KEY)["id"]
        self.store.save(route, b"x", 1, active=False)
        with self.later(10):
            self.store.save(route, b"y", 60, active=False)
        with self.store.connect() as database:
            self.assertEqual(database.execute("SELECT count(*) FROM messages").fetchone()[0], 2)
        with self.later(store.PRUNE_INTERVAL + 1):
            self.store.save(route, b"z", 60, active=False)
        with self.store.connect() as database:
            self.assertEqual(database.execute("SELECT count(*) FROM messages").fetchone()[0], 2)
            for table in ("messages", "routes"):
                plan = database.execute(
                    f"EXPLAIN QUERY PLAN DELETE FROM {table} WHERE expires < ?", (0,)
                ).fetchall()
                self.assertIn(f"{table}_expires", " ".join(row[3] for row in plan))

    def test_an_existing_database_gains_the_indexes(self):
        path = self.directory.name + "/old.sqlite"
        with sqlite3.connect(path) as database:
            database.executescript("""
                CREATE TABLE routes (id TEXT PRIMARY KEY, secret TEXT, token TEXT, key_id TEXT,
                    active INTEGER DEFAULT 0, expires INTEGER);
                CREATE TABLE messages (id TEXT PRIMARY KEY, payload BLOB, expires INTEGER,
                    route TEXT REFERENCES routes(id) ON DELETE CASCADE);
            """)
        database.close()
        with Store(path).connect() as database:
            names = {row[0] for row in database.execute("SELECT name FROM sqlite_master")}
        self.assertTrue({"messages_expires", "routes_expires", "routes_token"} <= names)
