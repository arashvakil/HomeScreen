#!/usr/bin/env bash
# Explicitly publish prepared assets to an existing tag on the remote default branch.
# No build, SSH, or private server configuration. Run prepare-release.py first.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
REPO="${RELEASE_REPO:-arashvakil/HomeScreen}"
RELEASE_DIR="${RELEASE_DIR:-build/release}"
VERSION="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["versionName"])' "$RELEASE_DIR/update.json")"
APK_NAME="$(python3 -c 'import json,sys; from urllib.parse import urlparse; from pathlib import PurePosixPath; print(PurePosixPath(urlparse(json.load(open(sys.argv[1]))["apkUrl"]).path).name)' "$RELEASE_DIR/update.json")"
python3 - "$RELEASE_DIR/update.json" "$RELEASE_DIR/$APK_NAME" "$REPO" <<'PY'
import hashlib, json, pathlib, sys
manifest = json.loads(pathlib.Path(sys.argv[1]).read_text())
apk = pathlib.Path(sys.argv[2])
assert hashlib.sha256(apk.read_bytes()).hexdigest() == manifest['sha256'], 'APK checksum changed'
assert manifest['apkUrl'] == f"https://github.com/{sys.argv[3]}/releases/download/v{manifest['versionName']}/{apk.name}", 'Unexpected release URL'
PY
# --verify-tag refuses to tag an unreviewed local checkout or invent a remote tag.
gh release create "v$VERSION" "$RELEASE_DIR/$APK_NAME" "$RELEASE_DIR/update.json" \
  --repo "$REPO" --verify-tag --title "HomeScreen $VERSION" \
  --notes-file <(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["notes"])' "$RELEASE_DIR/update.json")
