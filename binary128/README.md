# binary128

IEEE 754 **binary128** representation and the Intel DPML float128 kernels used
internally by libbid's BID64/BID128 transcendental functions.

Maven: `org.bidfp:binary128`. No dependency on BID.

## Purpose and scope

This artifact is the Java port of libbid's emulated `bid_f128_*` execution
engine. The `libbid-java` artifact uses it to implement BID64/BID128
transcendentals as BID-to-binary128 conversion, a DPML kernel, and conversion
back to BID. It is published separately to preserve the one-way dependency:
binary128 never depends on BID.

It is not intended to be a complete, general-purpose IEEE binary128 library or
a Java replacement for C `_Float128`. Its supported surface is exactly the
packed representation, arithmetic, and DPML kernel families needed by this
libbid port. In particular, it does not promise:

- the complete arithmetic and utility surface of an IEEE 754 language binding;
- decimal parsing or formatting of binary128 values;
- operations such as fused multiply-add, remainder, `nextAfter`, or `hypot`;
- binary80 support, native hardware integration, or C ABI compatibility; or
- independent compatibility with every public `_Float128` math library.

The original libbid public API exposes BID64/BID128 arithmetic and
transcendentals, plus conversion to and from binary128. Java users seeking the
libbid API should depend on `org.bidfp:libbid-java`; use this artifact directly
only when the packed DPML engine described below is specifically required.

## Packed bit layout (`Binary128`)

Two `long`s, high then low, matching Intel RDFP / later `BidConvert`:

```
high[63]     sign
high[62:48]  biased exponent (bias 16383)
high[47:0]   fraction bits 111:64
low[63:0]    fraction bits 63:0
```

Normals have an implicit leading 1 (113-bit significand). Quiet NaN sets
`high` bit 47 (`0x0000800000000000`).

## Supported API

- Packed fields: `fromRawBits`, `fromFields`, `fromBinary64`, accessors.
- Arithmetic: `add` / `subtract` / `multiply` / `divide` / `sqrt` with
  explicit `RoundingMode` and `StatusFlags` (no process-global FPSR).
- DPML facade: `Dpml.exp`, `log`, `pow`, `cbrt`, `sin`/`cos`/`tan`, inverse
  trig (including `atan2`), hyperbolics, `erf`/`erfc`, `lgamma`/`tgamma`.
- Narrow integration helpers: two-part `lgamma` and `exp` retain DPML guard
  bits across BID128 `tgamma`; they are not a general expansion API.

This is a deliberately bounded API. An operation present in Intel's wider
DPML source tree is not automatically part of this artifact.

## What landed

- Packed classification and field contract for `BidConvert`.
- Packed and unpacked UX (`Unpacked` + `UxOps`): normalize, unpack, five-mode
  pack, add/sub/mul/div/compare/sqrt. Normal arithmetic uses fixed-width limbs;
  division and square root no longer depend on `BigInteger`.
- Kernel families listed above use Intel QUAD UX range reduction and
  rational/polynomial paths (not `java.lang.Math`).
- Kernel evaluation uses nestable per-thread scratch frames, and large-angle
  trig reduction uses Intel's fixed-window Payne-Hanek limb convolution.
- Intel QUAD UX tables are generated into
  `tables/{Cons,Exp,Log,Pow,Cbrt,Trig,InvTrig,InvHyper,Erf,Lgamma}X` and
  `FourOverPi` by `binary128/tools/gen_dpml_tables.py`, which reads an external
  `LIBRARY/float128` tree.
- Tests include 1,522 packed vectors generated from Intel's soft
  `bid_f128_*` entry points. Arithmetic is checked within one ULP. Kernel
  families have targeted ULP oracle tests, while exact packed equality is
  required for specified special results. BID64/BID128 `readtest.in` coverage
  in the sibling module is the end-to-end correctness gate for decimal libm.

## Benchmark

The optional JMH suite measures packed arithmetic and all public DPML kernel
families over deterministic, domain-appropriate binary128 inputs:

```bash
mvn -pl binary128 -Pjmh clean package -DskipTests
java -jar binary128/target/binary128-benchmarks.jar \
  '.*Binary128JmhBenchmark.*'
```

Use a method filter such as `'.*Binary128JmhBenchmark\.(exp|log|pow)'` for a
shorter run. Each invocation includes clearing the explicit status flags.
For repeatable comparisons, keep the JVM and JMH settings fixed and write JSON:

```bash
java -jar binary128/target/binary128-benchmarks.jar \
  '.*Binary128JmhBenchmark.*' -wi 2 -i 3 -w 300ms -r 300ms -f 2 \
  -prof gc -rf json -rff binary128/target/benchmark-results/result.json
```

On a Linux x86-64 host with OpenJDK 17.0.15, a like-for-like short run reduced
the geometric-mean latency of all 33 benchmarks from 1,192 to 497 ns/op (2.40x).
The 27 transcendental benchmarks improved from 1,676 to 840 ns/op (2.00x).
Results are machine-specific and should only be compared with identical JVM
and JMH settings.

## BID integration

The sibling `libbid-java` artifact depends on this JAR and owns all decimal
concerns: exact BID conversion, decimal Payne-Hanek moduli, wrapper specials,
and the convert-kernel-convert seam. This artifact has no dependency on BID.
