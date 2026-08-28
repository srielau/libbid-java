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
- String, integer, binary32/64, and BID64<->BID128 conversion
- DPD encode/decode (BID64 and BID128)
- Intel `readtest.in` coverage for the core BID64/BID128 operation families above
- Provisional transcendentals via binary64 evaluation; these do not meet Intel
  DPML precision or flag semantics and are not part of the conformant core
- DBR adapters: Compare/Equals, Sign, RoundToScale, Canonicalize, Decimal
- Dual API: `Bid64`/`Bid128` objects and `Bid64Raw`/`Bid128Raw`, plus
  `DecFloat16Compat`/`DecFloat34Compat` JNI-shaped methods

This git tree publishes two JARs:

- `org.bidfp:libbid-java` (`bid/`) - BID64/BID128
- `org.bidfp:binary128` (`binary128/`) - packed binary128; DPML kernels TBD

Spark should depend on `libbid-java` (it pulls `binary128`). DPML port
instructions: `binary128/AGENTS.md`.

Not in this release: BID32, BID256, binary80, mixed-width arithmetic,
global rounding/flag modes. Packed `Binary128` exists; DPML libm and
BID <-> binary128 convert are the remaining transcendental gap.

## Build and test

Java 17+:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64   # if needed
./build.sh
```

Or with Maven:

```bash
mvn test
```

## JMH (optional)

```bash
mvn -Pjmh clean package
$JAVA_HOME/bin/java -jar bid/target/benchmarks.jar '.*BidJmhBenchmark.*'
```

Workloads compare BID64/BID128 with `MathContext.DECIMAL64` /
`DECIMAL128` on equivalent operands.

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
