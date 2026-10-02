#!/usr/bin/env bash
set +x
set -euo pipefail
release_script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec python3 "$release_script_dir/release_gtr.py" "$@"
