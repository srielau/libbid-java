# binary128

IEEE 754 **binary128** types and Intel DPML-style float128 kernels.

Maven: `org.bidfp:binary128`. No dependency on BID.

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

## Public API

- Packed fields: `fromRawBits`, `fromFields`, `fromBinary64`, accessors.
- Arithmetic: `add` / `subtract` / `multiply` / `divide` / `sqrt` with
  explicit `RoundingMode` and `StatusFlags` (no process-global FPSR).
- Libm facade: `Dpml.exp`, `log`, `pow`, `cbrt`, `sin`/`cos`/`tan`, inverse
  trig (including `atan2`), hyperbolics, `erf`/`erfc`, `lgamma`/`tgamma`.

## What landed

- Packed classification and field contract for `BidConvert`.
- Unpacked UX (`Unpacked` + `UxOps`): normalize, unpack, five-mode pack,
  add/sub/mul/div/compare/sqrt. Pack uses the DPML `S/K/L/R` bit-vectors.
- Kernel families listed above use Intel QUAD UX range reduction and
  rational/polynomial paths (not `java.lang.Math`).
- Intel QUAD UX tables are generated into
  `tables/{Cons,Exp,Log,Pow,Cbrt,Trig,InvTrig,InvHyper,Erf,Lgamma}X` and
  `FourOverPi` by `binary128/tools/gen_dpml_tables.py`, which reads an external
  `LIBRARY/float128` tree.
- Tests include 1,522 packed vectors generated from Intel's soft
  `bid_f128_*` entry points. Kernel-family tests require exact special results
  and compare finite outputs in ULPs.

## Follow-up outside this artifact

- Decimal Payne-Hanek moduli (BID wrappers, not this JAR).
- `bid/` convert + `BidTranscendental` rewire (see `AGENTS.md` close-the-gap).
