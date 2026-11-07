import concurrent.futures
import json
import pathlib
import sqlite3
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
import uuid

from service import ApiError, RateLimiter, Server, Store


FAVORITES = ["com.plexapp.android", "org.smarttube.stable"]


def claim():
    return str(uuid.uuid4())


class StoreTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.filename = str(pathlib.Path(self.directory.name) / "sync.sqlite3")
        self.now = 1000
        self.store = Store(self.filename, b"x" * 32, lambda: self.now)

    def tearDown(self):
        self.directory.cleanup()

    def create(self):
        body = {"claimId": claim(), "deviceName": "Astoria", "favorites": FAVORITES}
        return self.store.create(body), body

    def join(self, source):
        body = {"claimId": claim(), "deviceName": "Elizaville", "pairCode": source["pairCode"]}
        return self.store.join(body), body

    def write(self, token, revision, ordered, mutation=None):
        return self.store.update(token, {"revision": revision, "favorites": ordered, "mutationId": mutation or claim()})

    def error(self, status, action, *args):
        with self.assertRaises(ApiError) as caught:
            action(*args)
        self.assertEqual(caught.exception.status, status)
        return caught.exception.body

    def test_pair_and_share_order(self):
        source, _ = self.create()
        joined, _ = self.join(source)
        self.assertNotEqual(source["deviceToken"], joined["deviceToken"])
        result = self.write(joined["deviceToken"], 1, list(reversed(FAVORITES)))
        self.assertEqual(result["revision"], 2)
        self.assertEqual(self.store.get(source["deviceToken"]), result)
        self.assertEqual(source["pairCodeExpiresAt"], 1900)

    def test_create_and_join_retries_are_idempotent(self):
        source, source_body = self.create()
        retry = self.store.create(source_body)
        self.assertEqual(source["deviceToken"], retry["deviceToken"])
        joined, join_body = self.join(retry)
        self.now += 2000
        again = self.store.join(join_body)
        self.assertEqual(joined, again)
        with self.store.connection() as db:
            self.assertEqual(db.execute("SELECT count(*) FROM groups").fetchone()[0], 1)
            self.assertEqual(db.execute("SELECT count(*) FROM devices").fetchone()[0], 2)

    def test_claim_id_cannot_be_reused_for_a_different_request(self):
        _, body = self.create()
        self.error(409, self.store.create, {**body, "favorites": []})

    def test_expired_and_consumed_codes_do_not_create_devices(self):
        source, _ = self.create()
        self.join(source)
        second = {"claimId": claim(), "pairCode": source["pairCode"]}
        self.error(400, self.store.join, second)
        fresh = self.store.pair(source["deviceToken"])
        self.now = fresh["pairCodeExpiresAt"]
        self.error(400, self.store.join, {"claimId": claim(), "pairCode": fresh["pairCode"]})
        with self.store.connection() as db:
            self.assertEqual(db.execute("SELECT count(*) FROM devices").fetchone()[0], 2)

    def test_replacing_code_revokes_older_code(self):
        source, _ = self.create()
        current = self.store.pair(source["deviceToken"])
        self.error(400, self.store.join, {"claimId": claim(), "pairCode": source["pairCode"]})
        self.join(current)

    def test_unlink_revokes_token_claim_and_pending_code(self):
        source, body = self.create()
        joined, join_body = self.join(source)
        code = self.store.pair(joined["deviceToken"])
        self.store.unlink(joined["deviceToken"])
        self.error(401, self.store.get, joined["deviceToken"])
        self.error(410, self.store.join, join_body)
        self.error(400, self.store.join, {"claimId": claim(), "pairCode": code["pairCode"]})
        self.assertEqual(self.store.get(source["deviceToken"])["favorites"], FAVORITES)
        self.store.unlink(source["deviceToken"])
        self.error(410, self.store.create, body)

    def test_revision_conflict_returns_current_order(self):
        source, _ = self.create()
        updated = self.write(source["deviceToken"], 1, list(reversed(FAVORITES)))
        error = self.error(409, self.write, source["deviceToken"], 1, [])
        self.assertEqual(error, {"error": "revision_conflict", **updated})

    def test_concurrent_cas_writers_commit_only_one(self):
        source, _ = self.create()
        other, _ = self.join(source)
        def write(token):
            try:
                self.write(token, 1, ["com.netflix.ninja"])
                return "success"
            except ApiError as error:
                return error.status
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(write, [source["deviceToken"], other["deviceToken"]]))
        self.assertCountEqual(results, ["success", 409])

    def test_mutation_retry_after_other_tv_edits_does_not_reapply(self):
        source, _ = self.create()
        other, _ = self.join(source)
        mutation = claim()
        self.write(source["deviceToken"], 1, list(reversed(FAVORITES)), mutation)
        current = self.write(other["deviceToken"], 2, ["com.netflix.ninja"])
        retry = self.write(source["deviceToken"], 1, list(reversed(FAVORITES)), mutation)
        self.assertEqual(retry, current)
        self.assertEqual(retry["revision"], 3)
        self.error(409, self.write, source["deviceToken"], 1, [], mutation)

    def test_failed_mutation_record_rolls_back_favorites(self):
        source, _ = self.create()
        with self.store.connection() as db:
            db.execute("CREATE TRIGGER fail_mutation BEFORE INSERT ON mutations BEGIN SELECT RAISE(ABORT, 'test rollback'); END")
        with self.assertRaises(sqlite3.IntegrityError):
            self.write(source["deviceToken"], 1, [])
        self.assertEqual(self.store.get(source["deviceToken"]), {"revision": 1, "favorites": FAVORITES})

    def test_persistence_and_hashed_credentials(self):
        source, body = self.create()
        raw = pathlib.Path(self.filename).read_bytes()
        for private in (source["deviceToken"], body["claimId"], source["pairCode"]):
            self.assertNotIn(private.encode(), raw)
        reopened = Store(self.filename, b"x" * 32, lambda: self.now)
        self.assertEqual(reopened.get(source["deviceToken"])["favorites"], FAVORITES)
        self.assertEqual(reopened.create(body)["deviceToken"], source["deviceToken"])

    def test_input_validation_and_deduplication(self):
        bad_lists = [None, "com.plexapp.android", ["not a package"], [1], ["a.b"] * 129]
        for ordered in bad_lists:
            self.error(400, self.store.create, {"claimId": claim(), "favorites": ordered})
        self.error(400, self.store.create, {"claimId": "guessable", "favorites": []})
        self.error(400, self.store.create, {"claimId": claim(), "favorites": [], "deviceName": "\n"})
        source = self.store.create({"claimId": claim(), "favorites": FAVORITES * 2})
        self.assertEqual(source["favorites"], FAVORITES)
        self.error(400, self.store.update, source["deviceToken"], {"revision": True, "favorites": [], "mutationId": claim()})
        self.error(400, self.store.update, source["deviceToken"], {"revision": 1, "favorites": [], "mutationId": "guessable"})


class HttpTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        store = Store(str(pathlib.Path(self.directory.name) / "sync.sqlite3"), b"a" * 32)
        self.server = Server(("127.0.0.1", 0), store)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base = "http://127.0.0.1:" + str(self.server.server_port)

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        self.directory.cleanup()

    def request(self, method, path, body=None, token=None, raw=None):
        headers = {}
        data = raw
        if body is not None:
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        if token:
            headers["Authorization"] = "Bearer " + token
        request = urllib.request.Request(self.base + path, data=data, headers=headers, method=method)
        try:
            response = urllib.request.urlopen(request, timeout=3)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            content = response.read()
            return response.status, json.loads(content) if content else None, response.headers

    def test_http_pair_update_unlink(self):
        status, source, headers = self.request("POST", "/v1/groups", {"claimId": claim(), "favorites": FAVORITES})
        self.assertEqual(status, 200)
        self.assertEqual(headers["Cache-Control"], "no-store")
        status, joined, _ = self.request("POST", "/v1/join", {"claimId": claim(), "pairCode": source["pairCode"]})
        self.assertEqual(status, 200)
        token = joined["deviceToken"]
        status, state, _ = self.request("PUT", "/v1/favorites", {"revision": 1, "mutationId": claim(), "favorites": []}, token)
        self.assertEqual((status, state), (200, {"revision": 2, "favorites": []}))
        status, _, _ = self.request("POST", "/v1/pair-code", token=token)
        self.assertEqual(status, 200)
        status, _, _ = self.request("DELETE", "/v1/device", token=token)
        self.assertEqual(status, 204)
        self.assertEqual(self.request("GET", "/v1/favorites", token=token)[0], 401)

    def test_http_health_auth_body_and_rate_limits(self):
        self.assertEqual(self.request("GET", "/health")[0:2], (200, {"status": "ok"}))
        self.assertEqual(self.request("GET", "/v1/favorites")[0], 401)
        self.assertEqual(self.request("POST", "/v1/groups", raw=b"{}")[0], 415)
        self.assertEqual(self.request("POST", "/v1/groups", {"oversized": "x" * 17000})[0], 413)
        for _ in range(20):
            status, _, _ = self.request("POST", "/v1/join", {"claimId": claim(), "pairCode": "ABCDEFGH"})
            self.assertEqual(status, 400)
        self.assertEqual(self.request("POST", "/v1/join", {"claimId": claim(), "pairCode": "ABCDEFGH"})[0], 429)

    def test_rate_limiter_expires_and_bounds_state(self):
        now = [0]
        limiter = RateLimiter(lambda: now[0])
        for _ in range(10):
            self.assertTrue(limiter.allow("one", "creation"))
        self.assertFalse(limiter.allow("one", "creation"))
        now[0] = 3600
        self.assertTrue(limiter.allow("one", "creation"))
        for value in range(4095):
            self.assertTrue(limiter.allow(str(value), "requests"))
        self.assertFalse(limiter.allow("overflow", "requests"))
        now[0] += 61
        self.assertTrue(limiter.allow("overflow", "requests"))


if __name__ == "__main__":
    unittest.main()
