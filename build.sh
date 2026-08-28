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
  find "$ROOT/binary128/src/main/java" "$ROOT/binary128/src/test/java" \
       "$ROOT/bid/src/main/java" "$ROOT/bid/src/test/java" \
       -name '*.java' ! -name 'LibraryTests.java' ! -name 'Binary128Test.java' | sort
)

"$JAVAC" --release 17 -Werror -Xlint:all -d "$OUT" "${SOURCES[@]}"
"$JAVA" -ea -cp "$OUT" org.bidfp.binary128.Binary128Check
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
    UInt128Test \
    BidRawApiTest \
    BidComparisonVectorTest \
    BidRoundingVectorTest \
    BidScaleVectorTest \
    BidNextMinMaxVectorTest \
    BidFmaRemVectorTest \
    BidDpdVectorTest \
    BidIntegerVectorTest \
    BidMiscVectorTest \
    BidUtilityVectorTest; do
  "$JAVA" -ea -cp "$OUT" "org.bidfp.$test_class"
done
