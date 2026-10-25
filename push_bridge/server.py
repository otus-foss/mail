"""Gunicorn entry point; all deployment settings come from the environment."""

import os
from pathlib import Path

from push_bridge.apns import Apns
from push_bridge.application import Application
from push_bridge.store import Store

os.umask(0o077)
database = Path(os.environ.get("OTUS_PUSH_DATABASE", "/data/push.sqlite"))
database.parent.mkdir(parents=True, exist_ok=True)
application = Application(
    Store(str(database)),
    Apns(
        os.environ["APNS_KEY_FILE"],
        os.environ["APNS_KEY_ID"],
        os.environ["APNS_TEAM_ID"],
        os.environ["APNS_TOPIC"],
        sandbox=os.environ.get("APNS_ENVIRONMENT", "production") == "sandbox",
    ),
)
