#!/usr/bin/env sh
set -eu
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi
VERSION=3.9.11
MAVEN_HOME="$BASE_DIR/.mvn/apache-maven-$VERSION"
if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  ARCHIVE="$BASE_DIR/.mvn/apache-maven-$VERSION-bin.tar.gz"
  URL="https://archive.apache.org/dist/maven/maven-3/$VERSION/binaries/apache-maven-$VERSION-bin.tar.gz"
  mkdir -p "$BASE_DIR/.mvn"
  if command -v curl >/dev/null 2>&1; then curl -fL "$URL" -o "$ARCHIVE"
  elif command -v wget >/dev/null 2>&1; then wget -O "$ARCHIVE" "$URL"
  else echo "Maven is missing and neither curl nor wget is available." >&2; exit 1
  fi
  tar -xzf "$ARCHIVE" -C "$BASE_DIR/.mvn"
fi
exec "$MAVEN_HOME/bin/mvn" "$@"
