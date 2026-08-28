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
  trig, hyperbolics, `erf`/`erfc`, `lgamma`/`tgamma`.

## What landed

- Packed classification and field contract for `BidConvert`.
- Unpacked UX (`Unpacked` + `UxOps`): normalize, unpack, five-mode pack,
  add/sub/mul/div/compare/sqrt. Pack uses the DPML `S/K/L/R` bit-vectors.
- Kernel families listed above, evaluated with unpacked add/mul/div (not
  `Math.exp`). Coefficient *tables* from `*_t_table.c` are not dumped;
  range reduction uses IEEE constants in `tables/IeeeConstants`.

## What is stubbed / follow-up

- Full Intel generated polynomial tables (`dpml_pow_t_table.c` and friends).
- Decimal Payne-Hanek moduli (BID wrappers, not this JAR).
- `bid/` convert + `BidTranscendental` rewire (see `AGENTS.md` close-the-gap).
- Negative-argument reflection for `tgamma`/`lgamma`.
