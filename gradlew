#!/usr/bin/env bash
# Portable Gradle bootstrap for this POC. Unlike the upstream Gradle wrapper,
# this script has no committed gradle-wrapper.jar; it fetches Gradle 8.9 itself.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERSION="8.9"
DIST="$ROOT/.gradle-dist/gradle-$VERSION"
if [[ -n "${GRADLE_BIN:-}" ]]; then
  exec "$GRADLE_BIN" "$@"
fi
if [[ -x "$DIST/bin/gradle" ]]; then
  exec "$DIST/bin/gradle" "$@"
fi
mkdir -p "$ROOT/.gradle-dist"
ZIP="$ROOT/.gradle-dist/gradle-$VERSION-bin.zip"
if [[ ! -s "$ZIP" ]]; then
  echo "Downloading Gradle $VERSION over HTTPS..."
  curl --fail --location --retry 3 --show-error --silent \
    "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$ZIP.tmp"
  mv "$ZIP.tmp" "$ZIP"
fi
unzip -tq "$ZIP" >/dev/null || { echo 'Invalid Gradle download'; rm -f "$ZIP"; exit 1; }
unzip -q -o "$ZIP" -d "$ROOT/.gradle-dist"
exec "$DIST/bin/gradle" "$@"
