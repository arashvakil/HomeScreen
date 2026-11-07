# Host your own favorites sync service

HomeScreen’s public builds start with favorites sync unconfigured. Deploy this
service on a server you control and build HomeScreen with that service’s HTTPS URL.
Each household runs its own service and database.

Python 3.11+ and SQLite provide pairing codes, private device credentials, and a
shared ordered favorites list. There are no third-party Python dependencies.
Weather and app sign-ins stay on each TV.

## Deploy the service

1. Choose a server with Python 3.11+, systemd, Nginx, and a hostname with a valid
   HTTPS certificate. The examples use `sync.example.com`. Keep the application
   bound to loopback so Nginx provides the public HTTPS connection.
2. Copy this directory from your checkout to the server, then install the service.
   Adjust the Python executable in `homescreen-sync.service` if your host uses a
   different path.

   ```sh
   id homescreen-sync >/dev/null 2>&1 || sudo useradd --system --user-group --home-dir /var/lib/homescreen-sync --shell /sbin/nologin homescreen-sync
   sudo install -d -m 755 /opt/homescreen-sync
   sudo install -m 644 service.py /opt/homescreen-sync/service.py
   sudo install -m 644 homescreen-sync.service /etc/systemd/system/homescreen-sync.service
   sudo install -m 644 nginx-location.conf /etc/nginx/homescreen-sync-location.inc
   ```

3. Generate a stable service secret in a root-owned environment file. This command
   creates the file once and refuses to overwrite an existing secret.

   ```sh
   sudo python3 - <<'PYTHON'
   import os, secrets
   fd = os.open('/etc/homescreen-sync.env', os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
   with os.fdopen(fd, 'w') as f:
       f.write('SYNC_SECRET=' + secrets.token_hex(32) + '\n')
       f.write('SYNC_ALLOW_GROUP_CREATION=true\n')
   PYTHON
   sudo systemctl daemon-reload
   sudo systemctl enable --now homescreen-sync
   curl --fail http://127.0.0.1:8787/health
   ```

   The unit creates a private `/var/lib/homescreen-sync/` directory and stores the
   SQLite database there. It limits the process to 128 MiB RAM, 25% CPU, and 32
   tasks. The service accepts at most 16 concurrent request threads. If the first
   health request precedes startup, retry after the service reports running.

4. Include the supplied location file in your HTTPS Nginx server block. Use your
   hostname and certificate paths.

   ```nginx
   server {
       listen 443 ssl;
       server_name sync.example.com;
       ssl_certificate /etc/ssl/your-sync-host/fullchain.pem;
       ssl_certificate_key /etc/ssl/your-sync-host/privkey.pem;
       include /etc/nginx/homescreen-sync-location.inc;
   }
   ```

   Validate with `sudo nginx -t`, reload Nginx, and verify the HTTPS route.

   ```sh
   sudo systemctl reload nginx
   curl --fail https://sync.example.com/sync/health
   ```

   The response should be `{"status":"ok"}`. The template disables caching for
   the API and forwards the client address for rate limits. If Nginx is behind a
   trusted proxy, configure its real-client-IP handling so limits apply per client.

## Build and pair your TVs

Use JDK 17 and Android SDK 34. Follow the root README’s [signing instructions](../../README.md#build-and-publish)
with your own local signing key, then build with your service URL.

```sh
./gradlew :app:assembleRelease -PfavoritesSyncUrl=https://sync.example.com/sync/v1
```

Install your signed APK on each TV using `adb install -r`. Android requires the
same application ID and signing key for an update. Keep your signing key for future
builds. For updates to custom-signed builds, set
`-PupdateManifestUrl=https://updates.example.com/update.json` and publish matching
APKs and manifests through your own update channel, as described in the root README.
A custom-signed build needs its own application ID or an initial installation
on a TV that does not already have a differently signed build of that package.

On the first TV, open **Home Settings → Favorites sync → Share this TV’s favorites**.
On another TV, choose **Join an existing setup** and enter the displayed code. The
code expires after 15 minutes and works once. Joining adopts the shared list.

The TV remembers the service URL when it uses the configured service. Linked TVs
retain that saved endpoint through updates, including updates built with an empty
default URL. To switch to another service, unlink the TV first, then install a build
configured for the new URL. Wait for an offline unlink to finish before switching.

Favorites and their order sync while Home is visible, on returning Home, and
periodically in the background. Offline edits are saved locally and merged when
connected. Weather settings stay local to each TV.

## Close new setup creation

After your TVs are paired, set `SYNC_ALLOW_GROUP_CREATION=false` in
`/etc/homescreen-sync.env`, then restart only this service.

```sh
sudo systemctl restart homescreen-sync
```

New requests to create a separate shared setup then receive HTTP 403
`setup_disabled`. Existing TVs continue syncing. They can invite another TV by
creating a pairing code, and retries of an already committed setup request still
recover the original device link. Return the setting to `true` when you need to
create a new setup. The default is `true` for a new self-hosted service. Values must
be exactly `true` or `false`, ignoring case.

## Back up and recover

Keep the database and stable `SYNC_SECRET` in private backups. The secret derives
retry credentials from each random UUID claim. The database stores hashes of tokens,
claim IDs, pairing codes, and mutation payloads. Keep both files outside Git and
outside the directory served by Nginx.

SQLite uses WAL. Use its online backup API or `.backup` command to capture committed
writes, then back up that snapshot together with `/etc/homescreen-sync.env`.
For example, run this on the server with a private destination directory.

```sh
sudo sqlite3 /var/lib/homescreen-sync/sync.sqlite3 '.backup /var/lib/homescreen-sync/sync-backup.sqlite3'
sudo chmod 600 /var/lib/homescreen-sync/sync-backup.sqlite3
```

Verify backups with `PRAGMA integrity_check`. To restore, stop the service, restore
the matching database and environment file, give the database directory to the
`homescreen-sync` user, and start the service again. Unlinked-device tombstones and
committed mutation IDs are retained to prevent replay.

## Run tests locally

```sh
python3 -m unittest discover -s server/sync -v
```

## HTTP API


For the deployment below, the base URL is `https://sync.example.com/sync/v1`. Nginx strips `/sync`
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

When new setup creation is closed, a new claim returns HTTP 403 `setup_disabled`.
An existing successful claim can still be retried with its original request.

Other errors contain an `error` string. Invalid/expired/consumed pairing codes return
HTTP 400 `invalid_pair_code`. Missing or revoked device tokens return HTTP 401
`unauthorized`. Body size is limited to 16 KiB. Request volume, creation, and pairing
attempts are independently limited per client address. Rate limit failures return
HTTP 429 with a `Retry-After` header. Client address comes from the socket, except a
loopback reverse proxy may supply `X-Forwarded-For`. Nginx must overwrite that header
with `$remote_addr`, as in the included template. This service must stay bound to
loopback and be served publicly through HTTPS.

`GET /health` on the internal service, or HTTPS `/sync/health`, checks database
access and returns `{ "status": "ok" }`. It reveals no linked-device information.
