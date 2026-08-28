/*
 * Copyright (c) 2007-2025, Intel Corp.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the conditions in LICENSE-INTEL are met.
 */
package org.bidfp;

/** Round-to-integral kernels for BID64 and BID128. */
final class BidIntegral {
  private BidIntegral() {
  }

  static long round64(long x, RoundingMode mode, StatusFlags flags, boolean exact) {
    if (Bid64Raw.isNaN(x)) {
      return canonicalizeNaN64(x, flags);
    }
    if (Bid64Raw.isInf(x)) {
      return (x & Bid64.MASK_SIGN) | Bid64.MASK_INFINITY;
    }
    long coeff = Bid64.significandBits(x);
    int exp = Bid64.biasedExponentBits(x) - 398;
    boolean negative = Bid64Raw.isSigned(x);
    if (coeff == 0L) {
      int biased = Math.max(exp, 0) + 398;
      return Bid64.finiteRawBits(negative, biased, 0L);
    }
    if (exp >= 0) {
      return Bid64.finiteRawBits(negative, exp + 398, coeff);
    }
    int places = -exp;
    DecNum number = DecNum.ofCoefficient(negative, coeff, 0);
    boolean[] sticky = {false};
    StatusFlags local = new StatusFlags();
    int first = number.dividePow10(places, sticky);
    long kept = number.low64();
    boolean inexact = first != 0 || sticky[0];
    if (BidRound.shouldIncrement(negative, kept, first, sticky[0], mode)) {
      number.addOne();
      kept = number.low64();
    }
    if (inexact) {
      local.raise(StatusFlags.INEXACT);
    }
    if (exact) {
      flags.raise(local.bits());
    }
    if (number.isZero()) {
      return Bid64.finiteRawBits(negative, 398, 0L);
    }
    return Bid64.finiteRawBits(negative, 398, kept);
  }

  static long canonicalizeNaN64(long x, StatusFlags flags) {
    long payload = x & 0x0003_ffff_ffff_ffffL;
    if (payload > 999_999_999_999_999L) {
      x = x & 0xfe00_0000_0000_0000L;
    } else {
      x = x & 0xfe03_ffff_ffff_ffffL;
    }
    if ((x & Bid64.MASK_SIGNALING_NAN) == Bid64.MASK_SIGNALING_NAN) {
      flags.raise(StatusFlags.INVALID);
      x = x & 0xfdff_ffff_ffff_ffffL;
    }
    return x;
  }

  static void round128(
      long high,
      long low,
      RoundingMode mode,
      StatusFlags flags,
      boolean exact,
      long[] payloadOut) {
    Bid128 value = Bid128.fromRawBits(high, low);
    if (value.isNaN()) {
      canonicalizeNaN128(high, low, flags, payloadOut);
      return;
    }
    if (value.isInfinite()) {
      payloadOut[0] = (high & Bid128.MASK_SIGN) | Bid128.MASK_INFINITY;
      payloadOut[1] = 0L;
      return;
    }
    UInt128 coeff = value.coefficient();
    int exp = value.biasedExponent() - 6176;
    boolean negative = value.isSigned();
    if (coeff.isZero()) {
      int biased = Math.max(exp, 0) + 6176;
      DecNum.store128(Bid128.finite(negative, biased, 0L, 0L), payloadOut);
      return;
    }
    if (exp >= 0) {
      DecNum.store128(value, payloadOut);
      return;
    }
    DecNum number = DecNum.ofUnsigned(coeff.high(), coeff.low());
    if (negative) {
      number.setNegative();
    }
    boolean[] sticky = {false};
    int first = number.dividePow10(-exp, sticky);
    boolean inexact = first != 0 || sticky[0];
    long keptLow = number.low64();
    if (BidRound.shouldIncrement(negative, keptLow, first, sticky[0], mode)) {
      number.addOne();
    }
    if (exact && inexact) {
      flags.raise(StatusFlags.INEXACT);
    }
    UInt128 rounded = number.toUInt128();
    DecNum.store128(
        Bid128.finite(negative, 6176, rounded.high(), rounded.low()),
        payloadOut);
  }

  static void canonicalizeNaN128(
      long high, long low, StatusFlags flags, long[] payloadOut) {
    UInt128 payload = new UInt128(high & 0x0000_3fff_ffff_ffffL, low);
    long canonicalHigh = high & 0xfc00_0000_0000_0000L;
    long canonicalLow = 0L;
    if (payload.compareTo(PowersOfTen.MAX_33) <= 0) {
      canonicalHigh |= payload.high();
      canonicalLow = payload.low();
    }
    if (Bid128.fromRawBits(high, low).isSignalingNaN()) {
      flags.raise(StatusFlags.INVALID);
      canonicalHigh &= ~0x0200_0000_0000_0000L;
    }
    payloadOut[0] = canonicalHigh;
    payloadOut[1] = canonicalLow;
  }
}
