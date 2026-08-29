# libbid-java

IEEE 754 **decimal64** and **decimal128** arithmetic in BID encoding, in
pure Java 17. There is no native code. Arithmetic does not use
`BigDecimal`.

The kernels are a Java implementation of Intel's Decimal Floating-Point
Math Library (RDFP 2.0 Update 4). That C library is a separate product.

**Out of scope:** BID32.

## Capabilities

- Classification, canonicality, and sign operations
- Quiet and signaling comparisons, `totalOrder`, and `sameQuantum`
- Add, subtract, multiply, divide, FMA, remainder, sqrt, quantize, and scale
- Round-to-integral, nextUp/nextDown, and minnum/maxnum
- Conversion to and from strings, integers, binary32/64, BID64/BID128, and
  binary128
- DPD encode and decode for BID64 and BID128
- Transcendentals via BID-to-binary128 conversion, Intel DPML kernels, and
  conversion back, including Intel wrapper specials (exp clamps, near-1 log,
  Payne-Hanek trig, hypot, and domain handling)
- Object API (`Bid64`, `Bid128`), raw bit API (`Bid64Raw`, `Bid128Raw`), and
  JNI-shaped `DecFloat16Compat` / `DecFloat34Compat` methods
- `BigDecimal` conversion and `Comparable` order via IEEE 754 `totalOrder`

`Bid64` and `Bid128` do not extend `java.lang.Number`. `Number` conversions
have no rounding-mode or status channel; this library keeps both explicit.
Use `toLong`, `toFloat`, `toDouble`, or `toBigDecimal` and the matching
factories.

Transcendental operations specify INVALID and DIVBYZERO, matching Intel's
`readtest.c`. Intel's libm vectors disagree on INEXACT, OVERFLOW, and
UNDERFLOW for identical calls, and Intel's driver masks those bits. This
library does not specify those three bits on transcendentals. Arithmetic and
conversion report their full tested status.

## Artifacts

| Maven coordinate | Module | Contents |
| --- | --- | --- |
| `org.bidfp:libbid-java` | `bid/` | BID64 and BID128 |
| `org.bidfp:binary128` | `binary128/` | Packed binary128 and DPML kernels used by BID transcendentals |

Depend on `libbid-java`; it pulls `binary128` transitively. The binary128
artifact is the packed DPML engine (representation, arithmetic, and
`bid_f128_*` kernel families), not a general-purpose IEEE binary128 language
binding. See `binary128/README.md`.

```xml
<dependency>
  <groupId>org.bidfp</groupId>
  <artifactId>libbid-java</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Package: `org.bidfp`. The artifacts are not on Maven Central.

## Tests

`mvn test` (or `./build.sh`) is the default gate. It runs JUnit 5 in both
modules.

Intel `upstream/TESTS/readtest.in` is the correctness oracle: 6664 vectors
for core BID64/BID128 families and exact-bit BID-binary128 convert; 2461
named add/sub/mul/div lines (hex and decimal-text, bits and flags, object
vs raw); 5448 transcendental vectors with Intel per-vector ULP offsets and
INVALID/DIVBYZERO rules.

```bash
mvn test
```

Java 17+ and Maven are required. `./build.sh javac` compiles with `javac`
and runs `main()` suites only (no JUnit 5).

Optional native oracle (Intel C `readtest` against `upstream/TESTS/readtest.in`):

```bash
INTEL_RDFP_HOME=/path/to/IntelRDFPMathLib20U4 ./dev/run_intel_readtest.sh
```

## Benchmarks

```bash
benchmarks/run.sh quick
benchmarks/run.sh full
```

BID workloads compare BID64/BID128 with `MathContext.DECIMAL64` /
`DECIMAL128` on equivalent operands. See `benchmarks/README.md`.

Packed binary128 arithmetic and DPML kernels:

```bash
mvn -Pjmh -pl binary128 package -DskipTests
java -jar binary128/target/binary128-benchmarks.jar \
  '.*Binary128JmhBenchmark.*'
```

## License

Apache License 2.0 for the Java project. Intel BSD-3-Clause for the RDFP
algorithms and test vectors. See `LICENSE`, `LICENSE-INTEL`, and `NOTICE`.
Kernels come from Intel's BSD-3-Clause RDFP distribution, not GCC's GPL
libbid. Substantially ported sources keep the Intel notice.
