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

/** Intel {@code bid128_pow.c} specials and integer exponent; else DPML. */
final class Bid128Pow {
  private static final Bid128 ZERO =
      Bid128.fromRawBits(0x3040_0000_0000_0000L, 0L);
  private static final Bid128 ONE =
      Bid128.fromRawBits(0x3040_0000_0000_0000L, 1L);
  private static final Bid128 NAN =
      Bid128.fromRawBits(0x7c00_0000_0000_0000L, 0L);
  private static final Bid128 INF =
      Bid128.fromRawBits(Bid128.MASK_INFINITY, 0L);

  private Bid128Pow() {
  }

  static void pow(
      long xh, long xl, long yh, long yl,
      RoundingMode mode, StatusFlags flags, long[] out) {
    Bid128 x = Bid128.fromRawBits(xh, xl);
    Bid128 y = Bid128.fromRawBits(yh, yl);
    if (x.isSignalingNaN() || y.isSignalingNaN()) {
      flags.raise(StatusFlags.INVALID);
    }
    if (y.isZero() && !x.isSignalingNaN()) {
      DecNum.store128(ONE, out);
      return;
    }
    if (x.quietEqual(ONE, new StatusFlags()) && !x.isSignalingNaN()) {
      DecNum.store128(ONE, out);
      return;
    }
    if (x.isNaN()) {
      Bid128Libm.canonNan(xh, xl, flags, out);
      return;
    }
    if (y.isNaN()) {
      Bid128Libm.canonNan(yh, yl, flags, out);
      return;
    }
    long[] yInt = new long[2];
    Bid128Raw.roundIntegralNearestEven(yh, yl, new StatusFlags(), yInt);
    boolean isInt = Bid128.fromRawBits(yInt[0], yInt[1]).quietEqual(y, new StatusFlags());
    boolean odd = false;
    if (isInt) {
      int e = (int) ((yInt[0] >>> 49) & 0x3fff);
      if (e == 6176 && (yInt[1] & 1L) != 0L) {
        odd = true;
      }
    }
    if (y.isInfinite()) {
      Bid128 abs = Bid128.fromRawBits(xh & ~Bid128.MASK_SIGN, xl);
      if (abs.quietEqual(ONE, new StatusFlags())) {
        DecNum.store128(ONE, out);
        return;
      }
      boolean less = abs.quietLess(ONE, new StatusFlags());
      Bid128 result = less == y.isSigned() ? INF : ZERO;
      DecNum.store128(result, out);
      return;
    }
    if (x.isInfinite()) {
      Bid128 result = y.isSigned() ? ZERO : INF;
      if (odd && x.isSigned()) {
        result = Bid128.fromRawBits(result.highBits() ^ Bid128.MASK_SIGN, result.lowBits());
      }
      DecNum.store128(result, out);
      return;
    }
    if (x.isZero()) {
      Bid128 result;
      if (y.isSigned()) {
        flags.raise(StatusFlags.DIVIDE_BY_ZERO);
        result = INF;
      } else {
        result = ZERO;
      }
      if (odd && x.isSigned()) {
        result = Bid128.fromRawBits(result.highBits() ^ Bid128.MASK_SIGN, result.lowBits());
      }
      DecNum.store128(result, out);
      return;
    }
    if (x.isSigned() && !isInt) {
      flags.raise(StatusFlags.INVALID);
      DecNum.store128(NAN, out);
      return;
    }
    long[] packed = new long[2];
    StatusFlags convertFlags = new StatusFlags();
    BidConvert.toBinary128From128(xh, xl, mode, convertFlags, packed);
    Binary128 xd = Binary128.fromRawBits(packed[0], packed[1]);
    long[] packedY = new long[2];
    BidConvert.toBinary128From128(yh, yl, mode, convertFlags, packedY);
    Binary128 yd = Binary128.fromRawBits(packedY[0], packedY[1]);
    if (xd.isZero()) {
      if (yd.isZero()) {
        DecNum.store128(ONE, out);
      } else if (y.isSigned()) {
        DecNum.store128(INF, out);
      } else {
        DecNum.store128(ZERO, out);
      }
      return;
    }
    BidTranscendental.binary128(xh, xl, yh, yl, mode, flags, Dpml::pow, out);
    if (odd && x.isSigned()) {
      out[0] ^= Bid128.MASK_SIGN;
    }
  }
}
