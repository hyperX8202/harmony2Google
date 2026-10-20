#!/bin/sh
# macOS / Linux entry point: ./setup.sh <step> [options]   (./setup.sh --help for all steps)
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
for PY in python3 python; do
  if command -v "$PY" >/dev/null 2>&1 && "$PY" -c 'import sys; sys.exit(sys.version_info < (3, 8))' 2>/dev/null; then
    exec "$PY" "$DIR/scripts/aurora_setup.py" "$@"
  fi
done
echo "Python 3.8+ not found. macOS: 'xcode-select --install' or 'brew install python'; Linux: install python3." >&2
exit 1
