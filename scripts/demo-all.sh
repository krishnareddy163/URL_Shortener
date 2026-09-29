#!/usr/bin/env bash
# Runs the four scenario demos in sequence.
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"$DIR/demo-greenfield.sh"
"$DIR/demo-brownfield.sh"
"$DIR/demo-ambiguous.sh"
"$DIR/demo-bugfix.sh"
