/*
 * Copyright (c) 2007-2025, Intel Corp.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the conditions in LICENSE-INTEL are met.
 */
package org.bidfp;

final class BidSqrt {
  private BidSqrt() {
  }

  static long sqrt64(long x, RoundingMode mode, StatusFlags flags) {
    if (Bid64Raw.isNaN(x)) {
      return BidIntegral.canonicalizeNaN64(x, flags);
    }
    if (Bid64Raw.isInf(x)) {
      if (Bid64Raw.isSigned(x)) {
        flags.raise(StatusFlags.INVALID);
        return Bid64.MASK_NAN;
      }
      return Bid64.MASK_INFINITY;
    }
    if (Bid64Raw.isZero(x)) {
      int exp = Bid64.biasedExponentBits(x) - 398;
      int resultExp = exp >> 1;
      return Bid64.finiteRawBits(Bid64Raw.isSigned(x), resultExp + 398, 0L);
    }
    if (Bid64Raw.isSigned(x)) {
      flags.raise(StatusFlags.INVALID);
      return Bid64.MASK_NAN;
    }
    DecNum radicand = DecNum.ofLong(Bid64.significandBits(x));
    int exp = Bid64.biasedExponentBits(x) - 398;
    if ((exp & 1) != 0) {
      radicand.multiplySmall(10);
      exp--;
    }
    int scale = 16 - (radicand.digitCount() + 1) / 2;
    radicand.multiplyPow10(2 * scale);
    DecNum.Sqrt sqrt = DecNum.sqrtFloor(radicand);
    DecNum result = sqrt.root();
    boolean exact = sqrt.remainder().isZero();
    if (!exact) {
      if (incrementSqrt(result, sqrt.remainder(), mode)) {
        result.addOne();
      }
      flags.raise(StatusFlags.INEXACT);
    }
    result.shiftExp(exp / 2 - scale);
    if (exact) {
      result.stripTrailingZeros(exp / 2);
    }
    return result.packBid64(RoundingMode.TOWARD_ZERO, new StatusFlags());
  }

  static void sqrt128(
      long high, long low, RoundingMode mode, StatusFlags flags, long[] out) {
    Bid128 value = Bid128.fromRawBits(high, low);
    if (value.isNaN()) {
      BidIntegral.canonicalizeNaN128(high, low, flags, out);
      return;
    }
    if (value.isInfinite()) {
      if (value.isSigned()) {
        flags.raise(StatusFlags.INVALID);
        DecNum.store128(Bid128.QUIET_NAN, out);
        return;
      }
      DecNum.store128(Bid128.POSITIVE_INFINITY, out);
      return;
    }
    if (value.isZero()) {
      int exp = (value.biasedExponent() - 6176) >> 1;
      DecNum.store128(Bid128.finite(value.isSigned(), exp + 6176, 0L, 0L), out);
      return;
    }
    if (value.isSigned()) {
      flags.raise(StatusFlags.INVALID);
      DecNum.store128(Bid128.QUIET_NAN, out);
      return;
    }
    UInt128 coefficient = value.coefficient();
    DecNum radicand = DecNum.ofUnsigned(coefficient.high(), coefficient.low());
    int exp = value.biasedExponent() - 6176;
    if ((exp & 1) != 0) {
      radicand.multiplySmall(10);
      exp--;
    }
    int scale = 34 - (radicand.digitCount() + 1) / 2;
    radicand.multiplyPow10(2 * scale);
    DecNum.Sqrt sqrt = DecNum.sqrtFloor(radicand);
    DecNum result = sqrt.root();
    boolean exact = sqrt.remainder().isZero();
    if (!exact) {
      if (incrementSqrt(result, sqrt.remainder(), mode)) {
        result.addOne();
      }
      flags.raise(StatusFlags.INEXACT);
    }
    result.shiftExp(exp / 2 - scale);
    if (exact) {
      result.stripTrailingZeros(exp / 2);
    }
    result.packBid128(RoundingMode.TOWARD_ZERO, new StatusFlags(), out);
  }

  private static boolean incrementSqrt(
      DecNum root, DecNum remainder, RoundingMode mode) {
    if (mode == RoundingMode.TOWARD_POSITIVE) {
      return true;
    }
    if (mode == RoundingMode.TOWARD_NEGATIVE || mode == RoundingMode.TOWARD_ZERO) {
      return false;
    }
    DecNum left = new DecNum();
    left.copyFrom(remainder);
    left.multiplySmall(4);
    DecNum right = new DecNum();
    right.copyFrom(root);
    right.multiplySmall(4);
    right.addOne();
    return left.compareAbsolute(right) > 0;
  }
}
