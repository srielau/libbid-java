#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/target/classes"
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
JAVAC="$JAVA_HOME/bin/javac"
JAVA="$JAVA_HOME/bin/java"

rm -rf "$ROOT/target"
mkdir -p "$OUT"

mapfile -t SOURCES < <(
  find "$ROOT/src/main/java" "$ROOT/src/test/java" -name '*.java' \
    ! -name 'LibraryTests.java' | sort
)

"$JAVAC" --release 17 -Werror -Xlint:all -d "$OUT" "${SOURCES[@]}"
for test_class in \
    Bid64Test \
    Bid64IntelVectorTest \
    Bid64CompareTest \
    Bid64ConversionTest \
    Bid64AddTest \
    Bid64MultiplyTest \
    Bid64DivideTest \
    Bid64RawKernelTest \
    Bid128Test \
    Bid128AddTest \
    Bid128MultiplyTest \
    Bid128DivideTest \
    UInt128Test; do
  "$JAVA" -ea -cp "$OUT" "org.bidfp.$test_class"
done
