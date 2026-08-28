/*
 * Copyright (c) 2007-2025, Intel Corp.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the conditions in LICENSE-INTEL are met.
 */
package org.bidfp;

import org.bidfp.binary128.Binary128;
import org.bidfp.binary128.Dpml;

/** Intel {@code bid128_lgamma.c}: tiny -log|x|, huge Stirling, poles. */
final class Bid128Lgamma {
  private static final Bid128 INF =
      Bid128.fromRawBits(Bid128.MASK_INFINITY, 0L);
  private static final Bid128 HALF =
      Bid128.fromRawBits(0x303e_0000_0000_0000L, 5L);
  private static final Bid128 LOG_2PI_OVER_2 =
      Bid128.fromRawBits(0x2ffd_c512_596b_f2beL, 0x8512_e0b1_f71b_1870L);
  private static final Binary128 C_M1E34 =
      Binary128.fromRawBits(0xc06f_ed09_defd_561eL, 0x75b2_90c5_1000_0000L);
  private static final Binary128 C_1E34 =
      Binary128.fromRawBits(0x406f_ed09_defd_561eL, 0x75b2_90c5_1000_0000L);
  private static final Binary128 C_HALF =
      Binary128.fromRawBits(0x3ffe_0000_0000_0000L, 0L);
  private static final Binary128 C_1EM100 =
      Binary128.fromRawBits(0x3eb2_bff2_ee48_e052L, 0xfd7a_b2f0_fc57_2779L);

  private Bid128Lgamma() {
  }

  static void lgamma(
      long hi, long lo, RoundingMode mode, StatusFlags flags, long[] out) {
    if (Bid128Libm.canonNan(hi, lo, flags, out)) {
      return;
    }
    Bid128 x = Bid128.fromRawBits(hi, lo);
    if (x.isZero()) {
      flags.raise(StatusFlags.DIVIDE_BY_ZERO);
      DecNum.store128(INF, out);
      return;
    }
    if (x.isInfinite()) {
      DecNum.store128(INF, out);
      return;
    }
    long[] hiPart = new long[2];
    long[] loPart = new long[2];
    BidBinary128Convert.toBinary128TwoPart(hi, lo, hiPart, loPart);
    Binary128 xdHi = Binary128.fromRawBits(hiPart[0], hiPart[1]);
    if (Bid128Libm.lessEqual(xdHi, C_M1E34)) {
      flags.raise(StatusFlags.DIVIDE_BY_ZERO);
      DecNum.store128(INF, out);
      return;
    }
    if (!Bid128Libm.less(xdHi, C_1E34) || x.biasedExponent() - 6176 >= 34) {
      long[] lg1 = new long[2];
      long[] lg2 = new long[2];
      long[] lg3 = new long[2];
      Bid128Raw.sub(
          hi, lo, HALF.highBits(), HALF.lowBits(), mode, flags, lg1);
      Bid128Log.log(hi, lo, mode, flags, lg2);
      Bid128Raw.sub(
          LOG_2PI_OVER_2.highBits(), LOG_2PI_OVER_2.lowBits(),
          hi, lo, mode, flags, lg3);
      Bid128Raw.fma(
          lg1[0], lg1[1], lg2[0], lg2[1], lg3[0], lg3[1], mode, flags, out);
      return;
    }
    if (Bid128Libm.lessEqual(xdHi, C_HALF)) {
      long[] xInt = new long[2];
      Bid128Raw.roundIntegralNearestEven(hi, lo, new StatusFlags(), xInt);
      if (Bid128.fromRawBits(xInt[0], xInt[1]).quietEqual(x, new StatusFlags())) {
        flags.raise(StatusFlags.DIVIDE_BY_ZERO);
        DecNum.store128(INF, out);
        return;
      }
    }
    if (!Bid128Libm.less(xdHi, C_HALF)) {
      BidTranscendental.unary128(hi, lo, mode, flags, Dpml::lgamma, out);
      return;
    }
    if (Bid128Libm.lessEqual(xdHi.abs(), C_1EM100)) {
      long[] logAbs = new long[2];
      Bid128Log.log(hi & ~Bid128.MASK_SIGN, lo, mode, flags, logAbs);
      out[0] = logAbs[0] ^ Bid128.MASK_SIGN;
      out[1] = logAbs[1];
      return;
    }
    BidTranscendental.unary128(hi, lo, mode, flags, Dpml::lgamma, out);
  }
}
