#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/target/classes"
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
if [[ ! -x "$JAVA_HOME/bin/javac" ]] && [[ -x /usr/libexec/java_home ]]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 17)"
fi
JAVAC="$JAVA_HOME/bin/javac"
JAVA="$JAVA_HOME/bin/java"

rm -rf "$ROOT/target"
mkdir -p "$OUT"

SOURCES=()
while IFS= read -r source; do
  SOURCES+=("$source")
done < <(
  find "$ROOT/binary128/src/main/java" "$ROOT/bid/src/main/java" \
       "$ROOT/bid/src/test/java" -name '*.java' ! -name 'LibraryTests.java'
  printf '%s\n' \
      "$ROOT/binary128/src/test/java/org/bidfp/binary128/Binary128Check.java"
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
    BidUtilityVectorTest \
    BidObjectApiTest; do
  "$JAVA" -ea -cp "$OUT" "org.bidfp.$test_class"
done
