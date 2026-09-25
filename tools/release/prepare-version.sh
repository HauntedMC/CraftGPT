#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
[[ $# -eq 1 && "$1" =~ ^(major|minor|patch)$ ]] || { echo 'Usage: prepare-version.sh major|minor|patch' >&2; exit 2; }
python3 - "$1" <<'PYVERSION'
from pathlib import Path
from datetime import datetime, timezone
import re
import sys
kind = sys.argv[1]
pom = Path('pom.xml')
content = pom.read_text()
match = re.search(r'<revision>(\d+)\.(\d+)\.(\d+)</revision>', content)
if not match:
    raise SystemExit('Missing semantic revision in pom.xml')
old = match.group(0).removeprefix('<revision>').removesuffix('</revision>')
parts = list(map(int, match.groups()))
index = {'major': 0, 'minor': 1, 'patch': 2}[kind]
parts[index] += 1
for i in range(index + 1, 3):
    parts[i] = 0
new = '.'.join(map(str, parts))
def replace(path, before, after):
    value = path.read_text()
    if value.count(before) != 1:
        raise SystemExit(f'{path}: expected exactly one {before!r}')
    path.write_text(value.replace(before, after))
replace(pom, f'<revision>{old}</revision>', f'<revision>{new}</revision>')
replace(pom, f'<tag>v{old}</tag>', f'<tag>v{new}</tag>')
timestamp = datetime.now(timezone.utc).strftime('%Y-%m-%dT00:00:00Z')
content = pom.read_text()
match = re.search(r'<project\.build\.outputTimestamp>([^<]+)</project\.build\.outputTimestamp>', content)
if not match:
    raise SystemExit('Missing reproducible-build timestamp')
replace(pom, match.group(0), f'<project.build.outputTimestamp>{timestamp}</project.build.outputTimestamp>')
print(f'Prepared {old} -> {new}')
PYVERSION
git diff --check
