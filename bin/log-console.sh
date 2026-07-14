#!/usr/bin/env sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
exec java -jar "$PROJECT_DIR/target/log-console.jar" --config "$PROJECT_DIR/config/log-console.json" "$@"
