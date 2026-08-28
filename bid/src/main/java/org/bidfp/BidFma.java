/*
 * Copyright (c) 2007-2025, Intel Corp.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the conditions in LICENSE-INTEL are met.
 */
package org.bidfp;

final class BidFma {
  private BidFma() {
  }

  static long fma64(long x, long y, long z, RoundingMode mode, StatusFlags flags) {
    if (Bid64Raw.isNaN(x) || Bid64Raw.isNaN(y) || Bid64Raw.isNaN(z)) {
      if (Bid64Raw.isSignalingNaN(x)
          || Bid64Raw.isSignalingNaN(y)
          || Bid64Raw.isSignalingNaN(z)) {
        flags.raise(StatusFlags.INVALID);
      }
      long nan = Bid64Raw.isNaN(z) ? z : Bid64Raw.isNaN(x) ? x : y;
      return BidIntegral.canonicalizeNaN64(nan, new StatusFlags());
    }
    if ((Bid64Raw.isInf(x) && Bid64Raw.isZero(y))
        || (Bid64Raw.isInf(y) && Bid64Raw.isZero(x))) {
      flags.raise(StatusFlags.INVALID);
      return Bid64.MASK_NAN;
    }
    if (Bid64Raw.isInf(x) || Bid64Raw.isInf(y)) {
      boolean negative = Bid64Raw.isSigned(x) ^ Bid64Raw.isSigned(y);
      if (Bid64Raw.isInf(z) && Bid64Raw.isSigned(z) != negative) {
        flags.raise(StatusFlags.INVALID);
        return Bid64.MASK_NAN;
      }
      return (negative ? Bid64.MASK_SIGN : 0L) | Bid64.MASK_INFINITY;
    }
    if (Bid64Raw.isInf(z)) {
      return (z & Bid64.MASK_SIGN) | Bid64.MASK_INFINITY;
    }
    DecNum a = unpack64(x);
    DecNum b = unpack64(y);
    DecNum c = unpack64(z);
    a.multiply(b);
    boolean productNegative = a.isNegative();
    boolean addendNegative = c.isNegative();
    addSigned(a, c);
    if (a.isZero() && productNegative != addendNegative
        && mode == RoundingMode.TOWARD_NEGATIVE) {
      a.setNegative();
    }
    DecNum minNormal = DecNum.ofCoefficient(false, PowersOfTen.LONG[15], -398);
    boolean tiny = a.compareAbsolute(minNormal) < 0;
    StatusFlags local = new StatusFlags();
    long result = a.packBid64(mode, local);
    if (tiny && local.contains(StatusFlags.INEXACT)) {
      local.raise(StatusFlags.UNDERFLOW);
    }
    flags.raise(local.bits());
    return result;
  }

  static void fma128(
      long xh, long xl,
      long yh, long yl,
      long zh, long zl,
      RoundingMode mode,
      StatusFlags flags,
      long[] out) {
    Bid128 x = Bid128.fromRawBits(xh, xl);
    Bid128 y = Bid128.fromRawBits(yh, yl);
    Bid128 z = Bid128.fromRawBits(zh, zl);
    if (x.isNaN() || y.isNaN() || z.isNaN()) {
      if (x.isSignalingNaN() || y.isSignalingNaN() || z.isSignalingNaN()) {
        flags.raise(StatusFlags.INVALID);
      }
      Bid128 nan = z.isNaN() ? z : x.isNaN() ? x : y;
      BidIntegral.canonicalizeNaN128(
          nan.highBits(), nan.lowBits(), new StatusFlags(), out);
      return;
    }
    if (x.isInfinite() && y.isZero() || y.isInfinite() && x.isZero()) {
      flags.raise(StatusFlags.INVALID);
      DecNum.store128(Bid128.QUIET_NAN, out);
      return;
    }
    if (x.isInfinite() || y.isInfinite()) {
      boolean negative = x.isSigned() ^ y.isSigned();
      if (z.isInfinite() && z.isSigned() != negative) {
        flags.raise(StatusFlags.INVALID);
        DecNum.store128(Bid128.QUIET_NAN, out);
        return;
      }
      DecNum.store128(negative ? Bid128.NEGATIVE_INFINITY : Bid128.POSITIVE_INFINITY, out);
      return;
    }
    if (z.isInfinite()) {
      DecNum.store128(z.isSigned() ? Bid128.NEGATIVE_INFINITY : Bid128.POSITIVE_INFINITY, out);
      return;
    }
    DecNum a = unpack128(x);
    DecNum b = unpack128(y);
    DecNum c = unpack128(z);
    a.multiply(b);
    boolean productNegative = a.isNegative();
    boolean addendNegative = c.isNegative();
    addSigned(a, c);
    if (a.isZero() && productNegative != addendNegative
        && mode == RoundingMode.TOWARD_NEGATIVE) {
      a.setNegative();
    }
    DecNum minNormal = DecNum.ofUnsigned(PowersOfTen.pow10(33).high(),
        PowersOfTen.pow10(33).low());
    minNormal.shiftExp(-6176);
    boolean tiny = a.compareAbsolute(minNormal) < 0;
    StatusFlags local = new StatusFlags();
    a.packBid128(mode, local, out);
    if (tiny && local.contains(StatusFlags.INEXACT)) {
      local.raise(StatusFlags.UNDERFLOW);
    }
    flags.raise(local.bits());
  }

  private static void addSigned(DecNum a, DecNum c) {
    int cmp = a.compareAbsolute(c);
    if (a.isNegative() == c.isNegative()) {
      a.addAbsolute(c);
      return;
    }
    if (cmp >= 0) {
      a.subtractAbsolute(c);
    } else {
      boolean negative = c.isNegative();
      c.subtractAbsolute(a);
      a.copyFrom(c);
      if (negative) {
        a.setNegative();
      }
    }
  }

  private static DecNum unpack64(long x) {
    return DecNum.ofCoefficient(
        Bid64Raw.isSigned(x),
        Bid64Raw.isZero(x) ? 0L : Bid64.significandBits(x),
        Bid64.biasedExponentBits(x) - 398);
  }

  private static DecNum unpack128(Bid128 x) {
    UInt128 coeff = x.coefficient();
    DecNum number = DecNum.ofUnsigned(coeff.high(), coeff.low());
    if (x.isSigned()) {
      number.setNegative();
    }
    number.shiftExp(x.biasedExponent() - 6176);
    return number;
  }
}
