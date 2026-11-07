#!/usr/bin/env python3
"""Account-free TV pairing and revisioned favorites, using only Python's stdlib."""

import argparse
import collections
import contextlib
import hashlib
import hmac
import json
import logging
import os
import re
import secrets
import signal
import sqlite3
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PAIR_LIFETIME = 15 * 60
MAX_BODY = 16 * 1024
MAX_FAVORITES = 128
PACKAGE_ID = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+")
PAIR_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"


class ApiError(Exception):
    def __init__(self, status, error, **details):
        self.status = status
        self.body = {"error": error, **details}


def digest(value):
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def favorites(value):
    if not isinstance(value, list) or len(value) > MAX_FAVORITES:
        raise ApiError(400, "invalid_favorites")
    if any(not isinstance(item, str) or len(item) > 255 or not PACKAGE_ID.fullmatch(item) for item in value):
        raise ApiError(400, "invalid_favorites")
    return list(dict.fromkeys(value))


def claim_id(value, error="invalid_claim_id"):
    try:
        parsed = uuid.UUID(value)
    except (ValueError, TypeError, AttributeError):
        raise ApiError(400, error) from None
    if parsed.version != 4 or str(parsed) != value.lower():
        raise ApiError(400, error)
    return str(parsed)


def device_name(value):
    if value is None:
        return "TV"
    if not isinstance(value, str) or not 1 <= len(value.strip()) <= 64 or any(ord(c) < 32 for c in value):
        raise ApiError(400, "invalid_device_name")
    return value.strip()


class Store:
    def __init__(self, filename, secret, clock=time.time):
        if len(secret) < 32:
            raise ValueError("SYNC_SECRET must contain at least 32 bytes")
        self.filename = filename
        self.secret = secret
        self.clock = clock
        with self.connection() as db:
            db.execute("PRAGMA journal_mode=WAL")
            db.executescript("""
                CREATE TABLE IF NOT EXISTS groups (
                    id TEXT PRIMARY KEY,
                    revision INTEGER NOT NULL,
                    favorites TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS devices (
                    token_hash TEXT PRIMARY KEY,
                    claim_hash TEXT NOT NULL UNIQUE,
                    request_hash TEXT NOT NULL,
                    group_id TEXT NOT NULL REFERENCES groups(id),
                    name TEXT NOT NULL,
                    revoked INTEGER NOT NULL DEFAULT 0
                );
                CREATE TABLE IF NOT EXISTS pairing (
                    code_hash TEXT PRIMARY KEY,
                    group_id TEXT NOT NULL REFERENCES groups(id),
                    creator_hash TEXT NOT NULL REFERENCES devices(token_hash),
                    expires_at INTEGER NOT NULL,
                    consumed_by TEXT
                );
                CREATE TABLE IF NOT EXISTS mutations (
                    device_hash TEXT NOT NULL REFERENCES devices(token_hash),
                    mutation_id TEXT NOT NULL,
                    request_hash TEXT NOT NULL,
                    PRIMARY KEY (device_hash, mutation_id)
                );
                CREATE INDEX IF NOT EXISTS pairing_creator ON pairing(creator_hash);
            """)

    @contextlib.contextmanager
    def connection(self):
        db = sqlite3.connect(self.filename, timeout=10, isolation_level=None)
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA foreign_keys=ON")
        try:
            yield db
        finally:
            db.close()

    @contextlib.contextmanager
    def transaction(self):
        with self.connection() as db:
            db.execute("BEGIN IMMEDIATE")
            try:
                yield db
            except Exception:
                db.rollback()
                raise
            else:
                db.commit()

    def token(self, claim):
        return hmac.new(self.secret, ("device:" + claim).encode(), hashlib.sha256).hexdigest()

    @staticmethod
    def state(db, group):
        row = db.execute("SELECT revision, favorites FROM groups WHERE id=?", (group,)).fetchone()
        return {"revision": row["revision"], "favorites": json.loads(row["favorites"])}

    def authenticated(self, db, token):
        token_hash = digest(token)
        row = db.execute("SELECT * FROM devices WHERE token_hash=?", (token_hash,)).fetchone()
        stored_hash = row["token_hash"] if row is not None else "0" * 64
        matches = hmac.compare_digest(token_hash, stored_hash)
        if not matches or row is None or row["revoked"]:
            raise ApiError(401, "unauthorized")
        return row

    def existing_claim(self, db, claim, request_hash):
        row = db.execute("SELECT * FROM devices WHERE claim_hash=?", (digest(claim),)).fetchone()
        if row is None:
            return None
        if row["revoked"]:
            raise ApiError(410, "device_unlinked")
        if not hmac.compare_digest(row["request_hash"], request_hash):
            raise ApiError(409, "claim_already_used")
        return row

    def issue_code(self, db, group, creator):
        now = int(self.clock())
        db.execute("DELETE FROM pairing WHERE expires_at<=?", (now,))
        db.execute("DELETE FROM pairing WHERE creator_hash=?", (creator,))
        while True:
            code = "".join(secrets.choice(PAIR_ALPHABET) for _ in range(8))
            if db.execute("SELECT 1 FROM pairing WHERE code_hash=?", (digest(code),)).fetchone() is None:
                break
        expires = now + PAIR_LIFETIME
        db.execute("INSERT INTO pairing VALUES (?,?,?,?,NULL)", (digest(code), group, creator, expires))
        return {"pairCode": code, "pairCodeExpiresAt": expires}

    def create(self, body):
        claim = claim_id(body.get("claimId"))
        name = device_name(body.get("deviceName"))
        ordered = favorites(body.get("favorites"))
        request_hash = digest(json.dumps(["create", name, ordered], separators=(",", ":")))
        token = self.token(claim)
        with self.transaction() as db:
            device = self.existing_claim(db, claim, request_hash)
            if device is None:
                group = str(uuid.uuid4())
                db.execute("INSERT INTO groups VALUES (?,1,?)", (group, json.dumps(ordered)))
                db.execute("INSERT INTO devices VALUES (?,?,?,?,?,0)", (digest(token), digest(claim), request_hash, group, name))
            else:
                group = device["group_id"]
            # A retry may issue a fresh code. The group and device remain identical.
            code = self.issue_code(db, group, digest(token))
            return {"deviceToken": token, **self.state(db, group), **code}

    def join(self, body):
        claim = claim_id(body.get("claimId"))
        name = device_name(body.get("deviceName"))
        code = body.get("pairCode")
        if not isinstance(code, str) or len(code) != 8 or any(c not in PAIR_ALPHABET for c in code):
            raise ApiError(400, "invalid_pair_code")
        request_hash = digest(json.dumps(["join", name, code], separators=(",", ":")))
        token = self.token(claim)
        with self.transaction() as db:
            device = self.existing_claim(db, claim, request_hash)
            if device is not None:
                return {"deviceToken": token, **self.state(db, device["group_id"])}
            pairing = db.execute("SELECT * FROM pairing WHERE code_hash=?", (digest(code),)).fetchone()
            if pairing is None or pairing["expires_at"] <= self.clock() or pairing["consumed_by"] is not None:
                raise ApiError(400, "invalid_pair_code")
            group = pairing["group_id"]
            db.execute("INSERT INTO devices VALUES (?,?,?,?,?,0)", (digest(token), digest(claim), request_hash, group, name))
            db.execute("UPDATE pairing SET consumed_by=? WHERE code_hash=?", (digest(claim), digest(code)))
            return {"deviceToken": token, **self.state(db, group)}

    def get(self, token):
        with self.connection() as db:
            device = self.authenticated(db, token)
            return self.state(db, device["group_id"])

    def update(self, token, body):
        revision = body.get("revision")
        if type(revision) is not int or revision < 1:
            raise ApiError(400, "invalid_revision")
        ordered = favorites(body.get("favorites"))
        mutation = claim_id(body.get("mutationId"), "invalid_mutation_id")
        request_hash = digest(json.dumps([revision, ordered], separators=(",", ":")))
        with self.transaction() as db:
            device = self.authenticated(db, token)
            group = device["group_id"]
            prior = db.execute("SELECT request_hash FROM mutations WHERE device_hash=? AND mutation_id=?", (device["token_hash"], mutation)).fetchone()
            if prior is not None:
                if not hmac.compare_digest(prior["request_hash"], request_hash):
                    raise ApiError(409, "mutation_already_used")
                return self.state(db, group)
            state = self.state(db, group)
            if revision != state["revision"]:
                raise ApiError(409, "revision_conflict", **state)
            if ordered != state["favorites"]:
                db.execute("UPDATE groups SET revision=revision+1, favorites=? WHERE id=?", (json.dumps(ordered), group))
            db.execute("INSERT INTO mutations VALUES (?,?,?)", (device["token_hash"], mutation, request_hash))
            return self.state(db, group)

    def pair(self, token):
        with self.transaction() as db:
            device = self.authenticated(db, token)
            return self.issue_code(db, device["group_id"], device["token_hash"])

    def unlink(self, token):
        with self.transaction() as db:
            device = self.authenticated(db, token)
            db.execute("UPDATE devices SET revoked=1 WHERE token_hash=?", (device["token_hash"],))
            db.execute("DELETE FROM pairing WHERE creator_hash=?", (device["token_hash"],))


class RateLimiter:
    """Bound memory while limiting public pairing attempts and request volume."""
    def __init__(self, clock=time.monotonic):
        self.clock = clock
        self.buckets = {}
        self.lock = threading.Lock()

    def allow(self, address, category):
        limit, interval = {"requests": (180, 60), "pairing": (20, 600), "creation": (10, 3600)}[category]
        now = self.clock()
        key = (address, category)
        with self.lock:
            stale = [key for key, value in self.buckets.items() if value[1] <= now]
            for old_key in stale:
                del self.buckets[old_key]
            if key not in self.buckets:
                if len(self.buckets) >= 4096:
                    return False
                self.buckets[key] = (collections.deque(), now + interval)
            events, _ = self.buckets[key]
            while events and events[0] <= now - interval:
                events.popleft()
            if len(events) >= limit:
                return False
            events.append(now)
            self.buckets[key] = (events, now + interval)
            return True


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "HomeScreenSync"
    sys_version = ""

    def log_message(self, format, *args):
        # Pair codes and credentials never appear in URLs or request logs.
        logging.info("request %s %s", self.command, self.path.split("?", 1)[0])

    def setup(self):
        super().setup()
        self.connection.settimeout(10)

    def address(self):
        address = self.client_address[0]
        if address in ("127.0.0.1", "::1"):
            address = self.headers.get("X-Forwarded-For", address)
        return address[:64]

    def body(self):
        if self.headers.get("Transfer-Encoding"):
            raise ApiError(400, "invalid_request")
        if self.headers.get_content_type() != "application/json":
            raise ApiError(415, "json_required")
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            raise ApiError(400, "invalid_request") from None
        if length < 1 or length > MAX_BODY:
            raise ApiError(413, "invalid_body_size")
        try:
            value = json.loads(self.rfile.read(length))
        except (ValueError, UnicodeDecodeError):
            raise ApiError(400, "invalid_json") from None
        if not isinstance(value, dict):
            raise ApiError(400, "invalid_request")
        return value

    def bearer(self):
        auth = self.headers.get("Authorization", "")
        if not auth.startswith("Bearer ") or not re.fullmatch(r"[a-f0-9]{64}", auth[7:]):
            raise ApiError(401, "unauthorized")
        return auth[7:]

    def respond(self, status, value=None):
        content = b"" if value is None else json.dumps(value, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Length", str(len(content)))
        self.send_header("Connection", "close")
        if status == 429:
            self.send_header("Retry-After", "60")
        self.end_headers()
        self.wfile.write(content)
        self.close_connection = True

    def dispatch(self):
        limiter = self.server.limiter
        if not limiter.allow(self.address(), "requests"):
            raise ApiError(429, "rate_limited")
        path = self.path
        store = self.server.store
        if self.command == "GET" and path == "/health":
            with store.connection() as db:
                db.execute("SELECT 1 FROM groups LIMIT 1").fetchone()
            return 200, {"status": "ok"}
        if self.command == "POST" and path in ("/v1/groups", "/v1/join"):
            category = "creation" if path == "/v1/groups" else "pairing"
            if not limiter.allow(self.address(), category):
                raise ApiError(429, "rate_limited")
            result = store.create(self.body()) if path == "/v1/groups" else store.join(self.body())
            return 200, result
        if path == "/v1/favorites" and self.command == "GET":
            return 200, store.get(self.bearer())
        if path == "/v1/favorites" and self.command == "PUT":
            return 200, store.update(self.bearer(), self.body())
        if path == "/v1/pair-code" and self.command == "POST":
            if not limiter.allow(self.address(), "pairing"):
                raise ApiError(429, "rate_limited")
            return 200, store.pair(self.bearer())
        if path == "/v1/device" and self.command == "DELETE":
            store.unlink(self.bearer())
            return 204, None
        raise ApiError(404, "not_found")

    def handle_request(self):
        try:
            status, value = self.dispatch()
        except ApiError as error:
            status, value = error.status, error.body
        except (BrokenPipeError, ConnectionResetError, TimeoutError):
            self.close_connection = True
            return
        except Exception:
            logging.exception("request failed")
            status, value = 500, {"error": "server_error"}
        try:
            self.respond(status, value)
        except (BrokenPipeError, ConnectionResetError, TimeoutError):
            self.close_connection = True

    do_GET = handle_request
    do_POST = handle_request
    do_PUT = handle_request
    do_DELETE = handle_request


class Server(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 32

    def __init__(self, address, store):
        self.store = store
        self.limiter = RateLimiter()
        self.slots = threading.BoundedSemaphore(16)
        super().__init__(address, Handler)

    def process_request(self, request, client_address):
        if not self.slots.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, client_address)
        except Exception:
            self.slots.release()
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self.slots.release()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", default=8787, type=int)
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    filename = os.environ.get("SYNC_DB", "/data/sync.sqlite3")
    secret = os.environ.get("SYNC_SECRET", "").encode()
    os.umask(0o077)
    store = Store(filename, secret)
    server = Server((args.host, args.port), store)
    def stop(*_):
        threading.Thread(target=server.shutdown, daemon=True).start()
    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    try:
        server.serve_forever()
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
