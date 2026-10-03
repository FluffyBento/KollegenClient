#!/usr/bin/env bash
set -euo pipefail

V="${1#v}"
if [ -z "$V" ]; then
  echo "usage: $0 <version>" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

sed -i "s/^version = .*/version = \"$V\"/" "$ROOT/Cargo.toml"
sed -i "s/\"version\": \"[0-9][^\"]*\"/\"version\": \"$V\"/" "$ROOT/tauri.conf.json"
sed -i "s/\"version\": \"[0-9][^\"]*\"/\"version\": \"$V\"/" "$ROOT/package.json"

if [ -f "$ROOT/Cargo.lock" ]; then
  awk -v v="$V" '
    /^name = "kollegen-client"$/ { found=1 }
    found && /^version = / { sub(/= .*/, "= \"" v "\""); found=0 }
    { print }
  ' "$ROOT/Cargo.lock" > "$ROOT/Cargo.lock.tmp" && mv "$ROOT/Cargo.lock.tmp" "$ROOT/Cargo.lock"
fi

echo "Launcher version set to $V"
