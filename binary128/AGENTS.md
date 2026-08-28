# DPML / binary128 port (handoff)

This module publishes `org.bidfp:binary128`. Implement Intel DPML here.
Do **not** edit BID packing, `DecNum`, or `Bid64Raw` transcendentals until
the kernels and packed `Binary128` ops work. A follow-up pass in `bid/`
rewires `BidTranscendental` to convert BID -> binary128 -> kernel -> BID.

The C library is **not** in this git repo. Use Intel RDFP 2.0 Update 4
(BSD-3-Clause only; never GCC libbid). On this host a copy lives at:

`/home/serge.rielau/private/libbid-java/upstream/LIBRARY/`

`float128/` is DPML. `src/bid64_*.c` and `src/bid128_*.c` are BID wrappers
(those wrappers belong in `bid/` later, not here).

Test vectors: repo-root `upstream/TESTS/readtest.in`.

## Goal

Java IEEE binary128 libm that matches Intel's default RDFP path:

1. Packed binary128 (`Binary128`, two `long`s).
2. Unpacked DPML float128 ops (`dpml_ux_ops.c` / `dpml_ux_ops_64.c`).
3. Kernels: exp, log, pow, trig, inv trig, hyperbolics, erf, lgamma, cbrt.
4. Public Java functions on `Binary128` (or a `Dpml` facade) with explicit
   rounding and status, no process-global fpsr.

Skip native 80-bit (`USE_COMPILER_F80_TYPE=0`). Emulate f128 only.

Do not use `java.lang.Math` except as a temporary debug aid you delete
before claiming done.

## Non-goals

- BID64/BID128 encodings, `DecNum`, DPD, JNI `DecFloat*Compat`.
- BID32, BID256, mixed-width `bid64dq_*`.
- Publishing a second git repository.

## Layout (keep this package)

```
org.bidfp.binary128
  Binary128.java          packed bits (exists; extend, do not fork)
  Unpacked.java           DPML ux representation
  UxOps.java              add/mul/div/sqrt/compare from dpml_ux_ops*.c
  DpmlExp, DpmlLog, ...   one class per kernel family
  tables                  ported *_t.h / *_t_table.c as Java arrays
```

Public API stays in `org.bidfp.binary128`. Do not depend on `org.bidfp`.

## Implementation order (do not skip)

### 1. Packed binary128

Finish `Binary128`: sign, biased exponent, implicit-bit significand as
112-bit (`long` high 48 + `long` low 64, or two longs + explicit bit).
Canonicalize NaN if Intel convert tests require it.

Add `fromBinary64` / `toBinary64` only if needed to debug; they are not
the oracle.

### 2. BID convert tests live in `bid/` later

Intel families in `readtest.in`:

- `bid64_to_binary128`, `binary128_to_bid64`
- `bid128_to_binary128`, `binary128_to_bid128`

Those converters use BID codecs. **Do not implement them in this module.**
Document a `Binary128` bit layout that `BidConvert` can fill. Optionally
add a package-private test helper that checks exponent/fraction fields
against hex from `readtest.in` once a converter exists.

You may implement **IEEE binary128 <-> binary64** here if it helps unit
test kernels, but the close-the-gap convert is a `bid/` task.

### 3. Unpacked ops

Port `LIBRARY/float128/dpml_ux_ops.c` and `dpml_ux_ops_64.c` plus
`dpml_ux.h` / `dpml_private.h` macros you actually need.

Replace C macros with Java methods. Keep algorithms, not `#ifdef` soup.
Endian: Java is big-endian in the packed high/low convention already
used by `Binary128` (high = sign+exp+frac[111:64]).

Need working: add, sub, mul, div, sqrt, compare, normalize, round-to-
packed binary128 under the 5 IEEE rounding modes.

### 4. Tables then kernels

Port tables as `long[]` / `int[]`, keep Intel comments and LICENSE.

| Kernel | C sources (under LIBRARY/float128/) |
|---|---|
| exp / expm1 / exp2 / exp10 | `dpml_ux_exp.c`, `dpml_exp_x.h` |
| log / log2 / log10 / log1p | `dpml_ux_log.c`, `dpml_log_t.h` |
| pow | `dpml_ux_pow.c`, `dpml_pow_t_table.c` |
| cbrt | `dpml_ux_cbrt.c`, `dpml_cbrt_t_table.c` |
| trig | `dpml_ux_trig.c`, `dpml_four_over_pi.c` |
| inv trig | `dpml_ux_inv_trig.c` |
| inv hyper | `dpml_ux_inv_hyper.c` |
| erf / erfc | `dpml_ux_erf.c` |
| lgamma / tgamma | `dpml_ux_lgamma.c` |

Payne-Hanek for **decimal** sin/cos/tan uses moduli in `src/bid64_sin.c`
and `src/bid128_sin.c`. Those tables are **BID wrappers**, not this JAR.
Here you only need DPML radian trig on binary128. Decimal reduction is
the later `bid/` close-the-gap step.

### 5. Tests

- Unit tests for unpacked add/mul/div vs known hex.
- After kernels: compare binary128 results to a trusted f128 if you have
  one; otherwise delay numeric tests until `bid/` convert exists and
  `readtest.in` `bid64_exp` etc. can run with Intel ULP rules:

  BID64 nearest ~0.55 ULP, directed ~1.05 ULP;
  BID128 nearest ~2.0 ULP, directed ~5.0 ULP.

  NaN/Inf encodings must match bits. See `TESTS/readtest.c` `check64_rel`.

- Line length <= 100. ASCII punctuation in comments. Intel copyright
  on ported files; keep `LICENSE-INTEL` at repo root.

### 6. Done when

- `mvn -pl binary128 test` passes.
- Unpacked ops + at least `exp` and `log` kernels exist and are not
  `Math.exp` wrappers.
- `Binary128` packed format is documented so `BidConvert` can implement
  `bid64_to_binary128` without guessing fields.
- Short note in this file or `binary128/README.md`: what landed, what
  is stubbed.

## Close-the-gap (NOT this agent)

A later change in `bid/`:

1. `BidConvert`: `toBinary128` / `fromBinary128` vs `readtest.in`.
2. Replace `BidTranscendental` `double` path with convert-kernel-convert.
3. Port specials from `src/bid64_exp.c` (NaN quiet, `exp(0)=1`, overflow
   clamps, near-1 log/pow coefficient fixes).
4. Port decimal trig moduli for `sin`/`cos`/`tan`.
5. Turn on Intel vector tests for the 28 functions.

Until then, leave `BidTranscendental` as the provisional binary64 stub.

## Practical notes

- `bid_binarydecimal.c` is ~148k lines, mostly tables. Do not dump it
  into this module. BID<->binary128 convert belongs in `bid/` using
  `DecNum` / `UInt128` like the existing binary64 convert.
- Wrapper C files (`bid64_exp.c` ~97 lines) are thin; copy their
  special-case rules when rewiring BID, not when writing DPML.
- Spark DECFLOAT does not need this JAR until the gap is closed; still
  ship `binary128` as its own artifact from this parent POM.
