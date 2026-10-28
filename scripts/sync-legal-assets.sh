#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
dest="$root/app/src/main/assets/legal"

mkdir -p "$dest"
for file in PRIVACY_POLICY.md TERMS_OF_SERVICE.md ACCEPTABLE_USE.md PERMISSIONS_AND_DATA.md; do
    cp "$root/legal/$file" "$dest/$file"
done

printf 'Synced public legal documents to %s\n' "$dest"
