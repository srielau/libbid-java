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
- Add, subtract, multiply, divide (five IEEE rounding modes, status flags)
- Exact string conversion
- Intel `TESTS/readtest.in` vectors for BID64 and BID128

Not in this release: BID32, BID256, FMA, sqrt, quantize, transcendentals,
global rounding/flag modes.

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
$JAVA_HOME/bin/java -jar target/benchmarks.jar '.*BidJmhBenchmark.*'
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
