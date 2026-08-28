#!/usr/bin/env bash
# Optional second oracle: Intel C readtest against this repo's readtest.in.
#
# Not part of the default Maven gate. Set INTEL_RDFP_HOME to an unpacked Intel
# RDFP 2.0 Update 4 tree (LIBRARY/ and TESTS/). Requires gcc or clang.
#
#   INTEL_RDFP_HOME=/path/to/IntelRDFPMathLib20U4 ./dev/run_intel_readtest.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VECTORS="$ROOT/upstream/TESTS/readtest.in"
HOME_DIR="${INTEL_RDFP_HOME:-}"

if [[ -z "$HOME_DIR" ]]; then
  echo "INTEL_RDFP_HOME is unset; skipping native Intel readtest."
  exit 0
fi
if [[ ! -d "$HOME_DIR/LIBRARY" || ! -d "$HOME_DIR/TESTS" ]]; then
  echo "INTEL_RDFP_HOME=$HOME_DIR does not contain LIBRARY/ and TESTS/" >&2
  exit 1
fi
if [[ ! -f "$VECTORS" ]]; then
  echo "missing $VECTORS" >&2
  exit 1
fi

CC="${CC:-gcc}"
MAKE="${MAKE:-make}"
# Match this Java port: parameters, not process-global rounding or flags.
BUILD_FLAGS=(
  OS_TYPE=LINUX
  CC="$CC"
  CALL_BY_REF=0
  GLOBAL_RND=0
  GLOBAL_FLAGS=0
  UNCHANGED_BINARY_FLAGS=0
)

echo "Building Intel LIBRARY in $HOME_DIR/LIBRARY"
(
  cd "$HOME_DIR/LIBRARY"
  "$MAKE" "${BUILD_FLAGS[@]}"
)

echo "Building Intel TESTS/readtest"
(
  cd "$HOME_DIR/TESTS"
  "$MAKE" "${BUILD_FLAGS[@]}"
)

READTEST="$HOME_DIR/TESTS/readtest"
if [[ ! -x "$READTEST" ]]; then
  echo "expected executable $READTEST" >&2
  exit 1
fi

echo "Running Intel readtest < $VECTORS"
output="$(mktemp)"
trap 'rm -f "$output"' EXIT
set +e
"$READTEST" < "$VECTORS" > "$output" 2>&1
status=$?
set -e
if grep -q FAIL "$output"; then
  echo "Intel readtest reported FAIL:" >&2
  grep FAIL "$output" >&2
  exit 1
fi
if [[ "$status" -ne 0 ]]; then
  echo "Intel readtest exited $status" >&2
  tail -n 50 "$output" >&2
  exit 1
fi
echo "Intel native readtest: no FAIL (exit $status)"
