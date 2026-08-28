# libbid-java

Pure-Java IEEE 754 **BID decimal64 and decimal128** arithmetic. Java 17, no
native code, no `BigDecimal` on the arithmetic path.

This is **not** Intel's C libbid, and it is **not** an Apache Spark module.
Spark (or anyone else) can depend on this jar later. Decimal256 is out of
scope for this first release.

BID64 and BID128 kernels are a Java port of Intel's Decimal Floating-Point
Math Library (RDFP 2.0 Update 4) under BSD-3-Clause. Original Java packaging
and tests are Apache License 2.0. Keep `LICENSE`, `LICENSE-INTEL`, and
`NOTICE` together.

## Status

- Representation, classification, canonicality, sign ops
- Quiet/signaling comparisons, `totalOrder`, `sameQuantum`
- Add, subtract, multiply, divide, FMA, remainder, sqrt, quantize, scale
- Round-to-integral, nextUp/nextDown, minnum/maxnum
- String, integer, binary32/64, BID64<->BID128, and BID<->binary128 conversion
- DPD encode/decode (BID64 and BID128)
- Intel `readtest.in` coverage for the core BID64/BID128 operation families above
  plus exact-bit BID<->binary128 convert (6664 vectors)
- Add/sub/mul/div: every named BID64/BID128 line in `readtest.in` (2461), hex
  and decimal-text operands, bit and flag equality, object API vs raw kernel
- Transcendentals: convert to binary128, DPML kernel, convert back, plus Intel
  wrapper specials (exp clamps, near-1 log, Payne-Hanek trig, hypot, domain).
  `BidTranscendentalVectorTest` passes all 5448 Intel libm vectors using
  Intel's per-vector ULP offsets and INVALID/DIVBYZERO flag rules.
- DBR adapters: Compare/Equals, Sign, RoundToScale, Canonicalize, Decimal
- Dual API: `Bid64`/`Bid128` objects and `Bid64Raw`/`Bid128Raw`, plus
  `DecFloat16Compat`/`DecFloat34Compat` JNI-shaped methods; the object and raw
  APIs both expose the complete in-scope transcendental set
- Java interop: exact or rounded `BigDecimal` conversion and
  `Comparable` ordering via IEEE 754 `totalOrder`

This git tree builds two JARs:

- `org.bidfp:libbid-java` (`bid/`) - BID64/BID128
- `org.bidfp:binary128` (`binary128/`) - the bounded packed DPML engine used
  by BID transcendentals

Java users seeking the libbid API should depend on `libbid-java`, which pulls
`binary128` transitively. The binary128 artifact is not a complete
general-purpose IEEE binary128 library; it contains the representation,
arithmetic, and Intel `bid_f128_*` kernel families needed by this port. Its
supported surface and limitations are documented in `binary128/README.md`.

Out of scope: BID32, BID256, binary80, and mixed-width arithmetic.

As in Intel's `readtest.c`, BID64/BID128 transcendental compatibility covers
the INVALID and DIVBYZERO status bits. Intel's vectors contain contradictory
INEXACT, OVERFLOW, and UNDERFLOW expectations for identical calls and its test
driver deliberately masks those bits. This port therefore does not specify
those three status bits for transcendental operations. Core arithmetic and
conversion operations continue to report their full tested status.

`Bid64` and `Bid128` deliberately do not extend `java.lang.Number`.
`Number` conversions provide no rounding-mode or status channel, while this
library keeps both explicit. Use `toLong`, `toFloat`, `toDouble`, or
`toBigDecimal` and their corresponding factories instead.

## Build and test

Java 17+ and Maven. The default gate is `mvn test` (also `./build.sh`).
That runs JUnit 5 in both modules, including binary128 DPML oracles and
`Bid128TgammaBoundaryTest`. GitHub Actions runs the same command.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64   # if needed
mvn test
# or: ./build.sh
```

`./build.sh javac` is a Maven-free smoke path: it compiles with `javac` and
runs the `main()` suites only (no JUnit 5).

Optional native second oracle (Intel C `readtest` vs `upstream/TESTS/readtest.in`):

```bash
INTEL_RDFP_HOME=/path/to/IntelRDFPMathLib20U4 ./dev/run_intel_readtest.sh
```

## JMH (optional)

```bash
benchmarks/run.sh quick
benchmarks/run.sh full
```

Workloads compare BID64/BID128 with `MathContext.DECIMAL64` /
`DECIMAL128` on equivalent operands and capture JSON plus environment metadata.
See `benchmarks/README.md`. The binary128 suite covers arithmetic and public
DPML kernel families:

```bash
mvn -Pjmh -pl binary128 package -DskipTests
$JAVA_HOME/bin/java -jar binary128/target/binary128-benchmarks.jar \
  '.*Binary128JmhBenchmark.*'
```

## Coordinates

```xml
<dependency>
  <groupId>org.bidfp</groupId>
  <artifactId>libbid-java</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Package: `org.bidfp`. This artifact is not published to Maven Central yet.
The `org.bidfp` groupId will need a matching namespace (domain or Central
verification) before a Central release. Do not publish as `org.apache.spark`.

## Release

The `release` Maven profile attaches source and javadoc JARs, signs every
artifact, and uploads through Sonatype's Central Portal plugin. Publishing is
manual after Central validates the upload.

Before the first release:

1. Verify ownership of the `org.bidfp` namespace in Central Portal.
2. Configure a `central` server token in the user's Maven `settings.xml`.
3. Configure a GPG signing key without storing credentials in this repository.
4. Replace the `SNAPSHOT` version with the intended release version.
5. Run `mvn -Prelease deploy`, review the validated deployment in Central, and
   publish it there.

## Porting rules

1. Port only from Intel's BSD-3-Clause RDFP distribution. Do not use GCC's
   GPL copy of libbid.
2. Keep the Intel notice on substantially ported source files and retain
   `LICENSE-INTEL`.
3. Preserve BID64 and BID128 encodings exactly.
4. Use two's-complement Java `long` limbs. Do not implement arithmetic with
   `BigDecimal`.
5. Treat Intel `upstream/TESTS/readtest.in` as the correctness oracle.
6. Keep rounding and IEEE status flags explicit. No process-global arithmetic
   state.

## License

Apache License 2.0 for the Java project, plus Intel BSD-3-Clause for the
ported RDFP algorithms and test vectors. See `LICENSE`, `LICENSE-INTEL`, and
`NOTICE`.
