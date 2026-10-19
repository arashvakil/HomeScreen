#!/usr/bin/env python3
"""Package an already built, signed release; never uploads anything."""
import argparse
import hashlib
import json
import pathlib
import shutil

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--repo', default='arashvakil/HomeScreen')
parser.add_argument('--notes', default='')
parser.add_argument('--output', type=pathlib.Path, default=pathlib.Path('build/release'))
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parent.parent
metadata_path = root / 'app/build/outputs/apk/release/output-metadata.json'
metadata = json.loads(metadata_path.read_text())
assert metadata['applicationId'] == 'nyc.arash.homescreen.studio', 'Expected public launcher package'
element, = metadata['elements']
apk = metadata_path.parent / element['outputFile']
assert apk.is_file() and 'unsigned' not in apk.name, 'Build a signed release first'
version = element['versionName']
name = f"homescreen-{element['versionCode']}.apk"
args.output.mkdir(parents=True, exist_ok=True)
shutil.copyfile(apk, args.output / name)
manifest = dict(versionCode=element['versionCode'], versionName=version,
                apkUrl=f'https://github.com/{args.repo}/releases/download/v{version}/{name}',
                sha256=hashlib.sha256(apk.read_bytes()).hexdigest(), notes=args.notes)
(args.output / 'update.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(f"Prepared {args.output / name} and {args.output / 'update.json'} (no upload)")
