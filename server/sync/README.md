# Favorites sync service

Python 3.11+ and SQLite provide account-free pairing, private device credentials,
and a shared ordered favorites list. There are no third-party Python dependencies.
Weather and app sign-ins stay on each TV.

## Run and verify locally

```sh
python3 -m unittest discover -s server/sync -v
SYNC_DB=/tmp/homescreen-sync.sqlite3 SYNC_SECRET="$(python3 -c 'import secrets; print(secrets.token_hex(32))')" \
  python3 server/sync/service.py --host 127.0.0.1 --port 8787
```

Keep a stable random `SYNC_SECRET` of at least 32 bytes in the production environment.
Each client's random UUID claim derives a device credential through HMAC, allowing
safe create/join retries after a lost response. The database stores hashes of device
tokens, claim IDs, pairing codes, and mutation payloads. The secret must be backed up
with the database to preserve retry behavior. Never commit either to Git.

## HTTP API

The public base URL is `https://homescreen.arash.nyc/sync/v1`. Nginx strips `/sync`
before forwarding requests. JSON responses always have `Cache-Control: no-store`.
Device credentials are sent in `Authorization: Bearer <deviceToken>`.

| Method and relative route | Request JSON | Response JSON |
| --- | --- | --- |
| POST `/groups` | `claimId`, `deviceName`, `favorites` | `deviceToken`, `revision`, `favorites`, `pairCode`, `pairCodeExpiresAt` |
| POST `/join` | `claimId`, `deviceName`, `pairCode` | `deviceToken`, `revision`, `favorites` |
| GET `/favorites` | None, authenticated | `revision`, `favorites` |
| PUT `/favorites` | `revision`, `favorites`, `mutationId`, authenticated | `revision`, `favorites` |
| POST `/pair-code` | None, authenticated | `pairCode`, `pairCodeExpiresAt` |
| DELETE `/device` | None, authenticated | Empty HTTP 204 |

`claimId` and `mutationId` are random canonical UUIDv4 strings. The client retains a
claim until registration succeeds, and retains each exact mutation request until it
receives a response. IDs must never be shared between devices. `deviceName` is
optional, defaults to `TV`, and accepts at most 64 printable characters. `favorites`
is an ordered array of at most 128 package identifiers, deduplicated in order.

Pairing codes use eight uppercase characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`,
expire after 15 minutes, and can link one TV. Generating a new code invalidates the
creator's previous code. `pairCodeExpiresAt` uses Unix epoch seconds.

PUT uses compare-and-swap against the group revision. A revision conflict returns
HTTP 409 with `{ "error": "revision_conflict", "revision": ..., "favorites": [...] }`.
It does not record the mutation. The client can rebase its local edits onto that
snapshot and submit a new mutation. Successfully committed mutations are recorded
atomically with the favorites change. Retrying the same mutation returns the current
group snapshot, including any subsequent changes from another TV, without applying
the old write again. Reusing a mutation with different content returns HTTP 409
`mutation_already_used`. A no-op update retains the revision but records the mutation.

Create/join retry with the same claim and request content retains the device token.
Create may return a fresh pairing code on retry. A claim used with different content
returns HTTP 409 `claim_already_used`. Unlink revokes the token and that device's
pending codes. The revoked claim cannot register again, returning HTTP 410
`device_unlinked`. A newly linked setup uses a fresh claim UUID.

Other errors contain an `error` string. Invalid/expired/consumed pairing codes return
HTTP 400 `invalid_pair_code`. Missing or revoked device tokens return HTTP 401
`unauthorized`. Body size is limited to 16 KiB. Request volume, creation, and pairing
attempts are independently limited per client address. Rate limit failures return
HTTP 429 with a `Retry-After` header. Client address comes from the socket, except a
loopback reverse proxy may supply `X-Forwarded-For`. Nginx must overwrite that header
with `$remote_addr`, as in the included template. This service must stay bound to
loopback and be served publicly through HTTPS.

`GET /health` on the internal service, or public `/sync/health`, checks database
access and returns `{ "status": "ok" }`. It reveals no linked-device information.

## Production service

Deploy the merged `service.py` to `/opt/homescreen-sync/` and the included systemd
unit to `/etc/systemd/system/homescreen-sync.service`. The template uses a dedicated
`homescreen-sync` user/group, Python `/usr/bin/python3.11`, port 8787, a systemd-managed
private state directory, a 128 MiB memory limit, 25% CPU quota, and 32-task limit.
The service itself caps concurrent request threads at 16 and times out idle sockets.
`/etc/homescreen-sync.env` must be root-owned mode 0600 and contain the stable random
`SYNC_SECRET`. `SYNC_DB` defaults in the unit to
`/var/lib/homescreen-sync/sync.sqlite3`.

Include `nginx-location.conf` inside the existing homescreen host's server block.
Validate with `nginx -t` before reloading only Nginx. Confirm the deployed service's
`/health` and perform real create, join, update, conflict, and unlink requests over
the public HTTPS URL using a disposable pair of devices. Service installation and
public configuration are deployment actions.

SQLite uses WAL and transactional writes. Back up through SQLite's online backup
API or `.backup` command. Copying only the live main database file can omit WAL
transactions. Keep backup files private, retain the service secret separately, and
verify a restored copy. Never expose the database directory through the web server.
Unlinked device tombstones and committed mutation IDs are retained to prevent replay.
