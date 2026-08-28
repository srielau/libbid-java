#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RESULT_ROOT="${RESULT_ROOT:-$ROOT/benchmark-results}"
JAR="$ROOT/bid/target/benchmarks.jar"
INCLUDE='org\.bidfp\..*JmhBenchmark\..*'
REBUILD=false
GC_PROF=auto

usage() {
  echo "Usage: $0 [--rebuild] [--gc|--no-gc] [iter|quick|full] [include] [jmh-args...]" >&2
  echo "  iter   same-JVM, 200ms slices; skip rebuild when the JAR exists" >&2
  echo "  quick  1 fork, 250ms slices; rebuild unless the JAR exists" >&2
  echo "  full   2 forks, 1s slices; always rebuild. Hours for the whole suite" >&2
  echo "  include  JMH regex, e.g. BidJmhBenchmark.bid64Add or BidTranscendental" >&2
  exit 2
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --rebuild) REBUILD=true; shift ;;
    --gc) GC_PROF=yes; shift ;;
    --no-gc) GC_PROF=no; shift ;;
    -h|--help) usage ;;
    --) shift; break ;;
    -*)
      echo "Unknown option: $1" >&2
      usage
      ;;
    *) break ;;
  esac
done

PROFILE="${1:-iter}"
if [[ $# -gt 0 ]]; then
  shift
fi

case "$PROFILE" in
  iter)
    WARMUP_ITERATIONS=1
    WARMUP_TIME=200ms
    MEASUREMENT_ITERATIONS=2
    MEASUREMENT_TIME=200ms
    FORKS=0
    if [[ "$GC_PROF" == "auto" ]]; then
      GC_PROF=no
    fi
    ;;
  quick)
    WARMUP_ITERATIONS=2
    WARMUP_TIME=250ms
    MEASUREMENT_ITERATIONS=3
    MEASUREMENT_TIME=250ms
    FORKS=1
    if [[ "$GC_PROF" == "auto" ]]; then
      GC_PROF=yes
    fi
    ;;
  full)
    WARMUP_ITERATIONS=3
    WARMUP_TIME=1s
    MEASUREMENT_ITERATIONS=5
    MEASUREMENT_TIME=1s
    FORKS=2
    REBUILD=true
    if [[ "$GC_PROF" == "auto" ]]; then
      GC_PROF=yes
    fi
    ;;
  *)
    usage
    ;;
esac

if [[ $# -gt 0 && "$1" != -* ]]; then
  INCLUDE="$1"
  shift
fi

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
SAFE_INCLUDE="$(printf '%s' "$INCLUDE" | tr -c 'A-Za-z0-9._-' '_')"
SAFE_INCLUDE="${SAFE_INCLUDE:0:40}"
RESULT_DIR="$RESULT_ROOT/$TIMESTAMP-$PROFILE-$SAFE_INCLUDE"

JAVA_HOME="${JAVA_HOME:-}"
MVN="${MVN:-mvn}"
if [[ -z "$JAVA_HOME" ]]; then
  JAVA_BIN="$(command -v java)"
else
  JAVA_BIN="$JAVA_HOME/bin/java"
fi

NEED_BUILD=false
if [[ "$REBUILD" == true || ! -f "$JAR" ]]; then
  NEED_BUILD=true
fi

mkdir -p "$RESULT_DIR"

if [[ "$NEED_BUILD" == true ]]; then
  (
    cd "$ROOT"
    "$MVN" -Pjmh -pl bid -am package -DskipTests
  )
else
  echo "Reusing $JAR (pass --rebuild to package again)"
fi

{
  echo "timestamp_utc=$TIMESTAMP"
  echo "profile=$PROFILE"
  echo "include=$INCLUDE"
  echo "rebuild=$NEED_BUILD"
  echo "git_commit=$(git -C "$ROOT" rev-parse HEAD)"
  echo "git_dirty=$(test -n "$(git -C "$ROOT" status --porcelain)" && echo true || echo false)"
  echo "os=$(uname -srvmo)"
  echo "cpu=$(awk -F: '/model name/ {sub(/^[ \t]+/, "", $2); print $2; exit}' /proc/cpuinfo)"
  echo "logical_cpus=$(getconf _NPROCESSORS_ONLN)"
  echo "java_home=${JAVA_HOME:-system}"
  "$JAVA_BIN" -version 2>&1
} > "$RESULT_DIR/environment.txt"

CMD=(
  "$JAVA_BIN" -jar "$JAR"
  "$INCLUDE"
  -wi "$WARMUP_ITERATIONS"
  -w "$WARMUP_TIME"
  -i "$MEASUREMENT_ITERATIONS"
  -r "$MEASUREMENT_TIME"
  -f "$FORKS"
  -jvmArgs "-Xms1g -Xmx1g -XX:+AlwaysPreTouch"
  -rf json
  -rff "$RESULT_DIR/jmh-result.json"
)
if [[ "$GC_PROF" == yes ]]; then
  CMD+=(-prof gc)
fi
CMD+=("$@")

echo "Running: ${CMD[*]}"
"${CMD[@]}" | tee "$RESULT_DIR/jmh-output.txt"

echo "Benchmark results: $RESULT_DIR"
