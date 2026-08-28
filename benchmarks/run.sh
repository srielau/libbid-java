#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROFILE="${1:-quick}"
RESULT_ROOT="${RESULT_ROOT:-$ROOT/benchmark-results}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
RESULT_DIR="$RESULT_ROOT/$TIMESTAMP-$PROFILE"

case "$PROFILE" in
  quick)
    WARMUP_ITERATIONS=2
    WARMUP_TIME=250ms
    MEASUREMENT_ITERATIONS=3
    MEASUREMENT_TIME=250ms
    FORKS=1
    ;;
  full)
    WARMUP_ITERATIONS=3
    WARMUP_TIME=1s
    MEASUREMENT_ITERATIONS=5
    MEASUREMENT_TIME=1s
    FORKS=2
    ;;
  *)
    echo "Usage: $0 [quick|full]" >&2
    exit 2
    ;;
esac

JAVA_HOME="${JAVA_HOME:-}"
MVN="${MVN:-mvn}"
if [[ -z "$JAVA_HOME" ]]; then
  JAVA_BIN="$(command -v java)"
else
  JAVA_BIN="$JAVA_HOME/bin/java"
fi

mkdir -p "$RESULT_DIR"

(
  cd "$ROOT"
  "$MVN" -Pjmh -pl bid -am clean package -DskipTests
)

{
  echo "timestamp_utc=$TIMESTAMP"
  echo "profile=$PROFILE"
  echo "git_commit=$(git -C "$ROOT" rev-parse HEAD)"
  echo "git_dirty=$(test -n "$(git -C "$ROOT" status --porcelain)" && echo true || echo false)"
  echo "os=$(uname -srvmo)"
  echo "cpu=$(awk -F: '/model name/ {sub(/^[ \t]+/, "", $2); print $2; exit}' /proc/cpuinfo)"
  echo "logical_cpus=$(getconf _NPROCESSORS_ONLN)"
  echo "java_home=${JAVA_HOME:-system}"
  "$JAVA_BIN" -version 2>&1
} > "$RESULT_DIR/environment.txt"

"$JAVA_BIN" -jar "$ROOT/bid/target/benchmarks.jar" \
  'org\.bidfp\..*JmhBenchmark\..*' \
  -wi "$WARMUP_ITERATIONS" \
  -w "$WARMUP_TIME" \
  -i "$MEASUREMENT_ITERATIONS" \
  -r "$MEASUREMENT_TIME" \
  -f "$FORKS" \
  -jvmArgs "-Xms1g -Xmx1g -XX:+AlwaysPreTouch" \
  -prof gc \
  -rf json \
  -rff "$RESULT_DIR/jmh-result.json" \
  | tee "$RESULT_DIR/jmh-output.txt"

echo "Benchmark results: $RESULT_DIR"
