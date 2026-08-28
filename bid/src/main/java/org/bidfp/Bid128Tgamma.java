/*
 * Copyright (c) 2007-2025, Intel Corp.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the conditions in LICENSE-INTEL are met.
 */
package org.bidfp;

/** Intel {@code bid128_tgamma.c}: poles, exp(lgamma), odd-interval sign. */
final class Bid128Tgamma {
  private static final Bid128 NAN =
      Bid128.fromRawBits(0x7c00_0000_0000_0000L, 0L);
  private static final Bid128 INF =
      Bid128.fromRawBits(Bid128.MASK_INFINITY, 0L);
  private static final Bid128 ZERO =
      Bid128.fromRawBits(0L, 0L);
  private static final Bid128 SHIFTER =
      Bid128.fromRawBits(0x3040_629b_8c89_1b26L, 0x7182_b614_0000_0000L);

  private Bid128Tgamma() {
  }

  static void tgamma(
      long hi, long lo, RoundingMode mode, StatusFlags flags, long[] out) {
    if (Bid128Libm.canonNan(hi, lo, flags, out)) {
      return;
    }
    Bid128 x = Bid128.fromRawBits(hi, lo);
    if (x.isZero()) {
      flags.raise(StatusFlags.DIVIDE_BY_ZERO);
      out[0] = Bid128.MASK_INFINITY ^ (hi & Bid128.MASK_SIGN);
      out[1] = 0L;
      return;
    }
    if (x.isInfinite()) {
      if (x.isSigned()) {
        flags.raise(StatusFlags.INVALID);
        DecNum.store128(NAN, out);
      } else {
        DecNum.store128(INF, out);
      }
      return;
    }
    Bid128 tiny = Bid128.fromRawBits(0x3018_0000_0000_0000L, 1L);
    if (!x.isSigned() && x.quietLess(tiny, new StatusFlags())) {
      Bid128Raw.div(
          Bid128Libm.ONE.highBits(), Bid128Libm.ONE.lowBits(),
          hi, lo, mode, flags, out);
      return;
    }
    if (x.quietLessEqual(ZERO, new StatusFlags())) {
      long[] xInt = new long[2];
      long[] xFrac = new long[2];
      Bid128Raw.roundIntegralNearestEven(hi, lo, new StatusFlags(), xInt);
      Bid128Raw.sub(hi, lo, xInt[0], xInt[1], mode, flags, xFrac);
      if (Bid128.fromRawBits(xFrac[0], xFrac[1]).isZero()) {
        flags.raise(StatusFlags.INVALID);
        DecNum.store128(NAN, out);
        return;
      }
    }
    long[] y = new long[2];
    Bid128Raw.lgamma(hi, lo, mode, flags, y);
    Bid128Exp.exp(y[0], y[1], mode, flags, out);
    if (Bid128.fromRawBits(out[0], out[1]).isNaN() || !x.isSigned()) {
      return;
    }
    long[] xInt = new long[2];
    Bid128Raw.roundIntegralZero(hi, lo, new StatusFlags(), xInt);
    int e = (int) ((xInt[0] >>> 49) & 0x3fff);
    if (e <= 6176) {
      if (e < 6176) {
        Bid128Raw.add(
            SHIFTER.highBits(), SHIFTER.lowBits(),
            xInt[0], xInt[1], mode, flags, xInt);
      }
      if ((xInt[1] & 1L) == 0L) {
        out[0] ^= Bid128.MASK_SIGN;
      }
    }
  }
}
