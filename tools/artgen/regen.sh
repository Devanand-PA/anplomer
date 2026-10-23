#!/bin/sh
# Regenerate every generated art class listed in art/art.json.
#
#   tools/artgen/regen.sh            # write the .java files
#   tools/artgen/regen.sh --check    # report only, non-zero on warnings
#   tools/artgen/regen.sh --json     # machine-readable summary
#
# Runs from any working directory. Needs Python 3 only.
set -eu

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)

manifest="$root/art/art.json"

if [ ! -f "$manifest" ]; then
    echo "regen: no manifest at art/art.json (nothing to generate)" >&2
    exit 0
fi

exec python3 "$here/svg2java.py" --manifest "$manifest" "$@"
